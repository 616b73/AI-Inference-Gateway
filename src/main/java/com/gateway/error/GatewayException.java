package com.gateway.error;

import lombok.Getter;

/**
 * Base exception for all gateway business errors.
 * Wraps an {@link ErrorCode} so the {@link GlobalExceptionHandler}
 * can map it to the correct HTTP status and error response.
 */
@Getter
public class GatewayException extends RuntimeException {

    private final ErrorCode errorCode;

    public GatewayException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public GatewayException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
