# Intent Backlog — FitForge Monorepo Skeleton

MoSCoW prioritization. This is a single-Bolt scaffolding task (per
scope-definition Q2, nothing deferred), so the backlog is short and
everything ships in one pass.

## Must Have

| Item | Description | Source |
|---|---|---|
| Backend scaffold | Django 5 + DRF project, split settings, simplejwt, drf-spectacular, ruff, pytest stack, `/api/v1/` mount, 6 empty apps | [desc] |
| Android scaffold | Kotlin/Compose Gradle KTS project, version catalog, minSdk 26, Hilt/Room/WorkManager/Retrofit/DataStore, detekt, `com.fitforge.app`, empty feature packages | [desc] |
| CI pipeline | `.github/workflows/ci.yml`: ruff, pytest, detekt, `gradle assembleDebug`, gitleaks | [desc] |
| Pre-commit config | ruff + gitleaks | [desc] |
| Dev/secrets hygiene | `.env.example`, `.gitignore` (`.aidlc/`, local settings, build outputs, DB files) | [desc] |
| Local database | `backend/.env` (gitignored) wired to the provided Neon Postgres connection string via `DATABASE_URL` | [artifact:intent-statement] |
| Android build toolchain | Android SDK (cmdline-tools + platform + build-tools) + JDK 17 installed on this machine | [artifact:intent-statement] |
| `docs/` directory | Present in the repo layout | [desc] |

## Should Have

None — everything the user asked for is Must Have (Q2).

## Could Have

None identified — out of scope for a pure scaffolding task.

## Won't Have (this time)

| Item | Reason |
|---|---|
| Business logic / real Django models | Explicitly out of scope: "scaffolding only, no business logic, no models beyond app skeletons" |
| Android Studio install | User will do this separately, on their own machine setup |
| Deployment / observability / incident response | Operation phase is out of scope for this workflow |

## Proto-Units (for reference — units-generation is SKIPped in this workflow)

Given the confirmed no-strict-order sequencing, this backlog resolves to
one Bolt with two independently buildable halves:

1. **Backend scaffold** — Django project + apps + settings + CI (Python half)
2. **Android scaffold** — Gradle project + modules + Compose setup (Kotlin half)

Both are built in the same code-generation pass; there is no dependency
edge between them at scaffold time.
