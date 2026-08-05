package com.gateway.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * DTO exposing a request log entry to API consumers.
 * Maps from {@link com.gateway.logging.RequestLog} — excludes the internal UUID primary key.
 */
@Getter
@Builder
@AllArgsConstructor
public class RequestLogDto {

    private final String requestId;
    private final LocalDateTime timestamp;
    private final String provider;
    private final String model;
    private final String status;
    private final String errorCode;
    private final Integer latencyMs;
}
