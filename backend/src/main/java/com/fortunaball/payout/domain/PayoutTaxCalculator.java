package com.fortunaball.payout.domain;

/**
 * 로또 당첨금 세금 계산 규칙. DB·스프링 없이 숫자만 계산하는 순수 클래스라서 테스트하기 쉽다.
 *
 * 적용 기준 (동행복권 "당첨자 가이드" 안내, 확인일 2026-10-19)
 *   - 200만원 이하: 비과세 (세금 0)
 *   - 200만원 초과 ~ 3억원 이하: 22% (소득세 20% + 지방소득세 2%)
 *   - 3억원 초과: 33% (소득세 30% + 지방소득세 3%)
 *
 * ⚠ 헷갈리기 쉬운 점
 *   - 200만원 "이하"는 비과세, "초과"하면 금액 전체에 과세한다. (200만원을 넘은 부분만 과세하는 게 아니다)
 *   - 3억원 초과 세율(30%)은 3억원을 넘은 "초과분"에만 적용한다. (3억원까지는 20%)
 *     → 이 구간 분할 방식은 소득세법의 복권 당첨소득 기준을 따른 것이다. 동행복권 안내 페이지에는 구간 표기만 있다.
 *   - 지방소득세는 소득세의 10%이다.
 *   - 원 단위 미만은 버린다(절사). 실제 지급 기관의 계산과 1원 단위로 다를 수 있어서 "참고용"으로만 안내한다.
 *   - 세법·세율이 바뀌면 이 클래스의 상수와 확인일(POLICY_CHECKED_DATE)을 함께 고친다. (정책 버전 관리)
 */
public final class PayoutTaxCalculator {

    /** 세금 기준을 마지막으로 확인한 날 (화면에 "계산 기준일"로 보여준다) */
    public static final String POLICY_CHECKED_DATE = "2026-10-19";
    public static final String POLICY_SOURCE_NAME = "동행복권 당첨자 가이드";
    public static final String POLICY_SOURCE_URL = "https://www.dhlottery.co.kr/guide/wnrGuide";

    /** 이 금액 이하는 비과세 (200만원) */
    static final long TAX_FREE_LIMIT = 2_000_000L;
    /** 이 금액을 넘는 부분은 높은 세율 (3억원) */
    static final long HIGH_BRACKET_FROM = 300_000_000L;

    private PayoutTaxCalculator() {
    }

    public static TaxResult calculate(long prizeAmount) {
        if (prizeAmount < 0) {
            throw new IllegalArgumentException("당첨금은 0 이상이어야 합니다: " + prizeAmount);
        }
        if (prizeAmount <= TAX_FREE_LIMIT) {
            return new TaxResult(prizeAmount, true, 0, 0, prizeAmount, "비과세");
        }

        long incomeTax;
        String label;
        if (prizeAmount <= HIGH_BRACKET_FROM) {
            incomeTax = prizeAmount * 20 / 100;                                   // 20% (원 미만 절사)
            label = "22% (소득세 20% + 지방소득세 2%)";
        } else {
            // 3억원까지는 20%, 3억원을 넘은 부분은 30%
            incomeTax = HIGH_BRACKET_FROM * 20 / 100 + (prizeAmount - HIGH_BRACKET_FROM) * 30 / 100;
            label = "3억원까지 22%, 3억원 초과분 33%";
        }
        long localTax = incomeTax / 10;                                           // 소득세의 10% (원 미만 절사)
        long net = prizeAmount - incomeTax - localTax;
        return new TaxResult(prizeAmount, false, incomeTax, localTax, net, label);
    }
}
