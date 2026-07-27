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

Use the test API key in requests:
```bash
curl -H "X-API-Key: test-api-key-1" http://localhost:8080/v1/inference ...
```

---

## API Endpoints (MVP)

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/v1/inference` | POST | `X-API-Key` | Send a prompt, get an AI response |
| `/v1/providers` | GET | `X-API-Key` | List configured providers and models |
| `/v1/logs` | GET | `X-API-Key` | Query request history (paginated, filterable) |
| `/actuator/health` | GET | None | Service health check |

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
├── src/
│   ├── main/
│   │   ├── java/com/gateway/
│   │   │   ├── GatewayApplication.java
│   │   │   ├── api/            # Controllers (HTTP layer)
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
