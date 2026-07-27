package com.gateway.inference;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Inbound inference request DTO.
 * The {@code provider} field is optional — if omitted, the routing engine
 * falls back to the active default provider.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InferenceRequest {

    /**
     * Target provider name (e.g., "ollama-local"). Optional — if omitted,
     * the routing engine uses the active default provider.
     */
    private String provider;

    /**
     * Model name to use for inference (e.g., "qwen3").
     */
    @NotBlank(message = "model is required")
    private String model;

    /**
     * The prompt to send to the AI model.
     */
    @NotBlank(message = "prompt is required")
    private String prompt;
}
