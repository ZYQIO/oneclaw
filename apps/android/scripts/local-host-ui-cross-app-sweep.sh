#!/usr/bin/env bash
set -euo pipefail

WINDOWS_MS_RAW="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWEEP_WINDOWS_MS:-5000,15000,30000}"
STOP_ON_FIRST_NON_REACHABLE="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_STOP_ON_FIRST_NON_REACHABLE:-true}"
ARTIFACT_ROOT="${OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR:-$(mktemp -d -t openclaw-android-local-host-cross-app-sweep.XXXXXX)}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROBE_SCRIPT="$SCRIPT_DIR/local-host-ui-cross-app-probe.sh"
SUMMARY_JSON="$ARTIFACT_ROOT/summary.json"
SWEEP_JSONL="$ARTIFACT_ROOT/sweep.jsonl"

usage() {
  cat <<'EOF'
Usage:
  OPENCLAW_ANDROID_LOCAL_HOST_TOKEN=<token> \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWEEP_WINDOWS_MS=5000,15000,30000] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_STOP_ON_FIRST_NON_REACHABLE=true] \
  ./apps/android/scripts/local-host-ui-cross-app-sweep.sh

What it does:
  1. Reuses the cross-app probe across multiple observation windows
  2. Stores each probe run in its own artifact directory
  3. Produces a compact sweep summary so the first non-reachable window is easy to spot

Requirements:
  - Everything required by local-host-ui-cross-app-probe.sh
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ ! -x "$PROBE_SCRIPT" ]]; then
  echo "Cross-app probe script is missing or not executable: $PROBE_SCRIPT" >&2
  exit 1
fi

mkdir -p "$ARTIFACT_ROOT"
: >"$SWEEP_JSONL"

IFS=',' read -r -a raw_windows <<<"$WINDOWS_MS_RAW"
if [[ "${#raw_windows[@]}" -eq 0 ]]; then
  echo "No sweep windows configured." >&2
  exit 1
fi

first_non_reachable_window_ms=""
first_non_reachable_classification=""
first_non_reachable_artifact_dir=""
all_windows_reachable=true
run_count=0

echo "local_host.ui_cross_app_sweep=starting"
echo "artifacts.root=$ARTIFACT_ROOT"
echo "sweep.windows_ms=$WINDOWS_MS_RAW"

for raw_window in "${raw_windows[@]}"; do
  window_ms="${raw_window//[[:space:]]/}"
  if [[ -z "$window_ms" ]]; then
    continue
  fi
  if ! [[ "$window_ms" =~ ^[0-9]+$ ]] || [[ "$window_ms" -le 0 ]]; then
    echo "Invalid observation window: $window_ms" >&2
    exit 1
  fi

  run_count=$((run_count + 1))
  run_dir="$ARTIFACT_ROOT/window-${window_ms}ms"
  mkdir -p "$run_dir"

  echo "cross_app_sweep.window_ms=$window_ms run=$run_count"

  OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR="$run_dir" \
    OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_OBSERVE_WINDOW_MS="$window_ms" \
    bash "$PROBE_SCRIPT"

  run_summary_json="$run_dir/summary.json"
  if [[ ! -f "$run_summary_json" ]]; then
    echo "Missing probe summary: $run_summary_json" >&2
    exit 1
  fi

  classification="$(jq -r '.classification // ""' "$run_summary_json")"
  status_success_count="$(jq -r '.statusSuccessCount // 0' "$run_summary_json")"
  target_top_count="$(jq -r '.targetTopCount // 0' "$run_summary_json")"
  recovery_ok="$(jq -r '.recovery.ok // false' "$run_summary_json")"

  jq -n \
    --argjson run "$run_count" \
    --argjson observeWindowMs "$window_ms" \
    --arg classification "$classification" \
    --argjson statusSuccessCount "$status_success_count" \
    --argjson targetTopCount "$target_top_count" \
    --argjson recoveryOk "$( [[ "$recovery_ok" == "true" ]] && printf 'true' || printf 'false' )" \
    --arg artifactDir "$run_dir" \
    '{
      run: $run,
      observeWindowMs: $observeWindowMs,
      classification: $classification,
      statusSuccessCount: $statusSuccessCount,
      targetTopCount: $targetTopCount,
      recoveryOk: $recoveryOk,
      artifactDir: $artifactDir
    }' >>"$SWEEP_JSONL"
  printf '\n' >>"$SWEEP_JSONL"

  printf 'cross_app_sweep.result window_ms=%s classification=%s status_success=%s target_top=%s recovery_ok=%s\n' \
    "$window_ms" "$classification" "$status_success_count" "$target_top_count" "$recovery_ok"

  if [[ "$classification" != "foregrounded_host_reachable" || "$recovery_ok" != "true" ]]; then
    all_windows_reachable=false
    if [[ -z "$first_non_reachable_window_ms" ]]; then
      first_non_reachable_window_ms="$window_ms"
      first_non_reachable_classification="$classification"
      first_non_reachable_artifact_dir="$run_dir"
    fi
    if [[ "$STOP_ON_FIRST_NON_REACHABLE" == "true" ]]; then
      break
    fi
  fi
done

jq -n \
  --arg windowsMs "$WINDOWS_MS_RAW" \
  --argjson runCount "$run_count" \
  --argjson allWindowsReachable "$( [[ "$all_windows_reachable" == "true" ]] && printf 'true' || printf 'false' )" \
  --arg firstNonReachableWindowMs "$first_non_reachable_window_ms" \
  --arg firstNonReachableClassification "$first_non_reachable_classification" \
  --arg firstNonReachableArtifactDir "$first_non_reachable_artifact_dir" \
  --slurpfile runs "$SWEEP_JSONL" \
  '{
    windowsMs: ($windowsMs | split(",") | map(gsub(" "; "")) | map(select(length > 0)) | map(tonumber)),
    runCount: $runCount,
    allWindowsReachable: $allWindowsReachable,
    firstNonReachableWindowMs: (
      if $firstNonReachableWindowMs == "" then null else ($firstNonReachableWindowMs | tonumber) end
    ),
    firstNonReachableClassification: (
      if $firstNonReachableClassification == "" then null else $firstNonReachableClassification end
    ),
    firstNonReachableArtifactDir: (
      if $firstNonReachableArtifactDir == "" then null else $firstNonReachableArtifactDir end
    ),
    runs: $runs
  }' >"$SUMMARY_JSON"

if [[ "$all_windows_reachable" == "true" ]]; then
  echo "cross_app_sweep.boundary=not_found_within_windows"
else
  printf 'cross_app_sweep.boundary window_ms=%s classification=%s\n' \
    "$first_non_reachable_window_ms" "$first_non_reachable_classification"
fi

echo "local_host.ui_cross_app_sweep=completed"
echo "artifacts.summary=$SUMMARY_JSON"
echo "artifacts.runs=$SWEEP_JSONL"
