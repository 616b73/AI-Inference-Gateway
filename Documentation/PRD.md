# PRD.md — AI Inference Gateway

> This file defines **what** is being built, **who** it's for, and **what features** it must have. Any AI model picking up this project should read this first for product context before touching code.

---

## 1. What This Project Is

**AI Inference Gateway** is a backend platform that sits between client applications and AI model providers (OpenAI, Anthropic, local models, etc.), acting as a single, unified endpoint for all AI requests.

Instead of every application integrating directly with a provider's API, applications send requests to this gateway. The gateway routes the request to a configured provider, normalizes the response, logs the activity, and returns a consistent response — regardless of which provider actually handled it.

**One-line summary:** An API gateway for AI usage — one endpoint, multiple providers, centralized control and visibility.

---

## 2. Problem It Solves

Without a gateway, every team/app integrates with AI providers independently, which causes:

- Duplicated integration work across teams
- Painful provider switching (logic scattered across many apps)
- No central control over who can access which model
- No visibility into usage, errors, or performance
- Inconsistent AI access patterns across the org

This project centralizes AI access behind one platform layer.

---

## 3. Target Users

| User | What they need from this project |
|------|-----------------------------------|
| **Application developers** | One stable endpoint to call instead of integrating with multiple providers directly |
| **Platform / infra teams** | Central control over configured providers, routing, and traffic visibility |
| **Engineering teams evaluating providers** | Ability to test locally (Ollama) now and swap in real providers later without changing client code |

---

## 4. Core Goal (MVP)

> Prove that a single gateway endpoint can route AI requests to multiple pluggable providers, while centralizing logging, visibility, and provider abstraction — without needing to solve every enterprise problem on day one.

---

## 5. Features — In Scope (MVP)

- Single unified API endpoint for AI requests: `POST /v1/inference`
- Provider abstraction via a common interface (`infer()`, `info()`, `health()`) — gateway core has zero provider-specific knowledge
- One concrete provider integration for MVP: local model via **Ollama**
- Deterministic rule-based routing: explicit `provider` field → else configured default provider
- Standardized success response schema, and a single standardized error schema across all endpoints
- Every request/response tagged with a unique `requestId`
- Request logging to PostgreSQL (timestamp, provider, model, status, latency, error code)
- Paginated + filterable log retrieval: `GET /v1/logs` (`page`, `size`, `provider`, `status`, `from`, `to`)
- Provider configuration management, stored in PostgreSQL, viewable via `GET /v1/providers`
- Supported models stored as config data (not hardcoded)
- Simple API key authentication (`X-API-Key` header) on all endpoints
- Fully containerized local dev environment (Docker Compose: gateway + Postgres + Redis + Ollama)

## 6. Features — Explicitly Out of Scope (MVP)

- Rate limiting, quotas, caching (Redis is provisioned for this later, not used for logic yet)
- Provider failover / load balancing
- Active/background provider health monitoring (failures are surfaced per-request only)
- Streaming responses (token-by-token/SSE)
- JWT, OAuth2, RBAC, user accounts (API keys only)
- Multi-tenant governance, advanced policy management
- Cost tracking, billing, deep analytics/dashboards
- Multi-region deployment, compliance certifications (SOC2, HIPAA, etc.)
- Audit logs (request logs only, for now)

---

## 7. Success Criteria

The MVP is complete when:

- [x] An app can send a request to one stable gateway endpoint and get a provider response back
- [x] Requests route correctly per the explicit-provider / default-provider rule
- [x] Every response (success or error) carries a `requestId`
- [x] Requests without a valid API key are rejected with the standard error schema
- [x] Every request is logged with full metadata, retrievable via paginated/filtered `/v1/logs`
- [x] Configured providers are viewable via `/v1/providers`
- [x] The whole system runs locally via `docker compose up` with no external paid API dependency

---

## 8. Related Docs

- `Architecture.md` — tech stack, folder structure, system flow
- `Rules.md` — boundaries for AI-assisted development (libraries, error handling, do's/don'ts)
- `Phases.md` — build order broken into stages