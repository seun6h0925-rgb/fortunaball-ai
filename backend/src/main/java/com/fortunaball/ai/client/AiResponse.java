package com.fortunaball.ai.client;

/**
 * AI 가 돌려준 결과 한 건.
 *
 * @param text         답변 글
 * @param modelName    실제로 응답한 모델 이름 (사용 기록에 남긴다)
 * @param inputTokens  입력 토큰 수
 * @param outputTokens 출력 토큰 수
 */
public record AiResponse(String text, String modelName, int inputTokens, int outputTokens) {
}
