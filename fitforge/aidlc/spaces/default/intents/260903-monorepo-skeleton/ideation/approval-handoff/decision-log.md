# Decision Log — Ideation Phase — FitForge Monorepo Skeleton

| # | Decision | Stage | Rationale |
|---|---|---|---|
| 1 | Composed a custom `monorepo-skeleton` scope (10/33 stages) rather than a stock scope | Initialization / composer | Fully-specified scaffolding task with low ambiguity, low structural uncertainty, low risk (ARS 22/100, "Focused") — a stock scope's nearest match was 6 flips away |
| 2 | Scope boundary confirmed as scaffolding only, no business logic | intent-capture | Matches the user's explicit instruction and the composed scope |
| 3 | Install the Android SDK (cmdline-tools + platform + build-tools) and JDK 17 on this machine now | intent-capture | `./gradlew assembleDebug` is a stated done-when criterion; it cannot be satisfied without a real SDK/JDK present |
| 4 | Local dev/test database is real Postgres (hosted Neon instance), not SQLite | intent-capture | User had a Neon instance available; avoids a SQLite/Postgres behavior mismatch before any real models exist |
| 5 | CI database is an ephemeral Postgres service container, not the shared Neon dev database | intent-capture | Keeps CI isolated, avoids depending on external network/secrets for a scaffold-only pipeline |
| 6 | The Neon connection string is never written into any AIDLC artifact, questions file, or audit log | intent-capture | Standard secret-handling practice; codified as a project-level `## Forbidden` rule |
| 7 | No strict sequencing between backend and Android scaffolds | scope-definition | The two halves are independent at scaffold time; no dependency edge exists yet |
| 8 | Everything in the original request is Must Have; nothing deferred | scope-definition | User confirmed no trimming needed for this pass |
| 9 | GO recommendation; proceed directly to Inception (Practices Discovery) | approval-handoff | All Ideation artifacts are consistent, all risks have acknowledged mitigations |
