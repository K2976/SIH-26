# FitForge Entity-Relationship Diagram (ERD)

## Architecture Overview

This diagram represents both the **existing FitForge database schema** (currently running in Neon PostgreSQL) and the **proposed social extension** for the Android application.

- **Existing Tables**: Kept 100% intact to guarantee backward compatibility with the website.
- **New Tables (`social_*`)**: Added in a modular fashion to support Android mobile social features without touching legacy tables.

---

## Mermaid Entity-Relationship Diagram

```mermaid
erDiagram
    %% ========================================================
    %% EXISTING AUTH & ACCOUNTS DOMAIN
    %% ========================================================
    auth_user {
        int id PK
        varchar username UK "Email address in legacy website"
        varchar email
        varchar password
        boolean is_active
        boolean is_staff
        timestamptz date_joined
    }

    accounts_userprofile {
        bigint id PK
        int user_id FK,UK "1-to-1 with auth_user"
        date date_of_birth
        varchar sex_at_birth
        numeric height_cm
        numeric current_weight_kg
        numeric waist_cm
        smallint resting_hr
        varchar timezone
        varchar unit_preference
        varchar activity_level
        boolean tracks_cycle
        jsonb declined_fields
        timestamptz onboarding_completed_at
    }

    accounts_goal {
        bigint id PK
        int user_id FK
        varchar energy_direction
        varchar training_focus
        numeric target_rate_kg_per_week
        boolean auto_adjust
        timestamptz started_at
        timestamptz ended_at
    }

    %% ========================================================
    %% EXISTING REFERENCE DOMAIN
    %% ========================================================
    reference_exercise {
        bigint id PK
        varchar slug UK "Natural key"
        jsonb name
        jsonb instructions
        jsonb primary_muscles
        varchar equipment
        varchar movement_pattern
        numeric met_value
        boolean is_bodyweight
        varchar camera_position
        boolean is_active
        int updated_at_version
    }

    %% ========================================================
    %% EXISTING DIARY DOMAIN
    %% ========================================================
    diary_workoutsession {
        bigint id PK
        int user_id FK
        uuid client_uuid "UK with user_id"
        timestamptz occurred_at
        varchar utc_offset
        varchar source
        int duration_minutes
        text notes
        bigint planned_session_id FK
        bigint supersedes_id FK
    }

    diary_setlog {
        bigint id PK
        bigint session_id FK
        varchar exercise_id FK "References reference_exercise.slug"
        int user_id FK
        uuid client_uuid "UK with user_id"
        int set_index
        int reps
        numeric weight_kg
        numeric rpe
        boolean is_warmup
        varchar rom_achieved
        int tempo_ms
    }

    diary_meallog {
        bigint id PK
        int user_id FK
        uuid client_uuid "UK with user_id"
        timestamptz occurred_at
        varchar utc_offset
        bigint supersedes_id FK
    }

    diary_mealitem {
        bigint id PK
        bigint log_id FK
        int user_id FK
        uuid client_uuid
        varchar food_slug
        numeric quantity
        numeric calories
        numeric protein_g
        numeric carbs_g
        numeric fat_g
    }

    diary_weightlog {
        bigint id PK
        int user_id FK
        uuid client_uuid "UK with user_id"
        timestamptz occurred_at
        numeric weight_kg
    }

    diary_sleeplog {
        bigint id PK
        int user_id FK
        uuid client_uuid "UK with user_id"
        timestamptz bedtime
        timestamptz wake
        smallint quality
    }

    diary_cyclelog {
        bigint id PK
        int user_id FK
        uuid client_uuid "UK with user_id"
        date date
        varchar event
        varchar flow
        jsonb symptoms
        smallint energy_rating
    }

    engine_dailystate {
        bigint id PK
        int user_id FK "UK with local_date"
        date local_date "UK with user_id"
        timestamptz computed_at
        float consumed_kcal
        float burned_kcal
        float maintenance_kcal
        float target_kcal
    }

    %% ========================================================
    %% SOCIAL & COMMUNITY DOMAIN (apps/social)
    %% ========================================================
    social_profile {
        bigint id PK
        int user_id FK,UK "1-to-1 with auth_user [PHASE 1 - COMPLETED]"
        varchar username UK "Public handle e.g. @iron_lifter"
        varchar display_name
        text bio
        varchar profile_image
        boolean is_private
        timestamptz created_at
        timestamptz updated_at
    }

    social_community {
        bigint id PK
        int owner_id FK "auth_user.id [PHASE 2]"
        varchar name "e.g. Iron Gym 6AM Club"
        varchar slug UK "URL-safe identifier"
        text description
        varchar avatar_url
        varchar banner_url
        varchar visibility "public, private, invite_only"
        int members_count
        timestamptz created_at
        timestamptz updated_at
    }

    social_communitymembership {
        bigint id PK
        bigint community_id FK "social_community.id [PHASE 2]"
        int user_id FK "auth_user.id [PHASE 2]"
        varchar role "owner, admin, member"
        varchar status "active, pending, banned"
        timestamptz joined_at
    }

    social_challenge {
        bigint id PK
        bigint community_id FK "social_community.id (nullable for platform challenges) [PHASE 3]"
        int creator_id FK "auth_user.id [PHASE 3]"
        varchar title "e.g. 30-Day Consistency Challenge"
        text description
        varchar challenge_type "workout_count, volume_kg, step_count, days"
        numeric target_value
        varchar target_unit
        date start_date
        date end_date
        varchar status "upcoming, active, completed, cancelled"
        int participants_count
        timestamptz created_at
        timestamptz updated_at
    }

    social_challengeparticipation {
        bigint id PK
        bigint challenge_id FK "social_challenge.id [PHASE 3]"
        int user_id FK "auth_user.id [PHASE 3]"
        varchar status "active, completed, abandoned"
        numeric current_progress "Derived from logged workouts"
        timestamptz completed_at
        timestamptz joined_at
        timestamptz updated_at
    }

    social_userstreak {
        bigint id PK
        int user_id FK,UK "auth_user.id [PHASE 4 - Server-derived]"
        int current_streak_days "Continuous verified days"
        int longest_streak_days "All-time PR"
        date last_activity_date "Date of last valid logged workout"
        int streak_freeze_credits
        timestamptz updated_at
    }

    social_notification {
        bigint id PK
        int recipient_id FK "auth_user.id [PHASE 6]"
        int actor_id FK "auth_user.id (nullable)"
        varchar notification_type "challenge_invite, streak_alert, etc."
        bigint community_id FK "Nullable"
        bigint challenge_id FK "Nullable"
        varchar title
        text message
        boolean is_read
        timestamptz created_at
    }

    %% ========================================================
    %% RELATIONSHIPS
    %% ========================================================
    %% Existing relationships
    auth_user ||--|| accounts_userprofile : "has private profile"
    auth_user ||--o{ accounts_goal : "defines"
    auth_user ||--o{ diary_workoutsession : "performs"
    auth_user ||--o{ diary_meallog : "logs meals"
    auth_user ||--o{ diary_weightlog : "logs weight"
    auth_user ||--o{ diary_sleeplog : "logs sleep"
    auth_user ||--o{ diary_cyclelog : "tracks cycle"
    auth_user ||--o{ engine_dailystate : "has daily state"

    diary_workoutsession ||--o{ diary_setlog : "contains"
    reference_exercise ||--o{ diary_setlog : "performed in"
    diary_meallog ||--o{ diary_mealitem : "contains"

    %% Social Domain (Phase 1)
    auth_user ||--o| social_profile : "has public profile [PHASE 1 - COMPLETED]"

    %% Community Domain (Phase 2)
    auth_user ||--o{ social_community : "owns [PHASE 2]"
    auth_user ||--o{ social_communitymembership : "joins [PHASE 2]"
    social_community ||--o{ social_communitymembership : "has members [PHASE 2]"

    %% Challenge Domain (Phase 3 & 5)
    social_community ||--o{ social_challenge : "hosts [PHASE 3/5]"
    auth_user ||--o{ social_challenge : "originates [PHASE 3]"
    auth_user ||--o{ social_challengeparticipation : "enters [PHASE 3]"
    social_challenge ||--o{ social_challengeparticipation : "enrolls [PHASE 3]"

    %% Streak & Accountability (Phase 4)
    auth_user ||--o| social_userstreak : "earns [PHASE 4]"

    %% Notifications (Phase 6)
    auth_user ||--o{ social_notification : "receives [PHASE 6]"

    %% Cross-Domain Derivation (Server Logic — NO duplicate tables)
    diary_workoutsession ..> social_challengeparticipation : "server computes challenge progress"
    diary_workoutsession ..> social_userstreak : "server derives active streak"
```

---

## Architectural Distinctions & Deferred Scope

| Domain | Entity | Scope / Access | Primary Purpose |
| :--- | :--- | :--- | :--- |
| **Existing Accounts** | `accounts_userprofile` | **Private** | Biometrics, DOB, onboarding flags, engine configs. |
| **Social (Phase 1)** | `social_profile` | **Public** | Public `@username` handle, bio, display name, avatar. |
| **Community (Phase 2)**| `social_community` | **Community** | Groups, gyms, clubs, hostels for collective accountability. |
| **Challenges (Phase 3)**| `social_challenge` | **Community/Global** | Measurable fitness goals (volume, sessions, consistency). |
| **Streaks (Phase 4)** | `social_userstreak` | **Personal** | Server-verified consecutive fitness habits. |
| **Deferred Features** | Posts, Likes, Comments, Reels, DMs | **Deferred** | Kept out of scope to prevent social media bloat. |
