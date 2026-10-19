package com.fortunaball.issue.repository;

import com.fortunaball.issue.domain.ComboIssueCount;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
