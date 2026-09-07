# Team-Level Rules

> This team's affirmed practices and corrections. Loaded after `org.md` as
> strict-additive guidance; contradictions with broader policy are rejected.
> Populated by the practices-discovery affirmation gate. Edit at the gate,
> not directly.

## Way of Working
Branches are named `slice/<name>`; commits are conventional-format (e.g. `feat(social): add social profile model`). `master`/`main` stays green and installable, and no one — including an AI agent — pushes to it directly or auto-merges into it. `master` only moves when the user manually merges an approved PR. (Affirmed 2026-09-04, reaffirmed 2026-09-07).

## Walking Skeleton
Walking skeleton is scope-dependent. For the `social-media` feature slice, an initial walking skeleton (SocialProfile model + migration + serializer + view + endpoint test) proves end-to-end connectivity before building feeds and interaction graphs.

## Testing Posture
- **Methodology**: test-after
- **Ordering**: implement each applicable testable layer, then write and run that layer's tests.

Engine calculations specifically get property-based tests via hypothesis (per `fitforge/CLAUDE.md`). Social endpoints require unit and integration tests covering unique username validation and backward-compatible authentication.

## Deployment
Development/local environment with live Neon PostgreSQL database. Continuous integration runs Ruff, Pytest, and Detekt on GitHub Actions. Operation-phase deployment pipelines remain out of scope for this feature slice.

## Code Style
- Python (backend): Ruff (lint + format), no additional formatter.
- Kotlin (Android): Detekt, no additional formatter (no ktlint).
- Modular isolation: All social models, serializers, views, and routes reside under `backend/apps/social/` on the server and `feature/social/` on Android.
- Directory layout: exactly as specified in `fitforge/CLAUDE.md`'s "Repository layout" section.
## Forbidden

<!-- Team-specific forbidden patterns -->

## Mandated

<!-- Team-specific mandates -->

## Corrections

<!-- Self-learning loop appends here. -->
