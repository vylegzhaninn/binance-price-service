package com.example.price.api.dto;

import java.math.BigDecimal;

/**
 * Trailing zeros are stripped for readability: PostgreSQL AVG keeps the working
 * scale of the division (e.g. 740.1056154100000000), while the value itself stays exact.
 */
public record AveragePriceResponse(BigDecimal averagePrice) {

    public static AveragePriceResponse of(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() < 0) {
            stripped = stripped.setScale(0); // avoid scientific notation like 6E+2
        }
        return new AveragePriceResponse(stripped);
    }
}
