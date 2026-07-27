-- V5: Seed data for local development and testing.
--
-- Provider: ollama-local (default, active)
-- Model:    qwen3 (linked to ollama-local)
-- API Key:  BCrypt hash of plaintext "test-api-key-1"
--
-- After first `docker compose up`, pull the model once:
--   docker exec ollama ollama pull qwen3

-- Seed provider
INSERT INTO providers (id, name, type, base_url, is_default, active)
VALUES (
    'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
    'ollama-local',
    'ollama',
    'http://ollama:11434',
    TRUE,
    TRUE
);

-- Seed model
INSERT INTO models (id, provider_id, name, active)
VALUES (
    'b2c3d4e5-f6a7-8901-bcde-f12345678901',
    'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
    'qwen3',
    TRUE
);

-- Seed API key (plaintext: test-api-key-1)
INSERT INTO api_keys (id, key_hash, label, active, created_at)
VALUES (
    'c3d4e5f6-a7b8-9012-cdef-123456789012',
    '$2b$10$iemW2pbe8LCoClCT4DdP2ulCHQey3p3JfzX3bM0uFvXWLzU43HyuK',
    'local-test-key',
    TRUE,
    NOW()
);
