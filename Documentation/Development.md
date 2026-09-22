# Development workflow

Updated 2026-09-15. The project is moving from a constrained MVP to a complete product. The deleted rules file is retired. Its historical library prohibitions, fixed folder prescriptions and one-phase-at-a-time workflow are not current requirements.

## Authority to improve the MVP

The product owner explicitly authorized changes to existing architecture and implementation when they correct defects, improve maintainability or design, or prevent unnecessary future complexity. Developers and agents may refactor, replace or remove unsuitable MVP components as part of implementation without seeking renewed design permission for each change. Existing code and the proposed architecture are starting points that can be revised on evidence.

Assess correctness, responsibility boundaries, coupling, testability, operational cost and the needs of planned features. Choose the smallest coherent change that resolves the problem; a broader redesign is appropriate when incremental fixes would preserve the underlying problem. Retaining the current stack or structure is not an objective in itself.

For a significant change, document the concrete problem, alternatives considered, chosen design, expected benefit, affected components/contracts/data, migration or compatibility impact, validation and recovery limits. Update Architecture and affected product/API/phase documents before or alongside implementation; append the actual change and results to Roadmap when complete. Do not require a separate ADR for every refactor.

Internal implementation compatibility is not required. Legacy API/schema preservation in the plan is a default transition strategy, not a veto on necessary redesign: document and test any deliberate contract change and update affected clients/examples and release gates. Inspect actual consumers and data before choosing a transition. The MVP's demo origin alone does not establish that stored data is disposable or authorize deleting it. Existing environment permissions and authorization for production actions still apply.

## Working documents

| Document | Responsibility |
|---|---|
| [PRD.md](PRD.md) | Product outcomes, scope, requirement IDs and release targets |
| [Architecture.md](Architecture.md) | Inspected baseline, target design, contracts, tradeoffs and migration |
| [Phases.md](Phases.md) | Dependencies, work packages, acceptance evidence and next backlog |
| [API.md](API.md) | Current versus proposed endpoints and compatibility |
| [Security.md](Security.md) | Threat model, identity, privacy and security acceptance |
| [Operations.md](Operations.md) | Deployment contract, recovery and production evidence |
| [Research.md](Research.md) | Research-to-plan traceability and sources |
| [Roadmap.md](Roadmap.md) | Append-only development history plus accurate current-state summary |
| [README.md](../README.md) | Runnable setup and implemented capabilities |

Read the relevant context before a meaningful change; do not turn every small edit into a documentation ceremony. Product/design changes update the owning document before or alongside implementation. Routine fixes need appropriate tests and a clear explanation, not a new planning phase.

## How to deliver

1. Select a bounded outcome from the phase backlog and inspect actual code. Record prerequisites and any assumptions that affect correctness.
2. Choose libraries and implementation details using maintainability, compatibility, licensing, security and measured need. The old allowlist no longer applies. Prefer existing stack components when suitable, without treating that preference as a ban on alternatives.
3. Implement a vertical slice with relevant tests and migration/rollback notes. Independent work may proceed concurrently when contracts and file ownership are clear; phases are not a global execution lock.
4. Validate the behavior changed. Use fast unit tests for logic, real PostgreSQL for persistence/migrations, Redis/multiple replicas for distributed enforcement, wire/SDK tests for protocols and focused load/chaos tests for concurrency/failure behavior.
5. Explain the outcome, validation and limitations. Update the historical log for meaningful shipped work. Mark a phase complete only when its exit evidence exists.

Agentic implementation may make routine reversible choices within the accepted scope without repeated approval requests. Customer-facing publication, production changes, destructive migration, credential use or externally charged testing still follow the user's authorization and environment permissions. This workflow does not grant permission to deploy or spend merely because a feature appears in the plan.

## Engineering expectations

Keep provider wire details behind adapters, policy evaluation deterministic, credentials out of content/logs and ownership explicit. Bound time, memory, concurrency and retries. Preserve supported API behavior unless a deliberate contract change is documented under the policy above; evolve persisted data through a tested migration strategy. These are product correctness needs, not a requirement for a particular package layout or exhaustive tests that duplicate implementation.

Start with a modular monolith. A broker, microservice split, UI framework, SDK or reactive component is a design choice justified by concrete needs. Record substantial decisions with problem, chosen option, alternatives, consequences and a reconsideration trigger; the architecture decision table is sufficient until a separate ADR would help.

For each issue/PR, include the trigger and resulting behavior, requirement/phase reference, validation, migration impact and material limitations. Never describe mock-only tests as real provider validation. Never infer production readiness from the historical MVP suite.

## Documentation maintenance

Keep planned and implemented features visibly separate. README examples must run on the current checkout; proposed APIs belong in API/Architecture until implemented. Do not recreate the deleted rules file or restore its constraints through another filename.

Preserve Roadmap milestone text. Corrections are new entries, not historical rewrites. Its current architecture/stack summaries and next-step links can be updated in place. Future features move into the current summary only after implementation and evidence.
