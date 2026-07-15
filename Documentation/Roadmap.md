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

## Next Steps

### Future Enhancements
- **Phase 0:** Project scaffolding (Spring Boot init, Docker Compose, Flyway, folder structure) and data model (migrations, JPA entities, seed data) — first code milestone.
- **Additional providers:** OpenAI, Anthropic, Bedrock adapters (post-MVP, each is a new `AIProvider` implementation).
- **Rate limiting & caching:** Wire Redis into request handling for quota enforcement and response caching.
- **Streaming responses:** SSE/token-by-token streaming via reactive adapter.
- **Observability:** Prometheus metrics, Grafana dashboards, structured logging.
- **Event-driven processing:** Kafka for async request log ingestion and analytics pipelines.
- **Advanced auth:** JWT/OAuth2, RBAC, multi-tenant governance.
- **Deployment:** Kubernetes manifests, multi-region support, CI/CD pipelines.
