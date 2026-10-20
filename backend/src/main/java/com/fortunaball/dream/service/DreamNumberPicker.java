package com.fortunaball.dream.service;

import com.fortunaball.dream.domain.DreamKnowledge.PickedNumber;
import com.fortunaball.dream.domain.DreamKnowledge.SymbolNumber;
import com.fortunaball.dream.domain.DreamNumberOrigin;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 추천 번호를 최대 6개 고른다. AI 는 번호에 관여하지 않는다. 같은 꿈이면 항상 같은 결과가 나온다.
 *
 *  1순위 꿈 속에 직접 적힌 숫자 (한국 전통)
 *  2순위 상징별 추천 번호를 상징 순서대로 한 개씩 번갈아 가며 (한 상징이 번호를 독점하지 않게)
 *  3순위 번호가 6개보다 적고 사용자가 동의했을 때만 통계 번호(최근 100회 출현 빈도 순)로 보완
 *
 * 결과는 번호 오름차순으로 정렬하고, 번호마다 출처(origin)를 붙인다.
 */
@Component
public class DreamNumberPicker {

    static final int TARGET = 6;

    /**
     * @param dreamNumbers   꿈 속 숫자 (1~45, 중복 없음)
     * @param symbolNumbers  상징 순서대로 정렬된, 상징별 번호 목록 (바깥 리스트 = 상징, 안쪽 = 그 상징의 번호)
     * @param supplement     통계 보완 동의 여부
     * @param statNumbers    통계 번호 후보(출현 빈도 높은 순). 동의하지 않았으면 비워서 넘겨도 된다.
     */
    public List<PickedNumber> pick(List<Integer> dreamNumbers, List<List<SymbolNumber>> symbolNumbers,
                                   boolean supplement, List<Integer> statNumbers) {
        List<PickedNumber> picked = new ArrayList<>();
        Set<Integer> used = new HashSet<>();

        for (int n : dreamNumbers) {
            if (picked.size() >= TARGET) {
                break;
            }
            if (used.add(n)) {
                picked.add(new PickedNumber(n, DreamNumberOrigin.DREAM_TEXT));
            }
        }

        int round = 0;
        boolean any = true;
        while (picked.size() < TARGET && any) {
            any = false;
            for (List<SymbolNumber> list : symbolNumbers) {
                if (round < list.size()) {
                    any = true;
                    SymbolNumber sn = list.get(round);
                    if (picked.size() < TARGET && used.add(sn.number())) {
                        picked.add(new PickedNumber(sn.number(), sn.origin()));
                    }
                }
            }
            round++;
        }

        if (supplement && statNumbers != null) {
            for (int n : statNumbers) {
                if (picked.size() >= TARGET) {
                    break;
                }
                if (used.add(n)) {
                    picked.add(new PickedNumber(n, DreamNumberOrigin.STAT));
                }
            }
        }

        picked.sort(Comparator.comparingInt(PickedNumber::number));
        return picked;
    }

    /** 통계로 보완하지 않은 번호가 6개 이상이면 FULL 로 본다. */
    public boolean isComplete(List<PickedNumber> picked) {
        long nonStat = picked.stream().filter(p -> p.origin() != DreamNumberOrigin.STAT).count();
        return nonStat >= TARGET;
    }
}
