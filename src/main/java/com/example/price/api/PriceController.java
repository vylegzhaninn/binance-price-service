package com.example.price.api;

import com.example.price.api.dto.AveragePriceResponse;
import com.example.price.api.dto.PageResponse;
import com.example.price.api.dto.PriceResponse;
import com.example.price.api.dto.TrendResponse;
import com.example.price.config.BinanceProperties;
import com.example.price.domain.TimePeriod;
import com.example.price.service.PriceQueryService;
import com.example.price.service.TrendType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * All price endpoints. Dates are accepted in ISO-8601 with an offset,
 * e.g. 2026-10-01T00:00:00Z, and interpreted inclusively on both ends: [from, to].
 */
@Tag(name = "Prices", description = "Сохранённые средние цены торговой пары с Binance")
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
    @Operation(summary = "Последняя сохранённая запись (самая актуальная цена)")
    @GetMapping("/latest")
    public PriceResponse latest(
            @Parameter(description = "Торговая пара; по умолчанию — настроенный символ сервиса")
            @RequestParam(required = false) String symbol) {
        return PriceResponse.from(priceQueryService.latestPrice(resolveSymbol(symbol)));
    }

    /** Average price over stored records inside [from, to]. */
    @Operation(summary = "Средняя цена за период [from, to]")
    @GetMapping("/average")
    public AveragePriceResponse average(
            @Parameter(description = "Начало периода, ISO-8601, например 2026-10-01T00:00:00Z", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = "Конец периода (включительно), ISO-8601", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @Parameter(description = "Торговая пара; по умолчанию — настроенный символ сервиса")
            @RequestParam(required = false) String symbol) {
        TimePeriod period = TimePeriod.of(from.toInstant(), to.toInstant());
        return AveragePriceResponse.of(priceQueryService.averagePrice(resolveSymbol(symbol), period));
    }

    /** Stored records inside [from, to] with pagination, newest first. */
    @Operation(summary = "Записи за период [from, to] с пагинацией")
    @GetMapping
    public PageResponse<PriceResponse> history(
            @Parameter(description = "Начало периода, ISO-8601", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = "Конец периода (включительно), ISO-8601", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @Parameter(description = "Номер страницы, начиная с 0", example = "0")
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @Parameter(description = "Размер страницы (1..100)", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @Parameter(description = "Торговая пара; по умолчанию — настроенный символ сервиса")
            @RequestParam(required = false) String symbol) {
        TimePeriod period = TimePeriod.of(from.toInstant(), to.toInstant());
        Pageable pageable = PageRequest.of(page, size);
        return PageResponse.from(priceQueryService
                .priceHistory(resolveSymbol(symbol), period, pageable)
                .map(PriceResponse::from));
    }

    /** Trend over stored records inside [from, to]: UPWARD, DOWNWARD or FLAT. */
    @Operation(summary = "Тренд цены за период [from, to]",
            description = "Сравнивает первую и последнюю цену периода; изменение в пределах ±0.1% считается FLAT")
    @GetMapping("/trend")
    public TrendResponse trend(
            @Parameter(description = "Начало периода, ISO-8601", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = "Конец периода (включительно), ISO-8601", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @Parameter(description = "Торговая пара; по умолчанию — настроенный символ сервиса")
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
