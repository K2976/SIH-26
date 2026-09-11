# Priya Menon — presenter demo account

Run `python manage.py seed_presenter_demo` from `backend/` before a live demo.
It resets only this account, then replays the real signup, onboarding,
cycle-settings, and diary-sync endpoints. It is intentionally separate from
the app's generic **Try demo** account.

| Login | Value |
| --- | --- |
| Email | `priya.menon.demo@fitforge.app` |
| Password | `PriyaFitForge!2026` |
| Time zone | Asia/Kolkata (04:00 local day boundary) |

## Story to tell

Priya is a 30-year-old product designer in Bengaluru. She joined FitForge 61
days ago after feeling less strong during a busy release cycle. She began at
68.5 kg and chose a modest **cut + hypertrophy** goal, first selecting the
onboarding default and then setting the target rate to **0.35 kg/week**. Her
aim is to rebuild a consistent three-session strength habit while losing
slowly, not to crash diet.

Her normal plan is lower body on Monday, upper body on Wednesday, and a mixed
upper/body session on Friday. It is deliberately imperfect:

- In the fourth week, a work trip followed by a chest infection left every
  scheduled session skipped. Those are actual `PlannedSession` rows with
  `skipped` status, not an adherence value inserted into state.
- In the current week she completed the first planned session but skipped the
  two later ones. The dashboard's PlanDelta therefore receives a real planned
  volume shortfall and completed volume from diary rows to redistribute (or
  safely drop) rather than an invented recommendation.
- In the six days leading into the demo, she trained every day while trying to
  make up momentum. This is not her usual plan; it is why the computed
  days-since-rest/high-load input is present.

The logbook includes real sync-path `SetLog` rows with `source: camera` for
back squat, push-up, and dumbbell bicep curl. They carry the same `weight_kg:
null`, `rom_achieved: full`, and `tempo_ms` output shape written by the camera
rep counter's `saveSet` path. The surrounding sets are ordinary manually
logged weighted work, so missing external load on a camera-counted weighted
movement remains honest partial volume data rather than being silently made
up.

## Nutrition and body history

Priya generally logged three meals. Four earlier days have no meal log at all
(not zero-calorie rows), which lets the food history and data-sufficiency
behaviour demonstrate real null handling. Her final 14 days are complete,
with 1,620 kcal logged per day—below her calculated maintenance by more than
the engine's deficit margin. Morning weights are frequent but not perfect,
and include a small deterministic water-weight wobble over a gradual loss.

Every dashboard energy target, balance, trend, sufficiency status, training
volume, adherence value, and PlanDelta is calculated after the rows enter the
normal sync endpoint. The seed command never inserts `UserState`,
`DailyState`, or `CyclePattern` values; sync and the cycle recompute service
create those reproducible caches from the diary.

## Cycle narrative and safety flag

Priya opted into natural cycle tracking and stated a 27-day typical cycle in
cycle settings (confidence 0.6 while she is still building a history). The
seed contains a daily symptom and energy-rating observation for every one of
the last 61 local days. Her first observed period began 61 days ago; the next
began 22 days later. The current cycle has now run 39 days without a logged
period start: a notably long cycle relative to both that direct 27-day input
and her first observed 22-day interval. Menstrual flow and expected cramps /
fatigue are logged on each period day; later-cycle low energy and bloating are
also logged. With two observed cycle starts, pattern learning has two cycles
of actual daily observations and exposes its sample count/confidence instead
of pretending that the pattern is certain.

**Yes: the seeded history causes `energy_availability_review` to fire.** It
does so through the engine's real conjunction: natural tracking, the current
cycle at least 10 days overdue against her directly reported 27-day typical
length, 14 consecutive complete logged days in a meaningful deficit, and a
six-day training streak. The dashboard floor then prevents the computed
calorie target from going below maintenance. No flag is stored or scripted by
the seed.

Dates are generated relative to the date the command runs, from 60 days ago
through today, at Asia/Kolkata timestamps after the 04:00 boundary. Re-running
the command removes and recreates only Priya's account, so the live-demo
history remains repeatable and does not duplicate rows.
