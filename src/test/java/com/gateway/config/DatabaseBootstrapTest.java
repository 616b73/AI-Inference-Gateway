package com.gateway.config;

import com.gateway.auth.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseBootstrapTest {
    private final ApiKeyRepository keys = mock(ApiKeyRepository.class);
    private final ProviderConfigRepository providers = mock(ProviderConfigRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private DatabaseBootstrap bootstrap(GatewayProperties props, MockEnvironment env) {
        return new DatabaseBootstrap(keys, providers, props, env, encoder, mock(PlatformTransactionManager.class));
    }
    @Test void detectsRehashedCopyOfPublicKeyBeforeStartup() {
        when(keys.findByActiveTrue()).thenReturn(List.of(ApiKey.builder().id(UUID.randomUUID())
                .keyHash(encoder.encode(DatabaseBootstrap.DEMO_KEY)).active(true).build()));
        assertThatThrownBy(() -> bootstrap(new GatewayProperties(), new MockEnvironment()).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Active demo credential");
    }
    @Test void refusesLocalBootstrapWithoutLocalProfile() {
        var props = new GatewayProperties(); props.getBootstrap().setLocalEnabled(true);
        assertThatThrownBy(() -> bootstrap(props, new MockEnvironment()).afterPropertiesSet()).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(keys);
    }
    @Test void refusesCombinedLocalProductionProfiles() {
        var props = new GatewayProperties(); props.getBootstrap().setLocalEnabled(true);
        var env = new MockEnvironment(); env.setActiveProfiles("local", "production");
        assertThatThrownBy(() -> bootstrap(props, env).afterPropertiesSet()).isInstanceOf(IllegalStateException.class);
    }
    @Test void productionRejectsDemoDatabasePassword() {
        var env = new MockEnvironment().withProperty("spring.datasource.password", "gateway"); env.setActiveProfiles("production");
        assertThatThrownBy(() -> bootstrap(new GatewayProperties(), env).afterPropertiesSet()).hasMessageContaining("database password");
    }
    @Test void explicitLocalBootstrapHashesKeyAndUpdatesProviderEndpoint() {
        var props = new GatewayProperties(); props.getBootstrap().setLocalEnabled(true);
        props.getBootstrap().setApiKey("local-custom-key"); props.getBootstrap().setOllamaBaseUrl("http://localhost:1234");
        var env = new MockEnvironment(); env.setActiveProfiles("local");
        var provider = ProviderConfig.builder().name("ollama-local").build();
        when(providers.findByName("ollama-local")).thenReturn(java.util.Optional.of(provider));
        bootstrap(props, env).afterPropertiesSet();
        var saved = org.mockito.ArgumentCaptor.forClass(ApiKey.class); verify(keys).save(saved.capture());
        assertThat(encoder.matches("local-custom-key", saved.getValue().getKeyHash())).isTrue();
        assertThat(provider.getBaseUrl()).isEqualTo("http://localhost:1234");
    }
}
