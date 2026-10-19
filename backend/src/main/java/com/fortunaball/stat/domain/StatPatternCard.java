package com.fortunaball.stat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 통계 패턴 카드 (테이블 stat_pattern_card). 회차별 통계(snapshot) 하나에 패턴 6개 × 기간 3개 = 18줄이 붙는다.
 * 화면에는 "최근 {periodRounds}회 중 {hitCount}회 해당"으로 보인다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - (snapshot_id, pattern_code, period_rounds) 가 UNIQUE 라서 같은 조합을 두 번 저장하면 오류가 난다.
 *  - snapshotId 는 연관 객체가 아니라 숫자(Long)로 들고 있다. (조회가 단순하고 지연 로딩 문제가 없다)
 *  - conditionParams 는 JSON 글자 그대로 저장한다. 번호 생성이 쓰는 조건값(예: 핫 번호 목록)이 들어갈 자리이고, 없으면 null.
 */
@Entity
@Table(name = "stat_pattern_card")
public class StatPatternCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_id", nullable = false, updatable = false)
    private Long snapshotId;

    @Enumerated(EnumType.STRING)
    @Column(name = "pattern_code", nullable = false, length = 30)
    private PatternCode patternCode;

    @Column(name = "pattern_name", nullable = false, length = 50)
    private String patternName;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "period_rounds", nullable = false)
    private int periodRounds;

    @Column(name = "hit_count", nullable = false)
    private int hitCount;

    @Column(name = "condition_params", columnDefinition = "json")
    private String conditionParams;

    protected StatPatternCard() {
        // JPA 가 객체를 만들 때 쓰는 기본 생성자
    }

    public StatPatternCard(Long snapshotId, PatternCode patternCode, String description,
                           int periodRounds, int hitCount, String conditionParams) {
        this.snapshotId = snapshotId;
        this.patternCode = patternCode;
        this.patternName = patternCode.displayName();
        this.description = description;
        this.periodRounds = periodRounds;
        this.hitCount = hitCount;
        this.conditionParams = conditionParams;
    }

    public Long getId() {
        return id;
    }

    public Long getSnapshotId() {
        return snapshotId;
    }

    public PatternCode getPatternCode() {
        return patternCode;
    }

    public String getPatternName() {
        return patternName;
    }

    public String getDescription() {
        return description;
    }

    public int getPeriodRounds() {
        return periodRounds;
    }

    public int getHitCount() {
        return hitCount;
    }

    public String getConditionParams() {
        return conditionParams;
    }
}
