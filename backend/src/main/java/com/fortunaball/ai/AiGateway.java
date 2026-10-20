package com.fortunaball.ai;

import com.fortunaball.ai.client.AiClient;
import com.fortunaball.ai.client.AiProperties;
import com.fortunaball.ai.usage.AiCostCalculator;
import com.fortunaball.ai.usage.AiFeature;
import com.fortunaball.ai.usage.AiUsageGuard;
import com.fortunaball.ai.usage.AiUsageRecorder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * AI 를 쓰는 모든 기능(애널리스트·꿈풀이)이 거치는 단 하나의 문.
 * 한도 검사 → AI 호출 → 사용 기록을 여기서 한 번에 처리해서, 기능마다 비용 관리 코드를 따로 만들지 않는다.
 *
 * 쓰는 법 (기능 쪽 코드)
 *   AiSession session = aiGateway.open(AiFeature.DREAM, guestToken.value(), clientIp);  // 한도 초과면 여기서 오류
 *   (관리자 요청이면 마지막 인자에 true 를 넘겨 4개 인자 버전을 쓴다)
 *   AiResponse answer = session.call(new AiRequest(AiRole.ANSWER, 지시문, 질문과_근거, 최대토큰));
 *
 * AI 를 부르지 않고 고정 응답으로 끝낸 경우(정책 차단)에는 open() 을 부르지 말고 recordBlocked() 만 부른다.
 * (그러면 사용자 무료 횟수를 쓰지 않는다)
 * 📚 배운 내용: @Service, 생성자 주입
 */
@Service
@EnableConfigurationProperties(AiProperties.class)
public class AiGateway {

    private final AiClient client;
    private final AiProperties props;
    private final AiUsageGuard guard;
    private final AiUsageRecorder recorder;
    private final AiCostCalculator costCalculator;

    public AiGateway(AiClient client, AiProperties props, AiUsageGuard guard,
                     AiUsageRecorder recorder, AiCostCalculator costCalculator) {
        this.client = client;
        this.props = props;
        this.guard = guard;
        this.recorder = recorder;
        this.costCalculator = costCalculator;
    }

    /** 일반 사용자용. 한도를 검사하고 AI 호출 도구를 돌려준다. 안 되면 BusinessException (AI-001~004, COMMON-007) */
    public AiSession open(AiFeature feature, String guestKey, String clientIp) {
        return open(feature, guestKey, clientIp, false);
    }

    /**
     * @param admin 관리자 요청이면 true. 사용자별·접속지·분당·서비스 전체 한도를 모두 면제한다. (사용 기록은 남긴다)
     *              관리자 여부는 호출하는 쪽이 관리자 로그인으로 판단해서 넘긴다. 사용자가 보낸 값을 그대로 믿으면 안 된다.
     */
    public AiSession open(AiFeature feature, String guestKey, String clientIp, boolean admin) {
        guard.check(feature, guestKey, clientIp, admin);
        return new AiSession(feature, client, props, recorder, costCalculator);
    }

    /** AI 를 부르지 않고 정책 고정 응답으로 끝낸 요청을 기록한다. (예: OFF_TOPIC, GUARANTEE_REQUEST) */
    public void recordBlocked(AiFeature feature, String policyCode) {
        recorder.recordBlocked(feature, policyCode);
    }
}
