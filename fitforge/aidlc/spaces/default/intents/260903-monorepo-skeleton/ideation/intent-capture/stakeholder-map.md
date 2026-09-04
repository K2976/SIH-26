# Stakeholder Map — FitForge Monorepo Skeleton

## Stakeholders

| Stakeholder | Interest | Decision-maker or Influencer | Source |
|---|---|---|---|
| Project owner / sole developer | Wants a working, lint-clean, buildable scaffold on both backend and Android before starting feature slices; decides scope, tooling, and environment tradeoffs (SDK install, DB choice) | Decision-maker | [desc], [Q1], [Q2], [Q3], [Q4] |
| Future contributors (unnamed, not yet identified) | Will build feature slices on top of this scaffold; benefit from correct split settings, pinned versions, and working CI | N/A — solo-developer project, no other stakeholders identified | [desc], [Q7] |

## Communication Requirements

| Requirement | Detail | Source |
|---|---|---|
| Approval gates | Every AIDLC stage in this workflow presents its output for explicit Approve / Request Changes before proceeding | [scope] |
| Version transparency | Every dependency version must be looked up from current documentation and pinned; anything unconfirmable must be flagged rather than guessed | [desc] |

## Assumptions & Open Questions

None.
