package com.fortunaball.issue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 조합 제공 횟수. (같은 회차 + 같은 조합을 서비스 전체에서 몇 번 확정 발급했는지)
 * DB 테이블: combo_issue_count (UNIQUE: target_round_no + combo_key)
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 번호 생성(API-03)은 이 표를 "읽기만" 한다. 횟수를 올리는 것은 발급 확정(API-04)의 일이다.
 *  - 횟수를 올릴 때는 "issued_count < 3 일 때만 +1" 하는 한 문장 UPDATE 로 처리한다. (동시에 확정해도 3번을 넘지 않게)
 *    그 쓰기 기능은 API-04 를 만들 때 이 클래스와 저장소에 추가한다.
 */
@Entity
@Table(name = "combo_issue_count")
public class ComboIssueCount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_round_no", nullable = false, updatable = false)
    private int targetRoundNo;

    @Column(name = "combo_key", nullable = false, updatable = false, length = 17)
    private String comboKey;

    @Column(name = "issued_count", nullable = false)
    private int issuedCount;

    protected ComboIssueCount() {
        // JPA 가 객체를 만들 때 쓰는 기본 생성자
    }

    public Long getId() { return id; }
    public int getTargetRoundNo() { return targetRoundNo; }
    public String getComboKey() { return comboKey; }
    public int getIssuedCount() { return issuedCount; }
}
