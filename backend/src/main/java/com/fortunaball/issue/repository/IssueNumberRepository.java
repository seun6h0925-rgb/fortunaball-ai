package com.fortunaball.issue.repository;

import com.fortunaball.issue.domain.IssueNumber;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueNumberRepository extends JpaRepository<IssueNumber, Long> {

    List<IssueNumber> findByNumberIssueIdOrderByGameOrderAsc(Long numberIssueId);
}
