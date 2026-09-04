# Initiative Brief — FitForge Monorepo Skeleton

## Intent & Problem Statement

No repository skeleton exists yet for FitForge. Before any vertical feature
slice can be built, the project needs a working, lint-clean, test-runnable
scaffold for both the Django backend and the Android app, plus the CI/pre-commit
machinery that keeps `main` green. (See `ideation/intent-capture/intent-statement.md`.)

## Market Validation

Not applicable — internal developer tooling, not a market-facing initiative.
Market Research was correctly SKIPped for this scope.

## Feasibility & Risk Highlights

| Risk | Mitigation | Status |
|---|---|---|
| No Android SDK / JDK 17 on this machine, but `assembleDebug` is a stated done-when criterion | Install Android cmdline-tools + one platform + build-tools + JDK 17 during code-generation | Acknowledged, mitigation planned |
| No local Postgres server | User supplied a hosted (Neon) Postgres connection string; stored only in gitignored `backend/.env` | Resolved |
| Secret material (DB connection string) must never leak into version-controlled artifacts | Recorded as a project-level `## Forbidden` practice; never written to any AIDLC artifact/audit log | Resolved, codified as standing practice |
| Every dependency version must be current, not guessed | Deferred to code-generation: look up each version via web search before pinning | Acknowledged, deferred to code-generation |

## Scope Boundary

See `ideation/scope-definition/scope-document.md`. Summary: full backend +
Android scaffold, CI, pre-commit, dev hygiene files — everything Must Have,
nothing deferred. No business logic, no Android Studio install, no
deployment/observability (Operation phase out of scope for this workflow).

## Concept Visuals

Not applicable — no UI/UX surface exists yet at scaffold stage. Rough
Mockups was correctly SKIPped for this scope.

## Team Plan

Solo developer + AI pairing, no mob/team formation needed. Team Formation
was correctly SKIPped for this scope.

## Go/No-Go Recommendation

**GO.** All Ideation artifacts are internally consistent (intent → scope →
backlog), every open risk has an acknowledged mitigation, and the user
confirmed readiness to proceed into Inception (Practices Discovery) directly.
