package com.example.price.exception;

/** Thrown when a requested [from, to] period is invalid, e.g. from is after to. */
public class InvalidPeriodException extends RuntimeException {

    public InvalidPeriodException(String message) {
        super(message);
    }
}
