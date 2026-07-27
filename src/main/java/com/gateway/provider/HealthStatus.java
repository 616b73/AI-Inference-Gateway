package com.gateway.provider;

/**
 * Health check result for a provider, returned by {@link AIProvider#health()}.
 *
 * @param healthy whether the provider is reachable and responding
 * @param details optional detail message (e.g., connection error text), null if healthy
 */
public record HealthStatus(
        boolean healthy,
        String details
) {
    /** Convenience factory for a healthy status. */
    public static HealthStatus up() {
        return new HealthStatus(true, null);
    }

    /** Convenience factory for an unhealthy status with a reason. */
    public static HealthStatus down(String reason) {
        return new HealthStatus(false, reason);
    }
}
