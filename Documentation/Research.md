# Competitive research and planning decisions

Updated 2026-09-14. This note translates the supplied **AI_Inference_Gateway_Competitive_Research.docx** into project decisions and delivery scope. The Word document was supplied separately and is not copied into the repository. Its recommendations were treated as research, not as executable or authoritative development instructions.

## Evidence and limitations

The supplied report covers LiteLLM, Portkey, Bifrost, Kong, Agent Router, Apache APISIX, Higress, Helicone, Cloudflare, Azure API Management, TrueFoundry and WSO2. Its feature matrices are directional descriptions of documented capabilities, not independent benchmarks or guarantees about edition entitlements.

This revision inspected the local source/configuration/tests and selectively checked primary sources on 2026-09-14. It did not repeat the entire competitive study or independently benchmark any vendor. Pricing, provider counts, comparative superiority and certification claims are not used as requirements.

## Research to product traceability

| Research theme | Project decision | Requirement / phase |
|---|---|---|
| SDK-compatible gateway as the adoption path | Chat Completions, embeddings, explicit compatibility matrix; preserve legacy facade | F02/F13; 7 |
| Broad providers with shared contracts | Generic HTTP adapter plus tested Ollama/OpenAI/Anthropic; Bedrock/Azure later | F03/F14; 7/14 |
| Reliability as a coordinated pipeline | Virtual aliases, eligible pools, deadline/attempt budgets and safe fallback | F05; 9 |
| Hierarchical governance | Tenant/application/service account/key scopes; distributed limits and durable budgets | F04/F06; 8/10 |
| Explainable routing and simulation | Decision records, immutable policy revisions and shared live/simulator evaluator | F05/F09; 9/12 |
| Privacy as a product property | Content off across all sinks; scoped retention, guardrails and exports | F08; 11 |
| Observability beyond request rows | Early metrics; complete request/attempt traces, TTFT, cost and dashboards | F07; 6/11 |
| Caching for cost/latency | Opt-in exact cache after ownership/privacy; semantic cache deferred | F11; 11 |
| Self-hosted and disconnected use | One config model, Helm/systemd/Podman packages, verified offline bundle | F12; 13 |
| Java ecosystem differentiation | Keep Spring-based core, documented extension seams and Java example | F03/F10; 7/12 |
| Enterprise operator experience | API-first console, safe config rollout and operational recovery | F09/F10/F12; 12/13 |
| Agent and inference workload governance | Separate MCP/tool and endpoint-picker extensions after GA foundations | F15; 15 |

The recommended positioning is an inference gateway for platform teams that value self-hosting, understandable policy and privacy across local/cloud models. This is a strategic hypothesis to validate with pilot users, not a claim of exclusive capabilities or market demand.

## Adaptations to the supplied roadmap

The report's proposed Phases 6–11 are expanded into project Phases 6–15. Numbering in the report is not the implementation authority; Phases.md is.

- Added a foundation phase before provider expansion because source inspection exposed timeout, validation, logging, seed and migration-test gaps.
- Moved identity, minimal admin authorization and audit before shared governance. Observability and operational design start early rather than waiting for a late hardening phase.
- Separated full control-plane/console delivery from production qualification, with measurable deployment and recovery gates.
- Preserved `/v1/inference` as a facade; the report's “compatibility alias” wording does not mean identical wire formats or an HTTP redirect.
- Kept PostgreSQL authoritative for hard-budget reservations; Redis atomic limits do not imply durable financial accounting or globally consistent circuits.
- Added explicit uncertain usage, crash recovery and no-post-output-retry behavior. “No duplicate accounting” does not mean exactly-once provider execution.
- Limited exact caching to explicitly eligible workloads and recognized the limits of streaming redaction and metadata-only replay.
- Retained MVC as a measured candidate; streaming is not by itself a reason to rewrite all persistence/control-plane code.
- Deferred native Responses/Messages APIs and broader modalities to explicit operation designs; upstream Anthropic support does not imply inbound protocol parity.

## Primary sources selectively checked

| Source | Observation used in planning |
|---|---|
| [LiteLLM reliability](https://docs.litellm.ai/docs/proxy/reliability) | Documents ordered model-group fallbacks and their interaction with retries; used as a reliability design reference, not copied failure policy |
| [Cloudflare dynamic routing](https://developers.cloudflare.com/ai-gateway/features/dynamic-routing/) | Documents named routes, conditional/percentage decisions, versions and rollback; supports treating routing changes as versioned configuration |
| [Bifrost overview](https://docs.getbifrost.ai/overview) | Reference for a gateway combining provider access and operational/governance features; no performance claim adopted |
| [Ollama OpenAI compatibility](https://docs.ollama.com/api/openai-compatibility) | Documents compatibility with parts of the OpenAI API; supports testing a generic adapter rather than assuming full equivalence |
| [Spring asynchronous requests](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html) | MVC offers SSE/async integration; bounded execution and slow-client measurement remain design requirements |
| [OpenTelemetry GenAI notice](https://opentelemetry.io/docs/specs/semconv/gen-ai/) | Current page directs readers to a dedicated conventions repository; pin a reviewed schema at implementation time |
| [OWASP GenAI risks](https://genai.owasp.org/llm-top-10/) | Input to threat coverage for injection, sensitive content and consumption risks; not a certification standard |
| [Kubernetes Inference Extension](https://gateway-api-inference-extension.sigs.k8s.io/) | Defines inference routing and endpoint-picker concepts; suitable later integration boundary for self-hosted serving |

## Additional sources in the supplied report

These links preserve the report's wider reference trail; their full capability matrices were not independently revalidated in this revision.

- [Portkey AI Gateway](https://portkey.ai/docs/product/ai-gateway)
- [Kong AI Gateway](https://developer.konghq.com/ai-gateway/get-started/)
- [Agent Router](https://theagentrouter.ai/)
- [Apache APISIX AI Gateway](https://apisix.apache.org/ai-gateway/)
- [Higress](https://higress.ai/)
- [Helicone repository](https://github.com/Helicone/helicone)
- [Azure API Management AI gateway capabilities](https://learn.microsoft.com/en-us/azure/api-management/genai-gateway-capabilities)
- [TrueFoundry AI Gateway](https://www.truefoundry.com/docs/ai-gateway/intro-to-llm-gateway)
- [WSO2 AI Gateway](https://apim.docs.wso2.com/en/latest/ai-gateway/ai-gateway-overview/)
- [LiteLLM access control](https://docs.litellm.ai/docs/proxy/access_control)
- [Cloudflare AI Gateway](https://developers.cloudflare.com/ai-gateway/)

The report also cited this project's README, PRD and Roadmap. Local source inspection takes precedence over old documentation where implementation differs. Before implementing a protocol, provider or deployment integration, verify its current primary specification and record supported versions in the conformance matrix.
