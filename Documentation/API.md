# API contracts and compatibility plan

Updated 2026-09-21. The first section describes current Phase 6 behavior. Sections marked planned describe future contracts. This is a design contract, not a generated OpenAPI specification. Phase 7 produces machine-readable schemas and wire fixtures; Phase 12 completes admin schemas.

Legacy preservation below is the default transition design. Under the product owner's redesign authorization, a documented replacement can revise it when preserving demo behavior would perpetuate defects or unnecessary complexity. Assess actual consumers, define the replacement/migration, update examples and tests, and revise the relevant release gates before shipping. Internal implementation changes alone do not require wire compatibility work. See [Development.md](Development.md).

## Current endpoints

| Method and path | Authentication | Current contract |
|---|---|---|
| `POST /v1/inference` | `X-API-Key` | Required nonblank `model` and `prompt`; optional `provider`; synchronous generation |
| `GET /v1/providers` | `X-API-Key` | Array of `name`, `type`, `models`; no provider base URL |
| `GET /v1/logs` | `X-API-Key` | Pagination envelope: `content`, `page`, `size`, `totalElements`, `totalPages` |
| `GET :9090/actuator/health` | None; private listener | Minimal status only; liveness/readiness subpaths supported |
| `GET :9090/actuator/prometheus` | None; private listener | Operational metrics; blocked on the public listener |

Current log filters are `provider`, `status`, `from`, `to`, `page` (default 0), and `size` (default 20, upper cap 100). Times are ISO local date-times without an offset. Negative page, nonpositive size, status outside `SUCCESS`/`FAILURE`, malformed timestamps and reversed ranges return 400. Inference requires JSON, nonblank model/prompt, maximum 200-character model/provider and 262,144-character prompt, within the 1 MiB body limit.

Phase 6 deliberately corrects provider-unavailable from 502 to 503 and returns fixed safe error messages rather than raw parser/provider text. Malformed input returns 400; missing/invalid key 401; forbidden access 403; oversized body 413; unsupported media 415; unexpected failure 500; local saturation/upstream unavailability 503; provider/deadline timeout 504. Stable error codes distinguish `OVERLOADED` and `PROVIDER_UNAVAILABLE`. Requests rejected before inference use metrics, not database diagnostic rows. No automatic retries or rate-limit accounting is implied by these statuses.

Legacy inference example:

```json
{"model":"qwen3","prompt":"What is 2+2?","provider":"ollama-local"}
```

Legacy success fields are `requestId`, `text`, `model`, `provider`, `latencyMs`. Legacy error fields are `timestamp`, `status`, `error`, `message`, `path`, `requestId`. Every request handled by the ID filter gets `X-Request-Id`; not every success envelope embeds it. README and `http/requests.http` describe the runnable MVP.

## Planned inference endpoints

| Endpoint | Delivery | Contract scope |
|---|---|---|
| `POST /v1/chat/completions` | Phase 7 | Chat messages, text, SSE, tool declarations/results and structured output where deployment supports them |
| `POST /v1/embeddings` | Phase 7 | Text/string-array inputs, vectors, model and usage; no claim of universal dimensions/encoding support |
| `GET /v1/models` | Phase 7; tenant-scoped in Phase 8 | Discover only authorized virtual model aliases |
| `POST /v1/inference` | Retained | Existing shape through first GA series; internal legacy facade |
| `POST /v1/responses` | Phase 14 | Native Responses contract after state/tool/storage semantics are designed |
| `POST /v1/messages` | Phase 14 | Native Anthropic Messages contract; separate from Phase 7 upstream adapter |

New SDK-compatible routes use `Authorization: Bearer <gateway-key>`. The bearer value is a gateway credential, never an upstream provider key. Legacy routes continue accepting `X-API-Key` during migration. When both accepted headers are present, require the same credential or reject the request; do not silently choose a more privileged one. Reject oversized/malformed keys before expensive verification.

Planned Chat Completions request (illustrative alias, not seeded today):

```json
{
  "model": "support-chat",
  "messages": [{"role": "user", "content": "Summarize this incident."}],
  "stream": true
}
```

Keep the selected protocol's normal response shape. Return gateway correlation in `X-Request-Id`; do not wrap compatible responses in the legacy envelope. Gateway-specific metadata belongs in documented headers or a namespaced extension, not arbitrary schema changes.

### Compatibility rules

- Publish a versioned matrix for role/content types, generation controls, output constraints, tools, usage and streaming. A provider adapter may support a smaller set than another.
- Preserve tool IDs, argument fragments, finish reasons and usage semantics. The GA gateway passes tool requests to the application; it does not execute tools.
- Recognized but unsupported parameters return a structured 400 before dispatch. Define an explicit unknown-field policy in schema fixtures; no silent dropping of safety or semantic fields.
- Fallback candidates must support the requested operation and options. A text-only candidate cannot receive a vision request.
- Provider passthrough, if introduced later, is an explicitly authorized endpoint with an allowlist and size limits; it cannot bypass governance.
- Publish tested client SDK versions, provider/model/API versions and unsupported cases. SDK compatibility is scoped, not universal.

### Streaming

Use `text/event-stream`, disable intermediary response buffering, and preserve ordered events. Phase 7 fixtures define the Chat Completions chunk shape, optional final usage chunk and terminal marker. A disconnect may prevent final usage delivery; persist unknown/estimated usage internally when provider totals are absent.

Before response commitment, errors use the appropriate HTTP status and protocol error envelope. After response bytes are sent, emit a safe stream error where supported and close; an already-sent 200 cannot become 503. Do not fabricate normal completion or replay another provider after any downstream bytes. Native protocols introduced later need their own terminal/error fixtures.

### Error taxonomy

| Category | Target HTTP status before commitment | Expected behavior |
|---|---|---|
| Malformed/unsupported input | 400 | Safe field-level message, no upstream call |
| Invalid/expired credential | 401 | No resource disclosure |
| Forbidden model/scope/policy | 403 | Terminal; no fallback to evade policy |
| Unknown visible model/resource | 404 | Do not reveal another tenant's object existence |
| Concurrent admin update/idempotency mismatch | 409 or 412 for failed `If-Match` | Caller refreshes; no blind overwrite |
| Too large / unsupported media | 413 / 415 | Reject before expensive work |
| Request/token/budget limit exceeded | 429 with distinct stable code | `Retry-After` only when a meaningful retry time is known |
| No eligible healthy target/dependency unavailable | 503 | Bounded response; safe retry guidance |
| Total/provider timeout | 504 | No unbounded attempts |
| Unexpected internal failure | 500 | Sanitized message and correlation ID |

Internal categories map to protocol-native error bodies. Legacy routes retain their envelope. Add mappings in filters, async handlers and the global error handler; controller advice alone cannot cover every failure. Upstream credential errors are gateway deployment failures, not a 401 blaming a correctly authenticated client.

## Planned administration and query API

Use `/admin/v1` with OIDC and explicit role/scope checks. Proposed resources are `/tenants`, `/applications`, `/service-accounts`, `/keys`, `/providers`, `/deployments`, `/virtual-models`, `/route-policies`, `/budgets`, `/usage`, `/requests`, `/audit-events`, and `/config-versions`. These paths are not implemented yet.

Tenant-scoped endpoints derive authorization from the principal and validate any selected tenant. Platform-wide operations require platform role and audit. API keys are shown once at creation; subsequent representations expose metadata only. Provider credentials return masked metadata/reference IDs, never secret values.

Configuration actions on `/config-versions/{id}` include validation, simulation, staging and activation. Rollback creates an audited activation of an earlier revision. Return an operation identifier and convergence state, rather than claiming all replicas switched atomically.

New admin/event APIs use stable cursor pagination, bounded filters and UTC offset timestamps. Mutations support `ETag/If-Match` and scoped idempotency keys. Reusing an idempotency key with a different body is an error. A repeated admin request can be deduplicated; repeated inference is not guaranteed exactly once unless the operation and provider explicitly support it.

## Migration policy

1. Capture existing API fixtures before implementation and preserve legacy bodies through GA.
2. Add new protocols without redirects from `/v1/inference`.
3. Rotate legacy keys because their hashes cannot yield lookup prefixes. Publish the legacy acceptance window and disable it by default for new deployments.
4. Restrict old logs/providers endpoints by tenant and read scope. Existing globally visible records become restricted legacy history, not guessed ownership.
5. New clients use model aliases. Existing model names map explicitly when unambiguous; provider override still passes access/residency/capability checks.
6. Publish security changes and deprecation timelines before removal. Provider API changes and unsupported fields require a compatibility-matrix update and regressions.

See [Architecture.md](Architecture.md) for lifecycle, [Security.md](Security.md) for roles and [Phases.md](Phases.md) for release gates.
