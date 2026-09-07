# FitForge Current Database Schema Audit

## Overview

As verified by direct introspection of the active Neon PostgreSQL database (`ep-late-art-ax4bpg64-pooler.c-4.us-east-2.aws.neon.tech/neondb`), the database currently consists of **25 tables**.

These tables fall into two distinct categories:
1. **Django Infrastructure & Authentication Tables (13 tables)**
2. **FitForge Application Tables (12 tables)**

---

## 1. Django Infrastructure & Authentication Tables

### 1.1 `auth_user`
Standard Django user model storing credentials and account status.
- **`id`**: `integer` | PK | NOT NULL
- **`password`**: `varchar(128)` | NOT NULL
- **`last_login`**: `timestamptz` | NULL
- **`is_superuser`**: `boolean` | NOT NULL | Default `false`
- **`username`**: `varchar(150)` | UNIQUE | NOT NULL
  > **CRITICAL ARCHITECTURAL NOTE**: In FitForge, `username` is assigned the user's email address during registration (e.g., `user@example.com`). The web client and authentication layer assume `auth_user.username == auth_user.email`.
- **`first_name`**: `varchar(150)` | NOT NULL | Default `""`
- **`last_name`**: `varchar(150)` | NOT NULL | Default `""`
- **`email`**: `varchar(254)` | NOT NULL
- **`is_staff`**: `boolean` | NOT NULL | Default `false`
- **`is_active`**: `boolean` | NOT NULL | Default `true`
- **`date_joined`**: `timestamptz` | NOT NULL

### 1.2 `auth_group`, `auth_permission`, `auth_group_permissions`, `auth_user_groups`, `auth_user_user_permissions`
Standard Django RBAC permission tables:
- `auth_group`: `id` (PK), `name` (UNIQUE)
- `auth_permission`: `id` (PK), `name`, `content_type_id` (FK -> `django_content_type`), `codename` (UNIQUE combo)
- `auth_group_permissions`: `id` (PK), `group_id` (FK), `permission_id` (FK), UNIQUE `(group_id, permission_id)`
- `auth_user_groups`: `id` (PK), `user_id` (FK -> `auth_user`), `group_id` (FK -> `auth_group`), UNIQUE `(user_id, group_id)`
- `auth_user_user_permissions`: `id` (PK), `user_id` (FK -> `auth_user`), `permission_id` (FK -> `auth_permission`), UNIQUE `(user_id, permission_id)`

### 1.3 `django_content_type`, `django_migrations`, `django_session`, `django_admin_log`
Django framework state management:
- `django_content_type`: `id` (PK), `app_label`, `model`, UNIQUE `(app_label, model)`
- `django_migrations`: `id` (PK), `app`, `name`, `applied` (timestamptz)
- `django_session`: `session_key` (PK), `session_data` (text), `expire_date` (timestamptz, indexed)
- `django_admin_log`: `id` (PK), `action_time`, `object_id`, `object_repr`, `action_flag`, `change_message`, `content_type_id` (FK), `user_id` (FK -> `auth_user`)

### 1.4 `token_blacklist_outstandingtoken` & `token_blacklist_blacklistedtoken`
`rest_framework_simplejwt` token lifecycle tables:
- `token_blacklist_outstandingtoken`: `id` (PK), `user_id` (FK -> `auth_user`), `jti` (varchar UNIQUE), `token` (text), `created_at`, `expires_at`
- `token_blacklist_blacklistedtoken`: `id` (PK), `token_id` (FK -> `token_blacklist_outstandingtoken` UNIQUE), `blacklisted_at` (timestamptz)

---

## 2. FitForge Application Tables

### 2.1 Accounts Domain (`apps/accounts`)

#### `accounts_userprofile`
Extended user demographic, biometrics, preferences, and onboarding progress.
- **`id`**: `bigint` | PK | NOT NULL
- **`user_id`**: `integer` | FK -> `auth_user.id` ON DELETE CASCADE | UNIQUE
- **`date_of_birth`**: `date` | NULL
- **`sex_at_birth`**: `varchar(16)` | NULL (Choices: `male`, `female`, `intersex`)
- **`height_cm`**: `numeric(5, 1)` | NULL
- **`current_weight_kg`**: `numeric(5, 1)` | NULL
- **`waist_cm`**: `numeric(5, 1)` | NULL
- **`resting_hr`**: `smallint` | NULL
- **`timezone`**: `varchar(64)` | NOT NULL | Default `'UTC'`
- **`unit_preference`**: `varchar(16)` | NOT NULL | Default `'metric'` (Choices: `metric`, `imperial`)
- **`activity_level`**: `varchar(20)` | NULL (Choices: `sedentary`, `lightly_active`, `moderately_active`, `very_active`, `extra_active`)
- **`tracks_cycle`**: `boolean` | NULL (Explicit opt-in; never inferred)
- **`declined_fields`**: `jsonb` | NOT NULL | Default `'[]'` (Supports three-state optional fields: *known*, *not_asked*, *declined*)
- **`onboarding_completed_at`**: `timestamptz` | NULL

#### `accounts_goal`
Versioned user energy direction and training targets. Goals are append-only; active goals have `ended_at IS NULL`.
- **`id`**: `bigint` | PK | NOT NULL
- **`user_id`**: `integer` | FK -> `auth_user.id` ON DELETE CASCADE | NOT NULL
- **`energy_direction`**: `varchar(16)` | NOT NULL (Choices: `cut`, `maintain`, `gain`, `not_tracking`)
- **`training_focus`**: `varchar(16)` | NOT NULL (Choices: `strength`, `hypertrophy`, `endurance`, `general`)
- **`target_rate_kg_per_week`**: `numeric(4, 2)` | NOT NULL | Default `0.25`
- **`auto_adjust`**: `boolean` | NOT NULL | Default `true`
- **`started_at`**: `timestamptz` | NOT NULL | Default `timezone.now`
- **`ended_at`**: `timestamptz` | NULL (Active goal indicator)
- **Indexes**:
  - `(user_id, ended_at)`

---

### 2.2 Reference Domain (`apps/reference`)

#### `reference_referenceversion`
Single-row global version counter bumped atomically whenever any reference row changes.
- **`id`**: `bigint` | PK | NOT NULL
- **`value`**: `integer` | NOT NULL | Default `0`

#### `reference_exercise`
Canonical exercise library containing movement mechanics, MET values, and localized descriptions.
- **`id`**: `bigint` | PK | NOT NULL
- **`slug`**: `varchar(64)` | UNIQUE | NOT NULL (Natural key referenced by diary logs)
- **`name`**: `jsonb` | NOT NULL (Locale-keyed dict: `{"en": "Squat", "hi": "..."}`)
- **`instructions`**: `jsonb` | NOT NULL
- **`primary_muscles`**: `jsonb` | NOT NULL | Default `'[]'`
- **`secondary_muscles`**: `jsonb` | NOT NULL | Default `'[]'`
- **`equipment`**: `varchar(64)` | NOT NULL | Default `""`
- **`movement_pattern`**: `varchar(32)` | NOT NULL (Choices: `squat`, `hinge`, `lunge`, `push_horizontal`, `push_vertical`, `pull_horizontal`, `pull_vertical`, `carry`, `core`, `isolation`, `plyometric`, `cardio`)
- **`form_cues`**: `jsonb` | NOT NULL | Default `'[]'`
- **`met_value`**: `numeric(3, 1)` | NOT NULL (Required for burn estimation)
- **`is_bodyweight`**: `boolean` | NOT NULL | Default `false`
- **`pose_config`**: `jsonb` | NULL
- **`camera_position`**: `varchar(16)` | NULL (Choices: `front`, `side`, `45_degree`)
- **`is_active`**: `boolean` | NOT NULL | Default `true` (Soft delete only)
- **`updated_at_version`**: `integer` | NOT NULL | Default `0` (Indexed for delta sync)

---

### 2.3 Diary Domain (`apps/diary`)

All diary tables inherit from an abstract `DiaryEntry` contract enforcing:
1. **Offline-first client idempotency**: UNIQUE `(user_id, client_uuid)`
2. **Immutable history & corrections**: `supersedes_id` self-referential foreign key
3. **Local timezone tracking**: `occurred_at` (timestamptz) paired with `utc_offset` (e.g. `+05:30`)

#### `diary_plannedsession`
- **`id`**: `bigint` | PK | NOT NULL
- **`user_id`**: `integer` | FK -> `auth_user.id` | NOT NULL
- **`client_uuid`**: `uuid` | NOT NULL | UNIQUE with `user_id`
- **`occurred_at`**: `timestamptz` | NOT NULL
- **`utc_offset`**: `varchar` | NOT NULL
- **`source`**: `varchar` | NOT NULL (`generated` or `user_created`)
- **`created_at`**: `timestamptz` | NOT NULL
- **`supersedes_id`**: `bigint` | FK -> `diary_plannedsession.id` | NULL
- **`date`**: `date` | NOT NULL
- **`focus`**: `varchar` | NOT NULL
- **`planned_volume`**: `jsonb` | NOT NULL
- **`status`**: `varchar` | NOT NULL (`pending`, `completed`, `skipped`, `moved`)

#### `diary_workoutsession`
- **`id`**: `bigint` | PK | NOT NULL
- **`user_id`**: `integer` | FK -> `auth_user.id` | NOT NULL
- **`client_uuid`**: `uuid` | NOT NULL | UNIQUE with `user_id`
- **`occurred_at`**: `timestamptz` | NOT NULL
- **`utc_offset`**: `varchar` | NOT NULL
- **`source`**: `varchar` | NOT NULL
- **`created_at`**: `timestamptz` | NOT NULL
- **`supersedes_id`**: `bigint` | FK -> `diary_workoutsession.id` | NULL
- **`planned_session_id`**: `bigint` | FK -> `diary_plannedsession.id` | NULL
- **`duration_minutes`**: `integer` | NULL
- **`notes`**: `text` | NOT NULL | Default `""`

#### `diary_setlog`
- **`id`**: `bigint` | PK | NOT NULL
- **`user_id`**: `integer` | FK -> `auth_user.id` | NOT NULL
- **`client_uuid`**: `uuid` | NOT NULL | UNIQUE with `user_id`
- **`occurred_at`**: `timestamptz` | NOT NULL
- **`utc_offset`**: `varchar` | NOT NULL
- **`source`**: `varchar` | NOT NULL
- **`created_at`**: `timestamptz` | NOT NULL
- **`supersedes_id`**: `bigint` | FK -> `diary_setlog.id` | NULL
- **`session_id`**: `bigint` | FK -> `diary_workoutsession.id` ON DELETE CASCADE | NOT NULL
- **`exercise_id`**: `varchar` | FK -> `reference_exercise.slug` | NOT NULL
- **`set_index`**: `integer` | NOT NULL
- **`reps`**: `integer` | NOT NULL
- **`weight_kg`**: `numeric` | NULL
- **`rpe`**: `numeric` | NULL
- **`is_warmup`**: `boolean` | NOT NULL | Default `false`
- **`rom_achieved`**: `varchar` | NULL
- **`tempo_ms`**: `integer` | NULL

#### `diary_meallog`
- **`id`**: `bigint` | PK | NOT NULL
- **`user_id`**: `integer` | FK -> `auth_user.id` | NOT NULL
- **`client_uuid`**: `uuid` | NOT NULL | UNIQUE with `user_id`
- **`occurred_at`**: `timestamptz` | NOT NULL
- **`utc_offset`**: `varchar` | NOT NULL
- **`source`**: `varchar` | NOT NULL
- **`created_at`**: `timestamptz` | NOT NULL
- **`supersedes_id`**: `bigint` | FK -> `diary_meallog.id` | NULL

#### `diary_mealitem`
- **`id`**: `bigint` | PK | NOT NULL
- **`user_id`**: `integer` | FK -> `auth_user.id` | NOT NULL
- **`client_uuid`**: `uuid` | NOT NULL | UNIQUE with `user_id`
- **`occurred_at`**: `timestamptz` | NOT NULL
- **`utc_offset`**: `varchar` | NOT NULL
- **`source`**: `varchar` | NOT NULL
- **`created_at`**: `timestamptz` | NOT NULL
- **`supersedes_id`**: `bigint` | FK -> `diary_mealitem.id` | NULL
- **`log_id`**: `bigint` | FK -> `diary_meallog.id` ON DELETE CASCADE | NOT NULL
- **`food_slug`**: `varchar` | NOT NULL
- **`quantity`**: `numeric` | NOT NULL
- **`calories`**: `numeric` | NOT NULL
- **`protein_g`**: `numeric` | NOT NULL
- **`carbs_g`**: `numeric` | NOT NULL
- **`fat_g`**: `numeric` | NOT NULL

#### `diary_weightlog`
- **`id`**: `bigint` | PK | NOT NULL
- **`user_id`**: `integer` | FK -> `auth_user.id` | NOT NULL
- **`client_uuid`**: `uuid` | NOT NULL | UNIQUE with `user_id`
- **`occurred_at`**: `timestamptz` | NOT NULL
- **`utc_offset`**: `varchar` | NOT NULL
- **`source`**: `varchar` | NOT NULL
- **`created_at`**: `timestamptz` | NOT NULL
- **`supersedes_id`**: `bigint` | FK -> `diary_weightlog.id` | NULL
- **`weight_kg`**: `numeric` | NOT NULL

#### `diary_sleeplog`
- **`id`**: `bigint` | PK | NOT NULL
- **`user_id`**: `integer` | FK -> `auth_user.id` | NOT NULL
- **`client_uuid`**: `uuid` | NOT NULL | UNIQUE with `user_id`
- **`occurred_at`**: `timestamptz` | NOT NULL
- **`utc_offset`**: `varchar` | NOT NULL
- **`source`**: `varchar` | NOT NULL
- **`created_at`**: `timestamptz` | NOT NULL
- **`supersedes_id`**: `bigint` | FK -> `diary_sleeplog.id` | NULL
- **`bedtime`**: `timestamptz` | NOT NULL
- **`wake`**: `timestamptz` | NOT NULL
- **`quality`**: `smallint` | NULL (1 to 5 scale)

#### `diary_cyclelog`
- **`id`**: `bigint` | PK | NOT NULL
- **`user_id`**: `integer` | FK -> `auth_user.id` | NOT NULL
- **`client_uuid`**: `uuid` | NOT NULL | UNIQUE with `user_id`
- **`occurred_at`**: `timestamptz` | NOT NULL
- **`utc_offset`**: `varchar` | NOT NULL
- **`source`**: `varchar` | NOT NULL
- **`created_at`**: `timestamptz` | NOT NULL
- **`supersedes_id`**: `bigint` | FK -> `diary_cyclelog.id` | NULL
- **`date`**: `date` | NOT NULL
- **`event`**: `varchar` | NOT NULL
- **`flow`**: `varchar` | NOT NULL
- **`symptoms`**: `jsonb` | NOT NULL | Default `'[]'`
- **`energy_rating`**: `smallint` | NULL (1 to 5 scale)

---

### 2.4 Adaptive Engine Domain (`apps/engine`)

#### `engine_dailystate`
Precomputed daily summary snapshot powering single-payload reads (`UserState`) by client apps.
- **`id`**: `bigint` | PK | NOT NULL
- **`user_id`**: `integer` | FK -> `auth_user.id` | NOT NULL
- **`local_date`**: `date` | NOT NULL
- **`computed_at`**: `timestamptz` | NOT NULL
- **`consumed_kcal`**: `double precision` | NULL
- **`burned_kcal`**: `double precision` | NULL
- **`maintenance_kcal`**: `double precision` | NULL
- **`maintenance_source`**: `varchar` | NULL
- **`maintenance_confidence`**: `double precision` | NULL
- **`target_kcal`**: `double precision` | NULL
- **`week_balance_kcal`**: `double precision` | NULL
- **`week_volume_planned_kg`**: `double precision` | NULL
- **`week_volume_completed_kg`**: `double precision` | NULL
- **`volume_by_muscle_group`**: `jsonb` | NULL
- **`adherence_7d`**: `double precision` | NULL
- **`days_since_rest`**: `integer` | NULL
- **`weight_trend_kg_per_week`**: `double precision` | NULL
- **`weight_trend_confidence`**: `double precision` | NULL
- **`missing_food_days_7d`**: `smallint` | NOT NULL
- **`missing_weight_days_14d`**: `smallint` | NOT NULL
- **`goal_recently_changed`**: `boolean` | NOT NULL
- **`insufficient_data_for_adaptation`**: `boolean` | NOT NULL
- **`energy_data_sufficiency`**: `varchar` | NOT NULL
- **`partial_fields`**: `jsonb` | NOT NULL
- **Constraints & Indexes**:
  - UNIQUE `(user_id, local_date)` (`unique_daily_state_per_day`)
  - Index on `(user_id, local_date)`

---

## 3. Social Media Extension (Phase 1 Addition)

### 3.1 Social Profile Domain (`apps/social`)

#### `social_profile`
Public social identity table extending `auth_user` without mutating existing website auth.
- **`id`**: `bigint` | PK | NOT NULL
- **`user_id`**: `integer` | FK -> `auth_user.id` ON DELETE CASCADE | UNIQUE
- **`username`**: `varchar(32)` | UNIQUE | NOT NULL (Indexed)
- **`display_name`**: `varchar(64)` | NOT NULL
- **`bio`**: `text` | NOT NULL | Default `""`
- **`profile_image`**: `varchar(100)` | NULL
- **`is_private`**: `boolean` | NOT NULL | Default `false`
- **`created_at`**: `timestamptz` | NOT NULL
- **`updated_at`**: `timestamptz` | NOT NULL

**User Profile Dual Architecture**:
- `auth_user 1 ─── 1 accounts_userprofile`: Existing private demographic and physiological data (DOB, height, weight, waist, cycle tracking).
- `auth_user 1 ─── 1 social_profile`: New public-facing social handle, display name, bio, and avatar.

