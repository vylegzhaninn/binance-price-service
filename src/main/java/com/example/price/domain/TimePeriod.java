package com.example.price.domain;

import com.example.price.exception.InvalidPeriodException;
import java.time.Instant;

/**
 * Inclusive [from, to] time interval. Guarantees that from is not after to,
 * so every query method can rely on a valid period.
 */
public record TimePeriod(Instant from, Instant to) {

    public TimePeriod {
        if (from.isAfter(to)) {
            throw new InvalidPeriodException("Period start %s is after period end %s".formatted(from, to));
        }
    }

    public static TimePeriod of(Instant from, Instant to) {
        return new TimePeriod(from, to);
    }
}
