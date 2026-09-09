#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
readonly BACKEND_DIR="$PROJECT_DIR/backend"
readonly RUNTIME_DIR="$PROJECT_DIR/android/build/phone-backend"
readonly BACKEND_LOG="$RUNTIME_DIR/backend.log"
readonly BACKEND_PID="$RUNTIME_DIR/backend.pid"
readonly BACKEND_PORT="8000"
readonly HEALTH_URL="http://127.0.0.1:$BACKEND_PORT/api/v1/app-config/"

find_adb() {
    if [[ -n "${FITFORGE_ADB:-}" && -x "$FITFORGE_ADB" ]]; then
        printf '%s\n' "$FITFORGE_ADB"
    elif command -v adb >/dev/null 2>&1; then
        command -v adb
    elif [[ -n "${ANDROID_HOME:-}" && -x "$ANDROID_HOME/platform-tools/adb" ]]; then
        printf '%s\n' "$ANDROID_HOME/platform-tools/adb"
    elif [[ -n "${ANDROID_SDK_ROOT:-}" && -x "$ANDROID_SDK_ROOT/platform-tools/adb" ]]; then
        printf '%s\n' "$ANDROID_SDK_ROOT/platform-tools/adb"
    else
        return 1
    fi
}

backend_is_ready() {
    curl --fail --silent --max-time 2 "$HEALTH_URL" >/dev/null 2>&1
}

start_backend_if_needed() {
    if backend_is_ready; then
        printf 'FitForge backend is already ready at %s\n' "$HEALTH_URL"
        return
    fi

    if command -v lsof >/dev/null 2>&1 &&
        lsof -nP -iTCP:"$BACKEND_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
        printf 'Port %s is occupied, but the FitForge health check failed: %s\n' \
            "$BACKEND_PORT" "$HEALTH_URL" >&2
        printf 'Stop the process using that port, then run installDebug again.\n' >&2
        exit 1
    fi

    local python_bin="$BACKEND_DIR/.venv/bin/python"
    if [[ ! -x "$python_bin" ]]; then
        printf 'Backend virtual environment not found at %s\n' "$python_bin" >&2
        printf 'Create it and install backend/requirements/dev.txt, then retry.\n' >&2
        exit 1
    fi

    mkdir -p "$RUNTIME_DIR"
    (
        cd "$BACKEND_DIR"
        nohup "$python_bin" manage.py runserver "127.0.0.1:$BACKEND_PORT" --noreload \
            >"$BACKEND_LOG" 2>&1 &
        printf '%s\n' "$!" >"$BACKEND_PID"
    )

    local attempt
    for attempt in {1..40}; do
        if backend_is_ready; then
            printf 'Started FitForge backend at %s (log: %s)\n' "$HEALTH_URL" "$BACKEND_LOG"
            return
        fi

        if [[ -f "$BACKEND_PID" ]] && ! kill -0 "$(<"$BACKEND_PID")" 2>/dev/null; then
            break
        fi
        sleep 0.25
    done

    printf 'The FitForge backend did not become ready. Recent log output:\n' >&2
    tail -n 30 "$BACKEND_LOG" >&2 || true
    exit 1
}

configure_reverse_proxy() {
    local adb_bin
    if ! adb_bin="$(find_adb)"; then
        printf 'ADB was not found. Install Android SDK Platform Tools or set FITFORGE_ADB.\n' >&2
        exit 1
    fi

    "$adb_bin" start-server >/dev/null

    local devices=()
    local serial state
    if [[ -n "${FITFORGE_ANDROID_SERIAL:-}" ]]; then
        state="$("$adb_bin" -s "$FITFORGE_ANDROID_SERIAL" get-state 2>/dev/null || true)"
        if [[ "$state" != "device" ]]; then
            printf 'Android device %s is not connected and authorized.\n' \
                "$FITFORGE_ANDROID_SERIAL" >&2
            exit 1
        fi
        devices+=("$FITFORGE_ANDROID_SERIAL")
    else
        while IFS=$'\t' read -r serial state; do
            if [[ "$state" == "device" ]]; then
                devices+=("$serial")
            fi
        done < <("$adb_bin" devices | tail -n +2)
    fi

    if [[ "${#devices[@]}" -eq 0 ]]; then
        printf 'No authorized Android phone is connected.\n' >&2
        printf 'Connect the phone over USB, enable USB debugging, and accept its prompt.\n' >&2
        exit 1
    fi

    for serial in "${devices[@]}"; do
        "$adb_bin" -s "$serial" reverse "tcp:$BACKEND_PORT" "tcp:$BACKEND_PORT" >/dev/null
        if ! "$adb_bin" -s "$serial" reverse --list |
            grep -q "tcp:$BACKEND_PORT tcp:$BACKEND_PORT"; then
            printf 'ADB reverse verification failed for %s.\n' "$serial" >&2
            exit 1
        fi
        printf 'Forwarded %s localhost:%s to the FitForge backend.\n' \
            "$serial" "$BACKEND_PORT"
    done
}

start_backend_if_needed
configure_reverse_proxy
