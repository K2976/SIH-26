**Collaborator:** aidlc-quality-agent

## Contribution

Reviewed the draft `team-practices.md` Testing Posture section against the
project's actual test stack (`fitforge/CLAUDE.md`: pytest + pytest-django +
factory_boy + hypothesis on the backend, detekt on Android).

- The done-when criterion "pytest runs clean with zero collected tests" is
  the correct bar for a pure-scaffold pass — there is no code yet to test
  against. This should be stated explicitly in the interview so the human
  doesn't expect coverage numbers this pass.
- Recommend the interview's Testing Posture answer set `**Methodology**:
  test-after` (matches the org.md default and the project's stated
  intent to write property-based tests for the engine once it exists) and
  `**Ordering**: implement each testable layer, then write and run that
  layer's tests` — this is exactly what CLAUDE.md's "Testing" section
  describes for the engine, generalized to the whole project.
- No coverage floor should be affirmed yet — there's no code to measure
  coverage against. Recommend deferring the numeric floor decision to the
  first feature slice's practices re-run, not fixing it now.
- CI quality gate: ruff + pytest + detekt + `assembleDebug` + gitleaks
  matches CLAUDE.md's stated CI list exactly. No gap found.

## Positions

- AGREE: draft's Testing Posture direction (test-after, per-layer)
- OBJECT: draft doesn't yet state that zero tests is acceptable for THIS
  pass specifically — a reader unfamiliar with the acceptance criteria
  could mistake the empty test suite for an oversight. Recommend making
  this explicit in the integrated version.
