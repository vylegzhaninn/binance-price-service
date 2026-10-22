package com.example.price.repository;

import com.example.price.domain.PriceRecord;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PriceRecordRepository extends JpaRepository<PriceRecord, Long> {

    /** Latest record for a symbol (id is a tiebreaker for identical fetchedAt values). */
    Optional<PriceRecord> findFirstBySymbolOrderByFetchedAtDescIdDesc(String symbol);

    Page<PriceRecord> findBySymbolAndFetchedAtBetweenOrderByFetchedAtDescIdDesc(
            String symbol, Instant from, Instant to, Pageable pageable);

    Optional<PriceRecord> findFirstBySymbolAndFetchedAtBetweenOrderByFetchedAtAscIdAsc(
            String symbol, Instant from, Instant to);

    Optional<PriceRecord> findFirstBySymbolAndFetchedAtBetweenOrderByFetchedAtDescIdDesc(
            String symbol, Instant from, Instant to);

    /**
     * Exact average over numeric values computed by PostgreSQL; returns BigDecimal
     * without any floating point conversion.
     */
    @Query(value = """
            select avg(price)
            from price_records
            where symbol = :symbol and fetched_at between :from and :to
            """, nativeQuery = true)
    Optional<BigDecimal> findAveragePrice(@Param("symbol") String symbol,
                                          @Param("from") Instant from,
                                          @Param("to") Instant to);
}
