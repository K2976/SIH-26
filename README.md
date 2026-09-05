# FitForge

> **Next-generation adaptive fitness intelligence.** Where training, nutrition, and menstrual cycle data feed one adaptive engine.

---

## What FitForge Is

Most fitness apps track training, nutrition, and cycle data in isolated silos:
- A workout tracker doesn't know what you ate.
- A calorie counter doesn't know you trained heavy squats this morning.
- A menstrual cycle tracker knows nothing about either.

**FitForge computes them against each other.**
- Train harder today → today's energy budget dynamically moves.
- Miss a session → remaining weekly volume rebalances safely or drops (never crammed dangerously).
- Log lower recovery, poor sleep, or fatigue symptoms → today's readiness ceiling lowers.

Every feature on the mobile device reads one shared, unified state object: **`UserState`**.

---

## Architectural Principles (The Seven Non-Negotiable Rules)

1. **The LLM never computes a number.** All calories, targets, readiness scores, weights, and volumes are computed deterministically in pure Python from stored data.
2. **Missing is not zero.** An unlogged day is `null` and marked as `partial`. Fabricated numbers or plausible defaults are never substituted. If data is absent, the UI shows an explanatory empty state.
3. **Budgets, not rules.** Behavior emerges from three mathematically tracked quantities: Weekly Energy Balance, Weekly Training Volume, and Daily Readiness Ceiling.
4. **Absolute safety floors.** No proposal may violate safety floors: absolute daily calorie minimums (1,200 kcal for females / 1,500 kcal for males), 110% volume increase caps, and damping factors preventing restrict-overeat rebound cycles.
5. **Single state contract.** Features read `UserState` exclusively. No feature queries another feature's raw tables.
6. **Device records; server decides.** Mobile writes to Room immediately for seamless offline usage in gyms; only the server computes targets, clamps, and plan deltas.
7. **Immutable diary rows.** Diary entries (workouts, sets, meals, weights) are append-only. Corrections point back to previous entries using `supersedes_id`.

---

## Monorepo Layout

```
SIH-26/
├── README.md                  # This file
└── fitforge/
    ├── CLAUDE.md              # Operational rules and architecture guide
    ├── docs/
    │   ├── FITFORGE_PROJECT_GUIDE.md  # Comprehensive product specification
    │   └── DECISIONS.md       # Architectural decision records
    ├── backend/               # Django 5 REST Framework API & Adaptive Engine
    │   ├── apps/
    │   │   ├── accounts/      # Auth, UserProfile, Goal
    │   │   ├── reference/     # Exercise library, MET values, movement patterns
    │   │   ├── diary/         # Append-only diary logs (workouts, meals, weights)
    │   │   ├── engine/        # The Adaptive Engine (calculator, budgets, floors)
    │   │   ├── coach/         # LLM tool-calling boundary
    │   │   └── api/           # REST endpoints, StateView, SyncView, Swagger UI
    │   ├── fitforge/          # Django settings (dev.py, prod.py, base.py)
    │   ├── requirements/      # Dependency specifications
    │   └── README.md          # Dedicated backend engineering guide
    └── android/               # Modern Android app (Jetpack Compose, Room, Hilt)
        ├── app/
        │   └── src/main/java/com/fitforge/app/
        │       ├── core/      # Database (Room), DI (Hilt), Network (Retrofit)
        │       ├── data/      # Repositories (UserState, Sync, Reference)
        │       ├── feature/   # Compose screens (Dashboard, Onboarding, Logbook)
        │       └── sync/      # WorkManager background delta-sync workers
        └── openapi/
            └── schema.yaml    # Contract schema used to generate Retrofit client
```

---

## How to Run the Backend

For complete technical notes, see [`fitforge/backend/README.md`](fitforge/backend/README.md).

### 1. Prerequisites
- **Python**: 3.12 or 3.13
- **PostgreSQL**: 15+ (local PostgreSQL service or cloud instance like Neon)
- **pip** and `venv`

### 2. Set Up Virtual Environment & Dependencies
```bash
# Navigate to the backend directory
cd fitforge/backend

# Create a virtual environment
python3 -m venv .venv

# Activate the virtual environment
source .venv/bin/activate       # On Windows: .venv\Scripts\Activate.ps1

# Install requirements
pip install --upgrade pip
pip install -r requirements/dev.txt
```

### 3. Configure Environment Variables
Create your local `.env` configuration:
```bash
cp .env.example .env
```
Ensure your `.env` contains your database URL and settings:
```ini
DJANGO_SETTINGS_MODULE=fitforge.settings.dev
DJANGO_SECRET_KEY=dev-insecure-secret-key
DJANGO_ALLOWED_HOSTS=localhost,127.0.0.1,10.0.2.2
DATABASE_URL=postgresql://user:password@localhost:5432/fitforge
```
*(If you are running the backend with a Neon PostgreSQL cloud database, paste your connection string into `DATABASE_URL`)*.

### 4. Run Migrations & Seed Data
```bash
# Apply database migrations
python manage.py migrate

# Seed the exercise reference library (MET values, movement patterns, muscle groups)
python manage.py seed_exercises

# (Optional) Create an admin user to access the Django admin portal
python manage.py createsuperuser
```

### 5. Start the Backend Server
```bash
python manage.py runserver 0.0.0.0:8000
```

The backend is now live:
- **Interactive Swagger UI**: [http://localhost:8000/api/v1/docs/](http://localhost:8000/api/v1/docs/)
- **OpenAPI 3.0 Schema**: [http://localhost:8000/api/v1/schema/](http://localhost:8000/api/v1/schema/)
- **Django Admin**: [http://localhost:8000/admin/](http://localhost:8000/admin/)
- **State Endpoint**: [http://localhost:8000/api/v1/state/](http://localhost:8000/api/v1/state/)

### 6. Run the Test Suite
The backend contains unit tests and **Hypothesis** property-based tests verifying that safety floors, damping, and budgets hold over 14,000+ generated histories:
```bash
pytest
```

---

## How to Run the Android App

### 1. Prerequisites
- **Android Studio**: Ladybug / Meerkat or later
- **JDK**: Version 17 or higher
- **Android Device or Emulator**: Android 8.0+ (API 26+)

### 2. Build & Generate API Client
The Android Retrofit client is automatically generated from the backend's OpenAPI schema:
```bash
cd fitforge/android

# Generate the typed Retrofit client models & StateApi
./gradlew openApiGenerate

# Compile and run unit tests
./gradlew testDebugUnitTest

# Assemble the debug APK
./gradlew assembleDebug
```

### 3. Connect to the Backend
- **Android Emulator**: In `android/app/build.gradle.kts`, debug builds point to `http://10.0.2.2:8000/` which automatically routes to your host machine's `http://localhost:8000/`.
- **Physical Device**: Update `BASE_URL` in `android/app/build.gradle.kts` (or run a reverse ADB proxy via `adb reverse tcp:8000 tcp:8000`).

---

## Core Technologies

| Layer | Technologies |
|---|---|
| **Backend** | Python 3.13, Django 5.2, Django REST Framework, PostgreSQL, SimpleJWT, DRF-Spectacular, Ruff, Pytest, Hypothesis |
| **Android** | Kotlin 2.3, Jetpack Compose, Material 3, Room 2.8, Hilt DI, WorkManager, Retrofit 3.0, Kotlinx Serialization, Kotlinx Datetime, Vico |
