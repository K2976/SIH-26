# Business Overview — FitForge

## Executive Summary

FitForge is an integrated fitness coaching and telemetry platform engineered around an adaptive metabolic engine. Unlike conventional fitness applications that record workouts, food consumption, and biometrics in isolation, FitForge continuously computes training volume, caloric targets, and recovery thresholds against one another in real time.

The platform serves two primary client surfaces backed by a shared cloud infrastructure:
1. **FitForge Web Application**: The established browser-based interface providing user registration, initial onboarding, profile management, and desktop dashboard visibility.
2. **FitForge Android Application**: A modern, native Android application built with Kotlin and Jetpack Compose featuring offline-first local Room caching, vision-assisted rep tracking via MediaPipe, and an upcoming community social layer.

---

## Core Value Propositions

1. **Integrated Adaptive Engine**:
   - Training intensity shifts caloric and macronutrient targets.
   - Missed training sessions dynamically rebalance the remaining weekly volume.
   - Low self-reported energy drops the immediate intensity ceiling without penalizing long-term progress.

2. **Absolute Safety Floors**:
   - Energy recommendations cannot push below strict physiological minimums.
   - Mathematical safety clamping enforces sensible target rates of weight gain or loss.

3. **Offline-First Resilience**:
   - Designed for poor connectivity environments (e.g. gym basements).
   - Local state is maintained on-device in SQLite/Room, and changes are synchronized to the cloud via background workers when connectivity returns.

4. **Community & Social Interaction (In-Flight)**:
   - Extends the platform with public identity handles, following feeds, social workout sharing, likes, comments, and community fitness challenges.
   - Ensures zero data leakage of private biometrics or web login credentials.
