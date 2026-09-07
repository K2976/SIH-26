# Discovered Rules — FitForge

## Mandated

- ALWAYS branch as `slice/<name>` for any change to this repo.
- ALWAYS isolate social functionality inside `backend/apps/social/` on backend and `com.fitforge.app.feature.social` on Android.
- ALWAYS use separate public `social_profile.username` for public identities, preserving `auth_user.username == auth_user.email` for existing website compatibility.
- ALWAYS reuse existing `diary_workoutsession` records via nullable foreign key rather than duplicating workout tables.

## Forbidden

- NEVER push directly to `master`/`main`, or merge into it without explicit human action.
- NEVER write secrets (e.g. database connection strings or tokens) into code, artifacts, questions files, or logs.
- NEVER modify or remove existing tables, columns, or types in `accounts_*`, `diary_*`, `reference_*`, or `engine_*`.
- NEVER alter authentication behavior in a way that breaks existing website login or signup.
