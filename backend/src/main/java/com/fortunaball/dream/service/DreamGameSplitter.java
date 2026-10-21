package com.fortunaball.dream.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 꿈 상징 번호를 여러 게임에 나눠 담는다. (통계로 줄 세운 순서대로 돌아가며 한 장씩 나눠 주는 방식)
 *
 * 예) 번호 9개(많이 나온 순 a~i), 3게임 → 1게임 a·d·g / 2게임 b·e·h / 3게임 c·f·i
 *     번호 2개, 3게임 → 번호가 모자라 처음부터 다시 돌아가며 나눠 준다. (모든 게임에 꿈 번호가 최소 하나)
 * 많이 나온 번호끼리 한 게임에 몰리지 않고 게임마다 골고루 들어간다. 한 게임에 6개를 넘게 담지 않는다.
 * 각 게임의 모자란 자리는 번호 생성 기능이 통계로 채운다.
 */
public final class DreamGameSplitter {

    private DreamGameSplitter() {
    }

    /**
     * @param ranked 통계로 많이 나온 순으로 정렬된 꿈 번호
     * @param games  게임 수 (1 이상)
     * @param size   한 게임에 담을 수 있는 최대 번호 수
     */
    public static List<Set<Integer>> split(List<Integer> ranked, int games, int size) {
        List<Set<Integer>> result = new ArrayList<>();
        for (int i = 0; i < games; i++) {
            result.add(new LinkedHashSet<>());
        }
        if (ranked.isEmpty()) {
            return result;
        }
        int slots = Math.max(ranked.size(), games);
        for (int j = 0; j < slots; j++) {
            Set<Integer> game = result.get(j % games);
            if (game.size() < size) {
                game.add(ranked.get(j % ranked.size()));
            }
        }
        return result;
    }
}
