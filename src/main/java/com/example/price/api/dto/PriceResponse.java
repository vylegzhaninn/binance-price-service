package com.example.price.api.dto;

import com.example.price.domain.PriceRecord;
import java.math.BigDecimal;
import java.time.Instant;

/** Public representation of a stored price record. */
public record PriceResponse(String symbol, BigDecimal price, Instant fetchedAt) {

    public static PriceResponse from(PriceRecord record) {
        return new PriceResponse(record.getSymbol(), record.getPrice(), record.getFetchedAt());
    }
}
