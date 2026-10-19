package com.fortunaball.issue.repository;

import com.fortunaball.issue.domain.ComboIssueCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ComboIssueCountRepository extends JpaRepository<ComboIssueCount, Long> {

    /**
     * 이 회차에서 이미 limit 번 이상 확정 발급된 조합의 key 목록.
     * 번호 생성 때 이 조합들을 제외하기 위해 쓴다. (읽기 전용)
     * 📚 배운 내용: @Query (JPQL 직접 작성)
     */
    @Query("select c.comboKey from ComboIssueCount c where c.targetRoundNo = :round and c.issuedCount >= :limit")
    List<String> findKeysAtLimit(@Param("round") int round, @Param("limit") int limit);

    /**
     * (확정 발급용 1/2) 횟수 행이 없으면 0으로 만들어 둔다. 이미 있으면 아무 일도 하지 않는다.
     * 동시에 두 명이 같은 조합을 처음 확정해도 UNIQUE(target_round_no, combo_key) 때문에 행은 하나만 생긴다.
     * (MySQL 전용 문법이라 nativeQuery 로 쓴다)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT INTO combo_issue_count (target_round_no, combo_key, issued_count) "
            + "VALUES (:round, :key, 0) "
            + "ON DUPLICATE KEY UPDATE issued_count = issued_count", nativeQuery = true)
    void ensureRow(@Param("round") int round, @Param("key") String key);

    /**
     * (확정 발급용 2/2) "횟수가 limit 보다 작을 때만" 1 올린다. 올렸으면 1, 이미 찼으면 0 을 돌려준다.
     *
     * ⚠ 중요: 조회 후 코드에서 +1 하지 않고, 조건과 증가를 한 문장으로 처리한다.
     *   그래야 사람 두 명이 동시에 3번째 확정을 눌러도 DB 행 잠금 덕분에 한 명만 성공하고 나머지는 0 을 받는다.
     *   (요구사항: 같은 회차·같은 조합은 서비스 전체에서 최대 3번)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ComboIssueCount c set c.issuedCount = c.issuedCount + 1 "
            + "where c.targetRoundNo = :round and c.comboKey = :key and c.issuedCount < :limit")
    int incrementIfBelow(@Param("round") int round, @Param("key") String key, @Param("limit") int limit);

    /** 테스트·확인용 현재 횟수 */
    @Query("select c.issuedCount from ComboIssueCount c where c.targetRoundNo = :round and c.comboKey = :key")
    java.util.Optional<Integer> findCount(@Param("round") int round, @Param("key") String key);
}
