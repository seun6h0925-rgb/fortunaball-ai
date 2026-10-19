package com.fortunaball.stat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 회차별 통계 상태 (테이블 stat_round_snapshot).
 * 대상 회차 하나당 한 줄만 있다. (target_round_no UNIQUE)
 * 📚 배운 내용: @Entity - 자바 클래스를 DB 테이블 한 줄과 연결한다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - targetRoundNo : 이 통계를 "보여 줄" 대상 회차. 아직 추첨 전인 미래 회차일 수 있어 draw_round 와 FK 로 묶지 않았다.
 *  - basisRoundNo  : 계산에 실제로 포함된 마지막 회차 (draw_round 에 있는 회차, FK).
 *  - created_at / updated_at 은 DB 가 채우므로 코드에서 쓰지 않는다.
 */
@Entity
@Table(name = "stat_round_snapshot")
public class StatRoundSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_round_no", nullable = false, updatable = false)
    private int targetRoundNo;

    @Column(name = "basis_round_no", nullable = false)
    private int basisRoundNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SnapshotStatus status;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    protected StatRoundSnapshot() {
        // JPA 가 객체를 만들 때 쓰는 기본 생성자
    }

    /** 사전 분석(PREPARING) 상태로 새로 만든다 */
    public static StatRoundSnapshot prepare(int targetRoundNo, int basisRoundNo, LocalDateTime now) {
        StatRoundSnapshot s = new StatRoundSnapshot();
        s.targetRoundNo = targetRoundNo;
        s.basisRoundNo = basisRoundNo;
        s.status = SnapshotStatus.PREPARING;
        s.generatedAt = now;
        return s;
    }

    /** 결과 대기로 바꾼다 (추첨 시각이 지났고 결과는 아직 없을 때) */
    public void markWaiting() {
        this.status = SnapshotStatus.WAITING;
    }

    /** 대상 회차 결과까지 반영해 확정한다 */
    public void confirm(int basisRoundNo, LocalDateTime now) {
        this.basisRoundNo = basisRoundNo;
        this.status = SnapshotStatus.CONFIRMED;
        this.confirmedAt = now;
    }

    public Long getId() {
        return id;
    }

    public int getTargetRoundNo() {
        return targetRoundNo;
    }

    public int getBasisRoundNo() {
        return basisRoundNo;
    }

    public SnapshotStatus getStatus() {
        return status;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }
}
