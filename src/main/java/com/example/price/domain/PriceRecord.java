package com.example.price.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One successfully fetched average price of a trading pair.
 * Immutable from the application side: records are only inserted, never updated.
 */
@Entity
@Table(name = "price_records")
public class PriceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    protected PriceRecord() {
        // for JPA only
    }

    public PriceRecord(String symbol, BigDecimal price, Instant fetchedAt) {
        this.symbol = symbol;
        this.price = price;
        this.fetchedAt = fetchedAt;
    }

    public Long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }
}
