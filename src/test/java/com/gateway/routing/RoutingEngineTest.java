package com.gateway.routing;

import com.gateway.config.ModelConfig;
import com.gateway.config.ModelConfigRepository;
import com.gateway.config.ProviderConfig;
import com.gateway.config.ProviderConfigRepository;
import com.gateway.error.ErrorCode;
import com.gateway.error.GatewayException;
import com.gateway.inference.InferenceRequest;
import com.gateway.provider.AIProvider;
import com.gateway.provider.ProviderRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoutingEngineTest {

    @Mock
    private ProviderRegistry providerRegistry;

    @Mock
    private ProviderConfigRepository providerConfigRepository;

    @Mock
    private ModelConfigRepository modelConfigRepository;

    @Mock
    private AIProvider mockProvider;

    private RoutingEngine routingEngine;

    private static final UUID PROVIDER_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final String PROVIDER_NAME = "ollama-local";
    private static final String MODEL_NAME = "qwen3";

    @BeforeEach
    void setUp() {
        routingEngine = new RoutingEngine(providerRegistry, providerConfigRepository, modelConfigRepository);
    }

    @Test
    void resolve_explicitProvider_resolvesCorrectly() {
        InferenceRequest request = InferenceRequest.builder()
                .provider(PROVIDER_NAME)
                .model(MODEL_NAME)
                .prompt("Hello")
                .build();

        ProviderConfig config = ProviderConfig.builder()
                .id(PROVIDER_ID)
                .name(PROVIDER_NAME)
                .build();

        ModelConfig model = ModelConfig.builder()
                .name(MODEL_NAME)
                .active(true)
                .build();

        when(providerRegistry.getProvider(PROVIDER_NAME)).thenReturn(Optional.of(mockProvider));
        when(providerConfigRepository.findByName(PROVIDER_NAME)).thenReturn(Optional.of(config));
        when(modelConfigRepository.findByProviderIdAndActiveTrue(PROVIDER_ID)).thenReturn(List.of(model));

        RoutingEngine.RoutingResult result = routingEngine.resolve(request);

        assertSame(mockProvider, result.provider());
        assertEquals(PROVIDER_NAME, result.providerName());
    }

    @Test
    void resolve_omittedProvider_fallsBackToDefault() {
        InferenceRequest request = InferenceRequest.builder()
                .model(MODEL_NAME)
                .prompt("Hello")
                .build();

        ProviderConfig defaultConfig = ProviderConfig.builder()
                .id(PROVIDER_ID)
                .name(PROVIDER_NAME)
                .defaultProvider(true)
                .active(true)
                .build();

        ModelConfig model = ModelConfig.builder()
                .name(MODEL_NAME)
                .active(true)
                .build();

        when(providerConfigRepository.findByDefaultProviderTrueAndActiveTrue())
                .thenReturn(Optional.of(defaultConfig));
        when(providerRegistry.getProvider(PROVIDER_NAME)).thenReturn(Optional.of(mockProvider));
        when(providerConfigRepository.findByName(PROVIDER_NAME)).thenReturn(Optional.of(defaultConfig));
        when(modelConfigRepository.findByProviderIdAndActiveTrue(PROVIDER_ID)).thenReturn(List.of(model));

        RoutingEngine.RoutingResult result = routingEngine.resolve(request);

        assertSame(mockProvider, result.provider());
        assertEquals(PROVIDER_NAME, result.providerName());
    }

    @Test
    void resolve_missingProvider_throwsProviderNotFound() {
        InferenceRequest request = InferenceRequest.builder()
                .provider("nonexistent")
                .model(MODEL_NAME)
                .prompt("Hello")
                .build();

        when(providerRegistry.getProvider("nonexistent")).thenReturn(Optional.empty());

        GatewayException ex = assertThrows(GatewayException.class, () -> routingEngine.resolve(request));
        assertEquals(ErrorCode.PROVIDER_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void resolve_noDefaultConfigured_throwsBadConfiguration() {
        InferenceRequest request = InferenceRequest.builder()
                .model(MODEL_NAME)
                .prompt("Hello")
                .build();

        when(providerConfigRepository.findByDefaultProviderTrueAndActiveTrue())
                .thenReturn(Optional.empty());

        GatewayException ex = assertThrows(GatewayException.class, () -> routingEngine.resolve(request));
        assertEquals(ErrorCode.BAD_CONFIGURATION, ex.getErrorCode());
    }

    @Test
    void resolve_modelNotFound_throwsModelNotFound() {
        InferenceRequest request = InferenceRequest.builder()
                .provider(PROVIDER_NAME)
                .model("nonexistent-model")
                .prompt("Hello")
                .build();

        ProviderConfig config = ProviderConfig.builder()
                .id(PROVIDER_ID)
                .name(PROVIDER_NAME)
                .build();

        ModelConfig model = ModelConfig.builder()
                .name(MODEL_NAME)
                .active(true)
                .build();

        when(providerRegistry.getProvider(PROVIDER_NAME)).thenReturn(Optional.of(mockProvider));
        when(providerConfigRepository.findByName(PROVIDER_NAME)).thenReturn(Optional.of(config));
        when(modelConfigRepository.findByProviderIdAndActiveTrue(PROVIDER_ID)).thenReturn(List.of(model));

        GatewayException ex = assertThrows(GatewayException.class, () -> routingEngine.resolve(request));
        assertEquals(ErrorCode.MODEL_NOT_FOUND, ex.getErrorCode());
    }
}
