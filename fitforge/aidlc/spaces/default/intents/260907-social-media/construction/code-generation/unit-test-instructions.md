# Unit Test Instructions — FitForge Social Media Feature

## Overview
This document specifies the exact test harness, commands, mocking patterns, and verification procedures for the FitForge Social Media feature slice (`backend/apps/social/` and `feature/social/`).

Per the affirmed **Testing Contract** (Express scope / Minimal strategy):
- **Methodology**: Test-after (implement each applicable testable layer, then write and run that layer's tests).
- **Strategy Floor**: Requirement-driven unit tests (at least 1 test per requirement, 1 happy-path test per component).
- **Scope Floor**: Keep the existing test suite green.

---

## 1. Test Framework Setup & Configuration

### Backend (Python / Django)
- **Framework**: `pytest` with `pytest-django`, `hypothesis`, and `Faker`.
- **Configuration**: Root `pyproject.toml` (Django settings: `fitforge.settings.dev`).
- **Python Environment**: `backend/.venv/` (Python 3.12).
- **Database**: Django DB test runner handles migrations automatically; PostgreSQL / SQLite test database is provisioned during test session.

### Frontend (Android / Jetpack Compose)
- **Framework**: JUnit 4 / 5, MockK, Kotlinx Coroutines Test.
- **Compose UI Tests**: `androidx.compose.ui.test.junit4`.
- **Target Package**: `com.fitforge.app.feature.social`.

---

## 2. Unit-Scoped Run Commands

> **CRITICAL**: All commands below are strictly scoped to the social feature slice. Do NOT run unscoped suite commands.

### Backend Test Suite (Exact Command)
From directory `backend/`:
```bash
.venv\Scripts\pytest apps/social/tests/ -v
```
Or to run a specific layer:
```bash
# Model & Constraint Tests
.venv\Scripts\pytest apps/social/tests/test_models.py -v

# Service & Privacy Scrubber Tests
.venv\Scripts\pytest apps/social/tests/test_services.py -v

# Serializer Validation Tests
.venv\Scripts\pytest apps/social/tests/test_serializers.py -v

# End-to-End API Integration Tests
.venv\Scripts\pytest apps/social/tests/test_social_api.py -v
```

### Android Test Suite (Exact Command)
From directory `android/`:
```bash
./gradlew :app:testDebugUnitTest --tests "com.fitforge.app.feature.social.*"
```

---

## 3. Expected Coverage Targets & Test Scenarios

### Requirement Mapping Table

| Requirement | Test Scenario | File | Expected Behavior |
|---|---|---|---|
| **FR1.1** | Social Handle Creation & Uniqueness | `test_serializers.py`, `test_social_api.py` | 201 on valid handle; 400 on invalid characters or collision; `auth_user` unchanged |
| **FR1.2** | Read-Only Feed Access | `test_social_api.py` | 200 on unauthenticated GET `/api/v1/social/feed/explore/`; 401 on unauthenticated POST |
| **FR1.3** | Private Profile Toggle | `test_models.py`, `test_services.py` | Follows to private profile start as `status: pending` |
| **FR2.1** | Dual Feed Delivery | `test_social_api.py` | Following feed filters to followee posts; Explore shows all public posts |
| **FR2.2** | Cursor Pagination | `test_social_api.py` | Returns `next` cursor and 20 items per page |
| **FR3.1 / FR3.2** | Workout Sharing & Biometric Scrubbing | `test_services.py`, `test_social_api.py` | Mechanical metrics (sets, reps, exercises) included; weight, calories, cycle stripped |
| **FR4.1 / FR4.3** | Directed Following & Unfollow | `test_models.py`, `test_social_api.py` | Follow created with correct status; unfollow deletes record cleanly |
| **FR4.2** | Prevent Self-Follow | `test_models.py`, `test_social_api.py` | Database CheckConstraint and API return 400/IntegrityError on self-follow |
| **FR5.1** | Idempotent Post Likes | `test_models.py`, `test_social_api.py` | POST toggles like state; counter increments/decrements atomically |
| **FR5.2 / FR5.3** | Threaded Comments & Author Moderation | `test_social_api.py` | Nested reply has `parent_id`; post author can delete comments |
| **FR6.1** | User Blocking | `test_services.py`, `test_social_api.py` | Blocking dissolves follow and hides posts in feeds |
| **NFR2** | Auth Compatibility | `test_social_api.py` | Standard JWT token authenticates requests seamlessly |
| **NFR4** | Privacy Guarantee Floor | `test_serializers.py`, `test_services.py` | Zero occurrences of `weight`, `calories`, `cycle` in social post serializer outputs |
| **NFR5** | Data Integrity | `test_models.py` | Unique constraints prevent duplicate likes and duplicate follows |

---

## 4. Mocking & Stubbing Guidance

- **User Authentication**: Use Django REST framework `APIClient.force_authenticate(user=user)` with test users created via `django.contrib.auth.get_user_model()`.
- **Workout Sessions**: Create mock `diary.WorkoutSession` instances populated with `ExerciseLog` and `SetLog` to test the workout sharing telemetry card generation.
- **Biometric Leakage Stubs**: Ensure test workout sessions have synthetic energy expenditure (`calories_burned`) and linked body weight logs; explicitly assert that these keys do NOT appear in the social post payload.
- **Android Repository**: Use MockK or a fake in-memory `FakeSocialRepository` to test `SocialViewModel` state transitions independently of network responses.

---

## 5. Test Data Management

- All test instances (`User`, `SocialProfile`, `Post`, `Like`, `Comment`) are isolated via `@pytest.mark.django_db` transactions.
- Never hardcode primary keys in assertions; query by relations or verify payload shape.
- Teardown is automatically managed by Django's transactional test runner.
