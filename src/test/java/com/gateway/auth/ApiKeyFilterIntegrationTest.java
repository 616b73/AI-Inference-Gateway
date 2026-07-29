package com.gateway.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for API key authentication.
 * Uses the full Spring Boot context (H2 + auto-DDL) with a dummy /v1/test-auth endpoint
 * to verify the auth filter accepts/rejects requests correctly.
 * <p>
 * Seeds a test API key into the H2 database before each test so both valid-key
 * and invalid-key paths can be verified.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiKeyFilterIntegrationTest {

    private static final String VALID_RAW_KEY = "integration-test-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    /**
     * Dummy controller that exists only in the test context.
     * Provides a /v1/ endpoint to test auth against, since real controllers
     * don't exist until Phase 3/4.
     */
    @TestConfiguration
    static class TestConfig {
        @RestController
        static class DummyController {
            @GetMapping("/v1/test-auth")
            public String testAuth() {
                return "authenticated";
            }
        }
    }

    @BeforeEach
    void seedTestKey() {
        apiKeyRepository.deleteAll();
        ApiKey testKey = ApiKey.builder()
                .id(UUID.randomUUID())
                .keyHash(passwordEncoder.encode(VALID_RAW_KEY))
                .label("integration-test")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
        apiKeyRepository.save(testKey);
    }

    @Test
    void request_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(get("/v1/test-auth")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Missing API key — provide X-API-Key header"))
                .andExpect(jsonPath("$.path").value("/v1/test-auth"))
                .andExpect(jsonPath("$.requestId").value(startsWith("req_")))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void request_withInvalidApiKey_returns401() throws Exception {
        mockMvc.perform(get("/v1/test-auth")
                        .header("X-API-Key", "invalid-key-12345")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Invalid API key"))
                .andExpect(jsonPath("$.requestId").value(startsWith("req_")));
    }

    @Test
    void request_withValidApiKey_returns200() throws Exception {
        mockMvc.perform(get("/v1/test-auth")
                        .header("X-API-Key", VALID_RAW_KEY)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string("authenticated"));
    }

    @Test
    void actuatorHealth_withoutApiKey_returns200() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void unauthorizedResponse_containsXRequestIdHeader() throws Exception {
        mockMvc.perform(get("/v1/test-auth")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(header().string("X-Request-Id", startsWith("req_")));
    }

    @Test
    void request_withEmptyApiKey_returns401() throws Exception {
        mockMvc.perform(get("/v1/test-auth")
                        .header("X-API-Key", "")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Missing API key — provide X-API-Key header"));
    }

    @Test
    void validApiKey_responseShouldNotContainUnauthorizedFields() throws Exception {
        // Verify that a valid-key response is a clean 200 with no error fields
        mockMvc.perform(get("/v1/test-auth")
                        .header("X-API-Key", VALID_RAW_KEY)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").doesNotExist());
    }
}
