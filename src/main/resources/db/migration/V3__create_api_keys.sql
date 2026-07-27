-- V3: Create api_keys table (Architecture.md §6)
CREATE TABLE api_keys (
    id          UUID PRIMARY KEY,
    key_hash    TEXT NOT NULL,
    label       TEXT NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
