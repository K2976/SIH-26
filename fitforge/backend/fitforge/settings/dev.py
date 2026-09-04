from .base import *  # noqa: F403

DEBUG = True

SECRET_KEY = os.environ.get("DJANGO_SECRET_KEY", "dev-insecure-secret-key")  # noqa: F405

ALLOWED_HOSTS = ["*"]
