# Code Generation Plan — FitForge Social Media Feature

## Traceability & Upstream Context
- **Requirements**: `aidlc/spaces/default/intents/260907-social-media/inception/requirements-analysis/requirements.md` (FR1-FR6, NFR1-NFR5, CON1-CON4, ASM1-ASM2)
- **Team Rules**: `aidlc/spaces/default/memory/team.md` (Modular isolation, conventional commits on `slice/<name>`, walking skeleton, test-after posture)
- **Project Scope**: `express` (Minimal test strategy: requirement-driven tests covering happy paths, edge cases, and safety floors)

---

## Testing Contract

```json
{
  "version": 1,
  "methodology": "test-after",
  "source": "team",
  "ordering": "implement each applicable testable layer, then write and run that layer's tests.",
  "scope": "express",
  "test_strategy": "minimal",
  "project_type": "brownfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run that layer's tests.\n\nEngine calculations specifically get property-based tests via hypothesis (per `fitforge/CLAUDE.md`). Social endpoints require unit and integration tests covering unique username validation and backward-compatible authentication."
    }
  ],
  "obligations": {
    "strategy": "minimal",
    "strategy_volume": [
      "One verifiable test per requirement at the narrowest effective level.",
      "At least one happy-path unit test per component.",
      "Unit tests are the default; a bugfix/security scope floor may require an integration or E2E regression when that is the narrowest level that reproduces the defect."
    ],
    "scope_floor": [
      "Keep the existing test suite green.",
      "This scope adds no extra new-test floor beyond the selected test strategy."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "test-after",
    "runner_step": "Verify the existing test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Verify the existing test runner/configuration and record the exact unit-scoped command.",
      "Data model / database behavior - implement.",
      "Data model / database behavior - write and run its tests after implementation.",
      "Repository / data access - implement.",
      "Repository / data access - write and run its tests after implementation.",
      "Business logic - implement.",
      "Business logic - write and run its tests after implementation.",
      "API / endpoint - implement.",
      "API / endpoint - write and run its tests after implementation.",
      "Frontend behavior - implement.",
      "Frontend behavior - write and run its tests after implementation.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:b5dffcb8d9b6c0ab2fb22a01453d44f7a93e4b840e40715cf4621ce45bd36614",
  "contract_sha256": "sha256:ed05c2f9de98e040b2ef1b8c333f4106a9243cdf83a347e1c3caa4a33754b300"
}
```

---

## Implementation Steps & Story Traceability

- [ ] **Step 1: Project structure and production configuration skeleton**
  - Verify `backend/apps/social/` module isolation and ensure registered in `fitforge/settings/base.py`.
  - Check Android directory structure at `android/app/src/main/java/com/fitforge/app/feature/social/`.
  - *Traceability*: CON1 (Modular Isolation), NFR2 (Backward Compatibility).

- [ ] **Step 2: Verify existing test runner/configuration and record exact unit-scoped command**
  - Verify Python virtualenv pytest runner with `.venv\Scripts\pytest backend/apps/social/tests/`.
  - Document runner readiness and execution syntax in `unit-test-instructions.md`.
  - *Traceability*: Testing Contract, Team Practice.

- [ ] **Step 3: Data model / database behavior - implement**
  - Verify `backend/apps/social/models.py` schema for `SocialProfile`, `Follow`, `Post`, `PostMedia`, `Like`, `Comment`, `Notification`.
  - Ensure migrations (`0001_initial.py`) are consistent and constraints (`prevent_self_follow`, `unique_user_follow`, `unique_post_like`) are in place.
  - *Traceability*: FR1.1 (Social Handle), FR4.2 (Prevent Self-Follow), FR5.1 (Like Idempotency), NFR5 (Data Integrity), CON2 (Core Schema Immutability).

- [ ] **Step 4: Data model / database behavior - write and run tests after implementation**
  - Create `backend/apps/social/tests/test_models.py`.
  - Validate model constraints, self-follow rejection, string representations, cascade behavior.
  - Execute: `.venv\Scripts\pytest backend/apps/social/tests/test_models.py`.
  - *Traceability*: FR4.2, NFR5.

- [ ] **Step 5: Repository / data access - implement**
  - Implement query and service layer helpers in `backend/apps/social/services.py`:
    - Timeline/feed assembly: Following feed vs Explore feed with reverse-chronological cursor ordering.
    - Privacy enforcement: scrubber utility removing all biometrics (weight, calories, cycle) from shared workout sessions.
    - Follow request lifecycle (immediate accept for public, pending for private).
    - Block check and mutual post suppression.
  - *Traceability*: FR2.1 (Feed Discovery), FR3.2 (Mandatory Biometric Floor Exclusion), FR4.1 (Follow Mechanics), FR6.1 (User Blocking), NFR4 (Privacy Guarantee).

- [ ] **Step 6: Repository / data access - write and run tests after implementation**
  - Create `backend/apps/social/tests/test_services.py`.
  - Test biometric scrub helper, feed queryset filters, and follow status transitions.
  - Execute: `.venv\Scripts\pytest backend/apps/social/tests/test_services.py`.
  - *Traceability*: FR3.2, FR4.1, FR6.1, NFR4.

- [ ] **Step 7: Business logic - implement**
  - Create `backend/apps/social/serializers.py`:
    - `SocialProfileSerializer` & `SocialProfileCreateSerializer` (handle validation: 3-32 chars, alphanumeric + underscores).
    - `PostSerializer` (includes author profile, likes_count, comments_count, is_liked, formatted workout card).
    - `PostCreateSerializer` (creates post, links workout session, scrubs biometrics).
    - `CommentSerializer` (threaded comments and replies).
    - `FollowSerializer` & `FollowActionSerializer`.
    - `NotificationSerializer`.
  - *Traceability*: FR1.1, FR1.3, FR3.1, FR3.3, FR5.1, FR5.2, FR5.3.

- [ ] **Step 8: Business logic - write and run tests after implementation**
  - Create `backend/apps/social/tests/test_serializers.py`.
  - Validate handle character rules, comment threading, workout data serialization without biometric leakage.
  - Execute: `.venv\Scripts\pytest backend/apps/social/tests/test_serializers.py`.
  - *Traceability*: FR1.1, FR3.2, FR5.2, NFR4.

- [ ] **Step 9: API / endpoint - implement**
  - Create `backend/apps/social/views.py`:
    - `SocialProfileViewSet` (`/api/v1/social/profile/`, `/api/v1/social/profile/@{username}/`).
    - `FeedViewSet` (`/api/v1/social/feed/following/`, `/api/v1/social/feed/explore/`).
    - `PostViewSet` (`/api/v1/social/posts/`, `/api/v1/social/posts/{id}/like/`, `/api/v1/social/posts/{id}/comments/`).
    - `FollowViewSet` (`/api/v1/social/follow/{user_id}/`, `/api/v1/social/followers/`, `/api/v1/social/following/`).
    - `NotificationViewSet` (`/api/v1/social/notifications/`).
  - Create `backend/apps/social/urls.py` and register in `backend/apps/api/urls.py`.
  - Enforce permission classes: `IsAuthenticatedOrReadOnly` for feed browsing (FR1.2), `IsAuthenticated` for mutations.
  - *Traceability*: FR1.1, FR1.2, FR2.1, FR2.2, FR4.1, FR4.3, FR5.1, FR5.2, FR5.3, NFR1, NFR2.

- [ ] **Step 10: API / endpoint - write and run tests after implementation**
  - Create `backend/apps/social/tests/test_social_api.py`.
  - End-to-end API integration tests:
    - Profile registration, uniqueness conflict (400), retrieve profile.
    - Public unauthenticated feed read vs authenticated like/comment.
    - Create general post and workout share post; verify weight/calories are omitted from response.
    - Follow/unfollow toggle, self-follow rejection (400).
    - Idempotent like toggle and comment author moderation.
  - Execute: `.venv\Scripts\pytest backend/apps/social/tests/test_social_api.py`.
  - *Traceability*: FR1.1, FR1.2, FR2.1, FR3.1, FR3.2, FR4.1, FR4.2, FR5.1, FR5.2, FR5.3, NFR1, NFR2, NFR4, NFR5.

- [ ] **Step 11: Frontend behavior - implement**
  - Build Jetpack Compose UI under `android/app/src/main/java/com/fitforge/app/feature/social/`:
    - `SocialState.kt`: UI state models for Following/Explore tabs, posts, loading, error states.
    - `SocialViewModel.kt`: Tab switching, refresh, like toggle, follow toggle.
    - `SocialScreen.kt`: Dual-tab scaffold, swipe-to-refresh, empty state, post list.
    - `components/PostCard.kt`: Author header, caption, workout metric card, like & comment buttons with `data-testid` / Compose test tags.
    - `data/SocialRepository.kt`: Local/remote abstraction with Room caching readiness.
  - *Traceability*: FR2.1, FR2.2, FR3.3, FR5.1, NFR3, CON1.

- [ ] **Step 12: Frontend behavior - write and run tests after implementation**
  - Create `android/app/src/test/java/com/fitforge/app/feature/social/SocialViewModelTest.kt`.
  - Verify tab switching, like count optimistic update, offline error handling.
  - *Traceability*: FR2.1, FR5.1, NFR3.

- [ ] **Step 13: Environment/build configuration**
  - Run Ruff linter and formatter on backend: `ruff check backend/apps/social/` and `ruff format --check backend/apps/social/`.
  - Verify all migrations apply cleanly: `python manage.py makemigrations --check`.
  - *Traceability*: Team Code Style Rules.

- [ ] **Step 14: Documentation and traceability**
  - Generate `code-summary.md` documenting implemented files, key decisions, and test results.
  - Generate `source-manifest.json` listing all created and modified source files.
  - Generate `traceability.json` mapping requirements (FR1-FR6, NFR1-NFR5) to code files and test cases.
  - *Traceability*: AI-DLC Stage 3.5 contract obligations.
