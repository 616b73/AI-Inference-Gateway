-- V1: Create providers table (Architecture.md §6)
CREATE TABLE providers (
    id          UUID PRIMARY KEY,
    name        TEXT NOT NULL UNIQUE,
    type        TEXT NOT NULL,
    base_url    TEXT NOT NULL,
    is_default  BOOLEAN NOT NULL DEFAULT FALSE,
    active      BOOLEAN NOT NULL DEFAULT TRUE
);
