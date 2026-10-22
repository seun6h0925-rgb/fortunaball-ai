package com.fortunaball.issue.repository;

import com.fortunaball.issue.domain.NumberIssue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NumberIssueRepository extends JpaRepository<NumberIssue, Long> {

    Optional<NumberIssue> findByIssueCode(String issueCode);

    boolean existsByIssueCode(String issueCode);

    /**
     * 이 기기(게스트)의 발급 내역, 최신순. 발급 시각이 같으면 번호가 큰(나중 저장) 건이 먼저.
     * 사용자가 "지우기"를 누른 건(hiddenAt 있음)은 빼고 보여 준다. (메서드 이름은 그대로 두고 조건만 @Query 로 추가)
     */
    @Query("select n from NumberIssue n where n.guestToken = :guestToken and n.hiddenAt is null "
            + "order by n.createdAt desc, n.id desc")
    Page<NumberIssue> findByGuestTokenOrderByCreatedAtDescIdDesc(@Param("guestToken") String guestToken, Pageable pageable);
}
