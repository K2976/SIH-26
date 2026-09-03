# CLAUDE.md — FitForge

Read this fully before writing any code in this repository.

Full detail lives in `docs/PROJECT_GUIDE.md`. This file is the operational
summary and the rules you must not break.

---

## What FitForge is

An Android fitness coaching app where training, nutrition, and menstrual cycle
data feed one adaptive engine. Other apps track these separately; FitForge
computes them against each other.

Train harder today and today's food target moves. Miss a session and the week
rebalances. Log low energy and today's intensity ceiling drops.

That interconnection is the product. Every feature is an input to it or an
output from it.

---

## Hard rules

Violating any of these makes the code wrong regardless of whether it works.

1. **The LLM never computes a number.** It phrases decisions and calls
   functions. Every calorie, target, score, volume, and date is computed in
   Python from stored data.
2. **Never return a value the code did not derive from stored data.** No
   placeholders, no random values, no plausible defaults presented as
   measurements. If it cannot be computed, omit the field and let the UI show
   an empty state with an explanation.
3. **Budgets, not rules.** Behaviour emerges from a few tracked quantities.
   Do not write an if-else tree of real-life situations.
4. **Floors are absolute.** No computed value may push below a safety floor.
5. **Features read `UserState`, never each other's tables.**
6. **The device adds and subtracts; only the server decides.** Sums of local
   rows on-device are fine. Any threshold, clamp, or choice happens in Python.
7. **Missing is not zero.** An unlogged day is `null` and marked partial.

If a task seems to require breaking one of these, stop and say so rather than
working around it.

---

## Architecture in one paragraph

Android writes to Room immediately (offline-first, gyms have bad signal).
WorkManager syncs pending rows to Django. Django stores them in typed diary
tables, a calculator derives values, an engine of three budgets plus absolute
floors produces a `PlanDelta` with a stated reason, and the whole thing is
serialized into one `UserState` object that replaces the device's cache. Every
feature reads `UserState`. Nothing reads anything else.

---

## Repository layout

```
fitforge/
├── CLAUDE.md
├── docs/PROJECT_GUIDE.md      # full spec — read when in doubt
├── docs/DECISIONS.md          # append-only decision log
├── aidlc-docs/                # AI-DLC artifacts (committed)
├── backend/
│   ├── fitforge/              # Django project + split settings
│   └── apps/
│       ├── accounts/          # user, profile, goal
│       ├── reference/         # exercise library, food table, sync
│       ├── diary/             # workout, meal, weight, sleep, cycle logs
│       ├── engine/            # calculator + budgets + floors  ← the core
│       ├── coach/             # LLM tool calling
│       └── api/               # DRF routers, versioning
└── android/app/src/main/java/com/fitforge/app/
    ├── core/                  # DI, network, database, common UI
    ├── data/{local,remote,repository}
    ├── domain/
    ├── sync/                  # WorkManager workers
    ├── pose/                  # MediaPipe, calibration, rep state machine
    └── feature/{onboarding,logbook,repcounter,food,cycle,dashboard,coach}
```

`apps/engine/` contains pure functions with no I/O. Data is passed in, results
are returned. Never add an ORM query, network call, or `datetime.now()` inside
a budget function. Protect this property — it is what makes the engine
testable.

---

## Stack

**Android:** Kotlin (JDK 17), Jetpack Compose, Material 3, Hilt, Room + KSP,
WorkManager, Retrofit + OkHttp + kotlinx.serialization, DataStore, CameraX,
MediaPipe Tasks Vision, Vico, kotlinx-datetime, Coil. minSdk 26. Gradle KTS
with a version catalog. detekt.

**Backend:** Python 3.12, Django 5, DRF, PostgreSQL, simplejwt,
drf-spectacular, gunicorn, whitenoise, Pillow. ruff. pytest + pytest-django +
factory_boy + hypothesis.

**Not used:** LangChain, LangGraph, RL frameworks, httpx, any vector store,
Celery, object storage.

Pin every version from current documentation at setup time. Do not invent
version numbers — if you are unsure of a version, say so rather than guessing.

---

## Conventions

**Vertical slices.** Contributors own a feature top to bottom — Django models
through Compose UI — not a layer. When building, complete one slice end to end
before starting another.

**Branches:** `slice/<name>`. **Commits:** conventional, e.g.
`feat(food): add portion confirmation step`. `main` stays green and
installable.

**API:** everything under `/api/v1/`. Schema changes are additive — installed
apps hit old contracts for months. The Retrofit client is generated from the
drf-spectacular schema; do not hand-write DTOs that mirror serializers.

**Diary rows** all carry `user`, `client_uuid`, `occurred_at`, `source`,
`created_at`, `supersedes`. Rows are never destructively edited — corrections
supersede.

**Reference rows** (exercises, foods) use stable slugs as the natural key and
are never hard-deleted. Set `is_active = false`.

**Dates:** store UTC with the offset, resolve to local on read, and the day
rolls over at **04:00 local**. Use kotlinx-datetime and Python's zoneinfo.
Never hand-roll date arithmetic.

**Units:** store metric, display per user preference (kg/lb, cm/ft).

**Strings:** all UI text in Compose string resources from day one, locale-keyed,
English fallback.

**Optional fields have three states:** known, not asked, declined. Null column
plus a `declined_fields` list. "Skipped it" and "never asked" are different.

---

## Git workflow

- **No direct pushes to `master`.** Every change goes on a `slice/<name>`
  branch. `master` only moves when the user manually merges an approved PR —
  never an auto-merge, never a push straight to `master`, by anyone,
  including Claude.
- **Commit regularly.** Small, logical, conventional commits as work
  progresses on a branch — this is a standing instruction for this repo, so
  commit without waiting to be asked each time. Never push or merge without
  being asked — those stay explicit, human-triggered actions.
- **No AI attribution in commits or PRs.** Do not add `Co-Authored-By:
  Claude`, `Claude-Session:`, or a "Generated with Claude Code" footer to
  commit messages or PR descriptions in this repo. Commits use the
  repository's configured git identity only.

---

## Things that are deliberately absent

Do not add these back:

- **No `form_score` field.** 2D landmarks cannot support a universal form
  score. Score only depth, lockout, tempo, and symmetry, and report
  "can't assess from this angle" otherwise.
- **No cycle-phase coefficient on training load.** Phase is displayed and
  used for forecasting and pattern learning. Readiness is driven by
  self-reported symptoms and energy. The evidence does not support
  phase-based load prescriptions.
- **No retrieval/RAG over user data.** User state is a database query.
  Retrieval cannot count and cannot detect absence.
- **No mode flag for plan ownership.** One path; sessions carry
  `source: generated | user_created`.
- **No guest mode.** Login required, one code path.
- **No third-party OAuth login in v1.** Email and password only.

---

## Testing

Write these, not coverage targets:

- **Engine:** property-based tests with hypothesis. Random event histories;
  assert floors held, no target below minimum, damping applied, every
  `PlanDelta` carries a reason.
- **Pose:** pure function tests over synthetic landmark sequences. Assert rep
  counts, rejected reps, range-of-motion flags.
- **Sync:** idempotency. Same `client_uuid` posted repeatedly yields one row.
- **API:** smoke tests for auth, a diary write, a `UserState` read.

CI runs ruff, pytest, detekt, `assembleDebug`, gitleaks.

---

## Security

Secrets only in environment variables — never in source, never in docs, never
in example files. gitleaks in pre-commit. `.env.example` holds placeholders.
`DEBUG` defaults to off. Database files are never tracked.

**Cycle data never leaves the server.** Not to any LLM provider, not in a
prompt, not as an aggregate.

Food photos are sent for recognition and discarded. Macros are stored, images
are not.

---

## Working with me

- Build one vertical slice at a time, end to end. Do not scaffold all models,
  then all views, then all screens.
- After each slice: it compiles, its tests pass, and it is usable in the app.
- Write code that can be explained in an interview. Clear over clever. Length
  is fine; unexplained magic is not.
- If a request conflicts with the hard rules above, say so and propose an
  alternative rather than quietly working around it.
- When you are uncertain about a library version, an API surface, or a
  published model's formula, say you are uncertain. Do not fabricate.

---

## AI-DLC

Run `/aidlc` to start or resume a workflow — the harness installed under
`.claude/` and `aidlc/` registers the command; there is no file to read
manually. `/aidlc --doctor` checks the setup, `/aidlc --status` shows
progress.

Artifacts land under `aidlc/spaces/default/intents/<record>/` and are
committed — only per-user cursors and machine-local runtime files are
gitignored (see `.gitignore`). At the `practices-discovery` stage's testing
posture step, affirm hypothesis-based property testing for the engine — it
enforces the engine testing described above.
