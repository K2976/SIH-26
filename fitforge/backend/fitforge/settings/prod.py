from .base import *  # noqa: F403

# Hardcoded literal — see base.py for why this is never an env-var default.
DEBUG = False

if not SECRET_KEY:  # noqa: F405
    raise RuntimeError("DJANGO_SECRET_KEY environment variable is required in production.")

# Render (and most PaaS load balancers) terminate TLS at the edge and forward
# plain HTTP to the app with this header — without it, request.is_secure()
# is always False behind the proxy and SECURE_SSL_REDIRECT loops forever.
SECURE_PROXY_SSL_HEADER = ("HTTP_X_FORWARDED_PROTO", "https")

SECURE_SSL_REDIRECT = True
SESSION_COOKIE_SECURE = True
CSRF_COOKIE_SECURE = True

# Render's internal health-check probe may hit the instance directly,
# without going through the TLS-terminating proxy that sets
# X-Forwarded-Proto — which would otherwise turn the redirect above into a
# 301 for /healthz/ and make Render think the service is down. The
# endpoint carries no sensitive data, so serving it over plain HTTP is fine.
SECURE_REDIRECT_EXEMPT = [r"^healthz/$"]
