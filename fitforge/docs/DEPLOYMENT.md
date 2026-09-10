# Deploying the backend to Render

The Render Blueprint for this service is `render.yaml` at the repo root
(`SIH-26/render.yaml`), with `rootDir: fitforge/backend` so build/start
commands run inside the Django project.

## Database

This deploys against the **existing Neon PostgreSQL** instance already used
for local development (see `docs/PHONE_BACKEND_SETUP.md`) — there is no
Render-managed Postgres. `DATABASE_URL` in Render's dashboard is set to the
same Neon connection string (or a separate Neon branch/database for prod,
if you want isolation from local dev data). This avoids running two
Postgres instances and keeps the schema in one place.

## LLM key

Only `GEMINI_API_KEY` is required — it's the only LLM key the codebase
reads (`apps/reference/food_label.py`). No `GROQ_API_KEY` or equivalent is
wired up anywhere in this repo, so it isn't listed as a required variable.
If a Groq (or other) integration is added later, add its key to both
`render.yaml` and the list below.

## Environment variables to set in Render's dashboard before first deploy

Set these on the service in Render's dashboard — never in `render.yaml`,
never in source. Names only, no values here either:

| Variable | Purpose |
|---|---|
| `DJANGO_SECRET_KEY` | Django's cryptographic signing key. Generate a fresh one for prod, don't reuse a dev key. |
| `DATABASE_URL` | Neon Postgres connection string. |
| `GEMINI_API_KEY` | Food label recognition (`apps/reference/food_label.py`). |
| `USDA_FDC_API_KEY` | USDA FoodData Central lookups (`apps/reference/external_foods.py`). |
| `DJANGO_ALLOWED_HOSTS` | Comma-separated hostnames the app will accept `Host:` headers from (e.g. your `.onrender.com` domain, plus any custom domain) — bare hostnames, no scheme. |
| `CORS_ALLOWED_ORIGINS` | Comma-separated browser origins allowed to call the API cross-origin. Django requires a full scheme, e.g. `https://app.example.com`, not just `app.example.com`. Leave unset (empty) if nothing calls this from a browser. |
| `CSRF_TRUSTED_ORIGINS` | Comma-separated origins trusted for CSRF-protected requests (relevant for Django admin login). Same scheme requirement as `CORS_ALLOWED_ORIGINS`. |

`render.yaml` also sets `DJANGO_SETTINGS_MODULE=fitforge.settings.prod` and
`PYTHON_VERSION=3.12.8` directly (not secrets, no dashboard action needed).

Render prompts for every `sync: false` variable once, at Blueprint
creation time.

## Triggering a deploy

- **Automatic**: pushing to the `branch` configured in `render.yaml`
  (`master`) triggers a deploy, per Render's normal Git-connected flow.
- **Manual**: from the service page in Render's dashboard, "Manual Deploy"
  → "Deploy latest commit" (or a specific commit).

Each deploy runs, in order: `buildCommand` (install deps + `collectstatic`)
→ `preDeployCommand` (`migrate --noinput`, once per deploy, before new
instances take traffic) → `startCommand` (gunicorn).

`render.yaml` sets `plan: starter`. If you ever switch the service to the
`free` plan, confirm `preDeployCommand` still runs on that plan before
relying on it — if it silently stops running, migrations won't apply on
deploy and you'll need to trigger them manually (below) every time.

## Re-running migrations manually

If the release-phase migration needs to be re-run (e.g. it failed
partway, or you need to apply a migration outside a deploy), use Render's
one-off shell against the service — from the service page: "Shell" tab, or
the Render CLI:

```bash
render ssh fitforge-backend
python manage.py migrate
```

Do not run `migrate` against this database from a local machine unless you
mean to modify the deployed database — it's the same Neon instance the live
service reads from.

## Checking the health endpoint

`GET https://<your-render-url>/healthz/` returns `{"status": "ok"}` with a
plain `200` and never queries the database (`fitforge/backend/fitforge/health.py`)
— a slow or unreachable database shows up as a failing API, not a failing
health check, so a stuck query doesn't fool Render into cycling a healthy
instance.

## If a deploy fails

1. **Build/deploy logs**: service page → "Logs" tab (or "Events" for a
   deploy-by-deploy summary). Build failures show here first — check
   `pip install` and `collectstatic` output.
2. **Pre-deploy (migration) failures**: shown in the same deploy's log
   stream, after the build step. A failed `preDeployCommand` aborts the
   deploy before the new instance starts, so the previous instance keeps
   serving traffic.
3. **Runtime errors after a successful deploy**: "Logs" tab, filtered to
   the running instance — gunicorn/Django tracebacks land here.
4. **Health check failing**: confirm `/healthz/` works locally under
   `fitforge.settings.prod` first (`DJANGO_SETTINGS_MODULE=fitforge.settings.prod
   python manage.py runserver`, with the required env vars set) before
   assuming it's a Render-side issue.
