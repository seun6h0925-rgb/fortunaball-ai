package com.fortunaball.record.repository;

import com.fortunaball.record.domain.AnonymousVerification;
import com.fortunaball.record.domain.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AnonymousVerificationRepository extends JpaRepository<AnonymousVerification, Long> {

    /**
     * 이 발급 기록에 "반려가 아닌" 인증이 이미 있는지. (있으면 새로 등록할 수 없다)
     * 호출 예: existsByNumberIssueIdAndReviewStatusNot(issueId, ReviewStatus.REJECTED)
     */
    boolean existsByNumberIssueIdAndReviewStatusNot(Long numberIssueId, ReviewStatus status);

    /** 같은 용지(해시)로 "반려가 아닌" 인증이 이미 있는지. */
    boolean existsByTicketHashAndReviewStatusNot(String ticketHash, ReviewStatus status);

    /** 검수 상태별 목록. (정렬은 Pageable 로 정한다. 관리자 목록 API-28 에서 사용) */
    Page<AnonymousVerification> findByReviewStatus(ReviewStatus status, Pageable pageable);

    /**
     * 발급 기록별 "가장 높은 등수"(숫자가 작을수록 높음)를 한 번에 가져온다. 목록 한 쪽(최대 50건)에 대해 쿼리 1번으로 끝내려고 모아서 묻는다.
     * 결과 한 줄 = [발급 기록 번호(Long), 최고 등수(Integer)]. 당첨 조합이 없으면 그 발급 기록은 결과에 나오지 않는다.
     */
    @Query("""
            select n.numberIssueId, min(w.winningRank)
            from IssueNumber n, WinningVerification w
            where w.issueNumberId = n.id and w.winningRank is not null and n.numberIssueId in :issueIds
            group by n.numberIssueId
            """)
    List<Object[]> findBestRanksByIssueIds(@Param("issueIds") Collection<Long> issueIds);

    /** 공개 중인(승인 + 공개) 인증만. 당첨 기록 목록(API-17)에서 사용자 한마디를 붙일 때 쓴다. */
    List<AnonymousVerification> findByNumberIssueIdInAndReviewStatusAndPublicVisibleTrue(
            Collection<Long> numberIssueIds, ReviewStatus status);
}
