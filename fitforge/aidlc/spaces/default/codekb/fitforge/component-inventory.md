# Component Inventory — FitForge Monorepo

## Backend Components

| Component | Path | Responsibility | Key Dependencies |
| :--- | :--- | :--- | :--- |
| **Accounts** | `backend/apps/accounts/` | User authentication, password validation, UserProfile biometrics, goal tracking. | `django.contrib.auth`, SimpleJWT |
| **Reference** | `backend/apps/reference/` | Canonical exercise library, localized movement patterns, global versioning. | `django.db` |
| **Diary** | `backend/apps/diary/` | Workout sessions, set logs, meals, weight logs, sleep logs, cycle tracking. | `apps.accounts`, `apps.reference` |
| **Engine** | `backend/apps/engine/` | Pure adaptive metabolic calculator, volume balancing, energy deficit floors. | Python standard library (No I/O) |
| **API** | `backend/apps/api/` | URL routing, OpenAPI schema generation, global app config. | `drf_spectacular`, all apps |
| **Social (New)** | `backend/apps/social/` | Public profile `@username`, following graph, posts, workout sharing, comments. | `apps.accounts`, `apps.diary` |

---

## Android Components

| Component | Package / Path | Responsibility | Key Dependencies |
| :--- | :--- | :--- | :--- |
| **Core Database** | `com.fitforge.app.core.database` | Room database definition, DAOs for Exercises, WorkoutSessions, and SetLogs. | Room KSP |
| **Core Network** | `com.fitforge.app.core.network` | Retrofit HTTP client, JSON serialization, JWT auth interceptor. | Retrofit, OkHttp, Kotlinx Serialization |
| **Sync Manager** | `com.fitforge.app.sync` | WorkManager workers for background synchronization of diary logs. | AndroidX WorkManager, Hilt |
| **Onboarding** | `com.fitforge.app.feature.onboarding` | 6-step user setup flow (basics, fitness inputs, goal selection). | Compose, ViewModel |
| **Logbook** | `com.fitforge.app.feature.logbook` | Workout session logger, exercise picker, set recording. | Compose, Room DAOs |
| **Social (New)** | `com.fitforge.app.feature.social` | Social feed, public profile screen, workout post sharing, user search. | Compose, Coil, Retrofit |
