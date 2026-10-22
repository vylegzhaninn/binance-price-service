package com.example.price.api;

import com.example.price.exception.InvalidPeriodException;
import com.example.price.exception.PriceRecordNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps application exceptions to RFC 7807 problem details so clients always get
 * a consistent JSON error shape. Framework errors (unknown path, unsupported
 * method) are handled explicitly and keep their own status codes instead of
 * falling into the generic 500 handler.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(PriceRecordNotFoundException.class)
    ProblemDetail handleNotFound(PriceRecordNotFoundException e) {
        log.info("Not found: {}", e.getMessage());
        return problem(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(InvalidPeriodException.class)
    ProblemDetail handleInvalidPeriod(InvalidPeriodException e) {
        log.info("Invalid period requested: {}", e.getMessage());
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class})
    ProblemDetail handleBadRequest(Exception e) {
        log.info("Bad request: {}", e.getMessage());
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail handleNoResourceFound(NoResourceFoundException e) {
        log.info("Unknown endpoint requested: /{}", e.getResourcePath());
        return problem(HttpStatus.NOT_FOUND, "No such endpoint: /" + e.getResourcePath());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.info("Method not supported: {}", e.getMessage());
        return problem(HttpStatus.METHOD_NOT_ALLOWED, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) {
        log.error("Unhandled error", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        return problem;
    }
}
