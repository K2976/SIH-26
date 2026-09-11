from django.http import JsonResponse


def healthz(request):
    """Liveness check. Never touches the database — a slow query must not
    make Render think the process itself is down."""
    return JsonResponse({"status": "ok"})
