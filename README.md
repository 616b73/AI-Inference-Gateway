# AI Inference Gateway

A Java API gateway for AI inference, with Phase 6 foundation repairs and a plan for a production-ready enterprise product.

Applications send requests to the gateway, which selects a configured provider, normalizes the response and records metadata. **The current implementation supports synchronous Ollama generation only.** Multi-provider compatibility, streaming, tenant governance, an operator console and production deployment packages are planned, not implemented.

---

## Product status and plan

The MVP covers legacy inference, API-key authentication, provider/model selection and metadata log queries. It is a local demo baseline, not a qualified production deployment. The supplied competitive research has been translated into a dependency-based plan with explicit acceptance and release gates.

Start with [PRD.md](Documentation/PRD.md) for product scope, [Architecture.md](Documentation/Architecture.md) for current gaps and target design, and [Phases.md](Documentation/Phases.md) for the implementation backlog. **Phase 6 foundation is implemented; [Phase6.md](Documentation/Phase6.md) records validation and open release gates.** The first GA includes compatibility, governance, reliability, privacy, administration and tested operations; broader enterprise and MCP capabilities follow.

The former MVP rules and rigid phase sequencing are retired. Independent work can proceed once dependencies are satisfied. [Development.md](Documentation/Development.md) explains the workflow; [Roadmap.md](Documentation/Roadmap.md) preserves implementation history.

## Quick Start (local development)

### Prerequisites

- [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/install/)

That's it — no local Java, Maven, or database installation required.

### 1. Clone and configure

```bash
git clone <repository-url>
cd AI-Inference-Gateway
cp .env.example .env
```

### 2. Start all services

```bash
docker compose up --build
```

This starts four containers:

| Service | Port | Description |
|---------|------|-------------|
| `gateway` | 8080 / 9090 | API / private management |
| `postgres` | 5432 | PostgreSQL database |
| `redis` | 6379 | Redis (provisioned for future use) |
| `ollama` | 11434 | Local AI model server |

### 3. Pull an Ollama model (one-time setup)

After the containers are running, pull the model registered in the seed data:

```bash
docker compose exec ollama ollama pull qwen3
```

> **Note:** This downloads the model weights into the `ollama-data` Docker volume. You only need to do this once — the model persists across container restarts.

### 4. Verify the gateway is running

```bash
curl http://localhost:9090/actuator/health/readiness
```

Expected response:
```json
{
  "status": "UP"
}
```

---

## Seed Data

V1–V5 preserve the original provider/model seed. V6 disables the demo API key. The explicit `local` profile used by Compose reactivates a BCrypt-hashed `LOCAL_API_KEY` and updates the seeded provider URL. All Compose ports bind to host loopback. Production is the default outside Compose: it requires a non-demo database password and rejects any active known demo key before readiness. Do not use the local Compose stack as a production deployment or start the local profile against production data.

| Table | Seed Entry | Notes |
|-------|-----------|-------|
| `providers` | `ollama-local` | Type `ollama`, default provider, active |
| `models` | `qwen3` | Linked to `ollama-local`, active |
| `api_keys` | `local-bootstrap` | Local profile only; default `test-api-key-1`, override `LOCAL_API_KEY` |

---

## API Endpoints

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/v1/inference` | POST | `X-API-Key` | Send a prompt, get an AI response |
| `/v1/providers` | GET | `X-API-Key` | List configured providers and models |
| `/v1/logs` | GET | `X-API-Key` | Query request history (paginated, filterable) |
| `:9090/actuator/health` | GET | Private listener | Minimal health; `/liveness` and `/readiness` probes |
| `:9090/actuator/prometheus` | GET | Private listener | Metrics; never expose this listener publicly |

---

## Example Requests

> A complete set of sample requests is also available at [`http/requests.http`](http/requests.http) for IntelliJ HTTP Client or VS Code REST Client.

### Send an inference request

```bash
curl -X POST http://localhost:8080/v1/inference \
  -H "Content-Type: application/json" \
  -H "X-API-Key: test-api-key-1" \
  -d '{"model": "qwen3", "prompt": "What is 2+2? Answer in one word."}'
```

**Success response (200):**
```json
{
  "requestId": "req_4f6745e3-f051-45d3-94d9-0747a1449531",
  "text": "Four",
  "model": "qwen3",
  "provider": "ollama-local",
  "latencyMs": 1523
}
```

**Error response (400 — missing prompt):**
```json
{
  "timestamp": "2026-08-05T12:00:00.000",
  "status": 400,
  "error": "INVALID_REQUEST",
  "message": "Invalid request parameters",
  "path": "/v1/inference",
  "requestId": "req_abc123..."
}
```

**Error response (401 — missing API key):**
```json
{
  "timestamp": "2026-08-05T12:00:00.000",
  "status": 401,
  "error": "UNAUTHORIZED",
  "message": "Missing or invalid API key",
  "path": "/v1/inference",
  "requestId": "req_def456..."
}
```

### List active providers

```bash
curl http://localhost:8080/v1/providers \
  -H "X-API-Key: test-api-key-1"
```

**Response (200):**
```json
[
  {
    "name": "ollama-local",
    "type": "ollama",
    "models": ["qwen3"]
  }
]
```

### Query request logs (with filters)

```bash
# Default pagination
curl "http://localhost:8080/v1/logs" \
  -H "X-API-Key: test-api-key-1"

# Filter by provider and status
curl "http://localhost:8080/v1/logs?provider=ollama-local&status=SUCCESS&page=0&size=10" \
  -H "X-API-Key: test-api-key-1"

# Filter by date range (ISO-8601)
curl "http://localhost:8080/v1/logs?from=2026-08-01T00:00:00&to=2026-08-05T23:59:59" \
  -H "X-API-Key: test-api-key-1"
```

**Response (200):**
```json
{
  "content": [
    {
      "requestId": "req_4f6745e3-...",
      "timestamp": "2026-08-05T12:00:00.000",
      "provider": "ollama-local",
      "model": "qwen3",
      "status": "SUCCESS",
      "errorCode": null,
      "latencyMs": 1523
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

| Filter Param | Type | Example | Description |
|-------------|------|---------|-------------|
| `page` | int | `0` | Zero-based page index (default: 0) |
| `size` | int | `20` | Page size, max 100 (default: 20) |
| `provider` | string | `ollama-local` | Filter by provider name |
| `status` | string | `SUCCESS` | Filter by status (`SUCCESS` / `FAILURE`) |
| `from` | datetime | `2026-08-01T00:00:00` | Inclusive start (ISO-8601) |
| `to` | datetime | `2026-08-05T23:59:59` | Inclusive end (ISO-8601) |

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `gateway_db` | Database name |
| `DB_USERNAME` | `gateway` | Database user |
| `DB_PASSWORD` | Required | Non-demo database password in production; local profile defaults to `gateway` |
| `SPRING_PROFILES_ACTIVE` | `production` (default profile) | Explicitly choose `local` only for demos |
| `LOCAL_API_KEY` | `test-api-key-1` in local profile | Local bootstrap key, maximum 72 UTF-8 bytes |
| `OLLAMA_BASE_URL` | `http://localhost:11434` in local profile | Local bootstrap updates the seeded provider row; Compose sets `http://ollama:11434` |
| `MANAGEMENT_PORT` / `MANAGEMENT_ADDRESS` | `9090` / `127.0.0.1` | Separate private health/metrics listener |

Compose reads `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB` and `LOCAL_API_KEY` from `.env` and passes the corresponding runtime properties. Direct JAR launches use `DB_*`. Outside local bootstrap the database provider URL remains authoritative. [Operations.md](Documentation/Operations.md) lists request/transport limits, management access and upgrade behavior.

---

## Project Structure

```
AI-Inference-Gateway/
├── docker-compose.yml          # Orchestrates all 4 services
├── Dockerfile                  # Multi-stage build (Maven → JRE Alpine)
├── pom.xml                     # Maven project descriptor
├── Documentation/              # Product, architecture, delivery, API, security and operations docs
├── http/
│   └── requests.http           # Sample HTTP requests for manual testing
├── src/
│   ├── main/
│   │   ├── java/com/gateway/
│   │   │   ├── GatewayApplication.java
│   │   │   ├── api/            # Controllers (HTTP layer)
│   │   │   │   └── dto/        # Response DTOs (ProviderDto, RequestLogDto, PaginatedResponse)
│   │   │   ├── auth/           # API key authentication
│   │   │   ├── routing/        # Provider resolution logic
│   │   │   ├── provider/       # Provider abstraction + adapters
│   │   │   │   └── ollama/     # Ollama adapter
│   │   │   ├── inference/      # Core orchestration service
│   │   │   ├── logging/        # Request logging
│   │   │   ├── config/         # Provider/model config entities
│   │   │   ├── error/          # Standard error schema
│   │   │   └── common/         # Shared utilities
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/   # Flyway SQL migrations
│   └── test/
└── .env.example                # Environment variable template
```

---

## Documentation

- [PRD.md](Documentation/PRD.md) — requirements, scope and release targets
- [Architecture.md](Documentation/Architecture.md) — inspected baseline, target design and migration
- [Phases.md](Documentation/Phases.md) — dependencies, acceptance gates and first implementation issues
- [API.md](Documentation/API.md) — existing and proposed endpoints, compatibility and migration
- [Security.md](Documentation/Security.md) — tenant access, privacy and threat model
- [Operations.md](Documentation/Operations.md) — deployment, recovery and production evidence
- [Development.md](Documentation/Development.md) — flexible development workflow
- [Research.md](Documentation/Research.md) — research decisions and primary sources
- [Roadmap.md](Documentation/Roadmap.md) — preserved history and actual implementation status

---

## Build and tests

With Java 21 installed, use the checksum-pinned Maven wrapper (`mvnw.cmd` on Windows, `sh mvnw` on Linux):

```bash
sh mvnw -B test
# Use a disposable PostgreSQL database whose user may CREATE/DROP test schemas:
export TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/gateway_test
export TEST_DATABASE_USER=postgres
export TEST_DATABASE_PASSWORD='<test database password>'
sh mvnw -B -Ppostgres-it verify
# Optional loopback mock benchmark (writes target/phase6-baseline.json):
sh mvnw -B -Dgateway.benchmark=true -Dtest=BaselineBenchmarkTest test
python3 scripts/release_manifest.py target/ai-inference-gateway-0.0.1-SNAPSHOT.jar
```

The default suite uses H2 for speed. The required CI profile runs Flyway fresh-install, populated-MVP upgrade and real PostgreSQL production-startup checks; missing database configuration fails this profile. The Docker image build skips tests, while CI runs them first. CI also scans dependencies, secrets, configuration and container vulnerabilities and retains an artifact manifest/SBOM. See [Phase6.md](Documentation/Phase6.md) for actual local results and remote CI status.

## Current Tech Stack

| Layer | Technology |
|-------|-----------| 
| Backend | Spring Boot 4.1.1 (Java 21), MVC, Jackson 3 |
| Provider HTTP | Apache HttpClient 5, bounded shared pool, no retries |
| Build | Maven wrapper 3.9.11; GitHub Actions verification |
| Database | PostgreSQL 16 |
| Cache (future) | Redis 7 |
| AI Provider (MVP) | Ollama |
| Migrations | Flyway |
| Containerization | Docker / Docker Compose |
