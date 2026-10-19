package com.fortunaball.stat.dto;

import java.util.List;

/**
 * 패턴 계산에 쓰는 "한 회차의 당첨번호 6개". (보너스 번호는 패턴 계산에 쓰지 않는다)
 * DB 엔티티(DrawRound)에 직접 묶이지 않게 따로 둔 이유: 계산 클래스를 DB 없이 시험하고, 나중에 기준이 바뀌어도 영향이 적게 하기 위해서.
 */
public record DrawNumbers(int roundNo, List<Integer> numbers) {

    public DrawNumbers {
        if (numbers == null || numbers.size() != 6) {
            throw new IllegalArgumentException("당첨번호는 6개여야 합니다. round=" + roundNo);
        }
        numbers = List.copyOf(numbers);
    }
}
