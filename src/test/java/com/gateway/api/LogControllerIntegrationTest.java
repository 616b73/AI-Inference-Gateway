package com.gateway.api;

import com.gateway.auth.ApiKey;
import com.gateway.auth.ApiKeyRepository;
import com.gateway.logging.RequestLog;
import com.gateway.logging.RequestLogRepository;
import com.gateway.provider.AIProvider;
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
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@code GET /v1/logs}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LogControllerIntegrationTest {

    private static final String VALID_RAW_KEY = "log-test-key";
    private static final String LOGS_URL = "/v1/logs";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private RequestLogRepository requestLogRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    // Mock ProviderRegistry to prevent PostConstruct from loading real providers
    @MockitoBean
    private ProviderRegistry providerRegistry;

    @MockitoBean(name = "ollamaProvider")
    private AIProvider mockProvider;

    @BeforeEach
    void setUp() {
        requestLogRepository.deleteAll();
        apiKeyRepository.deleteAll();

        ApiKey testKey = ApiKey.builder()
                .id(UUID.randomUUID())
                .keyHash(passwordEncoder.encode(VALID_RAW_KEY))
                .label("log-test")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
        apiKeyRepository.save(testKey);

        // Seed 5 log entries with descending timestamps
        for (int i = 1; i <= 5; i++) {
            RequestLog log = RequestLog.builder()
                    .id(UUID.randomUUID())
                    .requestId("req_log_" + i)
                    .timestamp(LocalDateTime.now().minusMinutes(5 - i))
                    .provider("ollama-local")
                    .model("qwen3")
                    .status("SUCCESS")
                    .latencyMs(100 + i * 10)
                    .build();
            requestLogRepository.save(log);
        }
    }

    @Test
    void getLogs_defaultPagination_returns200WithAllEntries() throws Exception {
        mockMvc.perform(get(LOGS_URL)
                        .header("X-API-Key", VALID_RAW_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void getLogs_customPageSize_returnsPaginatedResult() throws Exception {
        mockMvc.perform(get(LOGS_URL + "?page=0&size=2")
                        .header("X-API-Key", VALID_RAW_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void getLogs_secondPage_returnsCorrectEntries() throws Exception {
        mockMvc.perform(get(LOGS_URL + "?page=1&size=2")
                        .header("X-API-Key", VALID_RAW_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(5));
    }

    @Test
    void getLogs_orderedByTimestampDescending_mostRecentFirst() throws Exception {
        // req_log_5 has the latest timestamp, so it should be first
        mockMvc.perform(get(LOGS_URL + "?page=0&size=1")
                        .header("X-API-Key", VALID_RAW_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].requestId").value("req_log_5"));
    }

    @Test
    void getLogs_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(get(LOGS_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void getLogs_emptyLogs_returnsEmptyPage() throws Exception {
        requestLogRepository.deleteAll();

        mockMvc.perform(get(LOGS_URL)
                        .header("X-API-Key", VALID_RAW_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void getLogs_entryFields_matchExpectedShape() throws Exception {
        mockMvc.perform(get(LOGS_URL + "?page=0&size=1")
                        .header("X-API-Key", VALID_RAW_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].requestId").exists())
                .andExpect(jsonPath("$.content[0].timestamp").exists())
                .andExpect(jsonPath("$.content[0].provider").value("ollama-local"))
                .andExpect(jsonPath("$.content[0].model").value("qwen3"))
                .andExpect(jsonPath("$.content[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.content[0].latencyMs").isNumber())
                // Internal UUID should NOT be exposed
                .andExpect(jsonPath("$.content[0].id").doesNotExist());
    }
}
