# AI Inference Gateway — Roadmap & Developer Guide

## Architecture & System Flow

AI Inference Gateway is a Spring Boot (Java) backend that sits between client applications and AI model providers, exposing a single unified endpoint (`POST /v1/inference`) for all AI requests. A client sends a request with an `X-API-Key` header and a JSON body; the gateway's auth filter validates the key against hashed keys in PostgreSQL, a routing engine resolves the target provider (explicit `provider` field → else active default from DB), and the resolved provider's `AIProvider` adapter calls the actual backend (Ollama for MVP). The response is normalized into a standard schema tagged with a unique `requestId`, a metadata row is written to `request_logs`, and the response is returned to the client. All errors follow a single standardized `ApiError` shape. Two additional visibility endpoints (`GET /v1/providers`, `GET /v1/logs`) expose configuration and request history.

## Tech Stack

- **Backend / Gateway:** Spring Boot (Java), Spring MVC (synchronous)
- **Local AI Provider:** Ollama (no paid API dependency for dev/test)
- **Database:** PostgreSQL (providers, models, API keys, request logs)
- **Cache / Future Infra:** Redis (provisioned, not used in MVP logic)
- **Containerization:** Docker / Docker Compose (gateway + Postgres + Redis + Ollama)
- **Migrations:** Flyway
- **Security:** Spring Security — API key filter only
- **HTTP Client:** Spring `RestClient` (provider adapters only)
- **Validation:** `jakarta.validation`
- **Testing:** JUnit 5 + Mockito

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

## Next Steps / Future Enhancements

### Upcoming MVP Phases
- **Phase 3:** `/v1/inference` endpoint & request logging — `InferenceController`, `InferenceService`, `RequestLogService`, end-to-end inference flow.
- **Phase 4:** Visibility endpoints — `GET /v1/providers`, `GET /v1/logs` with pagination and filtering.
- **Phase 5:** Hardening, test coverage, documentation polish.

### Post-MVP Enhancements
- **Additional providers:** OpenAI, Anthropic, Bedrock adapters (each is a new `AIProvider` implementation).
- **Rate limiting & caching:** Wire Redis into request handling for quota enforcement and response caching.
- **Streaming responses:** SSE/token-by-token streaming via reactive adapter.
- **Observability:** Prometheus metrics, Grafana dashboards, structured logging.
- **Event-driven processing:** Kafka for async request log ingestion and analytics pipelines.
- **Advanced auth:** JWT/OAuth2, RBAC, multi-tenant governance.
- **Deployment:** Kubernetes manifests, multi-region support, CI/CD pipelines.

