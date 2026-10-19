package com.fortunaball.payout.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PayoutTaxCalculatorTest {

    @Test
    void 오천원_오만원은_비과세() {
        TaxResult five = PayoutTaxCalculator.calculate(5_000);
        assertTrue(five.taxFree());
        assertEquals(0, five.totalTax());
        assertEquals(5_000, five.netAmount());
        assertEquals(50_000, PayoutTaxCalculator.calculate(50_000).netAmount());
    }

    @Test
    void 이백만원까지는_비과세_넘으면_전체에_과세() {
        TaxResult limit = PayoutTaxCalculator.calculate(2_000_000);
        assertTrue(limit.taxFree());
        assertEquals(2_000_000, limit.netAmount());

        TaxResult over = PayoutTaxCalculator.calculate(2_000_001);          // 1원만 넘어도 전체에 과세
        assertFalse(over.taxFree());
        assertEquals(400_000, over.incomeTax());                             // 2,000,001 × 20% = 400,000.2 → 절사
        assertEquals(40_000, over.localTax());
        assertEquals(2_000_001 - 440_000, over.netAmount());
    }

    @Test
    void 삼억원까지는_22퍼센트() {
        TaxResult r = PayoutTaxCalculator.calculate(300_000_000);
        assertEquals(60_000_000, r.incomeTax());
        assertEquals(6_000_000, r.localTax());
        assertEquals(66_000_000, r.totalTax());
        assertEquals(234_000_000, r.netAmount());
    }

    @Test
    void 삼억원_초과분만_높은_세율() {
        // 20억: 3억×20% + 17억×30% = 6천만 + 5억1천만 = 5억7천만 / 지방소득세 5천7백만
        TaxResult r = PayoutTaxCalculator.calculate(2_000_000_000L);
        assertEquals(570_000_000L, r.incomeTax());
        assertEquals(57_000_000L, r.localTax());
        assertEquals(627_000_000L, r.totalTax());
        assertEquals(1_373_000_000L, r.netAmount());
    }

    @Test
    void 실수령액과_세금의_합은_항상_당첨금과_같다() {
        for (long prize : new long[]{0, 5_000, 1_500_000, 2_000_001, 54_321_987, 299_999_999, 300_000_001, 2_345_678_901L}) {
            TaxResult r = PayoutTaxCalculator.calculate(prize);
            assertEquals(prize, r.netAmount() + r.totalTax());
        }
    }

    @Test
    void 음수_당첨금은_예외() {
        assertThrows(IllegalArgumentException.class, () -> PayoutTaxCalculator.calculate(-1));
    }
}
