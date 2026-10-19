package com.fortunaball.generate.service;

import com.fortunaball.generate.domain.Combo;
import com.fortunaball.generate.dto.PatternCondition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * 번호 생성 알고리즘의 중심. 게임 수만큼 조합을 만들어 돌려준다. (DB·캐시를 쓰지 않는 계산 전용 클래스)
 * 📚 배운 내용: @Component (스프링이 객체를 만들어 필요한 곳에 넣어 주는 일반 부품)
 *
 * 게임 하나를 뽑는 순서
 *  1) 패턴 조건을 만족하는 후보를 CANDIDATES_PER_GAME 개 만든다 (이미 3번 찼거나 이번에 이미 뽑은 조합은 제외)
 *  2) 공통 검증(DrawProfile)을 통과하고 쏠림 감점이 없는 후보를 우선 쓴다.
 *     그런 후보가 MIN_VALID_CANDIDATES 개보다 적으면 "쏠림 감점"을 허용하고, 그래도 적으면 "공통 검증"도 허용한다.
 *     (패턴 조건과 "이미 찬 조합 제외"는 어떤 경우에도 완화하지 않는다)
 *  3) 점수 순으로 줄 세워 상위 TOP_POOL_SIZE 개 중 하나를 랜덤으로 뽑는다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - 난수는 RandomGenerator 로 받는다. 서비스에서는 기본 난수를, 테스트에서는 씨앗(seed)을 준 난수를 넣어 결과를 똑같이 재현한다.
 *  - 한 번 요청 안에서 같은 조합이 두 번 나오지 않는다. (excludedKeys 에 방금 뽑은 조합을 계속 더해 간다)
 */
@Component
public class NumberGenerator {

    /** 서비스에서 쓰는 기본 버전 */
    public List<Combo> generate(PatternCondition cond, int gameCount, DrawProfile profile, Set<String> excludedKeys) {
        return generate(cond, gameCount, profile, excludedKeys, ThreadLocalRandom.current());
    }

    /**
     * @param excludedKeys 제외할 조합 key 들 (이 대상 회차에서 이미 3번 확정 발급된 조합)
     */
    public List<Combo> generate(PatternCondition cond, int gameCount, DrawProfile profile,
                                Set<String> excludedKeys, RandomGenerator rng) {
        if (gameCount < 1) {
            throw new IllegalArgumentException("게임 수는 1 이상이어야 합니다: " + gameCount);
        }
        Set<String> used = new HashSet<>(excludedKeys == null ? Set.of() : excludedKeys);
        List<Combo> result = new ArrayList<>();
        for (int i = 0; i < gameCount; i++) {
            Combo picked = pickOne(cond, profile, used, rng);
            result.add(picked);
            used.add(picked.key());
        }
        return result;
    }

    private Combo pickOne(PatternCondition cond, DrawProfile profile, Set<String> used, RandomGenerator rng) {
        for (int round = 0; round < GenerationRule.MAX_SAMPLING_ROUNDS; round++) {
            Map<String, Combo> pool = new LinkedHashMap<>();
            for (int i = 0; i < GenerationRule.CANDIDATES_PER_GAME; i++) {
                Combo c = CandidateFactory.next(cond, rng);
                if (!used.contains(c.key())) {
                    pool.putIfAbsent(c.key(), c);
                }
            }
            List<Combo> base = chooseBase(new ArrayList<>(pool.values()), profile);
            if (base.isEmpty()) {
                continue;
            }
            Collections.shuffle(base, rng);   // 점수가 같은 후보 사이의 순서를 섞는다
            base.sort((a, b) -> Integer.compare(ComboScorer.score(b, profile), ComboScorer.score(a, profile)));
            int top = Math.min(GenerationRule.TOP_POOL_SIZE, base.size());
            return base.get(rng.nextInt(top));
        }
        throw new IllegalStateException("조건에 맞는 번호를 만들지 못했습니다 (후보 부족)");
    }

    /** 검증을 단계적으로 완화하며 쓸 후보 목록을 정한다 */
    private List<Combo> chooseBase(List<Combo> candidates, DrawProfile profile) {
        List<Combo> strict = new ArrayList<>();
        List<Combo> noProfileSkew = new ArrayList<>();
        for (Combo c : candidates) {
            if (profile.accepts(c)) {
                noProfileSkew.add(c);
                if (ComboScorer.penalty(c) == 0) {
                    strict.add(c);
                }
            }
        }
        if (strict.size() >= GenerationRule.MIN_VALID_CANDIDATES) {
            return strict;
        }
        if (noProfileSkew.size() >= GenerationRule.MIN_VALID_CANDIDATES) {
            return noProfileSkew;
        }
        return candidates;
    }
}
