from .base import *  # noqa: F403

# Hardcoded literal — see base.py for why this is never an env-var default.
DEBUG = False

if not SECRET_KEY:  # noqa: F405
    raise RuntimeError("DJANGO_SECRET_KEY environment variable is required in production.")
