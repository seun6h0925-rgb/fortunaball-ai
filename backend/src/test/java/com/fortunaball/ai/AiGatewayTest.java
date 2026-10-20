package com.fortunaball.ai;

import com.fortunaball.ai.client.AiClient;
import com.fortunaball.ai.client.AiProperties;
import com.fortunaball.ai.client.AiRequest;
import com.fortunaball.ai.client.AiResponse;
import com.fortunaball.ai.client.AiRole;
import com.fortunaball.ai.usage.AiCostCalculator;
import com.fortunaball.ai.usage.AiFeature;
import com.fortunaball.ai.usage.AiUsageGuard;
import com.fortunaball.ai.usage.AiUsageRecorder;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AI 호출 창구 확인: 한도 검사 → 호출 → 기록 연결이 맞는지.
 * 핵심: 성공은 비용과 함께, 실패는 사유와 함께 기록하고, 실패 오류는 그대로(또는 AI-005 로) 다시 던진다.
 */
@ExtendWith(MockitoExtension.class)
class AiGatewayTest {

    private static final AiRequest REQUEST = new AiRequest(AiRole.ANSWER, "지시", "질문", 100);

    @Mock
    private AiClient client;
    @Mock
    private AiUsageGuard guard;
    @Mock
    private AiUsageRecorder recorder;

    private AiGateway gateway;

    @BeforeEach
    void setUp() {
        AiProperties props = AiTestFixtures.props("k");
        gateway = new AiGateway(client, props, guard, recorder, new AiCostCalculator(props));
    }

    @Test
    void 관리자_여부는_한도_검사기에_그대로_전달한다() {
        gateway.open(AiFeature.DREAM, "g", "ip", true);

        verify(guard).check(AiFeature.DREAM, "g", "ip", true);
    }

    @Test
    void 일반_사용자용_open은_관리자가_아니다() {
        gateway.open(AiFeature.DREAM, "g", "ip");

        verify(guard).check(AiFeature.DREAM, "g", "ip", false);
    }

    @Test
    void 한도에_걸리면_세션을_주지_않고_오류를_그대로_던진다() {
        BusinessException limit = new BusinessException(ErrorCode.AI_USER_LIMIT);
        doThrow(limit).when(guard).check(AiFeature.DREAM, "g", "ip", false);

        BusinessException e = assertThrows(BusinessException.class, () -> gateway.open(AiFeature.DREAM, "g", "ip"));

        assertSame(limit, e);
        verifyNoInteractions(client);
    }

    @Test
    void 성공하면_비용과_함께_성공_기록을_남긴다() {
        when(client.complete(REQUEST)).thenReturn(new AiResponse("답변", "gpt-6-luna", 1000, 200));

        AiResponse response = gateway.open(AiFeature.DREAM, "g", "ip").call(REQUEST);

        assertEquals("답변", response.text());
        // 입력 1000 × 0.10 + 출력 200 × 0.50 = 0.000200
        verify(recorder).recordSuccess(eq(AiFeature.DREAM), eq("gpt-6-luna"), eq(1000), eq(200), anyLong(),
                eq(new BigDecimal("0.000200")));
    }

    @Test
    void 한_요청_안에서_두_번_불러도_한도_검사는_한_번이다() {
        when(client.complete(REQUEST)).thenReturn(new AiResponse("답", "m", 1, 1));

        AiSession session = gateway.open(AiFeature.ANALYST, "g", "ip");
        session.call(REQUEST);
        session.call(REQUEST);

        verify(guard).check(AiFeature.ANALYST, "g", "ip", false);   // 정확히 1번
    }

    @Test
    void AI_실패는_실패_기록을_남기고_같은_오류를_다시_던진다() {
        BusinessException failure = new BusinessException(ErrorCode.AI_CALL_FAILED, "msg", "HTTP 500");
        when(client.complete(REQUEST)).thenThrow(failure);

        BusinessException e = assertThrows(BusinessException.class,
                () -> gateway.open(AiFeature.DREAM, "g", "ip").call(REQUEST));

        assertSame(failure, e);
        verify(recorder).recordFailed(eq(AiFeature.DREAM), eq("gpt-6-luna"), anyLong(), eq("HTTP 500"));
        verify(recorder, never()).recordSuccess(eq(AiFeature.DREAM), eq("gpt-6-luna"), eq(0), eq(0), anyLong(),
                eq(BigDecimal.ZERO));
    }

    @Test
    void 예상하지_못한_오류도_AI_005로_바꾸고_내용은_기록하지_않는다() {
        when(client.complete(REQUEST)).thenThrow(new IllegalStateException("질문 내용이 들어간 메시지"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> gateway.open(AiFeature.DREAM, "g", "ip").call(REQUEST));

        assertEquals(ErrorCode.AI_CALL_FAILED, e.getErrorCode());
        assertTrue(e.getDetail().contains("IllegalStateException"));
        assertTrue(!e.getDetail().contains("질문 내용"));   // 예외 메시지는 상세·기록에 넣지 않는다
        verify(recorder).recordFailed(eq(AiFeature.DREAM), eq("gpt-6-luna"), anyLong(),
                contains("IllegalStateException"));
    }

    @Test
    void 정책_고정_응답은_세션_없이_차단으로만_기록한다() {
        gateway.recordBlocked(AiFeature.ANALYST, "GUARANTEE_REQUEST");

        verify(recorder).recordBlocked(AiFeature.ANALYST, "GUARANTEE_REQUEST");
        verifyNoInteractions(guard, client);
    }
}
