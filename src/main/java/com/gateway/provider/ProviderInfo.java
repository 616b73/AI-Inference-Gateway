package com.gateway.provider;

import java.util.List;

/**
 * Metadata about a configured provider, returned by {@link AIProvider#info()}.
 *
 * @param name    provider name (e.g., "ollama-local")
 * @param type    provider type (e.g., "ollama", "openai")
 * @param baseUrl provider base URL
 * @param models  list of active model names available on this provider
 */
public record ProviderInfo(
        String name,
        String type,
        String baseUrl,
        List<String> models
) {
}
