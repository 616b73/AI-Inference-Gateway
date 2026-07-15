# Phases.md — AI Inference Gateway

> This file breaks the MVP into sequential, buildable phases. Build **one phase at a time, in order** — do not skip ahead or combine phases. Each phase should be completed, tested, and working before moving to the next. Read `PRD.md`, `Architecture.md`, and `Rules.md` first.

---

## Phase 0 — Project Scaffolding & Data Model

**Goal:** A running Spring Boot service with all infrastructure wired up, database tables created, and seed data loaded — no business logic yet.

### Scaffolding
- Initialize Spring Boot project (Web, Data JPA, Validation, PostgreSQL driver).
- Set up `docker-compose.yml`: gateway service, PostgreSQL, Redis, Ollama.
- Set up Flyway migration tooling (empty baseline).
- Set up `application.yml` with env-var-based config (DB connection, Ollama base URL, Redis).
- Create the folder structure exactly as defined in `Architecture.md §5` (empty packages are fine).
- Add a basic health endpoint (`GET /actuator/health` or a simple `GET /ping`) to confirm the service boots inside Docker.

### Data Model & Migrations
- Write Flyway migrations for `providers`, `models`, `api_keys`, `request_logs` (schemas in `Architecture.md §6`).
- Create corresponding JPA entities + Spring Data repositories.
- Write a seed migration: one `ollama-local` provider row (`is_default = true`), one model row (e.g. `qwen3`), one API key (hashed) for local testing.

**Done when:** `docker compose up` starts all four containers, migrations run automatically on startup, the gateway responds to a health check, and seed data is queryable directly via the DB.

---

## Phase 1 — Core Gateway Internals

**Goal:** All internal machinery — error handling, request ID generation, provider abstraction, routing — is built and unit-tested. Nothing is exposed via HTTP yet (beyond the health check), but every piece works in isolation.

### Error Schema & Request ID
- Implement `ErrorCode` enum (`INVALID_REQUEST`, `PROVIDER_NOT_FOUND`, `PROVIDER_UNAVAILABLE`, `MODEL_NOT_FOUND`, `PROVIDER_TIMEOUT`, `INTERNAL_ERROR`, `BAD_CONFIGURATION`, `UNAUTHORIZED`).
- Implement `ApiError` response shape per `Rules.md §4`.
- Implement `GlobalExceptionHandler` — central catch-all that converts exceptions into standardized `ApiError` responses.
- Implement `RequestIdGenerator` (`req_xxxxxxxxx` format).
- Wire request ID generation into a request-scoped context (filter or interceptor) so it's available to controllers, services, and the error handler.

### Provider Abstraction & Ollama Adapter
- Define the `AIProvider` interface (`infer()`, `info()`, `health()`) per `Architecture.md §4`.
- Implement `OllamaProvider` — calls the local Ollama HTTP API via Spring `RestClient` and maps the response into the internal `InferenceResponse` shape.
- Implement `ProviderRegistry` — loads active providers from the DB at startup and exposes lookup by name.

### Routing Engine
- Implement `RoutingEngine`: explicit `provider` field in request → else active default provider from DB → else `BAD_CONFIGURATION` error.

### Tests
- Unit test `OllamaProvider.infer()` with a mocked HTTP call — returns a normalized response.
- Unit test `RoutingEngine`: explicit provider resolves correctly; omitted provider falls back to default; no default configured produces the correct error.
- Unit test `GlobalExceptionHandler` — each defined exception type produces the correct standardized JSON shape with `requestId`.

**Done when:** All internal components are built and pass their unit tests. A manual integration test can call `OllamaProvider.infer()` directly and get back a real model response from the local Ollama container.

---

## Phase 2 — Authentication

**Goal:** API key auth is in place *before* any business endpoint goes live, so every endpoint is born protected.

- Implement `ApiKeyService` — validates a plaintext key against stored hashes using Spring Security's built-in hashing utilities.
- Implement `ApiKeyFilter` — Spring Security filter that reads the `X-API-Key` header on every request and delegates to `ApiKeyService`.
- Configure Spring Security to apply the filter to all `/v1/*` routes. Health/actuator endpoints remain unprotected.
- Missing or invalid key → `401 UNAUTHORIZED` using the standard `ApiError` schema (with `requestId`).

### Tests
- Unit test `ApiKeyService` — valid key passes, invalid/missing key is rejected.
- Integration test — request to a protected path without a key returns `401`; request with the seeded test key passes the filter.

**Done when:** All `/v1/*` routes reject requests without a valid `X-API-Key`. Requests with the seeded test key pass through. Health/actuator endpoints remain accessible without a key.

---

## Phase 3 — `/v1/inference` Endpoint & Request Logging

**Goal:** The primary use case works end-to-end — a client can send a prompt, get an AI response, and the request is logged. This is the core value delivery of the MVP.

### Inference Endpoint
- Implement `InferenceController` → delegates to `InferenceService`.
- `InferenceService` orchestrates: request validation → routing engine → provider adapter → response normalization.
- Define request/response DTOs with `jakarta.validation` annotations (`@Valid`, `@NotNull`, etc.).
- Return the normalized success response (with `requestId`), or a standardized `ApiError` if provider/routing fails.

### Request Logging
- Implement `RequestLogService` — writes a row to `request_logs` after every inference call (success or failure), including `requestId`, provider, model, status, latency_ms, error code.
- Hook logging into `InferenceService` so it happens regardless of outcome (success or caught exception).

### Tests
- Integration test: `POST /v1/inference` with a valid key and prompt → returns a real Ollama-generated response in the standard schema.
- Integration test: `POST /v1/inference` with explicit `provider` field → routes to that provider.
- Integration test: `POST /v1/inference` with missing/invalid fields → returns standardized `ApiError`.
- Verify that every inference call (success or failure) produces exactly one row in `request_logs`.

**Done when:** A `POST /v1/inference` call (with or without an explicit `provider`) returns a real Ollama-generated response in the standard schema, failures return standardized errors, and every call is logged to `request_logs`.

---

## Phase 4 — Visibility Endpoints

**Goal:** Platform/infra teams can inspect configured providers and query request history.

### `/v1/providers`
- Implement `ProviderController` → `GET /v1/providers`.
- Returns the list of configured providers and their associated models.

### `/v1/logs`
- Implement `LogController` → `GET /v1/logs`.
- Supports query parameters: `page`, `size`, `provider`, `status`, `from`, `to`.
- Results sorted newest-first.
- Invalid filter values return a standardized `ApiError`.

### Tests
- Integration test: `GET /v1/providers` returns the seeded provider and model data.
- Integration test: `GET /v1/logs` returns paginated results; filtering by provider, status, and date range works correctly.
- Both endpoints require a valid API key (verified by existing auth filter from Phase 2).

**Done when:** Both endpoints return correct, paginated, filterable data. Auth is enforced. Invalid inputs produce standardized errors.

---

## Phase 5 — Hardening, Test Coverage & Documentation

**Goal:** The MVP is demo-ready, stable, and welcoming to new contributors.

### Test Coverage
- Fill in any remaining unit tests (controllers, services, edge cases) per `Rules.md §6`.
- Add integration tests covering the full end-to-end happy path and at least 2–3 error paths (invalid key, unknown provider, Ollama down).
- Verify no stack traces, internal exception messages, or raw provider payloads leak to the client.

### Review & Polish
- Review all responses against `Rules.md` — standard error schema everywhere, no hardcoded provider/model names in Java code, no scope creep.
- Verify every response (success and error) includes `requestId`.
- Verify all MVP success criteria in `PRD.md §7` are checked off.

### Documentation
- Finalize `README.md`: project overview, prerequisites, `docker compose up` setup, example requests (curl or `.http` file), environment variables reference.
- Add sample request files in `postman/` or `http/` directory for manual testing.

**Done when:** All `PRD.md §7` success criteria are met. A fresh `git clone` → `docker compose up` gets a new developer to a working demo with no manual steps beyond providing env vars. Test suite passes green.

---

## Explicitly Deferred (Do Not Build in These Phases)

Anything listed in `PRD.md §6` as out of scope — rate limiting, caching logic, failover, streaming, JWT/OAuth/RBAC, multi-tenancy, audit logs, active health monitoring, cost tracking, analytics dashboards — is **not** a phase here. These become Phase 6+ only after the MVP phases above are complete and the docs are explicitly updated to bring them into scope.

---

## Related Docs

- `PRD.md` — product scope, features, target users
- `Architecture.md` — tech stack, folder structure, system flow
- `Rules.md` — boundaries for AI-assisted development