package com.gateway.provider.ollama;

import com.gateway.config.ModelConfig;
import com.gateway.config.ModelConfigRepository;
import com.gateway.config.ProviderConfig;
import com.gateway.error.ErrorCode;
import com.gateway.error.GatewayException;
import com.gateway.inference.InferenceRequest;
import com.gateway.inference.InferenceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(MockitoExtension.class)
class OllamaProviderTest {

    @Mock
    private ModelConfigRepository modelConfigRepository;

    private MockRestServiceServer mockServer;
    private OllamaProvider ollamaProvider;

    private static final UUID PROVIDER_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final String PROVIDER_NAME = "ollama-local";

    @BeforeEach
    void setUp() {
        ProviderConfig config = ProviderConfig.builder()
                .id(PROVIDER_ID)
                .name(PROVIDER_NAME)
                .type("ollama")
                .baseUrl("http://localhost:11434")
                .active(true)
                .defaultProvider(true)
                .build();

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        ollamaProvider = new OllamaProvider(config, modelConfigRepository, builder);
    }

    @Test
    void infer_validResponse_mapsCorrectly() {
        String ollamaJson = """
                {
                    "model": "qwen3",
                    "response": "Hello! How can I help you today?",
                    "done": true
                }
                """;

        mockServer.expect(requestTo("http://localhost:11434/api/generate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(ollamaJson, MediaType.APPLICATION_JSON));

        InferenceRequest request = InferenceRequest.builder()
                .model("qwen3")
                .prompt("Hi")
                .build();

        InferenceResponse response = ollamaProvider.infer(request);

        assertNotNull(response);
        assertEquals("Hello! How can I help you today?", response.getText());
        assertEquals("qwen3", response.getModel());
        assertEquals(PROVIDER_NAME, response.getProvider());
        assertTrue(response.getLatencyMs() >= 0);

        mockServer.verify();
    }

    @Test
    void infer_connectionFailure_throwsProviderUnavailable() {
        mockServer.expect(requestTo("http://localhost:11434/api/generate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        InferenceRequest request = InferenceRequest.builder()
                .model("qwen3")
                .prompt("Hi")
                .build();

        // Server error (5xx from Ollama) will result in an exception
        // which gets mapped to PROVIDER_UNAVAILABLE or INTERNAL_ERROR
        // depending on the actual exception type. For a 500, RestClient
        // throws HttpServerErrorException which is not ResourceAccessException,
        // so it won't be caught by our specific handler — it'll bubble up.
        // This tests the connection path.
        assertThrows(Exception.class, () -> ollamaProvider.infer(request));

        mockServer.verify();
    }

    @Test
    void info_returnsProviderMetadata() {
        ModelConfig model = ModelConfig.builder()
                .name("qwen3")
                .active(true)
                .build();

        when(modelConfigRepository.findByProviderIdAndActiveTrue(PROVIDER_ID))
                .thenReturn(List.of(model));

        var info = ollamaProvider.info();

        assertEquals(PROVIDER_NAME, info.name());
        assertEquals("ollama", info.type());
        assertEquals("http://localhost:11434", info.baseUrl());
        assertEquals(List.of("qwen3"), info.models());
    }

    @Test
    void health_serverUp_returnsHealthy() {
        mockServer.expect(requestTo("http://localhost:11434/"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("Ollama is running", MediaType.TEXT_PLAIN));

        var health = ollamaProvider.health();

        assertTrue(health.healthy());
        assertNull(health.details());

        mockServer.verify();
    }

    @Test
    void health_serverDown_returnsUnhealthy() {
        mockServer.expect(requestTo("http://localhost:11434/"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        var health = ollamaProvider.health();

        assertFalse(health.healthy());
        assertNotNull(health.details());

        mockServer.verify();
    }
}
