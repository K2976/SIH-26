# Intent Statement — FitForge Monorepo Skeleton

## Problem Statement

There is no repository skeleton yet for FitForge: no Django backend project,
no Android Gradle project, no CI, and no dev tooling. Before any feature
slice (per the vertical-slice convention) can be built, the project needs a
working, lint-clean, test-runnable scaffold for both halves of the stack plus
the CI/pre-commit machinery that keeps `main` green. [desc]

## Target Customer

The internal development team (currently the sole contributor) building
FitForge. The scaffold is not user-facing; it is the foundation every future
feature slice — Django models through Compose UI — is built on top of. [desc]

## Success Metrics

- `pytest` runs and passes with zero collected tests (backend project is
  importable, settings load, migrations are checkable). [desc]
- `./gradlew assembleDebug` succeeds on this machine, meaning the Android SDK
  and JDK toolchain are actually present and working, not just
  well-formed-on-paper. [Q2]
- `ruff` and `detekt` both pass with no findings against empty/skeleton code.
  [desc]
- CI (`.github/workflows/ci.yml`) is green on a pushed branch running all
  four checks above plus gitleaks. [desc]

## Initiative Trigger

Starting the FitForge project — this is the first piece of work in the
repository, run as the walking-skeleton bootstrap before any feature slice.
[desc], [memory:M1]

## Initial Scope Signal

- **Workflow-selected scope:** `monorepo-skeleton` (custom composed scope —
  framing stages plus practices-discovery, then straight to
  code-generation/build-and-test/ci-pipeline; no design stages). [scope]
- **User-confirmed product boundary:** matches the workflow-selected scope
  exactly — scaffolding only, no business logic, no models beyond app
  skeletons. [Q1]

## Confirmed (previously assumptions, resolved via follow-up)

- Local dev and CI both target a real Postgres database rather than SQLite:
  local dev uses a hosted (Neon) Postgres instance referenced only via the
  `DATABASE_URL` environment variable in a gitignored `backend/.env`; CI uses
  an ephemeral Postgres service container. The connection string itself is
  credential material and is never recorded in this or any other artifact,
  audit log, or committed file. [Q3], [Q4], [Q5]
- Installing the Android SDK (~1-2GB) and accepting Google's SDK licenses on
  this machine is authorized, scoped to cmdline-tools, one platform, and
  build-tools only — not a full Android Studio install. The user will
  separately install Android Studio themselves later, out of scope here.
  [Q2], [Q6]

## Assumptions & Open Questions

None.

## Review

**Verdict:** READY
**Reviewer:** aidlc-product-lead-agent
**Date:** 2026-09-03T21:24:17Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | intent-statement.md > Target Customer | The "solo contributor" claim is grounded by [desc] but not by the explicit confirmation in Q7 (no other stakeholders/cadence) | Add `[Q7]` alongside `[desc]` on that sentence | New |

### Summary

Every substantive claim traces to a permitted source; the assumption-confirmation
loop ran and closed, so both artifacts show `None.` under Assumptions & Open
Questions. Success metrics are concrete and machine-checkable (pytest exit
status, gradlew exit status, ruff/detekt exit status, CI status) rather than
vague. Scope boundary is explicit and narrow (scaffolding only, no models).
One Minor citation gap (R-01) does not block readiness — a developer could
start code-generation from this without coming back to ask questions.
