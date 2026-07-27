package com.gateway.error;

import org.springframework.http.HttpStatus;

/**
 * Standardized error codes used throughout the gateway.
 * Each code maps to an HTTP status for consistent API responses.
 *
 * @see ApiError
 * @see GlobalExceptionHandler
 */
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    PROVIDER_NOT_FOUND(HttpStatus.NOT_FOUND),
    PROVIDER_UNAVAILABLE(HttpStatus.BAD_GATEWAY),
    MODEL_NOT_FOUND(HttpStatus.NOT_FOUND),
    PROVIDER_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    BAD_CONFIGURATION(HttpStatus.INTERNAL_SERVER_ERROR),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public int getStatusCode() {
        return httpStatus.value();
    }
}
