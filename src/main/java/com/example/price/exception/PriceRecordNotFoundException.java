package com.example.price.exception;

/** Thrown when there is no stored data for a symbol (or symbol and period). */
public class PriceRecordNotFoundException extends RuntimeException {

    public PriceRecordNotFoundException(String message) {
        super(message);
    }
}
