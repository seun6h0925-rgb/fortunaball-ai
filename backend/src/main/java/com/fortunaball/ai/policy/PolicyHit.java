package com.fortunaball.ai.policy;

/** 걸린 정책. message 를 그대로 사용자에게 보여 주고, AI 는 부르지 않는다. */
public record PolicyHit(String policyCode, String policyType, String message) {
}
