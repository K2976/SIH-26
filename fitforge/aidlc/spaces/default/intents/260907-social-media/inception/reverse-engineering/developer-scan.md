## Developer Code Scan Results

### Scan Coverage
- **Analyzed deeply**:
  - backend/
  - android/
  - docs/
  - CLAUDE.md
- **Skimmed only**:
  - aidlc/
  - .claude/

### Packages Found
- backend — Django REST Framework backend service — Python 3.12 — Core API, adaptive health/fitness engine, diary sync, accounts, and reference database.
- android/app — Native Android mobile application — Kotlin (JDK 17) — Jetpack Compose UI, Room offline-first local DB, CameraX, MediaPipe vision, Retrofit API client.

### Build System
- **Type**: Gradle (KTS with version catalog) for Android; Poetry / pyproject.toml / pip requirements for Django backend.
- **Config Files**:
  - `android/build.gradle.kts`
  - `android/app/build.gradle.kts`
  - `android/gradle/libs.versions.toml`
  - `backend/pyproject.toml`
  - `backend/requirements/base.txt`
  - `backend/requirements/dev.txt`
- **Build Dependencies**:
  - Android consumes the backend via generated or typed Retrofit HTTP clients against `/api/v1/` endpoints documented in `android/openapi/schema.yaml`.

### APIs Discovered
- REST API (JSON) — `backend/apps/api/urls.py` & `backend/apps/accounts/urls.py` — 10 primary endpoints:
  - `POST /api/v1/auth/signup/` (account creation)
  - `POST /api/v1/auth/login/` (JWT token pair issuance via email)
  - `POST /api/v1/auth/token/refresh/` (JWT token rotation)
  - `GET, PUT, PATCH /api/v1/profile/` (user biometrics and preferences)
  - `GET, POST /api/v1/goal/` (training/dietary target goal)
  - `GET /api/v1/app-config/` (min supported client version check)
  - `GET /api/v1/reference/manifest/` (delta-sync version manifest)
  - `GET /api/v1/reference/exercises/` (exercise library delta sync)
  - `GET /api/v1/schema/` (OpenAPI schema definition)
  - `GET /api/v1/docs/` (Swagger documentation)

### Frameworks & Libraries
- Python / Django — 5.0+ — Web framework, ORM, database migrations.
- Django REST Framework — 3.15+ — REST API serializers and viewsets.
- SimpleJWT — 5.3+ — JWT access/refresh token authentication with blacklisting.
- PostgreSQL / Neon — Neon Serverless PG (psycopg2-binary 2.9+) — Persistent cloud database.
- drf-spectacular — 0.27+ — OpenAPI 3.0 schema generation.
- Kotlin / Android Jetpack — Kotlin 1.9+, Android SDK 34 (minSdk 26) — Mobile runtime.
- Jetpack Compose / Material 3 — Compose BOM 2024+ — Reactive declarative mobile UI.
- AndroidX Room — 2.6+ — Offline-first local SQLite persistence.
- WorkManager — 2.9+ — Background delta synchronization.
- Retrofit + OkHttp — 2.9+ / 4.12+ — HTTP network client.
- Kotlinx Serialization — 1.6+ — JSON parser for DTOs and network models.

### Test Coverage
- **Test Directories**:
  - `backend/apps/accounts/tests/`
  - `backend/apps/reference/tests/`
  - `backend/apps/api/tests/`
  - `android/app/src/test/`
- **Test Frameworks**:
  - Pytest (`pytest`, `pytest-django`, `hypothesis`, `factory_boy`) for Python.
  - JUnit 4 / MockK / Turbine for Android unit testing.
- **Coverage Config**: Present in `pyproject.toml` (`[tool.pytest.ini_options]`).

### Code Quality Indicators
- **Linting**:
  - Backend: Ruff configured in `backend/pyproject.toml`.
  - Android: Detekt configured in `android/app/build.gradle.kts`.
- **CI/CD**:
  - GitHub Actions workflows in `.github/workflows/` (running ruff, detekt, pytest, assembleDebug).
- **Documentation**:
  - Comprehensive architectural guides in `CLAUDE.md`, `docs/FITFORGE_PROJECT_GUIDE.md`, and new database documentation in `backend/database/`.

### Technical Debt Signals
- `auth_user.username` is populated with email addresses across existing web signups (`backend/apps/accounts/serializers.py:27`). Public social features require a separate `social_profile.username` to prevent leaking user emails.
- Branch divergence across slices (`slice/diary-models-sync`, `slice/health-connect`) with pending unmerged migrations on the database.

## Handoff Summary
- **Intent-relevant finding**:
  - Backend uses Django 5 with DRF and Neon PostgreSQL. The existing `auth_user` model maps `username` to email (`SignupSerializer`, line 27: `username=validated_data["email"]`). A separate `social_profile` table with globally unique public `username` handle must be introduced in a new `apps/social/` app to support mobile social features safely without disrupting website authentication.
- **Risks / follow-up**:
  - Do not alter `auth_user.username` or `auth_user.email`.
  - Do not modify existing `accounts_*`, `diary_*`, `reference_*`, or `engine_*` tables.
  - Workout sharing must link to existing `diary_workoutsession.id` via nullable foreign key (`ON DELETE SET NULL`) rather than duplicating workout tables.
