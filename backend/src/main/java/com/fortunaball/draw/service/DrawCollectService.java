package com.fortunaball.draw.service;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.draw.collector.DrawCollector;
import com.fortunaball.draw.domain.DrawRound;
import com.fortunaball.draw.dto.CollectOutcome;
import com.fortunaball.draw.dto.CollectSummary;
import com.fortunaball.draw.dto.DrawApiResponse;
import com.fortunaball.draw.repository.DrawPrizeRepository;
import com.fortunaball.draw.repository.DrawRoundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 공식 회차 데이터를 가져와 DB(draw_round, draw_prize)에 저장하는 서비스.
 * 처음 1번은 1회~최신 회차 전체를, 이후 매주는 새 회차만 가져온다 (이미 있는 회차는 건너뛰므로 같은 메서드를 쓴다).
 * 📚 배운 내용: @Service(업무 처리 담당), 트랜잭션(여러 저장을 하나로 묶어 "전부 성공 또는 전부 취소")
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 한 회차의 당첨번호(draw_round)와 당첨금 5줄(draw_prize)은 하나의 트랜잭션으로 저장한다.
 *    중간에 실패하면 둘 다 취소되어 "번호만 있고 당첨금이 없는" 반쪽 데이터가 남지 않는다.
 *  - 저장 순서는 반드시 당첨번호 → 당첨금이다 (draw_prize 가 draw_round 를 외래키로 참조).
 *  - @Transactional 대신 TransactionTemplate 을 쓴 이유: 같은 클래스 안에서 메서드를 부르면 @Transactional 이
 *    적용되지 않는 스프링의 함정이 있어서, 코드로 범위를 직접 정했다.
 *  - 한 회차가 실패해도 멈추지 않고 다음 회차로 간다. 단, 연속으로 N번 실패하면 사이트 문제로 보고 중단한다.
 *  - 수집은 한 번에 하나만 돈다. 이미 돌고 있을 때 또 호출하면 아무것도 하지 않고 "이미 실행 중"으로 끝낸다.
 *  - 요청 사이에 간격을 둔다 (공식 서버 부담 방지). 이미 있어서 건너뛴 회차는 요청을 안 보내므로 간격도 없다.
 */
@Service
public class DrawCollectService {

    private static final Logger log = LoggerFactory.getLogger(DrawCollectService.class);

    /** 무한 반복 방지용 안전 상한 (로또가 이 회차까지 가려면 수십 년이 더 걸린다) */
    private static final int MAX_ROUND = 5000;

    private final DrawCollector collector;
    private final DrawRoundRepository roundRepository;
    private final DrawPrizeRepository prizeRepository;
    private final TransactionTemplate transactionTemplate;
    private final long requestDelayMs;
    private final int maxConsecutiveFailures;
    /** 수집이 지금 돌고 있는지. 서버 시작 수집과 매주 스케줄러가 겹쳐 같은 회차를 동시에 저장하는 일을 막는다. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    public DrawCollectService(
            DrawCollector collector,
            DrawRoundRepository roundRepository,
            DrawPrizeRepository prizeRepository,
            TransactionTemplate transactionTemplate,
            @Value("${fortuna.draw.request-delay-ms:300}") long requestDelayMs,
            @Value("${fortuna.draw.max-consecutive-failures:5}") int maxConsecutiveFailures) {
        this.collector = collector;
        this.roundRepository = roundRepository;
        this.prizeRepository = prizeRepository;
        this.transactionTemplate = transactionTemplate;
        this.requestDelayMs = requestDelayMs;
        this.maxConsecutiveFailures = maxConsecutiveFailures;
    }

    /** 1회부터 최신 회차까지 빠진 회차를 전부 채운다. 마지막 회차를 지나(데이터 없음) 끝나면 정상 완료. */
    public CollectSummary collectAll() {
        if (!running.compareAndSet(false, true)) {
            log.warn("[회차수집] 이미 수집이 진행 중이라 이번 요청은 건너뜀");
            return new CollectSummary(0, 0, 0, List.of(), 0, "이미 실행 중이라 건너뜀");
        }
        try {
            return doCollectAll();
        } finally {
            running.set(false);   // 오류가 나도 반드시 풀어 둔다 (안 풀면 이후 수집이 영영 안 돈다)
        }
    }

    private CollectSummary doCollectAll() {
        int saved = 0;
        int skipped = 0;
        int notAggregated = 0;
        List<Integer> failed = new ArrayList<>();
        int consecutiveFailures = 0;
        int round = 1;
        String stopReason;

        while (true) {
            if (round > MAX_ROUND) {
                stopReason = "안전 상한(" + MAX_ROUND + "회)에 도달해 중단";
                break;
            }

            CollectOutcome outcome = null;
            try {
                outcome = collectRound(round);
                consecutiveFailures = 0;
            } catch (BusinessException e) {
                // 통신 실패·점검·형식 변경: 원인은 detail 에 있다
                failed.add(round);
                consecutiveFailures++;
                log.warn("[회차수집] {}회 실패: {}", round, e.getDetail());
            } catch (RuntimeException e) {
                // DB 오류, 받은 값이 규칙에 어긋남 등: 스택 정보까지 남긴다
                failed.add(round);
                consecutiveFailures++;
                log.warn("[회차수집] {}회 저장 중 오류: {}", round, e.toString(), e);
            }

            if (consecutiveFailures >= maxConsecutiveFailures) {
                stopReason = "연속 " + consecutiveFailures + "회 실패로 중단 (공식 사이트 점검·변경 가능성)";
                break;
            }

            if (outcome == CollectOutcome.NO_DATA) {
                stopReason = "마지막 회차까지 확인 완료";
                break;
            }
            if (outcome == CollectOutcome.SAVED) {
                saved++;
                if (saved % 100 == 0) {
                    log.info("[회차수집] 진행 중: {}회까지 확인, 새로 저장 {}건", round, saved);
                }
            } else if (outcome == CollectOutcome.SKIPPED_EXISTS) {
                skipped++;
            } else if (outcome == CollectOutcome.NOT_AGGREGATED) {
                notAggregated++;
            }

            round++;
            if (outcome != CollectOutcome.SKIPPED_EXISTS && !pause()) {
                stopReason = "중단 요청을 받아 종료";
                break;
            }
        }

        CollectSummary summary = new CollectSummary(saved, skipped, notAggregated, failed, round, stopReason);
        log.info("[회차수집] 끝: 새로 저장 {}, 건너뜀 {}, 집계 전 {}, 실패 {}건 {}, 마지막 확인 {}회 / {}",
                saved, skipped, notAggregated, failed.size(), failed, round, stopReason);
        return summary;
    }

    /**
     * 한 회차를 수집한다.
     * @throws BusinessException 공식 사이트에서 가져오지 못한 경우 (DrawCollector 에서 던짐)
     */
    public CollectOutcome collectRound(int roundNo) {
        if (roundRepository.existsByRoundNo(roundNo)) {
            return CollectOutcome.SKIPPED_EXISTS;
        }

        Optional<DrawApiResponse.Item> fetched = collector.fetch(roundNo);
        if (fetched.isEmpty()) {
            return CollectOutcome.NO_DATA;
        }
        DrawApiResponse.Item item = fetched.get();
        if (!item.isPrizeAggregated()) {
            log.info("[회차수집] {}회는 2~5등 집계 전이라 저장하지 않음 (다음에 다시 받음)", roundNo);
            return CollectOutcome.NOT_AGGREGATED;
        }

        // 당첨번호 → 당첨금 순서로, 하나의 트랜잭션으로 저장한다
        transactionTemplate.execute(status -> {
            DrawRound round = roundRepository.save(item.toDrawRound("DHLOTTERY_API"));
            prizeRepository.saveAll(item.toDrawPrizes());
            log.debug("[회차수집] {}회 저장 완료 (id={})", roundNo, round.getId());
            return null;
        });
        return CollectOutcome.SAVED;
    }

    /** 요청 사이 간격. 중단 요청(인터럽트)을 받으면 false. */
    private boolean pause() {
        if (requestDelayMs <= 0) {
            return true;
        }
        try {
            Thread.sleep(requestDelayMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
