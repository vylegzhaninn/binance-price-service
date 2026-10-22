package com.example.price.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.price.exception.InvalidPeriodException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TimePeriodTest {

    private static final Instant FROM = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-02T00:00:00Z");

    @Test
    @DisplayName("from equal to to is a valid (single-moment) period")
    void validPeriod() {
        assertThatCode(() -> TimePeriod.of(FROM, TO)).doesNotThrowAnyException();
        assertThatCode(() -> TimePeriod.of(FROM, FROM)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("from after to is rejected")
    void invalidPeriod() {
        assertThatThrownBy(() -> TimePeriod.of(TO, FROM))
                .isInstanceOf(InvalidPeriodException.class);
    }
}
