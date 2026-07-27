package com.gateway.provider;

import com.gateway.inference.InferenceRequest;
import com.gateway.inference.InferenceResponse;

/**
 * Contract for all AI provider adapters.
 * The gateway core never talks to a provider-specific SDK/API directly — only
 * through this interface (Architecture.md §4).
 * <p>
 * MVP implementation: {@link com.gateway.provider.ollama.OllamaProvider}.
 * Future: OpenAIProvider, ClaudeProvider, BedrockProvider, etc.
 */
public interface AIProvider {

    /**
     * Send a prompt to the AI model and return the response.
     *
     * @param request the inference request containing the model and prompt
     * @return normalized response with generated text, model, provider, and latency
     * @throws com.gateway.error.GatewayException on provider errors
     */
    InferenceResponse infer(InferenceRequest request);

    /**
     * Return metadata about this provider (name, type, available models).
     */
    ProviderInfo info();

    /**
     * Check if the provider is reachable and responding.
     */
    HealthStatus health();
}
