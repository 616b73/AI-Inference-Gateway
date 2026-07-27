package com.gateway.error;

import com.gateway.common.RequestIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for {@link GlobalExceptionHandler}.
 * Uses standalone MockMvc setup with a fake controller to avoid Spring Boot
 * auto-configuration interference (BasicErrorController, etc.).
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void gatewayException_providerNotFound_returns404() throws Exception {
        mockMvc.perform(get("/test/provider-not-found")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("PROVIDER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Test provider not found"))
                .andExpect(jsonPath("$.path").value("/test/provider-not-found"))
                .andExpect(jsonPath("$.requestId").value(startsWith("req_")))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void gatewayException_badConfiguration_returns500() throws Exception {
        mockMvc.perform(get("/test/bad-config")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("BAD_CONFIGURATION"))
                .andExpect(jsonPath("$.message").value("No default provider"))
                .andExpect(jsonPath("$.requestId").value(startsWith("req_")));
    }

    @Test
    void uncaughtException_returns500_noStackTraceLeak() throws Exception {
        mockMvc.perform(get("/test/unexpected")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.requestId").value(startsWith("req_")))
                // Ensure stack trace is NOT leaked
                .andExpect(jsonPath("$.message").value(not(containsString("NullPointerException"))));
    }

    @Test
    void responseContains_xRequestIdHeader() throws Exception {
        mockMvc.perform(get("/test/provider-not-found")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(header().string("X-Request-Id", startsWith("req_")));
    }

    /**
     * Fake controller used only by these tests to trigger specific exceptions.
     */
    @RestController
    static class TestController {

        @GetMapping("/test/provider-not-found")
        public String providerNotFound() {
            throw new GatewayException(ErrorCode.PROVIDER_NOT_FOUND, "Test provider not found");
        }

        @GetMapping("/test/bad-config")
        public String badConfig() {
            throw new GatewayException(ErrorCode.BAD_CONFIGURATION, "No default provider");
        }

        @GetMapping("/test/unexpected")
        public String unexpected() {
            throw new NullPointerException("should not be visible to client");
        }
    }
}
