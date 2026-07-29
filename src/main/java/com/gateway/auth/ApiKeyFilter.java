package com.gateway.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.common.RequestIdFilter;
import com.gateway.error.ApiError;
import com.gateway.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Servlet filter that authenticates requests using the {@code X-API-Key} header.
 * <p>
 * Registered exclusively in the Spring Security filter chain via {@link SecurityConfig}
 * (not as a servlet-level filter via {@code @Component}) so that the {@code SecurityContext}
 * set here is not cleared by Spring Security's own context management.
 * <p>
 * On auth failure, writes a JSON {@link ApiError} directly to the response —
 * {@code GlobalExceptionHandler} doesn't cover filters (they run before DispatcherServlet).
 */
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);
    private static final String API_KEY_HEADER = "X-API-Key";

    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper;

    public ApiKeyFilter(ApiKeyService apiKeyService, ObjectMapper objectMapper) {
        this.apiKeyService = apiKeyService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Missing API key on {} {}", request.getMethod(), request.getRequestURI());
            writeUnauthorizedResponse(response, request, "Missing API key — provide X-API-Key header");
            return;
        }

        if (!apiKeyService.validate(apiKey)) {
            log.warn("Invalid API key on {} {}", request.getMethod(), request.getRequestURI());
            writeUnauthorizedResponse(response, request, "Invalid API key");
            return;
        }

        // Set authentication in SecurityContext so Spring Security considers the request authenticated
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("api-key-user", null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    /**
     * Only apply this filter to /v1/** paths.
     * Health/actuator endpoints and other paths are excluded.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/v1/");
    }

    private void writeUnauthorizedResponse(HttpServletResponse response,
                                           HttpServletRequest request,
                                           String message) throws IOException {
        String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        if (requestId == null) {
            requestId = "unknown";
        }

        ApiError error = ApiError.of(ErrorCode.UNAUTHORIZED, message, request.getRequestURI(), requestId);

        response.setStatus(ErrorCode.UNAUTHORIZED.getStatusCode());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
