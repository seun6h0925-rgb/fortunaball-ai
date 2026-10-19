package com.fortunaball.issue.repository;

import com.fortunaball.issue.domain.NumberIssue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NumberIssueRepository extends JpaRepository<NumberIssue, Long> {

    Optional<NumberIssue> findByIssueCode(String issueCode);

    boolean existsByIssueCode(String issueCode);

    /** 이 기기(게스트)의 발급 내역, 최신순. 발급 시각이 같으면 번호가 큰(나중 저장) 건이 먼저. */
    Page<NumberIssue> findByGuestTokenOrderByCreatedAtDescIdDesc(String guestToken, Pageable pageable);
}
