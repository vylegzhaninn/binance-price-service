package com.example.price.service;

import com.example.price.client.BinanceClient;
import com.example.price.client.dto.BinanceAvgPrice;
import com.example.price.config.BinanceProperties;
import com.example.price.domain.PriceRecord;
import com.example.price.repository.PriceRecordRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write side of the service: fetches the average price once and stores exactly
 * one record per successful call. A failed call writes nothing, so the DB never
 * gets duplicated or half-written data; the next successful poll simply adds the
 * next row.
 */
@Service
public class PriceIngestionService {

    private final BinanceClient binanceClient;
    private final PriceRecordRepository repository;
    private final BinanceProperties properties;
    private final Clock clock;

    public PriceIngestionService(BinanceClient binanceClient,
                                 PriceRecordRepository repository,
                                 BinanceProperties properties,
                                 Clock clock) {
        this.binanceClient = binanceClient;
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public PriceRecord fetchAndSaveAveragePrice() {
        BinanceAvgPrice avgPrice = binanceClient.fetchAveragePrice(properties.symbol());
        PriceRecord record = new PriceRecord(
                properties.symbol(),
                avgPrice.priceValue(),
                Instant.now(clock).truncatedTo(ChronoUnit.SECONDS)); // ISO-8601 seconds precision per API contract
        return repository.save(record);
    }
}
