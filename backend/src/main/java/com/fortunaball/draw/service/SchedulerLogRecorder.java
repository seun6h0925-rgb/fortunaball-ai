package com.fortunaball.draw.service;

import com.fortunaball.draw.domain.SchedulerLog;
import com.fortunaball.draw.repository.SchedulerLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 스케줄러 실행 기록을 남기는 담당. (시작 → 성공/실패 순서로 호출)
 *
 * 사용 예
 *   Long logId = recorder.start("DRAW_COLLECT", null);
 *   ... 작업 ...
 *   recorder.success(logId, 저장건수);          // 또는 recorder.failed(logId, 저장건수, "사유");
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 기록을 남기다 실패해도 "원래 작업(회차 수집)"은 절대 멈추지 않는다.
 *    그래서 모든 메서드가 오류를 잡아 파일 로그(app.log)에만 남기고 넘어간다.
 *    (기록용 DB 문제 때문에 수집이 안 되면 본말전도)
 *  - start 가 기록에 실패하면 null 을 돌려준다. success/failed 는 null 이 오면 아무것도 하지 않는다.
 *  - 이 클래스의 메서드는 다른 클래스에서 불러야 @Transactional 이 적용된다. (같은 클래스 안에서 부르면 적용 안 됨)
 */
@Component
public class SchedulerLogRecorder {

    private static final Logger log = LoggerFactory.getLogger(SchedulerLogRecorder.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** error_message 가 너무 길어 저장이 실패하는 일을 막는 상한 */
    private static final int MAX_MESSAGE_LENGTH = 2000;

    private final SchedulerLogRepository repository;

    public SchedulerLogRecorder(SchedulerLogRepository repository) {
        this.repository = repository;
    }

    /** 작업 시작을 기록한다. 실패하면 null. */
    @Transactional
    public Long start(String jobName, Integer targetRoundNo) {
        try {
            SchedulerLog saved = repository.save(new SchedulerLog(jobName, targetRoundNo, now()));
            return saved.getId();
        } catch (RuntimeException e) {
            log.warn("[스케줄로그] 시작 기록 실패 (작업은 계속함): job={} / {}", jobName, e.toString());
            return null;
        }
    }

    /** 작업 성공으로 마무리한다. */
    @Transactional
    public void success(Long logId, int processedCount) {
        if (logId == null) {
            return;
        }
        try {
            repository.findById(logId).ifPresent(l -> l.markSuccess(now(), processedCount));
        } catch (RuntimeException e) {
            log.warn("[스케줄로그] 성공 기록 실패 (작업은 정상 종료됨): id={} / {}", logId, e.toString());
        }
    }

    /** 작업 실패로 마무리한다. */
    @Transactional
    public void failed(Long logId, int processedCount, String errorMessage) {
        if (logId == null) {
            return;
        }
        try {
            String message = truncate(errorMessage);
            repository.findById(logId).ifPresent(l -> l.markFailed(now(), processedCount, message));
        } catch (RuntimeException e) {
            log.warn("[스케줄로그] 실패 기록 실패: id={} / {}", logId, e.toString());
        }
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(KST);
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_MESSAGE_LENGTH ? message : message.substring(0, MAX_MESSAGE_LENGTH);
    }
}
