package com.example.price.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Binance integration settings. Every value can be overridden with environment variables
 * (see application.yml), the poll interval comes in as ISO-8601 duration, e.g. {@code PT30S}.
 */
@Validated
@ConfigurationProperties(prefix = "binance")
public record BinanceProperties(
        @NotNull URI baseUrl,
        @NotBlank String symbol,
        @NotNull Duration pollInterval) {
}
