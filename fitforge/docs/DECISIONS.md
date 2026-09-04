# Decisions

## 2026-09-04 — CI workflow and pre-commit config live at the git repo root, not `fitforge/`

`fitforge/` is a subdirectory of the `K2976/SIH-26` GitHub repository, not a
repository of its own. GitHub Actions only discovers workflows under
`.github/workflows/` at the actual repo root, so `ci.yml` and
`.pre-commit-config.yaml` are placed there (one level above `fitforge/`)
rather than inside `fitforge/.github/`, with paths and `working-directory`
scoped into `fitforge/backend` and `fitforge/android`. Everything else
(code, docs, `.env.example`, app-level `.gitignore` rules) stays under
`fitforge/` per this repo's layout.

## 2026-09-04 — Local Android SDK not installed on this machine

`./gradlew assembleDebug` and `./gradlew detekt` are verified via CI
(`ubuntu-latest` runners ship a preinstalled, license-accepted Android SDK),
not locally. The user has stated they'll install Android Studio themselves
later for on-device testing; installing a second full SDK locally just to
prove one build would duplicate that setup for no lasting benefit. JDK 17
(Temurin) was installed locally since it's needed for any local Gradle
invocation regardless of SDK presence.
