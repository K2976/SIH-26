# Run FitForge on a Physical Android Phone

The FitForge debug app connects to `http://localhost:8000/`. On a physical
phone, Android Debug Bridge (ADB) must forward that address to the Django
backend running on this Mac.

Repeat the backend and ADB forwarding steps after restarting the Mac, restarting
the phone, or reconnecting/reinstalling the app if it shows a network error.

## 1. Start the backend

Open Terminal and run:

```bash
cd /Users/kartik/Documents/SIH-26/fitforge/backend
source .venv/bin/activate
python manage.py runserver 0.0.0.0:8000
```

Keep this terminal open. A successful startup ends with a message similar to:

```text
Starting development server at http://0.0.0.0:8000/
```

The backend uses the database configured in `backend/.env`, so the Mac must
have internet access when that file points to Neon PostgreSQL.

If `.venv` does not exist, create it and install the dependencies first:

```bash
cd /Users/kartik/Documents/SIH-26/fitforge/backend
python3 -m venv .venv
.venv/bin/pip install -r requirements/dev.txt
```

## 2. Verify the backend on the Mac

In a second Terminal window, run:

```bash
curl http://127.0.0.1:8000/api/v1/app-config/
```

The expected response is:

```json
{"min_supported_version":"1.0.0"}
```

Do not continue until this request works.

## 3. Connect the phone

1. Enable **Developer options** and **USB debugging** on the phone.
2. Connect the phone to the Mac with a data-capable USB cable.
3. Unlock the phone and accept the USB debugging authorization prompt.
4. Confirm that ADB can see it:

```bash
adb devices -l
```

The device must be listed with the state `device`. If it says `unauthorized`,
accept the authorization prompt on the phone and run the command again.

## 4. Forward the backend port

Run:

```bash
adb reverse tcp:8000 tcp:8000
adb reverse --list
```

The list should contain:

```text
tcp:8000 tcp:8000
```

Keep the USB connection active while using the app.

## 5. Install or reinstall the debug app

From the Android project directory, install the current debug build:

```bash
cd /Users/kartik/Documents/SIH-26/fitforge/android
./gradlew installDebug
```

After installation, run the forwarding command again to be safe:

```bash
adb reverse tcp:8000 tcp:8000
```

Open FitForge on the phone. The debug build will now send its API requests to
the Django server through ADB.

## Quick restart commands

Terminal 1:

```bash
cd /Users/kartik/Documents/SIH-26/fitforge/backend
source .venv/bin/activate
python manage.py runserver 0.0.0.0:8000
```

Terminal 2:

```bash
adb devices -l
adb reverse tcp:8000 tcp:8000
adb reverse --list
```

## Troubleshooting

### The app says network error

Check these in order:

1. Confirm the Django terminal is still running.
2. Run the `curl` command from step 2.
3. Confirm `adb devices -l` lists the phone as `device`.
4. Recreate the forwarding rule:

   ```bash
   adb reverse --remove tcp:8000
   adb reverse tcp:8000 tcp:8000
   ```

5. Force-close FitForge on the phone and open it again.

### Port 8000 is already in use

Check whether a working backend is already running:

```bash
lsof -nP -iTCP:8000 -sTCP:LISTEN
curl http://127.0.0.1:8000/api/v1/app-config/
```

If `curl` succeeds, use the existing server and only repeat the ADB forwarding
step.

### Django cannot connect to the database

Confirm that the Mac has internet access and that
`/Users/kartik/Documents/SIH-26/fitforge/backend/.env` exists with a valid
`DATABASE_URL`. Never paste the real database password into source files or
commit it to Git.

### Login expired

An HTTP `401 Unauthorized` response means the phone reached the backend but its
saved login is no longer valid. Sign in again. This is different from a network
connection error.

## Debug builds only

These steps apply to the debug app. Release builds use the hosted API URL and
do not use the local Django server or ADB port forwarding.
