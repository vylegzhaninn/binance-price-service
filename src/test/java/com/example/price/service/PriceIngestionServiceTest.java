package com.example.price.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.price.client.BinanceClient;
import com.example.price.client.dto.BinanceAvgPrice;
import com.example.price.config.BinanceProperties;
import com.example.price.domain.PriceRecord;
import com.example.price.exception.BinanceApiException;
import com.example.price.repository.PriceRecordRepository;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PriceIngestionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:15:30.987Z");

    @Mock
    private BinanceClient binanceClient;
    @Mock
    private PriceRecordRepository repository;

    private PriceIngestionService ingestionService;

    @BeforeEach
    void setUp() {
        ingestionService = new PriceIngestionService(
                binanceClient,
                repository,
                new BinanceProperties(URI.create("https://api.binance.com"), "BNBUSDT", Duration.ofSeconds(30)),
                // fixed clock proves fetchedAt comes from the injected clock, not from system time
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("successful poll saves one record with the exact price and seconds-precision fetchedAt")
    void savesFetchedPrice() {
        when(binanceClient.fetchAveragePrice("BNBUSDT"))
                .thenReturn(new BinanceAvgPrice(5, "612.34567890"));

        ingestionService.fetchAndSaveAveragePrice();

        ArgumentCaptor<PriceRecord> captor = ArgumentCaptor.forClass(PriceRecord.class);
        verify(repository).save(captor.capture());
        PriceRecord saved = captor.getValue();
        assertThat(saved.getSymbol()).isEqualTo("BNBUSDT");
        assertThat(saved.getPrice()).isEqualByComparingTo(new BigDecimal("612.34567890"));
        assertThat(saved.getPrice().scale()).isEqualTo(8); // scale from Binance is preserved
        assertThat(saved.getFetchedAt()).isEqualTo(Instant.parse("2026-10-22T10:15:30Z"));
    }

    @Test
    @DisplayName("failed poll writes nothing to the database")
    void failedFetchWritesNothing() {
        when(binanceClient.fetchAveragePrice("BNBUSDT"))
                .thenThrow(new BinanceApiException("HTTP 429"));

        assertThatThrownBy(() -> ingestionService.fetchAndSaveAveragePrice())
                .isInstanceOf(BinanceApiException.class);

        verify(repository, never()).save(any());
    }
}
