package com.gateway.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ApiKeyService}.
 * Uses a real BCryptPasswordEncoder (not mocked) to validate actual hash matching.
 */
@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private ApiKeyService apiKeyService;

    private static final String VALID_RAW_KEY = "test-api-key-1";
    private String validKeyHash;

    @BeforeEach
    void setUp() {
        apiKeyService = new ApiKeyService(apiKeyRepository, passwordEncoder);
        validKeyHash = passwordEncoder.encode(VALID_RAW_KEY);
    }

    @Test
    void validate_validKey_returnsTrue() {
        ApiKey activeKey = ApiKey.builder()
                .id(UUID.randomUUID())
                .keyHash(validKeyHash)
                .label("test-key")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        when(apiKeyRepository.findByActiveTrue()).thenReturn(List.of(activeKey));

        assertThat(apiKeyService.validate(VALID_RAW_KEY)).isTrue();
    }

    @Test
    void validate_invalidKey_returnsFalse() {
        ApiKey activeKey = ApiKey.builder()
                .id(UUID.randomUUID())
                .keyHash(validKeyHash)
                .label("test-key")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        when(apiKeyRepository.findByActiveTrue()).thenReturn(List.of(activeKey));

        assertThat(apiKeyService.validate("wrong-key")).isFalse();
    }

    @Test
    void validate_noActiveKeys_returnsFalse() {
        when(apiKeyRepository.findByActiveTrue()).thenReturn(Collections.emptyList());

        assertThat(apiKeyService.validate(VALID_RAW_KEY)).isFalse();
    }

    @Test
    void validate_nullKey_returnsFalse() {
        assertThat(apiKeyService.validate(null)).isFalse();
    }

    @Test
    void validate_blankKey_returnsFalse() {
        assertThat(apiKeyService.validate("   ")).isFalse();
    }

    @Test
    void validate_matchesSecondKeyInList_returnsTrue() {
        String otherHash = passwordEncoder.encode("other-key");

        ApiKey firstKey = ApiKey.builder()
                .id(UUID.randomUUID())
                .keyHash(otherHash)
                .label("other-key")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        ApiKey secondKey = ApiKey.builder()
                .id(UUID.randomUUID())
                .keyHash(validKeyHash)
                .label("test-key")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        when(apiKeyRepository.findByActiveTrue()).thenReturn(List.of(firstKey, secondKey));

        assertThat(apiKeyService.validate(VALID_RAW_KEY)).isTrue();
    }
}
