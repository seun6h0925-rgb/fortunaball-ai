package com.fortunaball.issue.repository;

import com.fortunaball.issue.domain.NumberIssue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NumberIssueRepository extends JpaRepository<NumberIssue, Long> {

    Optional<NumberIssue> findByIssueCode(String issueCode);

    boolean existsByIssueCode(String issueCode);
}
