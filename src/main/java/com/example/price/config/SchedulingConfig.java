package com.example.price.config;

import com.example.price.scheduler.PriceFetchScheduler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Registers the Binance poll as a fixed-delay task: the next poll starts only after
 * the previous one has finished, so polls never overlap regardless of how long an
 * HTTP call takes. The delay comes from {@link BinanceProperties#pollInterval()},
 * i.e. from BINANCE_POLL_INTERVAL (ISO-8601, e.g. PT30S).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig implements SchedulingConfigurer {

    private final BinanceProperties properties;
    private final PriceFetchScheduler priceFetchScheduler;

    public SchedulingConfig(BinanceProperties properties, PriceFetchScheduler priceFetchScheduler) {
        this.properties = properties;
        this.priceFetchScheduler = priceFetchScheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(priceFetchScheduler::fetchAndStorePrice, properties.pollInterval());
    }
}
