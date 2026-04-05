#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

BASE_URL="${OPENCLAW_ANDROID_LOCAL_HOST_BASE_URL:-}"
TOKEN="${OPENCLAW_ANDROID_LOCAL_HOST_TOKEN:-}"
USE_ADB_FORWARD="${OPENCLAW_ANDROID_LOCAL_HOST_USE_ADB_FORWARD:-0}"
ADB_BIN="${OPENCLAW_ANDROID_LOCAL_HOST_ADB_BIN:-adb}"
PORT="${OPENCLAW_ANDROID_LOCAL_HOST_PORT:-3945}"
ARTIFACT_DIR="${OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR:-$(mktemp -d -t openclaw-android-browser-lane-smoke.XXXXXX)}"
FLOW_ID="${OPENCLAW_ANDROID_LOCAL_HOST_BROWSER_FLOW_ID:-openai-codex-oauth}"
START_BROWSER="${OPENCLAW_ANDROID_LOCAL_HOST_BROWSER_START:-1}"
POLL_ATTEMPTS="${OPENCLAW_ANDROID_LOCAL_HOST_BROWSER_POLL_ATTEMPTS:-8}"
POLL_INTERVAL_SEC="${OPENCLAW_ANDROID_LOCAL_HOST_BROWSER_POLL_INTERVAL_SEC:-2}"
DESKTOP_PROFILE_ID="${OPENCLAW_ANDROID_LOCAL_HOST_DESKTOP_PROFILE_ID:-openclaw-desktop-host}"
RUNTIME_TASK_ID="${OPENCLAW_ANDROID_LOCAL_HOST_POD_RUNTIME_TASK_ID:-runtime-smoke}"
TOOL_TASK_ID="${OPENCLAW_ANDROID_LOCAL_HOST_POD_TOOL_TASK_ID:-tool-brief-inspect}"
PLUGIN_TASK_ID="${OPENCLAW_ANDROID_LOCAL_HOST_POD_PLUGIN_TASK_ID:-plugin-allowlist-inspect}"

usage() {
  cat <<'EOF'
Usage:
  OPENCLAW_ANDROID_LOCAL_HOST_TOKEN=<token> \
  [OPENCLAW_ANDROID_LOCAL_HOST_USE_ADB_FORWARD=1] \
  [OPENCLAW_ANDROID_LOCAL_HOST_BASE_URL=http://127.0.0.1:3945] \
  [OPENCLAW_ANDROID_LOCAL_HOST_ADB_BIN=/path/to/adb] \
  [OPENCLAW_ANDROID_LOCAL_HOST_BROWSER_FLOW_ID=openai-codex-oauth] \
  [OPENCLAW_ANDROID_LOCAL_HOST_BROWSER_START=1] \
  [OPENCLAW_ANDROID_LOCAL_HOST_BROWSER_POLL_ATTEMPTS=8] \
  [OPENCLAW_ANDROID_LOCAL_HOST_BROWSER_POLL_INTERVAL_SEC=2] \
  ./apps/android/scripts/local-host-embedded-runtime-browser-lane-smoke.sh

What it does:
  1. Optionally runs adb forward tcp:<port> tcp:<port>
  2. Calls /status and /invoke/capabilities
  3. Replays pod.desktop.materialize for the packaged desktop profile
  4. Replays pod.runtime.execute for runtime-smoke, tool-brief-inspect, and plugin-allowlist-inspect
  5. Reads pod.browser.describe before the browser lane starts
  6. Optionally calls pod.browser.auth.start for the allowlisted auth flow
  7. Polls pod.browser.describe until replay state appears on disk
  8. Reads pod.runtime.describe again and writes a focused summary.json

Notes:
  - OPENCLAW_ANDROID_LOCAL_HOST_BROWSER_START=1 is the default and requires write access
  - After you finish the external-browser auth flow, rerun with OPENCLAW_ANDROID_LOCAL_HOST_BROWSER_START=0 to confirm the stored credential path
EOF
}

if [[ "${1:-}" == "--" ]]; then
  shift
fi

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

require_cmd() {
  local name=$1
  if ! has_cmd "$name"; then
    echo "$name required but missing." >&2
    exit 1
  fi
}

record_failure() {
  local check=$1
  smoke_failed=1
  failed_checks+=("$check")
}

get_json() {
  local url=$1
  curl --fail --silent --show-error \
    -H "$auth_header" \
    "$url"
}

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

require_cmd curl
require_cmd jq

if [[ "$START_BROWSER" != "0" && "$START_BROWSER" != "1" ]]; then
  echo "OPENCLAW_ANDROID_LOCAL_HOST_BROWSER_START must be 0 or 1." >&2
  exit 1
fi

if [[ "$USE_ADB_FORWARD" == "1" ]]; then
  require_cmd "$ADB_BIN"
  device_count="$("$ADB_BIN" devices | awk 'NR>1 && $2=="device" {c+=1} END {print c+0}')"
  if [[ "$device_count" -lt 1 ]]; then
    echo "No connected Android device (adb state=device)." >&2
    exit 1
  fi
  "$ADB_BIN" forward "tcp:$PORT" "tcp:$PORT" >/dev/null
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

mkdir -p "$ARTIFACT_DIR"

status_json="$ARTIFACT_DIR/status.json"
capabilities_json="$ARTIFACT_DIR/capabilities.json"
runtime_execute_json="$ARTIFACT_DIR/pod-runtime-execute.json"
tool_execute_json="$ARTIFACT_DIR/pod-tool-execute.json"
plugin_execute_json="$ARTIFACT_DIR/pod-plugin-execute.json"
desktop_materialize_json="$ARTIFACT_DIR/pod-desktop-materialize.json"
browser_before_json="$ARTIFACT_DIR/pod-browser-describe-before.json"
browser_start_json="$ARTIFACT_DIR/pod-browser-auth-start.json"
browser_after_json="$ARTIFACT_DIR/pod-browser-describe-after.json"
runtime_after_json="$ARTIFACT_DIR/pod-runtime-describe-after.json"
summary_json="$ARTIFACT_DIR/summary.json"

for artifact in \
  "$runtime_execute_json" \
  "$tool_execute_json" \
  "$plugin_execute_json" \
  "$desktop_materialize_json" \
  "$browser_before_json" \
  "$browser_start_json" \
  "$browser_after_json" \
  "$runtime_after_json"; do
  printf '{}\n' >"$artifact"
done

smoke_failed=0
failed_checks=()
failure_hint=""
auth_header="Authorization: Bearer $TOKEN"

echo "browser_lane.base_url=$BASE_URL"
echo "browser_lane.flow_id=$FLOW_ID"
echo "browser_lane.start_enabled=$START_BROWSER"
echo "browser_lane.poll_attempts=$POLL_ATTEMPTS"
echo "browser_lane.poll_interval_sec=$POLL_INTERVAL_SEC"

get_json "$BASE_URL/api/local-host/v1/status" | tee "$status_json" >/dev/null
get_json "$BASE_URL/api/local-host/v1/invoke/capabilities" | tee "$capabilities_json" >/dev/null

status_ok="$(jq -r '.ok // false' "$status_json")"
status_pod_ready="$(jq -r '.host.embeddedRuntimePod.ready // false' "$status_json")"
status_pod_version="$(jq -r '.host.embeddedRuntimePod.manifestVersion // ""' "$status_json")"

cap_has_browser_describe="$(jq -r --arg command 'pod.browser.describe' '(.commands // []) | index($command) != null' "$capabilities_json")"
cap_has_browser_auth_start="$(jq -r --arg command 'pod.browser.auth.start' '(.commands // []) | index($command) != null' "$capabilities_json")"
cap_has_desktop_materialize="$(jq -r --arg command 'pod.desktop.materialize' '(.commands // []) | index($command) != null' "$capabilities_json")"
cap_has_runtime_describe="$(jq -r --arg command 'pod.runtime.describe' '(.commands // []) | index($command) != null' "$capabilities_json")"
cap_has_runtime_execute="$(jq -r --arg command 'pod.runtime.execute' '(.commands // []) | index($command) != null' "$capabilities_json")"
cap_write_enabled="$(jq -r '.writeEnabled // false' "$capabilities_json")"

[[ "$status_ok" == "true" ]] || record_failure "status_not_ok"
[[ "$status_pod_ready" == "true" ]] || record_failure "status_pod_not_ready"
[[ "$cap_has_browser_describe" == "true" ]] || record_failure "capabilities_missing_pod_browser_describe"
[[ "$cap_has_runtime_describe" == "true" ]] || record_failure "capabilities_missing_pod_runtime_describe"
[[ "$cap_has_runtime_execute" == "true" ]] || record_failure "capabilities_missing_pod_runtime_execute"
[[ "$cap_has_desktop_materialize" == "true" ]] || record_failure "capabilities_missing_pod_desktop_materialize"

if [[ "$START_BROWSER" == "1" ]]; then
  [[ "$cap_has_browser_auth_start" == "true" ]] || record_failure "capabilities_missing_pod_browser_auth_start"
  [[ "$cap_write_enabled" == "true" ]] || record_failure "capabilities_write_disabled"
fi

if [[ "$smoke_failed" != "0" ]]; then
  failure_hint="install the current debug app, enable remote write if needed, rerun pnpm android:local-host:token -- --json, then rerun this browser-lane smoke"
else
  desktop_materialize_body="$(jq -cn --arg profileId "$DESKTOP_PROFILE_ID" '{command:"pod.desktop.materialize", params:{profileId:$profileId}}')"
  post_json "$BASE_URL/api/local-host/v1/invoke" "$desktop_materialize_body" | tee "$desktop_materialize_json" >/dev/null

  runtime_execute_body="$(jq -cn --arg taskId "$RUNTIME_TASK_ID" '{command:"pod.runtime.execute", params:{taskId:$taskId}}')"
  post_json "$BASE_URL/api/local-host/v1/invoke" "$runtime_execute_body" | tee "$runtime_execute_json" >/dev/null

  tool_execute_body="$(jq -cn --arg taskId "$TOOL_TASK_ID" '{command:"pod.runtime.execute", params:{taskId:$taskId}}')"
  post_json "$BASE_URL/api/local-host/v1/invoke" "$tool_execute_body" | tee "$tool_execute_json" >/dev/null

  plugin_execute_body="$(jq -cn --arg taskId "$PLUGIN_TASK_ID" '{command:"pod.runtime.execute", params:{taskId:$taskId}}')"
  post_json "$BASE_URL/api/local-host/v1/invoke" "$plugin_execute_body" | tee "$plugin_execute_json" >/dev/null

  post_json "$BASE_URL/api/local-host/v1/invoke" '{"command":"pod.browser.describe"}' | tee "$browser_before_json" >/dev/null

  if [[ "$START_BROWSER" == "1" ]]; then
    browser_start_body="$(jq -cn --arg flowId "$FLOW_ID" '{command:"pod.browser.auth.start", params:{flowId:$flowId}}')"
    post_json "$BASE_URL/api/local-host/v1/invoke" "$browser_start_body" | tee "$browser_start_json" >/dev/null
  fi

  replay_ready="false"
  auth_in_progress="false"
  attempt=1
  while [[ "$attempt" -le "$POLL_ATTEMPTS" ]]; do
    post_json "$BASE_URL/api/local-host/v1/invoke" '{"command":"pod.browser.describe"}' | tee "$browser_after_json" >/dev/null
    replay_ready="$(jq -r '.payload.browserReplayReady // false' "$browser_after_json")"
    auth_in_progress="$(jq -r '.payload.authInProgress // false' "$browser_after_json")"
    if [[ "$START_BROWSER" == "0" || "$replay_ready" == "true" ]]; then
      break
    fi
    if [[ "$attempt" -lt "$POLL_ATTEMPTS" ]]; then
      sleep "$POLL_INTERVAL_SEC"
    fi
    attempt=$((attempt + 1))
  done

  post_json "$BASE_URL/api/local-host/v1/invoke" '{"command":"pod.runtime.describe"}' | tee "$runtime_after_json" >/dev/null
fi

desktop_materialize_ok="$(jq -r '.ok // false' "$desktop_materialize_json")"
desktop_materialize_command="$(jq -r '.payload.command // ""' "$desktop_materialize_json")"
desktop_materialize_profile_id="$(jq -r '.payload.profileId // ""' "$desktop_materialize_json")"
desktop_materialize_home_ready="$(jq -r '.payload.desktopHomeReady // false' "$desktop_materialize_json")"
desktop_materialize_execution_count="$(jq -r '.payload.executionCount // -1' "$desktop_materialize_json")"

runtime_execute_ok="$(jq -r '.ok // false' "$runtime_execute_json")"
runtime_execute_command="$(jq -r '.payload.command // ""' "$runtime_execute_json")"
runtime_execute_task_id="$(jq -r '.payload.taskId // ""' "$runtime_execute_json")"
runtime_execute_runtime_home_ready="$(jq -r '.payload.runtimeHomeReady // false' "$runtime_execute_json")"

tool_execute_ok="$(jq -r '.ok // false' "$tool_execute_json")"
tool_execute_command="$(jq -r '.payload.command // ""' "$tool_execute_json")"
tool_execute_task_id="$(jq -r '.payload.taskId // ""' "$tool_execute_json")"
tool_execute_tool_id="$(jq -r '.payload.toolId // ""' "$tool_execute_json")"

plugin_execute_ok="$(jq -r '.ok // false' "$plugin_execute_json")"
plugin_execute_command="$(jq -r '.payload.command // ""' "$plugin_execute_json")"
plugin_execute_task_id="$(jq -r '.payload.taskId // ""' "$plugin_execute_json")"
plugin_execute_plugin_id="$(jq -r '.payload.pluginId // ""' "$plugin_execute_json")"
plugin_execute_runtime_home_ready="$(jq -r '.payload.runtimeHomeReady // false' "$plugin_execute_json")"
plugin_execute_execution_count="$(jq -r '.payload.executionCount // -1' "$plugin_execute_json")"
plugin_execute_result_path="$(jq -r '.payload.pluginResultFilePath // ""' "$plugin_execute_json")"
plugin_execute_profile_source="$(jq -r '.payload.pluginResult.profileSource // ""' "$plugin_execute_json")"
plugin_execute_descriptor_present="$(jq -r '.payload.packagedPluginDescriptorPresent // false' "$plugin_execute_json")"

browser_before_ok="$(jq -r '.ok // false' "$browser_before_json")"
browser_before_command="$(jq -r '.payload.command // ""' "$browser_before_json")"
browser_before_status="$(jq -r '.payload.browserStatus // ""' "$browser_before_json")"
browser_before_flow_count="$(jq -r '.payload.browserAuthFlowCount // -1' "$browser_before_json")"
browser_before_recommended_flow_id="$(jq -r '.payload.recommendedFlowId // ""' "$browser_before_json")"
browser_before_replay_ready="$(jq -r '.payload.browserReplayReady // false' "$browser_before_json")"

browser_start_ok="$(jq -r '.ok // false' "$browser_start_json")"
browser_start_command="$(jq -r '.payload.command // ""' "$browser_start_json")"
browser_start_flow_id="$(jq -r '.payload.flowId // ""' "$browser_start_json")"
browser_start_launch_status="$(jq -r '.payload.launchStatus // ""' "$browser_start_json")"
browser_start_auth_in_progress="$(jq -r '.payload.authInProgress // false' "$browser_start_json")"

browser_after_ok="$(jq -r '.ok // false' "$browser_after_json")"
browser_after_command="$(jq -r '.payload.command // ""' "$browser_after_json")"
browser_after_status="$(jq -r '.payload.browserStatus // ""' "$browser_after_json")"
browser_after_replay_ready="$(jq -r '.payload.browserReplayReady // false' "$browser_after_json")"
browser_after_auth_in_progress="$(jq -r '.payload.authInProgress // false' "$browser_after_json")"
browser_after_auth_credential_present="$(jq -r '.payload.authCredentialPresent // false' "$browser_after_json")"
browser_after_last_launch_status="$(jq -r '.payload.lastLaunchStatus // ""' "$browser_after_json")"
browser_after_last_launch_flow_id="$(jq -r '.payload.lastLaunchFlowId // ""' "$browser_after_json")"
browser_after_state_file_path="$(jq -r '.payload.stateFilePath // ""' "$browser_after_json")"
browser_after_log_file_path="$(jq -r '.payload.logFilePath // ""' "$browser_after_json")"

runtime_after_ok="$(jq -r '.ok // false' "$runtime_after_json")"
runtime_after_command="$(jq -r '.payload.command // ""' "$runtime_after_json")"
runtime_after_mainline_status="$(jq -r '.payload.mainlineStatus // ""' "$runtime_after_json")"
runtime_after_recommended_next_slice="$(jq -r '.payload.recommendedNextSlice // ""' "$runtime_after_json")"
runtime_after_browser_replay_ready="$(jq -r '.payload.browserReplayReady // false' "$runtime_after_json")"
runtime_after_browser_launch_state_count="$(jq -r '.payload.browserLaunchStateCount // -1' "$runtime_after_json")"
runtime_after_plugin_execution_state_count="$(jq -r '.payload.runtimePluginExecutionStateCount // -1' "$runtime_after_json")"

if [[ "$failure_hint" == "" ]]; then
  [[ "$desktop_materialize_ok" == "true" ]] || record_failure "pod_desktop_materialize_not_ok"
  [[ "$desktop_materialize_command" == "pod.desktop.materialize" ]] || record_failure "pod_desktop_materialize_command_mismatch"
  [[ "$desktop_materialize_profile_id" == "$DESKTOP_PROFILE_ID" ]] || record_failure "pod_desktop_materialize_profile_mismatch"
  [[ "$desktop_materialize_home_ready" == "true" ]] || record_failure "pod_desktop_materialize_home_not_ready"
  [[ "$desktop_materialize_execution_count" -ge 1 ]] || record_failure "pod_desktop_materialize_execution_count_invalid"
  [[ "$runtime_execute_ok" == "true" ]] || record_failure "pod_runtime_execute_not_ok"
  [[ "$runtime_execute_command" == "pod.runtime.execute" ]] || record_failure "pod_runtime_execute_command_mismatch"
  [[ "$runtime_execute_task_id" == "$RUNTIME_TASK_ID" ]] || record_failure "pod_runtime_execute_task_mismatch"
  [[ "$runtime_execute_runtime_home_ready" == "true" ]] || record_failure "pod_runtime_execute_runtime_home_not_ready"

  [[ "$tool_execute_ok" == "true" ]] || record_failure "pod_tool_execute_not_ok"
  [[ "$tool_execute_command" == "pod.runtime.execute" ]] || record_failure "pod_tool_execute_command_mismatch"
  [[ "$tool_execute_task_id" == "$TOOL_TASK_ID" ]] || record_failure "pod_tool_execute_task_mismatch"
  [[ "$tool_execute_tool_id" != "" ]] || record_failure "pod_tool_execute_missing_tool_id"

  [[ "$plugin_execute_ok" == "true" ]] || record_failure "pod_plugin_execute_not_ok"
  [[ "$plugin_execute_command" == "pod.runtime.execute" ]] || record_failure "pod_plugin_execute_command_mismatch"
  [[ "$plugin_execute_task_id" == "$PLUGIN_TASK_ID" ]] || record_failure "pod_plugin_execute_task_mismatch"
  [[ "$plugin_execute_plugin_id" == "openclaw-plugin-host-placeholder" ]] || record_failure "pod_plugin_execute_plugin_id_mismatch"
  [[ "$plugin_execute_runtime_home_ready" == "true" ]] || record_failure "pod_plugin_execute_runtime_home_not_ready"
  [[ "$plugin_execute_execution_count" -ge 1 ]] || record_failure "pod_plugin_execute_execution_count_invalid"
  [[ "$plugin_execute_result_path" == *"/work/"* ]] || record_failure "pod_plugin_execute_missing_result_path"
  [[ "$plugin_execute_descriptor_present" == "true" ]] || record_failure "pod_plugin_execute_missing_descriptor"

  [[ "$browser_before_ok" == "true" ]] || record_failure "pod_browser_describe_before_not_ok"
  [[ "$browser_before_command" == "pod.browser.describe" ]] || record_failure "pod_browser_describe_before_command_mismatch"
  [[ "$browser_before_status" != "" ]] || record_failure "pod_browser_describe_before_missing_status"
  [[ "$browser_before_flow_count" -ge 1 ]] || record_failure "pod_browser_describe_before_missing_flow"
  [[ "$browser_before_recommended_flow_id" == "$FLOW_ID" ]] || record_failure "pod_browser_describe_before_flow_id_mismatch"

  if [[ "$START_BROWSER" == "1" ]]; then
    [[ "$browser_start_ok" == "true" ]] || record_failure "pod_browser_auth_start_not_ok"
    [[ "$browser_start_command" == "pod.browser.auth.start" ]] || record_failure "pod_browser_auth_start_command_mismatch"
    [[ "$browser_start_flow_id" == "$FLOW_ID" ]] || record_failure "pod_browser_auth_start_flow_id_mismatch"
    [[ "$browser_start_launch_status" != "" ]] || record_failure "pod_browser_auth_start_missing_launch_status"
    [[ "$browser_start_launch_status" != "launch_failed" ]] || record_failure "pod_browser_auth_start_launch_failed"
  fi

  [[ "$browser_after_ok" == "true" ]] || record_failure "pod_browser_describe_after_not_ok"
  [[ "$browser_after_command" == "pod.browser.describe" ]] || record_failure "pod_browser_describe_after_command_mismatch"
  [[ "$browser_after_status" != "" ]] || record_failure "pod_browser_describe_after_missing_status"
  [[ "$browser_after_last_launch_flow_id" == "$FLOW_ID" || "$browser_after_last_launch_flow_id" == "" ]] || record_failure "pod_browser_describe_after_flow_id_mismatch"
  [[ "$browser_after_state_file_path" != "" ]] || record_failure "pod_browser_describe_after_missing_state_file_path"
  [[ "$browser_after_log_file_path" != "" ]] || record_failure "pod_browser_describe_after_missing_log_file_path"

  if [[ "$START_BROWSER" == "1" ]]; then
    [[ "$browser_after_replay_ready" == "true" ]] || record_failure "pod_browser_describe_after_not_replayed"
    [[ "$browser_after_last_launch_status" != "" ]] || record_failure "pod_browser_describe_after_missing_last_launch_status"
  fi

  [[ "$runtime_after_ok" == "true" ]] || record_failure "pod_runtime_describe_after_not_ok"
  [[ "$runtime_after_command" == "pod.runtime.describe" ]] || record_failure "pod_runtime_describe_after_command_mismatch"
  [[ "$runtime_after_mainline_status" != "" ]] || record_failure "pod_runtime_describe_after_missing_status"
  [[ "$runtime_after_browser_replay_ready" == "$browser_after_replay_ready" ]] || record_failure "pod_runtime_describe_after_replay_mismatch"
  [[ "$runtime_after_plugin_execution_state_count" -ge 1 ]] || record_failure "pod_runtime_describe_after_plugin_state_missing"

  if [[ "$START_BROWSER" == "1" ]]; then
    [[ "$runtime_after_mainline_status" == "process_runtime_supervision_bootstrapped" || "$runtime_after_mainline_status" == "process_runtime_activation_bootstrapped" || "$runtime_after_mainline_status" == "process_model_bootstrapped" || "$runtime_after_mainline_status" == "plugin_lane_replayed" || "$runtime_after_mainline_status" == "browser_lane_replayed" || "$runtime_after_mainline_status" == "browser_lane_configured" || "$runtime_after_mainline_status" == "desktop_bundle_ready" || "$runtime_after_mainline_status" == "desktop_home_ready" || "$runtime_after_mainline_status" == "desktop_home_configured" ]] || record_failure "pod_runtime_describe_after_browser_status_mismatch"
  fi
fi

failed_checks_joined=""
if [[ "${#failed_checks[@]}" -gt 0 ]]; then
  IFS=,
  failed_checks_joined="${failed_checks[*]}"
  unset IFS
fi

jq -n \
  --arg baseUrl "$BASE_URL" \
  --arg flowId "$FLOW_ID" \
  --arg artifactDir "$ARTIFACT_DIR" \
  --arg statusOk "$status_ok" \
  --arg statusPodReady "$status_pod_ready" \
  --arg statusPodVersion "$status_pod_version" \
  --arg capHasBrowserDescribe "$cap_has_browser_describe" \
  --arg capHasBrowserAuthStart "$cap_has_browser_auth_start" \
  --arg capHasDesktopMaterialize "$cap_has_desktop_materialize" \
  --arg capHasRuntimeDescribe "$cap_has_runtime_describe" \
  --arg capHasRuntimeExecute "$cap_has_runtime_execute" \
  --arg capWriteEnabled "$cap_write_enabled" \
  --arg desktopMaterializeOk "$desktop_materialize_ok" \
  --arg desktopMaterializeProfileId "$desktop_materialize_profile_id" \
  --arg desktopMaterializeHomeReady "$desktop_materialize_home_ready" \
  --arg desktopMaterializeExecutionCount "$desktop_materialize_execution_count" \
  --arg runtimeExecuteOk "$runtime_execute_ok" \
  --arg runtimeExecuteTaskId "$runtime_execute_task_id" \
  --arg runtimeExecuteRuntimeHomeReady "$runtime_execute_runtime_home_ready" \
  --arg toolExecuteOk "$tool_execute_ok" \
  --arg toolExecuteTaskId "$tool_execute_task_id" \
  --arg toolExecuteToolId "$tool_execute_tool_id" \
  --arg pluginExecuteOk "$plugin_execute_ok" \
  --arg pluginExecuteTaskId "$plugin_execute_task_id" \
  --arg pluginExecutePluginId "$plugin_execute_plugin_id" \
  --arg pluginExecuteRuntimeHomeReady "$plugin_execute_runtime_home_ready" \
  --arg pluginExecuteExecutionCount "$plugin_execute_execution_count" \
  --arg pluginExecuteResultPath "$plugin_execute_result_path" \
  --arg pluginExecuteProfileSource "$plugin_execute_profile_source" \
  --arg pluginExecuteDescriptorPresent "$plugin_execute_descriptor_present" \
  --arg browserBeforeOk "$browser_before_ok" \
  --arg browserBeforeStatus "$browser_before_status" \
  --arg browserBeforeReplayReady "$browser_before_replay_ready" \
  --arg browserStartOk "$browser_start_ok" \
  --arg browserStartLaunchStatus "$browser_start_launch_status" \
  --arg browserStartAuthInProgress "$browser_start_auth_in_progress" \
  --arg browserAfterOk "$browser_after_ok" \
  --arg browserAfterStatus "$browser_after_status" \
  --arg browserAfterReplayReady "$browser_after_replay_ready" \
  --arg browserAfterAuthInProgress "$browser_after_auth_in_progress" \
  --arg browserAfterAuthCredentialPresent "$browser_after_auth_credential_present" \
  --arg browserAfterLastLaunchStatus "$browser_after_last_launch_status" \
  --arg browserAfterStateFilePath "$browser_after_state_file_path" \
  --arg browserAfterLogFilePath "$browser_after_log_file_path" \
  --arg runtimeAfterOk "$runtime_after_ok" \
  --arg runtimeAfterMainlineStatus "$runtime_after_mainline_status" \
  --arg runtimeAfterRecommendedNextSlice "$runtime_after_recommended_next_slice" \
  --arg runtimeAfterBrowserReplayReady "$runtime_after_browser_replay_ready" \
  --arg runtimeAfterBrowserLaunchStateCount "$runtime_after_browser_launch_state_count" \
  --arg runtimeAfterPluginExecutionStateCount "$runtime_after_plugin_execution_state_count" \
  --arg failureHint "$failure_hint" \
  --arg failedChecksJoined "$failed_checks_joined" \
  --argjson startEnabled "$START_BROWSER" \
  --argjson pollAttempts "$POLL_ATTEMPTS" \
  --argjson pollIntervalSec "$POLL_INTERVAL_SEC" \
  '{
    ok: ($failedChecksJoined == ""),
    baseUrl: $baseUrl,
    hint: (if $failureHint == "" then null else $failureHint end),
    expected: {
      flowId: $flowId,
      startEnabled: ($startEnabled == 1),
      pollAttempts: $pollAttempts,
      pollIntervalSec: $pollIntervalSec
    },
    failedChecks: (
      if $failedChecksJoined == "" then
        []
      else
        ($failedChecksJoined | split(","))
      end
    ),
    status: {
      ok: ($statusOk == "true"),
      embeddedRuntimePodReady: ($statusPodReady == "true"),
      manifestVersion: (if $statusPodVersion == "" then null else $statusPodVersion end)
    },
    capabilities: {
      hasPodBrowserDescribe: ($capHasBrowserDescribe == "true"),
      hasPodBrowserAuthStart: ($capHasBrowserAuthStart == "true"),
      hasPodDesktopMaterialize: ($capHasDesktopMaterialize == "true"),
      hasPodRuntimeDescribe: ($capHasRuntimeDescribe == "true"),
      hasPodRuntimeExecute: ($capHasRuntimeExecute == "true"),
      writeEnabled: ($capWriteEnabled == "true")
    },
    desktopMaterialize: {
      ok: ($desktopMaterializeOk == "true"),
      profileId: (if $desktopMaterializeProfileId == "" then null else $desktopMaterializeProfileId end),
      desktopHomeReady: ($desktopMaterializeHomeReady == "true"),
      executionCount: ($desktopMaterializeExecutionCount | tonumber)
    },
    runtimeExecute: {
      ok: ($runtimeExecuteOk == "true"),
      taskId: (if $runtimeExecuteTaskId == "" then null else $runtimeExecuteTaskId end),
      runtimeHomeReady: ($runtimeExecuteRuntimeHomeReady == "true")
    },
    toolExecute: {
      ok: ($toolExecuteOk == "true"),
      taskId: (if $toolExecuteTaskId == "" then null else $toolExecuteTaskId end),
      toolId: (if $toolExecuteToolId == "" then null else $toolExecuteToolId end)
    },
    pluginExecute: {
      ok: ($pluginExecuteOk == "true"),
      taskId: (if $pluginExecuteTaskId == "" then null else $pluginExecuteTaskId end),
      pluginId: (if $pluginExecutePluginId == "" then null else $pluginExecutePluginId end),
      runtimeHomeReady: ($pluginExecuteRuntimeHomeReady == "true"),
      executionCount: ($pluginExecuteExecutionCount | tonumber),
      resultFilePath: (if $pluginExecuteResultPath == "" then null else $pluginExecuteResultPath end),
      profileSource: (if $pluginExecuteProfileSource == "" then null else $pluginExecuteProfileSource end),
      packagedPluginDescriptorPresent: ($pluginExecuteDescriptorPresent == "true")
    },
    browserDescribeBefore: {
      ok: ($browserBeforeOk == "true"),
      status: (if $browserBeforeStatus == "" then null else $browserBeforeStatus end),
      replayReady: ($browserBeforeReplayReady == "true")
    },
    browserAuthStart: {
      executed: ($startEnabled == 1),
      ok: ($browserStartOk == "true"),
      launchStatus: (if $browserStartLaunchStatus == "" then null else $browserStartLaunchStatus end),
      authInProgress: ($browserStartAuthInProgress == "true")
    },
    browserDescribeAfter: {
      ok: ($browserAfterOk == "true"),
      status: (if $browserAfterStatus == "" then null else $browserAfterStatus end),
      replayReady: ($browserAfterReplayReady == "true"),
      authInProgress: ($browserAfterAuthInProgress == "true"),
      authCredentialPresent: ($browserAfterAuthCredentialPresent == "true"),
      lastLaunchStatus: (if $browserAfterLastLaunchStatus == "" then null else $browserAfterLastLaunchStatus end),
      stateFilePath: (if $browserAfterStateFilePath == "" then null else $browserAfterStateFilePath end),
      logFilePath: (if $browserAfterLogFilePath == "" then null else $browserAfterLogFilePath end)
    },
    runtimeDescribeAfter: {
      ok: ($runtimeAfterOk == "true"),
      mainlineStatus: (if $runtimeAfterMainlineStatus == "" then null else $runtimeAfterMainlineStatus end),
      recommendedNextSlice: (if $runtimeAfterRecommendedNextSlice == "" then null else $runtimeAfterRecommendedNextSlice end),
      browserReplayReady: ($runtimeAfterBrowserReplayReady == "true"),
      browserLaunchStateCount: ($runtimeAfterBrowserLaunchStateCount | tonumber),
      runtimePluginExecutionStateCount: ($runtimeAfterPluginExecutionStateCount | tonumber)
    },
    artifacts: {
      rootDir: $artifactDir
    }
  }' >"$summary_json"

printf 'browser_lane.status ok=%s pod_ready=%s version=%s\n' \
  "$status_ok" "$status_pod_ready" "$status_pod_version"
printf 'browser_lane.capabilities describe=%s start=%s runtime_describe=%s runtime_execute=%s write=%s\n' \
  "$cap_has_browser_describe" "$cap_has_browser_auth_start" "$cap_has_runtime_describe" "$cap_has_runtime_execute" "$cap_write_enabled"
printf 'browser_lane.desktop_materialize ok=%s profile=%s ready=%s count=%s\n' \
  "$desktop_materialize_ok" "$desktop_materialize_profile_id" "$desktop_materialize_home_ready" "$desktop_materialize_execution_count"
printf 'browser_lane.runtime_execute ok=%s task=%s home=%s\n' \
  "$runtime_execute_ok" "$runtime_execute_task_id" "$runtime_execute_runtime_home_ready"
printf 'browser_lane.tool_execute ok=%s task=%s tool=%s\n' \
  "$tool_execute_ok" "$tool_execute_task_id" "$tool_execute_tool_id"
printf 'browser_lane.plugin_execute ok=%s task=%s plugin=%s home=%s count=%s profile_source=%s\n' \
  "$plugin_execute_ok" "$plugin_execute_task_id" "$plugin_execute_plugin_id" "$plugin_execute_runtime_home_ready" "$plugin_execute_execution_count" "$plugin_execute_profile_source"
printf 'browser_lane.describe_before ok=%s status=%s replay=%s\n' \
  "$browser_before_ok" "$browser_before_status" "$browser_before_replay_ready"
if [[ "$START_BROWSER" == "1" ]]; then
  printf 'browser_lane.auth_start ok=%s flow=%s launch=%s auth_in_progress=%s\n' \
    "$browser_start_ok" "$browser_start_flow_id" "$browser_start_launch_status" "$browser_start_auth_in_progress"
fi
printf 'browser_lane.describe_after ok=%s status=%s replay=%s auth_in_progress=%s credential=%s last_launch=%s\n' \
  "$browser_after_ok" "$browser_after_status" "$browser_after_replay_ready" "$browser_after_auth_in_progress" "$browser_after_auth_credential_present" "$browser_after_last_launch_status"
printf 'browser_lane.runtime_after ok=%s mainline=%s next=%s launch_states=%s\n' \
  "$runtime_after_ok" "$runtime_after_mainline_status" "$runtime_after_recommended_next_slice" "$runtime_after_browser_launch_state_count"
if [[ -n "$failure_hint" ]]; then
  echo "browser_lane.hint=$failure_hint"
fi

if [[ "$smoke_failed" != "0" ]]; then
  echo "browser_lane.smoke=failed" >&2
  jq '.' "$summary_json" >&2
  exit 1
fi

echo "browser_lane.smoke=completed"
echo "artifacts.dir=$ARTIFACT_DIR"
echo "artifacts.status=$status_json"
echo "artifacts.capabilities=$capabilities_json"
echo "artifacts.runtime_execute=$runtime_execute_json"
echo "artifacts.tool_execute=$tool_execute_json"
echo "artifacts.plugin_execute=$plugin_execute_json"
echo "artifacts.desktop_materialize=$desktop_materialize_json"
echo "artifacts.browser_before=$browser_before_json"
echo "artifacts.browser_start=$browser_start_json"
echo "artifacts.browser_after=$browser_after_json"
echo "artifacts.runtime_after=$runtime_after_json"
echo "artifacts.summary=$summary_json"
