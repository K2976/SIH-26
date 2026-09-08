# Technology Stack — FitForge

## Languages & Platforms

- **Python**: 3.12 (Backend services, API, and adaptive engine)
- **Kotlin**: 1.9+ (Native Android application, target SDK 34, min SDK 26)
- **SQL (PostgreSQL)**: PostgreSQL 16 hosted on Neon Serverless Cloud

---

## Backend Frameworks & Libraries

| Technology | Version | Purpose |
| :--- | :--- | :--- |
| **Django** | 5.0+ | Web application framework and ORM |
| **Django REST Framework** | 3.15+ | Web API serialization and views |
| **djangorestframework-simplejwt** | 5.3+ | JWT authentication and token blacklisting |
| **drf-spectacular** | 0.27+ | OpenAPI 3.0 schema generation |
| **dj-database-url** | 2.1+ | 12-factor database configuration |
| **psycopg2-binary** | 2.9+ | PostgreSQL adapter |
| **Gunicorn** | 21+ | WSGI HTTP server |
| **WhiteNoise** | 6.6+ | Static file serving for Django |
| **Ruff** | 0.3+ | Linter and code formatter |
| **Pytest + Hypothesis** | 8.0+ | Unit and property-based testing |

---

## Android Frameworks & Libraries

| Technology | Version | Purpose |
| :--- | :--- | :--- |
| **Jetpack Compose** | BOM 2024.02+ | Modern declarative UI toolkit |
| **Material 3** | 1.2+ | Material Design components |
| **Dagger Hilt** | 2.50+ | Dependency injection |
| **AndroidX Room** | 2.6+ | Local SQLite database abstraction |
| **AndroidX WorkManager** | 2.9+ | Background task scheduling and delta sync |
| **Retrofit + OkHttp** | 2.9+ / 4.12+ | HTTP networking client |
| **Kotlinx Serialization** | 1.6+ | JSON parsing and serialization |
| **MediaPipe Tasks Vision** | 0.10+ | Real-time on-device pose detection and rep counting |
| **Coil** | 2.6+ | Image loading for Android Compose |
| **Detekt** | 1.23+ | Static code analysis for Kotlin |
