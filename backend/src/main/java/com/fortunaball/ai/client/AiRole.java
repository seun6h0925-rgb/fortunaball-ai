package com.fortunaball.ai.client;

/**
 * AI 호출의 "역할". 역할마다 쓰는 모델과 가격이 다르다. (application.yml 의 fortuna.ai.*)
 *  - CLASSIFIER: 애매한 질문을 분류하는 저비용 호출 (결과가 짧다)
 *  - ANSWER: 사용자에게 보여 줄 설명 문장을 만드는 본 호출
 */
public enum AiRole {
    CLASSIFIER,
    ANSWER
}
