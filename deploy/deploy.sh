#!/usr/bin/env bash
set -euo pipefail

JAR_PATH="$1"
PID_FILE="/app/sofia.pid"
LOG_DIR="/app/log"

# 기존 프로세스 종료
if [ -f "$PID_FILE" ]; then
    OLD_PID=$(cat "$PID_FILE")
    if kill -0 "$OLD_PID" 2>/dev/null; then
        echo "Stopping existing process (PID: $OLD_PID)"
        kill "$OLD_PID"
        timeout 30 bash -c "while kill -0 $OLD_PID 2>/dev/null; do sleep 1; done" || true
    fi
    rm -f "$PID_FILE"
fi

# 로그 디렉토리 생성
mkdir -p "$LOG_DIR"

# 로그 파일명: {launch-date}-{version}.log
VERSION=$(basename "$JAR_PATH" | sed 's/sofia-\(.*\)\.jar/\1/')
LOG_FILE="${LOG_DIR}/$(date +%Y%m%d-%H%M%S)-${VERSION}.log"

# 환경변수 로드 후 실행
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/secrets.env"

if [ -f "$ENV_FILE" ]; then
    set -a
    source "$ENV_FILE"
    set +a
fi

echo "Starting Sofia v${VERSION}"
java -jar "$JAR_PATH" > "$LOG_FILE" 2>&1 &
echo $! > "$PID_FILE"
echo "Started (PID: $(cat "$PID_FILE"), Log: $LOG_FILE)"
