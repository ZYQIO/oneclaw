#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${OPENCLAW_ANDROID_LOCAL_HOST_BASE_URL:-}"
TOKEN="${OPENCLAW_ANDROID_LOCAL_HOST_TOKEN:-}"
USE_ADB_FORWARD="${OPENCLAW_ANDROID_LOCAL_HOST_USE_ADB_FORWARD:-0}"
ADB_BIN="${OPENCLAW_ANDROID_LOCAL_HOST_ADB_BIN:-adb}"
PORT="${OPENCLAW_ANDROID_LOCAL_HOST_PORT:-3945}"
APP_ID="${OPENCLAW_ANDROID_LOCAL_HOST_APP_ID:-ai.openclaw.app}"
CASES_CSV="${OPENCLAW_ANDROID_LOCAL_HOST_PERMISSION_CASES:-contacts,calendar,photos,notifications}"
ARTIFACT_DIR="${OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR:-$(mktemp -d -t openclaw-android-local-host-permissions.XXXXXX)}"

usage() {
  cat <<'EOF'
Usage:
  OPENCLAW_ANDROID_LOCAL_HOST_TOKEN=<token> \
  [OPENCLAW_ANDROID_LOCAL_HOST_BASE_URL=http://127.0.0.1:3945] \
  [OPENCLAW_ANDROID_LOCAL_HOST_USE_ADB_FORWARD=1] \
  [OPENCLAW_ANDROID_LOCAL_HOST_ADB_BIN=/path/to/adb] \
  [OPENCLAW_ANDROID_LOCAL_HOST_PERMISSION_CASES=contacts,calendar,photos,notifications] \
  ./apps/android/scripts/local-host-permission-smoke.sh

What it does:
  1. Verifies the local-host remote API is reachable
  2. Reads the current device permission snapshot
  3. Revokes one permission family at a time with adb
  4. Confirms /invoke returns the expected permission error
  5. Restores each permission family to its original granted/denied state

Requirements:
  - curl
  - jq
  - adb
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

has_cmd() {
  local name=$1
  if [[ "$name" == */* || "$name" == *:* ]]; then
    [[ -x "$name" || -f "$name" ]]
    return
  fi
  command -v "$name" >/dev/null 2>&1
}

require_command() {
  local name=$1
  if ! has_cmd "$name"; then
    echo "$name required but missing." >&2
    exit 1
  fi
}

require_command curl
require_command jq
require_command "$ADB_BIN"

if [[ -z "$TOKEN" ]]; then
  echo "OPENCLAW_ANDROID_LOCAL_HOST_TOKEN is required." >&2
  usage >&2
  exit 1
fi

device_count="$("$ADB_BIN" devices | awk 'NR>1 && $2=="device" {c+=1} END {print c+0}')"
if [[ "$device_count" -lt 1 ]]; then
  echo "No connected Android device (adb state=device)." >&2
  exit 1
fi

if [[ "$USE_ADB_FORWARD" == "1" ]]; then
  "$ADB_BIN" forward "tcp:$PORT" "tcp:$PORT" >/dev/null
  if [[ -z "$BASE_URL" ]]; then
    BASE_URL="http://127.0.0.1:$PORT"
  fi
fi

if [[ -z "$BASE_URL" ]]; then
  BASE_URL="http://127.0.0.1:$PORT"
fi
BASE_URL="${BASE_URL%/}"

mkdir -p "$ARTIFACT_DIR"
AUTH_HEADER="Authorization: Bearer $TOKEN"
STATUS_JSON="$ARTIFACT_DIR/status.json"
SDK_INT="$("$ADB_BIN" shell getprop ro.build.version.sdk | tr -d '\r')"
RESTORE_STATE_FILE="$ARTIFACT_DIR/restore-state.tsv"
: > "$RESTORE_STATE_FILE"

get_json() {
  local url=$1
  curl --fail --silent --show-error \
    -H "$AUTH_HEADER" \
    "$url"
}

post_invoke() {
  local body=$1
  local response_file=$2
  local status_file=$3
  curl --silent --show-error \
    -o "$response_file" \
    -w '%{http_code}' \
    -H "$AUTH_HEADER" \
    -H 'Content-Type: application/json' \
    -X POST \
    "$BASE_URL/api/local-host/v1/invoke" \
    -d "$body" >"$status_file"
}

permission_snapshot() {
  local output_file=$1
  post_invoke '{"command":"device.permissions"}' "$output_file" "$output_file.status"
  if [[ "$(cat "$output_file.status")" != "200" ]]; then
    echo "device.permissions failed." >&2
    cat "$output_file" >&2
    exit 1
  fi
}

permission_granted() {
  local snapshot_file=$1
  local category=$2
  local status
  status="$(jq -r --arg key "$category" '.payload.permissions[$key].status // "unknown"' "$snapshot_file")"
  [[ "$status" == "granted" ]] && echo "true" || echo "false"
}

reconcile_permissions() {
  local desired_state=$1
  shift
  local permissions=("$@")
  local action="revoke"
  if [[ "$desired_state" == "true" ]]; then
    action="grant"
  fi
  for permission in "${permissions[@]}"; do
    if ! "$ADB_BIN" shell pm "$action" "$APP_ID" "$permission" >/dev/null; then
      echo "Failed to $action $permission for $APP_ID via adb shell pm." >&2
      return 1
    fi
  done
}

cleanup() {
  if [[ ! -f "$RESTORE_STATE_FILE" ]]; then
    return
  fi
  while IFS=$'\t' read -r desired permission; do
    [[ -z "${desired:-}" || -z "${permission:-}" ]] && continue
    local action="revoke"
    if [[ "$desired" == "true" ]]; then
      action="grant"
    fi
    "$ADB_BIN" shell pm "$action" "$APP_ID" "$permission" >/dev/null 2>&1 || true
  done <"$RESTORE_STATE_FILE"
}

trap cleanup EXIT

register_restore_permissions() {
  local desired_state=$1
  shift
  local permissions=("$@")
  for permission in "${permissions[@]}"; do
    if ! grep -Fq -- "$desired_state"$'\t'"$permission" "$RESTORE_STATE_FILE"; then
      printf '%s\t%s\n' "$desired_state" "$permission" >>"$RESTORE_STATE_FILE"
    fi
  done
}

configure_case() {
  local case_name=$1
  case "$case_name" in
    contacts)
      CASE_COMMAND="contacts.search"
      CASE_PARAMS='{"query":"a","limit":1}'
      CASE_EXPECTED_CODE="CONTACTS_PERMISSION_REQUIRED"
      CASE_EXPECTED_MESSAGE="grant Contacts permission"
      CASE_CATEGORY="contacts"
      CASE_PERMISSIONS=("android.permission.READ_CONTACTS")
      ;;
    calendar)
      CASE_COMMAND="calendar.events"
      CASE_PARAMS='{"limit":1}'
      CASE_EXPECTED_CODE="CALENDAR_PERMISSION_REQUIRED"
      CASE_EXPECTED_MESSAGE="grant Calendar permission"
      CASE_CATEGORY="calendar"
      CASE_PERMISSIONS=("android.permission.READ_CALENDAR")
      ;;
    photos)
      CASE_COMMAND="photos.latest"
      CASE_PARAMS='{"limit":1}'
      CASE_EXPECTED_CODE="PHOTOS_PERMISSION_REQUIRED"
      CASE_EXPECTED_MESSAGE="grant Photos permission"
      CASE_CATEGORY="photos"
      if [[ "${SDK_INT:-0}" -ge 33 ]]; then
        CASE_PERMISSIONS=("android.permission.READ_MEDIA_IMAGES")
      else
        CASE_PERMISSIONS=("android.permission.READ_EXTERNAL_STORAGE")
      fi
      ;;
    notifications)
      CASE_COMMAND="system.notify"
      CASE_PARAMS='{"title":"Permission smoke","body":"This should fail while notifications permission is revoked."}'
      CASE_EXPECTED_CODE="NOT_AUTHORIZED"
      CASE_EXPECTED_MESSAGE="NOT_AUTHORIZED: notifications"
      CASE_CATEGORY="notifications"
      if [[ "${SDK_INT:-0}" -lt 33 ]]; then
        CASE_SKIP_REASON="POST_NOTIFICATIONS is not runtime-gated before Android 13."
      else
        CASE_PERMISSIONS=("android.permission.POST_NOTIFICATIONS")
      fi
      ;;
    *)
      echo "Unknown permission case: $case_name" >&2
      exit 1
      ;;
  esac
}

echo "local_host.base_url=$BASE_URL"
echo "local_host.permission_smoke=starting"
echo "artifacts.dir=$ARTIFACT_DIR"

get_json "$BASE_URL/api/local-host/v1/status" | tee "$STATUS_JSON" >/dev/null
if [[ "$(jq -r '.ok // false' "$STATUS_JSON")" != "true" ]]; then
  echo "Remote /status returned ok=false." >&2
  cat "$STATUS_JSON" >&2
  exit 1
fi

IFS=',' read -r -a CASES <<<"$CASES_CSV"
for raw_case in "${CASES[@]}"; do
  CASE_NAME="$(echo "$raw_case" | xargs)"
  [[ -z "$CASE_NAME" ]] && continue

  CASE_COMMAND=""
  CASE_PARAMS=""
  CASE_EXPECTED_CODE=""
  CASE_EXPECTED_MESSAGE=""
  CASE_CATEGORY=""
  CASE_PERMISSIONS=()
  CASE_SKIP_REASON=""
  configure_case "$CASE_NAME"

  if [[ -n "$CASE_SKIP_REASON" ]]; then
    echo "case=$CASE_NAME skipped=true reason=$CASE_SKIP_REASON"
    continue
  fi

  BEFORE_JSON="$ARTIFACT_DIR/$CASE_NAME.before.json"
  AFTER_JSON="$ARTIFACT_DIR/$CASE_NAME.after.json"
  RESPONSE_JSON="$ARTIFACT_DIR/$CASE_NAME.response.json"
  RESPONSE_STATUS="$ARTIFACT_DIR/$CASE_NAME.response.status"

  permission_snapshot "$BEFORE_JSON"
  INITIAL_GRANTED="$(permission_granted "$BEFORE_JSON" "$CASE_CATEGORY")"
  if [[ "$INITIAL_GRANTED" == "true" ]]; then
    register_restore_permissions "$INITIAL_GRANTED" "${CASE_PERMISSIONS[@]}"
    reconcile_permissions "false" "${CASE_PERMISSIONS[@]}"
    sleep 1
  fi

  post_invoke "{\"command\":\"$CASE_COMMAND\",\"params\":$CASE_PARAMS}" "$RESPONSE_JSON" "$RESPONSE_STATUS"
  HTTP_STATUS="$(cat "$RESPONSE_STATUS")"
  ERROR_CODE="$(jq -r '.error.code // ""' "$RESPONSE_JSON")"
  ERROR_MESSAGE="$(jq -r '.error.message // ""' "$RESPONSE_JSON")"

  if [[ "$HTTP_STATUS" != "400" ]]; then
    echo "case=$CASE_NAME expected_http=400 actual_http=$HTTP_STATUS" >&2
    cat "$RESPONSE_JSON" >&2
    exit 1
  fi
  if [[ "$ERROR_CODE" != "$CASE_EXPECTED_CODE" ]]; then
    echo "case=$CASE_NAME expected_code=$CASE_EXPECTED_CODE actual_code=$ERROR_CODE" >&2
    cat "$RESPONSE_JSON" >&2
    exit 1
  fi
  if [[ "$ERROR_MESSAGE" != *"$CASE_EXPECTED_MESSAGE"* ]]; then
    echo "case=$CASE_NAME expected_message_fragment=$CASE_EXPECTED_MESSAGE actual_message=$ERROR_MESSAGE" >&2
    cat "$RESPONSE_JSON" >&2
    exit 1
  fi

  if [[ "$INITIAL_GRANTED" == "true" ]]; then
    reconcile_permissions "$INITIAL_GRANTED" "${CASE_PERMISSIONS[@]}"
    sleep 1
  fi
  permission_snapshot "$AFTER_JSON"
  RESTORED_GRANTED="$(permission_granted "$AFTER_JSON" "$CASE_CATEGORY")"

  if [[ "$RESTORED_GRANTED" != "$INITIAL_GRANTED" ]]; then
    echo "case=$CASE_NAME restore_mismatch initial=$INITIAL_GRANTED restored=$RESTORED_GRANTED" >&2
    cat "$AFTER_JSON" >&2
    exit 1
  fi

  printf 'case=%s initial_granted=%s http=%s error_code=%s restored=%s\n' \
    "$CASE_NAME" "$INITIAL_GRANTED" "$HTTP_STATUS" "$ERROR_CODE" "$RESTORED_GRANTED"
done

echo "local_host.permission_smoke=completed"
