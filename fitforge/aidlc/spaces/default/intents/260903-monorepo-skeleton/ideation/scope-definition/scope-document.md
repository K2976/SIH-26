# Scope Document — FitForge Monorepo Skeleton

## In Scope

- `backend/`: Django 5 + DRF project, split settings (base/dev/prod), `dj-database-url`,
  DEBUG off by default, simplejwt, drf-spectacular, ruff, pytest stack
  (pytest, pytest-django, factory_boy, hypothesis), all URLs under `/api/v1/`,
  empty apps: accounts, reference, diary, engine, coach, api. [desc]
- `android/`: Kotlin (JDK 17) Gradle KTS project with a version catalog,
  minSdk 26, Jetpack Compose + Compose BOM, Material 3, Hilt, Room + KSP,
  WorkManager, Retrofit + OkHttp + kotlinx.serialization, DataStore,
  kotlinx-datetime, detekt, package `com.fitforge.app`, empty feature
  packages per CLAUDE.md layout. [desc]
- `docs/` directory. [desc]
- `.github/workflows/ci.yml` running ruff, pytest, detekt,
  `gradle assembleDebug`, and gitleaks. [desc]
- Pre-commit config running ruff and gitleaks. [desc]
- `.env.example` with placeholder-only values. [desc]
- `.gitignore` covering `.aidlc/`, local settings, build outputs, and
  database files. [desc]
- Installing the Android SDK (cmdline-tools + one current platform +
  build-tools) and a JDK 17 toolchain on this machine, so
  `./gradlew assembleDebug` actually succeeds here. [artifact:intent-statement]
- A gitignored `backend/.env` holding the real local-dev `DATABASE_URL`
  (the Neon Postgres connection string), and an ephemeral Postgres service
  container in CI. [artifact:intent-statement]

## Out of Scope (explicitly)

- Any business logic or Django models beyond empty app skeletons — no
  `UserState`, no engine calculations, no diary/coach logic. [desc]
- Android Studio installation (the user will do this separately,
  out of scope here). [artifact:intent-statement]
- Any deployment, environment provisioning, or observability setup —
  this workflow's scope (`monorepo-skeleton`) SKIPs the Operation phase
  entirely. [scope]
- Any product/UX design work (mockups, user stories, domain design) —
  this is scaffolding on an empty repo with no product surface yet.
  [Q2, this stage]

## Minimum Viable Scope

Everything listed under "In Scope" is Must Have — the user confirmed
nothing is deferred to a later pass. [Q2, this stage]

## Sequencing

Backend and Android are independent scaffolds with no runtime dependency
on each other at this stage; code-generation builds both with no strict
order requirement. [Q1, this stage]

## Value Stream

```
Idea: "no repo skeleton exists yet"
  -> Code Generation: backend + android + docs + CI scaffolded
  -> Build & Test: pytest, gradlew assembleDebug, ruff, detekt all pass
  -> CI Pipeline: same checks + gitleaks green on a pushed branch
  -> Outcome: a working foundation every future feature slice builds on
```

## Assumptions & Open Questions

None.
