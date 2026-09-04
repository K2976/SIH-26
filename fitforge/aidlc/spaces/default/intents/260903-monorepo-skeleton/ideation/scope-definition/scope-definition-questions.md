# Scope Definition & Prioritization — Questions

## Sources

- [desc] Initial description: "Set up the FitForge monorepo skeleton: backend/ (Django 5 + DRF project with split settings base/dev/prod, PostgreSQL via dj-database-url, DEBUG off by default, simplejwt, drf-spectacular, ruff, pytest+pytest-django+factory_boy+hypothesis, all URLs under /api/v1/, empty apps per CLAUDE.md layout: accounts, reference, diary, engine, coach, api), android/ (Kotlin JDK 17 Gradle KTS project with version catalog, minSdk 26, Jetpack Compose + Compose BOM, Material 3, Hilt, Room+KSP, WorkManager, Retrofit+OkHttp+kotlinx.serialization, DataStore, kotlinx-datetime, detekt, package com.fitforge.app, empty feature packages per CLAUDE.md layout: core, data/{local,remote,repository}, domain, sync, pose, feature/{onboarding,logbook,repcounter,food,cycle,dashboard,coach}), docs/, and .github/workflows/ci.yml (ruff, pytest, detekt, gradle assembleDebug, gitleaks) plus a pre-commit config (ruff, gitleaks). Add .env.example with placeholder-only values and a .gitignore covering .aidlc/, local settings, build outputs, and database files. This is scaffolding only — no business logic, no models beyond app skeletons. Every dependency version must be looked up from current documentation and pinned; do not guess versions, flag anything that can't be confirmed. Done when: pytest runs clean with zero tests, ./gradlew assembleDebug succeeds, ruff and detekt pass, and CI is green on a pushed branch."
- [artifact:intent-statement] `aidlc/spaces/default/intents/260903-monorepo-skeleton/ideation/intent-capture/intent-statement.md`

## Q1. Backend and Android are independent scaffolds with no runtime dependency on each other at this stage. Is there a build/commit sequencing preference, or should code-generation build both without a strict order?

- A. No strict order — build both, sequence doesn't matter for a scaffold
- B. Backend first (it defines the API surface Android will eventually call)
- C. Android first
- Not applicable

[Answer]: A. No strict order — build both, sequence doesn't matter for a scaffold

## Q2. Is everything in the original request a "Must Have" for this piece of work (nothing to defer to a later pass)?

- A. Yes — everything listed (backend, android, docs/, CI, pre-commit, .env.example, .gitignore) is Must Have; nothing deferred
- B. No — some of it should be deferred (please specify)
- Not applicable

[Answer]: A. Yes — everything listed is Must Have; nothing deferred

X. Other (please specify)

## Consolidated Summary Confirmation

- Looks correct
- Request changes

[Answer]: Looks correct
