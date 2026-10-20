package com.fortunaball.ai.usage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 사용 기록 만들기 확인: 서울 시간으로 기록하고, 저장이 실패해도 예외를 밖으로 내보내지 않는다. */
@ExtendWith(MockitoExtension.class)
class AiUsageRecorderTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 11, 0);

    @Mock
    private AiUsageLogRepository repository;
    @Mock
    private PlatformTransactionManager txManager;

    private AiUsageRecorder recorder;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(ZonedDateTime.of(NOW, SEOUL).toInstant(), SEOUL);
        recorder = new AiUsageRecorder(repository, txManager, clock);
    }

    private AiUsageLog saved() {
        ArgumentCaptor<AiUsageLog> captor = ArgumentCaptor.forClass(AiUsageLog.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void 성공_기록은_모델_토큰_비용을_담는다() {
        recorder.recordSuccess(AiFeature.DREAM, "gpt-6-luna", 100, 50, 850, new BigDecimal("0.000035"));

        AiUsageLog log = saved();
        assertEquals(AiRequestStatus.SUCCESS, log.getRequestStatus());
        assertEquals(AiFeature.DREAM, log.getFeatureType());
        assertEquals("gpt-6-luna", log.getModelName());
        assertEquals(100, log.getInputTokenCount());
        assertEquals(50, log.getOutputTokenCount());
        assertEquals(850, log.getResponseTimeMs());
        assertEquals(new BigDecimal("0.000035"), log.getEstimatedCost());
        assertEquals(NOW, log.getCreatedAt());   // 서울 시간
    }

    @Test
    void 실패_기록은_사유를_담고_비용은_0이다() {
        recorder.recordFailed(AiFeature.ANALYST, "gpt-6-luna", 20_000, "HTTP 500");

        AiUsageLog log = saved();
        assertEquals(AiRequestStatus.FAILED, log.getRequestStatus());
        assertEquals("HTTP 500", log.getErrorMessage());
        assertEquals(BigDecimal.ZERO, log.getEstimatedCost());
        assertEquals("gpt-6-luna", log.getModelName());   // 모델 이름이 있어야 "호출 1회"로 센다
    }

    @Test
    void 차단_기록은_모델_이름이_없다() {
        recorder.recordBlocked(AiFeature.ANALYST, "OFF_TOPIC");

        AiUsageLog log = saved();
        assertEquals(AiRequestStatus.BLOCKED, log.getRequestStatus());
        assertEquals("OFF_TOPIC", log.getPolicyCode());
        assertNull(log.getModelName());               // 호출 수·비용에 들어가지 않는다
        assertEquals(0, log.getInputTokenCount());
    }

    @Test
    void 저장이_실패해도_예외를_밖으로_내보내지_않는다() {
        when(repository.save(any())).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() -> recorder.recordBlocked(AiFeature.DREAM, "AI_DAILY_LIMIT"));
    }
}
