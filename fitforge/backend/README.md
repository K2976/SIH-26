# FitForge — Backend Service

FitForge Backend is a Django 5 REST framework service powering the FitForge mobile application. At its core is an **adaptive engine** that interrelates training, nutrition, and menstrual cycle data into a single unified `UserState` snapshot and generates adaptive plan adjustments (`PlanDelta`).

---

## Table of Contents
1. [Core Principles & Architectural Rules](#core-principles--architectural-rules)
2. [Project Structure](#project-structure)
3. [Prerequisites](#prerequisites)
4. [Environment Setup & Installation](#environment-setup--installation)
5. [Database Setup & Migrations](#database-setup--migrations)
6. [Seeding Reference Data](#seeding-reference-data)
7. [Running the Development Server](#running-the-development-server)
8. [API Documentation & Swagger UI](#api-documentation--swagger-ui)
9. [Running Tests](#running-tests)
10. [Code Quality & Linting](#code-quality--linting)
11. [OpenAPI Client Generation](#openapi-client-generation)
12. [Environment Variables Reference](#environment-variables-reference)

---

## Core Principles & Architectural Rules

The backend adheres strictly to the FitForge architectural invariants:
1. **The LLM Never Computes a Number**: All calories, targets, readiness scores, weights, and volumes are computed deterministically in pure Python functions.
2. **Missing Is Not Zero**: Unlogged days return `null` and are flagged as `partial`. The engine never substitutes a zero or plausible default for missing user data.
3. **Pure Engine**: `apps/engine/` contains pure functions with zero I/O, no database queries, and no system clock access.
4. **Absolute Safety Floors**: Calorie targets cannot drop below safety minimums (1,200 kcal for female / 1,500 kcal for male), volume increases are capped at 110% of trailing averages, and weekly balance adjustments are dampened to prevent restrict-overeat cycles.
5. **Immutable Diary Rows**: User diary records (workouts, sets, meals, weights) are append-only. Corrections point back via `supersedes_id` and are never destructively modified in place.
6. **Single Contract (`UserState`)**: All client features consume the unified `UserState` payload emitted by `GET /api/v1/state/`.

---

## Project Structure

```
backend/
├── fitforge/                  # Django project root
│   ├── settings/
│   │   ├── base.py            # Shared settings, installed apps, JWT config
│   │   ├── dev.py             # Development settings (DEBUG=True, relaxed hosts)
│   │   └── prod.py            # Production settings (SSL, secure cookies)
│   ├── urls.py                # Root URL routing (/admin/, /api/v1/)
│   ├── wsgi.py / asgi.py
├── apps/
│   ├── accounts/              # User model, UserProfile, Goal, auth views
│   ├── reference/             # Exercise library, MET values, movement patterns
│   ├── diary/                 # Immutable diary logs (Workout, Meal, Set, Weight)
│   ├── engine/                # The Adaptive Engine (calculator, budgets, floors)
│   │   ├── types.py           # Frozen dataclasses (UserState, Proposals, PlanDelta)
│   │   ├── constants.py       # Scientifically sourced constants & safety thresholds
│   │   ├── calculator.py      # Pure math functions for aggregations & trends
│   │   ├── budgets.py         # Pure energy, volume, and readiness budget functions
│   │   ├── floors.py          # Safety clamps (min kcal, max volume increase, damping)
│   │   ├── adaptive.py        # compute_plan_delta orchestrator
│   │   ├── service.py         # Impure boundary gathering ORM rows for the engine
│   │   └── models.py          # DailyState cache table
│   ├── coach/                 # LLM tool-calling boundary (no numeric calculation)
│   └── api/                   # REST endpoints, UserState serializer, sync views
├── requirements/
│   ├── base.txt               # Production dependencies
│   ├── dev.txt                # Development tools (pytest, ruff, hypothesis)
│   └── prod.txt               # Gunicorn and production utilities
├── manage.py
└── pyproject.toml             # Ruff & pytest configuration
```

---

## Prerequisites

- **Python**: 3.12 or 3.13
- **PostgreSQL**: 15+ (local instance or cloud database such as Neon / Supabase / AWS RDS)
- **Git**

---

## Environment Setup & Installation

### 1. Navigate to the backend directory
```bash
cd fitforge/backend
```

### 2. Create and activate a Python virtual environment
Using macOS / Linux:
```bash
python3 -m venv .venv
source .venv/bin/activate
```
Using Windows (PowerShell):
```powershell
python -m venv .venv
.venv\Scripts\Activate.ps1
```

### 3. Install dependencies
Install all development and runtime requirements:
```bash
pip install --upgrade pip
pip install -r requirements/dev.txt
```

### 4. Configure environment variables
Copy the template `.env.example` to `.env`:
```bash
cp .env.example .env
```

Open `.env` and configure your database and runtime settings:
```ini
DJANGO_SETTINGS_MODULE=fitforge.settings.dev
DJANGO_SECRET_KEY=your-secure-development-key-here
DJANGO_ALLOWED_HOSTS=localhost,127.0.0.1,10.0.2.2
DATABASE_URL=postgresql://postgres:password@localhost:5432/fitforge
GEMINI_API_KEY=
```

Set `GEMINI_API_KEY` to a real Google AI Studio key before testing photo food
labels. The server returns a setup error when it is blank; it never substitutes
fake label data.

> **Note for Android Emulators**: Include `10.0.2.2` in `DJANGO_ALLOWED_HOSTS` so the Android Emulator can reach your local development server.

---

## Database Setup & Migrations

Ensure PostgreSQL is running and your database exists, then run:

```bash
python manage.py migrate
```

This applies migrations across all apps (`accounts`, `reference`, `diary`, `engine`, `auth`, `token_blacklist`).

---

## Seeding Reference Data

FitForge requires an initial reference library of exercises (with movement patterns, primary/secondary muscle groups, and MET values for caloric burn estimation):

```bash
python manage.py seed_exercises
python manage.py seed_foods
```

To export the reference bundle for the Android client offline database:
```bash
python manage.py export_reference_bundle
```

---

## Running the Development Server

### 1. (Optional) Create a superuser for Django Admin
```bash
python manage.py createsuperuser
```
Follow the prompts to specify an email and password.

### 2. Start the development server
To bind to localhost only:
```bash
python manage.py runserver
```

To allow connections from Android emulators or devices on the same Wi-Fi:
```bash
python manage.py runserver 0.0.0.0:8000
```

The service will be accessible at:
- **API Base**: `http://localhost:8000/api/v1/`
- **Django Admin**: `http://localhost:8000/admin/`
- **Swagger Documentation**: `http://localhost:8000/api/v1/docs/`
- **OpenAPI 3.0 Schema**: `http://localhost:8000/api/v1/schema/`

---

## Key API Endpoints

All endpoints require JWT Bearer Authentication (`Authorization: Bearer <token>`) except `/auth/signup/`, `/auth/login/`, and `/app-config/`.

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/auth/signup/` | Create a new user account |
| `POST` | `/api/v1/auth/login/` | Authenticate and obtain JWT access & refresh tokens |
| `POST` | `/api/v1/auth/token/refresh/` | Rotate access and refresh tokens |
| `GET` / `PATCH` | `/api/v1/profile/` | Manage user profile, biometrics, preferences |
| `GET` / `POST` | `/api/v1/goal/` | Manage caloric direction (cut/maintain/gain), training focus |
| `GET` | `/api/v1/state/` | **Primary contract**: Current user derived state, budgets, and PlanDelta |
| `POST` | `/api/v1/sync/` | Batch upload offline diary entries (sessions, sets, meals, weights) |
| `GET` | `/api/v1/reference/manifest/` | Check reference database version |
| `GET` | `/api/v1/reference/exercises/` | Fetch exercise library delta updates |
| `GET` | `/api/v1/reference/foods/` | Fetch local food table delta updates |
| `POST` | `/api/v1/food/label/` | Detect catalog food names from a temporary photo |
| `GET` | `/api/v1/app-config/` | Minimum supported app versions and runtime flags |

---

## Running Tests

FitForge features a comprehensive test suite combining unit tests and **Hypothesis** property-based tests.

### Run all backend tests:
```bash
pytest
```

### Run specific test modules:
```bash
# Test the pure adaptive engine budgets and property tests (14,000+ generated histories)
pytest apps/engine/tests/test_budgets.py

# Test the State API contract and PlanDelta serialization
pytest apps/api/tests/test_state_api.py

# Test diary synchronization and idempotency
pytest apps/api/tests/test_sync.py
```

---

## Code Quality & Linting

FitForge uses **Ruff** for linting and code formatting:

```bash
# Check code for lint errors
ruff check .

# Automatically apply safe fixes
ruff check --fix .

# Format code
ruff format .
```

---

## OpenAPI Client Generation

The Android application builds its Retrofit client directly from the backend's OpenAPI schema.

When you modify API endpoints or serializers, regenerate the schema:
```bash
python manage.py spectacular --file ../android/openapi/schema.yaml
```

Then in the `android/` directory, run:
```bash
./gradlew openApiGenerate
```
This guarantees the Android models and API interfaces are never out of sync with the backend.
