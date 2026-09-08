# Practices Discovery Evidence

## Sources Inspected

1. `fitforge/CLAUDE.md`: Hard rules, repository layout, conventions, testing targets, git workflow.
2. `backend/pyproject.toml`: Python 3.12 dependencies, pytest settings, ruff linting config.
3. `android/app/build.gradle.kts` & `android/gradle/libs.versions.toml`: Android SDK 34, Kotlin 1.9+, Compose BOM, detekt config.
4. `aidlc/spaces/default/memory/team.md`: Baseline standing practices from earlier slices.
5. Reverse engineering artifacts in `aidlc/spaces/default/codekb/fitforge/`: Architecture, technology stack, and component inventory.

## Participant Evidence & Positions

- **aidlc-quality-agent**: Confirmed test-after posture; highlighted hypothesis requirement for engine calculations and unit testing for new social endpoints.
- **aidlc-developer-agent**: Confirmed ruff + detekt style rules; verified modular separation of `apps/social/` and strict backward-compatibility requirement for `auth_user`.
- **aidlc-devsecops-agent**: Confirmed secrets protection, absence of breaking DDL migrations on Neon, and public username privacy isolation.

## Decisions

All five baseline areas reaffirmed with explicit consensus.
