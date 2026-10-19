package com.fortunaball.stat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fortunaball.draw.domain.DrawRound;
import com.fortunaball.draw.repository.DrawRoundRepository;
import com.fortunaball.round.service.RoundPolicy;
import com.fortunaball.stat.domain.PatternCode;
import com.fortunaball.stat.domain.SnapshotStatus;
import com.fortunaball.stat.domain.StatPatternCard;
import com.fortunaball.stat.domain.StatRoundSnapshot;
import com.fortunaball.stat.dto.DrawNumbers;
import com.fortunaball.stat.dto.PatternResult;
import com.fortunaball.stat.repository.StatPatternCardRepository;
import com.fortunaball.stat.repository.StatRoundSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * 회차별 통계(스냅샷)를 "지금 상황에 맞게" 맞춰 두는 서비스.
 * 📚 배운 내용: 멱등(idempotent) - 몇 번을 실행해도 결과가 같다. 그래서 서버 시작·토요일 20시·새 회차 저장 때 부담 없이 불러도 된다.
 *
 * 하는 일 (L = DB 에 저장된 가장 최근 회차, T = 지금의 대상 회차)
 *  1) L회 통계: 확정(CONFIRMED), 기준 회차 L 로 맞춘다.
 *  2) T회 통계: 사전 분석(PREPARING), 기준 회차 L 로 맞춘다. (T > L 일 때)
 *  3) T-1 이 L 보다 크면(추첨은 끝났는데 결과 저장 전): T-1회 통계는 결과 대기(WAITING)로 바꾼다.
 * 기준 회차가 달라졌으면 카드 18장(패턴 6 × 기간 3)을 지우고 다시 계산해서 넣는다.
 *
 * ⚠ 카드 설명 문구는 "사실"만 쓴다. 예측·확률 상승 같은 표현은 쓰지 않는다.
 */
@Service
public class StatSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(StatSnapshotService.class);

    /** 가장 긴 기간(100) + 겨울잠 번호가 보는 앞선 회차(20) */
    private static final int HISTORY_SIZE = PatternRule.GENERATION_PERIOD + PatternRule.WINTER_GAP_ROUNDS;

    private final DrawRoundRepository drawRepo;
    private final StatRoundSnapshotRepository snapshotRepo;
    private final StatPatternCardRepository cardRepo;
    private final PatternCalculator calculator;
    private final RoundPolicy roundPolicy;
    private final ObjectMapper objectMapper;

    public StatSnapshotService(DrawRoundRepository drawRepo,
                               StatRoundSnapshotRepository snapshotRepo,
                               StatPatternCardRepository cardRepo,
                               PatternCalculator calculator,
                               RoundPolicy roundPolicy,
                               ObjectMapper objectMapper) {
        this.drawRepo = drawRepo;
        this.snapshotRepo = snapshotRepo;
        this.cardRepo = cardRepo;
        this.calculator = calculator;
        this.roundPolicy = roundPolicy;
        this.objectMapper = objectMapper;
    }

    /** 지금 시각 기준으로 통계를 맞춘다. */
    @Transactional
    public void sync() {
        sync(roundPolicy.targetRound(), LocalDateTime.now());
    }

    /** 대상 회차와 시각을 직접 받는 버전 (시험용) */
    @Transactional
    public void sync(int targetRound, LocalDateTime now) {
        Optional<DrawRound> latestOpt = drawRepo.findTopByOrderByRoundNoDesc();
        if (latestOpt.isEmpty()) {
            log.info("저장된 회차가 없어 통계를 만들지 않습니다.");
            return;
        }
        int latest = latestOpt.get().getRoundNo();
        List<DrawNumbers> history = loadHistory(latest);

        // 1) 최근 저장 회차: 확정
        ensureSnapshot(latest, latest, SnapshotStatus.CONFIRMED, history, now);

        // 2) 대상 회차: 사전 분석
        if (targetRound > latest) {
            // 3) 추첨은 끝났고 결과만 기다리는 회차: 결과 대기
            if (targetRound - 1 > latest) {
                ensureSnapshot(targetRound - 1, latest, SnapshotStatus.WAITING, history, now);
            }
            ensureSnapshot(targetRound, latest, SnapshotStatus.PREPARING, history, now);
        }
    }

    private void ensureSnapshot(int target, int basis, SnapshotStatus want,
                                List<DrawNumbers> history, LocalDateTime now) {
        Optional<StatRoundSnapshot> found = snapshotRepo.findByTargetRoundNo(target);
        StatRoundSnapshot snap;
        boolean rebuild;
        if (found.isEmpty()) {
            snap = snapshotRepo.save(StatRoundSnapshot.prepare(target, basis, now));
            rebuild = true;
            log.info("통계 새로 만듦: {}회 (기준 {}회)", target, basis);
        } else {
            snap = found.get();
            rebuild = snap.getBasisRoundNo() != basis;
            if (rebuild) {
                log.info("통계 다시 계산: {}회 (기준 {}회 → {}회)", target, snap.getBasisRoundNo(), basis);
                snap.rebuild(basis, now);
            }
        }

        if (rebuild) {
            cardRepo.deleteBySnapshotId(snap.getId());
            cardRepo.saveAll(buildCards(snap.getId(), history));
        }

        if (want == SnapshotStatus.CONFIRMED && snap.getStatus() != SnapshotStatus.CONFIRMED) {
            snap.confirm(basis, now);
        } else if (want == SnapshotStatus.WAITING && snap.getStatus() == SnapshotStatus.PREPARING) {
            snap.markWaiting();
        }
        snapshotRepo.save(snap);
    }

    /** latest 회차까지 최근 120회를 오래된 순서로 */
    private List<DrawNumbers> loadHistory(int latest) {
        List<DrawRound> rounds = new ArrayList<>(
                drawRepo.findByRoundNoLessThanEqualOrderByRoundNoDesc(latest, PageRequest.of(0, HISTORY_SIZE)));
        Collections.reverse(rounds);
        List<DrawNumbers> result = new ArrayList<>();
        for (DrawRound r : rounds) {
            result.add(new DrawNumbers(r.getRoundNo(), r.getNumbers()));
        }
        return result;
    }

    private List<StatPatternCard> buildCards(Long snapshotId, List<DrawNumbers> history) {
        List<StatPatternCard> cards = new ArrayList<>();
        Set<Integer> sleep = calculator.sleepNumbers(history);
        for (int period : PatternRule.PERIODS) {
            int from = Math.max(0, history.size() - period);
            Set<Integer> hot = calculator.hotNumbers(history.subList(from, history.size()));
            for (PatternResult r : calculator.calculate(history, period)) {
                cards.add(new StatPatternCard(snapshotId, r.code(), describe(r.code()),
                        period, r.hitCount(), toJson(params(r.code(), hot, sleep))));
            }
        }
        return cards;
    }

    /** 패턴 규칙을 한 줄로 설명 (사실만) */
    static String describe(PatternCode code) {
        return switch (code) {
            case HOT_NUMBER -> "기간 안에서 가장 많이 나온 번호 10개 중 3개 이상이 한 회차에 함께 나온 경우";
            case WINTER_SLEEP -> "직전 20회 동안 나오지 않았던 번호가 1개 이상 나온 경우";
            case BALANCE_MIX -> "홀수가 2~4개 섞여 나온 경우";
            case HIGH_LOW_MIX -> "22 이하 번호가 2~4개 섞여 나온 경우";
            case CENTER_ZONE -> "16~30 사이 번호가 4개 이상 나온 경우";
            case ALL_SECTION_SPREAD -> "5개 구간(1~9, 10~19, 20~29, 30~39, 40~45) 중 4개 이상에서 번호가 나온 경우";
        };
    }

    /** 번호 생성에서 쓰는 조건값 */
    private Map<String, Object> params(PatternCode code, Set<Integer> hot, Set<Integer> sleep) {
        Map<String, Object> m = new LinkedHashMap<>();
        switch (code) {
            case HOT_NUMBER -> {
                m.put("hotNumbers", new TreeSet<>(hot));
                m.put("minMatch", PatternRule.HOT_MIN_MATCH);
            }
            case WINTER_SLEEP -> {
                m.put("sleepNumbers", new TreeSet<>(sleep));
                m.put("gapRounds", PatternRule.WINTER_GAP_ROUNDS);
                m.put("minCount", 1);
            }
            case BALANCE_MIX -> {
                m.put("oddMin", PatternRule.ODD_MIN);
                m.put("oddMax", PatternRule.ODD_MAX);
            }
            case HIGH_LOW_MIX -> {
                m.put("lowMaxNumber", PatternRule.LOW_MAX_NUMBER);
                m.put("lowMin", PatternRule.LOW_MIN);
                m.put("lowMax", PatternRule.LOW_MAX);
            }
            case CENTER_ZONE -> {
                m.put("from", PatternRule.CENTER_FROM);
                m.put("to", PatternRule.CENTER_TO);
                m.put("minCount", PatternRule.CENTER_MIN);
            }
            case ALL_SECTION_SPREAD -> m.put("minSections", PatternRule.SPREAD_MIN_SECTIONS);
        }
        return m;
    }

    private String toJson(Map<String, Object> m) {
        try {
            return objectMapper.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("조건값을 JSON 으로 바꾸지 못했습니다", e);
        }
    }
}
