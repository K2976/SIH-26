# Discovered Rules — FitForge

## Mandated

- ALWAYS branch as `slice/<name>` for any change to this repo (affirmed
  2026-09-04, source: `fitforge/CLAUDE.md` "Git workflow").
- ALWAYS look up and pin current dependency versions from documentation
  rather than guessing; flag anything that can't be confirmed (affirmed
  2026-09-04, source: original request + `fitforge/CLAUDE.md` "Stack").
- ALWAYS hardcode `DEBUG = False` as a literal in `backend/fitforge/prod.py`
  (and the shared `base.py` default), not as a truthy-defaulted environment
  read — devsecops review flagged that an env-var default can silently flip
  (affirmed 2026-09-04).

## Forbidden

- NEVER push directly to `master`/`main`, or merge into it without explicit
  human action — no auto-merge, ever, including by an AI agent (affirmed
  2026-09-04, source: `fitforge/CLAUDE.md` "Git workflow").
- NEVER write a user-supplied secret (e.g. a database connection string)
  into any AIDLC artifact, questions file, memory diary, or audit log
  (already affirmed at intent-capture 2026-09-03; restated here as a
  standing team practice).
