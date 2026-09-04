# FitForge — Project Guide

Version 1.0. This is the single source of truth for what FitForge is, how it is
built, and the rules that govern every decision in it. Read it before writing
code. When this document and the code disagree, fix one of them immediately.

---

## 1. What FitForge is

An Android fitness coaching app in which training, nutrition, and menstrual
cycle data feed one adaptive engine.

Most fitness apps track these three things in separate silos. A workout log
does not know what you ate. A calorie counter does not know you trained legs
this morning. A cycle tracker knows nothing about either.

**FitForge computes them against each other.** Train harder today and today's
food target moves. Miss a session and the week rebalances. Log low energy and
today's intensity ceiling drops. Every feature reads the same state object,
and that state is derived from everything the user has logged.

That interconnection is the product. Everything else is an input to it or an
output from it.

---

## 2. The seven rules

These are non-negotiable. Any code, feature, or pull request that breaks one
is wrong regardless of how well it works.

1. **The LLM never computes a number.** It phrases decisions and calls
   functions. Every calorie, target, score, volume, and date is computed in
   Python from stored data.
2. **No response contains a value the code did not derive from stored data.**
   If it cannot be computed, the field does not exist in the response. No
   placeholder numbers, no random values, no "reasonable defaults" presented
   as measurements.
3. **Budgets, not rules.** Behaviour emerges from a small number of tracked
   quantities. There is no if-else tree of real-life situations.
4. **Floors are absolute.** No computed value may push below a safety floor,
   whatever the budget math says.
5. **Features read state, never each other.** One contract: `UserState`. No
   feature queries another feature's tables.
6. **The device adds and subtracts; only the server decides.** Sums of local
   rows on-device are fine. Any threshold, clamp, or choice happens in Python.
7. **Missing is not zero.** An unlogged day is `null` and is marked as
   partial. Treating absence as zero produces confidently wrong advice.

Rule 2 deserves emphasis. It is easy, under time pressure, to return a
plausible-looking number so a screen is not empty. Do not. An empty state with
an explanation is correct; a fabricated number is a bug that will not be
caught until someone asks where it came from.

---

## 3. Architecture

### Layers

```
  Android                                 Django
  ───────                                 ──────
  Compose UI · camera · chat
        │
        ▼
  Room (local writes, offline-first)
        │
        │  WorkManager sync
        ▼
  ──────────────────────────────────►  Diary (typed tables)
                                              │
                                              ▼
                                        Calculator (derived values)
                                              │
                                              ▼
                                        Engine (budgets + floors)
                                              │
                                              ▼
                                        LLM layer (phrasing + tools)
                                              │
  UserState  ◄────────────────────────────────┘
        │
        ▼
  Compose UI (+ local preview arithmetic)
```

Read it as an hourglass. Wide at the top — four ways to put data in. Narrow
in the middle — one diary, one calculator, three budgets, one state object.
Wide at the bottom — several things reading that state. New features attach
at the top or bottom. The middle rarely changes.

### Offline-first

Gyms have poor connectivity. If logging fails there, users stop logging.

- Writes land in Room immediately. The UI reads Room, not the network.
- Every row carries a **client-generated UUID** and a `sync_status`, so
  retries cannot create duplicates.
- WorkManager pushes pending rows when connectivity returns.
- The server recomputes and returns a fresh `UserState`, which **replaces**
  the device's cached copy wholesale. No merge, no conflict resolution.

### What the device may compute

Sums over local rows only: today's consumed kcal, today's estimated burn from
MET values, current session volume, rep counts. Displayed as preview,
overwritten on sync.

**Never on device:** maintenance calories, budgets, clamps, plan deltas,
adherence, weight trend, cycle phase, fitness age, pattern learning.

---

## 4. Repository structure

Monorepo.

```
fitforge/
├── CLAUDE.md                  # agent context — read first
├── README.md
├── docs/
│   ├── PROJECT_GUIDE.md       # this file
│   ├── API.md                 # generated from drf-spectacular
│   └── DECISIONS.md           # append-only decision log
├── aidlc-docs/                # AI-DLC generated artifacts (committed)
├── .github/workflows/ci.yml
├── .pre-commit-config.yaml
│
├── backend/
│   ├── manage.py
│   ├── pyproject.toml
│   ├── fitforge/              # Django project
│   │   ├── settings/
│   │   │   ├── base.py
│   │   │   ├── dev.py
│   │   │   └── prod.py
│   │   └── urls.py
│   └── apps/
│       ├── accounts/          # user, profile, goal
│       ├── reference/         # exercise library, food table, sync
│       ├── diary/             # workout, meal, weight, sleep, cycle logs
│       ├── engine/            # calculator + budgets + floors  ← the core
│       ├── coach/             # LLM tool calling
│       └── api/               # DRF routers, versioning, serializers
│
└── android/
    ├── settings.gradle.kts
    ├── gradle/libs.versions.toml
    └── app/src/main/java/com/fitforge/app/
        ├── core/              # DI, network, database, common UI
        ├── data/
        │   ├── local/         # Room entities, DAOs
        │   ├── remote/        # Retrofit services, DTOs
        │   └── repository/
        ├── domain/            # models, use cases
        ├── sync/              # WorkManager workers
        ├── pose/              # MediaPipe, calibration, rep state machine
        └── feature/
            ├── onboarding/
            ├── logbook/
            ├── repcounter/
            ├── food/
            ├── cycle/
            ├── dashboard/
            └── coach/
```

`apps/engine/` is the most important directory in the repository. It contains
pure functions with no I/O. Protect that property.

---

## 5. Data model

### Identity and intent

**`UserProfile`** — `user`, `date_of_birth`, `sex_at_birth`, `height_cm`,
`waist_cm`, `resting_hr`, `timezone`, `unit_preference` (metric | imperial),
`tracks_cycle`, `declined_fields[]`, `onboarding_completed_at`

Every optional field has three states: **known, not asked, declined.** A null
column plus membership in `declined_fields` distinguishes them. This matters
because "she skipped it" and "we never asked" produce different UI and
different confidence in derived values.

`waist_cm` and `resting_hr` exist for the fitness age model.

**`Goal`** — `user`, `energy_direction`, `training_focus`,
`target_rate_kg_per_week`, `auto_adjust`, `started_at`, `ended_at`

Goals are **two independent axes**:

- `energy_direction` ∈ {cut, maintain, gain, not_tracking}
- `training_focus` ∈ {strength, hypertrophy, endurance, general}

Recomp is not an option. It is `maintain` + `hypertrophy` + a protein floor,
and falls out of the two axes for free. `not_tracking` exists for users who
will not log food — the energy budget switches off entirely while the training
budget keeps working.

Goals are **versioned rows**, not editable fields. Changing a goal closes the
old row and opens a new one. Goal changes **prorate, never reset** — a reset
would let a bad week be erased from a dropdown.

### Reference data

**`Exercise`** — `slug` (natural key), `name`, `primary_muscles[]`,
`secondary_muscles[]`, `equipment`, `movement_pattern`, `instructions`,
`form_cues[]`, `met_value` (required), `is_bodyweight`, `pose_config`
(nullable), `camera_position` (nullable), `is_active`

Feeds the logbook picker, chat education, and the rep counter.
`met_value` is required — strength-session burn cannot be estimated without it.

**`FoodItem`** — `slug`, `name`, `kcal_per_100g`, `protein_g`, `carbs_g`,
`fat_g`, `default_portion_g`, `portion_label`, `source`, `is_active`

`default_portion_g` and `portion_label` ("1 katori", "2 rotis") exist because
portion size is most of the error in food logging.

Reference rows use **stable slugs** as the natural key, never autoincrement
IDs, and are **never hard-deleted** — set `is_active = false`. User history
points at these rows.

### The diary

All diary tables share: `user`, `client_uuid`, `occurred_at`, `source`,
`created_at`, `supersedes` (nullable self-FK).

Rows are never destructively edited. A correction is a new row that supersedes
an old one.

- **`PlannedSession`** — `date`, `source` (generated | user_created), `focus`,
  `planned_volume`, `status` (pending | completed | skipped | moved)
- **`WorkoutSession`** — `planned_session` (nullable), `duration_minutes`,
  `source` (camera | manual), `notes`
- **`SetLog`** — `session`, `exercise`, `set_index`, `reps`, `weight_kg`,
  `rpe`, `is_warmup`, `rom_achieved` (nullable), `tempo_ms` (nullable)
- **`MealLog`** / **`MealItem`** — macros snapshotted at log time
- **`WeightLog`**, **`SleepLog`** (bedtime, wake, quality 1–5)
- **`CycleLog`** — `date`, `event`, `flow`, `symptoms[]`, `energy_rating`

**One definition of a workout.** Camera and manual entry write the same rows,
differing only by `source`. Two sessions in one day are two sessions — days
aggregate sessions, no merge logic.

**Macro snapshots.** `MealItem` copies the food's macros at log time. If the
food table is corrected six months later, last April's calories must not
silently change.

### Plan ownership

One path, not two modes. Every `PlannedSession` carries
`source: generated | user_created`. The app always generates a plan; the user
may edit, replace, reorder, or delete any session, and edits stick. A user who
overrides everything ends up with all `user_created` rows — no mode flag
required.

A single `auto_adjust` setting decides whether engine deltas apply
automatically or appear as suggestions.

### Derived cache

**`DailyState`** — one row per user per day, holding §6's output. Recomputed on
write and nightly. Pure cache; always reproducible from the diary.

### Day boundary

Store UTC. Resolve to local day on read. **The day rolls over at 04:00 local,
not midnight.** A 23:40 session belongs to that day; a 01:00 session belongs to
the previous one. Store the UTC offset alongside each event so history never
re-buckets if the user changes timezone.

Use `kotlinx-datetime` on Android and Python's `zoneinfo` on the server. Do not
hand-roll date arithmetic.

---

## 6. `UserState` — the contract

The single object every feature reads. Serialized to the device on sync.

```
as_of

profile:   age, sex_at_birth, height_cm, unit_preference,
           tracks_cycle, timezone

goal:      energy_direction, training_focus,
           target_rate_kg_per_week, auto_adjust

energy:    maintenance_kcal, maintenance_source (equation | blended),
           maintenance_confidence, target_kcal_today,
           consumed_kcal_today, burned_kcal_today,
           week_balance_kcal, data_sufficiency

training:  week_volume_planned, week_volume_completed,
           volume_by_muscle_group, adherence_7d,
           readiness_ceiling_today, days_since_rest

body:      latest_weight_kg, weight_trend_kg_per_week,
           trend_confidence, fitness_age, fitness_age_inputs

cycle:     enabled, tracking_mode, phase, cycle_day, confidence,
           days_until_predicted_period, learned_pattern,
           energy_availability_flag

flags:     missing_food_days_7d, missing_weight_days_14d,
           goal_recently_changed, insufficient_data_for_adaptation
```

**Weight trend is a regression** over recent weigh-ins, not last-minus-first.
A 2 kg overnight water swing must not move it.

**`cycle.phase` is `null` when unknown** — never a default middle value.

**Progressive disclosure.** Every derived value ships with a confidence or
source marker. The UI shows what is computable from day one and grows more
precise. Nothing is gated behind a waiting period.

---

## 7. The engine

Lives in `backend/apps/engine/`. Pure functions, no I/O, no network, no ORM
calls inside the budget functions themselves — data is passed in.

### Budget 1 — weekly energy balance

Two stages, **blended, not switched**:

- **Equation.** Mifflin–St Jeor plus an activity factor. Live from the first
  session, so users see real targets on day one.
- **Measured.** Solve backwards from logged intake against the weight trend
  over a rolling 14-day window. Requires a minimum count of logged food days
  and of weigh-ins spread across the window. Below threshold, stay on the
  equation and say so in the UI. Above it, weight measured against equation by
  confidence so the number never jumps overnight.

Target rate is clamped at input with the reason shown. The engine cannot emit
a plan below the calorie floor regardless of what the goal demands.

### Budget 2 — weekly training volume

A skipped session is a volume shortfall. Redistribution across remaining days
is capped by Budget 3 and the max-jump floor. If the shortfall cannot be
recovered safely, it is **dropped, not crammed**.

### Budget 3 — daily readiness ceiling

**Inputs:** recent load, days since rest, sleep when present, and
**self-reported symptoms and energy rating**.

**Cycle phase is not an input.** See §10 for why.

### Floors — never overridden

- minimum daily calories
- minimum daily protein
- maximum single-day volume increase versus trailing average
- maximum single-day calorie adjustment
- **damping factor** — only a fraction of any drift carries forward, never all

Damping is a safety feature, not a tuning detail. Full compensation creates an
overeat → restrict → overeat loop, and the users most at risk are exactly the
ones this app serves.

### Output

```python
PlanDelta(
  changes    = [...],
  reason     = "one missed session redistributed; weekly volume preserved",
  clamped_by = ["max_daily_volume_jump"],
)
```

Every delta names why it fired and what limited it. This is what the coach
narrates and what an engineer reads when debugging.

---

## 8. Features

### v1 — core loop

1. Auth and onboarding — profile, two-axis goal, three-state fields
2. Exercise library — muscles, equipment, cues, MET values, pose configs
3. Workout logbook — routines, sets, reps, weight, rest timer, history, PRs
4. The engine — three budgets, floors, damping, `PlanDelta`
5. Dashboard — renders `UserState`, nothing more
6. Offline sync — Room, client UUIDs, WorkManager

### v1 — differentiators

7. Camera rep counter — constrained exercise set, on-device pose
8. Food logging — three-layer pipeline (§9)
9. Cycle module — symptoms drive readiness, pattern learning (§10)
10. Coach chat — tool calling, preview-then-confirm on writes

### v1 — cheap additions, build last

11. Music — `(workout_focus, cycle_phase) → genre → curated YouTube playlist`.
    Deep link out. No OAuth, no API, no quota.
12. Fitness age — a published non-exercise VO2max estimation model,
    implemented exactly, cited in the UI, inputs shown. Never tuned, never
    near the LLM.

### Deferred — designed for, not built

Leaderboards and challenges, social features, Health Connect integration,
grocery auto-ordering, hardware, server-side video analysis.

Leaderboards specifically need anti-cheat before they ship: browser or device
rep counts are forgeable, so server-side plausibility limits (reps per minute,
session duration, daily totals) and cold-start-friendly challenge design are
prerequisites, not follow-ups.

---

## 9. Food logging — three layers

**Label → portion → nutrition.**

1. **Label.** A model classifies the dish from a photo. The user can correct
   the label; corrections are stored and are training data.
2. **Portion.** The user confirms quantity, defaulted from the food's
   `default_portion_g` and `portion_label`. A label gives kcal per 100g; it
   says nothing about how much is on the plate, and portion is most of the
   error. **Never infer portion silently.**
3. **Nutrition.** Look up macros in the food table. Deterministic, auditable,
   correctable.

Text search and barcode entry skip layer 1 and go straight to portion.

### Food data

- A hand-seeded table of the most common regional dishes, sourced from a
  published food composition reference. This is unglamorous, takes days, and
  gates the entire nutrition feature. Do it early.
- Open Food Facts for packaged and barcoded items.
- A hosted vision model as the v1 classifier, with the option to replace it
  later with a trained classifier.

Photos are sent for recognition and then **discarded**. Macros are kept, the
image is not. No object storage in v1, and it is a real privacy property.

---

## 10. Cycle module

### Inputs

- Period start, end, flow, spotting
- Daily symptoms and an energy rating

### Two paths, and they are different

**Path A — phase.** Derived from period-start history. Displayed, used for
forecasting and pattern analysis. **It does not change training load.**

**Path B — symptoms.** Self-reported energy and symptoms feed the readiness
ceiling directly. This is the path that acts.

**Why.** The best available evidence on menstrual cycle phase and exercise
performance shows a trivial average effect with low-quality, inconsistent
findings, and the literature explicitly recommends a personalised approach
based on the individual's own response rather than general phase-based
guidelines. A fixed phase-to-load coefficient table would therefore invent
precision the science does not support — a violation of rule 2 wearing a
scientific costume.

**Pattern learning is the differentiator.** After several cycles, compare
*this user's* logged energy, symptoms, adherence, and completed volume across
cycle days. If her own data shows a consistent effect, surface it with sample
size and confidence attached. From her data, never from a textbook, and never
stated as causation.

### Energy availability check

If tracking mode is natural cycling, a period is significantly overdue against
her own history, and she has been in a sustained deficit with high training
load, the engine **stops escalating the deficit** and surfaces a suggestion to
speak to a doctor.

It does not diagnose, name a condition, or produce a score. It notices a
pattern in her own data and steps back. Build this. It is the most important
safety feature in the product.

### Tracking modes

`natural` · `hormonal_contraception` · `pregnant` · `postpartum` ·
`perimenopause` · `not_cycling` · `unknown`

Not edge cases — an explicit field. Hormonal contraception makes derived phase
meaningless, so the module still logs symptoms and learns patterns but shows
no phase. Without this field, phase calculation outputs confident nonsense for
a large share of real users.

### Opt-in and privacy

- Cycle tracking is enabled by a **direct question**, never inferred from sex.
  Menstruation and gender are different variables, and contraception,
  pregnancy, and menopause each break the inference.
- **Cycle data never leaves the server.** Not to any LLM provider, not in a
  prompt, not as an aggregate. If the coach must reference it, it receives a
  derived label and only with explicit opt-in, off by default.
- Full export and full delete are requirements.
- Discreet mode hides the module from the home screen. Some users share a
  phone.

### The honest risk

Everything above depends on daily symptom logging. If she does not log,
readiness falls back to load and rest, and the module becomes a passive period
tracker. **The symptom log is a UX problem to solve first, not a feature to
build in week six.** One tap from home, three seconds, skippable, no guilt.

---

## 11. On-device pose

MediaPipe Tasks Vision runs the pose model locally. The model file is bundled
in `assets/` — an offline-first app does not download its model on first
launch.

### What is measurable, and what is not

Landmark detection is fast and accurate. **Form scoring is not**, and
pretending otherwise reintroduces fabricated numbers.

Landmarks carry depth ambiguity even with estimated world coordinates. Rep
counting and range-of-motion are robust — joint angles crossing thresholds is
a reliable signal. Spinal position, knee valgus, and bar path from one camera
at an arbitrary angle are not.

Therefore:

- A **fixed set of supported exercises**, each declaring a required
  `camera_position`.
- Score only what is measurable: depth reached, lockout achieved, tempo,
  left/right symmetry.
- Everything else reports **"can't assess from this angle"** — not scored, not
  estimated.
- **There is no universal form score field.** It does not exist in `SetLog`.

### Accuracy, ranked by impact

1. **Benchmark the model variants** (lite / full / heavy) on target devices.
   A one-line change and usually the largest single gain available.
2. **Filter the signal.** Raw landmarks jitter. Smoothing on joint angles
   eliminates most false reps. Worth more than any model swap.
3. **Calibrate properly.** A thorough one-time setup per exercise — phone
   position, distance, joint visibility, a slow demo rep — cached against that
   exercise. Then a fast visibility re-check on later sessions. Thorough once,
   quick forever. Reject the session if required joints are not visible.
4. **Use world landmarks** for angle math where the movement has depth.
5. **Then**, later, a trained sequence classifier over the smoothed landmark
   stream, exported to TFLite and run on-device. Training data comes from the
   app's own labelled sessions.

---

## 12. Coach chat — tool calling

Not retrieval. The model selects a function and fills arguments; your code
executes it.

**Read tools:** `get_state`, `get_exercise_info`, `get_history`,
`explain_last_decision`

**Write tools:** `log_workout`, `log_meal`, `log_weight`,
`reschedule_session`, `swap_exercise`, `update_goal`

1. Every tool maps to a function the UI already calls. No chat-only paths.
2. Write tools return a **preview**. The user confirms before commit. If the
   model misreads "move leg day" as "delete leg day," the preview catches it.
3. Tools return values and reasons. The model narrates; it never recalculates.
4. Cycle data is not sent to the LLM provider.

Exercise education needs no retrieval — pass the `Exercise` row into context
and the model answers follow-ups grounded in it. A document retrieval layer is
a later phase, and only for knowledge the library does not hold.

---

## 13. Sync and reference data

### Diary sync

- Client UUID on every row; the server deduplicates on it.
- Server state always wins. The device never merges.
- Pending rows survive app death — Room, not memory.
- Sync is idempotent. Retry is always safe.
- A server-side validator rejects `occurred_at` more than a day in the future
  or implausibly far in the past.

### Reference data sync

- `GET /api/v1/reference/manifest` → `{reference_version, table_counts}`
- `GET /api/v1/reference/exercises?since=N` → rows changed since version N,
  including deactivation markers
- The client stores its local version in DataStore. App start and a daily
  worker check the manifest and pull deltas.
- A **seed bundle ships in `assets/`** as JSON, generated at build time from
  the server, so a fresh install works fully offline before its first sync.
- Reference rows are keyed by stable slug and never hard-deleted.
- Name and instruction fields are locale-keyed from the start with English
  fallback, so adding a language later costs nothing.

### App version gate

`GET /api/v1/app-config` returns `min_supported_version`. The app checks on
launch and blocks with an update prompt if it is below the minimum. **This
must ship in v1** — you cannot force-update users who install a version that
never had the check.

All endpoints live under `/api/v1/`. Schema changes are additive. Installed
apps will hit old contracts for months.

---

## 14. Tech stack

### Android

Kotlin (JDK 17) · Jetpack Compose · Material 3 · Navigation Compose · Hilt ·
Room + KSP · WorkManager · Retrofit + OkHttp + kotlinx.serialization ·
DataStore · CameraX · MediaPipe Tasks Vision · TensorFlow Lite (later) ·
Vico (charts) · kotlinx-datetime · Coil · Gradle KTS with a version catalog ·
detekt · JUnit, Turbine, MockWebServer, in-memory Room

minSdk 26. SQLite FTS via Room for exercise and food search.

### Backend

Python 3.12 · Django 5 · Django REST Framework · PostgreSQL ·
djangorestframework-simplejwt · `psycopg[binary]` · dj-database-url ·
django-cors-headers · drf-spectacular · gunicorn · whitenoise · Pillow ·
ruff · pytest + pytest-django + factory_boy + hypothesis · python-dotenv

Nightly recompute is a Django management command on a cron schedule. **Not
Celery** — that means Redis, a worker, and a broker to debug, for one daily
job. Add it when there is real async work.

**Explicitly not used:** LangChain, LangGraph, any RL framework, httpx, any
vector store.

### Services

PostgreSQL host (managed) · API host · GitHub Actions CI · Crashlytics
(Android) · Sentry (backend) · Firebase Remote Config (feature flags) ·
a hosted LLM for coach dialogue and tool calling · a hosted vision model for
food recognition · YouTube deep links for music, no API and no OAuth

No object storage in v1.

### Data sources

A published food composition reference for the regional food table ·
Open Food Facts for packaged goods · a published compendium of physical
activities for MET values · a published non-exercise VO2max model for fitness
age · a public exercise dataset as the library seed

Pin every dependency version from current documentation at setup time. Do not
copy version numbers from documents.

---

## 15. Working conventions

### Ownership

**You own the vertical slice you build, top to bottom.** One person takes
"food logging" through Django models, serializers, views, Android data layer,
and UI. Nobody takes "all the Django models" or "all the screens."

This is what keeps a monorepo with rotating contributors clean. Layer-based
splits mean every feature touches every person's files. Slice-based splits
mean merges are rare and reviews are meaningful.

### Branches and commits

- `main` is always green and always installable.
- Branch per slice: `slice/food-logging`, `slice/cycle-module`.
- Conventional commits: `feat(food): add portion confirmation step`.
- Every PR states which slice it belongs to and which rules from §2 it
  touched.

### The API contract

drf-spectacular generates the OpenAPI schema from DRF views. The Retrofit
client is generated from that schema. **Do not hand-write DTOs that mirror
serializers** — the contract will drift, and with rotating ownership it will
drift silently.

### Adding a feature

1. Does it read state or write to the diary? Those are the only two options.
2. Reading? Add it to `UserState` if the field does not exist. Never query
   another feature's tables.
3. Writing? New diary table, same shared columns, same sync path.
4. Changing how decisions are made? That is a budget or a floor. It touches
   the engine and requires re-testing every existing behaviour.

Most features are (2). That is the point of the design.

### Security

Secrets only in environment variables — never in source, never in docs.
gitleaks in pre-commit. `.env.example` holds placeholders only. `DEBUG`
defaults to off. Database files are never tracked. No third-party OAuth login
in v1 — email and password only, because a login without server-side token
verification is an authentication bypass, not a feature.

---

## 16. Testing

Not coverage targets. These specific things:

**Engine — property-based tests.** Generate random event histories, assert
that floors always held, that no target ever went below the minimum, that
damping was applied, and that every `PlanDelta` carries a reason. This is the
test suite that proves the product is safe.

**Pose — pure function tests.** Feed synthetic landmark sequences to the angle
calculation and rep state machine. Assert rep counts, rejected reps, and
range-of-motion flags.

**Sync — idempotency tests.** Post the same client UUID repeatedly. Assert one
row. Assert retry after partial failure is safe.

**API — smoke tests.** Auth, a diary write, a `UserState` read, a reference
delta.

CI runs ruff, pytest, detekt, `assembleDebug`, and gitleaks on every PR.

---

## 17. Decisions still open

Record answers in `docs/DECISIONS.md` as they are made.

1. Exercise library seed source and MET mapping coverage
2. Exact sufficiency thresholds for measured maintenance
3. Floor values and their justification
4. Damping fraction
5. Which exercises get `pose_config` in v1, and their camera positions
6. Deletion semantics — superseding versus account deletion
7. Behaviour after a long offline gap: replay day by day, or compute current
   state only
8. Per-user LLM call quotas and graceful degradation at the cap
9. Play Store data safety declarations and privacy policy hosting
10. Analytics events — at minimum, whether the symptom log is being used
