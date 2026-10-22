package com.example.price.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.example.price.client.BinanceApiException;
import com.example.price.service.PriceIngestionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PriceFetchSchedulerTest {

    @Mock
    private PriceIngestionService ingestionService;

    @Test
    @DisplayName("successful poll delegates to the ingestion service")
    void successfulPoll() {
        PriceFetchScheduler scheduler = new PriceFetchScheduler(ingestionService);

        scheduler.fetchAndStorePrice();

        verify(ingestionService).fetchAndSaveAveragePrice();
    }

    @Test
    @DisplayName("Binance failure is swallowed so the schedule keeps running")
    void swallowsBinanceFailure() {
        PriceFetchScheduler scheduler = new PriceFetchScheduler(ingestionService);
        doThrow(new BinanceApiException("HTTP 429"))
                .when(ingestionService).fetchAndSaveAveragePrice();

        assertThatCode(scheduler::fetchAndStorePrice).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("unexpected failure is swallowed as well")
    void swallowsUnexpectedFailure() {
        PriceFetchScheduler scheduler = new PriceFetchScheduler(ingestionService);
        doThrow(new IllegalStateException("db down"))
                .when(ingestionService).fetchAndSaveAveragePrice();

        assertThatCode(scheduler::fetchAndStorePrice).doesNotThrowAnyException();
    }
}
