package com.gateway.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Central exception handler — converts all exceptions into standardized
 * {@link ApiError} responses. No ad-hoc try/catch blocks in controllers (Rules.md §4).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles all gateway business errors (routing, provider, auth, etc.).
     */
    @ExceptionHandler(GatewayException.class)
    public ResponseEntity<ApiError> handleGatewayException(GatewayException ex, HttpServletRequest request) {
        ErrorCode code = ex.getErrorCode();
        String requestId = getRequestId(request);

        log.warn("Gateway error [{}] requestId={}: {}", code, requestId, ex.getMessage());

        ApiError error = ApiError.of(code, ex.getMessage(), request.getRequestURI(), requestId);
        return ResponseEntity.status(code.getHttpStatus()).body(error);
    }

    /**
     * Handles Jakarta validation failures (e.g., @NotBlank on InferenceRequest fields).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String requestId = getRequestId(request);

        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");

        log.warn("Validation error requestId={}: {}", requestId, message);

        ApiError error = ApiError.of(ErrorCode.INVALID_REQUEST, message, request.getRequestURI(), requestId);
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getHttpStatus()).body(error);
    }

    /**
     * Handles Spring Security access denied errors (e.g., authenticated but not authorized).
     * Returns 403 using the standard ApiError schema.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        String requestId = getRequestId(request);

        log.warn("Access denied requestId={}: {}", requestId, ex.getMessage());

        ApiError error = ApiError.of(ErrorCode.UNAUTHORIZED, "Access denied", request.getRequestURI(), requestId);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    /**
     * Catch-all for any unhandled exception — never leaks stack traces (Rules.md §4).
     * Also unwraps wrapped exceptions to check for GatewayException causes.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenericException(Exception ex, HttpServletRequest request) {
        // Check if the root cause is a GatewayException (can happen when Spring wraps exceptions)
        GatewayException gatewayException = findGatewayException(ex);
        if (gatewayException != null) {
            return handleGatewayException(gatewayException, request);
        }

        String requestId = getRequestId(request);

        log.error("Unexpected error requestId={}: {}", requestId, ex.getMessage(), ex);

        ApiError error = ApiError.of(
                ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred",
                request.getRequestURI(),
                requestId
        );
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus()).body(error);
    }

    private String getRequestId(HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return requestId != null ? requestId.toString() : "unknown";
    }

    /**
     * Walk the exception cause chain to find a GatewayException, if present.
     */
    private GatewayException findGatewayException(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof GatewayException ge) {
                return ge;
            }
            current = current.getCause();
        }
        return null;
    }
}
