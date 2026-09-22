package com.gateway.provider.ollama;

import com.gateway.config.ModelConfig;
import com.gateway.config.ModelConfigRepository;
import com.gateway.config.ProviderConfig;
import com.gateway.error.ErrorCode;
import com.gateway.error.GatewayException;
import com.gateway.inference.InferenceRequest;
import com.gateway.inference.InferenceResponse;
import com.gateway.provider.*;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Typed Ollama boundary using bounded transport; never retains raw provider errors. */
public class OllamaProvider implements AIProvider {
    private final ProviderConfig config;
    private final ModelConfigRepository models;
    private final ProviderTransport transport;
    private final ObjectMapper mapper;
    public OllamaProvider(ProviderConfig config, ModelConfigRepository models,
                          ProviderTransport transport, ObjectMapper mapper) {
        this.config = config; this.models = models; this.transport = transport; this.mapper = mapper;
        URI endpoint;
        try {
            endpoint = URI.create(config.getBaseUrl());
        } catch (IllegalArgumentException | NullPointerException invalidEndpoint) {
            throw new GatewayException(ErrorCode.BAD_CONFIGURATION, ErrorCode.BAD_CONFIGURATION.safeMessage());
        }
        if (!("http".equals(endpoint.getScheme()) || "https".equals(endpoint.getScheme()))
                || endpoint.getHost() == null || endpoint.getUserInfo() != null
                || endpoint.getQuery() != null || endpoint.getFragment() != null) {
            throw new GatewayException(ErrorCode.BAD_CONFIGURATION, ErrorCode.BAD_CONFIGURATION.safeMessage());
        }
    }
    @Override public InferenceResponse infer(InferenceRequest request) {
        long start = System.nanoTime();
        byte[] body = mapper.writeValueAsBytes(Map.of("model", request.getModel(), "prompt", request.getPrompt(), "stream", false));
        byte[] bytes = transport.exchange("POST", endpoint("/api/generate"), body);
        try {
            var json = mapper.readTree(bytes);
            if (json == null || !json.path("response").isString() || !json.path("done").asBoolean(false)) {
                throw new GatewayException(ErrorCode.PROVIDER_UNAVAILABLE, ErrorCode.PROVIDER_UNAVAILABLE.safeMessage());
            }
            return InferenceResponse.builder().text(json.get("response").asString()).model(request.getModel())
                    .provider(config.getName()).latencyMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).build();
        } catch (GatewayException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new GatewayException(ErrorCode.PROVIDER_UNAVAILABLE, ErrorCode.PROVIDER_UNAVAILABLE.safeMessage());
        }
    }
    @Override public ProviderInfo info() {
        return new ProviderInfo(config.getName(), config.getType(), config.getBaseUrl(),
                models.findByProviderIdAndActiveTrue(config.getId()).stream().map(ModelConfig::getName).toList());
    }
    @Override public HealthStatus health() {
        try { transport.exchange("GET", endpoint("/"), null); return HealthStatus.up(); }
        catch (RuntimeException ex) { return HealthStatus.down("Provider probe failed"); }
    }
    private URI endpoint(String path) { return URI.create(config.getBaseUrl().replaceAll("/+$", "") + path); }
}
