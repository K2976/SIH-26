# Evidence — Practices Discovery

## What each participant inspected

- **Lead (pipeline-deploy):** `fitforge/CLAUDE.md` (Git workflow, Testing,
  Security, Stack sections), `aidlc/spaces/default/memory/org.md`'s five
  matching sections (used as suggested defaults, superseded where CLAUDE.md
  was more specific).
- **Quality:** the project's stated test stack (pytest, pytest-django,
  factory_boy, hypothesis) against the "zero tests, done when clean" bar
  for this specific pass; recommended not fixing a coverage floor yet.
- **Developer:** the repository layout CLAUDE.md already pins for both
  backend and Android; recommended code-generation follow it verbatim
  rather than re-deciding structure, and keep the linter toolchain minimal
  (ruff + detekt, no additional formatter).
- **DevSecOps:** gitleaks placement (pre-commit + CI), the existing
  secret-handling rule from intent-capture, and the `DEBUG` default —
  flagged that it must be a hardcoded `False` literal in split settings,
  not an env-var default.

## Interview decisions

All five interview questions were answered by confirming the suggested
default drawn from `fitforge/CLAUDE.md`/`org.md` — no deviations. See
`practices-discovery-questions.md` for the full record.

## Unresolved uncertainty

- Numeric test-coverage floor: deliberately deferred to the first feature
  slice's practices re-run, once there is real code to measure against.
- Deployment cadence: deliberately deferred until a deployment target
  exists.
