# Decisions

## 2026-09-04 — CI workflow and pre-commit config live at the git repo root, not `fitforge/`

`fitforge/` is a subdirectory of the `K2976/SIH-26` GitHub repository, not a
repository of its own. GitHub Actions only discovers workflows under
`.github/workflows/` at the actual repo root, so `ci.yml` and
`.pre-commit-config.yaml` are placed there (one level above `fitforge/`)
rather than inside `fitforge/.github/`, with paths and `working-directory`
scoped into `fitforge/backend` and `fitforge/android`. Everything else
(code, docs, `.env.example`, app-level `.gitignore` rules) stays under
`fitforge/` per this repo's layout.

## 2026-09-04 — Local Android SDK not installed on this machine

`./gradlew assembleDebug` and `./gradlew detekt` are verified via CI
(`ubuntu-latest` runners ship a preinstalled, license-accepted Android SDK),
not locally. The user has stated they'll install Android Studio themselves
later for on-device testing; installing a second full SDK locally just to
prove one build would duplicate that setup for no lasting benefit. JDK 17
(Temurin) was installed locally since it's needed for any local Gradle
invocation regardless of SDK presence.

## 2026-09-04 — Calculator/UserState judgment calls (apps/engine/)

§6 and §7 leave several numbers and edge cases unspecified ("exact
sufficiency thresholds", "floor values" are explicitly open per §17). These
were decided to ship the calculator + `/api/v1/state/` slice; revisit if
product wants different numbers.

- **`week_balance_kcal` does not also subtract `burned_kcal_today`.**
  Maintenance is Mifflin-St Jeor **plus an activity factor**, so it already
  prices in general activity. Subtracting logged exercise burn on top would
  double-count it. `week_balance_kcal = Σconsumed − Σmaintenance` over the
  trailing 7 days; `burned_kcal_today` is a separate, informational field
  (the server-authoritative version of the same MET-based estimate the
  Android client previews live, per `WorkoutMetrics.kt`'s own docstring).
- **`week_volume_*` uses the calendar week (Mon-Sun); everything else
  "trailing N days" is a rolling window ending `as_of`.** Training block
  design conventionally resets on a calendar boundary; energy/adherence
  numbers don't.
- **`maintenance_kcal` is computed once per `as_of`** (current profile +
  latest known weight ≤ as_of) and that single figure is reused for every
  day in `week_balance_kcal`'s trailing window, rather than resolving
  historical profile/weight snapshots per day. TDEE from static profile
  fields doesn't meaningfully fluctuate day to day; only `consumed` varies
  and that's read per-day from real `MealItem` rows.
- **Intersex / unknown `sex_at_birth` → `maintenance_kcal = None`.**
  Mifflin-St Jeor is sex-specific with no third term; averaging the two
  would be a fabricated number (CLAUDE.md rule 2), so it's an honest gap.
- **`days_since_rest`** is the consecutive-training-day streak ending
  `as_of` (today untrained but something else logged ever = a real `0`;
  today trained after a rest day = `1`; etc). `None` only when the user's
  entire diary — every table — has zero rows ever. If the streak runs off
  the edge of the user's earliest diary entry without finding a rest day,
  it's returned with `partial=True` (a lower bound, not a wrong number).
- **`adherence_7d`** excludes still-`pending` (overdue, unresolved)
  `PlannedSession` rows from both numerator and denominator, marking the
  aggregate partial rather than presenting an unresolved outcome as a hit
  or a miss. Also nulls entirely for now in practice: no plan generator
  (§7 Budget 2) exists yet to populate `PlannedSession`.
- **`week_volume_planned`** assumes `PlannedSession.planned_volume` has
  the shape `{"total_kg": <number>}`. Nothing currently writes that field
  (no plan generator exists), so this is provisional — revisit once Budget
  2 ships and defines the real shape.
- **Weight trend**: 28-day lookback window; OLS linear regression
  (`statistics.linear_regression`) over `(day_offset, weight_kg)`, not
  last-minus-first. Confidence = `r² × min(n / 6, 1.0)` — squared
  correlation alone lets a 2-point line claim perfect confidence, so it's
  scaled down until at least 6 weigh-ins exist. A perfectly flat series
  (zero y-variance) is confidence `min(n/6, 1.0)` directly, since
  `statistics.correlation` is undefined there but the flat trend is real.
- **Maintenance activity multipliers**: the standard published Mifflin-St
  Jeor companion table (sedentary 1.2 → extra_active 1.9). Equation-derived
  maintenance always reports `confidence = 0.7`, a fixed constant — this
  slice never produces the "blended" measured path from §7 Budget 1, so
  there's no computed confidence to report yet.
- **Sufficiency thresholds**: `insufficient_data_for_adaptation` trips at
  >3 missing food days (of trailing 7) or >10 missing weight days (of
  trailing 14). `energy.data_sufficiency` is `"sufficient"` only with a
  computed `maintenance_kcal` and ≤2 missing food days.
- **`target_kcal_today` applies no floor.** §7's floor values are an
  explicitly open decision (§17) belonging to the not-yet-built budget
  engine; inventing a number here would itself violate rule 2.
- **`fitness_age`, `fitness_age_inputs`, `readiness_ceiling_today`, and the
  entire `cycle:` block are `null`/disabled in this slice.** They depend on
  models this task didn't build (VO2max/pose model, Budget 3, §10 cycle
  logic) — CLAUDE.md rule 2 says omit rather than fabricate, and the guide
  is explicit that `cycle.phase` must never default to a guessed middle
  value.
- **Per-field `_partial` companion keys** (`consumed_kcal_today_partial`,
  `week_volume_completed_partial`, etc.) were added to `energy`/`training`
  beyond §6's literal field list. The task requires "every derived value
  carries a confidence or source marker"; §6 only names one for
  `maintenance_*` and `trend_confidence`. The `_partial` fields are that
  marker for the rest — additive, nothing in §6's list was renamed or
  dropped.
- **`DailyState` is write-only in this slice.** It's populated on every
  sync and by the nightly rebuild, but `/api/v1/state/` always computes
  live via the same `_compute_daily_metrics` the cache writer calls — so
  the two can't drift, and correctness never depends on a warm cache. No
  code reads `DailyState` yet; it exists for later slices (historical
  trends, anything that shouldn't pay live-query cost per request).

## 2026-09-05 — Demo account: seeded, not scripted

`manage.py seed_demo_account` creates a fixed account (`demo@fitforge.app`)
with 6 weeks of history across every diary table (`WorkoutSession`/`SetLog`,
`MealLog`/`MealItem`, `WeightLog`, `CycleLog`) plus a `UserProfile` and
`Goal`, written through the same model layer `apps/api/sync.py` uses for a
real device. Nothing writes to `UserState` or `DailyState` — the demo
account's dashboard is `compute_user_state` running over these rows, exactly
like any other account. Confirmed against the real dev DB: maintenance,
weight trend (with real regression confidence, not a manufactured 1.0),
weekly training volume by muscle group, and a fully narrated `plan_delta`
(including the ±300 kcal floor actually engaging) all came out of the
calculator/engine, not out of the command.

- **Idempotent via delete-and-recreate, not per-row upsert.** Re-running
  deletes the demo `User` — which cascades through every diary table via FK
  — and rebuilds it from scratch. An idempotent diff across six tables would
  be far more code for the same guarantee the task actually asked for:
  "resets to its seeded state," not "merges."
- **`bulk_create`, not one `.create()` per row.** The first version used
  `.objects.create()` per row (~500 rows) and took several minutes against
  the real (Neon, remote) dev database — plainly wrong for a command whose
  purpose is a fast reset right before a live demo. Rewritten to build
  `WorkoutSession`/`MealLog` objects, `bulk_create` them (Postgres populates
  `.pk` on the returned objects), then build the dependent `SetLog`/
  `MealItem` rows against those ids and `bulk_create` those too. Cut the
  runtime to ~10 seconds.
- **Deterministic, not random.** Weight-log noise and meal/exercise
  variation come from fixed lookup tables and modular arithmetic on the day
  offset, not `random`. Re-running the command on the same day reproduces
  byte-identical seeded rows — a real property of "resets to its seeded
  state," not just an approximation of one.
- **`DailyState` is deliberately not warmed by this command.** It's a pure
  cache nothing reads yet (see the entry above) — populating it here would
  reintroduce the per-day query cost the `bulk_create` rewrite exists to
  remove, for a cache no code consumes.
- **Cycle phase stays `null` for the demo account too.** `CycleLog` rows are
  seeded (two realistic periods plus an ovulation-window symptom entry), and
  `profile.tracks_cycle = True`, so `cycle.enabled` is `True`. But
  `apps/engine/service.py` hard-codes `phase`/`cycle_day`/etc. to `null` for
  every account — §10 cycle-phase derivation isn't built yet. Seeding cycle
  history now means a future slice has real data to compute against; it
  does not and should not make the demo account special-case truthier
  output than a real user would get today.
- **The fixed password is not a secret.** `demo@fitforge.app` /
  `FitForgeDemo!2026` are hardcoded in both the seed command and the Android
  "Try demo" button on purpose — the button's entire job is to authenticate
  as this account through the normal `/api/v1/auth/login/` endpoint, no
  bypass path. Treating it as a credential to protect would contradict its
  own reachability.
- **The Android button is gated on `BuildConfig.DEBUG`**, not a runtime
  flag or a visual-only condition — AGP compiles `DEBUG` per variant, so a
  release build has no code path that can show it, regardless of any
  runtime state.
