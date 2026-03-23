#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"

BASE_URL="${OPENCLAW_ANDROID_LOCAL_HOST_BASE_URL:-}"
TOKEN="${OPENCLAW_ANDROID_LOCAL_HOST_TOKEN:-}"
MESSAGE="${OPENCLAW_ANDROID_LOCAL_HOST_MESSAGE:-Summarize the current device status in one sentence.}"
WAIT_MS="${OPENCLAW_ANDROID_LOCAL_HOST_WAIT_MS:-30000}"
INVOKE_COMMAND="${OPENCLAW_ANDROID_LOCAL_HOST_INVOKE_COMMAND:-device.status}"
INVOKE_PARAMS="${OPENCLAW_ANDROID_LOCAL_HOST_INVOKE_PARAMS:-}"
USE_ADB_FORWARD="${OPENCLAW_ANDROID_LOCAL_HOST_USE_ADB_FORWARD:-0}"
PORT="${OPENCLAW_ANDROID_LOCAL_HOST_PORT:-3945}"
ARTIFACT_DIR="${OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR:-$(mktemp -d -t openclaw-android-local-host.XXXXXX)}"

usage() {
  cat <<'EOF'
Usage:
  OPENCLAW_ANDROID_LOCAL_HOST_TOKEN=<token> \
  [OPENCLAW_ANDROID_LOCAL_HOST_BASE_URL=http://127.0.0.1:3945] \
  [OPENCLAW_ANDROID_LOCAL_HOST_USE_ADB_FORWARD=1] \
  [OPENCLAW_ANDROID_LOCAL_HOST_PORT=3945] \
  [OPENCLAW_ANDROID_LOCAL_HOST_MESSAGE="Hello"] \
  [OPENCLAW_ANDROID_LOCAL_HOST_INVOKE_COMMAND=device.status] \
  [OPENCLAW_ANDROID_LOCAL_HOST_INVOKE_PARAMS='{"includePermissions":true}'] \
  ./apps/android/scripts/local-host-remote-smoke.sh

What it does:
  1. Optionally runs adb forward tcp:<port> tcp:<port>
  2. Calls /status and prints a compact readiness summary
  3. Calls /chat/send-wait
  4. Calls /invoke/capabilities
  5. Calls /invoke with the configured command

Requirements:
  - curl
  - jq
  - adb (only when OPENCLAW_ANDROID_LOCAL_HOST_USE_ADB_FORWARD=1)
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "curl required but missing." >&2
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "jq required but missing." >&2
  exit 1
fi

if [[ "$USE_ADB_FORWARD" == "1" ]]; then
  if ! command -v adb >/dev/null 2>&1; then
    echo "adb required when OPENCLAW_ANDROID_LOCAL_HOST_USE_ADB_FORWARD=1." >&2
    exit 1
  fi
  device_count="$(adb devices | awk 'NR>1 && $2=="device" {c+=1} END {print c+0}')"
  if [[ "$device_count" -lt 1 ]]; then
    echo "No connected Android device (adb state=device)." >&2
    exit 1
  fi
  adb forward "tcp:$PORT" "tcp:$PORT" >/dev/null
  if [[ -z "$BASE_URL" ]]; then
    BASE_URL="http://127.0.0.1:$PORT"
  fi
fi

if [[ -z "$BASE_URL" ]]; then
  BASE_URL="http://127.0.0.1:$PORT"
fi

BASE_URL="${BASE_URL%/}"

if [[ -z "$TOKEN" ]]; then
  echo "OPENCLAW_ANDROID_LOCAL_HOST_TOKEN is required." >&2
  usage >&2
  exit 1
fi

auth_header="Authorization: Bearer $TOKEN"
mkdir -p "$ARTIFACT_DIR"
status_json="$ARTIFACT_DIR/status.json"
chat_json="$ARTIFACT_DIR/chat.json"
caps_json="$ARTIFACT_DIR/capabilities.json"
invoke_json="$ARTIFACT_DIR/invoke.json"

post_json() {
  local url=$1
  local body=$2
  curl --fail --silent --show-error \
    -H "$auth_header" \
    -H 'Content-Type: application/json' \
    -X POST \
    "$url" \
    -d "$body"
}

get_json() {
  local url=$1
  curl --fail --silent --show-error \
    -H "$auth_header" \
    "$url"
}

echo "local_host.base_url=$BASE_URL"
echo "local_host.smoke=starting"

get_json "$BASE_URL/api/local-host/v1/status" | tee "$status_json" >/dev/null

status_ok="$(jq -r '.ok // false' "$status_json")"
status_mode="$(jq -r '.mode // ""' "$status_json")"
status_codex="$(jq -r '.host.codexAuthConfigured // false' "$status_json")"
status_sessions="$(jq -r '.host.sessionCount // 0' "$status_json")"
status_runs="$(jq -r '.host.activeRunCount // 0' "$status_json")"
status_advanced="$(jq -r '.remoteAccess.advancedEnabled // false' "$status_json")"
status_write="$(jq -r '.remoteAccess.writeEnabled // false' "$status_json")"

if [[ "$status_ok" != "true" ]]; then
  echo "Remote /status returned ok=false." >&2
  cat "$status_json" >&2
  exit 1
fi

printf 'status.ok=%s mode=%s codex=%s sessions=%s active_runs=%s advanced=%s write=%s\n' \
  "$status_ok" "$status_mode" "$status_codex" "$status_sessions" "$status_runs" "$status_advanced" "$status_write"

get_json "$BASE_URL/api/local-host/v1/invoke/capabilities" | tee "$caps_json" >/dev/null

if ! jq -e --arg cmd "$INVOKE_COMMAND" '.commands | index($cmd)' "$caps_json" >/dev/null; then
  echo "Configured invoke command is not currently allowed: $INVOKE_COMMAND" >&2
  jq '.' "$caps_json" >&2
  exit 1
fi

echo "invoke.command=$INVOKE_COMMAND allowed=true"

chat_body="$(jq -cn --arg message "$MESSAGE" --argjson waitMs "$WAIT_MS" '{message:$message, waitMs:$waitMs}')"
post_json "$BASE_URL/api/local-host/v1/chat/send-wait" "$chat_body" | tee "$chat_json" >/dev/null

chat_ok="$(jq -r '.ok // false' "$chat_json")"
chat_timed_out="$(jq -r '.timedOut // false' "$chat_json")"
chat_run_id="$(jq -r '.runId // ""' "$chat_json")"
chat_state="$(jq -r '.payload.state // .state // ""' "$chat_json")"
chat_text="$(jq -r '.payload.message.content[0].text // ""' "$chat_json")"

printf 'chat.ok=%s timed_out=%s run_id=%s state=%s\n' "$chat_ok" "$chat_timed_out" "$chat_run_id" "$chat_state"
if [[ -n "$chat_text" ]]; then
  echo "chat.preview=$chat_text"
fi

invoke_body="$(jq -cn --arg command "$INVOKE_COMMAND" --arg params "$INVOKE_PARAMS" '
  if ($params | length) > 0 then
    {command:$command, params:($params | fromjson)}
  else
    {command:$command}
  end
')"

post_json "$BASE_URL/api/local-host/v1/invoke" "$invoke_body" | tee "$invoke_json" >/dev/null

invoke_ok="$(jq -r '.ok // false' "$invoke_json")"
invoke_error="$(jq -r '.error.message // ""' "$invoke_json")"

printf 'invoke.ok=%s command=%s\n' "$invoke_ok" "$INVOKE_COMMAND"
if [[ -n "$invoke_error" ]]; then
  echo "invoke.error=$invoke_error"
fi

echo "local_host.smoke=completed"
echo "artifacts.dir=$ARTIFACT_DIR"
echo "artifacts.status=$status_json"
echo "artifacts.chat=$chat_json"
echo "artifacts.capabilities=$caps_json"
echo "artifacts.invoke=$invoke_json"
