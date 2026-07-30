package com.gateway.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link RequestLogService}.
 */
@ExtendWith(MockitoExtension.class)
class RequestLogServiceTest {

    @Mock
    private RequestLogRepository requestLogRepository;

    @InjectMocks
    private RequestLogService requestLogService;

    @Test
    void log_successfulRequest_savesCorrectFields() {
        requestLogService.log("req_abc123", "ollama-local", "qwen3",
                "SUCCESS", null, 150);

        ArgumentCaptor<RequestLog> captor = ArgumentCaptor.forClass(RequestLog.class);
        verify(requestLogRepository).save(captor.capture());

        RequestLog saved = captor.getValue();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getRequestId()).isEqualTo("req_abc123");
        assertThat(saved.getProvider()).isEqualTo("ollama-local");
        assertThat(saved.getModel()).isEqualTo("qwen3");
        assertThat(saved.getStatus()).isEqualTo("SUCCESS");
        assertThat(saved.getErrorCode()).isNull();
        assertThat(saved.getLatencyMs()).isEqualTo(150);
        assertThat(saved.getTimestamp()).isNotNull();
    }

    @Test
    void log_failedRequest_savesErrorCode() {
        requestLogService.log("req_def456", "ollama-local", "qwen3",
                "FAILURE", "PROVIDER_UNAVAILABLE", 50);

        ArgumentCaptor<RequestLog> captor = ArgumentCaptor.forClass(RequestLog.class);
        verify(requestLogRepository).save(captor.capture());

        RequestLog saved = captor.getValue();
        assertThat(saved.getRequestId()).isEqualTo("req_def456");
        assertThat(saved.getStatus()).isEqualTo("FAILURE");
        assertThat(saved.getErrorCode()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(saved.getLatencyMs()).isEqualTo(50);
    }
}
