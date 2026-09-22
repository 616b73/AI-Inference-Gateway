package com.gateway.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Explicit resource limits. Durations use milliseconds for unambiguous deployment configuration. */
@Getter
@Setter
@Validated
@ConfigurationProperties("gateway")
public class GatewayProperties {
    @Min(1) @Max(16777216) private int maxRequestBytes = 1048576;
    @Min(1) @Max(1024) private int maxConcurrentRequests = 64;
    @Min(1) @Max(600000) private int totalTimeoutMs = 120000;
    @Valid private Transport transport = new Transport();
    @Valid private Bootstrap bootstrap = new Bootstrap();

    @Getter @Setter
    public static class Transport {
        @Min(1) @Max(60000) private int connectTimeoutMs = 2000;
        @Min(1) @Max(600000) private int readTimeoutMs = 30000;
        @Min(1) @Max(60000) private int poolAcquireTimeoutMs = 250;
        @Min(1) @Max(512) private int maxConnections = 32;
        @Min(1) @Max(512) private int maxConnectionsPerRoute = 8;
        @Min(1) @Max(16777216) private int maxResponseBytes = 2097152;
    }

    @Getter @Setter
    public static class Bootstrap {
        private boolean localEnabled;
        private String apiKey = "";
        @NotBlank private String ollamaBaseUrl = "http://localhost:11434";
    }
}
