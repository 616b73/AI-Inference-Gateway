package com.gateway.inference;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Standard inference response returned to the client.
 * All providers normalize their output into this shape.
 */
@Getter
@AllArgsConstructor
@Builder
public class InferenceResponse {

    /** Unique request tracking ID (e.g., "req_123e4567-..."). */
    private final String requestId;

    /** The AI-generated text output. */
    private final String text;

    /** The model that produced the response (e.g., "qwen3"). */
    private final String model;

    /** The provider that handled the request (e.g., "ollama-local"). */
    private final String provider;

    /** End-to-end latency in milliseconds. */
    private final long latencyMs;
}
