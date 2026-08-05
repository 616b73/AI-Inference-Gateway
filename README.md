# AI Inference Gateway

A backend API gateway for AI inference — one endpoint, multiple providers, centralized control and visibility.

Instead of every application integrating directly with a provider's API (OpenAI, Anthropic, Ollama, etc.), applications send requests to this gateway. The gateway routes requests to a configured provider, normalizes responses, logs activity, and returns a consistent response — regardless of which provider handled it.

---

## Quick Start

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
| `gateway` | 8080 | Spring Boot API gateway |
| `postgres` | 5432 | PostgreSQL database |
| `redis` | 6379 | Redis (provisioned for future use) |
| `ollama` | 11434 | Local AI model server |

### 3. Pull an Ollama model (one-time setup)

After the containers are running, pull the model registered in the seed data:

```bash
docker exec ollama ollama pull qwen3
```

> **Note:** This downloads the model weights into the `ollama-data` Docker volume. You only need to do this once — the model persists across container restarts.

### 4. Verify the gateway is running

```bash
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP"
    }
  }
}
```

---

## Seed Data

The database is automatically populated with test data on first startup:

| Table | Seed Entry | Notes |
|-------|-----------|-------|
| `providers` | `ollama-local` | Type `ollama`, default provider, active |
| `models` | `qwen3` | Linked to `ollama-local`, active |
| `api_keys` | `local-test-key` | Plaintext: `test-api-key-1` |

---

## API Endpoints

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/v1/inference` | POST | `X-API-Key` | Send a prompt, get an AI response |
| `/v1/providers` | GET | `X-API-Key` | List configured providers and models |
| `/v1/logs` | GET | `X-API-Key` | Query request history (paginated, filterable) |
| `/actuator/health` | GET | None | Service health check |

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
  "message": "prompt: must not be blank",
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
  "message": "Missing API key",
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
| `DB_PASSWORD` | `gateway` | Database password |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama server URL |

> When running via Docker Compose, these are set automatically in `docker-compose.yml`. The `.env` file is for customization only.

---

## Project Structure

```
AI-Inference-Gateway/
├── docker-compose.yml          # Orchestrates all 4 services
├── Dockerfile                  # Multi-stage build (Maven → JRE Alpine)
├── pom.xml                     # Maven project descriptor
├── Documentation/              # Project docs (PRD, Architecture, Rules, Phases, Roadmap)
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

- [PRD.md](Documentation/PRD.md) — product scope, features, target users
- [Architecture.md](Documentation/Architecture.md) — tech stack, system flow, data model
- [Rules.md](Documentation/Rules.md) — development boundaries and constraints
- [Phases.md](Documentation/Phases.md) — build order broken into stages
- [Roadmap.md](Documentation/Roadmap.md) — development history and decision log

---

## Tech Stack

| Layer | Technology |
|-------|-----------| 
| Backend | Spring Boot 3.4 (Java 21) |
| Database | PostgreSQL 16 |
| Cache (future) | Redis 7 |
| AI Provider (MVP) | Ollama |
| Migrations | Flyway |
| Containerization | Docker / Docker Compose |
