# Practices Discovery Interview

## Questions

### 1. Way of Working
How should the team organize branching and code changes?
A. Reaffirm standing practice: `slice/<name>` branches, conventional commits, PR required to merge into master (Default)
B. Other (please specify)

[Answer]: A

### 2. Walking Skeleton
Build a thin end-to-end slice first? A walking skeleton is a minimal version that runs the whole way through, built first to prove the pieces connect before the real features go in.
A. Yes — start with an initial walking skeleton for the social slice (connecting SocialProfile through DB to API) (Default)
B. No — implement components directly
C. Other (please specify)

[Answer]: A

### 3. Testing Posture
What is the testing methodology and order for this slice?
A. Reaffirm standing practice: Test-after methodology per layer; hypothesis property tests for the engine; smoke tests for social endpoints (Default)
B. TDD — write tests before implementation
C. Other (please specify)

[Answer]: A

### 4. Deployment
What is the deployment target for this slice?
A. Development/local environment with live Neon PostgreSQL staging database; deployment pipeline out of scope for feature slice (Default)
B. Other (please specify)

[Answer]: A

### 5. Code Style & Conventions
What code style tools and rules apply?
A. Reaffirm standing practice: Python uses Ruff (lint + format); Android uses Detekt; modular isolation in `backend/apps/social/` (Default)
B. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

Summary of practices discovered and reaffirmed:
- Way of Working: Conventional commits on `slice/<name>` branches; master protected by PRs.
- Walking Skeleton: Start with initial slice connecting SocialProfile through DB, API, and Android UI.
- Testing Posture: Test-after methodology with property testing on the engine.
- Deployment: Local / staging environment with live Neon PostgreSQL database.
- Code Style: Python Ruff, Android Detekt, modular isolation in `backend/apps/social/`.

Does this all look correct before I generate the artifact?
- Looks correct
- Request changes

[Answer]: Looks correct
