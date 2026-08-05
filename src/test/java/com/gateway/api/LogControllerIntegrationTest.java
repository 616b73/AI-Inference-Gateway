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
 * Integration tests for {@code GET /v1/logs} including pagination and filtering.
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

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 8, 1, 12, 0, 0);

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

        // Seed diverse log entries for filter testing
        // Entry 1: ollama-local, SUCCESS, Aug 1 12:00
        saveLog("req_1", BASE_TIME, "ollama-local", "qwen3", "SUCCESS", null, 100);
        // Entry 2: ollama-local, FAILURE, Aug 2 12:00
        saveLog("req_2", BASE_TIME.plusDays(1), "ollama-local", "qwen3", "FAILURE", "PROVIDER_UNAVAILABLE", 50);
        // Entry 3: openai-prod, SUCCESS, Aug 3 12:00
        saveLog("req_3", BASE_TIME.plusDays(2), "openai-prod", "gpt-4", "SUCCESS", null, 200);
        // Entry 4: ollama-local, SUCCESS, Aug 4 12:00
        saveLog("req_4", BASE_TIME.plusDays(3), "ollama-local", "qwen3", "SUCCESS", null, 150);
        // Entry 5: openai-prod, FAILURE, Aug 5 12:00
        saveLog("req_5", BASE_TIME.plusDays(4), "openai-prod", "gpt-4", "FAILURE", "PROVIDER_TIMEOUT", 5000);
    }

    private void saveLog(String requestId, LocalDateTime timestamp, String provider,
                         String model, String status, String errorCode, int latencyMs) {
        RequestLog log = RequestLog.builder()
                .id(UUID.randomUUID())
                .requestId(requestId)
                .timestamp(timestamp)
                .provider(provider)
                .model(model)
                .status(status)
                .errorCode(errorCode)
                .latencyMs(latencyMs)
                .build();
        requestLogRepository.save(log);
    }

    // --- Pagination tests ---

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
        // req_5 has the latest timestamp (Aug 5), should be first
        mockMvc.perform(get(LOGS_URL + "?page=0&size=1")
                        .header("X-API-Key", VALID_RAW_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].requestId").value("req_5"));
    }

    // --- Auth tests ---

    @Test
    void getLogs_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(get(LOGS_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    // --- Filter: provider ---

    @Test
    void getLogs_filterByProvider_returnsOnlyMatchingProvider() throws Exception {
        mockMvc.perform(get(LOGS_URL + "?provider=ollama-local")
                        .header("X-API-Key", VALID_RAW_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void getLogs_filterByProvider_noMatch_returnsEmpty() throws Exception {
        mockMvc.perform(get(LOGS_URL + "?provider=nonexistent")
                        .header("X-API-Key", VALID_RAW_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // --- Filter: status ---

    @Test
    void getLogs_filterByStatus_returnsOnlyMatchingStatus() throws Exception {
        mockMvc.perform(get(LOGS_URL + "?status=FAILURE")
                        .header("X-API-Key", VALID_RAW_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void getLogs_filterByStatusSuccess_returnsOnlySuccess() throws Exception {
        mockMvc.perform(get(LOGS_URL + "?status=SUCCESS")
                        .header("X-API-Key", VALID_RAW_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    // --- Filter: date range ---

    @Test
    void getLogs_filterByDateRange_returnsOnlyInRange() throws Exception {
        // from Aug 2 to Aug 4 should return entries 2, 3, 4
        mockMvc.perform(get(LOGS_URL + "?from=2026-08-02T00:00:00&to=2026-08-04T23:59:59")
                        .header("X-API-Key", VALID_RAW_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void getLogs_filterByFromOnly_returnsFromDateOnward() throws Exception {
        // from Aug 4 onward should return entries 4, 5
        mockMvc.perform(get(LOGS_URL + "?from=2026-08-04T00:00:00")
                        .header("X-API-Key", VALID_RAW_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void getLogs_filterByToOnly_returnsUpToDate() throws Exception {
        // up to Aug 2 should return entries 1, 2
        mockMvc.perform(get(LOGS_URL + "?to=2026-08-02T23:59:59")
                        .header("X-API-Key", VALID_RAW_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    // --- Combined filters ---

    @Test
    void getLogs_combinedProviderAndStatus_returnsIntersection() throws Exception {
        // ollama-local + FAILURE = entry 2 only
        mockMvc.perform(get(LOGS_URL + "?provider=ollama-local&status=FAILURE")
                        .header("X-API-Key", VALID_RAW_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].requestId").value("req_2"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getLogs_combinedAllFilters_returnsCorrectResult() throws Exception {
        // ollama-local + SUCCESS + Aug 3 to Aug 5 = entry 4 only
        mockMvc.perform(get(LOGS_URL + "?provider=ollama-local&status=SUCCESS&from=2026-08-03T00:00:00&to=2026-08-05T23:59:59")
                        .header("X-API-Key", VALID_RAW_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].requestId").value("req_4"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // --- Shape tests ---

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
                .andExpect(jsonPath("$.content[0].provider").exists())
                .andExpect(jsonPath("$.content[0].model").exists())
                .andExpect(jsonPath("$.content[0].status").exists())
                .andExpect(jsonPath("$.content[0].latencyMs").isNumber())
                // Internal UUID should NOT be exposed
                .andExpect(jsonPath("$.content[0].id").doesNotExist());
    }
}
