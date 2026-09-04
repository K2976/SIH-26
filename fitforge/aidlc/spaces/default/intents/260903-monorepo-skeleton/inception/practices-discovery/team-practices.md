# Team Practices — FitForge

## Way of Working

Branches are named `slice/<name>`; commits are conventional-format
(e.g. `feat(food): add portion confirmation step`). `master`/`main` stays
green and installable, and no one — including an AI agent — pushes to it
directly or auto-merges into it. `master` only moves when the user
manually merges an approved PR. (Affirmed 2026-09-04, source:
`fitforge/CLAUDE.md` "Git workflow".)

## Walking Skeleton

Not a standing ceremony for this project's Construction bolts in general —
determined per scope. This specific workflow (`monorepo-skeleton`) has no
gated Bolt-1 skeleton step because the workflow itself produces the
skeleton; there is nothing further to bootstrap underneath it.

## Testing Posture

- **Methodology**: test-after
- **Ordering**: implement each applicable testable layer, then write and
  run that layer's tests.

This pass ships zero tests by design (`pytest` running clean with zero
collected tests is an explicit "done when" criterion) — there is no
business logic yet to test against. Once feature slices add real logic,
the engine specifically gets property-based tests via hypothesis (per
`fitforge/CLAUDE.md`), following this same test-after ordering. No numeric
coverage floor is affirmed yet; defer that decision to the first feature
slice's practices re-run once there is code to measure.

## Deployment

Not applicable yet — no deployment target exists at scaffold stage, and
Operation-phase stages are out of scope for this workflow. Revisit at the
first slice that introduces a deployable environment.

## Code Style

- Python (backend): ruff (lint + format), no additional formatter.
- Kotlin (Android): detekt, no additional formatter (no ktlint).
- Directory layout: exactly as specified in `fitforge/CLAUDE.md`'s
  "Repository layout" section — code-generation follows it verbatim rather
  than re-deciding structure.
