package com.fortunaball.verify.service;

import com.fortunaball.draw.domain.DrawRound;
import com.fortunaball.draw.repository.DrawRoundRepository;
import com.fortunaball.verify.domain.MatchResult;
import com.fortunaball.verify.domain.VerificationResultStatus;
import com.fortunaball.verify.domain.WinningRankRule;
import com.fortunaball.verify.repository.UnverifiedGameRepository;
import com.fortunaball.verify.repository.UnverifiedGameRepository.UnverifiedGame;
import com.fortunaball.verify.repository.WinningVerificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 당첨 검증 배치: 추첨이 끝난 회차의 "아직 판정이 없는 발급 게임"을 한꺼번에 판정해서 저장한다.
 * 사용자가 발급코드로 결과를 조회하지 않아도, 당첨 기록(API-17)이 채워지게 하려는 것이다.
 * 📚 배운 내용: TransactionTemplate(코드로 트랜잭션 범위 정하기), 배치 처리(나눠서 읽고 처리)
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 등수는 기존 IssueResultService 와 똑같이 WinningRankRule 로만 정하고, 저장도 같은 insertIfAbsent 를 쓴다.
 *    그래서 사용자 조회와 배치가 동시에 같은 게임을 판정해도 결과는 한 건이다. 한 번 저장된 판정은 바뀌지 않는다.
 *  - 게임마다 따로 트랜잭션을 건다. 한 게임이 실패해도 그 게임만 건너뛰고(실패 수에 센다) 나머지는 계속 처리한다.
 *    (여러 게임을 한 트랜잭션으로 묶으면 한 건의 오류로 전부 취소되기 때문)
 *  - BATCH_SIZE 개씩 읽어서 처리한다. 한 번 읽은 묶음에서 성공이 하나도 없으면(같은 게임이 계속 실패하는 상황)
 *    무한 반복하지 않도록 멈춘다. 다음 실행 때 다시 시도한다.
 *  - 당첨 판정이 없는 "추첨 전 회차"는 조회 쿼리(JOIN draw_round)에서 빠지므로 여기서 따로 다루지 않는다.
 */
@Service
public class VerificationBatchService {

    private static final Logger log = LoggerFactory.getLogger(VerificationBatchService.class);

    static final int BATCH_SIZE = 500;
    /** 한 번 실행에서 처리하는 최대 묶음 수 (안전 상한: 500 × 200 = 10만 게임) */
    static final int MAX_BATCHES = 200;

    /** 실행 결과. verified = 새로 판정 저장한 게임 수, failed = 판정하지 못한 게임 수 */
    public record Result(int verified, int failed) {
    }

    private final UnverifiedGameRepository targetRepo;
    private final DrawRoundRepository drawRepo;
    private final WinningVerificationRepository verificationRepo;
    private final TransactionTemplate tx;

    public VerificationBatchService(UnverifiedGameRepository targetRepo,
                                    DrawRoundRepository drawRepo,
                                    WinningVerificationRepository verificationRepo,
                                    TransactionTemplate tx) {
        this.targetRepo = targetRepo;
        this.drawRepo = drawRepo;
        this.verificationRepo = verificationRepo;
        this.tx = tx;
    }

    /** 판정할 게임이 하나라도 있는지. (없으면 실행 기록도 남기지 않고 조용히 넘어가려고) */
    public boolean hasPending() {
        return !targetRepo.findUnverified(1).isEmpty();
    }

    /** 판정이 없는 게임을 모두 판정한다. */
    public Result verifyAll() {
        int verified = 0;
        int failed = 0;
        Map<Integer, DrawRound> drawCache = new HashMap<>();   // 같은 회차를 게임마다 다시 읽지 않으려고

        for (int i = 0; i < MAX_BATCHES; i++) {
            List<UnverifiedGame> games = targetRepo.findUnverified(BATCH_SIZE);
            if (games.isEmpty()) {
                break;
            }
            int okInBatch = 0;
            for (UnverifiedGame game : games) {
                if (verifyOne(game, drawCache)) {
                    okInBatch++;
                } else {
                    failed++;
                }
            }
            verified += okInBatch;
            if (okInBatch == 0) {
                log.warn("[당첨검증배치] 이번 묶음 {}건이 모두 실패해 중단 (다음 실행 때 다시 시도)", games.size());
                break;
            }
        }
        log.info("[당첨검증배치] 끝: 새로 판정 {}건, 실패 {}건", verified, failed);
        return new Result(verified, failed);
    }

    /** 한 게임을 판정해 저장한다. 성공하면 true. 오류는 밖으로 던지지 않고 로그에만 남긴다. */
    private boolean verifyOne(UnverifiedGame game, Map<Integer, DrawRound> drawCache) {
        try {
            DrawRound draw = drawCache.computeIfAbsent(game.getRoundNo(), r -> drawRepo.findByRoundNo(r).orElse(null));
            if (draw == null) {
                log.warn("[당첨검증배치] 추첨 결과를 찾지 못함: gameId={}, round={}", game.getId(), game.getRoundNo());
                return false;
            }
            List<Integer> numbers = List.of(game.getNumber1(), game.getNumber2(), game.getNumber3(),
                    game.getNumber4(), game.getNumber5(), game.getNumber6());
            MatchResult r = WinningRankRule.evaluate(numbers, draw.getNumbers(), draw.getBonusNumber());
            tx.execute(status -> {
                verificationRepo.insertIfAbsent(game.getId(), draw.getRoundNo(), r.matchCount(), r.bonusMatched(),
                        r.rank(), VerificationResultStatus.VERIFIED.name());
                return null;
            });
            return true;
        } catch (RuntimeException e) {
            log.warn("[당첨검증배치] 판정 실패: gameId={}, round={} / {}", game.getId(), game.getRoundNo(), e.toString());
            return false;
        }
    }
}
