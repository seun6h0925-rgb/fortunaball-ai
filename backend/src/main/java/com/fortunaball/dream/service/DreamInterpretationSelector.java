package com.fortunaball.dream.service;

import com.fortunaball.dream.domain.DreamKnowledge.Interpretation;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 상징 하나에 쓸 해석을 고른다.
 *
 *  1) 한국 전통 해석(KR)이 있으면 KR 만 쓰고, 없을 때만 해외 자료(GLOBAL)를 쓴다.  (확정 결정: 한국 해석 1순위)
 *  2) 꿈 내용에 해석의 상황 단어("집에 들어옴" 등)가 그대로 들어 있으면 그 해석을 먼저 쓴다. 없으면 앞의 2개를 쓴다.
 *  3) 한 상징에 최대 MAX_PER_SYMBOL 개까지.
 */
@Component
public class DreamInterpretationSelector {

    static final int MAX_PER_SYMBOL = 3;
    private static final int DEFAULT_COUNT = 2;

    public List<Interpretation> select(String dreamText, List<Interpretation> ofSymbol) {
        if (ofSymbol == null || ofSymbol.isEmpty()) {
            return List.of();
        }
        List<Interpretation> korean = new ArrayList<>();
        for (Interpretation i : ofSymbol) {
            if (i.isKorean()) {
                korean.add(i);
            }
        }
        List<Interpretation> pool = korean.isEmpty() ? ofSymbol : korean;

        List<Interpretation> bySituation = new ArrayList<>();
        for (Interpretation i : pool) {
            if (dreamText != null && i.situation() != null && !i.situation().isBlank()
                    && dreamText.contains(i.situation())) {
                bySituation.add(i);
            }
        }
        List<Interpretation> chosen = bySituation.isEmpty()
                ? pool.subList(0, Math.min(DEFAULT_COUNT, pool.size()))
                : bySituation;
        return new ArrayList<>(chosen.subList(0, Math.min(MAX_PER_SYMBOL, chosen.size())));
    }
}
