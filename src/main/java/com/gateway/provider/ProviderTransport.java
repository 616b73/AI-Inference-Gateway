package com.gateway.provider;

import com.gateway.common.RequestDeadline;
import com.gateway.config.GatewayProperties;
import com.gateway.error.ErrorCode;
import com.gateway.error.GatewayException;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared bounded HTTP pool. No automatic retry, redirect, cookies or raw response error logging. */
@Component
public class ProviderTransport implements AutoCloseable {
    private final GatewayProperties properties;
    private final MeterRegistry meters;
    private final CloseableHttpClient client;
    private final ScheduledThreadPoolExecutor deadlines;
    public ProviderTransport(GatewayProperties properties, MeterRegistry meters) {
        this.properties = properties; this.meters = meters;
        var config = properties.getTransport();
        var pool = PoolingHttpClientConnectionManagerBuilder.create()
                .setPoolConcurrencyPolicy(PoolConcurrencyPolicy.STRICT)
                .setMaxConnTotal(config.getMaxConnections())
                .setMaxConnPerRoute(config.getMaxConnectionsPerRoute())
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofMilliseconds(config.getConnectTimeoutMs()))
                        .setSocketTimeout(Timeout.ofMilliseconds(config.getReadTimeoutMs())).build()).build();
        client = HttpClients.custom().setConnectionManager(pool)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.ofMilliseconds(config.getPoolAcquireTimeoutMs()))
                        .setResponseTimeout(Timeout.ofMilliseconds(config.getReadTimeoutMs())).build())
                .disableAutomaticRetries().disableRedirectHandling().disableCookieManagement().build();
        deadlines = new ScheduledThreadPoolExecutor(1, Thread.ofPlatform().daemon().name("provider-deadlines").factory());
        deadlines.setRemoveOnCancelPolicy(true);
    }

    public byte[] exchange(String method, URI uri, byte[] body) {
        long remaining = RequestDeadline.remainingMillis(properties.getTotalTimeoutMs());
        if (remaining <= 0) throw failure(ErrorCode.PROVIDER_TIMEOUT);
        HttpUriRequestBase request = new HttpUriRequestBase(method, uri);
        if (body != null) request.setEntity(new ByteArrayEntity(body, ContentType.APPLICATION_JSON));
        AtomicBoolean expired = new AtomicBoolean();
        ScheduledFuture<?> cancellation = deadlines.schedule(() -> {
            expired.set(true); request.cancel();
        }, remaining, TimeUnit.MILLISECONDS);
        long start = System.nanoTime();
        String outcome = "success";
        try {
            byte[] result = client.execute(request, response -> {
                if (response.getCode() < 200 || response.getCode() >= 300) {
                    // Abort instead of consuming an arbitrary upstream error body.
                    request.cancel();
                    throw failure(ErrorCode.PROVIDER_UNAVAILABLE);
                }
                if (response.getEntity() == null) throw failure(ErrorCode.PROVIDER_UNAVAILABLE);
                int max = properties.getTransport().getMaxResponseBytes();
                try (var stream = response.getEntity().getContent()) {
                    byte[] bytes = stream.readNBytes(max + 1);
                    if (bytes.length > max) {
                        request.cancel(); throw failure(ErrorCode.PROVIDER_UNAVAILABLE);
                    }
                    return bytes;
                }
            });
            if (expired.get()) throw failure(ErrorCode.PROVIDER_TIMEOUT);
            return result;
        } catch (IOException ex) {
            outcome = expired.get() || hasTimeout(ex) ? "timeout" : "unavailable";
            throw failure(outcome.equals("timeout") ? ErrorCode.PROVIDER_TIMEOUT : ErrorCode.PROVIDER_UNAVAILABLE);
        } catch (GatewayException ex) {
            outcome = expired.get() || ex.getErrorCode() == ErrorCode.PROVIDER_TIMEOUT ? "timeout" : "unavailable";
            throw failure(outcome.equals("timeout") ? ErrorCode.PROVIDER_TIMEOUT : ex.getErrorCode());
        } finally {
            cancellation.cancel(false);
            meters.timer("gateway.provider.duration", "outcome", outcome)
                    .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }
    private boolean hasTimeout(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof TimeoutException) return true;
        }
        return false;
    }
    private GatewayException failure(ErrorCode code) { return new GatewayException(code, code.safeMessage()); }
    @Override @PreDestroy public void close() throws IOException { deadlines.shutdownNow(); client.close(); }
}
