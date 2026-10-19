package com.fortunaball.stat.service;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import com.fortunaball.round.service.RoundPolicy;
import com.fortunaball.stat.domain.StatPatternCard;
import com.fortunaball.stat.domain.StatRoundSnapshot;
import com.fortunaball.stat.dto.PatternCardResponse;
import com.fortunaball.stat.dto.PatternStatResponse;
import com.fortunaball.stat.repository.StatPatternCardRepository;
import com.fortunaball.stat.repository.StatRoundSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 저장된 통계(스냅샷·카드)를 읽어서 화면용 응답으로 만드는 서비스. (API-44)
 * 📚 배운 내용: @Transactional(readOnly = true) - 읽기 전용 작업임을 알려 주는 표시
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 여기서는 계산하지 않는다. 계산은 StatSnapshotService 가 미리 해서 DB 에 저장해 두었고, 여기는 읽기만 한다.
 *  - 보여 주는 통계는 "지금 대상 회차(RoundPolicy.targetRound)"의 통계다.
 *    대상 회차 통계는 항상 "결과가 저장된 마지막 회차"를 기준으로 하므로, 사전 분석 상태에서도 숫자는 최신 확정 결과 기준이다.
 *  - 통계가 아직 없으면(서버를 처음 켜서 맞춤이 끝나기 전 등) 503 + "준비하고 있어요" 로 안내한다.
 *  - period 는 20·50·100 만 허용하고, 안 보내면 100 이다. 그 외는 입력 오류(400).
 */
@Service
public class StatQueryService {

    private final RoundPolicy roundPolicy;
    private final StatRoundSnapshotRepository snapshotRepo;
    private final StatPatternCardRepository cardRepo;

    public StatQueryService(RoundPolicy roundPolicy,
                            StatRoundSnapshotRepository snapshotRepo,
                            StatPatternCardRepository cardRepo) {
        this.roundPolicy = roundPolicy;
        this.snapshotRepo = snapshotRepo;
        this.cardRepo = cardRepo;
    }

    /** 문자열로 받은 기간을 검사한다. 비어 있으면 기본값(100). */
    public static int parsePeriod(String raw) {
        if (raw == null || raw.isBlank()) {
            return PatternRule.DEFAULT_PERIOD;
        }
        int period;
        try {
            period = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw invalidPeriod(raw);
        }
        if (!PatternRule.PERIODS.contains(period)) {
            throw invalidPeriod(raw);
        }
        return period;
    }

    private static BusinessException invalidPeriod(String raw) {
        return new BusinessException(ErrorCode.INVALID_INPUT,
                "기간은 20, 50, 100 중에서 선택해 주세요.",
                "period=" + raw + " (허용: " + PatternRule.PERIODS + ")");
    }

    @Transactional(readOnly = true)
    public PatternStatResponse getPatterns(int period) {
        int target = roundPolicy.targetRound();
        StatRoundSnapshot snapshot = snapshotRepo.findByTargetRoundNo(target)
                .orElseThrow(() -> new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                        "통계를 준비하고 있어요. 잠시 후 다시 시도해 주세요.",
                        "stat_round_snapshot 없음: targetRound=" + target));

        List<PatternCardResponse> cards = cardRepo.findBySnapshotIdAndPeriodRounds(snapshot.getId(), period)
                .stream()
                .sorted(Comparator.comparingInt((StatPatternCard c) -> c.getPatternCode().ordinal()))
                .map(c -> new PatternCardResponse(
                        c.getPatternCode().name(), c.getPatternName(), c.getDescription(),
                        c.getPeriodRounds(), c.getHitCount()))
                .toList();

        return new PatternStatResponse(
                snapshot.getTargetRoundNo(),
                snapshot.getBasisRoundNo(),
                snapshot.getStatus().name(),
                snapshot.getStatus().label(),
                period,
                snapshot.getGeneratedAt(),
                cards);
    }
}
