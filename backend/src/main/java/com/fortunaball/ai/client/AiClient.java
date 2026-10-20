package com.fortunaball.ai.client;

/**
 * AI 호출 창구. 분류기·답변기가 이 인터페이스만 보게 해서, 나중에 다른 회사 AI 로 바꾸기 쉽게 한다.
 * (기능명세서: "분류기 교체가 쉬운 인터페이스 구조")
 * 📚 배운 내용: 인터페이스 - 쓰는 쪽은 "무엇을 하는지"만 알고 "어떻게 하는지"는 구현체가 맡는다.
 *
 * 구현체는 실패하면 BusinessException(AI-005 등)을 던진다. 한도·횟수 검사와 사용 기록은 여기서 하지 않는다.
 * (그 일은 ai.AiGateway 가 맡는다)
 */
public interface AiClient {

    AiResponse complete(AiRequest request);
}
