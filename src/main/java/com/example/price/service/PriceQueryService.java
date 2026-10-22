package com.example.price.service;

import com.example.price.domain.PriceRecord;
import com.example.price.domain.TimePeriod;
import com.example.price.exception.PriceRecordNotFoundException;
import com.example.price.repository.PriceRecordRepository;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the service: answers all REST queries from stored data only,
 * it never calls Binance.
 */
@Service
@Transactional(readOnly = true)
public class PriceQueryService {

    private final PriceRecordRepository repository;
    private final TrendCalculator trendCalculator;

    public PriceQueryService(PriceRecordRepository repository, TrendCalculator trendCalculator) {
        this.repository = repository;
        this.trendCalculator = trendCalculator;
    }

    public PriceRecord latestPrice(String symbol) {
        return repository.findFirstBySymbolOrderByFetchedAtDescIdDesc(symbol)
                .orElseThrow(() -> notFound(symbol, null));
    }

    public BigDecimal averagePrice(String symbol, TimePeriod period) {
        return repository.findAveragePrice(symbol, period.from(), period.to())
                .orElseThrow(() -> notFound(symbol, period));
    }

    public Page<PriceRecord> priceHistory(String symbol, TimePeriod period, Pageable pageable) {
        return repository.findBySymbolAndFetchedAtBetweenOrderByFetchedAtDescIdDesc(
                symbol, period.from(), period.to(), pageable);
    }

    public TrendType priceTrend(String symbol, TimePeriod period) {
        PriceRecord first = firstInPeriod(symbol, period);
        PriceRecord last = lastInPeriod(symbol, period);
        return trendCalculator.calculate(first.getPrice(), last.getPrice());
    }

    private PriceRecord firstInPeriod(String symbol, TimePeriod period) {
        return repository.findFirstBySymbolAndFetchedAtBetweenOrderByFetchedAtAscIdAsc(
                        symbol, period.from(), period.to())
                .orElseThrow(() -> notFound(symbol, period));
    }

    private PriceRecord lastInPeriod(String symbol, TimePeriod period) {
        return repository.findFirstBySymbolAndFetchedAtBetweenOrderByFetchedAtDescIdDesc(
                        symbol, period.from(), period.to())
                .orElseThrow(() -> notFound(symbol, period));
    }

    private static PriceRecordNotFoundException notFound(String symbol, TimePeriod period) {
        String message = period == null
                ? "No price records found for symbol " + symbol
                : "No price records found for symbol %s in period [%s; %s]"
                        .formatted(symbol, period.from(), period.to());
        return new PriceRecordNotFoundException(message);
    }
}
