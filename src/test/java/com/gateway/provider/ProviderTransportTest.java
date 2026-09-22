package com.gateway.provider;

import com.gateway.common.RequestDeadline;
import com.gateway.config.GatewayProperties;
import com.gateway.error.ErrorCode;
import com.gateway.error.GatewayException;
import com.gateway.support.MockUpstream;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class ProviderTransportTest {
    @Test void totalDeadlineAbortsSlowBodyAndReleasesPool() throws Exception {
        var properties = new GatewayProperties();
        properties.setTotalTimeoutMs(150);
        properties.getTransport().setReadTimeoutMs(5000);
        properties.getTransport().setMaxConnections(1);
        properties.getTransport().setMaxConnectionsPerRoute(1);
        try (var upstream = new MockUpstream(); var transport = new ProviderTransport(properties, new SimpleMeterRegistry())) {
            upstream.respondsWith(exchange -> {
                exchange.sendResponseHeaders(200, 0);
                try {
                    for (int i = 0; i < 100; i++) {
                        exchange.getResponseBody().write('x'); exchange.getResponseBody().flush(); Thread.sleep(20);
                    }
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            long start = System.nanoTime();
            assertThatThrownBy(() -> transport.exchange("GET", URI.create(upstream.url()), null))
                    .isInstanceOf(GatewayException.class).hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROVIDER_TIMEOUT);
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isLessThan(1500);
            assertThat(upstream.calls.get()).isEqualTo(1);
            upstream.respondsWith(exchange -> MockUpstream.respond(exchange, 200, "ok"));
            assertThat(new String(transport.exchange("GET", URI.create(upstream.url()), null))).isEqualTo("ok");
        }
    }
    @Test void responseBoundAndStatusErrorsDoNotLeakOrRetry() throws Exception {
        var properties = new GatewayProperties(); properties.getTransport().setMaxResponseBytes(32);
        try (var upstream = new MockUpstream(); var transport = new ProviderTransport(properties, new SimpleMeterRegistry())) {
            for (int status : new int[]{429, 500, 302, 200}) {
                upstream.respondsWith(exchange -> MockUpstream.respond(exchange, status, "SENSITIVE_PROVIDER_BODY".repeat(100)));
                assertThatThrownBy(() -> transport.exchange("GET", URI.create(upstream.url()), null))
                        .isInstanceOf(GatewayException.class).hasMessage("Provider is unavailable")
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROVIDER_UNAVAILABLE);
            }
            assertThat(upstream.calls.get()).isEqualTo(4);
        }
    }
    @Test void poolWaitIsBoundedAndDoesNotOpenAnotherConnection() throws Exception {
        var properties = new GatewayProperties(); properties.getTransport().setMaxConnections(1);
        properties.getTransport().setMaxConnectionsPerRoute(1); properties.getTransport().setPoolAcquireTimeoutMs(80);
        CountDownLatch entered = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
        try (var upstream = new MockUpstream(); var transport = new ProviderTransport(properties, new SimpleMeterRegistry());
             var workers = Executors.newSingleThreadExecutor()) {
            upstream.respondsWith(exchange -> {
                entered.countDown();
                try { release.await(3, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                MockUpstream.respond(exchange, 200, "ok");
            });
            Future<byte[]> first = workers.submit(() -> transport.exchange("GET", URI.create(upstream.url()), null));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            try {
                assertThatThrownBy(() -> transport.exchange("GET", URI.create(upstream.url()), null)).isInstanceOf(GatewayException.class);
                assertThat(upstream.calls.get()).isEqualTo(1);
            } finally { release.countDown(); }
            assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo("ok".getBytes());
        }
    }
    @Test void expiredAdmissionDeadlinePreventsDispatch() throws Exception {
        try (var upstream = new MockUpstream(); var transport = new ProviderTransport(new GatewayProperties(), new SimpleMeterRegistry())) {
            RequestDeadline.start(1); Thread.sleep(10);
            try {
                assertThatThrownBy(() -> transport.exchange("GET", URI.create(upstream.url()), null))
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROVIDER_TIMEOUT);
                assertThat(upstream.calls.get()).isZero();
            } finally { RequestDeadline.clear(); }
        }
    }
}
