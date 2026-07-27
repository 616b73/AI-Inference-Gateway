package com.gateway.logging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity mapping to the {@code request_logs} table.
 * Stores metadata for every inference request — no prompt/response bodies (Rules.md §5).
 */
@Entity
@Table(name = "request_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestLog {

    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private String requestId;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    private String provider;

    private String model;

    @Column(nullable = false)
    private String status;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "latency_ms")
    private Integer latencyMs;
}
