# AI Inference Gateway — Project Rules

## Roadmap.md Maintenance

You are responsible for continuously maintaining `Documentation/Roadmap.md`. This file is the project's living development log — the single place where any AI model or developer can reconstruct what was built, in what order, why decisions were made, and what state the codebase is in.

Roadmap.md is not a plan (that's `Phases.md`) and not a spec (that's `PRD.md`/`Architecture.md`). It is a **historical record**, written after work is done, describing what actually happened — including problems hit and how they were resolved.

### When to update Roadmap.md

Update it whenever any of the following happens:

- A phase from `Phases.md` is completed (one roadmap entry per phase, minimum).
- A significant feature, refactor, or architectural change ships, even mid-phase, if it's substantial enough to matter to someone reading the history later.
- A non-trivial bug is found and fixed in a way that changes design (not routine typo fixes).
- The tech stack, folder structure, or data model changes in a way that diverges from what `Architecture.md` currently describes (update `Architecture.md` too — `Roadmap.md` records that it changed and why; `Architecture.md` reflects current state).
- A dependency, library, or infrastructure decision is made or reversed.
- Environment/build issues were hit and solved (these are gold for future developers — always capture them).

Do not create a new entry for every commit or trivial change. Batch related work into one coherent milestone entry, written once the work is stable and tested.

### Writing rules

- Write in **past tense**, describing what happened — not instructions, not plans.
- **Be concrete.** Name the actual files, classes, endpoints, tables, and libraries involved.
- **Always justify non-obvious decisions.** If you chose library A over B, sync over async, one schema over another — say why.
- **Record friction honestly.** Build errors, version incompatibilities, environment quirks, and how they were fixed belong here.
- **Note test coverage changes** when relevant — don't fabricate numbers, only report what was actually run.
- Include a folder structure snapshot only when the structure meaningfully changed.
- **Never rewrite or delete past milestone entries.** History stays intact; corrections are additive (new milestone entry documenting the reversal).
- Keep the "Architecture & System Flow" and "Tech Stack" sections current, editing them in place each time they materially change.
- **Cross-reference `Phases.md`** where useful (e.g., "Completes Phase 5").
- Do not include anything marked out-of-scope in `PRD.md` as if it were built.

### Style

Match this density — specific, technical, skimmable:

```
What was done:
- Implemented RoutingEngine: explicit provider field in request → else active default provider from DB → else BAD_CONFIGURATION.
- Unit tests: explicit provider resolves correctly; omitted provider falls back to default; no default throws the correct error.

Key decisions:
- Rule-based routing over intelligent routing — MVP goal is to prove the gateway pattern works, not optimize provider selection.
```

Avoid vague entries like "improved backend" or "fixed some bugs."

### File structure

Roadmap.md always has these sections in this order:
1. `Architecture & System Flow` — current state, edited in place
2. `Tech Stack` — current state, edited in place
3. `Development Log (Chronological)` — append-only milestone entries
4. `Next Steps / Future Enhancements` — forward-looking, edited in place

Every milestone must have at minimum: **Goal**, **What was done**, and **Key decisions**. Optional subsections: **Known issues / follow-ups**, **Tests**.

## Documentation-First Rule

Read `PRD.md`, `Architecture.md`, `Rules.md`, and `Phases.md` before implementing any feature. If a proposed change adds scope, update documentation first — code changes follow doc changes, not the other way around.
