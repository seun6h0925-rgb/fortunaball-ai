package com.fortunaball.generate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.common.guest.GuestToken;
import com.fortunaball.draw.domain.DrawRound;
import com.fortunaball.draw.repository.DrawRoundRepository;
import com.fortunaball.generate.domain.Combo;
import com.fortunaball.generate.domain.GenerationTicket;
import com.fortunaball.generate.domain.PatternUnavailableException;
import com.fortunaball.generate.dto.GenerateRequest;
import com.fortunaball.generate.dto.GenerateResponse;
import com.fortunaball.generate.dto.PatternCondition;
import com.fortunaball.issue.repository.ComboIssueCountRepository;
import com.fortunaball.round.service.RoundPolicy;
import com.fortunaball.stat.domain.PatternCode;
import com.fortunaball.stat.domain.StatPatternCard;
import com.fortunaball.stat.domain.StatRoundSnapshot;
import com.fortunaball.stat.dto.DrawNumbers;
import com.fortunaball.stat.repository.StatPatternCardRepository;
import com.fortunaball.stat.repository.StatRoundSnapshotRepository;
import com.fortunaball.stat.service.PatternRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 번호 생성 API(API-03)의 흐름을 묶는 서비스. DB 에는 아무것도 쓰지 않는다. (읽기 전용)
 * 📚 배운 내용: @Service, @Transactional(readOnly = true), 생성자 주입
 *
 * 흐름
 *  1) 입력 검사 (게임 수 1~최대값, 패턴 코드)
 *  2) 대상 회차를 서버가 직접 계산 (클라이언트가 보낸 회차는 받지 않는다)
 *  3) 그 회차의 통계 스냅샷 + 패턴 카드(기간 100회)에서 조건값(핫넘버·겨울잠 번호)을 읽는다
 *  4) 기준 회차까지 최근 100회로 분포 기준(DrawProfile)을 만든다
 *  5) 이 회차에서 이미 N번(기본 3) 확정 발급된 조합을 읽어 제외 목록으로 쓴다
 *  6) NumberGenerator 로 번호 생성 → GenerationStore 에 30분 보관 → 확정 토큰과 함께 응답
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 패턴 조건이 안 맞는 상황(예: 겨울잠 번호가 하나도 없음)에서는 패턴을 완화하지 않고 CONFLICT(409)로 알린다.
 *    화면에서는 "다른 패턴을 선택해 주세요"로 안내한다.
 *  - 같은 게스트가 다시 생성하면 GenerationStore 가 이전 임시 결과를 버린다. (이전 토큰으로는 확정 불가)
 */
@Service
public class GenerateService {

    private static final Logger log = LoggerFactory.getLogger(GenerateService.class);

    private final RoundPolicy roundPolicy;
    private final StatRoundSnapshotRepository snapshotRepo;
    private final StatPatternCardRepository cardRepo;
    private final DrawRoundRepository drawRepo;
    private final ComboIssueCountRepository comboCountRepo;
    private final NumberGenerator generator;
    private final GenerationStore store;
    private final GenerationProperties props;
    private final ObjectMapper objectMapper;

    public GenerateService(RoundPolicy roundPolicy,
                           StatRoundSnapshotRepository snapshotRepo,
                           StatPatternCardRepository cardRepo,
                           DrawRoundRepository drawRepo,
                           ComboIssueCountRepository comboCountRepo,
                           NumberGenerator generator,
                           GenerationStore store,
                           GenerationProperties props,
                           ObjectMapper objectMapper) {
        this.roundPolicy = roundPolicy;
        this.snapshotRepo = snapshotRepo;
        this.cardRepo = cardRepo;
        this.drawRepo = drawRepo;
        this.comboCountRepo = comboCountRepo;
        this.generator = generator;
        this.store = store;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public GenerateResponse generate(GuestToken guest, GenerateRequest request) {
        // 1) 입력 검사
        int gameCount = validateGameCount(request == null ? null : request.gameCount());
        PatternCode code = parsePatternCode(request == null ? null : request.patternCode());

        // 2) 대상 회차는 서버가 계산한다
        ZonedDateTime now = ZonedDateTime.now(RoundPolicy.ZONE);
        int targetRound = roundPolicy.targetRound(now);
        LocalDate drawDate = roundPolicy.drawDate(now);

        // 3) 통계 스냅샷
        StatRoundSnapshot snapshot = snapshotRepo.findByTargetRoundNo(targetRound)
                .orElseThrow(() -> new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                        "통계를 준비하고 있어요. 잠시 후 다시 시도해 주세요.",
                        "stat_round_snapshot 없음: targetRound=" + targetRound));
        int basisRound = snapshot.getBasisRoundNo();

        // 4) 기준 회차까지 최근 100회로 분포 기준을 만든다
        DrawProfile profile = DrawProfile.from(loadHistory(basisRound));

        // 3-2) 패턴 조건값
        PatternCondition condition = loadCondition(snapshot, code);

        // 5) 이미 N번 찬 조합은 제외
        Set<String> excluded = new HashSet<>(comboCountRepo.findKeysAtLimit(targetRound, props.maxIssuePerCombo()));

        // 6) 생성 → 보관
        List<Combo> games = runGenerator(condition, gameCount, profile, excluded, code, targetRound);
        GenerationTicket ticket = store.issue(guest.value(), targetRound, basisRound, code, games);
        log.info("[번호생성] guest={} 대상회차={} 기준회차={} 게임수={} 패턴={}",
                guest, targetRound, basisRound, gameCount, code);

        return new GenerateResponse(
                ticket.confirmToken(),
                props.ttlMinutes(),
                ticket.expiresAt(),
                games.stream().map(Combo::numbers).toList(),
                code == null ? null : code.name(),
                code == null ? null : code.displayName(),
                targetRound,
                drawDate,
                basisRound);
    }

    private int validateGameCount(Integer gameCount) {
        if (gameCount == null || gameCount < 1 || gameCount > props.maxGames()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "게임 수는 1개부터 " + props.maxGames() + "개까지 선택할 수 있어요.",
                    "gameCount=" + gameCount + " (허용: 1~" + props.maxGames() + ")");
        }
        return gameCount;
    }

    /** 비어 있으면 null(패턴 없음). 모르는 코드는 입력 오류. */
    private PatternCode parsePatternCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return PatternCode.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "선택할 수 없는 패턴이에요. 패턴을 다시 선택해 주세요.",
                    "patternCode=" + raw);
        }
    }

    private List<DrawNumbers> loadHistory(int basisRound) {
        List<DrawRound> rounds = drawRepo.findByRoundNoLessThanEqualOrderByRoundNoDesc(
                basisRound, PageRequest.of(0, PatternRule.GENERATION_PERIOD));
        if (rounds.isEmpty()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "통계를 준비하고 있어요. 잠시 후 다시 시도해 주세요.",
                    "당첨 결과 없음: basisRound=" + basisRound);
        }
        return rounds.stream().map(r -> new DrawNumbers(r.getRoundNo(), r.getNumbers())).toList();
    }

    /** 패턴 카드(기간 100회)의 condition_params 에서 핫넘버·겨울잠 번호를 읽는다. 다른 패턴은 번호 목록이 필요 없다. */
    private PatternCondition loadCondition(StatRoundSnapshot snapshot, PatternCode code) {
        if (code == null) {
            return PatternCondition.none();
        }
        StatPatternCard card = cardRepo.findBySnapshotIdAndPeriodRounds(snapshot.getId(), PatternRule.GENERATION_PERIOD)
                .stream().filter(c -> c.getPatternCode() == code).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                        "통계를 준비하고 있어요. 잠시 후 다시 시도해 주세요.",
                        "패턴 카드 없음: snapshotId=" + snapshot.getId() + " code=" + code));
        return switch (code) {
            case HOT_NUMBER -> new PatternCondition(code, readNumbers(card, "hotNumbers"), Set.of());
            case WINTER_SLEEP -> new PatternCondition(code, Set.of(), readNumbers(card, "sleepNumbers"));
            default -> new PatternCondition(code, Set.of(), Set.of());
        };
    }

    private Set<Integer> readNumbers(StatPatternCard card, String field) {
        try {
            JsonNode arr = objectMapper.readTree(card.getConditionParams()).path(field);
            Set<Integer> result = new HashSet<>();
            arr.forEach(n -> result.add(n.asInt()));
            return result;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "번호를 만들지 못했어요. 잠시 후 다시 시도해 주세요.",
                    "condition_params 읽기 실패: cardId=" + card.getId() + " field=" + field + " cause=" + e);
        }
    }

    private List<Combo> runGenerator(PatternCondition condition, int gameCount, DrawProfile profile,
                                     Set<String> excluded, PatternCode code, int targetRound) {
        try {
            return generator.generate(condition, gameCount, profile, excluded);
        } catch (PatternUnavailableException e) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "지금은 이 패턴으로 번호를 만들 수 없어요. 다른 패턴을 선택해 주세요.",
                    "패턴 조건 불충족: code=" + code + " targetRound=" + targetRound + " cause=" + e.getMessage());
        } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "번호를 만들지 못했어요. 게임 수를 줄이거나 잠시 후 다시 시도해 주세요.",
                    "후보 부족: code=" + code + " targetRound=" + targetRound + " gameCount=" + gameCount + " cause=" + e.getMessage());
        }
    }
}
