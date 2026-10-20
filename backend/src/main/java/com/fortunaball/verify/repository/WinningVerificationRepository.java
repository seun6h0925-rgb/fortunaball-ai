package com.fortunaball.verify.repository;

import com.fortunaball.verify.domain.WinningVerification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * 한 발급 건의 당첨 조합(1~5등)의 공개 여부를 한꺼번에 바꾼다. (낙첨 조합은 건드리지 않는다)
     * 인증 승인/숨김/반려 때 AdminVerificationService 가 부른다. 반환 값 = 바뀐 행 수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE winning_verification SET is_public = :pub
            WHERE winning_rank IS NOT NULL
              AND issue_number_id IN (SELECT id FROM issue_number WHERE number_issue_id = :issueId)
            """)
    int updatePublicByIssue(@Param("issueId") Long issueId, @Param("pub") boolean pub);

    /** 당첨 기록 한 건의 공개 여부를 바꾼다. (API-27) 낙첨 건은 바뀌지 않는다. 반환 값 = 바뀐 행 수(0 이면 낙첨이거나 없음) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE winning_verification SET is_public = :pub
            WHERE id = :id AND winning_rank IS NOT NULL
            """)
    int updatePublicById(@Param("id") Long id, @Param("pub") boolean pub);

    /** 공개된 당첨 기록 목록. (API-17) roundNo·rank 는 null 이면 조건에서 뺀다. 정렬은 Pageable 로 정한다. */
    @Query("""
            select w from WinningVerification w
            where w.publicVisible = true and w.winningRank is not null
              and (:roundNo is null or w.roundNo = :roundNo)
              and (:rank is null or w.winningRank = :rank)
            """)
    Page<WinningVerification> findPublicRecords(@Param("roundNo") Integer roundNo, @Param("rank") Integer rank,
                                                Pageable pageable);
}
