package com.gateway.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that assigns a unique request ID to every inbound HTTP request.
 * <ul>
 *   <li>Stores it as a request attribute ({@code "requestId"}) for the error handler.</li>
 *   <li>Adds it to the SLF4J MDC so all log lines for the request carry the ID.</li>
 *   <li>Sets the {@code X-Request-Id} response header for client-side correlation.</li>
 * </ul>
 * Runs early in the filter chain (highest precedence) to ensure the ID is available
 * to all downstream filters and handlers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_ATTRIBUTE = "requestId";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = RequestIdGenerator.generate();

        // Store in request attribute for GlobalExceptionHandler
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);

        // Add to MDC for structured logging
        MDC.put(MDC_KEY, requestId);

        // Set response header for client correlation
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
