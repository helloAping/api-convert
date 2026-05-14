#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="all"
BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"
ADMIN_USERNAME="${API_CONVERT_ADMIN_USERNAME:-}"
ADMIN_PASSWORD="${API_CONVERT_ADMIN_PASSWORD:-}"
JAVA_HOME_OVERRIDE=""
DB_TYPE="${API_CONVERT_DB_TYPE:-}"
DATASOURCE_URL="${SPRING_DATASOURCE_URL:-}"
DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-}"
DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-}"

usage() {
  echo "Usage: $0 [all|backend|frontend] [--admin-username USER] [--admin-password PASSWORD] [--backend-port PORT] [--frontend-port PORT] [--java-home DIR] [--db-type TYPE] [--datasource-url URL] [--datasource-username USER] [--datasource-password PASSWORD]" >&2
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    all|backend|frontend)
      TARGET="$1"
      shift
      ;;
    --admin-username)
      ADMIN_USERNAME="${2:?Missing value for --admin-username}"
      shift 2
      ;;
    --admin-password)
      ADMIN_PASSWORD="${2:?Missing value for --admin-password}"
      shift 2
      ;;
    --backend-port)
      BACKEND_PORT="${2:?Missing value for --backend-port}"
      shift 2
      ;;
    --frontend-port)
      FRONTEND_PORT="${2:?Missing value for --frontend-port}"
      shift 2
      ;;
    --java-home)
      JAVA_HOME_OVERRIDE="${2:?Missing value for --java-home}"
      shift 2
      ;;
    --db-type)
      DB_TYPE="${2:?Missing value for --db-type}"
      shift 2
      ;;
    --datasource-url)
      DATASOURCE_URL="${2:?Missing value for --datasource-url}"
      shift 2
      ;;
    --datasource-username)
      DATASOURCE_USERNAME="${2:?Missing value for --datasource-username}"
      shift 2
      ;;
    --datasource-password)
      DATASOURCE_PASSWORD="${2:?Missing value for --datasource-password}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage
      exit 2
      ;;
  esac
done

if [[ -n "$JAVA_HOME_OVERRIDE" ]]; then
  export JAVA_HOME="$JAVA_HOME_OVERRIDE"
fi
if [[ -n "${JAVA_HOME:-}" ]]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi

enable_compact_object_headers() {
  local jvm_args="${JAVA_OPTS:-}"
  if [[ " $jvm_args " != *" -XX:+UseCompactObjectHeaders "* ]]; then
    if [[ " $jvm_args " != *" -XX:+UnlockExperimentalVMOptions "* ]]; then
      jvm_args="${jvm_args:+$jvm_args }-XX:+UnlockExperimentalVMOptions"
    fi
    jvm_args="${jvm_args:+$jvm_args }-XX:+UseCompactObjectHeaders"
  fi
  export JAVA_OPTS="$jvm_args"
}

start_backend() {
  cd "$ROOT"
  if [[ -n "$ADMIN_USERNAME" ]]; then
    export API_CONVERT_ADMIN_USERNAME="$ADMIN_USERNAME"
  fi
  if [[ -n "$ADMIN_PASSWORD" ]]; then
    export API_CONVERT_ADMIN_PASSWORD="$ADMIN_PASSWORD"
  fi
  if [[ -n "$DB_TYPE" ]]; then
    export API_CONVERT_DB_TYPE="$DB_TYPE"
  fi
  if [[ -n "$DATASOURCE_URL" ]]; then
    export SPRING_DATASOURCE_URL="$DATASOURCE_URL"
  fi
  if [[ -n "$DATASOURCE_USERNAME" ]]; then
    export SPRING_DATASOURCE_USERNAME="$DATASOURCE_USERNAME"
  fi
  if [[ -n "$DATASOURCE_PASSWORD" ]]; then
    export SPRING_DATASOURCE_PASSWORD="$DATASOURCE_PASSWORD"
  fi
  enable_compact_object_headers
  SERVER_PORT="$BACKEND_PORT" ./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="$JAVA_OPTS"
}

start_frontend() {
  cd "$ROOT/frontend"
  if [[ ! -d node_modules ]]; then
    npm install
  fi
  npm run dev -- --host 0.0.0.0 --port "$FRONTEND_PORT"
}

case "$TARGET" in
  backend)
    start_backend
    ;;
  frontend)
    start_frontend
    ;;
  all)
    start_backend &
    backend_pid=$!
    start_frontend &
    frontend_pid=$!
    trap 'kill "$backend_pid" "$frontend_pid" 2>/dev/null || true' EXIT INT TERM
    echo "Backend:  http://localhost:$BACKEND_PORT"
    echo "Frontend: http://localhost:$FRONTEND_PORT"
    if [[ -n "$ADMIN_USERNAME" ]]; then
      echo "Admin:    $ADMIN_USERNAME"
    fi
    wait -n "$backend_pid" "$frontend_pid"
    ;;
  *)
    usage
    exit 2
    ;;
esac
