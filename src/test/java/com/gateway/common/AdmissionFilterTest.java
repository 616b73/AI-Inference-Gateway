package com.gateway.common;

import com.gateway.config.GatewayProperties;
import com.gateway.error.ApiErrorWriter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class AdmissionFilterTest {
    private AdmissionFilter filter(GatewayProperties properties) {
        return new AdmissionFilter(properties, new ApiErrorWriter(JsonMapper.builder().build()), new SimpleMeterRegistry());
    }
    @Test void unknownContentLengthCannotBypassBodyBound() throws Exception {
        var props = new GatewayProperties(); props.setMaxRequestBytes(8);
        var request = new MockHttpServletRequest("POST", "/v1/inference") {
            @Override public long getContentLengthLong() { return -1; }
        };
        request.setContent("secret-too-long".getBytes()); request.setAttribute("requestId", "req_bounded");
        var response = new MockHttpServletResponse();
        filter(props).doFilter(request, response, (req, res) -> { throw new AssertionError("Must not reach authentication"); });
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).doesNotContain("secret-too-long").contains("req_bounded");
    }
    @Test void saturationRejectsPromptlyAndPermitIsReleased() throws Exception {
        var props = new GatewayProperties(); props.setMaxConcurrentRequests(1); var filter = filter(props);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var worker = Executors.newSingleThreadExecutor()) {
            var first = worker.submit(() -> {
                try {
                    filter.doFilter(new MockHttpServletRequest("POST", "/v1/inference"), new MockHttpServletResponse(), (req, res) -> {
                        entered.countDown();
                        try { release.await(3, TimeUnit.SECONDS); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                    });
                } catch (Exception ex) { throw new RuntimeException(ex); }
            });
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            try {
                var rejected = new MockHttpServletResponse();
                filter.doFilter(new MockHttpServletRequest("POST", "/v1/inference"), rejected, (req, res) -> { throw new AssertionError(); });
                assertThat(rejected.getStatus()).isEqualTo(503);
            } finally { release.countDown(); }
            first.get(2, TimeUnit.SECONDS);
            var admitted = new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest("GET", "/v1/providers"), admitted, (req, res) -> {});
            assertThat(admitted.getStatus()).isEqualTo(200);
        }
    }
}
