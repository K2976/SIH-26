# FitForge Database Migration Plan & Safety Roadmap

## Overview

This plan details how the proposed social database extensions will be introduced into the existing Neon PostgreSQL database using Django's migration engine.

**Strict Constraint**: At this audit stage, **NO migrations will be generated or executed**. This document serves as the approved blueprint for future execution.

---

## 1. Migration Safety Taxonomy

| Category | Classification | Criteria in FitForge |
| :--- | :--- | :--- |
| **Safe Migrations** | `GREEN` | • Creating new independent tables in a dedicated app (`apps/social/`).<br>• Adding nullable foreign keys to existing tables.<br>• Adding new unique constraints on newly created tables.<br>• Adding indexes on new tables. |
| **Cautionary Migrations** | `YELLOW` | • Foreign keys referencing heavily written tables (e.g. `diary_workoutsession`). Must use `ON DELETE SET NULL` or `ON DELETE CASCADE` appropriately so existing session writes/deletions are not blocked by locking. |
| **Prohibited / High-Risk** | `RED` (FORBIDDEN) | • Altering `auth_user.username` or `auth_user.email`.<br>• Renaming or dropping any existing column in `accounts_*`, `diary_*`, `reference_*`, or `engine_*`.<br>• Adding non-nullable columns without defaults to existing tables.<br>• Changing existing data types (e.g. `varchar` to `uuid`). |

---

## 2. Progressive Migration Phases

All migrations reside inside `backend/apps/social/migrations/`.

### Phase 1: Modular Foundation & Public Identity [COMPLETED & APPLIED]
**Target Migration**: `apps/social/migrations/0001_initial.py` — **STATUS: APPLIED ON NEON**
- **Actions Completed**:
  1. Created model `SocialProfile` with `(user_id, username, display_name, bio, profile_image, is_private, created_at, updated_at)`.
  2. Set `user` as `OneToOneField(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="social_profile")`.
  3. Set `username` as `CharField(max_length=32, unique=True, db_index=True)`.
- **Dependencies**: `("auth", "0012_alter_user_first_name_max_length")`.
- **Verification**:
  - 31 automated tests passing.
  - Successfully verified in production Neon database.
  - Public username decoupled from `auth_user.username` (email).

### Phase 2: Communities & Memberships Domain [NEXT UP]
**Target Migration**: `apps/social/migrations/0002_communities.py`
- **Actions**:
  1. Create model `Community` with `(name, slug, description, avatar_url, banner_url, visibility, owner, members_count, created_at, updated_at)`.
  2. Create model `CommunityMembership` with `(community, user, role, status, joined_at)`.
  3. Add `UniqueConstraint(fields=["community", "user"], name="unique_community_membership")` to prevent duplicate memberships.
  4. Add index on `(user, status)` and `(community, status, role)` for performant queries.
- **Dependencies**: `("social", "0001_initial")`.
- **Backward-Compatibility Evaluation**:
  - Fully additive. Zero locks or changes to existing accounts or diary tables.

### Phase 3: Challenges & Participation Domain
**Target Migration**: `apps/social/migrations/0003_challenges.py`
- **Actions**:
  1. Create model `Challenge` with `(community, creator, title, description, challenge_type, target_value, target_unit, start_date, end_date, status, participants_count, created_at, updated_at)`.
     - `community` is a nullable foreign key (`null=True, blank=True, on_delete=models.CASCADE`) to accommodate both community-specific and platform-wide challenges.
  2. Create model `ChallengeParticipation` with `(challenge, user, status, current_progress, completed_at, joined_at, updated_at)`.
  3. Add `UniqueConstraint(fields=["challenge", "user"], name="unique_challenge_participation")` to prevent duplicate entries.
- **Dependencies**: `("social", "0002_communities")`.
- **Backward-Compatibility Evaluation**:
  - Independent models. Logic derives progress from `diary_workoutsession` via backend calculation queries. No schema coupling or locks on diary tables.

### Phase 4: Streaks & Habit Accountability
**Target Migration**: `apps/social/migrations/0004_streaks.py`
- **Actions**:
  1. Create model `UserStreak` with `(user, current_streak_days, longest_streak_days, last_activity_date, streak_freeze_credits, updated_at)`.
  2. `user` has `OneToOneField(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="fitness_streak")`.
- **Dependencies**: `("social", "0001_initial")`.
- **Backward-Compatibility Evaluation**:
  - Additive cache table. Server calculates streak intervals from stored `diary_workoutsession` dates.

### Phase 5: Community-Driven Challenges & Leaderboards
- Leverages `Community` and `Challenge` models from Phases 2 and 3.
- Community leaderboard rankings computed via DRF aggregation endpoints over `ChallengeParticipation`.
- Zero new destructive DDL operations.

### Phase 6: Accountability Notifications
**Target Migration**: `apps/social/migrations/0005_notifications.py`
- **Actions**:
  1. Create `Notification` table for challenge milestones, community invites, and streak alerts.
- **Dependencies**: `("social", "0003_challenges")`.

---

## 3. Deferred Features (No Migrations Planned)

In accordance with product guidelines, traditional social feed tables are deferred:
- No `social_post` or `social_postmedia`
- No `social_like` or `social_comment`
- No feed timeline or follower graph tables

---

## 3. Migration Rollback & Disaster Recovery Strategy

Each migration operation will implement reversible Django operations:
- Every `CreateModel` operation is inherently reversible by Django's migration unapply (`python manage.py migrate social <previous_migration>`).
- If a migration fails halfway on Neon, PostgreSQL wraps DDL in an atomic transaction (`ATOMIC_REQUESTS` / DDL transactions), rolling back clean without leaving orphaned tables.

---

## 4. Pre-Execution Safety Checklist (For Future Implementation)

Before any migration is executed against Neon PostgreSQL:
1. [ ] Confirm `apps/social` is registered in `INSTALLED_APPS` in `fitforge/settings/base.py`.
2. [ ] Run `python manage.py makemigrations --dry-run` to inspect generated SQL operations.
3. [ ] Run `python manage.py sqlmigrate social 0001` to view exact PostgreSQL DDL statements.
4. [ ] Verify that no `ALTER TABLE auth_user` statement appears in the generated SQL.
5. [ ] Verify that all existing unit tests in `apps/accounts/tests/` and `apps/api/tests/` pass with zero failures.
6. [ ] Ensure the existing website staging environment can log in and sign up while the database contains the new migration.
