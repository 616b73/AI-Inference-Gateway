# Rules.md — AI Inference Gateway

> This file sets boundaries for any AI model (or human) contributing code to this project. Read `PRD.md` and `Architecture.md` first. These rules override general habits/defaults — if a suggestion conflicts with this file, this file wins.

---

## 1. Libraries & Tools — Use

| Concern | Use | Notes |
|---------|-----|-------|
| Web framework | Spring Boot (Spring Web/MVC) | Already the chosen stack — do not propose alternatives |
| Persistence | Spring Data JPA + PostgreSQL | |
| Migrations | Flyway (preferred) or Liquibase | Every schema change is a migration file, never a manual DB edit |
| Security | Spring Security, API-key filter only | No custom crypto — use Spring's built-in password/hash utilities for key hashing |
| HTTP client (to Ollama) | Spring `RestClient` / `WebClient` | Keep it inside the provider adapter only |
| Validation | `jakarta.validation` (`@Valid`, `@NotNull`, etc.) on request DTOs | |
| Testing | JUnit 5 + Mockito | Every service class gets a unit test; every controller gets at least a happy-path + one error-path test |
| Containerization | Docker / Docker Compose | No Kubernetes manifests yet — that's a future phase |

## 2. Libraries & Tools — Avoid (for MVP)

- **No JWT / OAuth2 / Spring Security full auth stack** — API key only (see `Architecture.md §6`).
- **No message queues** (Kafka, RabbitMQ) — deferred to a future phase.
- **No caching libraries wired into logic** — Redis is provisioned in `docker-compose.yml` but must not be used for actual caching/rate-limiting logic yet.
- **No ORMs other than JPA/Hibernate** — don't introduce MyBatis, jOOQ, etc.
- **No reactive stack (WebFlux) for the core gateway** — keep it synchronous/blocking Spring MVC. (Streaming/reactive is a future-phase concern.)
- **No lombok-heavy magic that hides business logic** — Lombok for boilerplate (getters/setters/builders) is fine; don't use it to hide validation or business rules.
- **No third-party API gateway frameworks** (e.g., Spring Cloud Gateway, Kong config) — this project *is* the gateway; don't wrap it in another gateway layer.
- **No new AI provider SDKs** unless explicitly requested — MVP only implements the Ollama adapter.

---

## 3. Architectural Boundaries

- **The gateway core must never import or reference a provider-specific SDK/class directly.** All provider interaction goes through the `AIProvider` interface (`infer()`, `info()`, `health()`). If you're tempted to `import` an OpenAI/Anthropic SDK class into `routing/` or `inference/`, stop — that logic belongs in a new adapter under `provider/`.
- **Controllers do not contain business logic.** They validate input, delegate to a service, and return a response. Routing decisions, provider selection, and normalization belong in service/engine classes.
- **No new REST endpoints outside the three defined in `Architecture.md §7`** (`/v1/inference`, `/v1/providers`, `/v1/logs`) without updating `PRD.md` and `Architecture.md` first. Documentation changes precede code changes for scope additions.
- **No hardcoded provider names or model names in Java code.** Providers and models are config data in PostgreSQL (`providers`, `models` tables). If a model name is being typed into a `.java` file, that's a rule violation.
- **Every request gets a `requestId`.** Do not add an endpoint or log entry that lacks one.

---

## 4. Error Handling Rules

- **All errors use the single standard schema** (defined in `PRD.md §6` / `Architecture.md §7`):
  ```json
  { "timestamp": "...", "status": 404, "error": "PROVIDER_NOT_FOUND", "message": "...", "path": "...", "requestId": "..." }
  ```
- **Never leak stack traces, internal exception messages, or raw provider error payloads to the client.** Catch, translate to a known `ErrorCode`, log the internal detail server-side.
- **Use the existing `ErrorCode` enum** (`INVALID_REQUEST`, `PROVIDER_NOT_FOUND`, `PROVIDER_UNAVAILABLE`, `MODEL_NOT_FOUND`, `PROVIDER_TIMEOUT`, `INTERNAL_ERROR`, `BAD_CONFIGURATION`). Adding a new error code requires updating `Architecture.md` too.
- **All exceptions are handled centrally** in `GlobalExceptionHandler` — do not add ad-hoc `try/catch` blocks in controllers that return custom error shapes.
- **Provider failures fail the individual request only.** Do not implement retries, circuit breakers, or background health polling — that's explicitly deferred (see `PRD.md §6`).

---

## 5. Data & Security Rules

- **API keys are never stored in plaintext.** Hash before persisting; compare hashes on auth.
- **No secrets in code or committed config.** DB credentials, etc. go through environment variables / `.env` (git-ignored), referenced in `docker-compose.yml` and `application.yml`.
- **Every schema change is a migration file**, named and ordered per the convention in `Architecture.md §5` (`V1__...sql`, `V2__...sql`, etc.). Never modify an already-applied migration — add a new one.
- **Logs must not contain full prompts/response bodies** by default (privacy/size) — log metadata only (provider, model, status, latency, error code), per the `request_logs` schema.

---

## 6. What the AI Should Do

- Always check `PRD.md` and `Architecture.md` before implementing a new feature — confirm it's in scope.
- Follow the existing folder structure (`Architecture.md §5`) — new code goes in the matching package, not a new top-level folder invented on the spot.
- Write a unit test alongside any new service/engine class.
- When a requirement is ambiguous, prefer the simplest MVP-consistent interpretation over a more "complete" enterprise-grade solution.
- Flag (in comments or PR description) any deviation from these rules, with a reason.

## 7. What the AI Should NOT Do

- Do not implement anything listed as "Out of Scope" in `PRD.md §6` (rate limiting, caching logic, failover, streaming, JWT/OAuth/RBAC, multi-tenancy, audit logs, analytics dashboards) unless the user explicitly asks and updates the docs first.
- Do not introduce a new provider, library, or framework not listed in `§1` without asking.
- Do not change the API error schema or success schema shape without updating `Architecture.md`.
- Do not bypass the `AIProvider` interface to call a provider directly "just this once."
- Do not silently expand scope while implementing a phase (see `Phases.md`) — if something feels like it belongs in a later phase, leave it for that phase.

---

## 8. Related Docs

- `PRD.md` — product scope, features, target users
- `Architecture.md` — tech stack, folder structure, system flow
- `Phases.md` — build order broken into stages