# Requirements Specification — FitForge Social Media Feature

## Sources & Upstream References
This specification derives from and maintains bidirectional traceability with the following upstream artifacts:
- `business-overview` (`aidlc/spaces/default/codekb/fitforge/business-overview.md`): Alignment with FitForge value proposition, multi-client ecosystem, and community layer goals.
- `architecture` (`aidlc/spaces/default/codekb/fitforge/architecture.md`): Django REST Framework API design, Room offline caching, and modular service isolation.
- `code-structure` (`aidlc/spaces/default/codekb/fitforge/code-structure.md`): Backend isolation in `backend/apps/social/` and Android package `com.fitforge.app.feature.social`.
- `team-practices` (`aidlc/spaces/default/intents/260907-social-media/inception/practices-discovery/team-practices.md`): Conventional commits on `slice/<name>` branches, walking skeleton strategy, test-after posture with property tests, and code style standards.

---

## 1. Intent Analysis

### 1.1 Problem Statement
FitForge athletes currently track workouts, nutrition, and recovery in isolation. While the adaptive engine computes cross-domain biometrics, users lack a mechanism to celebrate personal records (PRs), share structured training achievements, follow training partners, and engage with the broader fitness community directly within the app.

### 1.2 Objectives & Success Metrics
- **Engagement**: Increase daily active sessions by introducing social feeds and training highlights.
- **Safety & Privacy**: Enforce strict data isolation such that private biometrics (body weight, caloric budgets, menstrual cycle telemetry) are never exposed to public feeds.
- **Backward Compatibility**: Maintain uninterrupted authentication across web and mobile surfaces without mutating existing core domain schemas.

---

## 2. Functional Requirements (FR)

### FR1: Social Identity & Profile Management
- **FR1.1 — Independent Social Handle**: The system shall allow users to create and maintain a public social profile with a unique `@username` (3-32 alphanumeric characters, underscores allowed), display name (up to 64 chars), and bio (up to 256 chars).
  - *Given* an authenticated user without a social profile
  - *When* they register an unused `@handle`
  - *Then* a `SocialProfile` record is created and linked 1:1 to their `auth_user` record, leaving `auth_user.username` and `auth_user.email` untouched.
- **FR1.2 — Unauthenticated & Read-Only Browsing**: The system shall permit users without a social profile to browse public Explore feeds in read-only mode.
  - *Given* a user who has not created an `@handle`
  - *When* they navigate to the Social screen
  - *Then* they can browse public posts, but attempting to like, comment, follow, or post prompts profile creation.
- **FR1.3 — Profile Privacy Toggle**: The system shall allow users to set their profile to private (`is_private = True`), requiring approval for follow requests.

### FR2: Social Feed & Discovery
- **FR2.1 — Dual-Tab Android Feed**: The Android client shall provide a top-level tab navigation with "Following" and "Explore" feeds.
  - *Given* an active network connection
  - *When* the user views the "Following" tab
  - *Then* posts authored by accepted followees are displayed in reverse-chronological order.
  - *When* the user views the "Explore" tab
  - *Then* public community posts and verified PR achievements are displayed in reverse-chronological order with cursor-based pagination.
- **FR2.2 — Pagination & Feed Refresh**: The system shall support cursor-based pagination (default 20 posts per page) and swipe-to-refresh on both feeds.

### FR3: Workout Sharing & Data Privacy Scrubbing
- **FR3.1 — Configurable Workout Highlight Post**: When sharing a completed session from `diary.WorkoutSession`, the system shall allow the user to select which workout metrics to expose (e.g. exercise names, total sets, reps, weight lifted, session duration).
- **FR3.2 — Mandatory Biometric Floor Exclusion**: The system shall strictly scrub and exclude bodyweight measurements, caloric calculations, macro targets, and menstrual cycle phases from all social post payloads, regardless of user settings.
  - *Given* a workout session containing calculated energy expenditure or linked weight logs
  - *When* the user exports the session as a social post
  - *Then* only mechanical training telemetry (sets, reps, exercises, PR flags) is serialized into the post metadata.
- **FR3.3 — Text & Structured Metric Cards (v1 Scope)**: Posts in v1 shall support text captions and structured training metric cards. Image and video binary uploads are deferred to v2.

### FR4: Social Graph & Follow Mechanics
- **FR4.1 — Directed Following**: The system shall allow users to follow other public profiles immediately (`status: accepted`) or request to follow private profiles (`status: pending`).
- **FR4.2 — Prevent Self-Follow**: The system shall strictly reject attempts by a user to follow their own profile at both the API and database constraint levels.
- **FR4.3 — Unfollow & Request Cancellation**: Users shall be able to unfollow existing connections or withdraw pending follow requests at any time.

### FR5: Community Interactions (Likes & Threaded Comments)
- **FR5.1 — Post Likes**: Authenticated users with a social profile shall be able to like and unlike posts with idempotent toggle behavior. Like counters shall be updated atomically.
- **FR5.2 — Threaded Comments**: The system shall support top-level comments and 1-level nested replies on posts.
- **FR5.3 — Author Comment Moderation**: The author of a post shall have full authority to delete any comment or reply posted on their own post.

### FR6: Safety, Moderation & Reporting
- **FR6.1 — User Blocking**: Users shall be able to block other users. Blocking immediately dissolves any follow relationship, prevents further follow requests, and hides all posts and comments between the two users.
- **FR6.2 — Content Reporting**: Users shall be able to submit abuse/spam reports on posts and comments with predefined reason categories.
- **FR6.3 — Automated Abuse & Profanity Filtering**: The backend shall reject or flag comments containing disallowed abusive or profane content before persistence.
- **FR6.4 — Follower-Only Comment Restriction**: Post authors shall be able to restrict comment permissions on their posts to accepted followers only.

---

## 3. Non-Functional Requirements (NFR)

- **NFR1 — Feed Latency**: The `/api/v1/social/feed/` endpoint shall deliver the initial 20-post page within 300ms (P95) under standard database indexing on PostgreSQL.
- **NFR2 — Backward Compatibility & Auth Integrity**: All social endpoints shall authenticate via existing JWT credentials without altering `auth_user` schemas or invalidating web portal sessions.
- **NFR3 — Offline Resilience**: The Android application shall cache the latest 50 feed posts in Room, enabling offline read access in gym environments. Mutating actions (posting, liking, commenting) shall require network connectivity with graceful offline dialogs.
- **NFR4 — Privacy Guarantee**: Zero personal biometric fields (weight, body fat, cycle phase, caloric target) shall ever be exposed through any social endpoint response or public serializer.
- **NFR5 — Data Integrity**: Follow graph integrity and like idempotency shall be guarded by database-level unique constraints and atomic query expressions.

---

## 4. Constraints

- **CON1 — Modular Isolation**: All server-side models, views, serializers, and routers must reside strictly within `backend/apps/social/`. Android UI and domain logic must reside within `com.fitforge.app.feature.social`.
- **CON2 — Core Schema Immutability**: No tables or columns in `accounts_*`, `diary_*`, `reference_*`, or `engine_*` may be modified, renamed, or dropped.
- **CON3 — Media Deferral**: Binary image and video media upload pipelines are deferred to v2. V1 focuses on text, structured workout telemetry cards, and social graph interactions.
- **CON4 — No LLM Calculations**: In accordance with `fitforge/CLAUDE.md`, metric cards and PR badges are strictly computed by Python and SQL from stored diary data, never estimated or synthesized.

---

## 5. Assumptions

- **ASM1**: Existing users will be willing to pick a separate `@handle` when they decide to engage socially, keeping their email/web login private.
- **ASM2**: Standard PostgreSQL indexing on `social_post(created_at)`, `social_follow(follower, following)`, and `social_like(post, user)` will satisfy performance requirements for v1 volume without requiring dedicated Redis caching clusters.

---

## 6. Out of Scope (v1)

- Direct messaging (1-on-1 private chat).
- Image and video file uploads / media transcoding pipelines.
- Third-party social network cross-posting (Instagram / Strava export).
- Algorithmic engagement feed recommendation engines (v1 is strictly chronological and community Explore).

---

## 7. Open Questions & Downstream Traceability

- **OQ1**: What specific threshold marks a workout session as a "PR Achievement" card in Explore (e.g. 1RM calculation vs absolute weight record)? *To be resolved during Domain Design (2.6).*
- **OQ2**: Should notification push delivery use Firebase Cloud Messaging (FCM) immediately in v1 or stay as in-app notification list first? *To be detailed during Contract Design (2.8).*

## Review

**Verdict:** READY
**Reviewer:** aidlc-product-lead-agent
**Date:** 2026-09-07T15:55:07Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | aidlc/spaces/default/intents/260907-social-media/inception/requirements-analysis/requirements.md > FR3.1 | PR Achievement card distinction between 1RM and rep records | Detail calculation thresholds during Domain Design (2.6) | New |

### Summary

The requirements specification is exceptionally thorough, structurally sound, and respects all brownfield constraints and privacy safety floors. All acceptance criteria follow the BDD Given/When/Then format with measurable thresholds.
