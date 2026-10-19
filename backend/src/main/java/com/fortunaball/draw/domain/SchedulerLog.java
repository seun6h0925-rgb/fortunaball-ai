package com.fortunaball.draw.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 스케줄러 실행 기록 (DB 테이블 scheduler_log).
 * 자동 작업(회차 수집 등)이 "언제 시작해서 어떻게 끝났는지"를 한 줄씩 남긴다.
 * 관리자 대시보드의 "스케줄러 상태"와 오류 추적에 쓰인다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 작업이 시작되면 RUNNING 으로 먼저 저장하고, 끝나면 같은 줄을 SUCCESS 또는 FAILED 로 바꾼다.
 *    (줄이 새로 생기는 게 아니라 같은 줄이 갱신된다)
 *  - 서버가 도중에 꺼지면 RUNNING 으로 남을 수 있다. 관리자 화면에서 오래된 RUNNING 은 "중단됨"으로 봐야 한다.
 *  - 회차와 상관없는 작업이면 targetRoundNo 는 NULL 이다.
 *  - created_at 같은 자동 컬럼이 없는 테이블이라 시간은 모두 코드에서 넣는다. (한국 시간 기준)
 *
 * 📚 배운 내용: @Entity, @Table, @Id, @GeneratedValue (JPA)
 */
@Entity
@Table(name = "scheduler_log")
public class SchedulerLog {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String jobName;
    private String jobStatus;
    private Integer targetRoundNo;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Integer processedCount;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    protected SchedulerLog() { }   // JPA 가 객체를 만들 때 쓰는 기본 생성자

    /** 작업 시작 기록 (RUNNING) */
    public SchedulerLog(String jobName, Integer targetRoundNo, LocalDateTime startedAt) {
        this.jobName = jobName;
        this.jobStatus = STATUS_RUNNING;
        this.targetRoundNo = targetRoundNo;
        this.startedAt = startedAt;
    }

    public void markSuccess(LocalDateTime endedAt, int processedCount) {
        this.jobStatus = STATUS_SUCCESS;
        this.endedAt = endedAt;
        this.processedCount = processedCount;
    }

    public void markFailed(LocalDateTime endedAt, int processedCount, String errorMessage) {
        this.jobStatus = STATUS_FAILED;
        this.endedAt = endedAt;
        this.processedCount = processedCount;
        this.errorMessage = errorMessage;
    }

    public Long getId() { return id; }
    public String getJobName() { return jobName; }
    public String getJobStatus() { return jobStatus; }
    public Integer getTargetRoundNo() { return targetRoundNo; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public Integer getProcessedCount() { return processedCount; }
    public String getErrorMessage() { return errorMessage; }
}
