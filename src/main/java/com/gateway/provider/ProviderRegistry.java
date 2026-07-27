package com.gateway.provider;

import com.gateway.config.ModelConfigRepository;
import com.gateway.config.ProviderConfig;
import com.gateway.config.ProviderConfigRepository;
import com.gateway.provider.ollama.OllamaProvider;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of active {@link AIProvider} instances.
 * Loads active providers from the database at startup and creates the
 * appropriate adapter for each based on the provider type.
 */
@Service
public class ProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(ProviderRegistry.class);

    private final ProviderConfigRepository providerConfigRepository;
    private final ModelConfigRepository modelConfigRepository;
    private final RestClient.Builder restClientBuilder;

    private Map<String, AIProvider> providers = Collections.emptyMap();

    public ProviderRegistry(ProviderConfigRepository providerConfigRepository,
                            ModelConfigRepository modelConfigRepository,
                            RestClient.Builder restClientBuilder) {
        this.providerConfigRepository = providerConfigRepository;
        this.modelConfigRepository = modelConfigRepository;
        this.restClientBuilder = restClientBuilder;
    }

    @PostConstruct
    public void init() {
        List<ProviderConfig> activeProviders = providerConfigRepository.findAll()
                .stream()
                .filter(ProviderConfig::isActive)
                .toList();

        Map<String, AIProvider> registry = new LinkedHashMap<>();

        for (ProviderConfig config : activeProviders) {
            AIProvider adapter = createAdapter(config);
            if (adapter != null) {
                registry.put(config.getName(), adapter);
                log.info("Registered provider: {} (type={})", config.getName(), config.getType());
            }
        }

        this.providers = Collections.unmodifiableMap(registry);
        log.info("ProviderRegistry initialized with {} provider(s)", providers.size());
    }

    /**
     * Look up a provider by name.
     *
     * @param name the provider name (e.g., "ollama-local")
     * @return the provider adapter, or empty if not found
     */
    public Optional<AIProvider> getProvider(String name) {
        return Optional.ofNullable(providers.get(name));
    }

    /**
     * Get all registered providers (unmodifiable).
     */
    public Map<String, AIProvider> getAllProviders() {
        return providers;
    }

    private AIProvider createAdapter(ProviderConfig config) {
        return switch (config.getType().toLowerCase()) {
            case "ollama" -> new OllamaProvider(config, modelConfigRepository, restClientBuilder);
            default -> {
                log.warn("Unknown provider type '{}' for provider '{}' — skipping",
                        config.getType(), config.getName());
                yield null;
            }
        };
    }
}
