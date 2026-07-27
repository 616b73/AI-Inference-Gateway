-- V4: Create request_logs table (Architecture.md §6)
CREATE TABLE request_logs (
    id          UUID PRIMARY KEY,
    request_id  TEXT NOT NULL,
    timestamp   TIMESTAMP NOT NULL DEFAULT NOW(),
    provider    TEXT,
    model       TEXT,
    status      TEXT NOT NULL,
    error_code  TEXT,
    latency_ms  INTEGER
);

-- Index on request_id for lookup by tracking ID
CREATE INDEX idx_request_logs_request_id ON request_logs(request_id);
