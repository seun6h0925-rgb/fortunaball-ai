package com.fortunaball.generate.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * 번호 조합 하나 (게임 1개 = 번호 6개). 항상 오름차순으로 보관한다.
 *
 * ⚠ 헷갈리기 쉬운 점
 *  - DB 의 issue_number.combo_key 와 같은 형식("03-08-18-27-33-41", 17글자)을 key() 로 만든다.
 *    "같은 조합이 3번 찼는지" 비교할 때 이 key 를 쓴다. 번호를 한 자리로 쓰면("3-8-...") 비교가 틀어지므로 항상 두 자리로 만든다.
 *  - 생성자에서 6개·중복 없음·1~45 를 검사한다. 잘못된 값은 여기서 바로 막혀서 DB 나 화면까지 가지 않는다.
 */
public record Combo(List<Integer> numbers) {

    public Combo {
        if (numbers == null || numbers.size() != 6 || new HashSet<>(numbers).size() != 6) {
            throw new IllegalArgumentException("번호는 중복 없는 6개여야 합니다: " + numbers);
        }
        for (int n : numbers) {
            if (n < 1 || n > 45) {
                throw new IllegalArgumentException("번호는 1~45 여야 합니다: " + n);
            }
        }
        List<Integer> sorted = new ArrayList<>(numbers);
        Collections.sort(sorted);
        numbers = List.copyOf(sorted);
    }

    public static Combo of(Collection<Integer> numbers) {
        return new Combo(new ArrayList<>(numbers));
    }

    /** 오름차순 두 자리 키. 예) 03-08-18-27-33-41 */
    public String key() {
        StringBuilder sb = new StringBuilder(17);
        for (int i = 0; i < numbers.size(); i++) {
            if (i > 0) {
                sb.append('-');
            }
            sb.append(String.format("%02d", numbers.get(i)));
        }
        return sb.toString();
    }
}
