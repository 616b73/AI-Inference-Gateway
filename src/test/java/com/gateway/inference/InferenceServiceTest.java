package com.gateway.inference;

import com.gateway.error.ErrorCode;
import com.gateway.error.GatewayException;
import com.gateway.logging.RequestLogService;
import com.gateway.provider.AIProvider;
import com.gateway.routing.RoutingEngine;
import com.gateway.routing.RoutingEngine.RoutingResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link InferenceService}.
 */
@ExtendWith(MockitoExtension.class)
class InferenceServiceTest {
    @Spy private MeterRegistry meters = new SimpleMeterRegistry();

    @Mock
    private RoutingEngine routingEngine;

    @Mock
    private RequestLogService requestLogService;

    @Mock
    private AIProvider mockProvider;

    @InjectMocks
    private InferenceService inferenceService;

    private InferenceRequest createRequest(String provider, String model, String prompt) {
        return InferenceRequest.builder()
                .provider(provider)
                .model(model)
                .prompt(prompt)
                .build();
    }

    @Test void unexpectedFailureIsRecordedAndSanitized() {
        var request = createRequest(null, "qwen3", "secret prompt");
        when(routingEngine.resolve(request)).thenThrow(new RuntimeException("SECRET_INTERNAL"));
        assertThatThrownBy(() -> inferenceService.infer(request, "req_unexpected"))
                .hasMessage("An unexpected error occurred");
        verify(requestLogService).log(eq("req_unexpected"), isNull(), eq("qwen3"), eq("FAILURE"), eq("INTERNAL_ERROR"), anyInt());
    }

    @Test void diagnosticFailurePreservesProviderOutcome() {
        var request = createRequest(null, "qwen3", "hello");
        when(routingEngine.resolve(request)).thenReturn(new RoutingResult(mockProvider, "ollama-local"));
        when(mockProvider.infer(request)).thenReturn(InferenceResponse.builder().text("ok").build());
        doThrow(new RuntimeException("SECRET_DB_ERROR")).when(requestLogService).log(any(), any(), any(), any(), any(), anyInt());
        assertThat(inferenceService.infer(request, "req_logfail").getText()).isEqualTo("ok");
        assertThat(meters.get("gateway.diagnostics.dropped").counter().count()).isEqualTo(1);
        verify(requestLogService, times(1)).log(any(), any(), any(), any(), any(), anyInt());
    }

    @Test void diagnosticFailurePreservesOriginalProviderFailure() {
        var request = createRequest(null, "qwen3", "hello");
        var failure = new GatewayException(ErrorCode.PROVIDER_TIMEOUT, "Provider request timed out");
        when(routingEngine.resolve(request)).thenReturn(new RoutingResult(mockProvider, "ollama-local"));
        when(mockProvider.infer(request)).thenThrow(failure);
        doThrow(new RuntimeException("SECRET_DB_ERROR")).when(requestLogService).log(any(), any(), any(), any(), any(), anyInt());
        assertThatThrownBy(() -> inferenceService.infer(request, "req_bothfail")).isSameAs(failure);
        verify(requestLogService, times(1)).log(eq("req_bothfail"), any(), any(), eq("FAILURE"), eq("PROVIDER_TIMEOUT"), anyInt());
    }

    @Test
    void infer_happyPath_returnsEnrichedResponseAndLogsSuccess() {
        InferenceRequest request = createRequest(null, "qwen3", "Hello");
        String requestId = "req_test123";

        InferenceResponse providerResponse = InferenceResponse.builder()
                .text("Hi there!")
                .model("qwen3")
                .provider("ollama-local")
                .latencyMs(100)
                .build();

        when(routingEngine.resolve(request)).thenReturn(new RoutingResult(mockProvider, "ollama-local"));
        when(mockProvider.infer(request)).thenReturn(providerResponse);

        InferenceResponse result = inferenceService.infer(request, requestId);

        assertThat(result.getRequestId()).isEqualTo("req_test123");
        assertThat(result.getText()).isEqualTo("Hi there!");
        assertThat(result.getModel()).isEqualTo("qwen3");
        assertThat(result.getProvider()).isEqualTo("ollama-local");
        assertThat(result.getLatencyMs()).isGreaterThanOrEqualTo(0);

        verify(requestLogService).log(
                eq("req_test123"), eq("ollama-local"), eq("qwen3"),
                eq("SUCCESS"), isNull(), anyInt()
        );
    }

    @Test
    void infer_providerFailure_logsFailureAndRethrows() {
        InferenceRequest request = createRequest(null, "qwen3", "Hello");
        String requestId = "req_fail456";

        when(routingEngine.resolve(request)).thenReturn(new RoutingResult(mockProvider, "ollama-local"));
        when(mockProvider.infer(request)).thenThrow(
                new GatewayException(ErrorCode.PROVIDER_UNAVAILABLE, "Ollama is not reachable"));

        assertThatThrownBy(() -> inferenceService.infer(request, requestId))
                .isInstanceOf(GatewayException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROVIDER_UNAVAILABLE);

        verify(requestLogService).log(
                eq("req_fail456"), eq("ollama-local"), eq("qwen3"),
                eq("FAILURE"), eq("PROVIDER_UNAVAILABLE"), anyInt()
        );
    }

    @Test
    void infer_routingFailure_recordsTerminalFailure() {
        InferenceRequest request = createRequest("nonexistent", "qwen3", "Hello");
        String requestId = "req_route789";

        when(routingEngine.resolve(request)).thenThrow(
                new GatewayException(ErrorCode.PROVIDER_NOT_FOUND, "Provider not found"));

        assertThatThrownBy(() -> inferenceService.infer(request, requestId))
                .isInstanceOf(GatewayException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROVIDER_NOT_FOUND);

        verify(requestLogService).log(eq(requestId), isNull(), eq("qwen3"), eq("FAILURE"),
                eq("PROVIDER_NOT_FOUND"), anyInt());
    }

    @Test
    void infer_stampsRequestIdOnResponse() {
        InferenceRequest request = createRequest(null, "qwen3", "Test");
        String requestId = "req_stamp_test";

        InferenceResponse providerResponse = InferenceResponse.builder()
                .text("response text")
                .model("qwen3")
                .provider("ollama-local")
                .latencyMs(50)
                .build();

        when(routingEngine.resolve(request)).thenReturn(new RoutingResult(mockProvider, "ollama-local"));
        when(mockProvider.infer(request)).thenReturn(providerResponse);

        InferenceResponse result = inferenceService.infer(request, requestId);

        // Provider response has no requestId; service stamps it
        assertThat(result.getRequestId()).isEqualTo("req_stamp_test");
    }

    @Test
    void infer_measuresEndToEndLatency() {
        InferenceRequest request = createRequest(null, "qwen3", "Latency test");
        String requestId = "req_latency";

        InferenceResponse providerResponse = InferenceResponse.builder()
                .text("fast response")
                .model("qwen3")
                .provider("ollama-local")
                .latencyMs(10)
                .build();

        when(routingEngine.resolve(request)).thenReturn(new RoutingResult(mockProvider, "ollama-local"));
        when(mockProvider.infer(request)).thenReturn(providerResponse);

        InferenceResponse result = inferenceService.infer(request, requestId);

        // End-to-end latency should be >= 0 (measured by the service, not the provider)
        assertThat(result.getLatencyMs()).isGreaterThanOrEqualTo(0);
    }
}
