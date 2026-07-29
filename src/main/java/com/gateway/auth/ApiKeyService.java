package com.gateway.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Validates plaintext API keys against stored BCrypt hashes.
 * <p>
 * For MVP, iterates all active key hashes — acceptable with a small key count.
 * Post-MVP optimization: add a key-prefix lookup column to avoid full scans.
 */
@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);

    private final ApiKeyRepository apiKeyRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public ApiKeyService(ApiKeyRepository apiKeyRepository, BCryptPasswordEncoder passwordEncoder) {
        this.apiKeyRepository = apiKeyRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Validates a raw API key against all active stored hashes.
     *
     * @param rawKey the plaintext API key from the X-API-Key header
     * @return {@code true} if the key matches any active hash, {@code false} otherwise
     */
    public boolean validate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return false;
        }

        List<ApiKey> activeKeys = apiKeyRepository.findByActiveTrue();

        for (ApiKey key : activeKeys) {
            if (passwordEncoder.matches(rawKey, key.getKeyHash())) {
                log.debug("API key validated successfully (label={})", key.getLabel());
                return true;
            }
        }

        log.warn("API key validation failed — no matching active key");
        return false;
    }
}
