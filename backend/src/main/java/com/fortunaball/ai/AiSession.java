package com.fortunaball.ai;

import com.fortunaball.ai.client.AiClient;
import com.fortunaball.ai.client.AiProperties;
import com.fortunaball.ai.client.AiRequest;
import com.fortunaball.ai.client.AiResponse;
import com.fortunaball.ai.usage.AiCostCalculator;
import com.fortunaball.ai.usage.AiFeature;
import com.fortunaball.ai.usage.AiPermit;
import com.fortunaball.ai.usage.AiUsageGuard;
import com.fortunaball.ai.usage.AiUsageRecorder;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;

/**
 * 한도 검사를 통과한 사용자 요청 1건 안에서 AI 를 부르는 도구. (AiGateway.open() 이 만들어 준다)
 * 한 요청 안에서 AI 를 두 번 불러도(분류 → 답변) 사용자 횟수는 한 번만 쓴다. 호출 한 번마다 사용 기록이 한 줄 남는다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 실패해도 기록을 남기고(FAILED) 같은 오류를 그대로 다시 던진다. 호출한 쪽은 BusinessException(AI-005)만 처리하면 된다.
 *  - 실패하면 차감했던 무료 횟수를 돌려준다. (한 요청에서 여러 번 실패해도 한 번만 돌려준다 - AiPermit)
 *  - 성공 기록의 비용은 "추정값"이다. (AiCostCalculator)
 *  - 질문·답변 원문은 어디에도 남기지 않는다. 기록에는 모델 이름·토큰 수·시간·비용만 들어간다.
 */
public final class AiSession {

    private final AiFeature feature;
    private final AiClient client;
    private final AiProperties props;
    private final AiUsageRecorder recorder;
    private final AiCostCalculator costCalculator;
    private final AiUsageGuard guard;
    private final AiPermit permit;

    AiSession(AiFeature feature, AiClient client, AiProperties props,
              AiUsageRecorder recorder, AiCostCalculator costCalculator,
              AiUsageGuard guard, AiPermit permit) {
        this.feature = feature;
        this.client = client;
        this.props = props;
        this.recorder = recorder;
        this.costCalculator = costCalculator;
        this.guard = guard;
        this.permit = permit;
    }

    /** AI 를 부르고 결과를 기록한다. 실패하면 BusinessException(AI-005, 503) */
    public AiResponse call(AiRequest request) {
        long start = System.nanoTime();
        try {
            AiResponse response = client.complete(request);
            recorder.recordSuccess(feature, response.modelName(), response.inputTokens(), response.outputTokens(),
                    elapsedMillis(start),
                    costCalculator.estimate(request.role(), response.inputTokens(), response.outputTokens()));
            return response;
        } catch (BusinessException e) {
            recorder.recordFailed(feature, props.modelFor(request.role()), elapsedMillis(start),
                    e.getDetail() != null ? e.getDetail() : e.getMessage());
            guard.refund(permit);   // 실패는 사용자 잘못이 아니므로 무료 횟수를 돌려준다
            throw e;
        } catch (RuntimeException e) {
            // 예상하지 못한 오류도 AI 실패로 통일한다. (원인 종류만 남기고 내용은 남기지 않는다)
            String reason = "예상하지 못한 오류: " + e.getClass().getSimpleName();
            recorder.recordFailed(feature, props.modelFor(request.role()), elapsedMillis(start), reason);
            guard.refund(permit);
            throw new BusinessException(ErrorCode.AI_CALL_FAILED, ErrorCode.AI_CALL_FAILED.getDefaultMessage(), reason);
        }
    }

    private static long elapsedMillis(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000;
    }
}
