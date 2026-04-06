#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOKEN_SCRIPT="${OPENCLAW_ANDROID_LOCAL_HOST_TOKEN_SCRIPT:-}"
POD_SMOKE_SCRIPT="${OPENCLAW_ANDROID_LOCAL_HOST_POD_SMOKE_SCRIPT:-$SCRIPT_DIR/local-host-embedded-runtime-pod-smoke.sh}"
BROWSER_SMOKE_SCRIPT="${OPENCLAW_ANDROID_LOCAL_HOST_BROWSER_SMOKE_SCRIPT:-$SCRIPT_DIR/local-host-embedded-runtime-browser-lane-smoke.sh}"
ARTIFACT_DIR="${OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR:-$(mktemp -d -t openclaw-android-runtime-doctor.XXXXXX)}"
JSON=false

usage() {
  cat <<'EOF'
Usage:
  ./apps/android/scripts/local-host-embedded-runtime-pod-doctor.sh
  ./apps/android/scripts/local-host-embedded-runtime-pod-doctor.sh --json

What it does:
  1. Reuses OPENCLAW_ANDROID_LOCAL_HOST_TOKEN when already provided
  2. Otherwise bootstraps the debug local-host token over trusted adb
  3. Runs the embedded-runtime pod baseline smoke
  4. If the pod baseline is healthy, runs the browser-lane smoke
  5. Writes one combined summary.json for the desktop-runtime validation pass

Notes:
  - This wrapper is for diagnosis and validation prep, so it can still exit 0 even when the result is unhealthy
  - Pass OPENCLAW_ANDROID_LOCAL_HOST_BROWSER_START=0 when you want the confirm-only rerun after completing the external browser auth flow
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h | --help)
      usage
      exit 0
      ;;
    --json)
      JSON=true
      shift
      ;;
    --)
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

bool_json() {
  if [[ "${1:-false}" == "true" ]]; then
    printf 'true'
  else
    printf 'false'
  fi
}

run_token_helper() {
  if [[ -n "$TOKEN_SCRIPT" ]]; then
    "$TOKEN_SCRIPT"
  else
    pnpm exec tsx "$SCRIPT_DIR/local-host-token.ts" --json
  fi
}

require_cmd bash
require_cmd jq

mkdir -p "$ARTIFACT_DIR"

token_source="env"
token="${OPENCLAW_ANDROID_LOCAL_HOST_TOKEN:-}"
token_json=""
token_stdout="$ARTIFACT_DIR/token.stdout.txt"

if [[ -z "$token" ]]; then
  if [[ -z "$TOKEN_SCRIPT" ]]; then
    require_cmd pnpm
  fi
  token_source="bootstrap"
  if run_token_helper >"$token_stdout"; then
    token_json="$(cat "$token_stdout")"
  else
    echo "Token bootstrap failed." >&2
    cat "$token_stdout" >&2 || true
    exit 1
  fi
  token="$(printf '%s' "$token_json" | jq -r '.token // ""')"
  if [[ -z "$token" ]]; then
    echo "Token bootstrap did not return a token." >&2
    cat "$token_stdout" >&2
    exit 1
  fi
fi

POD_SMOKE_DIR="$ARTIFACT_DIR/pod-smoke"
POD_SMOKE_STDOUT="$ARTIFACT_DIR/pod-smoke.stdout.txt"
mkdir -p "$POD_SMOKE_DIR"
pod_smoke_exit_code=0
if OPENCLAW_ANDROID_LOCAL_HOST_TOKEN="$token" \
  OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR="$POD_SMOKE_DIR" \
  bash "$POD_SMOKE_SCRIPT" >"$POD_SMOKE_STDOUT" 2>&1; then
  pod_smoke_exit_code=0
else
  pod_smoke_exit_code=$?
fi

POD_SMOKE_SUMMARY="$POD_SMOKE_DIR/summary.json"
if [[ ! -f "$POD_SMOKE_SUMMARY" ]]; then
  echo "Missing pod smoke summary: $POD_SMOKE_SUMMARY" >&2
  cat "$POD_SMOKE_STDOUT" >&2 || true
  exit 1
fi

pod_smoke_ok="$(jq -r '.ok // false' "$POD_SMOKE_SUMMARY")"
pod_smoke_failed_checks="$(jq -r '[.failedChecks[]?] | join(",")' "$POD_SMOKE_SUMMARY")"
pod_smoke_hint="$(jq -r '.hint // ""' "$POD_SMOKE_SUMMARY")"

browser_smoke_executed=false
browser_smoke_exit_code=0
BROWSER_SMOKE_DIR="$ARTIFACT_DIR/browser-lane-smoke"
BROWSER_SMOKE_STDOUT="$ARTIFACT_DIR/browser-lane-smoke.stdout.txt"
BROWSER_SMOKE_SUMMARY="$BROWSER_SMOKE_DIR/summary.json"

if [[ "$pod_smoke_ok" == "true" ]]; then
  mkdir -p "$BROWSER_SMOKE_DIR"
  if OPENCLAW_ANDROID_LOCAL_HOST_TOKEN="$token" \
    OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR="$BROWSER_SMOKE_DIR" \
    bash "$BROWSER_SMOKE_SCRIPT" >"$BROWSER_SMOKE_STDOUT" 2>&1; then
    browser_smoke_executed=true
    browser_smoke_exit_code=0
  else
    browser_smoke_executed=true
    browser_smoke_exit_code=$?
  fi

  if [[ ! -f "$BROWSER_SMOKE_SUMMARY" ]]; then
    echo "Missing browser smoke summary: $BROWSER_SMOKE_SUMMARY" >&2
    cat "$BROWSER_SMOKE_STDOUT" >&2 || true
    exit 1
  fi
fi

browser_smoke_ok="false"
browser_smoke_failed_checks=""
browser_smoke_hint=""
browser_mainline_status=""
browser_recommended_next_slice=""
browser_replay_ready="false"
browser_auth_credential_present="false"

if [[ "$browser_smoke_executed" == "true" ]]; then
  browser_smoke_ok="$(jq -r '.ok // false' "$BROWSER_SMOKE_SUMMARY")"
  browser_smoke_failed_checks="$(jq -r '[.failedChecks[]?] | join(",")' "$BROWSER_SMOKE_SUMMARY")"
  browser_smoke_hint="$(jq -r '.hint // ""' "$BROWSER_SMOKE_SUMMARY")"
  browser_mainline_status="$(jq -r '.runtimeDescribeAfter.mainlineStatus // ""' "$BROWSER_SMOKE_SUMMARY")"
  browser_recommended_next_slice="$(jq -r '.runtimeDescribeAfter.recommendedNextSlice // ""' "$BROWSER_SMOKE_SUMMARY")"
  browser_replay_ready="$(jq -r '.browserDescribeAfter.replayReady // false' "$BROWSER_SMOKE_SUMMARY")"
  browser_auth_credential_present="$(jq -r '.browserDescribeAfter.authCredentialPresent // false' "$BROWSER_SMOKE_SUMMARY")"
fi

classification="desktop_runtime_unhealthy"
recommended_action="inspect-pod-summary"
recommended_command="cat \"$POD_SMOKE_SUMMARY\""

if [[ "$pod_smoke_ok" != "true" ]]; then
  classification="embedded_pod_unhealthy"
  if [[ -n "$pod_smoke_hint" ]]; then
    recommended_action="rerun-pod-baseline"
    recommended_command="$pod_smoke_hint"
  fi
elif [[ "$browser_smoke_executed" != "true" ]]; then
  classification="embedded_pod_ready_browser_pending"
  recommended_action="run-browser-lane-smoke"
  recommended_command="pnpm android:local-host:embedded-runtime-pod:browser-lane:smoke"
elif [[ "$browser_smoke_ok" != "true" ]]; then
  if [[ "$browser_smoke_failed_checks" == *"capabilities_write_disabled"* ]]; then
    classification="browser_lane_write_disabled"
    recommended_action="enable-browser-lane-write"
  elif [[ "$browser_smoke_failed_checks" == *"pod_browser_describe_after_not_replayed"* ]]; then
    classification="browser_lane_replay_missing"
    recommended_action="replay-browser-lane"
  else
    classification="browser_lane_unhealthy"
    recommended_action="inspect-browser-summary"
  fi
  recommended_command="cat \"$BROWSER_SMOKE_SUMMARY\""
  if [[ -n "$browser_smoke_hint" ]]; then
    recommended_command="$browser_smoke_hint"
  fi
elif [[ "$browser_mainline_status" == "desktop_home_configured" ]]; then
  classification="desktop_home_configured"
  recommended_action="none"
  recommended_command=""
elif [[ "$browser_mainline_status" == "process_runtime_supervisor_loop_bootstrapped" ]]; then
  classification="process_runtime_supervisor_loop_bootstrapped"
  recommended_action="advance-active-session"
  recommended_command=""
elif [[ "$browser_mainline_status" == "process_runtime_detached_launch_bootstrapped" ]]; then
  classification="process_runtime_detached_launch_bootstrapped"
  recommended_action="advance-supervisor-loop"
  recommended_command=""
elif [[ "$browser_mainline_status" == "process_runtime_recovery_bootstrapped" ]]; then
  classification="process_runtime_recovery_bootstrapped"
  recommended_action="advance-detached-launch"
  recommended_command=""
elif [[ "$browser_mainline_status" == "process_runtime_observation_bootstrapped" ]]; then
  classification="process_runtime_observation_bootstrapped"
  recommended_action="advance-process-recovery"
  recommended_command=""
elif [[ "$browser_mainline_status" == "process_runtime_supervision_bootstrapped" ]]; then
  classification="process_runtime_supervision_bootstrapped"
  recommended_action="advance-process-observation"
  recommended_command=""
elif [[ "$browser_mainline_status" == "process_runtime_activation_bootstrapped" ]]; then
  classification="process_runtime_activation_bootstrapped"
  recommended_action="advance-process-supervision"
  recommended_command=""
elif [[ "$browser_mainline_status" == "process_model_bootstrapped" ]]; then
  classification="process_model_bootstrapped"
  recommended_action="advance-process-runtime"
  recommended_command=""
elif [[ "$browser_mainline_status" == "plugin_lane_replayed" ]]; then
  classification="plugin_lane_replayed"
  recommended_action="advance-process-model"
  recommended_command=""
elif [[ "$browser_mainline_status" == "browser_lane_configured" ]]; then
  classification="browser_lane_configured"
  recommended_action="none"
  recommended_command=""
elif [[ "$browser_mainline_status" == "browser_lane_replayed" ]]; then
  classification="browser_lane_replayed"
  recommended_action="complete-browser-auth"
  recommended_command="OPENCLAW_ANDROID_LOCAL_HOST_BROWSER_START=0 pnpm android:local-host:embedded-runtime-pod:doctor"
elif [[ "$browser_mainline_status" == "desktop_bundle_ready" ]]; then
  classification="desktop_bundle_ready"
  recommended_action="materialize-desktop-home"
  recommended_command="cat \"$BROWSER_SMOKE_SUMMARY\""
elif [[ "$browser_mainline_status" == "desktop_home_ready" ]]; then
  classification="desktop_home_ready"
  recommended_action="continue-runtime-convergence"
  recommended_command="cat \"$BROWSER_SMOKE_SUMMARY\""
else
  classification="${browser_mainline_status:-desktop_runtime_ready}"
  if [[ "$browser_recommended_next_slice" == "desktop_home_materialize" ]]; then
    recommended_action="materialize-desktop-home"
    recommended_command="cat \"$BROWSER_SMOKE_SUMMARY\""
  elif [[ "$browser_recommended_next_slice" == "browser_lane_complete" ]]; then
    recommended_action="complete-browser-auth"
    recommended_command="OPENCLAW_ANDROID_LOCAL_HOST_BROWSER_START=0 pnpm android:local-host:embedded-runtime-pod:doctor"
  elif [[ "$browser_recommended_next_slice" == "plugin_lane" ]]; then
    recommended_action="stabilize-before-plugin"
    recommended_command=""
  elif [[ "$browser_recommended_next_slice" == "process_model_bootstrap" ]]; then
    recommended_action="advance-process-model"
    recommended_command=""
  elif [[ "$browser_recommended_next_slice" == "process_runtime_activation_bootstrap" ]]; then
    recommended_action="advance-process-runtime"
    recommended_command=""
  elif [[ "$browser_recommended_next_slice" == "process_runtime_supervision" ]]; then
    recommended_action="advance-process-supervision"
    recommended_command=""
  elif [[ "$browser_recommended_next_slice" == "process_runtime_observation" ]]; then
    recommended_action="advance-process-observation"
    recommended_command=""
  elif [[ "$browser_recommended_next_slice" == "process_runtime_recovery" ]]; then
    recommended_action="advance-process-recovery"
    recommended_command=""
  elif [[ "$browser_recommended_next_slice" == "process_runtime_detached_launch" ]]; then
    recommended_action="advance-detached-launch"
    recommended_command=""
  elif [[ "$browser_recommended_next_slice" == "process_runtime_supervisor_loop" ]]; then
    recommended_action="advance-supervisor-loop"
    recommended_command=""
  elif [[ "$browser_recommended_next_slice" == "process_runtime_active_session" ]]; then
    recommended_action="advance-active-session"
    recommended_command=""
  elif [[ "$browser_recommended_next_slice" == "process_runtime_activation" ]]; then
    recommended_action="advance-process-runtime"
    recommended_command=""
  else
    recommended_action="inspect-browser-summary"
    recommended_command="cat \"$BROWSER_SMOKE_SUMMARY\""
  fi
fi

SUMMARY_JSON="$ARTIFACT_DIR/summary.json"
jq_args=(
  -n
  --arg tokenSource "$token_source"
  --arg classification "$classification"
  --arg recommendedAction "$recommended_action"
  --arg recommendedCommand "$recommended_command"
  --arg artifactDir "$ARTIFACT_DIR"
  --arg tokenStdoutPath "$token_stdout"
  --arg podSmokeStdoutPath "$POD_SMOKE_STDOUT"
  --arg podSmokeSummaryPath "$POD_SMOKE_SUMMARY"
  --arg browserSmokeStdoutPath "$BROWSER_SMOKE_STDOUT"
  --arg browserSmokeSummaryPath "$BROWSER_SMOKE_SUMMARY"
  --arg podSmokeFailedChecks "$pod_smoke_failed_checks"
  --arg browserSmokeFailedChecks "$browser_smoke_failed_checks"
  --arg browserMainlineStatus "$browser_mainline_status"
  --arg browserRecommendedNextSlice "$browser_recommended_next_slice"
  --argjson podSmokeExitCode "$pod_smoke_exit_code"
  --argjson podSmokeOk "$(bool_json "$pod_smoke_ok")"
  --argjson browserSmokeExecuted "$(bool_json "$browser_smoke_executed")"
  --argjson browserSmokeExitCode "$browser_smoke_exit_code"
  --argjson browserSmokeOk "$(bool_json "$browser_smoke_ok")"
  --argjson browserReplayReady "$(bool_json "$browser_replay_ready")"
  --argjson browserAuthCredentialPresent "$(bool_json "$browser_auth_credential_present")"
  --slurpfile pod "$POD_SMOKE_SUMMARY"
)

if [[ -n "$token_json" ]]; then
  jq_args+=(--arg tokenJson "$token_json")
else
  jq_args+=(--arg tokenJson "")
fi

if [[ "$browser_smoke_executed" == "true" ]]; then
  jq_args+=(--slurpfile browser "$BROWSER_SMOKE_SUMMARY")
else
  jq_args+=(--argjson browser null)
fi

jq "${jq_args[@]}" '
  {
    classification: $classification,
    recommendedAction: (if $recommendedAction == "" then null else $recommendedAction end),
    recommendedCommand: (if $recommendedCommand == "" then null else $recommendedCommand end),
    token: {
      source: $tokenSource,
      bootstrapStdoutPath: (if $tokenSource == "bootstrap" then $tokenStdoutPath else null end),
      bootstrapResult: (
        if $tokenJson == "" then
          null
        else
          ($tokenJson | fromjson)
        end
      )
    },
    podSmoke: {
      exitCode: $podSmokeExitCode,
      ok: $podSmokeOk,
      failedChecks: (
        if $podSmokeFailedChecks == "" then
          []
        else
          ($podSmokeFailedChecks | split(","))
        end
      ),
      stdoutPath: $podSmokeStdoutPath,
      summaryPath: $podSmokeSummaryPath,
      summary: $pod[0]
    },
    browserLaneSmoke: {
      executed: $browserSmokeExecuted,
      exitCode: (if $browserSmokeExecuted then $browserSmokeExitCode else null end),
      ok: (if $browserSmokeExecuted then $browserSmokeOk else null end),
      failedChecks: (
        if ($browserSmokeExecuted | not) or $browserSmokeFailedChecks == "" then
          []
        else
          ($browserSmokeFailedChecks | split(","))
        end
      ),
      mainlineStatus: (if $browserMainlineStatus == "" then null else $browserMainlineStatus end),
      recommendedNextSlice: (if $browserRecommendedNextSlice == "" then null else $browserRecommendedNextSlice end),
      replayReady: (if $browserSmokeExecuted then $browserReplayReady else null end),
      authCredentialPresent: (if $browserSmokeExecuted then $browserAuthCredentialPresent else null end),
      stdoutPath: (if $browserSmokeExecuted then $browserSmokeStdoutPath else null end),
      summaryPath: (if $browserSmokeExecuted then $browserSmokeSummaryPath else null end),
      summary: (
        if $browser == null then
          null
        else
          $browser[0]
        end
      )
    },
    artifacts: {
      rootDir: $artifactDir
    }
  }
' >"$SUMMARY_JSON"

if [[ "$JSON" == "true" ]]; then
  cat "$SUMMARY_JSON"
  exit 0
fi

printf 'runtime_doctor.token_source=%s\n' "$token_source"
printf 'runtime_doctor.pod_smoke.ok=%s exit_code=%s failed_checks=%s\n' "$pod_smoke_ok" "$pod_smoke_exit_code" "${pod_smoke_failed_checks:-none}"
printf 'runtime_doctor.browser_smoke.executed=%s ok=%s exit_code=%s failed_checks=%s\n' "$browser_smoke_executed" "$browser_smoke_ok" "$browser_smoke_exit_code" "${browser_smoke_failed_checks:-none}"
printf 'runtime_doctor.classification=%s\n' "$classification"
if [[ -n "$recommended_action" ]]; then
  printf 'runtime_doctor.recommended_action=%s\n' "$recommended_action"
fi
if [[ -n "$recommended_command" ]]; then
  printf 'runtime_doctor.recommended_command=%s\n' "$recommended_command"
fi
printf 'artifacts.summary=%s\n' "$SUMMARY_JSON"
