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

recommended_command() {
  if [[ "$RUN_SWEEP" == "true" ]]; then
    printf '%s' "OPENCLAW_ANDROID_LOCAL_HOST_TOKEN=<token> OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PRESET=$FOLLOW_UP_PRESET pnpm android:local-host:ui:cross-app:next -- --sweep"
  else
    printf '%s' "OPENCLAW_ANDROID_LOCAL_HOST_TOKEN=<token> OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PRESET=$FOLLOW_UP_PRESET pnpm android:local-host:ui:cross-app:next"
  fi
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
