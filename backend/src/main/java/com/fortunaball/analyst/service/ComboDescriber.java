package com.fortunaball.analyst.service;

import com.fortunaball.analyst.domain.EvidenceItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 번호 6개의 특징을 사실 그대로 풀어 쓴다. (홀짝, 저고, 합계, 연속수, 구간, 최근 출현 횟수)
 * 계산만 하는 클래스라 DB 나 AI 를 쓰지 않는다. 번호 분석·발급 분석·생성 번호 근거에서 함께 쓴다.
 *
 * ⚠ 모든 값은 "과거 결과와 비교한 사실"이다. 좋다·나쁘다 같은 평가나 예측은 담지 않는다.
 */
final class ComboDescriber {

    private static final int[][] SECTIONS = {{1, 10}, {11, 20}, {21, 30}, {31, 40}, {41, 45}};

    private ComboDescriber() {
    }

    /**
     * @param numbers      번호 6개
     * @param countByNumber 번호별 최근 구간 출현 횟수 (null 이면 출현 횟수 줄은 만들지 않는다)
     * @param roundCount   그 구간의 회차 수 (출현 횟수 문구에 쓴다)
     * @param prefix       항목 이름 앞에 붙일 말. 예) "게임 1 " (없으면 빈 문자열)
     */
    static List<EvidenceItem> describe(List<Integer> numbers, Map<Integer, Integer> countByNumber, int roundCount,
                                       String prefix) {
        List<Integer> sorted = numbers.stream().sorted().toList();
        int odd = (int) sorted.stream().filter(n -> n % 2 == 1).count();
        int low = (int) sorted.stream().filter(n -> n <= 22).count();
        int sum = sorted.stream().mapToInt(Integer::intValue).sum();
        int pairs = 0;
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i) - sorted.get(i - 1) == 1) {
                pairs++;
            }
        }

        List<EvidenceItem> items = new ArrayList<>();
        items.add(new EvidenceItem(prefix + "번호", join(sorted)));
        items.add(new EvidenceItem(prefix + "홀짝", "홀수 " + odd + "개 · 짝수 " + (6 - odd) + "개"));
        items.add(new EvidenceItem(prefix + "저고", "저(1~22) " + low + "개 · 고(23~45) " + (6 - low) + "개"));
        items.add(new EvidenceItem(prefix + "합계", String.valueOf(sum)));
        items.add(new EvidenceItem(prefix + "연속수", "이웃한 숫자 쌍 " + pairs + "개"));
        items.add(new EvidenceItem(prefix + "구간", sections(sorted)));
        if (countByNumber != null) {
            String counts = sorted.stream()
                    .map(n -> n + "번 " + countByNumber.getOrDefault(n, 0) + "회")
                    .collect(Collectors.joining(", "));
            items.add(new EvidenceItem(prefix + "최근 " + roundCount + "회 출현", counts));
        }
        return items;
    }

    private static String sections(List<Integer> sorted) {
        List<String> parts = new ArrayList<>();
        for (int[] s : SECTIONS) {
            int c = (int) sorted.stream().filter(n -> n >= s[0] && n <= s[1]).count();
            parts.add(s[0] + "~" + s[1] + " " + c + "개");
        }
        return String.join(" · ", parts);
    }

    static String join(List<Integer> numbers) {
        return numbers.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }
}
