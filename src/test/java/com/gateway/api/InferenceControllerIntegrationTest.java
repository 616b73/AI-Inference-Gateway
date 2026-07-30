package com.gateway.api;

import com.gateway.auth.ApiKey;
import com.gateway.auth.ApiKeyRepository;
import com.gateway.config.ModelConfig;
import com.gateway.config.ModelConfigRepository;
import com.gateway.config.ProviderConfig;
import com.gateway.config.ProviderConfigRepository;
import com.gateway.inference.InferenceResponse;
import com.gateway.logging.RequestLogRepository;
import com.gateway.provider.AIProvider;
import com.gateway.provider.ProviderRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@code POST /v1/inference}.
 * Uses {@code @MockitoBean} to mock the provider layer (tests run with H2, no Ollama).
 * Seeds ProviderConfig, ModelConfig, and ApiKey data into H2 for routing to work.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InferenceControllerIntegrationTest {

    private static final String VALID_RAW_KEY = "integration-test-key";
    private static final String INFERENCE_URL = "/v1/inference";
    private static final UUID PROVIDER_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private ProviderConfigRepository providerConfigRepository;

    @Autowired
    private ModelConfigRepository modelConfigRepository;

    @Autowired
    private RequestLogRepository requestLogRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @MockitoBean
    private ProviderRegistry providerRegistry;

    @MockitoBean(name = "ollamaProvider")
    private AIProvider mockProvider;

    @BeforeEach
    void setUp() {
        // Clear existing data
        requestLogRepository.deleteAll();
        modelConfigRepository.deleteAll();
        providerConfigRepository.deleteAll();
        apiKeyRepository.deleteAll();

        // Seed provider config (needed by RoutingEngine for model validation)
        ProviderConfig providerConfig = ProviderConfig.builder()
                .id(PROVIDER_ID)
                .name("ollama-local")
                .type("ollama")
                .baseUrl("http://localhost:11434")
                .defaultProvider(true)
                .active(true)
                .build();
        providerConfigRepository.save(providerConfig);

        // Seed model config
        ModelConfig modelConfig = ModelConfig.builder()
                .id(UUID.randomUUID())
                .provider(providerConfig)
                .name("qwen3")
                .active(true)
                .build();
        modelConfigRepository.save(modelConfig);

        // Seed API key
        ApiKey testKey = ApiKey.builder()
                .id(UUID.randomUUID())
                .keyHash(passwordEncoder.encode(VALID_RAW_KEY))
                .label("integration-test")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
        apiKeyRepository.save(testKey);

        // Configure mock provider registry
        when(providerRegistry.getProvider("ollama-local")).thenReturn(Optional.of(mockProvider));
        when(providerRegistry.getAllProviders()).thenReturn(Map.of("ollama-local", mockProvider));
    }

    @Test
    void inference_happyPath_returns200WithResponse() throws Exception {
        InferenceResponse providerResponse = InferenceResponse.builder()
                .text("Four")
                .model("qwen3")
                .provider("ollama-local")
                .latencyMs(100)
                .build();
        when(mockProvider.infer(any())).thenReturn(providerResponse);

        mockMvc.perform(post(INFERENCE_URL)
                        .header("X-API-Key", VALID_RAW_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"qwen3\",\"prompt\":\"What is 2+2?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Four"))
                .andExpect(jsonPath("$.model").value("qwen3"))
                .andExpect(jsonPath("$.provider").value("ollama-local"))
                .andExpect(jsonPath("$.requestId").value(startsWith("req_")))
                .andExpect(jsonPath("$.latencyMs").isNumber());
    }

    @Test
    void inference_withExplicitProvider_routesToThatProvider() throws Exception {
        InferenceResponse providerResponse = InferenceResponse.builder()
                .text("Routed response")
                .model("qwen3")
                .provider("ollama-local")
                .latencyMs(50)
                .build();
        when(mockProvider.infer(any())).thenReturn(providerResponse);

        mockMvc.perform(post(INFERENCE_URL)
                        .header("X-API-Key", VALID_RAW_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"ollama-local\",\"model\":\"qwen3\",\"prompt\":\"Hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Routed response"))
                .andExpect(jsonPath("$.provider").value("ollama-local"));
    }

    @Test
    void inference_missingPrompt_returns400() throws Exception {
        mockMvc.perform(post(INFERENCE_URL)
                        .header("X-API-Key", VALID_RAW_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"qwen3\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.requestId").value(startsWith("req_")));
    }

    @Test
    void inference_missingModel_returns400() throws Exception {
        mockMvc.perform(post(INFERENCE_URL)
                        .header("X-API-Key", VALID_RAW_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Hello\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void inference_emptyBody_returns400() throws Exception {
        mockMvc.perform(post(INFERENCE_URL)
                        .header("X-API-Key", VALID_RAW_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void inference_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(post(INFERENCE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"qwen3\",\"prompt\":\"Hello\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void inference_successfulCall_createsRequestLogEntry() throws Exception {
        InferenceResponse providerResponse = InferenceResponse.builder()
                .text("Logged response")
                .model("qwen3")
                .provider("ollama-local")
                .latencyMs(75)
                .build();
        when(mockProvider.infer(any())).thenReturn(providerResponse);

        mockMvc.perform(post(INFERENCE_URL)
                        .header("X-API-Key", VALID_RAW_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"qwen3\",\"prompt\":\"Log test\"}"))
                .andExpect(status().isOk());

        // Verify exactly one request log entry was created
        var logs = requestLogRepository.findAll();
        assertThat(logs).hasSize(1);

        var logEntry = logs.get(0);
        assertThat(logEntry.getRequestId()).startsWith("req_");
        assertThat(logEntry.getProvider()).isEqualTo("ollama-local");
        assertThat(logEntry.getModel()).isEqualTo("qwen3");
        assertThat(logEntry.getStatus()).isEqualTo("SUCCESS");
        assertThat(logEntry.getErrorCode()).isNull();
        assertThat(logEntry.getLatencyMs()).isGreaterThanOrEqualTo(0);
    }
}
