package com.example.price.service;

import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Compares the first and the last price of a period. A change that stays within
 * {@link #FLAT_RELATIVE_THRESHOLD} of the start price (0.1%) is treated as FLAT:
 * smaller moves are ordinary market noise rather than a trend.
 */
@Component
public class TrendCalculator {

    /** Relative change (in percent/100) below which the movement is considered flat. */
    static final BigDecimal FLAT_RELATIVE_THRESHOLD = new BigDecimal("0.001");

    public TrendType calculate(BigDecimal startPrice, BigDecimal endPrice) {
        Objects.requireNonNull(startPrice, "startPrice must not be null");
        Objects.requireNonNull(endPrice, "endPrice must not be null");

        if (startPrice.compareTo(BigDecimal.ZERO) == 0) {
            if (endPrice.compareTo(BigDecimal.ZERO) == 0) {
                return TrendType.FLAT;
            }
            return endPrice.signum() > 0 ? TrendType.UPWARD : TrendType.DOWNWARD;
        }

        BigDecimal change = endPrice.subtract(startPrice);
        BigDecimal flatBand = startPrice.abs().multiply(FLAT_RELATIVE_THRESHOLD);
        if (change.abs().compareTo(flatBand) <= 0) {
            return TrendType.FLAT;
        }
        return change.signum() > 0 ? TrendType.UPWARD : TrendType.DOWNWARD;
    }
}
