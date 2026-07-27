# Architecture.md — AI Inference Gateway

> This file defines **how** the project is built: tech stack, folder structure, system components, and request flow. Read `PRD.md` first for product context, then this file for technical context before writing code.

---

## 1. Tech Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| Backend / Gateway | **Spring Boot** (Java) | Core gateway service — API, routing, orchestration |
| Local AI Provider | **Ollama** | Local model provider for dev/test, no paid API needed |
| Database | **PostgreSQL** | Providers, models, API keys, request logs |
| Cache / Future Infra | **Redis** | Provisioned now, used later for rate limiting/caching |
| Containerization | **Docker / Docker Compose** | Runs gateway + Postgres + Redis + Ollama together locally |
| Security | Spring Security (API key filter only) | Validates `X-API-Key` on every request |
| Future (post-MVP) | Kafka, Prometheus, Grafana, Kubernetes | Event-driven processing, observability, scaling |

---

## 2. High-Level Components

```
┌─────────────┐        ┌──────────────────────────────────────┐        ┌───────────────┐
│   Client     │──────▶│           Gateway Service              │──────▶│   Providers    │
│ Application  │  API  │  (Spring Boot)                         │ infer │ (Ollama, ...)  │
└─────────────┘  Key   │                                        │       └───────────────┘
                        │  ┌────────────┐  ┌──────────────────┐ │
                        │  │  Auth      │  │  Routing Engine   │ │
                        │  │  Filter    │  │  (explicit/default)│
                        │  └────────────┘  └──────────────────┘ │
                        │  ┌────────────┐  ┌──────────────────┐ │
                        │  │  Provider  │  │  Response/Error   │ │
                        │  │  Registry  │  │  Normalizer       │ │
                        │  └────────────┘  └──────────────────┘ │
                        └───────────────┬────────────────────────┘
                                        │
                        ┌───────────────▼────────────────┐
                        │           PostgreSQL             │
                        │  providers | models | api_keys   │
                        │  request_logs                    │
                        └───────────────────────────────────┘
                        ┌───────────────────────────────────┐
                        │              Redis                │
                        │   (reserved, not used yet)        │
                        └───────────────────────────────────┘
```

---

## 3. Request Flow

1. Client sends `POST /v1/inference` with `X-API-Key` header and JSON body (`prompt`, optional `provider`, optional `model`, etc.)
2. **Auth Filter** validates the API key against PostgreSQL. Invalid/missing key → `401` using the standard error schema.
3. Gateway generates a unique `requestId`.
4. **Routing Engine** resolves the target provider:
   - If `provider` is present in the request → use it.
   - Else → use the active default provider from config.
   - If neither resolves → `BAD_CONFIGURATION` error.
5. Gateway calls `infer()` on the resolved provider's adapter (implements the shared `AIProvider` interface).
6. Provider adapter calls the actual backend (e.g., Ollama's local API) and returns a result, or throws/times out.
7. **Response/Error Normalizer** converts the result into the standard success or error schema, tagged with `requestId`.
8. Gateway writes a row to `request_logs` (provider, model, status, latency, error code if any).
9. Gateway returns the normalized response to the client.

---

## 4. Provider Abstraction

All providers implement one shared interface. The gateway core never talks to a provider-specific SDK/API directly — only through this interface.

```java
public interface AIProvider {
    InferenceResponse infer(InferenceRequest request);
    ProviderInfo info();
    HealthStatus health();
}
```

- `OllamaProvider` — MVP's only concrete implementation.
- Future: `OpenAIProvider`, `ClaudeProvider`, `BedrockProvider`, etc. — each is a new adapter class + a config/registration entry. **No changes to gateway core logic.**

---

## 5. Folder Structure

```
ai-inference-gateway/
├── docker-compose.yml
├── Dockerfile
├── pom.xml
├── README.md
├── .env.example
├── Documentation/
│   ├── PRD.md
│   ├── Architecture.md
│   ├── Rules.md
│   ├── Phases.md
│   └── Roadmap.md
├── src/
│   ├── main/
│   │   ├── java/com/gateway/
│   │   │   ├── GatewayApplication.java
│   │   │   │
│   │   │   ├── api/                        # Controllers (HTTP layer only)
│   │   │   │   ├── InferenceController.java
│   │   │   │   ├── ProviderController.java
│   │   │   │   └── LogController.java
│   │   │   │
│   │   │   ├── auth/                       # API key authentication
│   │   │   │   ├── ApiKey.java             # entity
│   │   │   │   ├── ApiKeyRepository.java
│   │   │   │   ├── ApiKeyFilter.java
│   │   │   │   └── ApiKeyService.java
│   │   │   │
│   │   │   ├── routing/                    # Provider resolution logic
│   │   │   │   └── RoutingEngine.java
│   │   │   │
│   │   │   ├── provider/                   # Provider abstraction + adapters
│   │   │   │   ├── AIProvider.java         # interface
│   │   │   │   ├── ProviderRegistry.java
│   │   │   │   └── ollama/
│   │   │   │       └── OllamaProvider.java
│   │   │   │
│   │   │   ├── inference/                  # Core orchestration service
│   │   │   │   ├── InferenceService.java
│   │   │   │   ├── InferenceRequest.java
│   │   │   │   └── InferenceResponse.java
│   │   │   │
│   │   │   ├── logging/                    # Request logging
│   │   │   │   ├── RequestLog.java         # entity
│   │   │   │   ├── RequestLogRepository.java
│   │   │   │   └── RequestLogService.java
│   │   │   │
│   │   │   ├── config/                     # Provider/model config management
│   │   │   │   ├── ProviderConfig.java     # entity
│   │   │   │   ├── ProviderConfigRepository.java
│   │   │   │   ├── ModelConfig.java        # entity
│   │   │   │   └── ModelConfigRepository.java
│   │   │   │
│   │   │   ├── error/                      # Standard error schema + handling
│   │   │   │   ├── ApiError.java
│   │   │   │   ├── ErrorCode.java          # enum: INVALID_REQUEST, PROVIDER_NOT_FOUND, etc.
│   │   │   │   └── GlobalExceptionHandler.java
│   │   │   │
│   │   │   └── common/
│   │   │       └── RequestIdGenerator.java
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/               # Flyway SQL migrations
│   │           ├── V1__create_providers.sql
│   │           ├── V2__create_models.sql
│   │           ├── V3__create_api_keys.sql
│   │           ├── V4__create_request_logs.sql
│   │           └── V5__seed_data.sql
│   │
│   └── test/
│       └── java/com/gateway/
│           ├── GatewayApplicationTests.java
│           ├── routing/RoutingEngineTest.java
│           ├── provider/OllamaProviderTest.java
│           └── api/InferenceControllerTest.java
│
└── postman/ or http/                       # Sample requests for manual testing
    └── inference-requests.http
```

**Layering rule:** `api` → `inference`/`routing` → `provider` → external service. Controllers never talk to repositories or providers directly; they go through service classes.

---

## 6. Data Model (PostgreSQL)

### `providers`
| Column | Type | Notes |
|--------|------|-------|
| id | UUID | PK |
| name | text | e.g. `ollama-local` |
| type | text | e.g. `ollama`, `openai` |
| base_url | text | connection info |
| is_default | boolean | used by routing default rule |
| active | boolean | |

### `models`
| Column | Type | Notes |
|--------|------|-------|
| id | UUID | PK |
| provider_id | UUID | FK → providers.id |
| name | text | e.g. `qwen3` |
| active | boolean | |

### `api_keys`
| Column | Type | Notes |
|--------|------|-------|
| id | UUID | PK |
| key_hash | text | hashed at rest, never stored plaintext |
| label | text | which app/owner the key belongs to |
| active | boolean | |
| created_at | timestamp | |

### `request_logs`
| Column | Type | Notes |
|--------|------|-------|
| id | UUID | PK |
| request_id | text | `req_xxxxxxxxx`, indexed |
| timestamp | timestamp | |
| provider | text | |
| model | text | |
| status | text | `SUCCESS` / `FAILURE` |
| error_code | text | nullable |
| latency_ms | integer | |

---

## 7. API Surface (MVP)

| Endpoint | Method | Auth | Notes |
|----------|--------|------|-------|
| `/v1/inference` | POST | required | Core inference call |
| `/v1/providers` | GET | required | List configured providers |
| `/v1/logs` | GET | required | Paginated/filterable logs (`page`, `size`, `provider`, `status`, `from`, `to`) |

All responses (success and error) include `requestId`. All errors use the standard schema defined in `Rules.md` / `PRD.md`.

---

## 8. Local Development

- `docker compose up` brings up: gateway, PostgreSQL, Redis, Ollama (with a pulled model).
- Flyway/Liquibase migrations run automatically on gateway startup.
- Default provider + a seeded API key should be inserted via migration/seed script for local testing out of the box.

---

## 9. Related Docs

- `PRD.md` — product scope, features, target users
- `Rules.md` — boundaries for AI-assisted development
- `Phases.md` — build order broken into stages