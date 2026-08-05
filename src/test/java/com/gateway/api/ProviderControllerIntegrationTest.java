package com.gateway.api;

import com.gateway.auth.ApiKey;
import com.gateway.auth.ApiKeyRepository;
import com.gateway.provider.AIProvider;
import com.gateway.provider.ProviderInfo;
import com.gateway.provider.ProviderRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@code GET /v1/providers}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProviderControllerIntegrationTest {

    private static final String VALID_RAW_KEY = "provider-test-key";
    private static final String PROVIDERS_URL = "/v1/providers";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @MockitoBean
    private ProviderRegistry providerRegistry;

    @MockitoBean(name = "ollamaProvider")
    private AIProvider mockProvider;

    @BeforeEach
    void setUp() {
        apiKeyRepository.deleteAll();

        ApiKey testKey = ApiKey.builder()
                .id(UUID.randomUUID())
                .keyHash(passwordEncoder.encode(VALID_RAW_KEY))
                .label("provider-test")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
        apiKeyRepository.save(testKey);

        // Configure mock provider registry
        ProviderInfo info = new ProviderInfo("ollama-local", "ollama", "http://localhost:11434", List.of("qwen3", "llama3"));
        when(mockProvider.info()).thenReturn(info);
        when(providerRegistry.getAllProviders()).thenReturn(Map.of("ollama-local", mockProvider));
    }

    @Test
    void listProviders_happyPath_returns200WithProviders() throws Exception {
        mockMvc.perform(get(PROVIDERS_URL)
                        .header("X-API-Key", VALID_RAW_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("ollama-local"))
                .andExpect(jsonPath("$[0].type").value("ollama"))
                .andExpect(jsonPath("$[0].models", hasSize(2)))
                .andExpect(jsonPath("$[0].models[0]").value("qwen3"))
                .andExpect(jsonPath("$[0].models[1]").value("llama3"));
    }

    @Test
    void listProviders_doesNotExposeBaseUrl() throws Exception {
        mockMvc.perform(get(PROVIDERS_URL)
                        .header("X-API-Key", VALID_RAW_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].baseUrl").doesNotExist());
    }

    @Test
    void listProviders_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(get(PROVIDERS_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void listProviders_emptyRegistry_returnsEmptyArray() throws Exception {
        when(providerRegistry.getAllProviders()).thenReturn(Map.of());

        mockMvc.perform(get(PROVIDERS_URL)
                        .header("X-API-Key", VALID_RAW_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
