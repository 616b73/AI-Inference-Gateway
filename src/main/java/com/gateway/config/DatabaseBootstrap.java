package com.gateway.config;

import com.gateway.auth.ApiKey;
import com.gateway.auth.ApiKeyRepository;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Runs before provider registration and readiness. Local convenience requires an explicit profile and opt-in. */
@Component
public class DatabaseBootstrap implements InitializingBean {
    public static final String DEMO_KEY = "test-api-key-1";
    private static final UUID LOCAL_KEY_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    private final ApiKeyRepository keys;
    private final ProviderConfigRepository providers;
    private final GatewayProperties properties;
    private final Environment environment;
    private final BCryptPasswordEncoder encoder;
    private final TransactionTemplate transaction;
    public DatabaseBootstrap(ApiKeyRepository keys, ProviderConfigRepository providers,
                             GatewayProperties properties, Environment environment,
                             BCryptPasswordEncoder encoder, PlatformTransactionManager transactionManager) {
        this.keys = keys; this.providers = providers; this.properties = properties;
        this.environment = environment; this.encoder = encoder;
        this.transaction = new TransactionTemplate(transactionManager);
    }
    @Override public void afterPropertiesSet() {
        boolean local = environment.acceptsProfiles(Profiles.of("local"));
        boolean production = environment.acceptsProfiles(Profiles.of("production"));
        var bootstrap = properties.getBootstrap();
        if (bootstrap.isLocalEnabled() && (!local || production)) {
            throw new IllegalStateException("Local bootstrap requires the local profile without production");
        }
        if (production) {
            String password = environment.getProperty("spring.datasource.password", "");
            if (password.isBlank() || password.equals("gateway")) {
                throw new IllegalStateException("Production requires an explicit non-demo database password");
            }
        }
        transaction.executeWithoutResult(status -> {
            if (bootstrap.isLocalEnabled()) {
                String raw = bootstrap.getApiKey();
                if (raw.isBlank() || raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
                    throw new IllegalStateException("Local bootstrap requires a valid API key");
                keys.save(ApiKey.builder().id(LOCAL_KEY_ID).keyHash(encoder.encode(raw))
                        .label("local-bootstrap").active(true).createdAt(LocalDateTime.now()).build());
                providers.findByName("ollama-local").ifPresent(provider -> {
                    provider.setBaseUrl(bootstrap.getOllamaBaseUrl()); providers.save(provider);
                });
            }
            if (!bootstrap.isLocalEnabled()) {
                for (ApiKey key : keys.findByActiveTrue()) {
                    if (key.getId().equals(LOCAL_KEY_ID) || encoder.matches(DEMO_KEY, key.getKeyHash())) {
                        throw new IllegalStateException("Active demo credential detected; deactivate it before starting");
                    }
                }
            }
        });
    }
}
