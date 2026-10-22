package com.fortunaball.issue.domain;

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
 * 번호 발급 기록 한 건. (사용자가 "발급 확정"을 누를 때마다 1건, 그 안에 게임 여러 개)
 * DB 테이블: number_issue. 게임별 번호는 IssueNumber(issue_number)에 있다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 발급 기록과 번호들은 같은 트랜잭션으로 저장한다. (한쪽만 저장되면 안 된다)
 *  - guestToken 은 DB 에 그대로 저장한다. 화면·로그에는 앞 4자리만 보인다 (GuestToken.toString 참고).
 *  - createdAt 은 DB 가 넣는다(DEFAULT CURRENT_TIMESTAMP). 코드에서 값을 넣지 않는다.
 *  - 발급 기록의 번호·회차 등은 수정하지 않는다. 바뀌는 값은 hiddenAt(사용자가 "내 목록에서 지우기"를 누른 시각) 하나뿐이다.
 *  - 지워도 기록 자체는 DB 에 그대로 남는다. 이 기기의 목록(API-42)에서만 빠지고, 당첨 검증·통계·발급코드 조회에는 영향이 없다.
 */
@Entity
@Table(name = "number_issue")
public class NumberIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issue_code", nullable = false, updatable = false, length = 20)
    private String issueCode;

    @Column(name = "guest_token", nullable = false, updatable = false, length = 64)
    private String guestToken;

    @Column(name = "target_round_no", nullable = false, updatable = false)
    private int targetRoundNo;

    @Column(name = "game_count", nullable = false, updatable = false)
    private int gameCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "issue_type", nullable = false, updatable = false, length = 30)
    private IssueType issueType;

    @Column(name = "generation_basis_type", updatable = false, length = 30)
    private String generationBasisType;

    @Column(name = "generation_basis_value", updatable = false, length = 50)
    private String generationBasisValue;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 사용자가 이 기기 목록에서 지운 시각. NULL 이면 목록에 보인다. (기록은 지우지 않는다) */
    @Column(name = "hidden_at")
    private LocalDateTime hiddenAt;

    protected NumberIssue() {
        // JPA 가 객체를 만들 때 쓰는 기본 생성자
    }

    public static NumberIssue create(String issueCode, String guestToken, int targetRoundNo, int gameCount,
                                     IssueType issueType, String basisType, String basisValue) {
        NumberIssue n = new NumberIssue();
        n.issueCode = issueCode;
        n.guestToken = guestToken;
        n.targetRoundNo = targetRoundNo;
        n.gameCount = gameCount;
        n.issueType = issueType;
        n.generationBasisType = basisType;
        n.generationBasisValue = basisValue;
        return n;
    }

    /** "내 목록에서 지우기". 이미 지운 건이면 처음 지운 시각을 그대로 둔다. */
    public void hide(LocalDateTime now) {
        if (hiddenAt == null) {
            hiddenAt = now;
        }
    }

    public Long getId() { return id; }
    public String getIssueCode() { return issueCode; }
    public String getGuestToken() { return guestToken; }
    public int getTargetRoundNo() { return targetRoundNo; }
    public int getGameCount() { return gameCount; }
    public IssueType getIssueType() { return issueType; }
    public String getGenerationBasisType() { return generationBasisType; }
    public String getGenerationBasisValue() { return generationBasisValue; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getHiddenAt() { return hiddenAt; }
}
