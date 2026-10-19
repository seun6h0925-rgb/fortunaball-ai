package com.fortunaball.verify.repository;

import com.fortunaball.verify.domain.WinningVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface WinningVerificationRepository extends JpaRepository<WinningVerification, Long> {

    /** 발급 번호 여러 게임의 검증 결과를 한 번에 읽는다. (게임마다 따로 조회하지 않으려고) */
    List<WinningVerification> findByIssueNumberIdIn(Collection<Long> issueNumberIds);

    /**
     * 검증 결과를 저장한다. 이미 있으면 아무것도 하지 않는다. (결과는 한 번 정해지면 바뀌지 않는다)
     * 📚 배운 내용: INSERT ... ON DUPLICATE KEY (MySQL). "id = id" 는 중복일 때 값을 바꾸지 않겠다는 뜻이다.
     *
     * ⚠ 반환 값으로 "새로 저장됐는지"를 구분하면 안 된다. MySQL 은 건너뛴 경우에도 1을 돌려줄 수 있다.
     *   (그래서 반환 값 없음. 저장 여부는 다시 읽어서 확인한다)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            INSERT INTO winning_verification
                   (issue_number_id, round_no, match_count, bonus_matched, winning_rank, result_status)
            VALUES (:issueNumberId, :roundNo, :matchCount, :bonusMatched, :winningRank, :resultStatus)
            ON DUPLICATE KEY UPDATE id = id
            """)
    void insertIfAbsent(@Param("issueNumberId") Long issueNumberId,
                       @Param("roundNo") int roundNo,
                       @Param("matchCount") int matchCount,
                       @Param("bonusMatched") boolean bonusMatched,
                       @Param("winningRank") Integer winningRank,
                       @Param("resultStatus") String resultStatus);
}
