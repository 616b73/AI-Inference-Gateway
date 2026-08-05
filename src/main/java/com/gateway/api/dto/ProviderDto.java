package com.gateway.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * DTO exposing a provider and its active models to API consumers.
 * Maps from {@link com.gateway.provider.ProviderInfo} — intentionally omits
 * the base URL (internal implementation detail).
 */
@Getter
@Builder
@AllArgsConstructor
public class ProviderDto {

    private final String name;
    private final String type;
    private final List<String> models;
}
