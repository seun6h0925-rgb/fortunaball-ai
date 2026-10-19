package com.fortunaball.issue.repository;

import com.fortunaball.issue.domain.IssueNumber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface IssueNumberRepository extends JpaRepository<IssueNumber, Long> {

    List<IssueNumber> findByNumberIssueIdOrderByGameOrderAsc(Long numberIssueId);

    /** 발급 건별 검증 현황 집계용 결과 한 줄 */
    interface VerificationCount {
        Long getIssueId();

        Long getTotalGames();

        Long getVerifiedGames();

        Long getErrorGames();
    }

    /**
     * 발급 건별로 "전체 게임 수 / 검증 완료 수 / 검증 오류 수"를 한 번에 센다. (목록에서 건마다 따로 조회하지 않으려고)
     * winning_verification 은 아직 엔티티가 없어서(당첨 검증 기능 때 추가) 표 이름으로 직접 조회한다.
     * 검증 결과가 없는 게임은 LEFT JOIN 으로 빠지지 않고 "전체 게임 수"에만 센다.
     */
    @Query(nativeQuery = true, value = """
            SELECT n.number_issue_id AS issueId,
                   COUNT(*) AS totalGames,
                   COUNT(CASE WHEN v.result_status = 'VERIFIED' THEN 1 END) AS verifiedGames,
                   COUNT(CASE WHEN v.result_status = 'ERROR' THEN 1 END) AS errorGames
              FROM issue_number n
              LEFT JOIN winning_verification v ON v.issue_number_id = n.id
             WHERE n.number_issue_id IN (:ids)
             GROUP BY n.number_issue_id
            """)
    List<VerificationCount> countVerification(@Param("ids") Collection<Long> ids);
}
