package com.example.price.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TrendCalculatorTest {

    private final TrendCalculator calculator = new TrendCalculator();

    @Test
    @DisplayName("growth beyond the flat threshold is UPWARD")
    void upward() {
        assertThat(calculator.calculate(new BigDecimal("100"), new BigDecimal("110")))
                .isEqualTo(TrendType.UPWARD);
    }

    @Test
    @DisplayName("fall beyond the flat threshold is DOWNWARD")
    void downward() {
        assertThat(calculator.calculate(new BigDecimal("110"), new BigDecimal("100")))
                .isEqualTo(TrendType.DOWNWARD);
    }

    @Test
    @DisplayName("change within 0.1% of the start price is FLAT")
    void flatWithinThreshold() {
        // 100 -> 100.05 is +0.05%, below the 0.1% threshold
        assertThat(calculator.calculate(new BigDecimal("100"), new BigDecimal("100.05")))
                .isEqualTo(TrendType.FLAT);
        // exactly at the edge of the band is still FLAT
        assertThat(calculator.calculate(new BigDecimal("100"), new BigDecimal("99.9")))
                .isEqualTo(TrendType.FLAT);
    }

    @Test
    @DisplayName("equal prices are FLAT")
    void flatWhenEqual() {
        assertThat(calculator.calculate(new BigDecimal("612.34"), new BigDecimal("612.34")))
                .isEqualTo(TrendType.FLAT);
    }

    @Test
    @DisplayName("zero start price does not break the division")
    void zeroStartPrice() {
        assertThat(calculator.calculate(BigDecimal.ZERO, BigDecimal.ZERO)).isEqualTo(TrendType.FLAT);
        assertThat(calculator.calculate(BigDecimal.ZERO, new BigDecimal("1"))).isEqualTo(TrendType.UPWARD);
        assertThat(calculator.calculate(BigDecimal.ZERO, new BigDecimal("-1"))).isEqualTo(TrendType.DOWNWARD);
    }

    @Test
    @DisplayName("null prices are rejected")
    void nullPrices() {
        assertThatThrownBy(() -> calculator.calculate(null, BigDecimal.ONE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> calculator.calculate(BigDecimal.ONE, null))
                .isInstanceOf(NullPointerException.class);
    }
}
