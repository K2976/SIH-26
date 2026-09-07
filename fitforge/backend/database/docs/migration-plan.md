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

All migrations will be housed inside `backend/apps/social/migrations/`.

### Phase 1: Modular Foundation & Public Identity
**Target Migration**: `apps/social/migrations/0001_initial.py`
- **Actions**:
  1. Create model `SocialProfile` with `(user_id, username, display_name, bio, profile_image, is_private, created_at, updated_at)`.
  2. Set `user` as `OneToOneField(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="social_profile")`.
  3. Set `username` as `CharField(max_length=32, unique=True, db_index=True)`.
- **Dependencies**: `("auth", "0012_alter_user_first_name_max_length")`.
- **Backward-Compatibility Evaluation**:
  - `auth_user` remains completely untouched.
  - Existing website continues signing up users with `username=email`.
  - Social profiles can be created lazily upon first mobile app login or via a dedicated onboarding step.

### Phase 2: Social Graph (Follows)
**Target Migration**: `apps/social/migrations/0002_social_follow.py`
- **Actions**:
  1. Create model `Follow` with `(follower, following, status, created_at)`.
  2. Add `UniqueConstraint(fields=["follower", "following"], name="unique_user_follow")`.
  3. Add `CheckConstraint(check=~Q(follower=models.F("following")), name="prevent_self_follow")`.
- **Dependencies**: `("social", "0001_initial")`.
- **Backward-Compatibility Evaluation**:
  - Independent table. Zero impact on website queries or auth checks.

### Phase 3: Posts & Workout Integration
**Target Migration**: `apps/social/migrations/0003_social_post_and_media.py`
- **Actions**:
  1. Create model `Post` with `author`, `post_type`, `caption`, `workout_session`, `visibility`, `likes_count`, `comments_count`.
  2. `workout_session` is `ForeignKey("diary.WorkoutSession", null=True, blank=True, on_delete=models.SET_NULL, related_name="social_shares")`.
  3. Create model `PostMedia` with `post`, `media_url`, `media_type`, `order`.
- **Dependencies**:
  - `("social", "0002_social_follow")`
  - `("diary", "0001_initial")` (Guarantees `diary_workoutsession` table exists prior to applying FK).
- **Backward-Compatibility Evaluation**:
  - `diary_workoutsession` is referenced purely as a read-only foreign target with `SET_NULL` on delete. If a user deletes a workout session in their private diary, the social post retains its caption and sets `workout_session_id = NULL`, avoiding cascades or corruption.

### Phase 4: Social Engagement (Likes & Comments)
**Target Migration**: `apps/social/migrations/0004_social_engagement.py`
- **Actions**:
  1. Create model `Like` with `(user, post, created_at)` and `UniqueConstraint(fields=["user", "post"])`.
  2. Create model `Comment` with `(post, author, parent, content, created_at, updated_at)`.
- **Dependencies**: `("social", "0003_social_post_and_media")`.
- **Backward-Compatibility Evaluation**:
  - Self-contained in social app. Zero website impact.

### Phase 5: Notifications & Community Challenges
**Target Migration**: `apps/social/migrations/0005_notifications_and_challenges.py`
- **Actions**:
  1. Create `Notification` table for social events.
  2. Create `Challenge` and `ChallengeParticipant` tables.
- **Dependencies**: `("social", "0004_social_engagement")`.

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
