#!/usr/bin/env bash
set -euo pipefail

ARTIFACT_ROOT="${OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR:-$(mktemp -d -t openclaw-android-local-host-cross-app-next.XXXXXX)}"
FOLLOW_UP_PRESET="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PRESET:-settings-search-input}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROBE_SCRIPT="$SCRIPT_DIR/local-host-ui-cross-app-probe.sh"
SWEEP_SCRIPT="$SCRIPT_DIR/local-host-ui-cross-app-sweep.sh"
DESCRIBE_ONLY=false
RUN_SWEEP=false

usage() {
  cat <<'EOF'
Usage:
  OPENCLAW_ANDROID_LOCAL_HOST_TOKEN=<token> \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PRESET=settings-search-input] \
  ./apps/android/scripts/local-host-ui-cross-app-next.sh

  ./apps/android/scripts/local-host-ui-cross-app-next.sh --describe
  ./apps/android/scripts/local-host-ui-cross-app-next.sh --sweep

What it does:
  1. Defaults the existing cross-app follow-up harness to the current repo preset
  2. Reuses the underlying probe or sweep script without retyping all env vars
  3. Writes a wrapper-level next-summary.json with both describe metadata and the final result

Requirements:
  - jq
  - Everything required by the underlying probe or sweep script
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    --)
      shift
      ;;
    --describe)
      DESCRIBE_ONLY=true
      shift
      ;;
    --sweep)
      RUN_SWEEP=true
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

shell_quote() {
  printf '%q' "$1"
}

probe_describe_json() {
  OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PRESET="$FOLLOW_UP_PRESET" \
    bash "$PROBE_SCRIPT" --describe | jq -Rn '
      reduce inputs as $line ({};
        ($line | capture("^(?<key>[^=]+)=(?<value>.*)$")?) as $kv
        | if $kv == null then . else . + {($kv.key): $kv.value} end
      )'
}

sweep_describe_json() {
  OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PRESET="$FOLLOW_UP_PRESET" \
    bash "$SWEEP_SCRIPT" --describe
}

append_recommended_env() {
  local env_name=$1
  local env_value=${!env_name-}
  if [[ -z "${env_value}" ]]; then
    return 0
  fi
  printf '%s=%s\n' "$env_name" "$(shell_quote "$env_value")"
}

build_forwarded_env_assignments() {
  local env_name
  local env_names=(
    OPENCLAW_ANDROID_LOCAL_HOST_BASE_URL
    OPENCLAW_ANDROID_LOCAL_HOST_PORT
    OPENCLAW_ANDROID_LOCAL_HOST_UI_REQUEST_TIMEOUT_SEC
    OPENCLAW_ANDROID_LOCAL_HOST_UI_APP_PACKAGE
    OPENCLAW_ANDROID_LOCAL_HOST_UI_APP_COMPONENT
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PACKAGE
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_OBSERVE_WINDOW_MS
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_POLL_INTERVAL_MS
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_RECOVERY_WAIT_MS
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_FORCE_STOP_TARGET_BEFORE_LAUNCH
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_WAIT_TEXT
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_WAIT_MATCH_MODE
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_WAIT_TIMEOUT_MS
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_WAIT_POLL_INTERVAL_MS
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_FINAL_WAIT_TEXT
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_FINAL_WAIT_MATCH_MODE
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_FINAL_WAIT_TIMEOUT_MS
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_FINAL_WAIT_POLL_INTERVAL_MS
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_TAP_TEXT
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_TAP_CONTENT_DESCRIPTION
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_TAP_RESOURCE_ID
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_TAP_MATCH_MODE
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_TAP_INDEX
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_INPUT_VALUE
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_INPUT_TEXT
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_INPUT_CONTENT_DESCRIPTION
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_INPUT_RESOURCE_ID
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_INPUT_MATCH_MODE
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_INPUT_INDEX
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_START_X
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_START_Y
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_END_X
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_END_Y
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_START_X_RATIO
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_START_Y_RATIO
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_END_X_RATIO
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_END_Y_RATIO
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_DURATION_MS
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_FOLLOW_UP_FOREGROUND_TIMEOUT_MS
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_FOLLOW_UP_FOREGROUND_POLL_INTERVAL_MS
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_FOLLOW_UP_SETTLE_MS
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWEEP_WINDOWS_MS
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_STOP_ON_FIRST_NON_REACHABLE
  )

  for env_name in "${env_names[@]}"; do
    append_recommended_env "$env_name"
  done
}

recommended_command() {
  local -a parts=()

  parts+=("OPENCLAW_ANDROID_LOCAL_HOST_TOKEN=<token>")
  parts+=("OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PRESET=$(shell_quote "$FOLLOW_UP_PRESET")")

  while IFS= read -r forwarded_env; do
    if [[ -n "$forwarded_env" ]]; then
      parts+=("$forwarded_env")
    fi
  done < <(build_forwarded_env_assignments)

  if [[ "$RUN_SWEEP" == "true" ]]; then
    parts+=("pnpm" "android:local-host:ui:cross-app:next" "--" "--sweep")
  else
    parts+=("pnpm" "android:local-host:ui:cross-app:next")
  fi

  local output=""
  local part
  for part in "${parts[@]}"; do
    if [[ -n "$output" ]]; then
      output+=" "
    fi
    output+="$part"
  done
  printf '%s' "$output"
}

require_cmd jq

if [[ ! -x "$PROBE_SCRIPT" ]]; then
  echo "Cross-app probe script is missing or not executable: $PROBE_SCRIPT" >&2
  exit 1
fi

if [[ ! -x "$SWEEP_SCRIPT" ]]; then
  echo "Cross-app sweep script is missing or not executable: $SWEEP_SCRIPT" >&2
  exit 1
fi

probe_describe="$(probe_describe_json)"
sweep_describe="null"
if [[ "$RUN_SWEEP" == "true" ]]; then
  sweep_describe="$(sweep_describe_json)"
fi

if [[ "$DESCRIBE_ONLY" == "true" ]]; then
  jq -n \
    --arg script "local-host-ui-cross-app-next.sh" \
    --arg command "./apps/android/scripts/local-host-ui-cross-app-next.sh" \
    --arg packageCommand "pnpm android:local-host:ui:cross-app:next" \
    --arg mode "$( [[ "$RUN_SWEEP" == "true" ]] && printf '%s' 'sweep' || printf '%s' 'probe' )" \
    --arg preset "$FOLLOW_UP_PRESET" \
    --arg artifactRoot "$ARTIFACT_ROOT" \
    --arg recommendedCommand "$(recommended_command)" \
    --argjson probeDescribe "$probe_describe" \
    --argjson sweepDescribe "$sweep_describe" \
    '{
      script: $script,
      command: $command,
      packageCommand: $packageCommand,
      mode: $mode,
      preset: $preset,
      artifactRoot: $artifactRoot,
      describeOnly: true,
      recommendedCommand: $recommendedCommand,
      probeDescribe: $probeDescribe,
      sweepDescribe: $sweepDescribe
    }'
  exit 0
fi

mkdir -p "$ARTIFACT_ROOT"
run_dir="$ARTIFACT_ROOT/probe"
result_summary="$run_dir/summary.json"

if [[ "$RUN_SWEEP" == "true" ]]; then
  run_dir="$ARTIFACT_ROOT/sweep"
  result_summary="$run_dir/summary.json"
  mkdir -p "$run_dir"
  OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR="$run_dir" \
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PRESET="$FOLLOW_UP_PRESET" \
    bash "$SWEEP_SCRIPT"
else
  mkdir -p "$run_dir"
  OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR="$run_dir" \
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PRESET="$FOLLOW_UP_PRESET" \
    bash "$PROBE_SCRIPT"
fi

if [[ ! -f "$result_summary" ]]; then
  echo "Missing result summary: $result_summary" >&2
  exit 1
fi

next_summary="$ARTIFACT_ROOT/next-summary.json"

jq -n \
  --arg script "local-host-ui-cross-app-next.sh" \
  --arg command "./apps/android/scripts/local-host-ui-cross-app-next.sh" \
  --arg packageCommand "pnpm android:local-host:ui:cross-app:next" \
  --arg mode "$( [[ "$RUN_SWEEP" == "true" ]] && printf '%s' 'sweep' || printf '%s' 'probe' )" \
  --arg preset "$FOLLOW_UP_PRESET" \
  --arg artifactRoot "$ARTIFACT_ROOT" \
  --arg runDir "$run_dir" \
  --arg resultSummaryPath "$result_summary" \
  --arg recommendedCommand "$(recommended_command)" \
  --argjson probeDescribe "$probe_describe" \
  --argjson sweepDescribe "$sweep_describe" \
  --slurpfile result "$result_summary" \
  '{
    script: $script,
    command: $command,
    packageCommand: $packageCommand,
    mode: $mode,
    preset: $preset,
    artifactRoot: $artifactRoot,
    runDir: $runDir,
    resultSummaryPath: $resultSummaryPath,
    recommendedCommand: $recommendedCommand,
    probeDescribe: $probeDescribe,
    sweepDescribe: $sweepDescribe,
    result: ($result[0] // null)
  }' >"$next_summary"

echo "local_host.ui_cross_app_next=completed"
echo "cross_app_next.mode=$( [[ "$RUN_SWEEP" == "true" ]] && printf '%s' 'sweep' || printf '%s' 'probe' )"
echo "cross_app_next.preset=$FOLLOW_UP_PRESET"
echo "artifacts.root=$ARTIFACT_ROOT"
echo "artifacts.result_summary=$result_summary"
echo "artifacts.next_summary=$next_summary"
