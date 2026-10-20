package com.fortunaball.ai.policy;

import com.fortunaball.ai.usage.AiFeature;

/**
 * 정책 판별에 필요한 요청 정보.
 *
 *  feature      어느 기능의 요청인지 (ANALYST / DREAM)
 *  text         사용자가 입력한 문장 (저장하지 않는다. 판별에만 쓴다)
 *  mapContext   지도 화면의 AI 질문인지
 *  hasLocation  위치(위도·경도)가 요청에 함께 왔는지 (값 자체는 받지 않는다)
 *  hasSymbols   꿈풀이에서 꿈 상징을 찾았는지 (꿈풀이가 아니면 false)
 */
public record PolicyContext(AiFeature feature, String text, boolean mapContext, boolean hasLocation, boolean hasSymbols) {

    public static PolicyContext dream(String text, boolean hasSymbols) {
        return new PolicyContext(AiFeature.DREAM, text, false, false, hasSymbols);
    }

    public static PolicyContext analyst(String text, boolean mapContext, boolean hasLocation) {
        return new PolicyContext(AiFeature.ANALYST, text, mapContext, hasLocation, false);
    }
}
