-- V2: Create models table (Architecture.md §6)
CREATE TABLE models (
    id          UUID PRIMARY KEY,
    provider_id UUID NOT NULL REFERENCES providers(id),
    name        TEXT NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (provider_id, name)
);
