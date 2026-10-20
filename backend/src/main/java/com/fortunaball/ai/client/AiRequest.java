package com.fortunaball.ai.client;

/**
 * AI 에 보내는 요청 한 건. (회사·모델에 관계없이 같은 모양으로 쓴다)
 *
 * @param role            분류용인지 답변용인지 (쓸 모델과 가격이 이 값으로 정해진다)
 * @param systemPrompt    AI 의 역할·금지 규칙을 적은 지시문
 * @param userPrompt      사용자 질문과 서버가 모은 통계 근거
 * @param maxOutputTokens 답변 길이 상한(토큰). 비용을 막는 안전장치라 반드시 넣는다.
 */
public record AiRequest(AiRole role, String systemPrompt, String userPrompt, int maxOutputTokens) {

    public AiRequest {
        if (role == null) {
            throw new IllegalArgumentException("AI 역할이 필요합니다.");
        }
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException("AI 에 보낼 내용이 비어 있습니다.");
        }
        if (maxOutputTokens < 1) {
            throw new IllegalArgumentException("답변 길이 상한은 1 이상이어야 합니다: " + maxOutputTokens);
        }
        if (systemPrompt == null) {
            systemPrompt = "";
        }
    }
}
