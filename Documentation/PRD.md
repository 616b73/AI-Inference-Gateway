# AI Inference Gateway product requirements

Updated 2026-09-21. Status: product plan with Phase 6 foundation implemented; validation evidence and remaining gates are in [Phase6.md](Phase6.md). This document replaces the MVP-only scope. Production readiness is a release outcome to demonstrate, not a claim about the current code.

## Product direction

Build a self-hosted Java AI gateway that gives organizations one governed endpoint for cloud and local models. Applications should be able to change model deployments without rewriting integrations. Platform teams should be able to explain routing decisions, control access and spending, protect data, and operate reliably.

The supplied competitive research informs the direction; its recommendations are inputs to this plan, not development instructions. [Research.md](Research.md) records evidence and adaptations. [Architecture.md](Architecture.md) defines the design, [Phases.md](Phases.md) defines delivery dependencies, and [Roadmap.md](Roadmap.md) records work actually completed.

The initial product is customer-operated with customer-owned provider credentials. Hosted SaaS, commercial pricing, and certification commitments require separate product decisions. No provider-count or performance leadership claim is assumed.

## Users and essential journeys

| User | Essential journey | Product outcome |
|---|---|---|
| Application developer | Obtain a scoped key, discover allowed models, call chat or embeddings using a supported SDK | Standard integration, usable errors, streaming, migration examples |
| Platform administrator | Connect providers, simulate a routing policy, activate and roll back a version | Validated, attributable changes without worker restarts |
| Tenant administrator | Create applications and service accounts, rotate keys, assign access and limits | Self-service within tenant boundaries |
| SRE | Diagnose an outage, drain a replica, restore a backup | Recovery without prompt logging |
| Security reviewer | Inspect identity, residency, redaction, audit records and retention | Testable controls and explicit protection limits |
| Cost owner | Compare usage and reconcile provider estimates | Explainable spend and enforceable allocation |

An application must never gain access to another tenant's keys, aliases, configuration, cached outputs, usage, or logs by changing a header or resource ID. Cross-tenant platform administration is explicit and audited.

## Current baseline

The code exposes `POST /v1/inference`, `GET /v1/providers`, `GET /v1/logs`, and unauthenticated health. It supports synchronous Ollama generation, explicit/default provider selection, model validation, BCrypt API keys, request IDs, and PostgreSQL metadata logs. Redis is provisioned but unused. There is no control-plane API or UI.

MVP Phases 0–5 were recorded complete, and the original 62 tests were reproduced before Phase 6 changes. Phase 6 adds bounded transport/admission, safe errors, terminal diagnostic handling, private management, seed safeguards, wrapper/CI and real PostgreSQL migration tests. Identity is still a generic principal and logs are globally visible to valid keys; tenant isolation remains Phase 8. See [Architecture.md](Architecture.md) and [Phase6.md](Phase6.md) for current limits and measured evidence.

## Requirements and release scope

Requirements below remain **planned**, except the baseline above and implemented F01/basic F07 controls documented in Phase6. F01's remote CI gate remains open until a clean-checkout workflow passes. IDs link requirements to phases and release evidence.

| ID | Requirement | Observable acceptance | Delivery |
|---|---|---|---|
| F01 | Safe foundation | Sanitized errors, deadlines, bounded input/concurrency, seed safeguards, PostgreSQL migration tests, repeatable CI | Phase 6 |
| F02 | Protocol compatibility | Chat Completions text, tools and structured output where supported; SSE; scoped model discovery; published compatibility subset | Phase 7; GA |
| F03 | Provider runtime | Generic OpenAI-compatible HTTP adapter, Ollama, validated OpenAI and Anthropic adapters, deployment capabilities and credential references | Phase 7; GA |
| F04 | Identity and isolation | Tenants, applications, service accounts, indexed keys, scopes, expiry, rotation, revocation, admin OIDC and roles | Phase 8; GA |
| F05 | Reliable routing | Virtual models, pools, ordered fallback, priority/weighted selection, bounded retries, health and circuits; recorded reasons | Phase 9; GA |
| F06 | Consumption governance | Distributed request/token admission, concurrency controls, durable usage and price versions, alerts, hard budget reservations and reconciliation | Phase 10; GA |
| F07 | Operational visibility | Metrics, traces, request/attempt correlation, TTFT, cancellation, usage, routing reasons, dashboards and alerts | Basic Phase 6; complete Phase 11; GA |
| F08 | Privacy and guardrails | Content off by default, scoped retention/export, built-in secret/regex policies, pre/post hooks, streaming and failure semantics | Phase 11; GA |
| F09 | Configuration lifecycle | Admin APIs and declarative import/export; validate, simulate, stage, activate, monitor convergence, roll back; immutable revisions and audit | Phase 12; GA |
| F10 | Operator experience | API-backed console for onboarding, access, routes, decisions and usage; onboarding and SDK examples | Phase 12; GA |
| F11 | Exact caching | Opt-in, tenant-scoped, policy/version-aware keys, TTL and size bounds, purge and hit accounting; sensitive requests bypass | Phase 11; GA |
| F12 | Deployable product | Hardened image, Helm and Linux systemd/Podman packages, local Compose, backup/restore, upgrades, rollback and offline verification | Phase 13; GA |
| F13 | Embeddings | Text embeddings and normalized usage for supported deployments | Phase 7; GA |
| F14 | Enterprise extensions | Bedrock/Azure adapters, native Responses and Anthropic Messages endpoints, SCIM, richer multimodal/batch workloads, secret-manager integrations | Phase 14; post-GA |
| F15 | Agent and serving extensions | MCP registry/proxy with per-tool authorization; inference endpoint-picker integration; prompt/evaluation modules | Phase 15; post-GA |

GA means F01–F13 pass release gates. F14–F15 are product expansion, not hidden blockers for the first production deployment. A private preview after Phases 6–7 is restricted to trusted isolated evaluation. Enterprise beta requires Phases 6–10 and carries no production availability promise.

## Required behavior

### Client compatibility

The product owner permits redesigning unsuitable MVP architecture and implementation. The legacy continuity commitments below are the initial transition plan and may be revised where they preserve defects or create unjustified complexity. Record the reason, actual consumer/data impact and replacement contract, and update API documentation, examples and acceptance tests together. This permits deliberate design changes without assuming that existing data can be discarded. See [Development.md](Development.md).

Keep `/v1/inference` and its existing JSON shape through the first GA series. It is a legacy facade, not a redirect or a wire-compatible alias of Chat Completions. New `/v1/chat/completions`, `/v1/embeddings`, and `/v1/models` endpoints expose a documented subset. Changing base URL, gateway credential, and model alias should suffice for that subset; arbitrary provider features are not guaranteed.

Reject unsupported features before upstream invocation. Never silently drop tools, structured-output constraints, safety settings, or modalities. New policies apply to legacy routes too. Tenant restrictions on legacy visibility endpoints and key rotation are intentional security migration changes documented before rollout. [API.md](API.md) defines the transition.

### Policy and reliability

A virtual model represents an operator-approved set of interchangeable deployments. Fallback cannot bypass model permissions, residency, guardrails, context limits, or price constraints. Policy rejection is terminal. Retries and fallbacks consume one attempt budget and total deadline. Once downstream response bytes have been sent, generation cannot restart transparently.

Every admitted inference request receives a decision summary with configuration version, selected deployment, relevant rule IDs, and outcome. Denials record safe reasons without exposing other tenants' resources. Simulation uses the live policy evaluator and cannot invoke a model, consume budget, or execute a tool.

### Spending and privacy

Distinguish estimates from provider-reported tokens and reconciled cost. Account for charged failed attempts, retries and cancellations. Missing usage is unknown, never automatically zero. Hard-budget routes require a conservative price/usage bound before dispatch; unsupported bounds reject or use explicitly designated soft-budget mode.

Store no prompt, completion, tool argument, or secret by default in logs, traces, audit records, or exports. Content capture and caching require separate explicit policy. Redacted sampling and encrypted capture are tenant-controlled. A logging setting does not authorize response reuse across users.

## Proposed quality targets

These are initial engineering targets, not measured capabilities or contractual SLAs. Phase 6 establishes a reproducible workload; Phase 13 validates them or records a product-approved revision with evidence.

| Dimension | Target and measurement |
|---|---|
| Availability | 99.9% monthly gateway-controlled success for valid, admitted calls; exclude intentional policy/limit denials, separately report upstream failures and total client-visible success |
| Overhead | At 100 requests/s and 200 concurrent streams on the declared reference environment: p95 added non-streaming latency ≤50 ms and p95 added TTFT ≤50 ms against a controlled upstream; publish p50/p95/p99 and saturation |
| Streaming | Cancel upstream and release resources within 2 seconds of detected disconnect; bounded detection through writes/heartbeat or timeout; no post-output replay |
| Limits | Zero unauthorized admissions in two-replica atomic request-limit tests; token-estimate drift measured and reconciled |
| Budget | Concurrent reservations cannot exceed configured admission budget; uncertain/charged attempts remain attributable until settlement |
| Config and revocation | Healthy replicas converge within 5 seconds; replicas with security state older than 30 seconds reject new governed traffic |
| Recovery | Reference RPO ≤15 minutes and RTO ≤60 minutes, proven by restore drill; reconcile strict budget state before reopening paid traffic |
| Security | No unresolved critical/high exploitable release findings; cross-tenant denial and secret/content non-disclosure tests pass |
| Compatibility | Version-pinned official Java, Python and Node SDK fixtures pass the advertised subset; legacy regressions pass |

## Boundaries and open product decisions

The gateway does not train or host model weights, execute application tool calls in the GA core, guarantee model correctness, or neutralize all prompt injection. Applications remain responsible for validating outputs and authorizing business actions.

Semantic caching, quality-based automatic routing, mandatory Kafka, independent microservices, active-active multi-region accounting, hosted billing, and compliance certifications are outside first GA. They require measured need or a separate product case. Offline means no mandatory vendor callback; selected cloud providers still require network access.

Before beta, the product owner selects pilot workloads, deployment environment, identity provider, residency regions and retention settings. Before GA, the release owner names operational owners, supported versions, support policy and license/distribution terms. Missing dates or staffing do not justify invented delivery commitments.
