<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-03T21:24:17Z — treated the user-pasted Neon Postgres connection string as credential material: it will land only in a gitignored `backend/.env`, never in any AIDLC artifact, questions file, or audit log, even though the protocol otherwise requires verbatim user-input logging.

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-03T21:24:17Z — this project's `.claude/` lives at `fitforge/.claude/`, one level below the actual session/project root (`SIH-26/`), so none of the PostToolUse/UserPromptSubmit hooks that normally mint HUMAN_TURN and ARTIFACT_CREATED/UPDATED events fire automatically. Manually invoked `aidlc-record-human-turn.ts` after each genuine AskUserQuestion response and `aidlc-write-audit-log.ts` after each artifact Write/Edit, feeding them the same payload shape Claude Code's PostToolUse would supply, so the audit trail stays accurate to what actually happened rather than fabricating anything.

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-03T21:24:17Z — user chose real Postgres for local dev/test (not SQLite) since a hosted Neon instance was already available, and a Postgres service container for CI (not the shared Neon dev DB) to keep CI isolated and secret-free.

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-03T21:24:17Z — the exact Android compileSdk/targetSdk (current stable API level) and every backend/Android dependency version are deferred to code-generation, per the user's explicit "look up versions, don't guess" instruction.
