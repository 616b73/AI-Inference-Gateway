package com.gateway.error;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Standard error response shape returned by every failing API call.
 * Matches the schema defined in Rules.md §4:
 * <pre>
 * {
 *   "timestamp": "...",
 *   "status": 404,
 *   "error": "PROVIDER_NOT_FOUND",
 *   "message": "...",
 *   "path": "...",
 *   "requestId": "..."
 * }
 * </pre>
 */
@Getter
@AllArgsConstructor
@Builder
public class ApiError {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private final LocalDateTime timestamp;

    private final int status;

    private final String error;

    private final String message;

    private final String path;

    private final String requestId;

    /**
     * Factory method to build an ApiError from an ErrorCode.
     */
    public static ApiError of(ErrorCode errorCode, String message, String path, String requestId) {
        return ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(errorCode.getStatusCode())
                .error(errorCode.name())
                .message(message)
                .path(path)
                .requestId(requestId)
                .build();
    }
}
