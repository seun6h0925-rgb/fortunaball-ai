package com.fortunaball.record.repository;

import com.fortunaball.record.domain.AnonymousVerification;
import com.fortunaball.record.domain.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnonymousVerificationRepository extends JpaRepository<AnonymousVerification, Long> {

    /**
     * 이 발급 기록에 "반려가 아닌" 인증이 이미 있는지. (있으면 새로 등록할 수 없다)
     * 호출 예: existsByNumberIssueIdAndReviewStatusNot(issueId, ReviewStatus.REJECTED)
     */
    boolean existsByNumberIssueIdAndReviewStatusNot(Long numberIssueId, ReviewStatus status);

    /** 같은 용지(해시)로 "반려가 아닌" 인증이 이미 있는지. */
    boolean existsByTicketHashAndReviewStatusNot(String ticketHash, ReviewStatus status);
}
