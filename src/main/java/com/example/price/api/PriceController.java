package com.example.price.api;

import com.example.price.api.dto.AveragePriceResponse;
import com.example.price.api.dto.PageResponse;
import com.example.price.api.dto.PriceResponse;
import com.example.price.api.dto.TrendResponse;
import com.example.price.config.BinanceProperties;
import com.example.price.domain.TimePeriod;
import com.example.price.service.PriceQueryService;
import com.example.price.service.TrendType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * All price endpoints. Dates are accepted in ISO-8601 with an offset,
 * e.g. 2026-10-01T00:00:00Z, and interpreted inclusively on both ends: [from, to].
 */
@RestController
@RequestMapping("/api/v1/prices")
@Validated
public class PriceController {

    static final int MAX_PAGE_SIZE = 100;

    private final PriceQueryService priceQueryService;
    private final BinanceProperties properties;

    public PriceController(PriceQueryService priceQueryService, BinanceProperties properties) {
        this.priceQueryService = priceQueryService;
        this.properties = properties;
    }

    /** Latest stored record (the most recent successful poll). */
    @GetMapping("/latest")
    public PriceResponse latest(@RequestParam(required = false) String symbol) {
        return PriceResponse.from(priceQueryService.latestPrice(resolveSymbol(symbol)));
    }

    /** Average price over stored records inside [from, to]. */
    @GetMapping("/average")
    public AveragePriceResponse average(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String symbol) {
        TimePeriod period = TimePeriod.of(from.toInstant(), to.toInstant());
        return AveragePriceResponse.of(priceQueryService.averagePrice(resolveSymbol(symbol), period));
    }

    /** Stored records inside [from, to] with pagination, newest first. */
    @GetMapping
    public PageResponse<PriceResponse> history(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @RequestParam(required = false) String symbol) {
        TimePeriod period = TimePeriod.of(from.toInstant(), to.toInstant());
        Pageable pageable = PageRequest.of(page, size);
        return PageResponse.from(priceQueryService
                .priceHistory(resolveSymbol(symbol), period, pageable)
                .map(PriceResponse::from));
    }

    /** Trend over stored records inside [from, to]: UPWARD, DOWNWARD or FLAT. */
    @GetMapping("/trend")
    public TrendResponse trend(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String symbol) {
        TimePeriod period = TimePeriod.of(from.toInstant(), to.toInstant());
        String resolvedSymbol = resolveSymbol(symbol);
        TrendType trend = priceQueryService.priceTrend(resolvedSymbol, period);
        return new TrendResponse(
                resolvedSymbol,
                from.withOffsetSameInstant(ZoneOffset.UTC).toLocalDate(),
                to.withOffsetSameInstant(ZoneOffset.UTC).toLocalDate(),
                trend);
    }

    private String resolveSymbol(String requested) {
        return requested != null ? requested : properties.symbol();
    }
}
