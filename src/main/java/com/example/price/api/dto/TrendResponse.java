package com.example.price.api.dto;

import com.example.price.service.TrendType;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

public record TrendResponse(
        String symbol,
        @JsonProperty("start_date") LocalDate startDate,
        @JsonProperty("end_date") LocalDate endDate,
        TrendType trend
) {
}
