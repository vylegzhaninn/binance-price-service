package com.example.price.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * Response of GET /api/v3/avgPrice. The price arrives as a string; it is kept as
 * string until conversion so the exact decimal scale sent by Binance is preserved.
 * The closeTime field is present in the payload but is not needed by this service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BinanceAvgPrice(
        @JsonProperty("mins") int mins,
        @JsonProperty("price") String price) {

    public BigDecimal priceValue() {
        return new BigDecimal(price);
    }
}
