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
| `DJANGO_ALLOWED_HOSTS` | **Required — the health check itself fails without it.** Comma-separated hostnames the app will accept `Host:` headers from (e.g. `fitforge-backend-f7ow.onrender.com`, plus any custom domain) — bare hostnames, no scheme. Left unset, `ALLOWED_HOSTS` defaults to empty and Django rejects every request with `400`, including `/healthz/`, which fails the deploy outright (this happened on the first deploy — see "Free-tier cold starts" below). |
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

Each deploy runs, in order: `buildCommand` (install deps, `collectstatic`,
then `migrate --noinput`) → `startCommand` (gunicorn).

`render.yaml` sets `plan: free`. Render's free tier doesn't support
`preDeployCommand` (confirmed against Render's docs — it's paid-plan only),
so `migrate` runs as the last step of `buildCommand` instead of as a
separate pre-deploy step. This is safe here: `migrate` is idempotent (a
no-op when nothing's pending), and the free plan can't scale to multiple
instances anyway, so the "once per deploy, not once per instance" concern
that `preDeployCommand` exists for elsewhere doesn't apply on this plan.

## Re-running migrations manually

Render's free tier has no Shell/SSH access (also paid-plan only), so there's
no `render ssh` here. If a migration needs to be re-run outside a normal
deploy:

- **Preferred**: trigger a redeploy (Manual Deploy → "Deploy latest
  commit", or push an empty commit). `buildCommand` re-runs `migrate` every
  time, so this is usually enough.
- **Fallback**: run it locally, pointed at the deployed database:

  ```bash
  cd fitforge/backend
  DJANGO_SECRET_KEY=x DJANGO_SETTINGS_MODULE=fitforge.settings.prod \
  DJANGO_ALLOWED_HOSTS=localhost DATABASE_URL='<the deployed DATABASE_URL>' \
  python manage.py migrate
  ```

  Only do this when you mean to modify the deployed database — it's the
  same Neon instance (or Neon branch) the live service reads from. Don't
  leave the real `DATABASE_URL` in shell history longer than you have to.

## Free-tier cold starts

Render's free web services spin down after **15 minutes with no inbound
traffic**, and the next request pays a **~30-50s (Render's own docs say "about
a minute") cold start** while the instance restarts. Two mitigations are in
place, and neither is a guarantee — read both.

> **Confirmed URL**: the deployed service is at
> `https://fitforge-backend-f7ow.onrender.com` — Render appended `-f7ow`
> because the bare `fitforge-backend` name from `render.yaml` was
> unavailable. The keep-alive workflow and the Android release fallback
> (`app/build.gradle.kts`) both use this confirmed URL now. If the service
> is ever recreated and lands at a different URL, update both, or set the
> `RENDER_HEALTH_URL` repo variable (see below) instead of editing the
> workflow.

> **First-deploy gotcha (already hit, now documented so it isn't repeated)**:
> the first deploy attempt failed because `DJANGO_ALLOWED_HOSTS` wasn't set
> in Render's dashboard. With it unset, `ALLOWED_HOSTS` in `base.py`
> defaults to an empty list, so Django's `DisallowedHost` check rejects
> *every* request with a plain `400` — including the health check itself,
> which made Render time out the deploy after 15 minutes of failing checks
> (visible in the deploy logs as `GET /healthz/` returning `400` on a tight
> loop). `DJANGO_ALLOWED_HOSTS` must be set to the service's actual host —
> `fitforge-backend-f7ow.onrender.com` — not left blank. If you ever
> recreate the service under a new name/URL, this is the first thing to set
> before the health check can pass.

### Automated keep-alive (best-effort, not a guarantee)

`.github/workflows/keep-alive.yml` pings `/healthz/` every 10 minutes
during 05:30-23:30 IST (00:00-18:00 UTC). It runs on **GitHub's**
infrastructure, not Render's, so it costs no Render resource beyond the
request itself — but it isn't free of every constraint:

- **Render's instance-hour cap**: the free tier grants **750 instance-hours
  per workspace per month** (shared across every free service in that
  workspace, not per-service). The keep-alive window is ~19h/day ≈ 570
  hours/month for this one service — comfortable headroom under 750, but
  check the Render dashboard for other free services in the same workspace
  before assuming there's room to widen the window.
- **GitHub Actions minutes**: this repo is private, so scheduled workflow
  runs count against the account's Actions minutes (2,000 free min/month on
  GitHub's Free plan). Each run is a single `curl` with no checkout step —
  a few seconds — so at 10-minute intervals for ~19h/day this uses well
  under 1,000 minutes/month.
- **It does not eliminate cold starts.** Outside the scheduled window
  (overnight), or during any stretch the service is genuinely idle for
  longer than the ping interval can cover (a missed/delayed GitHub Actions
  run — scheduled workflows aren't guaranteed to fire exactly on time), the
  service still spins down and the next real request still pays the full
  cold-start cost. Don't tell anyone this guarantees a warm instance.
- **To adjust the window or interval**: edit the `cron:` line in
  `.github/workflows/keep-alive.yml`. To point it at a different health
  check URL without editing the file, set the repo variable
  `RENDER_HEALTH_URL` (Settings → Secrets and variables → Actions →
  Variables).
- **To disable**: disable the workflow from the repo's Actions tab, or
  delete the file.

### Before a demo (manual backstop)

The automated keep-alive is best-effort and won't help if the service has
been genuinely idle (overnight, or longer than the ping window covers).
Independent of it, **5-10 minutes before presenting**, manually hit the
health check to force a warm instance:

```bash
curl https://<your-render-url>/healthz/
```

Or open `https://<your-render-url>/healthz/` in a browser. Expect it to
hang for up to about a minute on a cold instance, then return
`{"status": "ok"}`. Repeat once more a minute or two before you actually
start, since the instance-hour/interval caveats above mean the automated
ping isn't guaranteed to have kept it warm. You can also trigger
`.github/workflows/keep-alive.yml` on demand from the Actions tab
("Run workflow") instead of curling directly.

### Database-layer sleep is separate, and much faster

This deploys against Neon (see "Database" above), not Render-managed
Postgres — but Neon's free tier has its **own**, independent auto-suspend
behavior, worth knowing about even though it's not the bottleneck: a Neon
free-tier compute suspends after 5 minutes of inactivity and resumes in "a
few hundred milliseconds" on the next query (per Neon's docs). That's
negligible next to Render's 30-50s web-service cold start, so it doesn't
meaningfully compound with it. It's mentioned here because it's easy to
wrongly assume the database has no sleep behavior of its own just because
it isn't a Render-managed database — Render-managed Postgres has its own
separate free-tier constraints (1 GB storage cap, expires 30 days after
creation) that would matter if a future change switches off Neon; this
deployment doesn't hit them.

## Checking the health endpoint

`GET https://<your-render-url>/healthz/` returns `{"status": "ok"}` with a
plain `200` and never queries the database (`fitforge/backend/fitforge/health.py`)
— a slow or unreachable database shows up as a failing API, not a failing
health check, so a stuck query doesn't fool Render into cycling a healthy
instance.

## If a deploy fails

1. **Build/deploy logs**: service page → "Logs" tab (or "Events" for a
   deploy-by-deploy summary). Build failures show here first — check
   `pip install`, `collectstatic`, and `migrate` output (all three run in
   `buildCommand` on the free plan — see "Triggering a deploy" above).
2. **Migration failures**: since `migrate` runs inside `buildCommand`, a
   failed migration fails the build itself and aborts the deploy before the
   new instance starts — the previous instance keeps serving traffic. Look
   for the `migrate` output at the end of the build log.
3. **Runtime errors after a successful deploy**: "Logs" tab, filtered to
   the running instance — gunicorn/Django tracebacks land here.
4. **Health check failing**: on the free plan, rule out a plain cold start
   first — give it up to ~60s and retry before assuming something's
   actually broken (see "Free-tier cold starts" above). If it's still
   failing after that, confirm `/healthz/` works locally under
   `fitforge.settings.prod` (`DJANGO_SETTINGS_MODULE=fitforge.settings.prod
   python manage.py runserver`, with the required env vars set) before
   assuming it's a Render-side issue.
