package com.fortunaball.ai.usage;

/**
 * AI 를 쓰는 기능 구분. DB ai_usage_log.feature_type 값과 같은 이름이다. (CHECK 제약: ANALYST / DREAM)
 */
public enum AiFeature {
    /** 2번 AI 로또 애널리스트 */
    ANALYST,
    /** 3번 AI 로또 꿈풀이 */
    DREAM
}
