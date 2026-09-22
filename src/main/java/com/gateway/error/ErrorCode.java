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
    PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    MODEL_NOT_FOUND(HttpStatus.NOT_FOUND),
    PROVIDER_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    BAD_CONFIGURATION(HttpStatus.INTERNAL_SERVER_ERROR),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    OVERLOADED(HttpStatus.SERVICE_UNAVAILABLE);

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

    public String safeMessage() {
        return switch (this) {
            case INVALID_REQUEST -> "Invalid request parameters";
            case PROVIDER_NOT_FOUND -> "Provider not found or not active";
            case MODEL_NOT_FOUND -> "Model not found or not active";
            case PROVIDER_UNAVAILABLE -> "Provider is unavailable";
            case PROVIDER_TIMEOUT -> "Request deadline or provider timeout exceeded";
            case BAD_CONFIGURATION -> "Gateway configuration is invalid";
            case UNAUTHORIZED -> "Missing or invalid API key";
            case FORBIDDEN -> "Access denied";
            case PAYLOAD_TOO_LARGE -> "Request body exceeds the configured limit";
            case UNSUPPORTED_MEDIA_TYPE -> "Unsupported media type";
            case METHOD_NOT_ALLOWED -> "HTTP method is not supported";
            case RESOURCE_NOT_FOUND -> "Resource not found";
            case OVERLOADED -> "Gateway capacity is exhausted";
            case INTERNAL_ERROR -> "An unexpected error occurred";
        };
    }
}
