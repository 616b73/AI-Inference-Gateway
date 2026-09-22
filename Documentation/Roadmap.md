# AI Inference Gateway — Roadmap & Developer Guide

## Architecture & System Flow

The current implementation includes the Phase 6 foundation: `POST /v1/inference`, `GET /v1/providers`, `GET /v1/logs`, and private health/metrics. `RequestIdFilter` assigns correlation before bounded `AdmissionFilter` and API-key authentication. `RoutingEngine` selects an explicit/default provider and validates the model. `OllamaProvider` encodes synchronous generation through the pooled, deadline-cancellable `ProviderTransport`. `InferenceService` records one best-effort terminal diagnostic outcome, including routing/unexpected failures; diagnostic persistence cannot replace the provider result.

Keys still resolve to a generic principal; providers/logs are not tenant-scoped. Registry adapters load at startup while routing also queries the database. Provider/log success bodies do not all embed a request ID, though the response header does. Redis is provisioned only. Fast tests use H2; the separate required CI profile runs PostgreSQL/Flyway fresh-install, upgrade and production-startup tests. V6 disables the public demo key, local bootstrap is explicit, and production startup rejects active demo credentials. Streaming, deployment bulkheads, durable accounting and enterprise authorization remain unimplemented.

The production target is documented separately in [Architecture.md](Architecture.md) and [Phases.md](Phases.md). A documentation update does not mean the target modules or release controls exist.

## Tech Stack

- **Backend / Gateway:** Spring Boot 4.1.1, Java 21, Spring MVC (synchronous), Jackson 3; Tomcat 11.0.25 security override
- **Local AI Provider:** Ollama (no paid API dependency for dev/test)
- **Database:** PostgreSQL (providers, models, API keys, request logs)
- **Cache / Future Infra:** Redis (provisioned, not used in MVP logic)
- **Containerization:** Docker / Docker Compose (gateway + Postgres + Redis + Ollama)
- **Migrations:** Flyway
- **Security:** Spring Security — API key filter only
- **HTTP Client:** Apache HttpClient 5 in shared bounded ProviderTransport; no retries/redirects
- **Validation:** `jakarta.validation`
- **Testing:** JUnit/Jupiter + Mockito, real PostgreSQL integration profile, controllable loopback HTTP upstream
- **Build / Operations:** Maven wrapper 3.9.11 with SHA-256 verification, pinned non-root runtime image, Micrometer/Prometheus, GitHub Actions build/test/Trivy scans and artifact manifest

---

## Development Log (Chronological)

This section documents every major milestone in chronological order.
Each entry records what was built, why certain decisions were made, and what was learned.

---

### Milestone 1: Project Planning & Documentation (`initial`)

**Goal:** Establish the project's scope, architecture, development rules, and build plan before writing any code.

**What was done:**
- Created `PRD.md` — defines the product scope, target users, MVP features (unified inference endpoint, provider abstraction, routing, logging, API key auth), explicit out-of-scope items (rate limiting, caching, failover, streaming, JWT/OAuth, multi-tenancy, analytics), and success criteria.
- Created `Architecture.md` — defines the tech stack (Spring Boot, PostgreSQL, Redis, Ollama, Docker Compose), system component diagram, request flow (9-step auth → route → infer → normalize → log → respond), `AIProvider` interface contract (`infer()`, `info()`, `health()`), folder structure (`api/`, `auth/`, `routing/`, `provider/`, `inference/`, `logging/`, `config/`, `error/`, `common/`), PostgreSQL data model (4 tables: `providers`, `models`, `api_keys`, `request_logs`), and API surface (3 endpoints).
- Created `Rules.md` — sets boundaries for development: approved libraries (Spring Boot, JPA, Flyway, Spring Security, RestClient, jakarta.validation, JUnit 5 + Mockito), explicitly avoided libraries (JWT/OAuth, Kafka, reactive/WebFlux, third-party gateway frameworks), architectural boundaries (no provider-specific imports in core, controllers don't contain business logic, no hardcoded provider/model names), error handling rules (single `ApiError` schema, no leaked stack traces, central `GlobalExceptionHandler`), and security rules (hashed API keys, no secrets in code, migrations for all schema changes).
- Created `Phases.md` — breaks the MVP into 6 sequential phases: Phase 0 (scaffolding + data model), Phase 1 (core internals: error schema, provider abstraction, routing), Phase 2 (API key auth), Phase 3 (inference endpoint + request logging), Phase 4 (visibility endpoints), Phase 5 (hardening, tests, documentation).
- Created `Roadmap.md` (this file) — living development log maintained alongside the codebase.

**Key decisions:**
- **Ollama as the sole MVP provider** — enables fully local development and testing with zero paid API costs. Provider abstraction via the `AIProvider` interface means adding OpenAI/Anthropic later requires only a new adapter class and a config entry, no core changes.
- **Spring MVC (synchronous) over WebFlux (reactive)** — MVP doesn't need streaming or non-blocking I/O. Simpler mental model, easier debugging, and the team can introduce reactive patterns in a future phase when streaming support is added.
- **API key auth only (no JWT/OAuth/RBAC)** — minimizes security complexity for MVP while still gating access. Full auth is explicitly deferred in `PRD.md §6`.
- **Flyway over Liquibase** — simpler SQL-based migrations; the project's schema is straightforward and doesn't need Liquibase's XML/YAML abstraction.
- **6 consolidated phases over 10 granular phases** — original 10-phase draft was refactored. Key changes: merged scaffolding + data model (infrastructure isn't testable without tables), consolidated all internal machinery into one phase (error schema, routing, and provider abstraction are tightly coupled), moved auth before endpoints (so every endpoint is born protected), and bundled request logging with the inference endpoint (logging is integral to the inference flow).
- **Redis provisioned but unused** — `docker-compose.yml` includes Redis so the infrastructure is ready for rate limiting and caching in a future phase, but no application code touches it during MVP.

---

### Milestone 2: Phase 0 — Project Scaffolding & Data Model

**Goal:** Get a running Spring Boot service with all infrastructure wired up, database tables created via Flyway, JPA entities mapped, and seed data loaded — no business logic yet. Completes Phase 0.

**What was done:**

*Infrastructure & Build:*
- Created `pom.xml` — Spring Boot 3.4.1 parent, Java 21, dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `postgresql`, `flyway-core`, `flyway-database-postgresql`, `lombok`, `spring-boot-starter-test`, `h2` (test scope).
- Created `Dockerfile` — multi-stage build: `maven:3.9-eclipse-temurin-21` for build (with dependency layer caching), `eclipse-temurin:21-jre-alpine` for runtime.
- Created `docker-compose.yml` — 4 services: `gateway` (built from Dockerfile, port 8080), `postgres` (16-alpine, port 5432, healthcheck via `pg_isready`), `redis` (7-alpine, port 6379), `ollama` (ollama/ollama, port 11434). Gateway depends on Postgres health before starting. Postgres and Ollama data persisted via named volumes.
- Created `.env.example` — template for all environment variables, documents test API key plaintext and Ollama model pull step.
- Created `.gitignore` (Java/Maven/IDE standard) and `.dockerignore` (excludes target/, .git/, docs, .env from build context).

*Spring Boot Application:*
- Created `GatewayApplication.java` — minimal `@SpringBootApplication` entry point.
- Created `application.yml` — all config externalized via env vars with local-dev defaults. JPA `ddl-auto: validate` (Flyway owns schema), `open-in-view: false`, Actuator health with `show-details: always`.
- Created `src/test/resources/application.yml` — test profile using H2 in-memory DB, Flyway disabled, Hibernate `create-drop` auto-DDL.

*Flyway Migrations (5 files under `src/main/resources/db/migration/`):*
- `V1__create_providers.sql` — `providers` table with UUID PK, UNIQUE on `name`.
- `V2__create_models.sql` — `models` table with FK to `providers`, composite UNIQUE on `(provider_id, name)`.
- `V3__create_api_keys.sql` — `api_keys` table, `key_hash` for BCrypt-hashed keys, `created_at` defaults to `NOW()`.
- `V4__create_request_logs.sql` — `request_logs` table with index on `request_id`.
- `V5__seed_data.sql` — seeds one provider (`ollama-local`, `is_default=true`), one model (`qwen3`), one API key (BCrypt hash of `test-api-key-1`, label `local-test-key`). Uses deterministic UUIDs for stable cross-references.

*JPA Entities & Repositories (domain-driven placement per Architecture.md §5):*
- `config/ProviderConfig.java` + `config/ProviderConfigRepository.java` — maps `providers` table. `@OneToMany` lazy to `ModelConfig`. Field named `defaultProvider` (mapped to `is_default` column) to avoid Lombok boolean getter ambiguity. Repository includes `findByName()` and `findByDefaultProviderTrueAndActiveTrue()`.
- `config/ModelConfig.java` + `config/ModelConfigRepository.java` — maps `models` table. `@ManyToOne` lazy to `ProviderConfig`. Repository includes `findByProviderIdAndActiveTrue()`.
- `auth/ApiKey.java` + `auth/ApiKeyRepository.java` — maps `api_keys` table. Placed in `auth/` package (extended Architecture.md §5 listing). Repository includes `findByActiveTrue()`.
- `logging/RequestLog.java` + `logging/RequestLogRepository.java` — maps `request_logs` table. Minimal repository for Phase 0.

*Package Stubs (7 `package-info.java` files):*
- `api/`, `routing/`, `provider/`, `provider/ollama/`, `inference/`, `error/`, `common/` — empty packages with Javadoc comments describing future contents, so folder structure matches Architecture.md from day one.

*Tests:*
- `GatewayApplicationTests.java` — basic `@SpringBootTest` context-load test, verifies all beans wire correctly using H2.

*Documentation:*
- Created `README.md` — quick start guide (clone → `docker compose up --build` → pull model → verify health), seed data reference, API endpoint table, env var reference, project structure diagram, tech stack summary.
- Updated `Architecture.md §5` — `docs/` → `Documentation/`, added `Roadmap.md`, added `ApiKey.java`/`ApiKeyRepository.java` to `auth/` listing, added `V5__seed_data.sql`, added `GatewayApplicationTests.java`, changed "Flyway/Liquibase" → "Flyway".

**Key decisions:**
- **Java 21 locally and in Docker** — Java 21.0.11 LTS installed locally; Dockerfile uses `eclipse-temurin:21`. Both `mvn compile` and `mvn test` pass locally. Docker build is also a fully self-contained path (no local Java required).
- **H2 for test scope** — added as test dependency so `@SpringBootTest` works without a running Postgres. Test `application.yml` disables Flyway and uses Hibernate auto-DDL since our migrations use Postgres-specific SQL.
- **BCrypt `$2b$` prefix** — Python's `bcrypt` library generates `$2b$` prefix hashes (BCrypt version indicator). Spring Security's `BCryptPasswordEncoder` accepts both `$2a$` and `$2b$`, so the seed hash is compatible.
- **Deterministic UUIDs in seed SQL** — used fixed UUIDs (`a1b2c3d4-...`) so the seed data is idempotent and model/key references are stable across environments.
- **Field naming: `defaultProvider` not `isDefault`** — Lombok generates `isIsDefault()` for boolean fields prefixed with `is`, so the JPA field is named `defaultProvider` mapped via `@Column(name = "is_default")`.
- **Composite unique constraint on models** — `UNIQUE(provider_id, name)` added to V2 migration, not in original Architecture.md schema but logically necessary (two providers could offer models with the same name, but a single provider shouldn't list duplicates).
- **`spring-boot-starter-security` excluded from Phase 0** — including it would auto-configure and block all endpoints. Security dependency added in Phase 2 when `ApiKeyFilter` is implemented.

**Tests:**
- `mvn compile` — passes cleanly with Java 21.0.11.
- `mvn test` — 1 test run (`GatewayApplicationTests.contextLoads`), 0 failures, 0 errors. Spring context boots with H2, all 4 JPA repositories discovered, Actuator health endpoint exposed.
- `docker compose up --build` — all 4 containers start (gateway, postgres, redis, ollama). Flyway successfully applied 5 migrations to schema "public", now at version v5. Gateway started in 13.1s on port 8080.
- `GET /actuator/health` → `200 OK`, status `UP`, DB component healthy (PostgreSQL 16.14).
- Seed data verified via `psql`: `providers` table contains `ollama-local` (type `ollama`, `is_default=true`, `active=true`).
- `docker exec ollama ollama pull qwen3` — model pulled successfully (5.2 GB). Ollama container is ready for inference.

**Known issues / follow-ups:**
- Hibernate logs a deprecation warning: `HHH90000025: PostgreSQLDialect does not need to be specified explicitly`. Can be silenced by removing the explicit `hibernate.dialect` property from `application.yml` (non-blocking, cosmetic).

---

### Milestone 3: Phase 1 — Core Internals & Provider Abstraction
**Goal:** Establish the internal routing logic, provider interfaces, standardized error handling, and request tracing before exposing any HTTP endpoints. Completes Phase 1.

**What was done:**

*Error Schema & Request Correlation:*
- Created `error/ErrorCode.java` — enum mapping business error codes (`INVALID_REQUEST`, `PROVIDER_NOT_FOUND`, `PROVIDER_UNAVAILABLE`, `MODEL_NOT_FOUND`, `PROVIDER_TIMEOUT`, `INTERNAL_ERROR`, `BAD_CONFIGURATION`, `UNAUTHORIZED`) to HTTP status codes.
- Created `error/ApiError.java` — 6-field standardized error response (status, error, message, path, timestamp, requestId) per `Rules.md §4`.
- Created `error/GatewayException.java` — runtime exception wrapping an `ErrorCode`, thrown by business logic and caught centrally.
- Created `error/GlobalExceptionHandler.java` — `@RestControllerAdvice` with handlers for `GatewayException`, `MethodArgumentNotValidException`, and a catch-all `Exception` handler. Includes cause-chain unwrapping to handle Spring-wrapped exceptions. No stack traces leak to clients.
- Created `common/RequestIdGenerator.java` — generates `req_` + UUID strings.
- Created `common/RequestIdFilter.java` — servlet filter that generates a request ID, stores it in `HttpServletRequest` attributes and SLF4J MDC, and writes it to the `X-Request-Id` response header.

*Core DTOs:*
- Created `inference/InferenceRequest.java` — fields: `provider` (optional), `model` (required), `prompt` (required).
- Created `inference/InferenceResponse.java` — fields: `requestId`, `text`, `model`, `provider`, `latencyMs`.

*Provider Abstraction:*
- Created `provider/AIProvider.java` — interface with `infer()`, `info()`, `health()` methods returning rich record types.
- Created `provider/ProviderInfo.java` — record holding provider name, type, and supported models.
- Created `provider/HealthStatus.java` — record holding provider name, health status, and optional error message.
- Created `provider/ollama/OllamaProvider.java` — `AIProvider` implementation using Spring `RestClient`. Resolves the Ollama base URL from `ProviderConfig`, handles connection and timeout exceptions with appropriate `GatewayException` wrapping.
- Created `provider/ProviderRegistry.java` — `@Component` that loads all active `ProviderConfig` records from the database at startup, instantiates appropriate `AIProvider` adapters, and provides lookup by name.

*Routing Engine:*
- Created `routing/RoutingEngine.java` — resolves the target provider: explicit `provider` field in request → else active default provider from DB → else `BAD_CONFIGURATION` error. Validates that the resolved provider has the requested model available.

*Infrastructure Fixes:*
- Removed explicit `hibernate.dialect` property from `application.yml` to resolve the `HHH90000025` deprecation warning (Hibernate auto-detects PostgreSQL dialect).
- Deleted `package-info.java` stub files from `error/`, `common/`, `provider/`, `provider/ollama/`, `routing/`, `inference/` — these packages now contain real classes.

**Key decisions:**
- **Standalone MockMvc for `GlobalExceptionHandlerTest`:** Initially used `@WebMvcTest` with `@Import(GlobalExceptionHandler.class)`, but Spring Boot's `BasicErrorController` was intercepting exceptions thrown from the inner-class test controller before our `@RestControllerAdvice` could handle them. The `GatewayException` handler never fired — the catch-all `Exception` handler matched instead. Switching to `MockMvcBuilders.standaloneSetup()` with `.setControllerAdvice(new GlobalExceptionHandler())` gave us full control over the handler resolution chain and fixed all failures.
- **Rich Provider Abstraction:** Designed `AIProvider` to return structured records (`ProviderInfo`, `HealthStatus`) instead of primitives, setting up a solid foundation for health-check endpoints and provider visibility.
- **Cause-chain unwrapping in error handler:** Added `findGatewayException()` to the catch-all `Exception` handler that walks the exception cause chain. This future-proofs against Spring wrapping `GatewayException` in proxy or servlet exceptions.
- **Rule-based routing over intelligent routing:** MVP goal is to prove the gateway pattern works, not optimize provider selection. The `RoutingEngine` uses simple explicit-or-default logic.

**Tests:**
- `RoutingEngineTest` (5 tests) — explicit provider resolves correctly; omitted provider falls back to default; no default throws `BAD_CONFIGURATION`; unknown provider throws `PROVIDER_NOT_FOUND`; model not available throws `MODEL_NOT_FOUND`.
- `OllamaProviderTest` (5 tests) — successful inference returns normalized response; health check returns healthy/unhealthy status; connection failure throws `PROVIDER_UNAVAILABLE`; uses `MockRestServiceServer` to mock the Ollama HTTP API.
- `GlobalExceptionHandlerTest` (4 tests) — `GatewayException` with `PROVIDER_NOT_FOUND` returns 404; `BAD_CONFIGURATION` returns 500 with correct error code; uncaught `NullPointerException` returns generic 500 with no stack trace leak; `X-Request-Id` header is present on all responses.
- `GatewayApplicationTests` (1 test) — Spring context loads with H2.
- **Total: 15 tests, 0 failures, 0 errors.** `mvn test` passes in ~24s.

**Docker verification:**
- `docker compose up --build -d` — gateway container rebuilt with all Phase 1 classes, started successfully in ~20s.
- No `HHH90000025` warning in logs (dialect fix confirmed).
- `ProviderRegistry` log: `Registered provider: ollama-local (type=ollama)` — seed data correctly loaded.
- Flyway: `Schema "public" is up to date. No migration necessary.` — no schema changes in this phase.
---

### Milestone 4: Phase 2 — Authentication

**Goal:** Implement API key authentication so that every `/v1/*` endpoint is born protected, before any business endpoints go live. Completes Phase 2.

**What was done:**

*Dependencies:*
- Added `spring-boot-starter-security` to `pom.xml` — provides Spring Security's filter chain, `BCryptPasswordEncoder`, session management, and authorization infrastructure.
- Added `spring-security-test` (test scope) — provides `MockMvc` security integration utilities.

*Auth Service:*
- Created `auth/ApiKeyService.java` — validates a plaintext API key against stored BCrypt hashes. Injects `ApiKeyRepository` and `BCryptPasswordEncoder`. The `validate(String rawKey)` method loads all active keys via `findByActiveTrue()` and iterates with `BCryptPasswordEncoder.matches()`. Returns `true` on first match. Handles null/blank inputs as early returns.

*Auth Filter:*
- Created `auth/ApiKeyFilter.java` — `OncePerRequestFilter` that reads the `X-API-Key` header, delegates to `ApiKeyService.validate()`, and either sets a `UsernamePasswordAuthenticationToken` in the `SecurityContext` (on success) or writes a 401 JSON `ApiError` response directly (on failure). Only applies to `/v1/**` paths via `shouldNotFilter()` — actuator and other paths are excluded. The 401 response includes `requestId` from the upstream `RequestIdFilter`.

*Security Configuration:*
- Created `auth/SecurityConfig.java` — `@Configuration` + `@EnableWebSecurity`. Defines a `SecurityFilterChain` bean: CSRF disabled, `STATELESS` session policy, `/actuator/**` permitted, `/v1/**` authenticated, all other paths denied. Creates `ApiKeyFilter` as a `@Bean` and inserts it via `addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)`. Exposes `BCryptPasswordEncoder` as a bean.

*Error Handler Update:*
- Modified `error/GlobalExceptionHandler.java` — added `AccessDeniedException` handler that returns 403 with standard `ApiError` JSON shape. Covers the edge case where a request passes the API key filter but hits a denied path.

*Documentation:*
- Updated `Architecture.md §5` — added `SecurityConfig.java` to the `auth/` package listing.

**Key decisions:**
- **`ApiKeyFilter` as a `@Bean` in `SecurityConfig`, not a `@Component`:** Initial implementation used `@Component` + `@Order` on the filter. This caused Spring Boot to register it as both a servlet-level filter AND a Spring Security filter (via `addFilterBefore`). The servlet-level instance ran before Spring Security's `SecurityContextHolderFilter`, which then cleared the `SecurityContext` we set — causing all valid-key requests to get 403. Removing `@Component` and creating the filter as a `@Bean` in `SecurityConfig` ensures it only runs inside the Security filter chain where `SecurityContext` lifecycle is properly managed.
- **Manual JSON response in the filter:** `GlobalExceptionHandler` (`@RestControllerAdvice`) only catches exceptions thrown inside the `DispatcherServlet`. Filters execute before it, so auth failures must write the response directly using `ObjectMapper`. The response still follows the standard `ApiError` schema with `requestId`.
- **`shouldNotFilter()` for path scoping:** Rather than relying solely on Spring Security's `requestMatchers` for path-based auth decisions, the filter itself skips non-`/v1/` paths. This provides defense-in-depth and avoids unnecessary BCrypt hash comparisons for health checks.
- **MVP key iteration:** For MVP with a small number of keys, iterating all active hashes is acceptable. Post-MVP optimization would add a key-prefix lookup column to avoid full scans.

**Tests:**
- `ApiKeyServiceTest` (6 tests) — valid key matches stored hash; invalid key rejected; no active keys returns false; null key returns false; blank key returns false; matches second key in a multi-key list.
- `ApiKeyFilterIntegrationTest` (7 tests) — full `@SpringBootTest` + `@AutoConfigureMockMvc` with a `@TestConfiguration` dummy `/v1/test-auth` controller. Seeds a test API key via `ApiKeyRepository` in `@BeforeEach`. Tests: missing key → 401 with `ApiError` JSON; invalid key → 401; valid key → 200; actuator health without key → 200; 401 response carries `X-Request-Id` header; empty key → 401; valid key response has no error fields.
- All existing Phase 0/1 tests (15) continue to pass.
- **Total: 28 tests, 0 failures, 0 errors.** `mvn test` passes in ~23s.

**Docker verification:**
- `docker compose up --build -d` — gateway container rebuilt with security dependencies, started successfully.
- `GET /actuator/health` (no key) → 200 UP — health check remains accessible.
- `GET /v1/providers` (no key) → 401 `{"status":401,"error":"UNAUTHORIZED","message":"Missing API key","requestId":"req_..."}`.
- `GET /v1/providers` (wrong key) → 401 `{"status":401,"error":"UNAUTHORIZED","message":"Invalid API key","requestId":"req_..."}`.
- `GET /v1/providers` (valid key `test-api-key-1`) → 500 (expected — no `/v1/providers` controller yet, confirming auth passed successfully).

---

### Milestone 5: Phase 3 — `/v1/inference` Endpoint & Request Logging

**Goal:** Implement the primary end-to-end inference flow. A client sends a prompt, it's routed to a provider, and the outcome is logged. Completes Phase 3.

**What was done:**

*Request Logging:*
- Created `logging/RequestLogService.java` — logs every inference call to the `request_logs` table. Records `requestId`, provider, model, `SUCCESS`/`FAILURE` status, error code, and end-to-end latency. Does NOT store prompt or response text.

*Inference Service:*
- Created `inference/InferenceService.java` — the core orchestrator. 
  1. Resolves provider via `RoutingEngine`.
  2. Calls the provider adapter.
  3. Measures end-to-end latency and stamps the `requestId` onto the response.
  4. Calls `RequestLogService` to log the success or failure.
  5. Re-throws exceptions so `GlobalExceptionHandler` can format them.

*Inference Controller:*
- Created `api/InferenceController.java` — thin REST controller for `POST /v1/inference`. Validates the request using `@Valid` (rejects missing model/prompt with 400). Reads `requestId` from the servlet request attributes and delegates to `InferenceService`.

**Key decisions:**
- **Latency Measurement:** Latency is measured end-to-end in `InferenceService` (including routing time and provider call) rather than just inside the provider adapter, ensuring the logged latency accurately reflects the gateway's overhead plus provider latency.
- **RequestId Stamping:** The provider adapter (`OllamaProvider`) does not know the `requestId`. `InferenceService` builds a final `InferenceResponse` copy that includes the `requestId` before returning it to the controller.
- **Provider Mocking in Tests:** In `InferenceControllerIntegrationTest`, `@MockitoBean` was used to mock `ProviderRegistry` and `AIProvider` to prevent tests from hitting real Ollama instances while still testing the entire Spring context. `ProviderConfig` and `ModelConfig` records had to be seeded in the H2 DB in `@BeforeEach` so the `RoutingEngine` wouldn't fail model validation.

**Tests:**
- `RequestLogServiceTest` (2 tests) — validates correct fields are saved on success and failure.
- `InferenceServiceTest` (5 tests) — tests orchestration, latency measurement, `requestId` stamping, and proper exception logging/re-throwing.
- `InferenceControllerIntegrationTest` (7 tests) — tests full Spring Boot endpoint with mocked provider layer. Verifies happy path, 400 on bad requests, 401 on missing auth, explicit provider routing, and DB log insertion.
- **Total: 42 tests, 0 failures, 0 errors.** `mvn test` passes cleanly.

**Docker verification:**
- `docker compose up --build -d` — gateway container rebuilt and started.
- `POST /v1/inference` (valid key + prompt) → 200 OK with `text`, `model`, `provider`, `requestId`, `latencyMs`.
- `POST /v1/inference` (missing prompt) → 400 `INVALID_REQUEST`.
- `POST /v1/inference` (no key) → 401 `UNAUTHORIZED`.
- `SELECT * FROM request_logs` → verified successful inference was logged with latency and no error code.

---

### Milestone 6: Phase 4 — Visibility Endpoints

**Goal:** Provide clients with visibility into the gateway's state and history via `GET /v1/providers` and `GET /v1/logs`. Completes Phase 4.

**What was done:**

*Data Transfer Objects (DTOs):*
- Created `ProviderDto`, `RequestLogDto`, and `PaginatedResponse` in `api/dto/`. 
- Ensured internal data (like provider base URLs and UUID primary keys) do not leak to API clients.

*Provider Visibility:*
- Created `api/ProviderController.java` (`GET /v1/providers`). Iterates over `ProviderRegistry.getAllProviders()` and maps them to `ProviderDto`.

*Log Visibility:*
- Modified `logging/RequestLogService.java` — added `getLogs(page, size)` that uses Spring Data's `PageRequest` with descending timestamp sorting and caps `size` at 100.
- Created `api/LogController.java` (`GET /v1/logs`). Accepts `?page` and `?size` parameters, delegates to `RequestLogService`, and returns a `PaginatedResponse<RequestLogDto>`.

**Key decisions:**
- **In-Memory Provider Querying:** `ProviderController` queries the in-memory `ProviderRegistry` rather than the `ProviderConfigRepository`. This guarantees that the API only advertises providers that have successfully started their adapters, avoiding false positives from broken DB configs.
- **Max Page Size:** Hardcoded a `Math.min(size, 100)` cap in `RequestLogService` to protect the DB from unbounded `SELECT` queries.

**Tests:**
- `ProviderControllerIntegrationTest` (4 tests) — tests happy path, base URL exclusion, 401 without key, and empty registry.
- `LogControllerIntegrationTest` (7 tests) — tests default/custom pagination, ordering, 401, empty logs, and payload structure.
- **Total: 53 tests, 0 failures, 0 errors.** `mvn test` passes cleanly.

**Docker verification:**
- `docker compose up --build -d` — rebuilt and started.
- `GET /v1/providers` → 200 OK with `[{"name":"ollama-local","type":"ollama","models":["qwen3"]}]`.
- `GET /v1/logs?page=0&size=10` → 200 OK with paginated list of recent inference calls.

---

### Milestone 7: Phase 5 — Hardening, Test Coverage & Documentation

**Goal:** Make the MVP demo-ready, fully compliant with PRD/Architecture/Rules specs, and welcoming to new contributors. Close the one remaining functional gap (log filtering). Completes Phase 5 and the entire MVP.

**What was done:**

*`/v1/logs` Filtering (PRD §5 compliance):*
- Extended `RequestLogRepository` with `JpaSpecificationExecutor<RequestLog>` to support dynamic WHERE clause composition.
- Updated `RequestLogService.getLogs()` to accept optional `provider`, `status`, `from`, `to` filter parameters. Builds a `Specification<RequestLog>` from non-null filters — avoids combinatorial explosion of query methods.
- Updated `LogController` with four optional `@RequestParam` parameters: `provider` (String), `status` (String), `from` (ISO-8601 LocalDateTime), `to` (ISO-8601 LocalDateTime). Uses `@DateTimeFormat(iso = DATE_TIME)` for auto-parsing.

*Test Coverage Hardening:*
- Expanded `LogControllerIntegrationTest` from 7 to 16 tests. Seeds diverse data (two providers, mixed SUCCESS/FAILURE, spread across Aug 1–5). Tests: filter by provider, filter by status, filter by date range (from only, to only, both), combined provider+status, combined all four filters, empty result sets, and response shape validation.
- Total test count: **62 tests, 0 failures, 0 errors.**

*Documentation Polish:*
- Rewrote `README.md`: added full "Example Requests" section with curl commands and JSON responses for all 3 endpoints, filter parameter reference table, updated project structure diagram to include `http/` and `api/dto/` directories.
- Created `http/requests.http` — IntelliJ HTTP Client / VS Code REST Client file with pre-built requests for all endpoints including health check, inference (happy + error paths), providers, and logs with all filter combinations.

*PRD §7 Success Criteria:*
- Checked off all 7 success criteria in `PRD.md §7` — all are now `[x]`.

**Key decisions:**
- **JPA Specifications over custom `@Query`:** Specifications compose dynamically — each filter is an independent predicate ANDed together. This avoids writing 2^4 = 16 query method permutations and scales cleanly when new filters are added.
- **`@DateTimeFormat` over manual parsing:** Spring's built-in ISO-8601 annotation handles parsing and returns a clean 400 on malformed input, with no custom error handling needed.

**Tests:**
- 16 `LogControllerIntegrationTest` tests covering all filter permutations, pagination, auth, and response shape.
- **Full suite: 62 tests, 0 failures, 0 errors.** `mvn test` passes cleanly.

**Docker verification:**
- `docker compose up --build -d` — rebuilt and started.
- `GET /v1/logs?provider=ollama-local` → 200 OK, filtered to ollama-local entries only.
- `GET /v1/logs?status=SUCCESS` → 200 OK, filtered to SUCCESS entries only.
- `GET /v1/logs?from=2026-08-01T00:00:00&to=2026-08-05T23:59:59` → 200 OK, date-filtered to 1 matching entry.

### Milestone 8: Enterprise product documentation reset (2026-09-14)

**Goal:** Turn the completed MVP's planning documents into an implementation-ready product and production qualification plan using the supplied competitive research and inspected source.

**What was done:**
- Read the supplied `AI_Inference_Gateway_Competitive_Research.docx`, including its tables and source links, and selectively checked primary documentation.
- Replaced the MVP-only PRD with F01–F15 requirements, user journeys, first-GA boundaries and explicitly unmeasured quality targets.
- Reworked Architecture into an inspected baseline plus target modular-monolith design: protocol/provider contracts, tenant identity, routing and streaming failure rules, distributed admission, durable reservations, configuration lifecycle, cache/privacy and additive migration.
- Replaced the old sequential execution plan with planned Phases 6–15, dependency-based work packages, release gates and a first twelve-item implementation backlog.
- Added API, Security, Operations, Development and Research documentation and refreshed README navigation and actual capability/setup limitations.
- Updated the protected `.agents/AGENTS.md` guidance with filesystem approval to remove the deleted rules dependency and allow dependency-based development while retaining historical-log maintenance.
- Retained original MVP milestones below their original headings without rewriting their historical claims. Corrected current-state summaries and replaced speculative next steps.

**Key decisions:**
- Treated attached research as evidence, not as instructions. Retired the deleted rules file's fixed allowlist and one-phase-at-a-time restrictions in active planning documents.
- Kept one deployable Spring application with explicit module boundaries; did not mandate Kafka, microservices or a reactive rewrite.
- Put foundation repairs first and tenant authorization before shared governance. Started metrics/operational work early; deferred MCP and broader protocols until after a qualified core.
- Kept hard-budget state durable in PostgreSQL, with Redis for ephemeral distributed admission/cache; documented uncertain provider charges and recovery.
- Separated current behavior, proposed architecture, engineering targets and completed work. No implementation feature or production deployment was claimed.

**Validation:**
- Inspected source, configuration, migrations and existing test files; no application test suite or live-provider test was run for this documentation change.
- Checked local documentation links, phase/requirement references, retired-rule references and preserved milestone history during review.

**Known issues / follow-ups:**
- Historical milestones report 62 passing tests; fresh baseline validation is G001.
- Actual production safeguards, schema/identity changes and migration tests remain unimplemented; Phase 6 is next.
- Existing source comments may cite old document sections or the retired rules file. They are historical annotations, not current development constraints, and can be refreshed alongside the relevant implementation.

### Milestone 9: MVP redesign authority clarification (2026-09-15)

**Goal:** Make the product owner's authorization to improve or replace unsuitable MVP design explicit in implementation guidance.

**What was done:**
- Updated Development, Architecture, PRD, API, Phases and Operations to permit documented refactoring or replacement for correctness, maintainability, design quality and unnecessary future complexity.
- Added a retain/refactor/replace assessment to Phase 6 and G001, including actual consumer/data dependencies and revised acceptance needs.
- Made legacy preservation a revisable transition default and required significant changes to include rationale, contract/data impact and validation.

**Key decisions:**
- Existing implementation and proposed architecture remained revisable based on evidence; preserving the demo stack was not an objective in itself.
- Design authority did not imply that existing data was disposable. Actual consumer/data impact still informed migration and recovery.
- Recorded the clarification without starting feature implementation or changing runtime behavior.

### Milestone 10: Phase 6 foundation implementation (2026-09-21)

**Goal:** Repair the demo baseline before building the enterprise protocol, identity and operations layers.

**What was done:**
- Reproduced the original 62-test baseline. Upgraded Boot 3.4.1 to 4.1.1, adopted Jackson 3 and the split MVC/Flyway test/runtime modules, and retained Java 21, MVC, JPA and the modular monolith.
- Added a checksum-pinned Maven wrapper, deterministic JAR timestamps, artifact manifest generation, pinned CI actions, PostgreSQL CI service, dependency/secret/configuration/image scans and SBOM output. Added a non-root runtime and an image target that packages the exact tested artifact; retained a source-build target for local Compose.
- Introduced `GatewayProperties`, `AdmissionFilter`, `RequestDeadline` and `ProviderTransport` for body/response bounds, fail-fast concurrency, strict HTTP pool limits, typed timeouts and active deadline cancellation. Disabled client retries and redirects.
- Repaired parser/query/media status mapping, sanitized public/provider error paths, and extended inference terminal logging to routing and unexpected failures. Diagnostic persistence became explicitly best effort and could no longer replace either a successful response or the original provider failure.
- Added Micrometer request/outcome/latency/admission/drop metrics, a controllable HTTP mock, deadline/pool/saturation/privacy regressions and an opt-in full-HTTP mock benchmark.
- Preserved V1–V5 and added V6 to deactivate the known demo key. Added pre-readiness credential validation and explicit local key/provider bootstrap. Made production the default profile, management private, health minimal, and all local Compose port publications loopback-only.
- Added real PostgreSQL tests for fresh migration, populated V5 upgrade, production readiness/management isolation, and rejection of a rehashed demo key.
- Updated README, API, Architecture, PRD, Phases, Security, Operations and HTTP examples; recorded the Phase 7 streaming experiment and evidence in Phase6.

**Key decisions:**
- Kept the existing public inference shape and durable data, while deliberately correcting provider-unavailable to HTTP 503 and replacing raw error text with stable safe messages. No repository evidence established a deployed external consumer; no external environment or customer database was modified.
- Replaced implicit RestClient transport with explicit pooled cancellation rather than rewriting the application reactively before the streaming experiment. Shared pool isolation and disconnect propagation remained later work.
- Retained fast H2 tests but added actual Flyway/PostgreSQL gates. Diagnostics remained separate from future durable financial accounting.
- Applied Tomcat 11.0.25 after Trivy found three critical advisories in Boot's managed 11.0.24. Used the vendor's fixed patch instead of suppressing findings; remove the override once Boot manages an equivalent or newer patch.

**Tests and environment:**
- Expanded suite passed 88 tests with one optional benchmark skipped; all four PostgreSQL tests passed. The benchmark ran separately and passed. Full commands, artifact identity, measurements and final build/scan status are in [Phase6.md](Phase6.md).
- Local mock measurements included real BCrypt and H2 diagnostic writes; they were not production or real-provider capacity evidence.
- Maven initially needed an ignored project cache because its sandbox default repository was unwritable. PowerShell Maven properties containing dotted versions required quoted arguments. Docker Desktop was available outside PATH; container DNS and advisory downloads required retries. Remote CI execution was not claimed.

**Known issues / follow-ups:**
- Phase 6 implementation did not establish production readiness. Remaining acceptance gates are explicitly tracked in Phase6; production qualification remains Phase 13.
- Generic identity, globally visible metadata, all-key BCrypt scans, startup registry refresh, synchronous diagnostics and absent streaming/tenant/budget controls remain visible limitations for Phases 7–12.

## Next Steps / Future Enhancements

The original six MVP phases (0–5) are recorded complete. Phase 6 implementation and local verification are recorded in Milestone 10 and [Phase6.md](Phase6.md); acceptance remains open for the unresolved build/security/clean-checkout CI gates listed there. Phases 7–15 have not started.

Close Phase 6's remaining external verification gates, then start G007 provider/context contracts and G008 streaming transport experiment in [Phases.md](Phases.md). Protocol/provider and identity work can progress concurrently once contracts are agreed. First production GA requires F01–F13 and the evidence checklist in [Operations.md](Operations.md). Broader enterprise protocols and agent/serving extensions are later phases.

[PRD.md](PRD.md) owns scope, [Architecture.md](Architecture.md) owns design, and [Research.md](Research.md) explains the evidence and adaptations. Record completed work here as it occurs.

