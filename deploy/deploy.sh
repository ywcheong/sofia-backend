#!/usr/bin/env bash
set -euo pipefail

JAR_PATH="$1"
PID_FILE="/app/sofia.pid"
LOG_DIR="/app/log"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/secrets.env"

stop_existing_process() {
    if [ ! -f "$PID_FILE" ]; then
        return
    fi

    local old_pid
    old_pid=$(cat "$PID_FILE")

    if kill -0 "$old_pid" 2>/dev/null; then
        echo "Stopping existing process (PID: $old_pid)"
        kill "$old_pid"
        timeout 30 bash -c "while kill -0 $old_pid 2>/dev/null; do sleep 1; done" || true
    fi

    rm -f "$PID_FILE"
}

build_log_file() {
    mkdir -p "$LOG_DIR"
    local version
    version=$(basename "$JAR_PATH" | sed 's/sofia-\(.*\)\.jar/\1/')
    echo "${LOG_DIR}/$(date +%Y%m%d-%H%M%S)-${version}.log"
}

refresh_secrets_from_env() {
    local secret_keys=("SOFIA_DATASOURCE_URL" "SOFIA_DATASOURCE_PASSWORD" "SOFIA_DATASOURCE_USERNAME")
    local available=false

    for key in "${secret_keys[@]}"; do
        if [ -n "${!key:-}" ]; then
            available=true
            break
        fi
    done

    if [ "$available" = true ]; then
        echo "Refreshing secrets.env from environment variables"
        > "$ENV_FILE"
        for key in "${secret_keys[@]}"; do
            if [ -n "${!key:-}" ]; then
                echo "${key}=${!key}" >> "$ENV_FILE"
            fi
        done
    fi
}

load_env_file() {
    if [ -f "$ENV_FILE" ]; then
        set -a
        source "$ENV_FILE"
        set +a
    fi
}

start_application() {
    local log_file="$1"
    local version
    version=$(basename "$JAR_PATH" | sed 's/sofia-\(.*\)\.jar/\1/')

    echo "Starting Sofia v${version}"
    java \
        -Xms128m -Xmx256m \
        -XX:MetaspaceSize=64m \
        -XX:MaxMetaspaceSize=128m \
        -XX:+UseSerialGC \
        -jar "$JAR_PATH" > "$log_file" 2>&1 &

    echo $! > "$PID_FILE"
    echo "Started (PID: $(cat "$PID_FILE"), Log: $log_file)"
}

# --- Main ---
stop_existing_process
log_file=$(build_log_file)
refresh_secrets_from_env
load_env_file
start_application "$log_file"
