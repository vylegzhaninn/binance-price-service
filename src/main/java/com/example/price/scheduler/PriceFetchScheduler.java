package com.example.price.scheduler;

import com.example.price.exception.BinanceApiException;
import com.example.price.domain.PriceRecord;
import com.example.price.service.PriceIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs the Binance poll. All exceptions are caught on purpose: a failed poll is
 * logged and skipped, and because the task is scheduled with a fixed delay the
 * schedule itself keeps running (see SchedulingConfig).
 */
@Component
public class PriceFetchScheduler {

    private static final Logger log = LoggerFactory.getLogger(PriceFetchScheduler.class);

    private final PriceIngestionService ingestionService;

    public PriceFetchScheduler(PriceIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    public void fetchAndStorePrice() {
        try {
            PriceRecord saved = ingestionService.fetchAndSaveAveragePrice();
            log.info("Stored price record: symbol={}, price={}, fetchedAt={}",
                    saved.getSymbol(), saved.getPrice(), saved.getFetchedAt());
        } catch (BinanceApiException e) {
            log.warn("Binance poll failed, will retry on next schedule: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during Binance poll, will retry on next schedule", e);
        }
    }
}
