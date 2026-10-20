package com.fortunaball.verify.service;

import com.fortunaball.draw.service.SchedulerLogRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 당첨 검증 배치를 정해진 간격으로 돌리는 스케줄러. (서버가 켜지고 1분 뒤 첫 실행, 이후 10분마다)
 * 새 회차가 저장되면 최대 10분 안에 그 회차의 발급 게임이 판정되고,
 * 서버가 꺼져 있던 동안 놓친 판정도 서버를 켠 뒤 첫 실행에서 채워진다.
 * 📚 배운 내용: @Scheduled(fixedDelay), @ConditionalOnProperty
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 판정할 게임이 없으면 DB 조회 한 번만 하고 끝난다. 실행 기록(scheduler_log)도 남기지 않는다. (10분마다 기록이 쌓이는 것을 막으려고)
 *  - 판정할 것이 있어서 돌았을 때만 scheduler_log(VERIFY_BATCH)에 시작·결과를 남긴다.
 *    실패한 게임이 하나라도 있으면 FAILED 로 기록하고, 다음 실행 때 그 게임을 다시 시도한다.
 *  - 이미 돌고 있을 때 또 호출되면 건너뛴다. (fixedDelay 는 앞 실행이 끝난 뒤부터 간격을 세므로 보통 겹치지 않지만, 안전장치)
 *  - fortuna.verify.batch.enabled=false 로 끌 수 있다. 간격은 fortuna.verify.batch.interval-ms 로 바꾼다. (기본 10분)
 */
@Component
@ConditionalOnProperty(name = "fortuna.verify.batch.enabled", havingValue = "true", matchIfMissing = true)
public class VerificationBatchRunner {

    private static final Logger log = LoggerFactory.getLogger(VerificationBatchRunner.class);

    /** 실행 기록(scheduler_log)의 작업 이름 */
    public static final String JOB_NAME = "VERIFY_BATCH";

    private final VerificationBatchService batchService;
    private final SchedulerLogRecorder logRecorder;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public VerificationBatchRunner(VerificationBatchService batchService, SchedulerLogRecorder logRecorder) {
        this.batchService = batchService;
        this.logRecorder = logRecorder;
    }

    @Scheduled(initialDelayString = "${fortuna.verify.batch.initial-delay-ms:60000}",
            fixedDelayString = "${fortuna.verify.batch.interval-ms:600000}")
    public void run() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            if (!batchService.hasPending()) {
                return;
            }
            Long logId = logRecorder.start(JOB_NAME, null);
            try {
                VerificationBatchService.Result result = batchService.verifyAll();
                if (result.failed() == 0) {
                    logRecorder.success(logId, result.verified());
                } else {
                    logRecorder.failed(logId, result.verified(), "판정 실패 " + result.failed() + "건 (다음 실행 때 다시 시도)");
                }
            } catch (RuntimeException e) {
                logRecorder.failed(logId, 0, "예기치 못한 오류: " + e);
                throw e;
            }
        } catch (RuntimeException e) {
            // 스케줄러는 다음 시각에 다시 돈다. 원인만 로그로 남긴다.
            log.error("[당첨검증배치] 예기치 못한 오류: {}", e.toString(), e);
        } finally {
            running.set(false);
        }
    }
}
