package com.example.price.exception;

/**
 * Signals that a Binance API call failed (HTTP error, I/O problem or an unreadable response).
 * The scheduler treats it as a skipped poll and retries on the next tick.
 */
public class BinanceApiException extends RuntimeException {

    public BinanceApiException(String message) {
        super(message);
    }

    public BinanceApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
