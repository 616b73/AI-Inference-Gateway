package com.gateway.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Public errors and application logs never include exception messages or upstream payloads. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(GatewayException.class)
    public ResponseEntity<ApiError> handleGatewayException(GatewayException ex, HttpServletRequest request) {
        return error(ex.getErrorCode(), request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, ServletRequestBindingException.class,
            HandlerMethodValidationException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiError> invalidRequest(Exception ex, HttpServletRequest request) {
        return error(ErrorCode.INVALID_REQUEST, request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> unsupportedMedia(Exception ex, HttpServletRequest request) {
        return error(ErrorCode.UNSUPPORTED_MEDIA_TYPE, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> unsupportedMethod(Exception ex, HttpServletRequest request) {
        return error(ErrorCode.METHOD_NOT_ALLOWED, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> notFound(Exception ex, HttpServletRequest request) {
        return error(ErrorCode.RESOURCE_NOT_FOUND, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return error(ErrorCode.FORBIDDEN, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenericException(Exception ex, HttpServletRequest request) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = ex; cause != null && visited.add(cause); cause = cause.getCause()) {
            if (cause instanceof GatewayException gateway) return error(gateway.getErrorCode(), request);
        }
        return error(ErrorCode.INTERNAL_ERROR, request);
    }

    private ResponseEntity<ApiError> error(ErrorCode code, HttpServletRequest request) {
        String id = String.valueOf(request.getAttribute("requestId"));
        log.debug("Request rejected requestId={} code={}", id, code);
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiError.of(code, code.safeMessage(), request.getRequestURI(), id));
    }
}
