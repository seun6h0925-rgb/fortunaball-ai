package com.fortunaball.stat.domain;

import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.common.exception.ErrorCode;

import java.util.List;

/**
 * 통계 조회 기간. 화면의 [최근 10회 / 최근 50회 / 최근 100회 / 전체] 칩과 같다. (API-09, API-10)
 * ⚠ 패턴 카드(API-44)의 기간(20·50·100)과는 다른 값이다. 이 둘은 화면(UI-07)에서 따로 쓰인다.
 *
 * @param rounds 최근 몇 회인지. null 이면 전체
 */
public record StatPeriod(Integer rounds) {

    public static final List<Integer> ALLOWED = List.of(10, 50, 100);
    public static final int DEFAULT_ROUNDS = 100;
    public static final StatPeriod ALL = new StatPeriod(null);

    public boolean isAll() {
        return rounds == null;
    }

    /** 응답에 돌려줄 표기. "10" / "50" / "100" / "ALL" */
    public String label() {
        return rounds == null ? "ALL" : String.valueOf(rounds);
    }

    /** 문자열로 받은 기간을 검사한다. 비어 있으면 100. 허용: 10, 50, 100, ALL(대소문자 무관) */
    public static StatPeriod parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new StatPeriod(DEFAULT_ROUNDS);
        }
        String v = raw.trim();
        if (v.equalsIgnoreCase("ALL")) {
            return ALL;
        }
        try {
            int n = Integer.parseInt(v);
            if (ALLOWED.contains(n)) {
                return new StatPeriod(n);
            }
        } catch (NumberFormatException ignored) {
            // 아래에서 같은 오류로 처리한다
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT, "기간은 10, 50, 100, ALL 중에서 선택해 주세요.",
                "period=" + raw + " (허용: " + ALLOWED + ", ALL)");
    }
}
