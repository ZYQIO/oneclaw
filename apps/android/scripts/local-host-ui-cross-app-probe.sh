#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${OPENCLAW_ANDROID_LOCAL_HOST_BASE_URL:-}"
TOKEN="${OPENCLAW_ANDROID_LOCAL_HOST_TOKEN:-}"
PORT="${OPENCLAW_ANDROID_LOCAL_HOST_PORT:-3945}"
APP_PACKAGE="${OPENCLAW_ANDROID_LOCAL_HOST_UI_APP_PACKAGE:-ai.openclaw.app}"
APP_COMPONENT="${OPENCLAW_ANDROID_LOCAL_HOST_UI_APP_COMPONENT:-ai.openclaw.app/.MainActivity}"
TARGET_PACKAGE="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PACKAGE:-com.android.settings}"
OBSERVE_WINDOW_MS="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_OBSERVE_WINDOW_MS:-5000}"
POLL_INTERVAL_MS="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_POLL_INTERVAL_MS:-500}"
REQUEST_TIMEOUT_SEC="${OPENCLAW_ANDROID_LOCAL_HOST_UI_REQUEST_TIMEOUT_SEC:-5}"
RECOVERY_WAIT_MS="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_RECOVERY_WAIT_MS:-1500}"
FOLLOW_UP_WAIT_TEXT="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_WAIT_TEXT:-}"
FOLLOW_UP_WAIT_MATCH_MODE="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_WAIT_MATCH_MODE:-contains}"
FOLLOW_UP_WAIT_TIMEOUT_MS="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_WAIT_TIMEOUT_MS:-10000}"
FOLLOW_UP_WAIT_POLL_INTERVAL_MS="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_WAIT_POLL_INTERVAL_MS:-250}"
FOLLOW_UP_TAP_TEXT="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_TAP_TEXT:-}"
FOLLOW_UP_TAP_CONTENT_DESCRIPTION="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_TAP_CONTENT_DESCRIPTION:-}"
FOLLOW_UP_TAP_RESOURCE_ID="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_TAP_RESOURCE_ID:-}"
FOLLOW_UP_TAP_MATCH_MODE="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_TAP_MATCH_MODE:-contains}"
FOLLOW_UP_TAP_INDEX="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_TAP_INDEX:-0}"
FOLLOW_UP_INPUT_VALUE="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_INPUT_VALUE:-}"
FOLLOW_UP_INPUT_TEXT="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_INPUT_TEXT:-}"
FOLLOW_UP_INPUT_CONTENT_DESCRIPTION="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_INPUT_CONTENT_DESCRIPTION:-}"
FOLLOW_UP_INPUT_RESOURCE_ID="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_INPUT_RESOURCE_ID:-}"
FOLLOW_UP_INPUT_MATCH_MODE="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_INPUT_MATCH_MODE:-contains}"
FOLLOW_UP_INPUT_INDEX="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_INPUT_INDEX:-0}"
FOLLOW_UP_SETTLE_MS="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_FOLLOW_UP_SETTLE_MS:-1000}"
ARTIFACT_DIR="${OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR:-$(mktemp -d -t openclaw-android-local-host-cross-app.XXXXXX)}"
DESCRIBE_ONLY=false

usage() {
  cat <<'EOF'
Usage:
  OPENCLAW_ANDROID_LOCAL_HOST_TOKEN=<token> \
  [OPENCLAW_ANDROID_LOCAL_HOST_PORT=3945] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PACKAGE=com.android.settings] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_OBSERVE_WINDOW_MS=5000] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_POLL_INTERVAL_MS=500] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_WAIT_TEXT="Settings"] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_TAP_RESOURCE_ID="com.example:id/search"] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_INPUT_VALUE="openclaw"] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_INPUT_RESOURCE_ID="com.example:id/search_src_text"] \
  ./apps/android/scripts/local-host-ui-cross-app-probe.sh

  ./apps/android/scripts/local-host-ui-cross-app-probe.sh --describe

What it does:
  1. Requires adb plus a reachable local-host bearer token
  2. Verifies /status and /invoke/capabilities, then foregrounds OpenClaw
  3. Calls ui.launchApp for the target package
  4. Optionally runs wait/tap/inputText follow-up actions inside the launched app
  5. Polls both adb foreground activity state and remote /status over time
  6. Restores OpenClaw with adb and verifies the host is reachable again

Follow-up note:
  - The optional wait/tap/input selectors are app and OEM specific.
  - Keep the current 30s reachability proof separate from follow-up-action proof.

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

while [[ $# -gt 0 ]]; do
  case "$1" in
    --describe)
      DESCRIBE_ONLY=true
      shift
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

require_cmd() {
  local name=$1
  if ! command -v "$name" >/dev/null 2>&1; then
    echo "$name required but missing." >&2
    exit 1
  fi
}

require_cmd jq

validate_match_mode() {
  local raw=$1
  case "$raw" in
    exact | contains) ;;
    *)
      echo "Unsupported match mode: $raw (expected exact or contains)." >&2
      exit 1
      ;;
  esac
}

validate_match_mode "$FOLLOW_UP_WAIT_MATCH_MODE"
validate_match_mode "$FOLLOW_UP_TAP_MATCH_MODE"
validate_match_mode "$FOLLOW_UP_INPUT_MATCH_MODE"

follow_up_wait_requested=false
if [[ -n "$FOLLOW_UP_WAIT_TEXT" ]]; then
  follow_up_wait_requested=true
fi

follow_up_tap_requested=false
if [[ -n "$FOLLOW_UP_TAP_TEXT$FOLLOW_UP_TAP_CONTENT_DESCRIPTION$FOLLOW_UP_TAP_RESOURCE_ID" ]]; then
  follow_up_tap_requested=true
fi

follow_up_input_selector_requested=false
if [[ -n "$FOLLOW_UP_INPUT_TEXT$FOLLOW_UP_INPUT_CONTENT_DESCRIPTION$FOLLOW_UP_INPUT_RESOURCE_ID" ]]; then
  follow_up_input_selector_requested=true
fi

follow_up_input_requested=false
if [[ -n "$FOLLOW_UP_INPUT_VALUE" || "$follow_up_input_selector_requested" == "true" ]]; then
  follow_up_input_requested=true
fi

if [[ "$follow_up_input_selector_requested" == "true" && -z "$FOLLOW_UP_INPUT_VALUE" ]]; then
  echo "OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_INPUT_VALUE is required when input selectors are set." >&2
  exit 1
fi

follow_up_requested=false
if [[ "$follow_up_wait_requested" == "true" || "$follow_up_tap_requested" == "true" || "$follow_up_input_requested" == "true" ]]; then
  follow_up_requested=true
fi

follow_up_mode_parts=()
if [[ "$follow_up_wait_requested" == "true" ]]; then
  follow_up_mode_parts+=("wait")
fi
if [[ "$follow_up_tap_requested" == "true" ]]; then
  follow_up_mode_parts+=("tap")
fi
if [[ "$follow_up_input_requested" == "true" ]]; then
  follow_up_mode_parts+=("input")
fi
if [[ "${#follow_up_mode_parts[@]}" -eq 0 ]]; then
  follow_up_mode="none"
else
  follow_up_mode="$(IFS='+'; printf '%s' "${follow_up_mode_parts[*]}")"
fi

if [[ "$DESCRIBE_ONLY" == "true" ]]; then
  printf 'cross_app.describe=enabled\n'
  printf 'cross_app.script=%s\n' "local-host-ui-cross-app-probe.sh"
  printf 'cross_app.command=%s\n' "./apps/android/scripts/local-host-ui-cross-app-probe.sh"
  printf 'cross_app.target_package=%s\n' "$TARGET_PACKAGE"
  printf 'cross_app.follow_up_mode=%s\n' "$follow_up_mode"
  printf 'cross_app.observe_window_ms=%s\n' "$OBSERVE_WINDOW_MS"
  printf 'cross_app.poll_interval_ms=%s\n' "$POLL_INTERVAL_MS"
  printf 'cross_app.recovery_wait_ms=%s\n' "$RECOVERY_WAIT_MS"
  printf 'cross_app.run_env.OPENCLAW_ANDROID_LOCAL_HOST_PORT=%s\n' "$PORT"
  printf 'cross_app.run_env.OPENCLAW_ANDROID_LOCAL_HOST_UI_APP_PACKAGE=%s\n' "$APP_PACKAGE"
  printf 'cross_app.run_env.OPENCLAW_ANDROID_LOCAL_HOST_UI_APP_COMPONENT=%s\n' "$APP_COMPONENT"
  printf 'cross_app.run_env.OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PACKAGE=%s\n' "$TARGET_PACKAGE"
  printf 'cross_app.run_env.OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_OBSERVE_WINDOW_MS=%s\n' "$OBSERVE_WINDOW_MS"
  printf 'cross_app.run_env.OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_POLL_INTERVAL_MS=%s\n' "$POLL_INTERVAL_MS"
  printf 'cross_app.run_env.OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_RECOVERY_WAIT_MS=%s\n' "$RECOVERY_WAIT_MS"
  printf 'cross_app.follow_up.wait_requested=%s\n' "$follow_up_wait_requested"
  printf 'cross_app.follow_up.tap_requested=%s\n' "$follow_up_tap_requested"
  printf 'cross_app.follow_up.input_requested=%s\n' "$follow_up_input_requested"
  printf 'cross_app.follow_up.input_selector_requested=%s\n' "$follow_up_input_selector_requested"
  printf 'cross_app.follow_up.wait_match_mode=%s\n' "$FOLLOW_UP_WAIT_MATCH_MODE"
  printf 'cross_app.follow_up.tap_match_mode=%s\n' "$FOLLOW_UP_TAP_MATCH_MODE"
  printf 'cross_app.follow_up.input_match_mode=%s\n' "$FOLLOW_UP_INPUT_MATCH_MODE"
  printf 'cross_app.follow_up.settle_ms=%s\n' "$FOLLOW_UP_SETTLE_MS"
  printf 'cross_app.follow_up.wait_text=%s\n' "${FOLLOW_UP_WAIT_TEXT:-<empty>}"
  printf 'cross_app.follow_up.tap_text=%s\n' "${FOLLOW_UP_TAP_TEXT:-<empty>}"
  printf 'cross_app.follow_up.input_value=%s\n' "${FOLLOW_UP_INPUT_VALUE:-<empty>}"
  exit 0
fi

if [[ -z "$TOKEN" ]]; then
  echo "OPENCLAW_ANDROID_LOCAL_HOST_TOKEN is required." >&2
  usage >&2
  exit 1
fi

require_cmd curl
require_cmd adb

follow_up_requested_count=0
if [[ "$follow_up_wait_requested" == "true" ]]; then
  follow_up_requested_count=$((follow_up_requested_count + 1))
fi
if [[ "$follow_up_tap_requested" == "true" ]]; then
  follow_up_requested_count=$((follow_up_requested_count + 1))
fi
if [[ "$follow_up_input_requested" == "true" ]]; then
  follow_up_requested_count=$((follow_up_requested_count + 1))
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
BASE_URL="${BASE_URL%/}"

AUTH_HEADER="Authorization: Bearer $TOKEN"
mkdir -p "$ARTIFACT_DIR"

STATUS_JSON="$ARTIFACT_DIR/status.json"
CAPABILITIES_JSON="$ARTIFACT_DIR/capabilities.json"
LAUNCH_SELF_JSON="$ARTIFACT_DIR/ui-launch-self.json"
LAUNCH_TARGET_JSON="$ARTIFACT_DIR/ui-launch-target.json"
TIMELINE_JSONL="$ARTIFACT_DIR/timeline.jsonl"
FOLLOW_UP_WAIT_JSON="$ARTIFACT_DIR/ui-follow-up-wait.json"
FOLLOW_UP_TAP_JSON="$ARTIFACT_DIR/ui-follow-up-tap.json"
FOLLOW_UP_INPUT_JSON="$ARTIFACT_DIR/ui-follow-up-input.json"
FOLLOW_UP_STATE_JSON="$ARTIFACT_DIR/ui-follow-up-state.json"
RECOVERY_STATUS_JSON="$ARTIFACT_DIR/recovery-status.json"
RECOVERY_STATUS_CODE="$ARTIFACT_DIR/recovery-status.code"
RECOVERY_STATE_JSON="$ARTIFACT_DIR/recovery-state.json"
SUMMARY_JSON="$ARTIFACT_DIR/summary.json"

sleep_seconds_from_ms() {
  local ms=$1
  awk "BEGIN { printf \"%.3f\", $ms / 1000 }"
}

get_json() {
  local url=$1
  curl --fail --silent --show-error --max-time "$REQUEST_TIMEOUT_SEC" \
    -H "$AUTH_HEADER" \
    "$url"
}

post_json() {
  local url=$1
  local body=$2
  local max_time=${3:-$REQUEST_TIMEOUT_SEC}
  curl --fail --silent --show-error --max-time "$max_time" \
    -H "$AUTH_HEADER" \
    -H 'Content-Type: application/json' \
    -X POST \
    "$url" \
    -d "$body"
}

invoke_command() {
  local command=$1
  local params_json=${2:-}
  local output_file=$3
  local max_time=${4:-$REQUEST_TIMEOUT_SEC}
  local body
  body="$(jq -cn --arg command "$command" --arg params "$params_json" '
    if ($params | length) > 0 then
      {command:$command, params:($params | fromjson)}
    else
      {command:$command}
    end
  ')"
  post_json "$BASE_URL/api/local-host/v1/invoke" "$body" "$max_time" | tee "$output_file" >/dev/null
}

status_probe() {
  local response_file=$1
  local code_file=$2
  curl --silent --show-error --max-time "$REQUEST_TIMEOUT_SEC" \
    -o "$response_file" \
    -w '%{http_code}' \
    -H "$AUTH_HEADER" \
    "$BASE_URL/api/local-host/v1/status" >"$code_file" || true
}

current_top_activity_line() {
  local activity_dump
  activity_dump="$(adb shell dumpsys activity activities | tr -d '\r')"
  awk '/topResumedActivity/ {print; exit}' <<<"$activity_dump"
}

current_focus_line() {
  local window_dump
  window_dump="$(adb shell dumpsys window windows | tr -d '\r')"
  awk '/mCurrentFocus=/ {print; exit}' <<<"$window_dump"
}

current_top_package() {
  local top_line=$1
  if [[ -z "$top_line" ]]; then
    printf '%s' ""
    return 0
  fi
  printf '%s\n' "$top_line" | sed -n 's/.* u0 \([^/ ]*\)\/.*/\1/p'
}

assert_allowed_command() {
  local command=$1
  if ! jq -e --arg command "$command" '.commands | index($command)' "$CAPABILITIES_JSON" >/dev/null; then
    echo "Required UI command is not enabled for remote access: $command" >&2
    jq '.' "$CAPABILITIES_JSON" >&2
    exit 1
  fi
}

echo "local_host.base_url=$BASE_URL"
echo "local_host.ui_cross_app_probe=starting"
echo "artifacts.dir=$ARTIFACT_DIR"

get_json "$BASE_URL/api/local-host/v1/status" | tee "$STATUS_JSON" >/dev/null
if [[ "$(jq -r '.ok // false' "$STATUS_JSON")" != "true" ]]; then
  echo "Remote /status returned ok=false." >&2
  cat "$STATUS_JSON" >&2
  exit 1
fi

status_ui_available="$(jq -r '.host.uiAutomationAvailable // false' "$STATUS_JSON")"
status_ui_enabled="$(jq -r '.host.uiAutomation.enabled // false' "$STATUS_JSON")"
status_ui_connected="$(jq -r '.host.uiAutomation.serviceConnected // false' "$STATUS_JSON")"
status_write="$(jq -r '.remoteAccess.writeEnabled // false' "$STATUS_JSON")"
if [[ "$status_ui_available" != "true" || "$status_ui_enabled" != "true" || "$status_ui_connected" != "true" || "$status_write" != "true" ]]; then
  echo "UI automation or write-tier readiness is missing." >&2
  jq '.' "$STATUS_JSON" >&2
  exit 1
fi

get_json "$BASE_URL/api/local-host/v1/invoke/capabilities" | tee "$CAPABILITIES_JSON" >/dev/null
for required_command in ui.launchApp ui.state; do
  assert_allowed_command "$required_command"
done
if [[ "$follow_up_wait_requested" == "true" ]]; then
  assert_allowed_command "ui.waitForText"
fi
if [[ "$follow_up_tap_requested" == "true" ]]; then
  assert_allowed_command "ui.tap"
fi
if [[ "$follow_up_input_requested" == "true" ]]; then
  assert_allowed_command "ui.inputText"
fi

invoke_command "ui.launchApp" "$(jq -cn --arg packageName "$APP_PACKAGE" '{packageName:$packageName}')" "$LAUNCH_SELF_JSON"
if ! jq -e --arg package "$APP_PACKAGE" '
  .ok == true and
  .payload.action == "launchApp" and
  .payload.launched == true and
  .payload.packageName == $package
' "$LAUNCH_SELF_JSON" >/dev/null; then
  echo "ui.launchApp failed to foreground OpenClaw before the cross-app probe." >&2
  jq '.' "$LAUNCH_SELF_JSON" >&2
  exit 1
fi

invoke_command "ui.launchApp" "$(jq -cn --arg packageName "$TARGET_PACKAGE" '{packageName:$packageName}')" "$LAUNCH_TARGET_JSON"
if ! jq -e --arg package "$TARGET_PACKAGE" '
  .ok == true and
  .payload.action == "launchApp" and
  .payload.launched == true and
  .payload.packageName == $package
' "$LAUNCH_TARGET_JSON" >/dev/null; then
  echo "ui.launchApp failed for the target cross-app package." >&2
  jq '.' "$LAUNCH_TARGET_JSON" >&2
  exit 1
fi

follow_up_wait_ok=false
follow_up_wait_matched_text=""
follow_up_tap_ok=false
follow_up_tap_strategy=""
follow_up_input_ok=false
follow_up_input_strategy=""
follow_up_state_ok=false
follow_up_state_package=""

if [[ "$follow_up_requested" == "true" ]]; then
  echo "cross_app.follow_up=requested"

  if [[ "$follow_up_wait_requested" == "true" ]]; then
    follow_up_wait_request_timeout_sec="$(
      awk "BEGIN { printf \"%.0f\", ($FOLLOW_UP_WAIT_TIMEOUT_MS / 1000) + 5 }"
    )"
    invoke_command \
      "ui.waitForText" \
      "$(jq -cn \
        --arg text "$FOLLOW_UP_WAIT_TEXT" \
        --arg packageName "$TARGET_PACKAGE" \
        --arg matchMode "$FOLLOW_UP_WAIT_MATCH_MODE" \
        --argjson timeoutMs "$FOLLOW_UP_WAIT_TIMEOUT_MS" \
        --argjson pollIntervalMs "$FOLLOW_UP_WAIT_POLL_INTERVAL_MS" \
        '{text:$text, packageName:$packageName, matchMode:$matchMode, timeoutMs:$timeoutMs, pollIntervalMs:$pollIntervalMs}')" \
      "$FOLLOW_UP_WAIT_JSON" \
      "$follow_up_wait_request_timeout_sec"
    if ! jq -e --arg package "$TARGET_PACKAGE" '
      .ok == true and
      .payload.packageName == $package and
      .payload.wait.matched == true
    ' "$FOLLOW_UP_WAIT_JSON" >/dev/null; then
      echo "Cross-app follow-up wait did not match inside the target package." >&2
      jq '.' "$FOLLOW_UP_WAIT_JSON" >&2
      exit 1
    fi
    follow_up_wait_ok=true
    follow_up_wait_matched_text="$(jq -r '.payload.wait.matchedText // ""' "$FOLLOW_UP_WAIT_JSON" 2>/dev/null || printf '%s' "")"
  fi

  if [[ "$follow_up_tap_requested" == "true" ]]; then
    invoke_command \
      "ui.tap" \
      "$(jq -cn \
        --arg text "$FOLLOW_UP_TAP_TEXT" \
        --arg contentDescription "$FOLLOW_UP_TAP_CONTENT_DESCRIPTION" \
        --arg resourceId "$FOLLOW_UP_TAP_RESOURCE_ID" \
        --arg packageName "$TARGET_PACKAGE" \
        --arg matchMode "$FOLLOW_UP_TAP_MATCH_MODE" \
        --argjson index "$FOLLOW_UP_TAP_INDEX" \
        '{
          packageName:$packageName,
          matchMode:$matchMode,
          index:$index
        }
        + (if $text != "" then {text:$text} else {} end)
        + (if $contentDescription != "" then {contentDescription:$contentDescription} else {} end)
        + (if $resourceId != "" then {resourceId:$resourceId} else {} end)')" \
      "$FOLLOW_UP_TAP_JSON"
    if ! jq -e --arg package "$TARGET_PACKAGE" '
      .ok == true and
      .payload.action == "tap" and
      .payload.performed == true and
      .payload.packageName == $package
    ' "$FOLLOW_UP_TAP_JSON" >/dev/null; then
      echo "Cross-app follow-up tap did not complete inside the target package." >&2
      jq '.' "$FOLLOW_UP_TAP_JSON" >&2
      exit 1
    fi
    follow_up_tap_ok=true
    follow_up_tap_strategy="$(jq -r '.payload.strategy // ""' "$FOLLOW_UP_TAP_JSON" 2>/dev/null || printf '%s' "")"
  fi

  if [[ "$follow_up_input_requested" == "true" ]]; then
    invoke_command \
      "ui.inputText" \
      "$(jq -cn \
        --arg value "$FOLLOW_UP_INPUT_VALUE" \
        --arg text "$FOLLOW_UP_INPUT_TEXT" \
        --arg contentDescription "$FOLLOW_UP_INPUT_CONTENT_DESCRIPTION" \
        --arg resourceId "$FOLLOW_UP_INPUT_RESOURCE_ID" \
        --arg packageName "$TARGET_PACKAGE" \
        --arg matchMode "$FOLLOW_UP_INPUT_MATCH_MODE" \
        --argjson index "$FOLLOW_UP_INPUT_INDEX" \
        '{
          value:$value,
          packageName:$packageName,
          matchMode:$matchMode,
          index:$index
        }
        + (if $text != "" then {text:$text} else {} end)
        + (if $contentDescription != "" then {contentDescription:$contentDescription} else {} end)
        + (if $resourceId != "" then {resourceId:$resourceId} else {} end)')" \
      "$FOLLOW_UP_INPUT_JSON"
    if ! jq -e --arg package "$TARGET_PACKAGE" '
      .ok == true and
      .payload.action == "inputText" and
      .payload.performed == true and
      .payload.packageName == $package
    ' "$FOLLOW_UP_INPUT_JSON" >/dev/null; then
      echo "Cross-app follow-up input did not complete inside the target package." >&2
      jq '.' "$FOLLOW_UP_INPUT_JSON" >&2
      exit 1
    fi
    follow_up_input_ok=true
    follow_up_input_strategy="$(jq -r '.payload.strategy // ""' "$FOLLOW_UP_INPUT_JSON" 2>/dev/null || printf '%s' "")"
  fi

  if [[ "$follow_up_tap_requested" == "true" || "$follow_up_input_requested" == "true" ]]; then
    sleep "$(sleep_seconds_from_ms "$FOLLOW_UP_SETTLE_MS")"
  fi

  invoke_command "ui.state" "" "$FOLLOW_UP_STATE_JSON"
  if ! jq -e --arg package "$TARGET_PACKAGE" '
    .ok == true and
    .payload.packageName == $package
  ' "$FOLLOW_UP_STATE_JSON" >/dev/null; then
    echo "Cross-app follow-up state check no longer reports the target package on top." >&2
    jq '.' "$FOLLOW_UP_STATE_JSON" >&2
    exit 1
  fi
  follow_up_state_ok=true
  follow_up_state_package="$(jq -r '.payload.packageName // ""' "$FOLLOW_UP_STATE_JSON" 2>/dev/null || printf '%s' "")"
fi

rounds=$(( (OBSERVE_WINDOW_MS + POLL_INTERVAL_MS - 1) / POLL_INTERVAL_MS ))
status_success_count=0
target_top_count=0
first_target_round=-1
first_status_failure_round=-1
: >"$TIMELINE_JSONL"

for ((round = 1; round <= rounds; round++)); do
  probe_response="$ARTIFACT_DIR/status-round-$round.json"
  probe_code="$ARTIFACT_DIR/status-round-$round.code"
  status_probe "$probe_response" "$probe_code"
  http_code="$(cat "$probe_code" 2>/dev/null || printf '%s' "000")"
  status_ok=false
  if [[ "$http_code" == "200" ]]; then
    status_ok="$(jq -r '.ok // false' "$probe_response" 2>/dev/null || printf '%s' "false")"
  fi

  top_line="$(current_top_activity_line)"
  focus_line="$(current_focus_line)"
  top_package="$(current_top_package "$top_line")"
  target_on_top=false
  if [[ "$top_package" == "$TARGET_PACKAGE" ]]; then
    target_on_top=true
    target_top_count=$((target_top_count + 1))
    if [[ "$first_target_round" -lt 0 ]]; then
      first_target_round=$round
    fi
  fi

  if [[ "$status_ok" == "true" ]]; then
    status_success_count=$((status_success_count + 1))
  elif [[ "$first_status_failure_round" -lt 0 ]]; then
    first_status_failure_round=$round
  fi

  jq -n \
    --argjson round "$round" \
    --arg httpCode "$http_code" \
    --argjson statusOk "$( [[ "$status_ok" == "true" ]] && printf 'true' || printf 'false' )" \
    --arg topPackage "$top_package" \
    --arg topLine "$top_line" \
    --arg focusLine "$focus_line" \
    --arg targetPackage "$TARGET_PACKAGE" \
    --argjson targetOnTop "$( [[ "$target_on_top" == "true" ]] && printf 'true' || printf 'false' )" \
    '{
      round: $round,
      httpCode: $httpCode,
      statusOk: $statusOk,
      topPackage: (if $topPackage == "" then null else $topPackage end),
      topLine: (if $topLine == "" then null else $topLine end),
      focusLine: (if $focusLine == "" then null else $focusLine end),
      targetPackage: $targetPackage,
      targetOnTop: $targetOnTop
    }' >>"$TIMELINE_JSONL"
  printf '\n' >>"$TIMELINE_JSONL"

  if (( round < rounds )); then
    sleep "$(sleep_seconds_from_ms "$POLL_INTERVAL_MS")"
  fi
done

classification="launch_accepted_not_foregrounded"
if [[ "$target_top_count" -gt 0 && "$first_status_failure_round" -lt 0 ]]; then
  classification="foregrounded_host_reachable"
elif [[ "$target_top_count" -gt 0 && "$first_status_failure_round" -ge 0 ]]; then
  classification="foregrounded_then_remote_unreachable"
fi

adb shell am start -n "$APP_COMPONENT" >/dev/null
sleep "$(sleep_seconds_from_ms "$RECOVERY_WAIT_MS")"
status_probe "$RECOVERY_STATUS_JSON" "$RECOVERY_STATUS_CODE"
recovery_http_code="$(cat "$RECOVERY_STATUS_CODE" 2>/dev/null || printf '%s' "000")"
recovery_ok=false
if [[ "$recovery_http_code" == "200" ]]; then
  recovery_ok="$(jq -r '.ok // false' "$RECOVERY_STATUS_JSON" 2>/dev/null || printf '%s' "false")"
fi

invoke_command "ui.state" "" "$RECOVERY_STATE_JSON"
recovered_package="$(jq -r '.payload.packageName // ""' "$RECOVERY_STATE_JSON" 2>/dev/null || printf '%s' "")"

jq -n \
  --arg appPackage "$APP_PACKAGE" \
  --arg targetPackage "$TARGET_PACKAGE" \
  --arg classification "$classification" \
  --argjson observeWindowMs "$OBSERVE_WINDOW_MS" \
  --argjson pollIntervalMs "$POLL_INTERVAL_MS" \
  --argjson rounds "$rounds" \
  --argjson statusSuccessCount "$status_success_count" \
  --argjson targetTopCount "$target_top_count" \
  --argjson firstTargetRound "$first_target_round" \
  --argjson firstStatusFailureRound "$first_status_failure_round" \
  --arg recoveryHttpCode "$recovery_http_code" \
  --argjson recoveryOk "$( [[ "$recovery_ok" == "true" ]] && printf 'true' || printf 'false' )" \
  --arg recoveredPackage "$recovered_package" \
  --arg followUpWaitText "$FOLLOW_UP_WAIT_TEXT" \
  --arg followUpWaitMatchedText "$follow_up_wait_matched_text" \
  --arg followUpTapText "$FOLLOW_UP_TAP_TEXT" \
  --arg followUpTapContentDescription "$FOLLOW_UP_TAP_CONTENT_DESCRIPTION" \
  --arg followUpTapResourceId "$FOLLOW_UP_TAP_RESOURCE_ID" \
  --arg followUpTapStrategy "$follow_up_tap_strategy" \
  --arg followUpInputText "$FOLLOW_UP_INPUT_TEXT" \
  --arg followUpInputContentDescription "$FOLLOW_UP_INPUT_CONTENT_DESCRIPTION" \
  --arg followUpInputResourceId "$FOLLOW_UP_INPUT_RESOURCE_ID" \
  --arg followUpInputStrategy "$follow_up_input_strategy" \
  --arg followUpStatePackage "$follow_up_state_package" \
  --arg followUpMode "$follow_up_mode" \
  --argjson followUpRequested "$( [[ "$follow_up_requested" == "true" ]] && printf 'true' || printf 'false' )" \
  --argjson followUpWaitRequested "$( [[ "$follow_up_wait_requested" == "true" ]] && printf 'true' || printf 'false' )" \
  --argjson followUpWaitOk "$( [[ "$follow_up_wait_ok" == "true" ]] && printf 'true' || printf 'false' )" \
  --argjson followUpTapRequested "$( [[ "$follow_up_tap_requested" == "true" ]] && printf 'true' || printf 'false' )" \
  --argjson followUpTapOk "$( [[ "$follow_up_tap_ok" == "true" ]] && printf 'true' || printf 'false' )" \
  --argjson followUpInputRequested "$( [[ "$follow_up_input_requested" == "true" ]] && printf 'true' || printf 'false' )" \
  --argjson followUpInputOk "$( [[ "$follow_up_input_ok" == "true" ]] && printf 'true' || printf 'false' )" \
  --argjson followUpStateOk "$( [[ "$follow_up_state_ok" == "true" ]] && printf 'true' || printf 'false' )" \
  --argjson followUpRequestedCount "$follow_up_requested_count" \
  --argjson followUpInputValueLength "${#FOLLOW_UP_INPUT_VALUE}" \
  '{
    appPackage: $appPackage,
    targetPackage: $targetPackage,
    classification: $classification,
    observeWindowMs: $observeWindowMs,
    pollIntervalMs: $pollIntervalMs,
    rounds: $rounds,
    statusSuccessCount: $statusSuccessCount,
    targetTopCount: $targetTopCount,
    firstTargetRound: $firstTargetRound,
    firstStatusFailureRound: $firstStatusFailureRound,
    followUp: {
      mode: $followUpMode,
      requested: $followUpRequested,
      requestedCount: $followUpRequestedCount,
      waitRequested: $followUpWaitRequested,
      waitText: ($followUpWaitText | select(length > 0)),
      waitMatchedText: ($followUpWaitMatchedText | select(length > 0)),
      waitOk: $followUpWaitOk,
      tapRequested: $followUpTapRequested,
      tapText: ($followUpTapText | select(length > 0)),
      tapContentDescription: ($followUpTapContentDescription | select(length > 0)),
      tapResourceId: ($followUpTapResourceId | select(length > 0)),
      tapStrategy: ($followUpTapStrategy | select(length > 0)),
      tapOk: $followUpTapOk,
      inputRequested: $followUpInputRequested,
      inputText: ($followUpInputText | select(length > 0)),
      inputContentDescription: ($followUpInputContentDescription | select(length > 0)),
      inputResourceId: ($followUpInputResourceId | select(length > 0)),
      inputStrategy: ($followUpInputStrategy | select(length > 0)),
      inputValueLength: (if $followUpInputRequested then $followUpInputValueLength else null end),
      inputOk: $followUpInputOk,
      stateOk: $followUpStateOk,
      statePackage: (if $followUpStatePackage == "" then null else $followUpStatePackage end)
    },
    recovery: {
      httpCode: $recoveryHttpCode,
      ok: $recoveryOk,
      recoveredPackage: (if $recoveredPackage == "" then null else $recoveredPackage end)
    }
  }' >"$SUMMARY_JSON"

printf 'cross_app.target=%s classification=%s target_top_rounds=%s status_success_rounds=%s\n' \
  "$TARGET_PACKAGE" "$classification" "$target_top_count" "$status_success_count"
printf 'cross_app.first_target_round=%s first_status_failure_round=%s\n' \
  "$first_target_round" "$first_status_failure_round"
if [[ "$follow_up_requested" == "true" ]]; then
  printf 'cross_app.follow_up_mode=%s wait_ok=%s tap_ok=%s input_ok=%s state_ok=%s\n' \
    "$follow_up_mode" \
    "$follow_up_wait_ok" "$follow_up_tap_ok" "$follow_up_input_ok" "$follow_up_state_ok"
fi
printf 'recovery.http_code=%s recovery.ok=%s recovered_package=%s\n' \
  "$recovery_http_code" "$recovery_ok" "${recovered_package:-unknown}"

echo "local_host.ui_cross_app_probe=completed"
echo "artifacts.status=$STATUS_JSON"
echo "artifacts.timeline=$TIMELINE_JSONL"
echo "artifacts.summary=$SUMMARY_JSON"
