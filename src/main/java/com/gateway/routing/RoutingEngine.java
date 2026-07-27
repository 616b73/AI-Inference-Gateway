package com.gateway.routing;

import com.gateway.config.ModelConfigRepository;
import com.gateway.config.ProviderConfig;
import com.gateway.config.ProviderConfigRepository;
import com.gateway.error.ErrorCode;
import com.gateway.error.GatewayException;
import com.gateway.inference.InferenceRequest;
import com.gateway.provider.AIProvider;
import com.gateway.provider.ProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Resolves which {@link AIProvider} should handle a given inference request.
 * <p>
 * Resolution strategy (Architecture.md §3, Phases.md Phase 1):
 * <ol>
 *   <li>If {@code request.getProvider()} is set → look up in {@link ProviderRegistry}.</li>
 *   <li>If not set → fall back to the active default provider from the DB.</li>
 *   <li>Validate the requested model exists for the resolved provider.</li>
 * </ol>
 */
@Service
public class RoutingEngine {

    private static final Logger log = LoggerFactory.getLogger(RoutingEngine.class);

    private final ProviderRegistry providerRegistry;
    private final ProviderConfigRepository providerConfigRepository;
    private final ModelConfigRepository modelConfigRepository;

    public RoutingEngine(ProviderRegistry providerRegistry,
                         ProviderConfigRepository providerConfigRepository,
                         ModelConfigRepository modelConfigRepository) {
        this.providerRegistry = providerRegistry;
        this.providerConfigRepository = providerConfigRepository;
        this.modelConfigRepository = modelConfigRepository;
    }

    /**
     * Resolve the provider for a given inference request.
     *
     * @param request the inference request
     * @return a {@link RoutingResult} containing the resolved provider adapter and provider name
     * @throws GatewayException with appropriate error code if resolution fails
     */
    public RoutingResult resolve(InferenceRequest request) {
        String providerName;
        AIProvider provider;

        if (request.getProvider() != null && !request.getProvider().isBlank()) {
            // Explicit provider specified
            providerName = request.getProvider();
            provider = providerRegistry.getProvider(providerName)
                    .orElseThrow(() -> new GatewayException(ErrorCode.PROVIDER_NOT_FOUND,
                            "Provider not found or not active: " + providerName));
        } else {
            // Fall back to default provider
            ProviderConfig defaultConfig = providerConfigRepository
                    .findByDefaultProviderTrueAndActiveTrue()
                    .orElseThrow(() -> new GatewayException(ErrorCode.BAD_CONFIGURATION,
                            "No active default provider configured"));
            providerName = defaultConfig.getName();
            provider = providerRegistry.getProvider(providerName)
                    .orElseThrow(() -> new GatewayException(ErrorCode.BAD_CONFIGURATION,
                            "Default provider '" + providerName + "' is not registered in the adapter registry"));
        }

        // Validate the model exists for this provider
        validateModel(providerName, request.getModel());

        log.debug("Resolved provider '{}' for model '{}'", providerName, request.getModel());
        return new RoutingResult(provider, providerName);
    }

    private void validateModel(String providerName, String modelName) {
        ProviderConfig config = providerConfigRepository.findByName(providerName)
                .orElseThrow(() -> new GatewayException(ErrorCode.PROVIDER_NOT_FOUND,
                        "Provider config not found: " + providerName));

        boolean modelExists = modelConfigRepository
                .findByProviderIdAndActiveTrue(config.getId())
                .stream()
                .anyMatch(m -> m.getName().equals(modelName));

        if (!modelExists) {
            throw new GatewayException(ErrorCode.MODEL_NOT_FOUND,
                    "Model '" + modelName + "' not found for provider '" + providerName + "'");
        }
    }

    /**
     * Result of routing resolution.
     *
     * @param provider     the resolved AIProvider adapter
     * @param providerName the name of the resolved provider
     */
    public record RoutingResult(AIProvider provider, String providerName) {
    }
}
