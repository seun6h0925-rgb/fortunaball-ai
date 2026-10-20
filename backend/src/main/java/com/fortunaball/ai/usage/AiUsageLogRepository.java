package com.fortunaball.ai.usage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 사용 기록 저장소. 한도 검사에 쓰는 "오늘 호출 수"와 "이번 달 비용"을 센다.
 * 📚 배운 내용: @Query(JPQL) - 엔티티 이름·필드 이름으로 쓰는 쿼리
 */
public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {

    /** since 이후 AI 를 실제로 부른 횟수. (모델 이름이 있는 SUCCESS·FAILED 만. BLOCKED 는 제외) */
    @Query("select count(l) from AiUsageLog l where l.createdAt >= :since and l.modelName is not null")
    long countCallsSince(@Param("since") LocalDateTime since);

    /** since 이후 추정 비용 합계(USD). 기록이 없으면 null (호출한 쪽에서 0 으로 본다) */
    @Query("select sum(l.estimatedCost) from AiUsageLog l where l.createdAt >= :since")
    BigDecimal sumCostSince(@Param("since") LocalDateTime since);
}
