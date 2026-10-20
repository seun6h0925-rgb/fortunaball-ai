package com.fortunaball.ai.usage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 실제 MySQL 에 연결해서 확인하는 저장소 테스트. (끝나면 자동 롤백, DB 에 남지 않는다)
 * 핵심: "오늘 호출 수"와 "이번 달 비용" 집계가 BLOCKED 를 빼고 정확히 계산되는지, 실제 테이블 제약(CHECK)에 맞게 저장되는지.
 *
 * 실제 운영 기록과 섞이지 않게, 테스트 기록은 먼 미래 날짜(2998년)로 만들고 그 기준으로만 센다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AiUsageLogRepositoryTest {

    /** 집계 시작 기준. 이보다 이른 기록은 세지 않는다. */
    private static final LocalDateTime SINCE = LocalDateTime.of(2998, 12, 31, 0, 0);
    private static final LocalDateTime INSIDE = LocalDateTime.of(2998, 12, 31, 12, 0);
    private static final LocalDateTime BEFORE = LocalDateTime.of(2998, 12, 30, 12, 0);

    @Autowired
    private AiUsageLogRepository repo;

    @Test
    void 성공과_실패만_호출로_세고_차단은_세지_않는다() {
        repo.save(AiUsageLog.success(AiFeature.DREAM, "gpt-6-luna", 100, 50, 800, new BigDecimal("0.000200"), INSIDE));
        repo.save(AiUsageLog.failed(AiFeature.ANALYST, "gpt-6-luna", 20_000, "HTTP 500", INSIDE));
        repo.save(AiUsageLog.blocked(AiFeature.ANALYST, "OFF_TOPIC", INSIDE));
        repo.save(AiUsageLog.blocked(AiFeature.DREAM, "AI_USER_LIMIT", INSIDE));

        assertEquals(2, repo.countCallsSince(SINCE));
    }

    @Test
    void 기준_시각_이전_기록은_세지_않는다() {
        repo.save(AiUsageLog.success(AiFeature.DREAM, "gpt-6-luna", 1, 1, 10, new BigDecimal("0.000100"), BEFORE));
        repo.save(AiUsageLog.success(AiFeature.DREAM, "gpt-6-luna", 1, 1, 10, new BigDecimal("0.000300"), INSIDE));

        assertEquals(1, repo.countCallsSince(SINCE));
        assertEquals(0, new BigDecimal("0.000300").compareTo(repo.sumCostSince(SINCE)));
    }

    @Test
    void 비용_합계는_성공과_실패_비용을_더한다() {
        repo.save(AiUsageLog.success(AiFeature.DREAM, "gpt-6-luna", 1, 1, 10, new BigDecimal("0.000200"), INSIDE));
        repo.save(AiUsageLog.success(AiFeature.ANALYST, "gpt-6-luna", 1, 1, 10, new BigDecimal("1.500000"), INSIDE));
        repo.save(AiUsageLog.failed(AiFeature.ANALYST, "gpt-6-luna", 10, "x", INSIDE));   // 실패는 비용 0
        repo.save(AiUsageLog.blocked(AiFeature.ANALYST, "OFF_TOPIC", INSIDE));            // 차단은 비용 없음(NULL)

        assertEquals(0, new BigDecimal("1.500200").compareTo(repo.sumCostSince(SINCE)));
    }

    @Test
    void 기록이_없으면_비용_합계는_null이고_호출_수는_0이다() {
        assertNull(repo.sumCostSince(SINCE));   // 한도 검사기가 null 을 0 으로 본다
        assertEquals(0, repo.countCallsSince(SINCE));
    }

    @Test
    void 저장한_값이_그대로_읽힌다() {
        AiUsageLog saved = repo.saveAndFlush(
                AiUsageLog.success(AiFeature.DREAM, "gpt-6-luna", 123, 45, 900, new BigDecimal("0.000035"), INSIDE));

        AiUsageLog found = repo.findById(saved.getId()).orElseThrow();
        assertEquals(AiFeature.DREAM, found.getFeatureType());
        assertEquals(AiRequestStatus.SUCCESS, found.getRequestStatus());
        assertEquals(123, found.getInputTokenCount());
        assertEquals(45, found.getOutputTokenCount());
        assertEquals(900, found.getResponseTimeMs());
        assertEquals(INSIDE, found.getCreatedAt());
    }
}
