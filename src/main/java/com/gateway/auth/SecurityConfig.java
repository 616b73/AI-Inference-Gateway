package com.gateway.auth;

import com.gateway.error.ApiErrorWriter;
import com.gateway.error.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ApiKeyFilter keys, ApiErrorWriter errors, Environment env) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(request -> request.getLocalPort() == env.getProperty("local.management.port", Integer.class,
                                env.getProperty("management.server.port", Integer.class, 9090))
                                && request.getLocalPort() != env.getProperty("local.server.port", Integer.class,
                                env.getProperty("server.port", Integer.class, 8080))
                                && request.getRequestURI().equals("/actuator/prometheus")).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                        .requestMatchers("/v1/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, ignored) -> errors.write(req, res, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((req, res, ignored) -> errors.write(req, res, ErrorCode.FORBIDDEN)))
                .addFilterBefore(keys, UsernamePasswordAuthenticationFilter.class).build();
    }
    @Bean public ApiKeyFilter apiKeyFilter(ApiKeyService keys, ApiErrorWriter errors) {
        return new ApiKeyFilter(keys, errors);
    }
    @Bean public FilterRegistrationBean<ApiKeyFilter> disableServletRegistration(ApiKeyFilter filter) {
        FilterRegistrationBean<ApiKeyFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false); return registration;
    }
    @Bean public org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }
}
