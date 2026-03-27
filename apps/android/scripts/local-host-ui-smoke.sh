#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${OPENCLAW_ANDROID_LOCAL_HOST_BASE_URL:-}"
TOKEN="${OPENCLAW_ANDROID_LOCAL_HOST_TOKEN:-}"
USE_ADB_FORWARD="${OPENCLAW_ANDROID_LOCAL_HOST_USE_ADB_FORWARD:-0}"
PORT="${OPENCLAW_ANDROID_LOCAL_HOST_PORT:-3945}"
APP_PACKAGE="${OPENCLAW_ANDROID_LOCAL_HOST_UI_APP_PACKAGE:-ai.openclaw.app}"
CONNECT_LABEL="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CONNECT_LABEL:-Connect}"
CHAT_LABEL="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CHAT_LABEL:-Chat}"
CHAT_READY_TEXT="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CHAT_READY_TEXT:-Select thinking level}"
EDITOR_HINT_TEXT="${OPENCLAW_ANDROID_LOCAL_HOST_UI_EDITOR_HINT_TEXT:-Type a message}"
DRAFT_VALUE="${OPENCLAW_ANDROID_LOCAL_HOST_UI_DRAFT_VALUE:-UI smoke draft}"
MATCH_MODE="${OPENCLAW_ANDROID_LOCAL_HOST_UI_MATCH_MODE:-exact}"
WAIT_TIMEOUT_MS="${OPENCLAW_ANDROID_LOCAL_HOST_UI_WAIT_TIMEOUT_MS:-10000}"
POLL_INTERVAL_MS="${OPENCLAW_ANDROID_LOCAL_HOST_UI_POLL_INTERVAL_MS:-250}"
REQUEST_TIMEOUT_SEC="${OPENCLAW_ANDROID_LOCAL_HOST_UI_REQUEST_TIMEOUT_SEC:-15}"
CROSS_APP_PACKAGE="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PACKAGE:-}"
CROSS_APP_WAIT_MS="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_WAIT_MS:-1500}"
ARTIFACT_DIR="${OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR:-$(mktemp -d -t openclaw-android-local-host-ui.XXXXXX)}"

usage() {
  cat <<'EOF'
Usage:
  OPENCLAW_ANDROID_LOCAL_HOST_TOKEN=<token> \
  [OPENCLAW_ANDROID_LOCAL_HOST_BASE_URL=http://127.0.0.1:3945] \
  [OPENCLAW_ANDROID_LOCAL_HOST_USE_ADB_FORWARD=1] \
  [OPENCLAW_ANDROID_LOCAL_HOST_PORT=3945] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_APP_PACKAGE=ai.openclaw.app] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CONNECT_LABEL=Connect] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CHAT_LABEL=Chat] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CHAT_READY_TEXT="Select thinking level"] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_EDITOR_HINT_TEXT="Type a message"] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_DRAFT_VALUE="UI smoke draft"] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PACKAGE=com.android.settings] \
  ./apps/android/scripts/local-host-ui-smoke.sh

What it does:
  1. Optionally runs adb forward tcp:<port> tcp:<port>
  2. Verifies /status plus /invoke/capabilities
  3. Uses ui.launchApp to bring OpenClaw to the foreground
  4. Uses ui.tap + ui.waitForText to move into the Chat tab
  5. Focuses the chat editor, writes a temporary draft, then clears it again
  6. Optionally runs a follow-up cross-app ui.launchApp probe

Requirements:
  - curl
  - jq
  - adb (only when OPENCLAW_ANDROID_LOCAL_HOST_USE_ADB_FORWARD=1, or when using the optional cross-app probe with adb verification)
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

require_cmd() {
  local name=$1
  if ! command -v "$name" >/dev/null 2>&1; then
    echo "$name required but missing." >&2
    exit 1
  fi
}

require_cmd curl
require_cmd jq

if [[ "$MATCH_MODE" != "exact" && "$MATCH_MODE" != "contains" ]]; then
  echo "OPENCLAW_ANDROID_LOCAL_HOST_UI_MATCH_MODE must be exact or contains." >&2
  exit 1
fi

if [[ "$USE_ADB_FORWARD" == "1" || -n "$CROSS_APP_PACKAGE" ]]; then
  require_cmd adb
fi

device_count=0
if command -v adb >/dev/null 2>&1; then
  device_count="$(adb devices | awk 'NR>1 && $2=="device" {c+=1} END {print c+0}')"
fi

if [[ "$USE_ADB_FORWARD" == "1" ]]; then
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

AUTH_HEADER="Authorization: Bearer $TOKEN"
mkdir -p "$ARTIFACT_DIR"

STATUS_JSON="$ARTIFACT_DIR/status.json"
CAPABILITIES_JSON="$ARTIFACT_DIR/capabilities.json"
LAUNCH_SELF_JSON="$ARTIFACT_DIR/ui-launch-self.json"
WAIT_CONNECT_JSON="$ARTIFACT_DIR/ui-wait-connect.json"
TAP_CHAT_JSON="$ARTIFACT_DIR/ui-tap-chat.json"
WAIT_CHAT_READY_JSON="$ARTIFACT_DIR/ui-wait-chat-ready.json"
STATE_BEFORE_JSON="$ARTIFACT_DIR/ui-state-before.json"
TAP_EDITOR_JSON="$ARTIFACT_DIR/ui-tap-editor.json"
STATE_FOCUSED_JSON="$ARTIFACT_DIR/ui-state-focused.json"
INPUT_JSON="$ARTIFACT_DIR/ui-input.json"
STATE_WRITTEN_JSON="$ARTIFACT_DIR/ui-state-written.json"
CLEAR_INPUT_JSON="$ARTIFACT_DIR/ui-input-clear.json"
STATE_AFTER_JSON="$ARTIFACT_DIR/ui-state-after.json"
SUMMARY_JSON="$ARTIFACT_DIR/summary.json"
CROSS_APP_JSON="$ARTIFACT_DIR/ui-launch-cross-app.json"
CROSS_APP_STATUS_JSON="$ARTIFACT_DIR/ui-cross-app-status.json"
CROSS_APP_STATUS_CODE="$ARTIFACT_DIR/ui-cross-app-status.code"
CROSS_APP_TOP_ACTIVITY="$ARTIFACT_DIR/ui-cross-app-top-activity.txt"

get_json() {
  local url=$1
  curl --fail --silent --show-error --max-time "$REQUEST_TIMEOUT_SEC" \
    -H "$AUTH_HEADER" \
    "$url"
}

post_json() {
  local url=$1
  local body=$2
  curl --fail --silent --show-error --max-time "$REQUEST_TIMEOUT_SEC" \
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
  local body
  body="$(jq -cn --arg command "$command" --arg params "$params_json" '
    if ($params | length) > 0 then
      {command:$command, params:($params | fromjson)}
    else
      {command:$command}
    end
  ')"
  post_json "$BASE_URL/api/local-host/v1/invoke" "$body" | tee "$output_file" >/dev/null
}

cross_app_status_probe() {
  curl --silent --show-error --max-time "$REQUEST_TIMEOUT_SEC" \
    -o "$CROSS_APP_STATUS_JSON" \
    -w '%{http_code}' \
    -H "$AUTH_HEADER" \
    "$BASE_URL/api/local-host/v1/status" >"$CROSS_APP_STATUS_CODE"
}

assert_allowed_command() {
  local command=$1
  if ! jq -e --arg command "$command" '.commands | index($command)' "$CAPABILITIES_JSON" >/dev/null; then
    echo "Required UI command is not enabled for remote access: $command" >&2
    jq '.' "$CAPABILITIES_JSON" >&2
    exit 1
  fi
}

assert_editable_present() {
  local state_file=$1
  if ! jq -e --arg package "$APP_PACKAGE" '
    .ok == true and
    .payload.packageName == $package and
    ([.payload.nodes[]? | select((.editable // false) == true)] | length) >= 1
  ' "$state_file" >/dev/null; then
    echo "Expected an editable node in $APP_PACKAGE." >&2
    jq '.' "$state_file" >&2
    exit 1
  fi
}

echo "local_host.base_url=$BASE_URL"
echo "local_host.ui_smoke=starting"
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

if [[ "$status_ui_available" != "true" || "$status_ui_enabled" != "true" || "$status_ui_connected" != "true" ]]; then
  echo "UI automation is not ready for smoke validation." >&2
  jq '.' "$STATUS_JSON" >&2
  exit 1
fi

if [[ "$status_write" != "true" ]]; then
  echo "Write remote commands must be enabled for UI smoke validation." >&2
  jq '.' "$STATUS_JSON" >&2
  exit 1
fi

printf 'status.ui_available=%s enabled=%s connected=%s write=%s\n' \
  "$status_ui_available" "$status_ui_enabled" "$status_ui_connected" "$status_write"

get_json "$BASE_URL/api/local-host/v1/invoke/capabilities" | tee "$CAPABILITIES_JSON" >/dev/null
for required_command in ui.state ui.waitForText ui.launchApp ui.tap ui.inputText; do
  assert_allowed_command "$required_command"
done
echo "capabilities.ui_commands=ui.state,ui.waitForText,ui.launchApp,ui.tap,ui.inputText"

invoke_command "ui.launchApp" "$(jq -cn --arg packageName "$APP_PACKAGE" '{packageName:$packageName}')" "$LAUNCH_SELF_JSON"
if ! jq -e --arg package "$APP_PACKAGE" '
  .ok == true and
  .payload.action == "launchApp" and
  .payload.launched == true and
  .payload.packageName == $package
' "$LAUNCH_SELF_JSON" >/dev/null; then
  echo "ui.launchApp failed to foreground OpenClaw." >&2
  jq '.' "$LAUNCH_SELF_JSON" >&2
  exit 1
fi
echo "ui.launch_app.self=true"

invoke_command \
  "ui.waitForText" \
  "$(jq -cn --arg text "$CONNECT_LABEL" --arg packageName "$APP_PACKAGE" --arg matchMode "$MATCH_MODE" --argjson timeoutMs "$WAIT_TIMEOUT_MS" --argjson pollIntervalMs "$POLL_INTERVAL_MS" '{text:$text, packageName:$packageName, matchMode:$matchMode, timeoutMs:$timeoutMs, pollIntervalMs:$pollIntervalMs}')" \
  "$WAIT_CONNECT_JSON"
if ! jq -e --arg package "$APP_PACKAGE" --arg text "$CONNECT_LABEL" '
  .ok == true and
  .payload.packageName == $package and
  .payload.wait.matched == true and
  .payload.wait.matchedText == $text
' "$WAIT_CONNECT_JSON" >/dev/null; then
  echo "ui.waitForText did not see the Connect tab label." >&2
  jq '.' "$WAIT_CONNECT_JSON" >&2
  exit 1
fi
echo "ui.wait.connect=true"

invoke_command \
  "ui.tap" \
  "$(jq -cn --arg text "$CHAT_LABEL" --arg packageName "$APP_PACKAGE" --arg matchMode "$MATCH_MODE" '{text:$text, packageName:$packageName, matchMode:$matchMode}')" \
  "$TAP_CHAT_JSON"
if ! jq -e --arg package "$APP_PACKAGE" '
  .ok == true and
  .payload.action == "tap" and
  .payload.performed == true and
  .payload.packageName == $package
' "$TAP_CHAT_JSON" >/dev/null; then
  echo "ui.tap could not move the app into the Chat tab." >&2
  jq '.' "$TAP_CHAT_JSON" >&2
  exit 1
fi
echo "ui.tap.chat=true"

invoke_command \
  "ui.waitForText" \
  "$(jq -cn --arg text "$CHAT_READY_TEXT" --arg packageName "$APP_PACKAGE" --arg matchMode "$MATCH_MODE" --argjson timeoutMs "$WAIT_TIMEOUT_MS" --argjson pollIntervalMs "$POLL_INTERVAL_MS" '{text:$text, packageName:$packageName, matchMode:$matchMode, timeoutMs:$timeoutMs, pollIntervalMs:$pollIntervalMs}')" \
  "$WAIT_CHAT_READY_JSON"
if ! jq -e --arg package "$APP_PACKAGE" --arg text "$CHAT_READY_TEXT" '
  .ok == true and
  .payload.packageName == $package and
  .payload.wait.matched == true and
  .payload.wait.matchedText == $text
' "$WAIT_CHAT_READY_JSON" >/dev/null; then
  echo "ui.waitForText did not find the expected Chat-ready text." >&2
  jq '.' "$WAIT_CHAT_READY_JSON" >&2
  exit 1
fi
echo "ui.wait.chat_ready=true"

invoke_command "ui.state" "" "$STATE_BEFORE_JSON"
if ! jq -e --arg package "$APP_PACKAGE" '.ok == true and .payload.packageName == $package' "$STATE_BEFORE_JSON" >/dev/null; then
  echo "ui.state before interaction did not report the expected package." >&2
  jq '.' "$STATE_BEFORE_JSON" >&2
  exit 1
fi
assert_editable_present "$STATE_BEFORE_JSON"

invoke_command \
  "ui.tap" \
  "$(jq -cn --arg text "$EDITOR_HINT_TEXT" --arg packageName "$APP_PACKAGE" --arg matchMode "contains" '{text:$text, packageName:$packageName, matchMode:$matchMode}')" \
  "$TAP_EDITOR_JSON"
if ! jq -e --arg package "$APP_PACKAGE" '
  .ok == true and
  .payload.action == "tap" and
  .payload.performed == true and
  .payload.packageName == $package
' "$TAP_EDITOR_JSON" >/dev/null; then
  echo "ui.tap could not focus the chat editor." >&2
  jq '.' "$TAP_EDITOR_JSON" >&2
  exit 1
fi
echo "ui.tap.editor=true"

invoke_command "ui.state" "" "$STATE_FOCUSED_JSON"
assert_editable_present "$STATE_FOCUSED_JSON"

invoke_command \
  "ui.inputText" \
  "$(jq -cn --arg value "$DRAFT_VALUE" '{value:$value}')" \
  "$INPUT_JSON"
if ! jq -e --arg package "$APP_PACKAGE" '
  .ok == true and
  .payload.action == "inputText" and
  .payload.performed == true and
  .payload.packageName == $package
' "$INPUT_JSON" >/dev/null; then
  echo "ui.inputText failed on the expected editable field." >&2
  jq '.' "$INPUT_JSON" >&2
  exit 1
fi

sleep 1
invoke_command "ui.state" "" "$STATE_WRITTEN_JSON"
if ! jq -e --arg package "$APP_PACKAGE" --arg text "$DRAFT_VALUE" '
  .ok == true and
  .payload.packageName == $package and
  ([.payload.nodes[]? | select((.editable // false) == true and (.focused // false) == true and (.text // "") == $text)] | length) >= 1
' "$STATE_WRITTEN_JSON" >/dev/null; then
  echo "Expected the focused chat editor to contain the smoke draft text." >&2
  jq '.' "$STATE_WRITTEN_JSON" >&2
  exit 1
fi

invoke_command \
  "ui.inputText" \
  "$(jq -cn --arg value "" '{value:$value}')" \
  "$CLEAR_INPUT_JSON"
if ! jq -e --arg package "$APP_PACKAGE" '
  .ok == true and
  .payload.action == "inputText" and
  .payload.performed == true and
  .payload.packageName == $package
' "$CLEAR_INPUT_JSON" >/dev/null; then
  echo "ui.inputText could not clear the temporary chat draft." >&2
  jq '.' "$CLEAR_INPUT_JSON" >&2
  exit 1
fi

sleep 1
invoke_command "ui.state" "" "$STATE_AFTER_JSON"
if ! jq -e --arg package "$APP_PACKAGE" --arg hint "$EDITOR_HINT_TEXT" '
  .ok == true and
  .payload.packageName == $package and
  ([.payload.nodes[]? | select((.editable // false) == true and (.focused // false) == true)] | length) >= 1 and
  ([.payload.visibleText[]? | select((. // "") | contains($hint))] | length) >= 1
' "$STATE_AFTER_JSON" >/dev/null; then
  echo "Expected the cleared chat editor to show its hint text again." >&2
  jq '.' "$STATE_AFTER_JSON" >&2
  exit 1
fi

cross_app_requested=false
cross_app_follow_up="skipped"
cross_app_http_code=""
top_activity_line=""

if [[ -n "$CROSS_APP_PACKAGE" ]]; then
  cross_app_requested=true
  invoke_command "ui.launchApp" "$(jq -cn --arg packageName "$CROSS_APP_PACKAGE" '{packageName:$packageName}')" "$CROSS_APP_JSON"
  if ! jq -e --arg package "$CROSS_APP_PACKAGE" '
    .ok == true and
    .payload.action == "launchApp" and
    .payload.launched == true and
    .payload.packageName == $package
  ' "$CROSS_APP_JSON" >/dev/null; then
    echo "Optional cross-app ui.launchApp probe failed." >&2
    jq '.' "$CROSS_APP_JSON" >&2
    exit 1
  fi

  sleep "$(awk "BEGIN { printf \"%.3f\", $CROSS_APP_WAIT_MS / 1000 }")"
  cross_app_status_probe || true
  cross_app_http_code="$(cat "$CROSS_APP_STATUS_CODE" 2>/dev/null || true)"
  if [[ "$cross_app_http_code" == "200" ]]; then
    cross_app_follow_up="reachable"
  else
    cross_app_follow_up="timeout_or_error"
  fi

  if [[ "$device_count" -gt 0 ]]; then
    top_activity_line="$(adb shell dumpsys activity activities | tr -d '\r' | awk '/topResumedActivity/ {print; exit}')"
    printf '%s\n' "$top_activity_line" >"$CROSS_APP_TOP_ACTIVITY"
  fi
fi

jq -n \
  --arg appPackage "$APP_PACKAGE" \
  --arg connectLabel "$CONNECT_LABEL" \
  --arg chatLabel "$CHAT_LABEL" \
  --arg chatReadyText "$CHAT_READY_TEXT" \
  --arg editorHintText "$EDITOR_HINT_TEXT" \
  --arg draftValue "$DRAFT_VALUE" \
  --arg matchMode "$MATCH_MODE" \
  --arg launchStrategy "$(jq -r '.payload.activityClassName // ""' "$LAUNCH_SELF_JSON")" \
  --arg tapStrategy "$(jq -r '.payload.strategy // ""' "$TAP_EDITOR_JSON")" \
  --arg inputStrategy "$(jq -r '.payload.strategy // ""' "$INPUT_JSON")" \
  --arg crossAppPackage "$CROSS_APP_PACKAGE" \
  --arg crossAppFollowUp "$cross_app_follow_up" \
  --arg crossAppHttpCode "$cross_app_http_code" \
  --arg topActivity "$top_activity_line" \
  --argjson crossAppRequested "$cross_app_requested" \
  '{
    appPackage: $appPackage,
    connectLabel: $connectLabel,
    chatLabel: $chatLabel,
    chatReadyText: $chatReadyText,
    editorHintText: $editorHintText,
    draftValue: $draftValue,
    matchMode: $matchMode,
    launchApp: {
      activityClassName: ($launchStrategy | select(length > 0))
    },
    tap: {
      strategy: ($tapStrategy | select(length > 0))
    },
    inputText: {
      strategy: ($inputStrategy | select(length > 0))
    },
    crossApp: {
      requested: $crossAppRequested,
      packageName: ($crossAppPackage | select(length > 0)),
      followUp: $crossAppFollowUp,
      httpCode: ($crossAppHttpCode | select(length > 0)),
      topActivity: ($topActivity | select(length > 0))
    }
  }' >"$SUMMARY_JSON"

printf 'ui.launch_app.self=%s ui.tap.chat=%s ui.tap.editor=%s ui.input_text=%s\n' "true" "true" "true" "true"
echo "ui.chat_ready_text=$CHAT_READY_TEXT"
echo "ui.draft_written=$DRAFT_VALUE"
echo "ui.draft_cleared=true"
if [[ -n "$CROSS_APP_PACKAGE" ]]; then
  printf 'ui.cross_app.package=%s follow_up=%s\n' "$CROSS_APP_PACKAGE" "$cross_app_follow_up"
  if [[ -n "$top_activity_line" ]]; then
    echo "ui.cross_app.top_activity=$top_activity_line"
  fi
fi

echo "local_host.ui_smoke=completed"
echo "artifacts.status=$STATUS_JSON"
echo "artifacts.capabilities=$CAPABILITIES_JSON"
echo "artifacts.summary=$SUMMARY_JSON"
