# FitForge Proposed Database Changes (Backward-Compatible Design)

## Overview & Change Strategy

This document outlines the proposed schema additions to support the new Android application (social feeds, public profiles, workout sharing, follows, comments, and challenges) while maintaining strict **100% backward compatibility** with the existing website implementation.

### Legend:
- **[KEEP]**: Unchanged existing table/column. Preserves 100% website functionality.
- **[ADD]**: New table or new additive column. Does not impact existing queries.
- **[MODIFY]**: Schema or index modification (strictly avoided in this design to prevent regressions).

---

## 1. Existing Tables Audit: [KEEP] Policy

All 25 existing tables in the database are designated **[KEEP]**.

| Table | Status | Impact on Existing Website | Justification |
| :--- | :--- | :--- | :--- |
| `auth_user` | **[KEEP]** | None | The website login relies on `auth_user.username == auth_user.email`. Modifying this breaks auth across all web sessions. |
| `accounts_userprofile` | **[KEEP]** | None | Contains private user biometrics and onboarding settings. Private data must not be exposed to social features. |
| `accounts_goal` | **[KEEP]** | None | Private engine targets used by the adaptive recommendation loop. |
| `reference_exercise` | **[KEEP]** | None | Canonical exercise library. Reused directly by social posts via foreign keys. |
| `reference_referenceversion`| **[KEEP]** | None | Global version counter for delta-sync cache busting. |
| `diary_workoutsession` | **[KEEP]** | None | Existing workout history. Social workout posts will link to this table by FK rather than duplicating data. |
| `diary_setlog` | **[KEEP]** | None | Detailed set/rep performance. Retained for deep workout post rendering. |
| `diary_plannedsession` | **[KEEP]** | None | Training schedule management. |
| `diary_meallog` | **[KEEP]** | None | Private nutrition diary. |
| `diary_mealitem` | **[KEEP]** | None | Itemized macro entries. |
| `diary_weightlog` | **[KEEP]** | None | Private weigh-in history. |
| `diary_sleeplog` | **[KEEP]** | None | Private sleep logs. |
| `diary_cyclelog` | **[KEEP]** | None | Sensitive cycle health tracking; strictly protected and non-public. |
| `engine_dailystate` | **[KEEP]** | None | Calculated daily user state. |
| All Django Auth/Admin/Token Tables | **[KEEP]** | None | Framework infrastructure. |

---

## 2. Public Username & Email Decoupling Strategy

### The Architectural Conflict:
- **Current Website Architecture**:
  - `auth_user.username` = `user@example.com` (identical to `auth_user.email`).
  - `SignupSerializer` executes `User.objects.create_user(username=email, email=email)`.
  - `EmailTokenObtainPairSerializer` validates login using `email` and populates `username` internally.
- **New Mobile Social Architecture**:
  - Requires public `@handle` (e.g. `@iron_lifter_99`) for mentions, profiles, and feeds.
  - Exposing `auth_user.username` publicly would leak the user's private email address.

### The Backward-Compatible Solution:
Introduce a dedicated **`social_profile`** table.
- `auth_user.username` remains untouched, private, and equal to email.
- `social_profile.username` becomes the globally unique, public handle.
- Website continues its standard auth and user flow with zero code changes.
- Android app presents `social_profile.username` and `social_profile.display_name` across all social UI components.

---

## 3. Completed Additions: Phase 1 — Social Profile Domain

All social models reside in a dedicated Django app: **`apps/social/`** (table prefix `social_`).

### 3.1 [ADD] [COMPLETED] `social_profile`
Dedicated public identity table linked 1-to-1 with `auth_user`. **Already implemented, migrated to Neon, and verified.**

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `bigint` | PK, Auto Increment | Primary key |
| `user_id` | `integer` | FK -> `auth_user.id`, UNIQUE, NOT NULL | OneToOne relationship to Django User |
| `username` | `varchar(32)` | UNIQUE, NOT NULL, INDEX | Public unique handle (e.g., `alex_lifts`). Lowercase alphanumeric + underscores. |
| `display_name` | `varchar(64)` | NOT NULL | User's public display name (e.g. `Alex Rivera`) |
| `bio` | `text` | NOT NULL, Default `""` | User self-description or motto |
| `profile_image` | `varchar(255)` | NULL | Avatar image URL / path |
| `is_private` | `boolean` | NOT NULL, Default `false` | Privacy preference flag |
| `created_at` | `timestamptz` | NOT NULL, Default `now()` | Timestamp of profile creation |
| `updated_at` | `timestamptz` | NOT NULL, Default `now()` | Timestamp of last modification |

**Indexes & Constraints**:
- `social_profile_username_idx` on `(username)`: Fast lookup for search, mentions, and route handling.
- `social_profile_user_id_idx` on `(user_id)`: Instant resolution from authenticated session.
- Django validation constraint: Regex for alphanumeric + underscores, 3-30 chars, lowercase.

---

## 4. Completed Additions: Phase 2 — Communities Domain

FitForge focuses on **accountability groups** (gyms, college groups, hostel groups, sports clubs, friends circles).
Traditional open feeds and algorithmic timelines are **deferred**.

### 4.1 [ADD] [COMPLETED] `social_community`
Represents an accountability group or fitness community. **Implemented and migrated to Neon (`0002_community_communitymembership.py`).**

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `bigint` | PK, Auto Increment | Primary key |
| `owner_id` | `integer` | FK -> `auth_user.id` ON DELETE CASCADE, NOT NULL | Community creator and owner |
| `name` | `varchar(100)` | NOT NULL | Human-readable community name (e.g. `Iron Gym 6AM Club`) |
| `description` | `text` | NOT NULL, Default `""` | Community purpose, rules, and focus |
| `visibility` | `varchar(10)` | NOT NULL, Default `'public'` | Choices: `'public'`, `'private'` |
| `created_at` | `timestamptz` | NOT NULL, Default `now()` | Creation timestamp |
| `updated_at` | `timestamptz` | NOT NULL, Default `now()` | Last modification timestamp |

### 4.2 [ADD] [COMPLETED] `social_communitymembership`
Manages user membership, roles, and admission status in communities. **Implemented and migrated to Neon (`0002_community_communitymembership.py`).**

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `bigint` | PK, Auto Increment | Primary key |
| `community_id` | `bigint` | FK -> `social_community.id` ON DELETE CASCADE, NOT NULL | Target community |
| `user_id` | `integer` | FK -> `auth_user.id` ON DELETE CASCADE, NOT NULL | Member user |
| `role` | `varchar(10)` | NOT NULL, Default `'member'` | Choices: `'owner'`, `'admin'`, `'member'` |
| `joined_at` | `timestamptz` | NOT NULL, Default `now()` | Timestamp of joining |

**Constraints & Indexes**:
- UNIQUE `(community_id, user_id)`: **Guarantees a user cannot have duplicate memberships in the same community.**
- Index on `(community_id, role)`: Fast query for member roster and role-based access checks.


---

## 5. Upcoming Additions: Phase 3 — Challenges & Participation Domain

Challenges are goal-oriented fitness endeavors that drive accountability within communities or across the platform.

### 5.1 [ADD] `social_challenge`
Defines the parameters, targets, and timeline of a fitness challenge.

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `bigint` | PK, Auto Increment | Primary key |
| `community_id` | `bigint` | FK -> `social_community.id` ON DELETE CASCADE, NULL | Community scope (NULL = Platform-wide challenge) |
| `creator_id` | `integer` | FK -> `auth_user.id` ON DELETE CASCADE, NOT NULL | Challenge originator |
| `title` | `varchar(128)` | NOT NULL | Title (e.g., `30-Day Workout Challenge`, `10k Steps Daily`) |
| `description` | `text` | NOT NULL, Default `""` | Rules, details, and incentives |
| `challenge_type` | `varchar(32)` | NOT NULL | Type: `'workout_count'`, `'volume_kg'`, `'step_count'`, `'consistency_days'`, `'exercise_reps'` |
| `target_value` | `numeric(12,2)`| NOT NULL | Target goal number (e.g. 30 sessions, 50000 kg, 10000 steps) |
| `target_unit` | `varchar(32)` | NOT NULL, Default `""` | Unit descriptor (e.g., `'workouts'`, `'kg'`, `'steps'`, `'days'`) |
| `start_date` | `date` | NOT NULL | Challenge start date |
| `end_date` | `date` | NOT NULL | Challenge end date |
| `status` | `varchar(16)` | NOT NULL, Default `'active'` | Choices: `'upcoming'`, `'active'`, `'completed'`, `'cancelled'` |
| `participants_count` | `integer`| NOT NULL, Default `0` | Denormalized count for listing views |
| `created_at` | `timestamptz` | NOT NULL, Default `now()` | Timestamp |
| `updated_at` | `timestamptz` | NOT NULL, Default `now()` | Timestamp |

**Indexes**:
- Index on `(community_id, status)`: Fast retrieval of active community challenges.
- Index on `(start_date, end_date, status)`: Schedule filtering and state transitions.

### 5.2 [ADD] `social_challengeparticipation`
Tracks an individual user's participation and progress in a challenge.

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `bigint` | PK, Auto Increment | Primary key |
| `challenge_id` | `bigint` | FK -> `social_challenge.id` ON DELETE CASCADE, NOT NULL | Target challenge |
| `user_id` | `integer` | FK -> `auth_user.id` ON DELETE CASCADE, NOT NULL | Participant |
| `status` | `varchar(16)` | NOT NULL, Default `'active'` | Choices: `'active'`, `'completed'`, `'abandoned'` |
| `current_progress`| `numeric(12,2)`| NOT NULL, Default `0.00` | Current derived progress |
| `completed_at` | `timestamptz` | NULL | Timestamp when target was achieved |
| `joined_at` | `timestamptz` | NOT NULL, Default `now()` | Enrollment timestamp |
| `updated_at` | `timestamptz` | NOT NULL, Default `now()` | Progress update timestamp |

**Constraints & Indexes**:
- UNIQUE `(challenge_id, user_id)`: **Guarantees a user cannot have duplicate participation records for the same challenge.**
- Index on `(user_id, status)`: Fast user dashboard lookup for "My Active Challenges".
- Index on `(challenge_id, current_progress)`: Fast community challenge leaderboard generation.

---

## 6. Upcoming Additions: Phase 4 — Streaks & Accountability Domain

Streaks represent continuous fitness habits and consistency over time.

### Core Architectural Principle:
- **Server-Derived Only**: Streaks are strictly computed by the backend from valid stored activity records (`diary_workoutsession`, logged events).
- **Client Integrity**: Android clients **never** submit arbitrary counters (e.g. `streak = 30`).
- **Missing Is Not Zero**: Missing a day may break a streak unless a freeze/grace rule applies, but historical logs are never erased.

### 6.1 [ADD] `social_userstreak`
Caches the server-verified streak state for high-performance reading on mobile dashboards.

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `bigint` | PK, Auto Increment | Primary key |
| `user_id` | `integer` | FK -> `auth_user.id` ON DELETE CASCADE, UNIQUE, NOT NULL | OneToOne relationship with User |
| `current_streak_days` | `integer` | NOT NULL, Default `0` | Active verified consecutive days |
| `longest_streak_days` | `integer` | NOT NULL, Default `0` | All-time highest streak record |
| `last_activity_date` | `date` | NULL | Date of most recent qualifying fitness activity |
| `streak_freeze_credits` | `integer` | NOT NULL, Default `0` | Available grace/freeze credits |
| `updated_at` | `timestamptz` | NOT NULL, Default `now()` | Recalculation timestamp |

---

## 7. Upcoming Additions: Phase 5 & 6 — Community-Wide Aggregations & Notifications

### 7.1 Community Challenge Rollup & Leaderboard
- Computed dynamically using Django aggregation over `social_challengeparticipation` records for members of `social_community`.
- No denormalized duplicate tables needed; relies on proper index on `(challenge_id, current_progress DESC)`.

### 7.2 [ADD] `social_notification` (Phase 6)
Lightweight accountability alerts and notifications.

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `bigint` | PK, Auto Increment | Primary key |
| `recipient_id` | `integer` | FK -> `auth_user.id` ON DELETE CASCADE, NOT NULL | Alert recipient |
| `actor_id` | `integer` | FK -> `auth_user.id` ON DELETE CASCADE, NULL | Triggering user (if any) |
| `notification_type` | `varchar(32)` | NOT NULL | Type: `'challenge_invite'`, `'challenge_completed'`, `'community_joined'`, `'streak_milestone'`, `'streak_warning'` |
| `community_id` | `bigint` | FK -> `social_community.id` ON DELETE CASCADE, NULL | Related community (if any) |
| `challenge_id` | `bigint` | FK -> `social_challenge.id` ON DELETE CASCADE, NULL | Related challenge (if any) |
| `title` | `varchar(128)` | NOT NULL | Alert title |
| `message` | `text` | NOT NULL | Alert message body |
| `is_read` | `boolean` | NOT NULL, Default `false` | Read receipt |
| `created_at` | `timestamptz` | NOT NULL, Default `now()` | Alert timestamp |

---

## 8. Deferred Features (Not In Scope)

To avoid bloat and maintain a lean, purpose-driven fitness platform, the following traditional social media features are **DEFERRED**:
- ❌ Feed posts (`social_post`, `social_postmedia`)
- ❌ Likes (`social_like`)
- ❌ Comments & threaded conversations (`social_comment`)
- ❌ Reels & video stories
- ❌ Direct messages (DMs)
- ❌ Algorithmic content recommendations

---

## 9. Backward Compatibility Impact Matrix

| Component | Risk Level | Compatibility Protection Mechanism |
| :--- | :--- | :--- |
| **Website Auth** | **Zero Risk** | `auth_user` model and fields untouched. No changes to `username` / `email` handling. |
| **Website API Contracts** | **Zero Risk** | All new social endpoints are mounted under new URLs (`/api/v1/social/`). Old endpoints (`/api/v1/auth/`, `/api/v1/profile/`, etc.) remain identical. |
| **Neon Database Stability** | **Zero Risk** | Additive tables only. No modifications, column drops, type changes, or constraint alterations on existing tables. |
| **Existing Migrations** | **Zero Risk** | New migrations will be isolated in `apps/social/migrations/` and will depend on `auth.0012_alter_user_first_name_max_length` and `diary.0001_initial`. |
