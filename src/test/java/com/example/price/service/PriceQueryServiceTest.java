package com.example.price.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.price.domain.PriceRecord;
import com.example.price.domain.TimePeriod;
import com.example.price.exception.PriceRecordNotFoundException;
import com.example.price.repository.PriceRecordRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class PriceQueryServiceTest {

    private static final String SYMBOL = "BNBUSDT";
    private static final Instant FROM = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-02T00:00:00Z");
    private static final TimePeriod PERIOD = TimePeriod.of(FROM, TO);

    @Mock
    private PriceRecordRepository repository;

    private PriceQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new PriceQueryService(repository, new TrendCalculator());
    }

    @Test
    @DisplayName("latest returns the newest stored record")
    void latestPrice() {
        PriceRecord record = new PriceRecord(SYMBOL, new BigDecimal("612.34"), TO);
        when(repository.findFirstBySymbolOrderByFetchedAtDescIdDesc(SYMBOL)).thenReturn(Optional.of(record));

        assertThat(queryService.latestPrice(SYMBOL)).isSameAs(record);
    }

    @Test
    @DisplayName("latest without any stored data throws NotFound")
    void latestPriceMissing() {
        when(repository.findFirstBySymbolOrderByFetchedAtDescIdDesc(SYMBOL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.latestPrice(SYMBOL))
                .isInstanceOf(PriceRecordNotFoundException.class);
    }

    @Test
    @DisplayName("average is taken from the repository value")
    void averagePrice() {
        when(repository.findAveragePrice(SYMBOL, FROM, TO))
                .thenReturn(Optional.of(new BigDecimal("610.50")));

        assertThat(queryService.averagePrice(SYMBOL, PERIOD))
                .isEqualByComparingTo("610.50");
    }

    @Test
    @DisplayName("average over an empty period throws NotFound")
    void averagePriceMissing() {
        when(repository.findAveragePrice(SYMBOL, FROM, TO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.averagePrice(SYMBOL, PERIOD))
                .isInstanceOf(PriceRecordNotFoundException.class);
    }

    @Test
    @DisplayName("history is delegated to the repository with the same period and paging")
    void priceHistory() {
        Pageable pageable = PageRequest.of(0, 20);
        List<PriceRecord> records = List.of(new PriceRecord(SYMBOL, BigDecimal.TEN, TO));
        when(repository.findBySymbolAndFetchedAtBetweenOrderByFetchedAtDescIdDesc(SYMBOL, FROM, TO, pageable))
                .thenReturn(new PageImpl<>(records, pageable, records.size()));

        assertThat(queryService.priceHistory(SYMBOL, PERIOD, pageable).getContent()).isEqualTo(records);
    }

    @Test
    @DisplayName("trend compares the first and the last record of the period")
    void priceTrendUpward() {
        when(repository.findFirstBySymbolAndFetchedAtBetweenOrderByFetchedAtAscIdAsc(SYMBOL, FROM, TO))
                .thenReturn(Optional.of(new PriceRecord(SYMBOL, new BigDecimal("100"), FROM)));
        when(repository.findFirstBySymbolAndFetchedAtBetweenOrderByFetchedAtDescIdDesc(SYMBOL, FROM, TO))
                .thenReturn(Optional.of(new PriceRecord(SYMBOL, new BigDecimal("150"), TO)));

        assertThat(queryService.priceTrend(SYMBOL, PERIOD)).isEqualTo(TrendType.UPWARD);
    }

    @Test
    @DisplayName("trend for a period without data throws NotFound")
    void priceTrendMissing() {
        when(repository.findFirstBySymbolAndFetchedAtBetweenOrderByFetchedAtAscIdAsc(any(), any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.priceTrend(SYMBOL, PERIOD))
                .isInstanceOf(PriceRecordNotFoundException.class);
    }
}
