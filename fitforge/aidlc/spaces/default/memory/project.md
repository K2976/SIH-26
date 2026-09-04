# Project-Level Rules

> Project-specific specialisation and corrections. Loaded after `org.md` and
> `team.md` as strict-additive guidance; contradictions with broader policy
> are rejected. Populated by practices-discovery and the self-learning loop.
>
> Use sparingly: most teams don't need a project layer. Reach for it
> only when this specific project needs stable, durable guidance beyond the
> team practice (for example, package-specific release checks or an additional
> regression suite for a legacy component).

## Way of Working

<!-- Project-specific specialisation. Example: -->
<!-- This monorepo requires package-scoped branch names and a package owner -->
<!-- review in addition to the team's normal merge policy. -->

## Walking Skeleton

<!-- Project-specific specialisation. Example: -->
<!-- The walking skeleton must exercise the legacy service adapter as well -->
<!-- as the new service boundary. -->

## Testing Posture

<!-- Project-specific specialisation. -->

## Deployment

<!-- Project-specific specialisation. -->

## Code Style

<!-- Project-specific specialisation. -->

## Tech Stack

<!-- Technology choices locked for this project. -->

## Decided

<!-- Decisions made in earlier stages that should not be re-asked. -->
<!-- Format: DECIDED: [decision] (Stage [slug], [date]) -->

## Scope Overrides

<!-- Custom scope rules for this project. -->

## Forbidden

<!-- Populated by practices-discovery affirmation gate. -->
<!-- Format: NEVER [behavior] (affirmed [date]) -->
<!-- Example: NEVER throw exceptions across service layer boundaries (affirmed 2026-05-17) -->

- Never write a user-supplied secret (e.g. a database connection string) into any AIDLC artifact, questions file, memory diary, or audit log. Secrets go only into gitignored .env files, referenced by name via environment variables. (learned 2026-09-03) <!-- cid:260903-monorepo-skeleton:intent-capture:30712949bf5e5f812aa5537db34b788bf5de078784f22c6bc5ff3889bfff0701 -->
- NEVER push directly to `master`/`main`, or merge into it without explicit (affirmed 2026-09-04)
human action — no auto-merge, ever, including by an AI agent (affirmed (affirmed 2026-09-04)
- NEVER write a user-supplied secret (e.g. a database connection string) (affirmed 2026-09-04)
into any AIDLC artifact, questions file, memory diary, or audit log (affirmed 2026-09-04)
(already affirmed at intent-capture 2026-09-03; restated here as a (affirmed 2026-09-04)
standing team practice). (affirmed 2026-09-04)
## Mandated

<!-- Populated by practices-discovery affirmation gate. -->
<!-- Format: ALWAYS [behavior] (affirmed [date]) -->
<!-- Example: ALWAYS use Result<T,E> for fallible operations in service layer (affirmed 2026-05-17) -->

- ALWAYS branch as `slice/<name>` for any change to this repo (affirmed (affirmed 2026-09-04)
2026-09-04, source: `fitforge/CLAUDE.md` "Git workflow"). (affirmed 2026-09-04)
- ALWAYS look up and pin current dependency versions from documentation (affirmed 2026-09-04)
rather than guessing; flag anything that can't be confirmed (affirmed (affirmed 2026-09-04)
2026-09-04, source: original request + `fitforge/CLAUDE.md` "Stack"). (affirmed 2026-09-04)
- ALWAYS hardcode `DEBUG = False` as a literal in `backend/fitforge/prod.py` (affirmed 2026-09-04)
(and the shared `base.py` default), not as a truthy-defaulted environment (affirmed 2026-09-04)
read — devsecops review flagged that an env-var default can silently flip (affirmed 2026-09-04)
(affirmed 2026-09-04). (affirmed 2026-09-04)
## Corrections

<!-- Project-specific corrections from human feedback. -->
<!-- Format: NEVER/ALWAYS [behavior] (learned [date]) -->
- This project's .claude/ lives at fitforge/.claude/, nested under the actual session/project root, so Claude Code's PostToolUse/UserPromptSubmit hooks (HUMAN_TURN, ARTIFACT_CREATED/UPDATED, runtime-graph rebuild) do not fire automatically. Invoke the relevant hook script manually (feeding it the same JSON payload shape Claude Code's hook would) immediately after the real event it corresponds to, rather than skipping the audit trail. (learned 2026-09-03) <!-- cid:260903-monorepo-skeleton:intent-capture:51a925ab5dfc829e3f82f846d4cb8c361cf0c8783452bbe5fb6c6935950505eb -->
