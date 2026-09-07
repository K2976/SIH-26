# Code Structure — FitForge Monorepo

## Repository Layout

```
fitforge/
├── CLAUDE.md                    # Operational constraints & hard rules
├── docs/                        # Specifications and design decisions
│   ├── FITFORGE_PROJECT_GUIDE.md
│   └── DECISIONS.md
├── backend/                     # Django REST Framework backend
│   ├── manage.py
│   ├── pyproject.toml
│   ├── database/                # Database documentation & migration plans
│   ├── fitforge/                # Project root & settings (base, dev, prod)
│   └── apps/                    # Pluggable Django applications
│       ├── accounts/            # User model, profile, goals, auth
│       ├── reference/           # Exercise library, versioned delta-sync
│       ├── diary/               # Workout, meal, weight, sleep, cycle logs
│       ├── engine/              # Pure calculator algorithms & budgets
│       ├── coach/               # LLM tool-calling interfaces
│       ├── api/                 # Central router & OpenAPI schema
│       └── social/              # [In-Flight] Social profile, feed, interactions
└── android/                     # Kotlin + Jetpack Compose Android app
    ├── build.gradle.kts
    └── app/src/main/java/com/fitforge/app/
        ├── core/                # DI (Hilt), Room DB, network, utils
        ├── data/                # Data sources & repositories
        ├── domain/              # Business models & workout metrics
        ├── sync/                # WorkManager sync workers
        └── feature/             # UI screens and ViewModels
            ├── onboarding/
            ├── logbook/
            ├── profile/
            └── social/          # [In-Flight] Mobile social feed & profile UI
```

---

## Code Patterns & Conventions

1. **Vertical Slices**: Features are implemented top-to-bottom (database model -> serializer -> view -> Android Compose UI) rather than layer-by-layer.
2. **REST API Versioning**: All API paths reside under `/api/v1/` and follow OpenAPI 3.0 schema generation via `drf-spectacular`.
3. **Repository Pattern (Android)**: Compose ViewModels interact solely with Repositories; Repositories mediate between local Room DAOs and Retrofit remote network services.
4. **Offline First (Sync Strategy)**:
   - Data is committed to local Room database immediately.
   - Sync status is marked as pending.
   - Background WorkManager worker batches pending entities to `/api/v1/diary/sync/` using UUID idempotency.
