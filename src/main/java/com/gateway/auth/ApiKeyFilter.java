package com.gateway.auth;

import com.gateway.error.ApiErrorWriter;
import com.gateway.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collections;

/** Registered only in the security chain; never logs supplied credentials or paths. */
public class ApiKeyFilter extends OncePerRequestFilter {
    private final ApiKeyService keys;
    private final ApiErrorWriter errors;
    public ApiKeyFilter(ApiKeyService keys, ApiErrorWriter errors) { this.keys = keys; this.errors = errors; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/v1/");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                               FilterChain chain) throws IOException, ServletException {
        String key = request.getHeader("X-API-Key");
        try {
            if (key == null || key.isBlank() || key.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72 || !keys.validate(key)) {
                errors.write(request, response, ErrorCode.UNAUTHORIZED); return;
            }
        } catch (RuntimeException failure) {
            errors.write(request, response, ErrorCode.PROVIDER_UNAVAILABLE); return;
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("api-key-user", null, Collections.emptyList()));
        chain.doFilter(request, response);
    }
}
