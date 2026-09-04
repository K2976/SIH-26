# Intent Capture & Framing — Questions

## Sources

- [desc] Initial description: "Set up the FitForge monorepo skeleton: backend/ (Django 5 + DRF project with split settings base/dev/prod, PostgreSQL via dj-database-url, DEBUG off by default, simplejwt, drf-spectacular, ruff, pytest+pytest-django+factory_boy+hypothesis, all URLs under /api/v1/, empty apps per CLAUDE.md layout: accounts, reference, diary, engine, coach, api), android/ (Kotlin JDK 17 Gradle KTS project with version catalog, minSdk 26, Jetpack Compose + Compose BOM, Material 3, Hilt, Room+KSP, WorkManager, Retrofit+OkHttp+kotlinx.serialization, DataStore, kotlinx-datetime, detekt, package com.fitforge.app, empty feature packages per CLAUDE.md layout: core, data/{local,remote,repository}, domain, sync, pose, feature/{onboarding,logbook,repcounter,food,cycle,dashboard,coach}), docs/, and .github/workflows/ci.yml (ruff, pytest, detekt, gradle assembleDebug, gitleaks) plus a pre-commit config (ruff, gitleaks). Add .env.example with placeholder-only values and a .gitignore covering .aidlc/, local settings, build outputs, and database files. This is scaffolding only — no business logic, no models beyond app skeletons. Every dependency version must be looked up from current documentation and pinned; do not guess versions, flag anything that can't be confirmed. Done when: pytest runs clean with zero tests, ./gradlew assembleDebug succeeds, ruff and detekt pass, and CI is green on a pushed branch."
- [scope] Workflow-selected scope: `monorepo-skeleton`.
- [memory:M1] `aidlc/spaces/default/memory/org.md#Way of Working`: "We use trunk-based development. All work merges to main via short-lived feature branches (typically resolved within 1-2 days)."

## Q1. The workflow was started with the `monorepo-skeleton` scope (framing only — intent-capture, scope-definition, practices-discovery, then straight to code-generation/build-and-test/ci-pipeline, no design stages). Does that match the boundary you intend for this piece of work?

- A. Yes — this is scaffolding only, no business logic, matches the scope exactly
- B. No — I actually want feature/business-logic work included too
- Not applicable

[Answer]: A. Yes — this is scaffolding only, no business logic, matches the scope exactly

## Q2. This machine has no Android SDK installed at all (no `ANDROID_HOME`, no `~/Library/Android/sdk`) and no JDK 17 (only JDK 23/24 present). `./gradlew assembleDebug` is one of your stated "done when" criteria. Satisfying it requires installing the Android command-line tools + a platform + build-tools (roughly 1-2GB download) and accepting Google's SDK licenses on this machine, plus getting Gradle onto a JDK 17 toolchain. How should code-generation/build-and-test handle this?

- A. Install the Android SDK + accept licenses now, so `assembleDebug` actually runs and passes here
- B. Scaffold the Android project correctly but don't install the SDK — "done" for Android means the Gradle project is well-formed, not that it was actually built on this machine
- Not applicable

[Answer]: A. Install the Android SDK + accept licenses now, so `assembleDebug` actually runs and passes here.

## Q3. `pytest` needs to run clean with zero tests. Django's settings will use `dj-database-url` reading `DATABASE_URL` from the environment. There's no local Postgres server on this machine (no `psql`/`pg_config`). What should the dev/test default be?

- A. Default `DATABASE_URL` to a local SQLite file when unset (via `.env`/`.env.example`), so `pytest` runs without any database server; Postgres stays the documented production target
- B. Require a real Postgres connection even for local dev/test (I'll provide one, or CI should spin one up as a service container)
- Not applicable

[Answer]: B. Require a real Postgres connection even for local dev/test — the user supplied a hosted Postgres (Neon) connection string for local dev, to be stored only in `backend/.env` (gitignored) and referenced as `DATABASE_URL`; never written into any committed file, audit log, or artifact.

## Q4. `.github/workflows/ci.yml` needs a database for the Django job (migrations/checks) even with zero app tests. Should CI run against SQLite (matches Q3's local-dev default) or spin up a Postgres service container (closer to production, more setup)?

- A. SQLite in CI — simplest, matches local dev, sufficient for a scaffold with no real models yet
- B. Postgres service container in CI — closer to production parity from day one
- Not applicable

[Answer]: B. Postgres service container in CI — an ephemeral GitHub Actions `services:` container, not the hosted Neon dev database (keeps CI isolated, avoids depending on external network/secret for a scaffold-only pipeline).

X. Other (please specify)

## Consolidated Summary Confirmation

- Looks correct
- Request changes

[Answer]: Looks correct

## Assumption Confirmation

Assumptions recorded in `intent-statement.md` and `stakeholder-map.md`:
1. Local dev DB is a hosted (Neon) Postgres instance referenced only via
   `DATABASE_URL` in a gitignored `backend/.env`; CI uses an ephemeral
   Postgres service container. The connection string is not recorded in any
   artifact.
2. The Android SDK install authorized by Q2 is scoped to cmdline-tools + one
   platform + build-tools needed for `assembleDebug` — not a full Android
   Studio install.
3. No reporting cadence or additional stakeholders exist beyond the
   in-session approval gates — this is a solo-developer bootstrap task.

A. Accept assumptions
B. Convert to follow-up questions

[Answer]: B. Convert to follow-up questions

## Q5. For local dev/test, the connection string you pasted in chat will be stored only in `backend/.env` (gitignored, never committed, never written into any AIDLC artifact/audit log) and read via the `DATABASE_URL` environment variable. Is that the right handling?

- A. Yes — `.env` only, never committed or logged anywhere
- B. No — handle it differently (please specify)
- Not applicable

[Answer]: A. Yes — `.env` only, never committed or logged anywhere

## Q6. The Android SDK install (to satisfy `assembleDebug`) will be scoped to just what's needed: cmdline-tools, one platform (the current stable API level, looked up rather than guessed), and build-tools — not a full Android Studio install. OK?

- A. Yes — cmdline-tools + platform + build-tools only
- B. No — install Android Studio / something broader instead
- Not applicable

[Answer]: A. Yes — cmdline-tools + platform + build-tools only for now; the user will install Android Studio separately later on their own, out of scope for this workflow.

## Q7. This is a solo-developer bootstrap task with no other stakeholders and no reporting cadence beyond the approval gates in this workflow. Correct?

- A. Yes — solo developer, no other stakeholders/cadence
- B. No — there are other stakeholders or a cadence to capture
- Not applicable

[Answer]: A. Yes — solo developer, no other stakeholders/cadence

X. Other (please specify)

