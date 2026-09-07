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

## 3. Immediate Additions: Phase 1 — Social Profile Domain

All new tables will reside in a dedicated Django app: **`apps/social/`** (table prefix `social_`).

### 3.1 [ADD] `social_profile`
Dedicated public identity table linked 1-to-1 with `auth_user`.

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `bigint` | PK, Auto Increment | Primary key |
| `user_id` | `integer` | FK -> `auth_user.id`, UNIQUE, NOT NULL | OneToOne relationship to Django User |
| `username` | `varchar(32)` | UNIQUE, NOT NULL, INDEX | Public unique handle (e.g., `alex_lifts`). Lowercase alphanumeric + underscores. |
| `display_name` | `varchar(64)` | NOT NULL | User's public display name (e.g. `Alex Rivera`) |
| `bio` | `text` | NOT NULL, Default `""` | User self-description or motto |
| `profile_image` | `varchar(255)` | NULL | Avatar image URL / path |
| `is_private` | `boolean` | NOT NULL, Default `false` | Whether posts require follow approval |
| `created_at` | `timestamptz` | NOT NULL, Default `now()` | Timestamp of profile creation |
| `updated_at` | `timestamptz` | NOT NULL, Default `now()` | Timestamp of last modification |

**Indexes**:
- `social_profile_username_idx` on `(username)`: Fast lookup for search, mentions, and route handling.
- `social_profile_user_id_idx` on `(user_id)`: Instant resolution from authenticated session.

---

## 4. Progressive Additions: Phase 2 — Social Graph (Follow/Unfollow)

### 4.1 [ADD] `social_follow`
Directed graph of user follow relationships.

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `bigint` | PK, Auto Increment | Primary key |
| `follower_id` | `integer` | FK -> `auth_user.id` ON DELETE CASCADE, NOT NULL | User initiating the follow |
| `following_id` | `integer` | FK -> `auth_user.id` ON DELETE CASCADE, NOT NULL | User being followed |
| `status` | `varchar(16)` | NOT NULL, Default `'accepted'` | Choices: `'accepted'`, `'pending'` (for private accounts) |
| `created_at` | `timestamptz` | NOT NULL, Default `now()` | Follow timestamp |

**Constraints & Indexes**:
- UNIQUE `(follower_id, following_id)`: Prevents duplicate follow links.
- CHECK `(follower_id != following_id)`: Prevents self-following.
- Index on `(following_id, status)`: Fast followers list querying.
- Index on `(follower_id, status)`: Fast following list querying.

---

## 5. Progressive Additions: Phase 3 — Posts & Workout Sharing

### 5.1 [ADD] `social_post`
Feed posts supporting general text/media updates and rich workout highlights.

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `bigint` | PK, Auto Increment | Primary key |
| `author_id` | `integer` | FK -> `auth_user.id` ON DELETE CASCADE, NOT NULL | Post creator |
| `post_type` | `varchar(20)` | NOT NULL, Default `'general'` | Choices: `'general'`, `'workout_share'`, `'pr_achievement'`, `'milestone'` |
| `caption` | `text` | NOT NULL, Default `""` | User post text |
| `workout_session_id` | `bigint` | FK -> `diary_workoutsession.id` ON DELETE SET NULL, NULL | **Reuses existing workout session data** |
| `visibility` | `varchar(16)` | NOT NULL, Default `'public'` | Choices: `'public'`, `'followers_only'`, `'private'` |
| `likes_count` | `integer` | NOT NULL, Default `0` | Denormalized count for performant feed reads |
| `comments_count` | `integer` | NOT NULL, Default `0` | Denormalized count for performant feed reads |
| `created_at` | `timestamptz` | NOT NULL, Default `now()`, INDEX | Feed sorting |
| `updated_at` | `timestamptz` | NOT NULL, Default `now()` | Edit tracking |

> **Key Architectural Pattern**: `workout_session_id` links directly to `diary_workoutsession.id`. When displaying a workout post, the API queries sets from `diary_setlog` associated with that session. This eliminates data duplication and keeps the source of truth in the existing diary domain.

### 5.2 [ADD] `social_postmedia`
Media attachments (images/videos) for posts.

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `bigint` | PK, Auto Increment | Primary key |
| `post_id` | `bigint` | FK -> `social_post.id` ON DELETE CASCADE, NOT NULL | Associated post |
| `media_url` | `varchar(512)` | NOT NULL | File storage URL |
| `media_type` | `varchar(16)` | NOT NULL | Choices: `'image'`, `'video'` |
| `order` | `smallint` | NOT NULL, Default `0` | Display carousel order |
| `created_at` | `timestamptz` | NOT NULL, Default `now()` | Upload timestamp |

---

## 6. Progressive Additions: Phase 4 — Social Engagement

### 6.1 [ADD] `social_like`
User likes on posts.

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `bigint` | PK, Auto Increment | Primary key |
| `user_id` | `integer` | FK -> `auth_user.id` ON DELETE CASCADE, NOT NULL | User who liked |
| `post_id` | `bigint` | FK -> `social_post.id` ON DELETE CASCADE, NOT NULL | Liked post |
| `created_at` | `timestamptz` | NOT NULL, Default `now()` | Timestamp |

**Constraints & Indexes**:
- UNIQUE `(user_id, post_id)`: Prevents duplicate likes.
- Index on `(post_id)`: Fast like enumeration.

### 6.2 [ADD] `social_comment`
Threaded comments on posts.

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `bigint` | PK, Auto Increment | Primary key |
| `post_id` | `bigint` | FK -> `social_post.id` ON DELETE CASCADE, NOT NULL | Target post |
| `author_id` | `integer` | FK -> `auth_user.id` ON DELETE CASCADE, NOT NULL | Commenter |
| `parent_id` | `bigint` | FK -> `social_comment.id` ON DELETE CASCADE, NULL | Parent comment for nested threads |
| `content` | `text` | NOT NULL | Comment body |
| `created_at` | `timestamptz` | NOT NULL, Default `now()` | Creation timestamp |
| `updated_at` | `timestamptz` | NOT NULL, Default `now()` | Edit timestamp |

**Indexes**:
- Index on `(post_id, created_at)`: Fast ordered comment retrieval.

---

## 7. Progressive Additions: Phase 5 — Notifications & Challenges

### 7.1 [ADD] `social_notification`
In-app activity notifications.
- `id`: `bigint` PK
- `recipient_id`: `integer` FK -> `auth_user.id`
- `actor_id`: `integer` FK -> `auth_user.id`
- `notification_type`: `varchar(32)` (`like`, `comment`, `follow`, `challenge_invite`, `pr_shoutout`)
- `post_id`: `bigint` FK -> `social_post.id` (NULL)
- `comment_id`: `bigint` FK -> `social_comment.id` (NULL)
- `is_read`: `boolean` Default `false`
- `created_at`: `timestamptz` Default `now()`

### 7.2 [ADD] `social_challenge` & `social_challengeparticipant`
Community fitness challenges.
- `social_challenge`: `id`, `creator_id`, `title`, `description`, `challenge_type` (`volume`, `workouts_count`, `streak`), `target_value`, `start_date`, `end_date`, `created_at`.
- `social_challengeparticipant`: `id`, `challenge_id`, `user_id`, `current_progress`, `completed`, `joined_at`.

---

## 8. Backward Compatibility Impact Matrix

| Component | Risk Level | Compatibility Protection Mechanism |
| :--- | :--- | :--- |
| **Website Auth** | **Zero Risk** | `auth_user` model and fields untouched. No changes to `username` / `email` handling. |
| **Website API Contracts** | **Zero Risk** | All new social endpoints are mounted under new URLs (`/api/v1/social/`). Old endpoints (`/api/v1/auth/`, `/api/v1/profile/`, etc.) remain identical. |
| **Neon Database Stability** | **Zero Risk** | Additive tables only. No modifications, column drops, type changes, or constraint alterations on existing tables. |
| **Existing Migrations** | **Zero Risk** | New migrations will be isolated in `apps/social/migrations/` and will depend on `auth.0012_alter_user_first_name_max_length` and `diary.0001_initial`. |
