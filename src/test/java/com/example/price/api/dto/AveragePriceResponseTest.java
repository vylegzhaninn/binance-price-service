package com.example.price.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AveragePriceResponseTest {

    @Test
    @DisplayName("trailing zeros of the AVG scale are stripped without changing the value")
    void stripsTrailingZeros() {
        BigDecimal average = AveragePriceResponse.of(new BigDecimal("740.1056154100000000")).averagePrice();
        assertThat(average).isEqualByComparingTo("740.10561541");
        assertThat(average.scale()).isEqualTo(8);
    }

    @Test
    @DisplayName("whole averages are rendered as plain numbers, not scientific notation")
    void avoidsScientificNotation() {
        BigDecimal whole = new BigDecimal("600.0000000000000000");
        assertThat(AveragePriceResponse.of(whole).averagePrice().toPlainString())
                .isEqualTo("600");
    }
}
