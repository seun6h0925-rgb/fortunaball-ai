package com.fortunaball.stat.dto;

import java.util.List;
import java.util.Map;

/**
 * 로또 통계 요약 (API-09). 화면 UI-07 의 탭(핫넘버·겨울잠·밸런스·하이로우·센터 존·올 구간)이 쓰는 값을 한 번에 준다.
 *
 * ⚠ 모든 수치는 "과거에 나온 결과를 정리한 사실"이다. 예측 값은 없다.
 *  - basisRound  : 이 회차까지의 결과로 계산했다. (기본 = 저장된 가장 최근 회차)
 *  - period      : 계산 구간. "10" / "50" / "100" / "ALL"
 *  - roundCount  : 구간에 실제로 들어간 회차 수 (이력이 짧으면 period 보다 작을 수 있다)
 *  - status      : 회차 통계 상태(PREPARING/WAITING/CONFIRMED). 기준 회차가 최신이 아니면 CONFIRMED. 알 수 없으면 null
 */
public record StatSummaryResponse(
        int basisRound,
        String period,
        int roundCount,
        int fromRound,
        int toRound,
        String status,
        String statusLabel,
        List<HotNumber> hotNumbers,
        Absence absence,
        Split oddEven,
        Split highLow,
        Sum sum,
        List<Band> sections,
        Consecutive consecutive,
        List<Band> lastDigits,
        Ac ac,
        Prime prime,
        List<RoundDetail> recentRounds) {

    /** 라벨 + 횟수 한 칸 (막대 하나) */
    public record Band(String label, int count) {
    }

    /** 많이 나온 번호. change = 바로 앞 같은 길이 구간 대비 출현 횟수 증감. 전체(ALL)이거나 앞 구간이 없으면 null */
    public record HotNumber(int number, int count, Integer change) {
    }

    public record AbsentNumber(int number, Integer lastRound, int absentRounds) {
    }

    /** 장기 미출현. averageInterval = 번호 한 개가 평균 몇 회에 한 번 나오는지(45÷6 = 7.5, 이론값) */
    public record Absence(int maxAbsent, double averageInterval, int over10Count, List<AbsentNumber> numbers) {
    }

    /**
     * 두 편으로 나누는 통계(홀짝·저고). first = 홀수 / 저(1~22), second = 짝수 / 고(23~45).
     * combos 는 "first개:second개" 별 회차 수. 키는 "6:0" ~ "0:6" 순서를 지킨다.
     */
    public record Split(int firstTotal, int secondTotal, double firstPercent, double secondPercent,
                        Map<String, Integer> combos) {
    }

    public record SumPoint(int roundNo, int sum) {
    }

    /** 합계. series = 구간 안 최근 최대 100회의 회차별 합계(오래된 순) */
    public record Sum(double average, int min, int max, List<Band> bands, List<SumPoint> series) {
    }

    /** 연속수. pairDistribution 키: "0", "1", "2", "3+" (한 회차에 이웃한 숫자 쌍이 몇 개였는지 별 회차 수) */
    public record Consecutive(int drawsWithConsecutive, double percent, Map<String, Integer> pairDistribution) {
    }

    /** AC 값(번호 6개 사이 서로 다른 차이의 개수 - 5, 0~10). 분포는 라벨 "0"~"10" */
    public record Ac(double average, List<Band> distribution) {
    }

    /** 소수. 한 회차에 소수가 몇 개 있었는지 분포("0"~"6") */
    public record Prime(double average, List<Band> distribution, List<Integer> primeNumbers) {
    }

    /** 최근 회차 한 줄(최대 5회, 최신순). sectionCounts 는 1~10, 11~20, 21~30, 31~40, 41~45 순서 */
    public record RoundDetail(int roundNo, List<Integer> numbers, int odd, int even, int low, int high, int sum,
                              List<Integer> sectionCounts, int consecutivePairs, int ac, int primeCount) {
    }
}
