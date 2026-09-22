package com.gateway.provider.ollama;

import com.gateway.config.*;
import com.gateway.error.*;
import com.gateway.inference.InferenceRequest;
import com.gateway.provider.ProviderTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OllamaProviderTest {
    private final ProviderTransport transport = mock(ProviderTransport.class);
    private final ModelConfigRepository models = mock(ModelConfigRepository.class);
    private final UUID id = UUID.randomUUID();
    private OllamaProvider provider;
    @BeforeEach void prepare() {
        provider = new OllamaProvider(ProviderConfig.builder().id(id).name("ollama-local").type("ollama")
                .baseUrl("http://localhost:11434").build(), models, transport, JsonMapper.builder().build());
    }
    private InferenceRequest request() { return InferenceRequest.builder().model("qwen3").prompt("hello").build(); }
    @Test void mapsValidResponse() {
        when(transport.exchange(eq("POST"), any(), any())).thenReturn("{\"response\":\"hello\",\"done\":true}".getBytes());
        var response = provider.infer(request());
        assertThat(response.getText()).isEqualTo("hello");
        assertThat(response.getProvider()).isEqualTo("ollama-local");
    }
    @Test void rejectsMalformedOrIncompleteResponseWithoutLeakingBody() {
        for (String body : List.of("SECRET_RAW_PAYLOAD", "{}", "{\"response\":17,\"done\":true}", "{\"response\":\"secret\",\"done\":false}")) {
            when(transport.exchange(eq("POST"), any(), any())).thenReturn(body.getBytes());
            assertThatThrownBy(() -> provider.infer(request())).isInstanceOf(GatewayException.class)
                    .hasMessage("Provider is unavailable");
        }
    }
    @Test void infoReadsConfiguredModels() {
        when(models.findByProviderIdAndActiveTrue(id)).thenReturn(List.of(ModelConfig.builder().name("qwen3").build()));
        assertThat(provider.info().models()).containsExactly("qwen3");
    }
    @Test void healthDoesNotExposeExceptionText() {
        when(transport.exchange(eq("GET"), any(), isNull())).thenThrow(new RuntimeException("SECRET_ENDPOINT"));
        assertThat(provider.health().healthy()).isFalse();
        assertThat(provider.health().details()).isEqualTo("Provider probe failed");
    }
    @Test void invalidEndpointsAreRejectedWithoutLeakingTheirValues() {
        for (String endpoint : List.of("http://user:SECRET@host", "http://SECRET/%broken", "file:///SECRET")) {
            assertThatThrownBy(() -> new OllamaProvider(ProviderConfig.builder().baseUrl(endpoint).build(),
                    models, transport, JsonMapper.builder().build()))
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_CONFIGURATION)
                    .hasMessage("Gateway configuration is invalid").hasNoCause();
        }
    }
}
