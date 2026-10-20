package com.fortunaball.verify.domain;

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
 * 발급 번호 한 게임의 당첨 검증 결과. (읽기용)
 * DB 테이블: winning_verification  (발급 번호 1게임당 결과 1건 - issue_number_id 에 UNIQUE)
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 저장은 이 엔티티로 하지 않는다. WinningVerificationRepository.insertIfAbsent (INSERT ... ON DUPLICATE KEY) 로만 넣는다.
 *    같은 번호를 두 사람이 동시에 조회해도 결과가 두 건 생기거나 오류가 나지 않게 하려는 것이다. (ComboIssueCount 와 같은 방식)
 *  - winningRank 가 null 이면 낙첨이다. (1~5등만 값이 있다)
 *  - 발급 번호와 연결은 객체 참조 없이 issueNumberId 로만 한다. (IssueNumber 와 같은 방식)
 */
@Entity
@Table(name = "winning_verification")
public class WinningVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issue_number_id", nullable = false, insertable = false, updatable = false)
    private Long issueNumberId;

    @Column(name = "round_no", nullable = false, insertable = false, updatable = false)
    private int roundNo;

    @Column(name = "match_count", nullable = false, insertable = false, updatable = false)
    private int matchCount;

    @Column(name = "bonus_matched", nullable = false, insertable = false, updatable = false)
    private boolean bonusMatched;

    /** 1~5등, 낙첨이면 null */
    @Column(name = "winning_rank", insertable = false, updatable = false)
    private Integer winningRank;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_status", nullable = false, insertable = false, updatable = false, length = 20)
    private VerificationResultStatus resultStatus;

    /** 우리 서비스 당첨 기록 공개 여부. 읽기만 한다. 바꿀 때는 Repository 의 updatePublic* 로만 바꾼다. */
    @Column(name = "is_public", nullable = false, insertable = false, updatable = false)
    private boolean publicVisible;

    @Column(name = "verified_at", insertable = false, updatable = false)
    private LocalDateTime verifiedAt;

    protected WinningVerification() {
        // JPA 가 객체를 만들 때 쓰는 기본 생성자
    }

    /** 저장된 결과를 계산 결과 객체로 바꿔준다. */
    public MatchResult toMatchResult() {
        return new MatchResult(matchCount, bonusMatched, winningRank);
    }

    public Long getId() { return id; }
    public Long getIssueNumberId() { return issueNumberId; }
    public int getRoundNo() { return roundNo; }
    public int getMatchCount() { return matchCount; }
    public boolean isBonusMatched() { return bonusMatched; }
    public Integer getWinningRank() { return winningRank; }
    public VerificationResultStatus getResultStatus() { return resultStatus; }
    public boolean isPublicVisible() { return publicVisible; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
}
