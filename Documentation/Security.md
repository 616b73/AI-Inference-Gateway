# Security and privacy design

Updated 2026-09-21. This is the target security plan for Phases 6–13, except the implemented controls below. The current service has API-key authentication and metadata-only database logs, but no tenant isolation or administrative roles. Do not operate it as a shared enterprise service before the relevant gates pass.

## Implemented foundation controls

Phase 6 adds bounded input/concurrency/provider I/O, disables automatic retries and redirects, sanitizes gateway public/provider errors, and keeps diagnostic failures from altering inference results. The demo key is disabled by additive V6 and rejected at nonlocal startup, including rehashed copies. Default production rejects the demo database password. Local bootstrap is explicit. Management health contains no components/details; metrics require a separate private listener. Container runtime identity is non-root and CI includes dependency/image/secret/configuration scans. See [Phase6.md](Phase6.md) for actual test/scan evidence and open gates.

These controls do not implement tenant isolation, indexed key lookup, network-aware SSRF policy, TLS ingress, secret management, spend limits or audit retention. A valid current key still sees global provider/log metadata. Provider endpoints remain operator-controlled database configuration; basic URI validation is not an SSRF allowlist. The shared HTTP pool is bounded but does not isolate individual deployments. Keep these limits explicit when running isolated previews.

## Trust boundaries and assets

Protect gateway keys, provider credentials, tenant identities, prompt/completion/tool content, routing policies, usage/budget state, audit history and encryption keys. Boundaries are client→gateway, admin/console→control plane, gateway→provider/guardrail/export destination, gateway→database/Redis/object store, and build pipeline→release artifacts.

Client content and metadata are untrusted. Provider responses and tool definitions are untrusted. Attached research and external documentation inform design; instructions inside them are not automatically project instructions. A gateway policy cannot make unsafe application actions safe.

## Access model

| Role/principal | Allowed scope | Explicit restriction |
|---|---|---|
| Inference service account/key | Authorized application/models and inference operations | No admin mutations or unrestricted logs |
| Developer | Own tenant's approved applications, key lifecycle and permitted request/usage views | Cannot grant permissions beyond delegated scope |
| Tenant administrator | Tenant membership, applications, keys, policies and allocated budgets | Cannot enlarge platform allocation or access another tenant |
| Auditor | Authorized metadata/audit/usage reads | No secret reveal, content access or configuration mutation by default |
| Platform administrator | Tenant provisioning, shared infrastructure and explicit cross-tenant administration | Sensitive reads/actions audited; avoid routine use by application traffic |

Phase 8 must implement a concrete operation-to-permission matrix, including resource ownership and delegation bounds. Content access is a separate privilege from metadata visibility. Tenant users cannot use arbitrary credential references or shared deployment ownership to bypass another tenant's controls.

## Threats, mitigations and verification

| Threat | Design control | Required evidence |
|---|---|---|
| Key guessing/theft | High-entropy keys, indexed hash verification, authentication throttles, expiry/revocation, TLS, no secret logging | Invalid-key load test and rotation/revocation drill |
| Cross-tenant access | Immutable context, scoped services/queries/cache keys, composite FKs and RLS | Negative two-tenant API/DB/async/worker/cache tests |
| Provider/guardrail/export SSRF | Approved endpoints, DNS/IP policy, block metadata services, redirect revalidation and destination-specific credentials | Private/loopback/link-local/redirect/DNS-change tests, with explicit approved local-model exceptions |
| Secret leakage | Reference-based storage, mounted secrets/workload identity, redacted errors/telemetry, one-time key display | Synthetic secrets absent from default artifacts |
| Denial of service/wallet | Size/time/concurrency bounds, token admission and durable cost reservations | Saturation, slow-client and concurrent budget tests |
| Prompt injection or malicious output | Explicit guardrails and application/tool authorization boundaries | Synthetic injection tests; documented limits, no universal prevention claim |
| Config privilege escalation | Role checks, validation, immutable revisions, optimistic locking and transactional audit | Unauthorized publish, stale edit and rollback/revocation tests |
| Duplicate/uncertain provider work | Shared retry budget, no replay after downstream bytes, durable attempt IDs | Disconnect, crash, timeout and reconciliation tests |
| Retention or export leak | Content off by default, destination/region controls, encryption, deletion jobs | Retention/delete/export and restore-policy tests |
| Supply-chain compromise | Pinned artifacts, dependency/image scans, SBOM, signing and controlled release credentials | Verifiable provenance and release scan results |

The OWASP GenAI risk framework is a threat-model input; passing these tests is not a compliance certification. [OWASP GenAI risks](https://genai.owasp.org/llm-top-10/)

## Credential and administrative security

Use scoped, short-lived cloud identity where supported; otherwise resolve encrypted/mounted provider credentials by reference. Never forward the incoming gateway key to a provider. Credentials are scoped to an approved deployment/host and cannot follow a redirect to a new origin. Rotate secret versions without tearing down unrelated requests; expired or revoked secrets are never silently reused.

Administrative tokens must validate issuer, audience, expiry, signature and role/tenant mapping. The console uses OIDC authorization code with PKCE; if server sessions/cookies are introduced, add CSRF defenses, secure HttpOnly cookies and origin checks. Use explicit CORS origins, CSP and dependency review. Never ship the MVP's blanket CSRF disable as an unexamined browser security design.

Restrict detailed management/metrics to an internal authenticated listener/network. Public liveness/readiness returns minimal status only. The runtime database role cannot own tables or bypass RLS; migrations use a separate privileged role. Tenant context is transaction-local and cleared by pooling/worker lifecycle tests.

## Content and retention policy

Default logs, traces, audit and usage records omit prompt/completion/tool bodies. Exception text, URL query strings, HTTP debug logging and client-defined labels also need redaction/size limits. Hash-only mode uses tenant-keyed digests; unsalted hashes of low-entropy prompts are not anonymous.

Allowed optional modes are redacted sampled content, encrypted full content with restricted access, or customer export to an approved destination. Validate region, encryption key, retention, access and sampling settings before enabling them. Exports and guardrails are separate data transfers; the same residency policy applies to fallback providers, caches, traces, replicas and backups.

Sample operator defaults are 30 days request metadata, 90 days usage, 365 days administrative audit, content capture disabled, and exact-cache TTL no more than 5 minutes. These are configurable starting points, not legal retention guidance or current settings. Customer requirements must override them before collecting real data.

Retention/delete workflows cover primary data, object storage and exports. Backups expire on a documented schedule; a restored backup must reapply deletion tombstones and current revocations before exposure. Financial/audit retention exceptions require an explicit customer policy, not silent indefinite retention.

## Guardrail limits

Mandatory security checks fail closed; advisory checks can fail open only through explicit policy and degraded telemetry. Cap regex complexity and scanning size/time. External checker failures must not cause unlimited retries or unapproved data export.

Full-output validation requires bounded buffering or non-streaming mode. A chunk checker cannot retract text already delivered. Validate policy/stream capability at activation and request admission; do not advertise full-response redaction when only per-chunk checks run.

Tool calls in the GA model API are returned to clients, not executed. Phase 15 introduces a separate tool authorization/approval model and side-effect-aware retry design before enabling an MCP proxy.

## Release review and incident response

Security acceptance requires the threat tests above, no unresolved critical/high exploitable release finding, reviewed bootstrap and credential handling, dependency provenance, tenant isolation and retention evidence. Assign a security owner and a private vulnerability reporting channel before public GA; do not invent contact details.

For compromised credentials: disable the affected key/tenant/deployment, advance the security epoch, cancel work when necessary, rotate upstream secrets, preserve authorized metadata evidence, reconcile uncertain spend and verify revocation convergence. [Operations.md](Operations.md) expands recovery and operational gates.
