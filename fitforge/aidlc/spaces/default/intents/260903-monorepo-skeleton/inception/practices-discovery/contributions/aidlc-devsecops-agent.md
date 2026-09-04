**Collaborator:** aidlc-devsecops-agent

## Contribution

Reviewed the draft for secret-handling, scanning, and supply-chain
controls.

- gitleaks in both pre-commit and CI is already planned per the original
  request — good baseline secret-scanning coverage (catches a secret
  before commit AND before merge).
- The intent-capture stage already affirmed a stricter-than-default rule:
  never write a user-supplied secret into any AIDLC artifact/audit log.
  This should be carried into `discovered-rules.md`'s `## Forbidden`
  section verbatim so it survives as a project-level practice, not just an
  intent-capture footnote. (Draft already does this — no gap.)
- `DEBUG` defaults to off, per the original request and CLAUDE.md's
  Security section — this belongs in Django's `base.py`/`prod.py` split
  settings, not left to an environment default that could silently flip.
  Flagging for code-generation: `prod.py` (and `base.py`) must hardcode
  `DEBUG = False` rather than reading it from an env var with a truthy
  default.
- No SAST/DAST tooling is requested beyond ruff/detekt (which are linters,
  not security scanners) and gitleaks (secret scanner, not a
  vulnerability scanner). For a zero-model scaffold with no attack surface
  yet, this is proportionate — recommend NOT adding a dependency
  vulnerability scanner (e.g. pip-audit, Safety) in this pass, since it
  would be scanning an empty dependency surface for no real benefit yet.
  Revisit at the first feature slice's practices re-run.

## Positions

- AGREE: gitleaks placement (pre-commit + CI) is sufficient for this pass
- AGREE: carry the secret-handling rule into discovered-rules.md
- OBJECT: draft doesn't yet flag that DEBUG=False must be a hardcoded
  literal in `prod.py`/`base.py`, not an env-var default — recommend
  adding this to evidence.md as a concrete instruction for code-generation.
