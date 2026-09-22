# Deployment and production readiness plan

Updated 2026-09-21. Local Compose, a non-root Dockerfile, Maven wrapper and CI build/test/scan workflow are implemented. [Phase6.md](Phase6.md) records observed verification and open gates. Helm, systemd, Podman, signed releases, HA and recovery procedures below remain **planned deliverables**. Use [README.md](../README.md) for current setup.

The MVP upgrade path below is the current continuity strategy. Phase 6 may replace it with a documented transition if design/consumer/data assessment justifies a simpler architecture. Adjust the release evidence accordingly; preserve real data and supported release recovery requirements. A demo origin is not permission to reset an existing environment.

## Deployment contract

First GA supports a single region, customer-owned providers/credentials, and customer-operated stateful services. A reference HA deployment has at least two gateway replicas behind TLS ingress, HA PostgreSQL and Redis, an OIDC issuer for administration, and optional encrypted object storage/telemetry destinations. Managed database/cache services are acceptable; Kubernetes is not required.

| Package | Purpose | Qualification |
|---|---|---|
| Docker Compose | Local development and demonstrations | Fresh clone, explicit local bootstrap, model pull, API examples |
| OCI image and Helm | Kubernetes customer deployment | Probes, resources, rolling drain, secret references, network policy, external DB/Redis, HPA and disruption budget |
| JAR with systemd unit | Linux VM/bare-metal deployment | Non-root identity, restart/drain, mounted config/secrets, log rotation and external state |
| Podman Quadlet | Linux container deployment | Equivalent security/configuration, service dependencies, volumes, upgrades and drain |
| Offline bundle | Disconnected environment with local provider | Verified images/JAR, schemas, catalogs, migrations, checksums/signatures and no mandatory remote callback |

Phase 13 records exact tested OS/runtime/package versions. Do not promise compatibility with all distributions. Local Ollama model weights remain separately installed/licensed prerequisites; an offline install must supply them before disconnecting. Cloud providers and external identity/guardrail services require connectivity if selected.

## Configuration and secrets

Keep one documented property schema across packages with precedence: built-in safe defaults → mounted configuration → explicit environment overrides. Published tenant routing/policy revisions live in PostgreSQL; environment variables configure bootstrap/infrastructure and cannot silently override tenant authorization.

Document all planned properties when implemented: environment/profile, public and management listeners, database/migration credentials, Redis TLS/auth/topology, OIDC issuer/audience, secret resolver, provider connection profiles, request/stream size and timeout limits, concurrency, shutdown grace, telemetry destinations, retention, feature flags and security freshness.

Current direct JAR configuration uses `DB_*`; Compose interpolates `POSTGRES_*` and supplies the corresponding runtime properties. The adapter uses the database provider `base_url`. Explicit local bootstrap updates the seeded Ollama row from `OLLAMA_BASE_URL`; other profiles do not rewrite provider configuration from that variable. These infrastructure properties are not yet a complete production configuration contract.

Production startup must reject known demo credentials, missing required secret references and invalid security settings. Keep secrets out of Git, image layers, process arguments, API exports and logs. Use mounted secret files or a supported resolver. Restrict local exposed infrastructure ports; reference production packages expose only intended ingress and private management.

## Lifecycle and dependency readiness

### Implemented Phase 6 behavior

Production is the default profile; `DB_PASSWORD` must be nonblank and different from `gateway`. V6 disables the historical fixed seed key without deleting customer rows. Startup rejects active keys matching the original seed ID or known demo plaintext, including rehashed copies. Local bootstrap requires `local` without `production`, is enabled in `application-local.yml`, and hashes `LOCAL_API_KEY` before saving it. Never start the local profile against customer data: it intentionally rewrites the demo key and seeded provider URL.

Before upgrading an MVP database, back it up and arrange a non-demo key through the existing operator-controlled database process; there is no administration/provisioning API yet. Apply V6 through normal Flyway startup and verify existing non-demo key access. If a previous local bootstrap reactivated the demo row, deactivate that row before production startup. Do not edit V1–V5, run Flyway repair to hide checksum changes, or reactivate the demo credential to recover access. Roll forward for defects; reverting to the MVP binary would remove these runtime safeguards.

Management defaults to loopback port 9090 with health/prometheus only and no health components/details. Prometheus access requires a distinct management listener; using the same public port does not grant access. Compose binds management to the container network but publishes it on host loopback only. Place authenticated network access in front of management if remote scraping is needed; there is no management-user role yet. Readiness includes the database and readiness state; it does not certify provider/model availability. Flyway and credential validation complete before application readiness.

The runtime runs as UID/GID 10001. `docker build --target packaged -t gateway:local .` packages the exact JAR already tested by the wrapper; CI uses this target and generates its SHA-256 manifest. Default `docker build`/Compose builds source inside the JDK stage and needs Maven Central access. Neither path implies that live-provider or production qualification passed.

| Property (Spring relaxed environment binding supported) | Default | Purpose |
|---|---|---|
| `gateway.max-request-bytes` | 1048576 | Bound request buffering, including unknown-length bodies |
| `gateway.max-concurrent-requests` | 64 | Fail-fast `/v1/` admission before authentication |
| `gateway.total-timeout-ms` | 120000 | Remaining admission-to-upstream deadline; actively abort provider I/O |
| `gateway.transport.connect-timeout-ms` | 2000 | TCP connection bound |
| `gateway.transport.read-timeout-ms` | 30000 | Response and socket-idle bound |
| `gateway.transport.pool-acquire-timeout-ms` | 250 | Maximum shared-pool queue wait |
| `gateway.transport.max-connections` / `max-connections-per-route` | 32 / 8 | Strict shared HTTP pool |
| `gateway.transport.max-response-bytes` | 2097152 | Maximum buffered upstream success body |

The synchronous request deadline does not guarantee total servlet completion: body reads check between reads with a 5 s idle bound, while SQL and response writes have separate bounds/limitations. Database defaults are 10 Hikari connections, 2 s connection/query timeout and 5 s JDBC socket/transaction timeout. Measure before raising concurrency; Phase 7 must add stream/write/disconnect controls. Spring graceful shutdown currently uses its framework grace default; the longer stream drain procedure below is a future qualification requirement.

Metrics include `gateway.requests` by HTTP status, `gateway.requests.active`, `gateway.admission.rejected`, `gateway.inference.outcomes`, `gateway.inference.duration`, `gateway.provider.duration`, and `gateway.diagnostics.dropped` (Prometheus normalizes dots to underscores). Validation/authentication denials do not create database rows. Diagnostic writes occur once per inference execution and are best effort; a failure increments the drop counter without replacing the inference result. These are not audit or financial records.

### Target lifecycle for subsequent phases

- Liveness reflects whether the process can make progress, not whether every provider is healthy. Avoid outage-driven restart storms.
- Readiness requires compatible schema, usable published configuration, fresh security state, and dependencies needed for governed admission. Failed providers affect route availability, not automatically process liveness.
- Startup probes allow migrations/bootstrap to complete. Run schema migration as an explicit deployment job with a privileged migration role; runtime role is restricted. Test lock contention and restart behavior.
- On shutdown, mark unready, stop new admission, drain streams, cancel remaining attempts after the grace deadline, settle or retain reservations and close pools. Reference drain grace is 150 seconds for a 120-second request deadline; align ingress/load-balancer and termination timing.
- Bound concurrency and queues. Scale with active requests/streams, queue time and CPU/memory, not CPU alone. Record DB connections per replica so autoscaling cannot exhaust the database.

The dependency outage matrix in [Architecture.md](Architecture.md) is authoritative. Strict limits never silently become best-effort when Redis fails. PostgreSQL loss blocks new governed admission; stale revocations block new traffic after the security freshness bound.

## Monitoring and capacity evidence

Provide dashboards and alerts for gateway-controlled errors, client-visible upstream errors, request/stream saturation, p95/p99 latency, TTFT, deadline/cancellation rates, retries/fallback, config convergence, circuit status, Redis/DB availability, exporter backlog, dropped diagnostic events and old budget reservations.

Define the availability SLI from valid admitted requests. Policy/limit denials are counted separately; upstream failures remain visible in a separate end-to-end SLI rather than disappearing from operational reports. A stream that starts successfully and later fails is a failed completion. Record error-budget burn alerts and incident thresholds with the selected SLO window.

The initial performance target from PRD uses a controlled upstream, 100 requests/s and 200 concurrent streams. Phase 6 records payload sizes, stream length/token cadence, operation mix, hardware, JVM/GC, DB/Redis topology, network and test duration. Measure non-streaming overhead against direct-upstream latency, and added TTFT separately from generation time. Publish saturation and slow-consumer behavior. Vendor benchmarks are not evidence for this application.

Qualification includes at least a one-hour steady-state test and a longer soak sized to expose resource leaks, plus overload, provider outages, dependency recovery and rolling upgrades. Assign the final soak duration and workload after the baseline; do not claim these runs have happened.

## Backup, restore and upgrade

PostgreSQL backups and WAL/PITR protect configuration, identities, audit, usage and reservations. Back up encryption-key references/configuration and required object data separately; storing ciphertext without recoverable keys is not a restore plan. Redis is rebuildable cache/limiter state, not authoritative spend.

Reference recovery targets are RPO ≤15 minutes and RTO ≤60 minutes, to be proven on the declared topology. Database recovery can lose recent usage records within RPO: retain/recover durable dispatch evidence and reconcile provider activity before reopening strict-budget paid traffic. Never reset spending to the last backup and assume no later cost occurred.

Restore procedure to implement and rehearse:

1. Isolate ingress and restore database/object data into a clean environment using verified backups.
2. Validate schema, configuration hashes, tenant ownership and integrity; restore secret access separately.
3. Reapply current revocations, retention/deletion tombstones and security state from authorized recovery records.
4. Reconcile unsettled attempts and any provider activity beyond the restored point. Keep affected paid routes closed or conservatively reserved while uncertain.
5. Rebuild Redis state conservatively, warm config snapshots and run synthetic tenant/permission/inference checks.
6. Reopen a canary, monitor errors/spend/config convergence, then expand. Record actual RPO/RTO and gaps.

Upgrade path: back up → verify source/target compatibility → apply additive schema → stage new binary/config → canary → monitor → promote. Keep old instances only while both binaries support the schema and security contract. Destructive schema contraction happens after a release compatibility window. Database down-migration is not the default rollback; use roll-forward or isolated restore when appropriate. Configuration rollback never reverses migrations, charges or revocations.

## Runbooks required before GA

| Incident | Required operator action and evidence |
|---|---|
| Provider timeout/429/outage | Inspect sanitized attempt reasons, deadlines/circuits and eligibility; disable target or activate validated policy; no guardrail/residency bypass |
| Database failure | Stop governed admission; restore/fail over; reconcile in-flight uncertainty before reopening |
| Redis failure | Observe fail-closed strict routes, bypass cache, restore counters conservatively and verify multi-replica limits |
| Bad configuration | Inspect replica acknowledgements, activate prior validated revision, keep current security epoch |
| Key or provider-secret compromise | Revoke/disable, propagate epoch, rotate, inspect audit/usage, verify convergence |
| Budget discrepancy | Inspect price/usage source, retries and uncertain reservations; append corrections rather than overwrite ledger |
| Export backlog | Inspect bounded queue/outbox age, destination health and poison events; retry idempotently, alert on retention pressure |
| Capacity exhaustion/slow clients | Shed load within bounds, inspect active streams/pools/DB connections, scale within downstream capacity |

## Release evidence checklist

These are intentionally unchecked until implementation and verification occur. A release owner attaches revision, environment, command/report and responsible owner to each item.

- [ ] Supported dependency/runtime versions, release artifact manifest, SBOM, scans and signatures verified.
- [ ] Fresh install, populated MVP upgrade and previous-supported-version upgrade passed on PostgreSQL.
- [ ] Demo credential rejection, restricted management and non-root runtime verified.
- [ ] SDK/protocol/provider conformance matrix completed, including controlled live-provider smoke gates.
- [ ] Two-tenant API/database/cache/async authorization tests passed.
- [ ] Cross-replica quotas, budget reservation, crash/reconciliation and Redis/DB failure tests passed.
- [ ] Stream cancellation, slow consumers, partial failure and no-post-output-retry tests passed.
- [ ] Default content/secret non-disclosure, guardrail and retention/delete/export tests passed.
- [ ] Config activation, missed-notification recovery, stale replica removal, revocation and rollback tested.
- [ ] Load/soak/chaos results meet the published workload targets and resource bounds.
- [ ] Backup/PITR restore, current-revocation replay and usage reconciliation meet declared recovery targets.
- [ ] Helm, systemd and Podman packages demonstrate the same policy/security behavior; offline local-provider install passed.
- [ ] Console/API onboarding and operator incident journey accepted; accessibility and authorization checks complete.
- [ ] On-call/security/release owners, incident contacts, supported versions, limitations and distribution terms published.
- [ ] Canary promotion and binary/config recovery rehearsed; all F01–F13 gates closed.

No production deployment has been performed. Phase 6 evidence does not close this GA checklist; [Phases.md](Phases.md) assigns the remaining work and release order.
