package com.gateway.provider.ollama;

import com.gateway.config.ModelConfig;
import com.gateway.config.ModelConfigRepository;
import com.gateway.config.ProviderConfig;
import com.gateway.error.ErrorCode;
import com.gateway.error.GatewayException;
import com.gateway.inference.InferenceRequest;
import com.gateway.inference.InferenceResponse;
import com.gateway.provider.AIProvider;
import com.gateway.provider.HealthStatus;
import com.gateway.provider.ProviderInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * {@link AIProvider} implementation for Ollama.
 * Calls the local Ollama HTTP API via Spring {@link RestClient}.
 */
public class OllamaProvider implements AIProvider {

    private static final Logger log = LoggerFactory.getLogger(OllamaProvider.class);

    private final ProviderConfig config;
    private final ModelConfigRepository modelConfigRepository;
    private final RestClient restClient;

    public OllamaProvider(ProviderConfig config,
                          ModelConfigRepository modelConfigRepository,
                          RestClient.Builder restClientBuilder) {
        this.config = config;
        this.modelConfigRepository = modelConfigRepository;
        this.restClient = restClientBuilder
                .baseUrl(config.getBaseUrl())
                .build();
    }

    @Override
    public InferenceResponse infer(InferenceRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            Map<String, Object> ollamaRequest = Map.of(
                    "model", request.getModel(),
                    "prompt", request.getPrompt(),
                    "stream", false
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> ollamaResponse = restClient.post()
                    .uri("/api/generate")
                    .body(ollamaRequest)
                    .retrieve()
                    .body(Map.class);

            long latencyMs = System.currentTimeMillis() - startTime;

            if (ollamaResponse == null) {
                throw new GatewayException(ErrorCode.PROVIDER_UNAVAILABLE,
                        "Ollama returned an empty response");
            }

            String text = (String) ollamaResponse.get("response");

            return InferenceResponse.builder()
                    .text(text)
                    .model(request.getModel())
                    .provider(config.getName())
                    .latencyMs(latencyMs)
                    .build();

        } catch (ResourceAccessException ex) {
            long latencyMs = System.currentTimeMillis() - startTime;
            log.error("Ollama connection error after {}ms: {}", latencyMs, ex.getMessage());

            if (ex.getMessage() != null && ex.getMessage().contains("timed out")) {
                throw new GatewayException(ErrorCode.PROVIDER_TIMEOUT,
                        "Ollama request timed out", ex);
            }
            throw new GatewayException(ErrorCode.PROVIDER_UNAVAILABLE,
                    "Ollama is not reachable: " + ex.getMessage(), ex);
        }
    }

    @Override
    public ProviderInfo info() {
        List<String> modelNames = modelConfigRepository
                .findByProviderIdAndActiveTrue(config.getId())
                .stream()
                .map(ModelConfig::getName)
                .toList();

        return new ProviderInfo(
                config.getName(),
                config.getType(),
                config.getBaseUrl(),
                modelNames
        );
    }

    @Override
    public HealthStatus health() {
        try {
            restClient.get()
                    .uri("/")
                    .retrieve()
                    .toBodilessEntity();
            return HealthStatus.up();
        } catch (Exception ex) {
            log.warn("Ollama health check failed: {}", ex.getMessage());
            return HealthStatus.down(ex.getMessage());
        }
    }
}
