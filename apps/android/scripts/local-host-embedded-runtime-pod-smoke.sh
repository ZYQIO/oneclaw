#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
RUNTIME_POD_DIR="$ANDROID_DIR/runtime-pod"
POD_SPEC_PATH="$RUNTIME_POD_DIR/pod-spec.json"
CONTENT_INDEX_PATH="$RUNTIME_POD_DIR/assets/workspace/content-index.json"

BASE_URL="${OPENCLAW_ANDROID_LOCAL_HOST_BASE_URL:-}"
TOKEN="${OPENCLAW_ANDROID_LOCAL_HOST_TOKEN:-}"
USE_ADB_FORWARD="${OPENCLAW_ANDROID_LOCAL_HOST_USE_ADB_FORWARD:-0}"
ADB_BIN="${OPENCLAW_ANDROID_LOCAL_HOST_ADB_BIN:-adb}"
PORT="${OPENCLAW_ANDROID_LOCAL_HOST_PORT:-3945}"
ARTIFACT_DIR="${OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR:-$(mktemp -d -t openclaw-android-runtime-pod-smoke.XXXXXX)}"

if [[ ! -f "$POD_SPEC_PATH" ]]; then
  echo "Missing pod spec at $POD_SPEC_PATH" >&2
  exit 1
fi

if [[ ! -f "$CONTENT_INDEX_PATH" ]]; then
  echo "Missing workspace content index at $CONTENT_INDEX_PATH" >&2
  exit 1
fi

usage() {
  cat <<'EOF'
Usage:
  OPENCLAW_ANDROID_LOCAL_HOST_TOKEN=<token> \
  [OPENCLAW_ANDROID_LOCAL_HOST_BASE_URL=http://127.0.0.1:3945] \
  [OPENCLAW_ANDROID_LOCAL_HOST_USE_ADB_FORWARD=1] \
  [OPENCLAW_ANDROID_LOCAL_HOST_ADB_BIN=/path/to/adb] \
  [OPENCLAW_ANDROID_LOCAL_HOST_PORT=3945] \
  [OPENCLAW_ANDROID_LOCAL_HOST_POD_SMOKE_QUERY=<query>] \
  [OPENCLAW_ANDROID_LOCAL_HOST_POD_SMOKE_LIMIT=5] \
  [OPENCLAW_ANDROID_LOCAL_HOST_POD_SMOKE_EXPECTED_PATH=<relative-path>] \
  [OPENCLAW_ANDROID_LOCAL_HOST_POD_RUNTIME_TASK_ID=runtime-smoke] \
  ./apps/android/scripts/local-host-embedded-runtime-pod-smoke.sh

What it does:
  1. Optionally runs adb forward tcp:<port> tcp:<port>
  2. Calls /status and verifies embeddedRuntimePod readiness
  3. Calls /invoke/capabilities and checks pod helper allowlisting
  4. Calls /invoke pod.health
  5. Calls /invoke pod.manifest.describe
  6. Calls /invoke pod.runtime.describe
  7. Calls /invoke pod.runtime.execute
  8. Calls /invoke pod.workspace.scan
  9. Calls /invoke pod.workspace.read
  10. Verifies the phone-side results against repo pod-spec.json and content-index.json

Requirements:
  - curl
  - jq
  - adb (only when OPENCLAW_ANDROID_LOCAL_HOST_USE_ADB_FORWARD=1)
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

require_cmd curl
require_cmd jq

EXPECTED_VERSION="$(jq -r '.version // empty' "$POD_SPEC_PATH")"
EXPECTED_STAGE_COUNT="$(jq -r '(.stages // []) | length' "$POD_SPEC_PATH")"
EXPECTED_DOCUMENT_COUNT="$(jq -r '(.documents // []) | length' "$CONTENT_INDEX_PATH")"
DEFAULT_EXPECTED_PATH="$(jq -r '.documents[0].path // empty' "$CONTENT_INDEX_PATH")"
EXPECTED_FILE_COUNT="$(find "$RUNTIME_POD_DIR/assets" -type f | wc -l | tr -d '[:space:]')"
EXPECTED_WORKSPACE_STAGE_FILE_COUNT="$(find "$RUNTIME_POD_DIR/assets/workspace" -type f | wc -l | tr -d '[:space:]')"

if [[ -z "$EXPECTED_VERSION" ]]; then
  echo "Unable to determine expected pod version from $POD_SPEC_PATH" >&2
  exit 1
fi

if [[ -n "$DEFAULT_EXPECTED_PATH" ]]; then
  DEFAULT_QUERY="$(basename "$DEFAULT_EXPECTED_PATH")"
  DEFAULT_QUERY="${DEFAULT_QUERY%.*}"
else
  DEFAULT_QUERY="handoff"
fi

QUERY="${OPENCLAW_ANDROID_LOCAL_HOST_POD_SMOKE_QUERY:-$DEFAULT_QUERY}"
LIMIT="${OPENCLAW_ANDROID_LOCAL_HOST_POD_SMOKE_LIMIT:-5}"
EXPECTED_PATH="${OPENCLAW_ANDROID_LOCAL_HOST_POD_SMOKE_EXPECTED_PATH:-$DEFAULT_EXPECTED_PATH}"
RUNTIME_TASK_ID="${OPENCLAW_ANDROID_LOCAL_HOST_POD_RUNTIME_TASK_ID:-runtime-smoke}"
EXPECTED_WORKSPACE_FILE=""
EXPECTED_WORKSPACE_FILE_SIZE=""
if [[ -n "$EXPECTED_PATH" && -f "$RUNTIME_POD_DIR/assets/workspace/$EXPECTED_PATH" ]]; then
  EXPECTED_WORKSPACE_FILE="$RUNTIME_POD_DIR/assets/workspace/$EXPECTED_PATH"
  EXPECTED_WORKSPACE_FILE_SIZE="$(wc -c <"$EXPECTED_WORKSPACE_FILE" | tr -d '[:space:]')"
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

auth_header="Authorization: Bearer $TOKEN"
mkdir -p "$ARTIFACT_DIR"
status_json="$ARTIFACT_DIR/status.json"
capabilities_json="$ARTIFACT_DIR/capabilities.json"
health_json="$ARTIFACT_DIR/pod-health.json"
manifest_json="$ARTIFACT_DIR/pod-manifest-describe.json"
runtime_describe_json="$ARTIFACT_DIR/pod-runtime-describe.json"
runtime_execute_json="$ARTIFACT_DIR/pod-runtime-execute.json"
workspace_json="$ARTIFACT_DIR/pod-workspace-scan.json"
workspace_read_json="$ARTIFACT_DIR/pod-workspace-read.json"
summary_json="$ARTIFACT_DIR/summary.json"

smoke_failed=0
failed_checks=()
failure_hint=""
skip_helper_invocations=0

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

echo "runtime_pod.base_url=$BASE_URL"
echo "runtime_pod.expected_version=$EXPECTED_VERSION"
echo "runtime_pod.expected_stage_count=$EXPECTED_STAGE_COUNT"
echo "runtime_pod.expected_file_count=$EXPECTED_FILE_COUNT"
echo "runtime_pod.expected_workspace_stage_file_count=$EXPECTED_WORKSPACE_STAGE_FILE_COUNT"
echo "runtime_pod.expected_document_count=$EXPECTED_DOCUMENT_COUNT"
if [[ -n "$EXPECTED_PATH" ]]; then
  echo "runtime_pod.expected_path=$EXPECTED_PATH"
fi

get_json "$BASE_URL/api/local-host/v1/status" | tee "$status_json" >/dev/null
get_json "$BASE_URL/api/local-host/v1/invoke/capabilities" | tee "$capabilities_json" >/dev/null

status_ok="$(jq -r '.ok // false' "$status_json")"
status_mode="$(jq -r '.mode // ""' "$status_json")"
status_pod_available="$(jq -r '.host.embeddedRuntimePodAvailable // false' "$status_json")"
status_pod_ready="$(jq -r '.host.embeddedRuntimePod.ready // false' "$status_json")"
status_pod_version="$(jq -r '.host.embeddedRuntimePod.manifestVersion // ""' "$status_json")"
status_pod_verified_count="$(jq -r '.host.embeddedRuntimePod.verifiedFileCount // -1' "$status_json")"

cap_has_health="$(jq -r --arg command 'pod.health' '(.commands // []) | index($command) != null' "$capabilities_json")"
cap_has_manifest_describe="$(jq -r --arg command 'pod.manifest.describe' '(.commands // []) | index($command) != null' "$capabilities_json")"
cap_has_runtime_describe="$(jq -r --arg command 'pod.runtime.describe' '(.commands // []) | index($command) != null' "$capabilities_json")"
cap_has_runtime_execute="$(jq -r --arg command 'pod.runtime.execute' '(.commands // []) | index($command) != null' "$capabilities_json")"
cap_has_workspace_scan="$(jq -r --arg command 'pod.workspace.scan' '(.commands // []) | index($command) != null' "$capabilities_json")"
cap_has_workspace_read="$(jq -r --arg command 'pod.workspace.read' '(.commands // []) | index($command) != null' "$capabilities_json")"
cap_write_enabled="$(jq -r '.writeEnabled // false' "$capabilities_json")"

[[ "$cap_has_health" == "true" ]] || record_failure "capabilities_missing_pod_health"
[[ "$cap_has_manifest_describe" == "true" ]] || record_failure "capabilities_missing_pod_manifest_describe"
[[ "$cap_has_runtime_describe" == "true" ]] || record_failure "capabilities_missing_pod_runtime_describe"
[[ "$cap_has_runtime_execute" == "true" ]] || record_failure "capabilities_missing_pod_runtime_execute"
[[ "$cap_has_workspace_scan" == "true" ]] || record_failure "capabilities_missing_pod_workspace_scan"
[[ "$cap_has_workspace_read" == "true" ]] || record_failure "capabilities_missing_pod_workspace_read"

if [[ "$cap_has_health" != "true" || "$cap_has_manifest_describe" != "true" || "$cap_has_runtime_describe" != "true" || "$cap_has_runtime_execute" != "true" || "$cap_has_workspace_scan" != "true" || "$cap_has_workspace_read" != "true" ]]; then
  skip_helper_invocations=1
  failure_hint="install the current debug app on the device, rerun pnpm android:local-host:token -- --json, then rerun pnpm android:local-host:embedded-runtime-pod:smoke"
  printf '{}\n' >"$health_json"
  printf '{}\n' >"$manifest_json"
  printf '{}\n' >"$runtime_describe_json"
  printf '{}\n' >"$runtime_execute_json"
  printf '{}\n' >"$workspace_json"
  printf '{}\n' >"$workspace_read_json"
else
  post_json "$BASE_URL/api/local-host/v1/invoke" '{"command":"pod.health"}' | tee "$health_json" >/dev/null
  post_json "$BASE_URL/api/local-host/v1/invoke" '{"command":"pod.manifest.describe"}' | tee "$manifest_json" >/dev/null
  post_json "$BASE_URL/api/local-host/v1/invoke" '{"command":"pod.runtime.describe"}' | tee "$runtime_describe_json" >/dev/null
  runtime_execute_body="$(jq -cn --arg taskId "$RUNTIME_TASK_ID" '{command:"pod.runtime.execute", params:{taskId:$taskId}}')"
  post_json "$BASE_URL/api/local-host/v1/invoke" "$runtime_execute_body" | tee "$runtime_execute_json" >/dev/null
  workspace_body="$(jq -cn --arg query "$QUERY" --argjson limit "$LIMIT" '{command:"pod.workspace.scan", params:{query:$query, limit:$limit}}')"
  post_json "$BASE_URL/api/local-host/v1/invoke" "$workspace_body" | tee "$workspace_json" >/dev/null
  workspace_read_body="$(jq -cn --arg path "$EXPECTED_PATH" '{command:"pod.workspace.read", params:{path:$path, maxChars:4096}}')"
  post_json "$BASE_URL/api/local-host/v1/invoke" "$workspace_read_body" | tee "$workspace_read_json" >/dev/null
fi

health_ok="$(jq -r '.ok // false' "$health_json")"
health_command="$(jq -r '.payload.command // ""' "$health_json")"
health_ready="$(jq -r '.payload.ready // false' "$health_json")"
health_local_execution="$(jq -r '.payload.localExecutionAvailable // false' "$health_json")"
health_version="$(jq -r '.payload.manifestVersion // ""' "$health_json")"
health_verified_count="$(jq -r '.payload.verifiedFileCount // -1' "$health_json")"

manifest_ok="$(jq -r '.ok // false' "$manifest_json")"
manifest_command="$(jq -r '.payload.command // ""' "$manifest_json")"
manifest_source="$(jq -r '.payload.manifestSource // ""' "$manifest_json")"
manifest_layout_source="$(jq -r '.payload.layoutSource // ""' "$manifest_json")"
manifest_stage_count="$(jq -r '.payload.stageCount // -1' "$manifest_json")"
manifest_directory_count="$(jq -r '.payload.directoryCount // -1' "$manifest_json")"
manifest_file_count="$(jq -r '.payload.fileCount // -1' "$manifest_json")"
manifest_workspace_declared="$(jq -r '.payload.workspaceStageDeclared // false' "$manifest_json")"
manifest_workspace_installed="$(jq -r '.payload.workspaceStageInstalled // false' "$manifest_json")"
manifest_version="$(jq -r '.payload.podManifest.version // ""' "$manifest_json")"
manifest_layout_version="$(jq -r '.payload.podLayout.version // ""' "$manifest_json")"
manifest_workspace_stage_file_count="$(jq -r '.payload.fileStageCounts.workspace // -1' "$manifest_json")"

runtime_describe_ok="$(jq -r '.ok // false' "$runtime_describe_json")"
runtime_describe_command="$(jq -r '.payload.command // ""' "$runtime_describe_json")"
runtime_describe_branch="$(jq -r '.payload.mainlineBranch // ""' "$runtime_describe_json")"
runtime_describe_status="$(jq -r '.payload.mainlineStatus // ""' "$runtime_describe_json")"
runtime_describe_engine_domain="$(jq -r '(.payload.domains // []) | map(.id) | index("engine") != null' "$runtime_describe_json")"
runtime_describe_environment_domain="$(jq -r '(.payload.domains // []) | map(.id) | index("environment") != null' "$runtime_describe_json")"
runtime_describe_browser_domain="$(jq -r '(.payload.domains // []) | map(.id) | index("browser") != null' "$runtime_describe_json")"
runtime_describe_tools_domain="$(jq -r '(.payload.domains // []) | map(.id) | index("tools") != null' "$runtime_describe_json")"
runtime_describe_plugins_domain="$(jq -r '(.payload.domains // []) | map(.id) | index("plugins") != null' "$runtime_describe_json")"

runtime_execute_ok="$(jq -r '.ok // false' "$runtime_execute_json")"
runtime_execute_command="$(jq -r '.payload.command // ""' "$runtime_execute_json")"
runtime_execute_task_id="$(jq -r '.payload.taskId // ""' "$runtime_execute_json")"
runtime_execute_runtime_home_ready="$(jq -r '.payload.runtimeHomeReady // false' "$runtime_execute_json")"
runtime_execute_engine_id="$(jq -r '.payload.engineId // ""' "$runtime_execute_json")"
runtime_execute_execution_count="$(jq -r '.payload.executionCount // -1' "$runtime_execute_json")"
runtime_execute_state_path="$(jq -r '.payload.stateFilePath // ""' "$runtime_execute_json")"
runtime_execute_log_path="$(jq -r '.payload.logFilePath // ""' "$runtime_execute_json")"

workspace_ok="$(jq -r '.ok // false' "$workspace_json")"
workspace_command="$(jq -r '.payload.command // ""' "$workspace_json")"
workspace_stage_present="$(jq -r '.payload.workspaceStagePresent // false' "$workspace_json")"
workspace_stage_manifest_present="$(jq -r '.payload.stageManifestPresent // false' "$workspace_json")"
workspace_content_index_present="$(jq -r '.payload.contentIndexPresent // false' "$workspace_json")"
workspace_stage_name="$(jq -r '.payload.stageManifest.stage // ""' "$workspace_json")"
workspace_document_count="$(jq -r '(.payload.contentIndex.documents // []) | length' "$workspace_json")"
workspace_matched_file_count="$(jq -r '.payload.matchedFileCount // 0' "$workspace_json")"
workspace_returned_file_count="$(jq -r '.payload.returnedFileCount // 0' "$workspace_json")"
workspace_first_path="$(jq -r '.payload.files[0].relativePath // ""' "$workspace_json")"
workspace_first_preview="$(jq -r '.payload.files[0].textPreview // ""' "$workspace_json")"
workspace_expected_path_found="false"
if [[ -n "$EXPECTED_PATH" ]]; then
  workspace_expected_path_found="$(jq -r --arg path "$EXPECTED_PATH" '(.payload.files // []) | map(.relativePath) | index($path) != null' "$workspace_json")"
fi

workspace_read_ok="$(jq -r '.ok // false' "$workspace_read_json")"
workspace_read_command="$(jq -r '.payload.command // ""' "$workspace_read_json")"
workspace_read_relative_path="$(jq -r '.payload.relativePath // ""' "$workspace_read_json")"
workspace_read_text="$(jq -r '.payload.text // ""' "$workspace_read_json")"
workspace_read_text_truncated="$(jq -r '.payload.textTruncated // false' "$workspace_read_json")"
workspace_read_size_bytes="$(jq -r '.payload.sizeBytes // -1' "$workspace_read_json")"
workspace_read_document_kind="$(jq -r '.payload.document.kind // ""' "$workspace_read_json")"

[[ "$status_ok" == "true" ]] || record_failure "status_not_ok"
[[ "$status_pod_available" == "true" ]] || record_failure "status_pod_unavailable"
[[ "$status_pod_ready" == "true" ]] || record_failure "status_pod_not_ready"
[[ "$status_pod_version" == "$EXPECTED_VERSION" ]] || record_failure "status_version_mismatch"
[[ "$status_pod_verified_count" == "$EXPECTED_FILE_COUNT" ]] || record_failure "status_verified_file_count_mismatch"

if [[ "$skip_helper_invocations" == "0" ]]; then
  [[ "$health_ok" == "true" ]] || record_failure "pod_health_not_ok"
  [[ "$health_command" == "pod.health" ]] || record_failure "pod_health_command_mismatch"
  [[ "$health_ready" == "true" ]] || record_failure "pod_health_not_ready"
  [[ "$health_local_execution" == "true" ]] || record_failure "pod_health_local_execution_unavailable"
  [[ "$health_version" == "$EXPECTED_VERSION" ]] || record_failure "pod_health_version_mismatch"
  [[ "$health_verified_count" == "$EXPECTED_FILE_COUNT" ]] || record_failure "pod_health_verified_file_count_mismatch"

  [[ "$manifest_ok" == "true" ]] || record_failure "pod_manifest_describe_not_ok"
  [[ "$manifest_command" == "pod.manifest.describe" ]] || record_failure "pod_manifest_describe_command_mismatch"
  [[ "$manifest_source" == "installed" ]] || record_failure "pod_manifest_describe_manifest_source_mismatch"
  [[ "$manifest_layout_source" == "installed" ]] || record_failure "pod_manifest_describe_layout_source_mismatch"
  [[ "$manifest_stage_count" == "$EXPECTED_STAGE_COUNT" ]] || record_failure "pod_manifest_describe_stage_count_mismatch"
  [[ "$manifest_directory_count" == "$EXPECTED_STAGE_COUNT" ]] || record_failure "pod_manifest_describe_directory_count_mismatch"
  [[ "$manifest_file_count" == "$EXPECTED_FILE_COUNT" ]] || record_failure "pod_manifest_describe_file_count_mismatch"
  [[ "$manifest_workspace_declared" == "true" ]] || record_failure "pod_manifest_describe_workspace_not_declared"
  [[ "$manifest_workspace_installed" == "true" ]] || record_failure "pod_manifest_describe_workspace_not_installed"
  [[ "$manifest_version" == "$EXPECTED_VERSION" ]] || record_failure "pod_manifest_describe_manifest_version_mismatch"
  [[ "$manifest_layout_version" == "$EXPECTED_VERSION" ]] || record_failure "pod_manifest_describe_layout_version_mismatch"
  [[ "$manifest_workspace_stage_file_count" == "$EXPECTED_WORKSPACE_STAGE_FILE_COUNT" ]] || record_failure "pod_manifest_describe_workspace_file_count_mismatch"

  [[ "$runtime_describe_ok" == "true" ]] || record_failure "pod_runtime_describe_not_ok"
  [[ "$runtime_describe_command" == "pod.runtime.describe" ]] || record_failure "pod_runtime_describe_command_mismatch"
  [[ "$runtime_describe_branch" == "android-desktop-runtime-mainline-20260403" ]] || record_failure "pod_runtime_describe_branch_mismatch"
  [[ "$runtime_describe_status" != "" ]] || record_failure "pod_runtime_describe_missing_status"
  [[ "$runtime_describe_engine_domain" == "true" ]] || record_failure "pod_runtime_describe_missing_engine_domain"
  [[ "$runtime_describe_environment_domain" == "true" ]] || record_failure "pod_runtime_describe_missing_environment_domain"
  [[ "$runtime_describe_browser_domain" == "true" ]] || record_failure "pod_runtime_describe_missing_browser_domain"
  [[ "$runtime_describe_tools_domain" == "true" ]] || record_failure "pod_runtime_describe_missing_tools_domain"
  [[ "$runtime_describe_plugins_domain" == "true" ]] || record_failure "pod_runtime_describe_missing_plugins_domain"

  [[ "$runtime_execute_ok" == "true" ]] || record_failure "pod_runtime_execute_not_ok"
  [[ "$runtime_execute_command" == "pod.runtime.execute" ]] || record_failure "pod_runtime_execute_command_mismatch"
  [[ "$runtime_execute_task_id" == "$RUNTIME_TASK_ID" ]] || record_failure "pod_runtime_execute_task_mismatch"
  [[ "$runtime_execute_runtime_home_ready" == "true" ]] || record_failure "pod_runtime_execute_runtime_home_not_ready"
  [[ "$runtime_execute_engine_id" == "embedded-runtime-task-engine-v1" ]] || record_failure "pod_runtime_execute_engine_id_mismatch"
  [[ "$runtime_execute_execution_count" -ge 1 ]] || record_failure "pod_runtime_execute_execution_count_invalid"
  [[ "$runtime_execute_state_path" == *"/state/"* ]] || record_failure "pod_runtime_execute_missing_state_path"
  [[ "$runtime_execute_log_path" == *"/logs/"* ]] || record_failure "pod_runtime_execute_missing_log_path"

  [[ "$workspace_ok" == "true" ]] || record_failure "pod_workspace_scan_not_ok"
  [[ "$workspace_command" == "pod.workspace.scan" ]] || record_failure "pod_workspace_scan_command_mismatch"
  [[ "$workspace_stage_present" == "true" ]] || record_failure "pod_workspace_stage_missing"
  [[ "$workspace_stage_manifest_present" == "true" ]] || record_failure "pod_workspace_stage_manifest_missing"
  [[ "$workspace_content_index_present" == "true" ]] || record_failure "pod_workspace_content_index_missing"
  [[ "$workspace_stage_name" == "workspace" ]] || record_failure "pod_workspace_stage_name_mismatch"
  [[ "$workspace_document_count" == "$EXPECTED_DOCUMENT_COUNT" ]] || record_failure "pod_workspace_document_count_mismatch"
  [[ "$workspace_matched_file_count" -ge 1 ]] || record_failure "pod_workspace_no_matched_files"
  [[ "$workspace_returned_file_count" -ge 1 ]] || record_failure "pod_workspace_no_returned_files"
  [[ -n "$workspace_first_preview" ]] || record_failure "pod_workspace_missing_text_preview"
  if [[ -n "$EXPECTED_PATH" ]]; then
    [[ "$workspace_expected_path_found" == "true" ]] || record_failure "pod_workspace_expected_path_missing"
  fi

  [[ "$workspace_read_ok" == "true" ]] || record_failure "pod_workspace_read_not_ok"
  [[ "$workspace_read_command" == "pod.workspace.read" ]] || record_failure "pod_workspace_read_command_mismatch"
  [[ "$workspace_read_relative_path" == "$EXPECTED_PATH" ]] || record_failure "pod_workspace_read_path_mismatch"
  [[ -n "$workspace_read_text" ]] || record_failure "pod_workspace_read_missing_text"
  if [[ -n "$EXPECTED_WORKSPACE_FILE_SIZE" ]]; then
    [[ "$workspace_read_size_bytes" == "$EXPECTED_WORKSPACE_FILE_SIZE" ]] || record_failure "pod_workspace_read_size_mismatch"
  fi
fi

failed_checks_json='[]'
if [[ ${#failed_checks[@]} -gt 0 ]]; then
  failed_checks_json="$(printf '%s\n' "${failed_checks[@]}" | jq -R . | jq -s .)"
fi

jq -n \
  --arg baseUrl "$BASE_URL" \
  --arg expectedVersion "$EXPECTED_VERSION" \
  --argjson expectedStageCount "$EXPECTED_STAGE_COUNT" \
  --argjson expectedDocumentCount "$EXPECTED_DOCUMENT_COUNT" \
  --argjson expectedFileCount "$EXPECTED_FILE_COUNT" \
  --argjson expectedWorkspaceStageFileCount "$EXPECTED_WORKSPACE_STAGE_FILE_COUNT" \
  --arg expectedPath "$EXPECTED_PATH" \
  --arg query "$QUERY" \
  --argjson limit "$LIMIT" \
  --arg failureHint "$failure_hint" \
  --arg skipHelperInvocations "$skip_helper_invocations" \
  --argjson failedChecks "$failed_checks_json" \
  --arg statusOk "$status_ok" \
  --arg statusMode "$status_mode" \
  --arg statusPodAvailable "$status_pod_available" \
  --arg statusPodReady "$status_pod_ready" \
  --arg statusPodVersion "$status_pod_version" \
  --arg statusPodVerifiedCount "$status_pod_verified_count" \
  --arg capHasHealth "$cap_has_health" \
  --arg capHasManifestDescribe "$cap_has_manifest_describe" \
  --arg capHasRuntimeDescribe "$cap_has_runtime_describe" \
  --arg capHasRuntimeExecute "$cap_has_runtime_execute" \
  --arg capHasWorkspaceScan "$cap_has_workspace_scan" \
  --arg capHasWorkspaceRead "$cap_has_workspace_read" \
  --arg capWriteEnabled "$cap_write_enabled" \
  --arg healthOk "$health_ok" \
  --arg healthCommand "$health_command" \
  --arg healthReady "$health_ready" \
  --arg healthLocalExecution "$health_local_execution" \
  --arg healthVersion "$health_version" \
  --arg healthVerifiedCount "$health_verified_count" \
  --arg manifestOk "$manifest_ok" \
  --arg manifestCommand "$manifest_command" \
  --arg manifestSource "$manifest_source" \
  --arg manifestLayoutSource "$manifest_layout_source" \
  --arg manifestStageCount "$manifest_stage_count" \
  --arg manifestDirectoryCount "$manifest_directory_count" \
  --arg manifestFileCount "$manifest_file_count" \
  --arg manifestWorkspaceDeclared "$manifest_workspace_declared" \
  --arg manifestWorkspaceInstalled "$manifest_workspace_installed" \
  --arg manifestVersion "$manifest_version" \
  --arg manifestLayoutVersion "$manifest_layout_version" \
  --arg manifestWorkspaceStageFileCount "$manifest_workspace_stage_file_count" \
  --arg runtimeDescribeOk "$runtime_describe_ok" \
  --arg runtimeDescribeCommand "$runtime_describe_command" \
  --arg runtimeDescribeBranch "$runtime_describe_branch" \
  --arg runtimeDescribeStatus "$runtime_describe_status" \
  --arg runtimeDescribeEngineDomain "$runtime_describe_engine_domain" \
  --arg runtimeDescribeEnvironmentDomain "$runtime_describe_environment_domain" \
  --arg runtimeDescribeBrowserDomain "$runtime_describe_browser_domain" \
  --arg runtimeDescribeToolsDomain "$runtime_describe_tools_domain" \
  --arg runtimeDescribePluginsDomain "$runtime_describe_plugins_domain" \
  --arg runtimeExecuteOk "$runtime_execute_ok" \
  --arg runtimeExecuteCommand "$runtime_execute_command" \
  --arg runtimeExecuteTaskId "$runtime_execute_task_id" \
  --arg runtimeExecuteRuntimeHomeReady "$runtime_execute_runtime_home_ready" \
  --arg runtimeExecuteEngineId "$runtime_execute_engine_id" \
  --arg runtimeExecuteExecutionCount "$runtime_execute_execution_count" \
  --arg runtimeExecuteStatePath "$runtime_execute_state_path" \
  --arg runtimeExecuteLogPath "$runtime_execute_log_path" \
  --arg workspaceOk "$workspace_ok" \
  --arg workspaceCommand "$workspace_command" \
  --arg workspaceStagePresent "$workspace_stage_present" \
  --arg workspaceStageManifestPresent "$workspace_stage_manifest_present" \
  --arg workspaceContentIndexPresent "$workspace_content_index_present" \
  --arg workspaceStageName "$workspace_stage_name" \
  --arg workspaceDocumentCount "$workspace_document_count" \
  --arg workspaceMatchedFileCount "$workspace_matched_file_count" \
  --arg workspaceReturnedFileCount "$workspace_returned_file_count" \
  --arg workspaceExpectedPathFound "$workspace_expected_path_found" \
  --arg workspaceFirstPath "$workspace_first_path" \
  --arg workspaceFirstPreview "$workspace_first_preview" \
  --arg workspaceReadOk "$workspace_read_ok" \
  --arg workspaceReadCommand "$workspace_read_command" \
  --arg workspaceReadRelativePath "$workspace_read_relative_path" \
  --arg workspaceReadText "$workspace_read_text" \
  --arg workspaceReadTextTruncated "$workspace_read_text_truncated" \
  --arg workspaceReadSizeBytes "$workspace_read_size_bytes" \
  --arg workspaceReadDocumentKind "$workspace_read_document_kind" \
  '{
    ok: ($failedChecks | length == 0),
    baseUrl: $baseUrl,
    hint: (if $failureHint == "" then null else $failureHint end),
    expected: {
      podVersion: $expectedVersion,
      stageCount: $expectedStageCount,
      assetFileCount: $expectedFileCount,
      workspaceStageFileCount: $expectedWorkspaceStageFileCount,
      contentIndexDocumentCount: $expectedDocumentCount,
      expectedPath: (if $expectedPath == "" then null else $expectedPath end),
      query: $query,
      limit: $limit
    },
    failedChecks: $failedChecks,
    preflight: {
      helperInvocationsSkipped: ($skipHelperInvocations == "1")
    },
    status: {
      ok: ($statusOk == "true"),
      mode: (if $statusMode == "" then null else $statusMode end),
      embeddedRuntimePodAvailable: ($statusPodAvailable == "true"),
      ready: ($statusPodReady == "true"),
      manifestVersion: (if $statusPodVersion == "" then null else $statusPodVersion end),
      verifiedFileCount: ($statusPodVerifiedCount | tonumber)
    },
    capabilities: {
      hasPodHealth: ($capHasHealth == "true"),
      hasPodManifestDescribe: ($capHasManifestDescribe == "true"),
      hasPodRuntimeDescribe: ($capHasRuntimeDescribe == "true"),
      hasPodRuntimeExecute: ($capHasRuntimeExecute == "true"),
      hasPodWorkspaceScan: ($capHasWorkspaceScan == "true"),
      hasPodWorkspaceRead: ($capHasWorkspaceRead == "true"),
      writeEnabled: ($capWriteEnabled == "true")
    },
    podHealth: {
      ok: ($healthOk == "true"),
      command: (if $healthCommand == "" then null else $healthCommand end),
      ready: ($healthReady == "true"),
      localExecutionAvailable: ($healthLocalExecution == "true"),
      manifestVersion: (if $healthVersion == "" then null else $healthVersion end),
      verifiedFileCount: ($healthVerifiedCount | tonumber)
    },
    podManifestDescribe: {
      ok: ($manifestOk == "true"),
      command: (if $manifestCommand == "" then null else $manifestCommand end),
      manifestSource: (if $manifestSource == "" then null else $manifestSource end),
      layoutSource: (if $manifestLayoutSource == "" then null else $manifestLayoutSource end),
      stageCount: ($manifestStageCount | tonumber),
      directoryCount: ($manifestDirectoryCount | tonumber),
      fileCount: ($manifestFileCount | tonumber),
      workspaceStageDeclared: ($manifestWorkspaceDeclared == "true"),
      workspaceStageInstalled: ($manifestWorkspaceInstalled == "true"),
      manifestVersion: (if $manifestVersion == "" then null else $manifestVersion end),
      layoutVersion: (if $manifestLayoutVersion == "" then null else $manifestLayoutVersion end),
      workspaceStageFileCount: ($manifestWorkspaceStageFileCount | tonumber)
    },
    podRuntimeDescribe: {
      ok: ($runtimeDescribeOk == "true"),
      command: (if $runtimeDescribeCommand == "" then null else $runtimeDescribeCommand end),
      mainlineBranch: (if $runtimeDescribeBranch == "" then null else $runtimeDescribeBranch end),
      mainlineStatus: (if $runtimeDescribeStatus == "" then null else $runtimeDescribeStatus end),
      hasEngineDomain: ($runtimeDescribeEngineDomain == "true"),
      hasEnvironmentDomain: ($runtimeDescribeEnvironmentDomain == "true"),
      hasBrowserDomain: ($runtimeDescribeBrowserDomain == "true"),
      hasToolsDomain: ($runtimeDescribeToolsDomain == "true"),
      hasPluginsDomain: ($runtimeDescribePluginsDomain == "true")
    },
    podRuntimeExecute: {
      ok: ($runtimeExecuteOk == "true"),
      command: (if $runtimeExecuteCommand == "" then null else $runtimeExecuteCommand end),
      taskId: (if $runtimeExecuteTaskId == "" then null else $runtimeExecuteTaskId end),
      runtimeHomeReady: ($runtimeExecuteRuntimeHomeReady == "true"),
      engineId: (if $runtimeExecuteEngineId == "" then null else $runtimeExecuteEngineId end),
      executionCount: ($runtimeExecuteExecutionCount | tonumber),
      stateFilePath: (if $runtimeExecuteStatePath == "" then null else $runtimeExecuteStatePath end),
      logFilePath: (if $runtimeExecuteLogPath == "" then null else $runtimeExecuteLogPath end)
    },
    podWorkspaceScan: {
      ok: ($workspaceOk == "true"),
      command: (if $workspaceCommand == "" then null else $workspaceCommand end),
      workspaceStagePresent: ($workspaceStagePresent == "true"),
      stageManifestPresent: ($workspaceStageManifestPresent == "true"),
      contentIndexPresent: ($workspaceContentIndexPresent == "true"),
      stageName: (if $workspaceStageName == "" then null else $workspaceStageName end),
      documentCount: ($workspaceDocumentCount | tonumber),
      matchedFileCount: ($workspaceMatchedFileCount | tonumber),
      returnedFileCount: ($workspaceReturnedFileCount | tonumber),
      expectedPathFound: ($workspaceExpectedPathFound == "true"),
      firstPath: (if $workspaceFirstPath == "" then null else $workspaceFirstPath end),
      firstPreview: (if $workspaceFirstPreview == "" then null else $workspaceFirstPreview end)
    },
    podWorkspaceRead: {
      ok: ($workspaceReadOk == "true"),
      command: (if $workspaceReadCommand == "" then null else $workspaceReadCommand end),
      relativePath: (if $workspaceReadRelativePath == "" then null else $workspaceReadRelativePath end),
      sizeBytes: ($workspaceReadSizeBytes | tonumber),
      textPresent: ($workspaceReadText != ""),
      textTruncated: ($workspaceReadTextTruncated == "true"),
      documentKind: (if $workspaceReadDocumentKind == "" then null else $workspaceReadDocumentKind end)
    }
  }' >"$summary_json"

printf 'runtime_pod.status ok=%s mode=%s ready=%s version=%s verified=%s\n' \
  "$status_ok" "$status_mode" "$status_pod_ready" "$status_pod_version" "$status_pod_verified_count"
printf 'runtime_pod.health ok=%s ready=%s local=%s version=%s verified=%s\n' \
  "$health_ok" "$health_ready" "$health_local_execution" "$health_version" "$health_verified_count"
printf 'runtime_pod.manifest ok=%s source=%s layout=%s stages=%s files=%s workspace_files=%s\n' \
  "$manifest_ok" "$manifest_source" "$manifest_layout_source" "$manifest_stage_count" "$manifest_file_count" "$manifest_workspace_stage_file_count"
printf 'runtime_pod.runtime_describe ok=%s branch=%s status=%s engine=%s browser=%s tools=%s plugins=%s\n' \
  "$runtime_describe_ok" "$runtime_describe_branch" "$runtime_describe_status" "$runtime_describe_engine_domain" "$runtime_describe_browser_domain" "$runtime_describe_tools_domain" "$runtime_describe_plugins_domain"
printf 'runtime_pod.runtime_execute ok=%s task=%s home=%s engine=%s count=%s\n' \
  "$runtime_execute_ok" "$runtime_execute_task_id" "$runtime_execute_runtime_home_ready" "$runtime_execute_engine_id" "$runtime_execute_execution_count"
printf 'runtime_pod.workspace ok=%s stage=%s docs=%s matched=%s returned=%s first=%s\n' \
  "$workspace_ok" "$workspace_stage_present" "$workspace_document_count" "$workspace_matched_file_count" "$workspace_returned_file_count" "$workspace_first_path"
printf 'runtime_pod.workspace_read ok=%s path=%s size=%s truncated=%s kind=%s\n' \
  "$workspace_read_ok" "$workspace_read_relative_path" "$workspace_read_size_bytes" "$workspace_read_text_truncated" "$workspace_read_document_kind"
if [[ -n "$failure_hint" ]]; then
  echo "runtime_pod.hint=$failure_hint"
fi

if [[ "$smoke_failed" != "0" ]]; then
  echo "runtime_pod.smoke=failed" >&2
  jq '.' "$summary_json" >&2
  exit 1
fi

echo "runtime_pod.smoke=completed"
echo "artifacts.dir=$ARTIFACT_DIR"
echo "artifacts.status=$status_json"
echo "artifacts.capabilities=$capabilities_json"
echo "artifacts.health=$health_json"
echo "artifacts.manifest=$manifest_json"
echo "artifacts.runtime_describe=$runtime_describe_json"
echo "artifacts.runtime_execute=$runtime_execute_json"
echo "artifacts.workspace_scan=$workspace_json"
echo "artifacts.workspace_read=$workspace_read_json"
echo "artifacts.summary=$summary_json"
