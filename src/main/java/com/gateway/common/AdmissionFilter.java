package com.gateway.common;

import com.gateway.config.GatewayProperties;
import com.gateway.error.ApiErrorWriter;
import com.gateway.error.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Bounded admission before BCrypt/JSON work, including chunked bodies without Content-Length. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AdmissionFilter extends OncePerRequestFilter {
    private final GatewayProperties properties;
    private final ApiErrorWriter errors;
    private final MeterRegistry meters;
    private final Semaphore permits;
    public AdmissionFilter(GatewayProperties properties, ApiErrorWriter errors, MeterRegistry meters) {
        this.properties = properties; this.errors = errors; this.meters = meters;
        this.permits = new Semaphore(properties.getMaxConcurrentRequests());
        meters.gauge("gateway.requests.active", permits,
                s -> properties.getMaxConcurrentRequests() - s.availablePermits());
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/v1/");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                               FilterChain chain) throws IOException, ServletException {
        long start = System.nanoTime();
        if (!permits.tryAcquire()) {
            errors.write(request, response, ErrorCode.OVERLOADED);
            meters.counter("gateway.admission.rejected", "reason", "concurrency").increment();
            return;
        }
        RequestDeadline.start(properties.getTotalTimeoutMs());
        try {
            int limit = properties.getMaxRequestBytes();
            if (request.getContentLengthLong() > limit) {
                errors.write(request, response, ErrorCode.PAYLOAD_TOO_LARGE); return;
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.min(limit, 8192));
            byte[] chunk = new byte[8192];
            var input = request.getInputStream();
            while (buffer.size() <= limit) {
                if (RequestDeadline.remainingMillis(properties.getTotalTimeoutMs()) == 0) {
                    errors.write(request, response, ErrorCode.PROVIDER_TIMEOUT); return;
                }
                int read = input.read(chunk, 0, Math.min(chunk.length, limit + 1 - buffer.size()));
                if (read < 0) break;
                buffer.write(chunk, 0, read);
            }
            byte[] body = buffer.toByteArray();
            if (body.length > limit) {
                errors.write(request, response, ErrorCode.PAYLOAD_TOO_LARGE); return;
            }
            if (RequestDeadline.remainingMillis(properties.getTotalTimeoutMs()) == 0) {
                errors.write(request, response, ErrorCode.PROVIDER_TIMEOUT); return;
            }
            chain.doFilter(new BufferedRequest(request, body), response);
        } finally {
            RequestDeadline.clear(); permits.release();
            // Only bounded categories: never URL, user key, model or exception text.
            meters.timer("gateway.requests", "status", Integer.toString(response.getStatus()))
                    .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }
    private static final class BufferedRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        BufferedRequest(HttpServletRequest request, byte[] body) { super(request); this.body = body; }
        @Override public ServletInputStream getInputStream() {
            ByteArrayInputStream source = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return source.read(); }
                @Override public int read(byte[] b, int off, int len) { return source.read(b, off, len); }
                @Override public boolean isFinished() { return source.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) {
                    throw new IllegalStateException("Async request-body reading is not supported by this endpoint");
                }
            };
        }
        @Override public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
