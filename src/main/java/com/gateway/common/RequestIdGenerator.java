package com.gateway.common;

import java.util.UUID;

/**
 * Generates unique request IDs in the format {@code req_xxxxxxxxx}.
 * Used by {@link RequestIdFilter} to tag every inbound request.
 */
public final class RequestIdGenerator {

    private RequestIdGenerator() {
        // utility class
    }

    /**
     * Generate a new request ID.
     *
     * @return a string in the format "req_{uuid}", e.g. "req_123e4567-e89b-12d3-a456-426614174000"
     */
    public static String generate() {
        return "req_" + UUID.randomUUID();
    }
}
