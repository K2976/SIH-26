# Practices Discovery — Questions

Greenfield project. Suggested answers below come from this project's own
`CLAUDE.md` (which is more specific than the framework's generic defaults)
plus the three reviewers' contributions in `contributions/`.

## Q1. How should branches and merges to `main`/`master` work on this repo?

Your project's `CLAUDE.md` already states: branches named `slice/<name>`,
conventional commit messages, and no direct pushes or auto-merges to
`master` by anyone — including an AI agent; only you merge an approved PR.

- A. Yes — use that exactly as the affirmed practice
- B. No — something different (please specify)

[Answer]: A. Yes — use that exactly as the affirmed practice

## Q2. Build a thin end-to-end slice first? A walking skeleton is a minimal version that runs the whole way through, built first to prove the pieces connect before the real features go in.

Not applicable here — this workflow IS the walking skeleton (the whole
point of this pass is standing up the skeleton itself, not building a
feature on top of one yet).

- A. Correct, not applicable — this pass produces the skeleton
- B. Actually, treat this differently (please specify)

[Answer]: A. Correct, not applicable — this pass produces the skeleton

## Q3. What's the testing approach for code written in this repo going forward — write code first and test after, or test-driven (write the failing test first)?

Suggested default: test-after (implement each layer, then test it), which
matches your `CLAUDE.md`'s description of hypothesis-based property tests
for the engine once it exists. This pass itself ships zero tests (an
explicit "done when" criterion, since there's no logic yet to test).

- A. Test-after — implement a layer, then test it
- B. Test-driven — write the failing test before the implementation
- C. Something else / mixed (please specify)

[Answer]: A. Test-after — implement a layer, then test it

## Q4. Any deployment/release cadence to affirm yet?

Not applicable — no deployment target exists at scaffold stage, and
Operation-phase stages are out of scope for this workflow.

- A. Correct, nothing to affirm yet
- B. Actually there is something to decide now (please specify)

[Answer]: A. Correct, nothing to affirm yet

## Q5. Confirm the code style tooling: ruff for Python (backend), detekt for Kotlin (Android), no additional formatter (no Black, no ktlint) — and confirm code-generation should follow CLAUDE.md's exact directory layout rather than opening that up as a fresh decision.

- A. Yes to both — ruff/detekt only, and follow CLAUDE.md's layout exactly
- B. No — change something (please specify)

[Answer]: A. Yes to both — ruff/detekt only, and follow CLAUDE.md's layout exactly

X. Other (please specify)

## Consolidated Summary Confirmation

- Looks correct
- Request changes

[Answer]: Looks correct
