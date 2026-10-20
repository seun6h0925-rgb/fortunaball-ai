package com.fortunaball.ai.policy;

import java.util.List;

/** 사용 중인 정책을 돌려주는 곳. 엔진이 DB 에 직접 묶이지 않게 분리해 두었다. (테스트에서는 람다로 대체한다) */
@FunctionalInterface
public interface AiPolicySource {

    List<AiPolicy> activePolicies();
}
