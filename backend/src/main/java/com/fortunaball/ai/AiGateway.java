package com.fortunaball.ai;

import com.fortunaball.admin.auth.service.AdminAccess;
import com.fortunaball.ai.client.AiClient;
import com.fortunaball.ai.client.AiProperties;
import com.fortunaball.ai.usage.AiCostCalculator;
import com.fortunaball.ai.usage.AiFeature;
import com.fortunaball.ai.usage.AiPermit;
import com.fortunaball.ai.usage.AiUsageGuard;
import com.fortunaball.ai.usage.AiUsageRecorder;
import com.fortunaball.ai.usage.AiUsageSnapshot;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * AI 를 쓰는 모든 기능(애널리스트·꿈풀이)이 거치는 단 하나의 문.
 * 한도 검사 → AI 호출 → 사용 기록을 여기서 한 번에 처리해서, 기능마다 비용 관리 코드를 따로 만들지 않는다.
 *
 * 쓰는 법 (기능 쪽 코드)
 *   AiSession session = aiGateway.open(AiFeature.DREAM, guestToken.value(), clientIp);  // 한도 초과면 여기서 오류
 *   (관리자는 로그인 세션으로 서버가 직접 알아본다. 로그인한 관리자의 요청이면 한도 검사 없이 통과한다)
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

    /**
     * 한도를 검사하고 AI 호출 도구를 돌려준다. 안 되면 BusinessException (AI-001~004, COMMON-007)
     * 로그인한 관리자의 요청이면 한도를 면제한다. (관리자 여부는 세션으로만 판단하고, 사용자가 보낸 값은 믿지 않는다)
     */
    public AiSession open(AiFeature feature, String guestKey, String clientIp) {
        return open(feature, guestKey, clientIp, AdminAccess.isAdmin());
    }

    /**
     * @param admin 관리자 요청이면 true. 사용자별·접속지·분당·서비스 전체 한도를 모두 면제한다. (사용 기록은 남긴다)
     *              관리자 여부는 호출하는 쪽이 관리자 로그인으로 판단해서 넘긴다. 사용자가 보낸 값을 그대로 믿으면 안 된다.
     *              (3개 인자 버전은 AdminAccess 로 직접 판단해서 이 값을 넘긴다)
     */
    public AiSession open(AiFeature feature, String guestKey, String clientIp, boolean admin) {
        AiPermit permit = guard.check(feature, guestKey, clientIp, admin);
        return new AiSession(feature, client, props, recorder, costCalculator, guard, permit);
    }

    /** 오늘 남은 무료 횟수를 알려 준다. (횟수를 쓰지 않는다. 화면 오른쪽 위 표시용) */
    public AiUsageSnapshot usage(String guestKey, String clientIp, boolean admin) {
        return guard.snapshot(guestKey, clientIp, admin || AdminAccess.isAdmin());
    }

    /** AI 를 부르지 않고 정책 고정 응답으로 끝낸 요청을 기록한다. (예: OFF_TOPIC, GUARANTEE_REQUEST) */
    public void recordBlocked(AiFeature feature, String policyCode) {
        recorder.recordBlocked(feature, policyCode);
    }
}
