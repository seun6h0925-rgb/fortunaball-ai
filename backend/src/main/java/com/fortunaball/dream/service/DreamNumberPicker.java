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
 * 꿈에서 나온 번호를 모두 모은다. AI 는 번호에 관여하지 않는다. 같은 꿈이면 항상 같은 결과가 나온다.
 *
 *  - 꿈 속에 직접 적힌 숫자 (한국 전통: 꿈에 나온 숫자를 그대로 쓴다)
 *  - 꿈에서 찾은 모든 상징의 추천 번호 (같은 번호는 한 번만. 전통 번호가 서비스 매핑보다 앞선다)
 *
 * ⚠ 개수 제한을 두지 않는다. 6개를 넘으면 DreamService 가 사용자에게 어떻게 할지 묻는다.
 *   (몇 게임으로 나눠 담을지 / 통계로 6개만 고를지 / 뺄 번호가 있는지)
 * 6개보다 모자라면 통계로 채우는데, 그건 여기서 하지 않는다.
 * DreamService 가 번호 생성 기능(GenerateService)에 "이 번호는 포함"이라고 맡겨서 채운다.
 * (그래야 다시 생성할 때마다 통계 번호만 새로 바뀌고, 확정 토큰도 함께 받는다)
 *
 * 결과는 번호 오름차순으로 정렬하고, 번호마다 출처(origin)를 붙인다.
 */
@Component
public class DreamNumberPicker {

    /** 한 게임에 담을 수 있는 번호 개수 */
    static final int TARGET = 6;

    /**
     * @param dreamNumbers  꿈 속 숫자 (1~45, 중복 없음)
     * @param symbolNumbers 상징 순서대로 정렬된, 상징별 번호 목록 (바깥 리스트 = 상징, 안쪽 = 그 상징의 번호)
     */
    public List<PickedNumber> pick(List<Integer> dreamNumbers, List<List<SymbolNumber>> symbolNumbers) {
        List<PickedNumber> picked = new ArrayList<>();
        Set<Integer> used = new HashSet<>();

        for (int n : dreamNumbers) {
            if (used.add(n)) {
                picked.add(new PickedNumber(n, DreamNumberOrigin.DREAM_TEXT));
            }
        }
        for (List<SymbolNumber> list : symbolNumbers) {
            for (SymbolNumber sn : list) {
                if (used.add(sn.number())) {
                    picked.add(new PickedNumber(sn.number(), sn.origin()));
                }
            }
        }

        picked.sort(Comparator.comparingInt(PickedNumber::number));
        return picked;
    }
}
