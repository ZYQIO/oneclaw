#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOKEN_SCRIPT="${OPENCLAW_ANDROID_LOCAL_HOST_TOKEN_SCRIPT:-}"
POD_SMOKE_SCRIPT="${OPENCLAW_ANDROID_LOCAL_HOST_POD_SMOKE_SCRIPT:-$SCRIPT_DIR/local-host-embedded-runtime-pod-smoke.sh}"
BROWSER_SMOKE_SCRIPT="${OPENCLAW_ANDROID_LOCAL_HOST_BROWSER_SMOKE_SCRIPT:-$SCRIPT_DIR/local-host-embedded-runtime-browser-lane-smoke.sh}"
ARTIFACT_DIR="${OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR:-$(mktemp -d -t openclaw-android-runtime-doctor.XXXXXX)}"
BROWSER_START="${OPENCLAW_ANDROID_LOCAL_HOST_BROWSER_START:-1}"
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
  - When the first browser-lane pass reaches live proof with OPENCLAW_ANDROID_LOCAL_HOST_BROWSER_START=1, doctor now automatically reruns one confirm-only browser-lane pass with OPENCLAW_ANDROID_LOCAL_HOST_BROWSER_START=0
  - The confirm-only rerun now also checks that browser replay, long-lived process readiness, active-session observation, recovery re-entry, restart continuity, validation observations, and proof-artifact counts stay aligned with the first live-proof capture
  - Pass OPENCLAW_ANDROID_LOCAL_HOST_BROWSER_START=0 when you want only the confirm-only rerun after completing the external browser auth flow
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

append_csv_value() {
  local current=$1
  local next_value=$2
  if [[ -z "$current" ]]; then
    printf '%s' "$next_value"
  else
    printf '%s,%s' "$current" "$next_value"
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

if [[ "$BROWSER_START" != "0" && "$BROWSER_START" != "1" ]]; then
  echo "OPENCLAW_ANDROID_LOCAL_HOST_BROWSER_START must be 0 or 1." >&2
  exit 1
fi

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
    OPENCLAW_ANDROID_LOCAL_HOST_BROWSER_START="$BROWSER_START" \
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
browser_runtime_after_browser_long_lived_process_ready="false"
browser_runtime_after_browser_process_status=""
browser_runtime_after_browser_supervision_status=""
browser_runtime_after_browser_active_session_status=""
browser_runtime_after_browser_active_session_observed="false"
browser_runtime_after_browser_active_session_recovery_reentry_ready="false"
browser_runtime_after_browser_active_session_restart_continuity_ready="false"
browser_runtime_after_browser_active_session_validation_status=""
browser_runtime_after_browser_active_session_validation_lease_renewal_observed="false"
browser_runtime_after_browser_active_session_validation_recovery_reentry_observed="false"
browser_runtime_after_browser_active_session_validation_restart_continuity_observed="false"
browser_runtime_after_browser_active_session_validation_device_proof_required="false"
browser_runtime_after_browser_active_session_device_proof_status=""
browser_runtime_after_browser_active_session_device_proof_observed="false"
browser_runtime_after_browser_active_session_device_proof_live_proof_required="false"
browser_runtime_after_browser_active_session_device_proof_expected_artifact_count="0"
browser_runtime_after_browser_active_session_device_proof_captured_artifact_count="0"
confirm_browser_smoke_required=false
confirm_browser_smoke_executed=false
confirm_browser_smoke_exit_code=0
CONFIRM_BROWSER_SMOKE_DIR="$ARTIFACT_DIR/browser-lane-confirm-smoke"
CONFIRM_BROWSER_SMOKE_STDOUT="$ARTIFACT_DIR/browser-lane-confirm-smoke.stdout.txt"
CONFIRM_BROWSER_SMOKE_SUMMARY="$CONFIRM_BROWSER_SMOKE_DIR/summary.json"
confirm_browser_smoke_ok="false"
confirm_browser_smoke_failed_checks=""
confirm_browser_smoke_hint=""
confirm_browser_mainline_status=""
confirm_browser_recommended_next_slice=""
confirm_browser_replay_ready="false"
confirm_browser_auth_credential_present="false"
confirm_browser_live_proof_replayed="false"
confirm_browser_runtime_after_browser_long_lived_process_ready="false"
confirm_browser_runtime_after_browser_process_status=""
confirm_browser_runtime_after_browser_supervision_status=""
confirm_browser_runtime_after_browser_active_session_status=""
confirm_browser_runtime_after_browser_active_session_observed="false"
confirm_browser_runtime_after_browser_active_session_recovery_reentry_ready="false"
confirm_browser_runtime_after_browser_active_session_restart_continuity_ready="false"
confirm_browser_runtime_after_browser_active_session_validation_status=""
confirm_browser_runtime_after_browser_active_session_validation_lease_renewal_observed="false"
confirm_browser_runtime_after_browser_active_session_validation_recovery_reentry_observed="false"
confirm_browser_runtime_after_browser_active_session_validation_restart_continuity_observed="false"
confirm_browser_runtime_after_browser_active_session_validation_device_proof_required="false"
confirm_browser_runtime_after_browser_active_session_device_proof_status=""
confirm_browser_runtime_after_browser_active_session_device_proof_observed="false"
confirm_browser_runtime_after_browser_active_session_device_proof_live_proof_required="false"
confirm_browser_runtime_after_browser_active_session_device_proof_expected_artifact_count="0"
confirm_browser_runtime_after_browser_active_session_device_proof_captured_artifact_count="0"
confirm_browser_live_proof_continuity_checked=false
confirm_browser_live_proof_continuity_preserved=false
confirm_browser_live_proof_continuity_failed_checks=""

if [[ "$browser_smoke_executed" == "true" ]]; then
  browser_smoke_ok="$(jq -r '.ok // false' "$BROWSER_SMOKE_SUMMARY")"
  browser_smoke_failed_checks="$(jq -r '[.failedChecks[]?] | join(",")' "$BROWSER_SMOKE_SUMMARY")"
  browser_smoke_hint="$(jq -r '.hint // ""' "$BROWSER_SMOKE_SUMMARY")"
  browser_mainline_status="$(jq -r '.runtimeDescribeAfter.mainlineStatus // ""' "$BROWSER_SMOKE_SUMMARY")"
  browser_recommended_next_slice="$(jq -r '.runtimeDescribeAfter.recommendedNextSlice // ""' "$BROWSER_SMOKE_SUMMARY")"
  browser_replay_ready="$(jq -r '.browserDescribeAfter.replayReady // false' "$BROWSER_SMOKE_SUMMARY")"
  browser_auth_credential_present="$(jq -r '.browserDescribeAfter.authCredentialPresent // false' "$BROWSER_SMOKE_SUMMARY")"
  browser_runtime_after_browser_long_lived_process_ready="$(jq -r '.runtimeExecuteAfterBrowser.longLivedProcessReady // false' "$BROWSER_SMOKE_SUMMARY")"
  browser_runtime_after_browser_process_status="$(jq -r '.runtimeExecuteAfterBrowser.processStatus // ""' "$BROWSER_SMOKE_SUMMARY")"
  browser_runtime_after_browser_supervision_status="$(jq -r '.runtimeExecuteAfterBrowser.supervisionStatus // ""' "$BROWSER_SMOKE_SUMMARY")"
  browser_runtime_after_browser_active_session_status="$(jq -r '.runtimeExecuteAfterBrowser.activeSessionStatus // ""' "$BROWSER_SMOKE_SUMMARY")"
  browser_runtime_after_browser_active_session_observed="$(jq -r '.runtimeExecuteAfterBrowser.activeSessionObserved // false' "$BROWSER_SMOKE_SUMMARY")"
  browser_runtime_after_browser_active_session_recovery_reentry_ready="$(jq -r '.runtimeExecuteAfterBrowser.activeSessionRecoveryReentryReady // false' "$BROWSER_SMOKE_SUMMARY")"
  browser_runtime_after_browser_active_session_restart_continuity_ready="$(jq -r '.runtimeExecuteAfterBrowser.activeSessionRestartContinuityReady // false' "$BROWSER_SMOKE_SUMMARY")"
  browser_runtime_after_browser_active_session_validation_status="$(jq -r '.runtimeExecuteAfterBrowser.activeSessionValidationStatus // ""' "$BROWSER_SMOKE_SUMMARY")"
  browser_runtime_after_browser_active_session_validation_lease_renewal_observed="$(jq -r '.runtimeExecuteAfterBrowser.activeSessionValidationLeaseRenewalObserved // false' "$BROWSER_SMOKE_SUMMARY")"
  browser_runtime_after_browser_active_session_validation_recovery_reentry_observed="$(jq -r '.runtimeExecuteAfterBrowser.activeSessionValidationRecoveryReentryObserved // false' "$BROWSER_SMOKE_SUMMARY")"
  browser_runtime_after_browser_active_session_validation_restart_continuity_observed="$(jq -r '.runtimeExecuteAfterBrowser.activeSessionValidationRestartContinuityObserved // false' "$BROWSER_SMOKE_SUMMARY")"
  browser_runtime_after_browser_active_session_validation_device_proof_required="$(jq -r '.runtimeExecuteAfterBrowser.activeSessionValidationDeviceProofRequired // false' "$BROWSER_SMOKE_SUMMARY")"
  browser_runtime_after_browser_active_session_device_proof_status="$(jq -r '.runtimeExecuteAfterBrowser.activeSessionDeviceProofStatus // ""' "$BROWSER_SMOKE_SUMMARY")"
  browser_runtime_after_browser_active_session_device_proof_observed="$(jq -r '.runtimeExecuteAfterBrowser.activeSessionDeviceProofObserved // false' "$BROWSER_SMOKE_SUMMARY")"
  browser_runtime_after_browser_active_session_device_proof_live_proof_required="$(jq -r '.runtimeExecuteAfterBrowser.activeSessionDeviceProofLiveProofRequired // false' "$BROWSER_SMOKE_SUMMARY")"
  browser_runtime_after_browser_active_session_device_proof_expected_artifact_count="$(jq -r '.runtimeExecuteAfterBrowser.activeSessionDeviceProofExpectedArtifactCount // 0' "$BROWSER_SMOKE_SUMMARY")"
  browser_runtime_after_browser_active_session_device_proof_captured_artifact_count="$(jq -r '.runtimeExecuteAfterBrowser.activeSessionDeviceProofCapturedArtifactCount // 0' "$BROWSER_SMOKE_SUMMARY")"
fi

if [[ "$browser_smoke_ok" == "true" && "$browser_mainline_status" == "process_runtime_active_session_live_proof_captured" && "$BROWSER_START" != "0" ]]; then
  confirm_browser_smoke_required=true
  mkdir -p "$CONFIRM_BROWSER_SMOKE_DIR"
  if OPENCLAW_ANDROID_LOCAL_HOST_TOKEN="$token" \
    OPENCLAW_ANDROID_LOCAL_HOST_BROWSER_START=0 \
    OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR="$CONFIRM_BROWSER_SMOKE_DIR" \
    bash "$BROWSER_SMOKE_SCRIPT" >"$CONFIRM_BROWSER_SMOKE_STDOUT" 2>&1; then
    confirm_browser_smoke_executed=true
    confirm_browser_smoke_exit_code=0
  else
    confirm_browser_smoke_executed=true
    confirm_browser_smoke_exit_code=$?
  fi

  if [[ ! -f "$CONFIRM_BROWSER_SMOKE_SUMMARY" ]]; then
    echo "Missing confirm browser smoke summary: $CONFIRM_BROWSER_SMOKE_SUMMARY" >&2
    cat "$CONFIRM_BROWSER_SMOKE_STDOUT" >&2 || true
    exit 1
  fi

  confirm_browser_smoke_ok="$(jq -r '.ok // false' "$CONFIRM_BROWSER_SMOKE_SUMMARY")"
  confirm_browser_smoke_failed_checks="$(jq -r '[.failedChecks[]?] | join(",")' "$CONFIRM_BROWSER_SMOKE_SUMMARY")"
  confirm_browser_smoke_hint="$(jq -r '.hint // ""' "$CONFIRM_BROWSER_SMOKE_SUMMARY")"
  confirm_browser_mainline_status="$(jq -r '.runtimeDescribeAfter.mainlineStatus // ""' "$CONFIRM_BROWSER_SMOKE_SUMMARY")"
  confirm_browser_recommended_next_slice="$(jq -r '.runtimeDescribeAfter.recommendedNextSlice // ""' "$CONFIRM_BROWSER_SMOKE_SUMMARY")"
  confirm_browser_replay_ready="$(jq -r '.browserDescribeAfter.replayReady // false' "$CONFIRM_BROWSER_SMOKE_SUMMARY")"
  confirm_browser_auth_credential_present="$(jq -r '.browserDescribeAfter.authCredentialPresent // false' "$CONFIRM_BROWSER_SMOKE_SUMMARY")"
  confirm_browser_runtime_after_browser_long_lived_process_ready="$(jq -r '.runtimeExecuteAfterBrowser.longLivedProcessReady // false' "$CONFIRM_BROWSER_SMOKE_SUMMARY")"
  confirm_browser_runtime_after_browser_process_status="$(jq -r '.runtimeExecuteAfterBrowser.processStatus // ""' "$CONFIRM_BROWSER_SMOKE_SUMMARY")"
  confirm_browser_runtime_after_browser_supervision_status="$(jq -r '.runtimeExecuteAfterBrowser.supervisionStatus // ""' "$CONFIRM_BROWSER_SMOKE_SUMMARY")"
  confirm_browser_runtime_after_browser_active_session_status="$(jq -r '.runtimeExecuteAfterBrowser.activeSessionStatus // ""' "$CONFIRM_BROWSER_SMOKE_SUMMARY")"
  confirm_browser_runtime_after_browser_active_session_observed="$(jq -r '.runtimeExecuteAfterBrowser.activeSessionObserved // false' "$CONFIRM_BROWSER_SMOKE_SUMMARY")"
  confirm_browser_runtime_after_browser_active_session_recovery_reentry_ready="$(jq -r '.runtimeExecuteAfterBrowser.activeSessionRecoveryReentryReady // false' "$CONFIRM_BROWSER_SMOKE_SUMMARY")"
  confirm_browser_runtime_after_browser_active_session_restart_continuity_ready="$(jq -r '.runtimeExecuteAfterBrowser.activeSessionRestartContinuityReady // false' "$CONFIRM_BROWSER_SMOKE_SUMMARY")"
  confirm_browser_runtime_after_browser_active_session_validation_status="$(jq -r '.runtimeExecuteAfterBrowser.activeSessionValidationStatus // ""' "$CONFIRM_BROWSER_SMOKE_SUMMARY")"
  confirm_browser_runtime_after_browser_active_session_validation_lease_renewal_observed="$(jq -r '.runtimeExecuteAfterBrowser.activeSessionValidationLeaseRenewalObserved // false' "$CONFIRM_BROWSER_SMOKE_SUMMARY")"
  confirm_browser_runtime_after_browser_active_session_validation_recovery_reentry_observed="$(jq -r '.runtimeExecuteAfterBrowser.activeSessionValidationRecoveryReentryObserved // false' "$CONFIRM_BROWSER_SMOKE_SUMMARY")"
  confirm_browser_runtime_after_browser_active_session_validation_restart_continuity_observed="$(jq -r '.runtimeExecuteAfterBrowser.activeSessionValidationRestartContinuityObserved // false' "$CONFIRM_BROWSER_SMOKE_SUMMARY")"
  confirm_browser_runtime_after_browser_active_session_validation_device_proof_required="$(jq -r '.runtimeExecuteAfterBrowser.activeSessionValidationDeviceProofRequired // false' "$CONFIRM_BROWSER_SMOKE_SUMMARY")"
  confirm_browser_runtime_after_browser_active_session_device_proof_status="$(jq -r '.runtimeExecuteAfterBrowser.activeSessionDeviceProofStatus // ""' "$CONFIRM_BROWSER_SMOKE_SUMMARY")"
  confirm_browser_runtime_after_browser_active_session_device_proof_observed="$(jq -r '.runtimeExecuteAfterBrowser.activeSessionDeviceProofObserved // false' "$CONFIRM_BROWSER_SMOKE_SUMMARY")"
  confirm_browser_runtime_after_browser_active_session_device_proof_live_proof_required="$(jq -r '.runtimeExecuteAfterBrowser.activeSessionDeviceProofLiveProofRequired // false' "$CONFIRM_BROWSER_SMOKE_SUMMARY")"
  confirm_browser_runtime_after_browser_active_session_device_proof_expected_artifact_count="$(jq -r '.runtimeExecuteAfterBrowser.activeSessionDeviceProofExpectedArtifactCount // 0' "$CONFIRM_BROWSER_SMOKE_SUMMARY")"
  confirm_browser_runtime_after_browser_active_session_device_proof_captured_artifact_count="$(jq -r '.runtimeExecuteAfterBrowser.activeSessionDeviceProofCapturedArtifactCount // 0' "$CONFIRM_BROWSER_SMOKE_SUMMARY")"
  confirm_browser_live_proof_continuity_checked=true

  if [[ "$browser_replay_ready" != "$confirm_browser_replay_ready" ]]; then
    confirm_browser_live_proof_continuity_failed_checks="$(append_csv_value "$confirm_browser_live_proof_continuity_failed_checks" "browserReplayReady")"
  fi
  if [[ "$browser_auth_credential_present" != "$confirm_browser_auth_credential_present" ]]; then
    confirm_browser_live_proof_continuity_failed_checks="$(append_csv_value "$confirm_browser_live_proof_continuity_failed_checks" "browserAuthCredentialPresent")"
  fi
  if [[ "$browser_runtime_after_browser_long_lived_process_ready" != "$confirm_browser_runtime_after_browser_long_lived_process_ready" ]]; then
    confirm_browser_live_proof_continuity_failed_checks="$(append_csv_value "$confirm_browser_live_proof_continuity_failed_checks" "longLivedProcessReady")"
  fi
  if [[ "$browser_runtime_after_browser_process_status" != "$confirm_browser_runtime_after_browser_process_status" ]]; then
    confirm_browser_live_proof_continuity_failed_checks="$(append_csv_value "$confirm_browser_live_proof_continuity_failed_checks" "processStatus")"
  fi
  if [[ "$browser_runtime_after_browser_supervision_status" != "$confirm_browser_runtime_after_browser_supervision_status" ]]; then
    confirm_browser_live_proof_continuity_failed_checks="$(append_csv_value "$confirm_browser_live_proof_continuity_failed_checks" "supervisionStatus")"
  fi
  if [[ "$browser_runtime_after_browser_active_session_status" != "$confirm_browser_runtime_after_browser_active_session_status" ]]; then
    confirm_browser_live_proof_continuity_failed_checks="$(append_csv_value "$confirm_browser_live_proof_continuity_failed_checks" "activeSessionStatus")"
  fi
  if [[ "$browser_runtime_after_browser_active_session_observed" != "$confirm_browser_runtime_after_browser_active_session_observed" ]]; then
    confirm_browser_live_proof_continuity_failed_checks="$(append_csv_value "$confirm_browser_live_proof_continuity_failed_checks" "activeSessionObserved")"
  fi
  if [[ "$browser_runtime_after_browser_active_session_recovery_reentry_ready" != "$confirm_browser_runtime_after_browser_active_session_recovery_reentry_ready" ]]; then
    confirm_browser_live_proof_continuity_failed_checks="$(append_csv_value "$confirm_browser_live_proof_continuity_failed_checks" "activeSessionRecoveryReentryReady")"
  fi
  if [[ "$browser_runtime_after_browser_active_session_restart_continuity_ready" != "$confirm_browser_runtime_after_browser_active_session_restart_continuity_ready" ]]; then
    confirm_browser_live_proof_continuity_failed_checks="$(append_csv_value "$confirm_browser_live_proof_continuity_failed_checks" "activeSessionRestartContinuityReady")"
  fi
  if [[ "$browser_runtime_after_browser_active_session_validation_status" != "$confirm_browser_runtime_after_browser_active_session_validation_status" ]]; then
    confirm_browser_live_proof_continuity_failed_checks="$(append_csv_value "$confirm_browser_live_proof_continuity_failed_checks" "activeSessionValidationStatus")"
  fi
  if [[ "$browser_runtime_after_browser_active_session_validation_lease_renewal_observed" != "$confirm_browser_runtime_after_browser_active_session_validation_lease_renewal_observed" ]]; then
    confirm_browser_live_proof_continuity_failed_checks="$(append_csv_value "$confirm_browser_live_proof_continuity_failed_checks" "activeSessionValidationLeaseRenewalObserved")"
  fi
  if [[ "$browser_runtime_after_browser_active_session_validation_recovery_reentry_observed" != "$confirm_browser_runtime_after_browser_active_session_validation_recovery_reentry_observed" ]]; then
    confirm_browser_live_proof_continuity_failed_checks="$(append_csv_value "$confirm_browser_live_proof_continuity_failed_checks" "activeSessionValidationRecoveryReentryObserved")"
  fi
  if [[ "$browser_runtime_after_browser_active_session_validation_restart_continuity_observed" != "$confirm_browser_runtime_after_browser_active_session_validation_restart_continuity_observed" ]]; then
    confirm_browser_live_proof_continuity_failed_checks="$(append_csv_value "$confirm_browser_live_proof_continuity_failed_checks" "activeSessionValidationRestartContinuityObserved")"
  fi
  if [[ "$browser_runtime_after_browser_active_session_validation_device_proof_required" != "$confirm_browser_runtime_after_browser_active_session_validation_device_proof_required" ]]; then
    confirm_browser_live_proof_continuity_failed_checks="$(append_csv_value "$confirm_browser_live_proof_continuity_failed_checks" "activeSessionValidationDeviceProofRequired")"
  fi
  if [[ "$browser_runtime_after_browser_active_session_device_proof_status" != "$confirm_browser_runtime_after_browser_active_session_device_proof_status" ]]; then
    confirm_browser_live_proof_continuity_failed_checks="$(append_csv_value "$confirm_browser_live_proof_continuity_failed_checks" "activeSessionDeviceProofStatus")"
  fi
  if [[ "$browser_runtime_after_browser_active_session_device_proof_observed" != "$confirm_browser_runtime_after_browser_active_session_device_proof_observed" ]]; then
    confirm_browser_live_proof_continuity_failed_checks="$(append_csv_value "$confirm_browser_live_proof_continuity_failed_checks" "activeSessionDeviceProofObserved")"
  fi
  if [[ "$browser_runtime_after_browser_active_session_device_proof_live_proof_required" != "$confirm_browser_runtime_after_browser_active_session_device_proof_live_proof_required" ]]; then
    confirm_browser_live_proof_continuity_failed_checks="$(append_csv_value "$confirm_browser_live_proof_continuity_failed_checks" "activeSessionDeviceProofLiveProofRequired")"
  fi
  if [[ "$browser_runtime_after_browser_active_session_device_proof_expected_artifact_count" != "$confirm_browser_runtime_after_browser_active_session_device_proof_expected_artifact_count" ]]; then
    confirm_browser_live_proof_continuity_failed_checks="$(append_csv_value "$confirm_browser_live_proof_continuity_failed_checks" "activeSessionDeviceProofExpectedArtifactCount")"
  fi
  if [[ "$browser_runtime_after_browser_active_session_device_proof_captured_artifact_count" != "$confirm_browser_runtime_after_browser_active_session_device_proof_captured_artifact_count" ]]; then
    confirm_browser_live_proof_continuity_failed_checks="$(append_csv_value "$confirm_browser_live_proof_continuity_failed_checks" "activeSessionDeviceProofCapturedArtifactCount")"
  fi

  if [[ -z "$confirm_browser_live_proof_continuity_failed_checks" ]]; then
    confirm_browser_live_proof_continuity_preserved=true
  fi

  if [[ "$confirm_browser_mainline_status" == "process_runtime_active_session_live_proof_captured" && "$confirm_browser_recommended_next_slice" == "process_runtime_lane_hardening" && "$confirm_browser_live_proof_continuity_preserved" == "true" ]]; then
    confirm_browser_live_proof_replayed="true"
  fi
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
elif [[ "$confirm_browser_smoke_required" == "true" && ( "$confirm_browser_smoke_ok" != "true" || "$confirm_browser_live_proof_replayed" != "true" ) ]]; then
  classification="process_runtime_lane_hardening_pending"
  recommended_action="confirm-live-proof-replay"
  recommended_command="OPENCLAW_ANDROID_LOCAL_HOST_BROWSER_START=0 pnpm android:local-host:embedded-runtime-pod:doctor -- --json"
elif [[ "$browser_mainline_status" == "process_runtime_active_session_live_proof_captured" ]]; then
  classification="process_runtime_active_session_live_proof_captured"
  recommended_action="preserve-live-proof-baseline"
  recommended_command=""
elif [[ "$browser_mainline_status" == "process_runtime_active_session_device_proof_bootstrapped" ]]; then
  classification="process_runtime_active_session_device_proof_bootstrapped"
  recommended_action="capture-live-active-session-proof"
  recommended_command=""
elif [[ "$browser_mainline_status" == "process_runtime_active_session_validation_bootstrapped" ]]; then
  classification="process_runtime_active_session_validation_bootstrapped"
  recommended_action="advance-active-session-device-proof"
  recommended_command=""
elif [[ "$browser_mainline_status" == "process_runtime_active_session_bootstrapped" ]]; then
  classification="process_runtime_active_session_bootstrapped"
  recommended_action="advance-active-session-validation"
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
  elif [[ "$browser_recommended_next_slice" == "process_runtime_active_session_validation" ]]; then
    recommended_action="advance-active-session-validation"
    recommended_command=""
  elif [[ "$browser_recommended_next_slice" == "process_runtime_active_session_device_proof" ]]; then
    recommended_action="advance-active-session-device-proof"
    recommended_command=""
  elif [[ "$browser_recommended_next_slice" == "process_runtime_active_session_live_proof" ]]; then
    recommended_action="capture-live-active-session-proof"
    recommended_command=""
  elif [[ "$browser_recommended_next_slice" == "process_runtime_lane_hardening" ]]; then
    recommended_action="preserve-live-proof-baseline"
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
  --arg confirmBrowserSmokeStdoutPath "$CONFIRM_BROWSER_SMOKE_STDOUT"
  --arg confirmBrowserSmokeSummaryPath "$CONFIRM_BROWSER_SMOKE_SUMMARY"
  --arg podSmokeFailedChecks "$pod_smoke_failed_checks"
  --arg browserSmokeFailedChecks "$browser_smoke_failed_checks"
  --arg confirmBrowserSmokeFailedChecks "$confirm_browser_smoke_failed_checks"
  --arg browserMainlineStatus "$browser_mainline_status"
  --arg browserRecommendedNextSlice "$browser_recommended_next_slice"
  --arg confirmBrowserMainlineStatus "$confirm_browser_mainline_status"
  --arg confirmBrowserRecommendedNextSlice "$confirm_browser_recommended_next_slice"
  --arg confirmBrowserLiveProofContinuityFailedChecks "$confirm_browser_live_proof_continuity_failed_checks"
  --arg browserRuntimeAfterBrowserProcessStatus "$browser_runtime_after_browser_process_status"
  --arg browserRuntimeAfterBrowserSupervisionStatus "$browser_runtime_after_browser_supervision_status"
  --arg browserRuntimeAfterBrowserActiveSessionStatus "$browser_runtime_after_browser_active_session_status"
  --arg browserRuntimeAfterBrowserActiveSessionValidationStatus "$browser_runtime_after_browser_active_session_validation_status"
  --arg browserRuntimeAfterBrowserActiveSessionValidationLeaseRenewalObserved "$browser_runtime_after_browser_active_session_validation_lease_renewal_observed"
  --arg browserRuntimeAfterBrowserActiveSessionValidationRecoveryReentryObserved "$browser_runtime_after_browser_active_session_validation_recovery_reentry_observed"
  --arg browserRuntimeAfterBrowserActiveSessionValidationRestartContinuityObserved "$browser_runtime_after_browser_active_session_validation_restart_continuity_observed"
  --arg browserRuntimeAfterBrowserActiveSessionValidationDeviceProofRequired "$browser_runtime_after_browser_active_session_validation_device_proof_required"
  --arg browserRuntimeAfterBrowserActiveSessionDeviceProofStatus "$browser_runtime_after_browser_active_session_device_proof_status"
  --arg browserRuntimeAfterBrowserActiveSessionDeviceProofLiveProofRequired "$browser_runtime_after_browser_active_session_device_proof_live_proof_required"
  --arg browserRuntimeAfterBrowserActiveSessionDeviceProofExpectedArtifactCount "$browser_runtime_after_browser_active_session_device_proof_expected_artifact_count"
  --arg browserRuntimeAfterBrowserActiveSessionDeviceProofCapturedArtifactCount "$browser_runtime_after_browser_active_session_device_proof_captured_artifact_count"
  --arg confirmBrowserRuntimeAfterBrowserProcessStatus "$confirm_browser_runtime_after_browser_process_status"
  --arg confirmBrowserRuntimeAfterBrowserSupervisionStatus "$confirm_browser_runtime_after_browser_supervision_status"
  --arg confirmBrowserRuntimeAfterBrowserActiveSessionStatus "$confirm_browser_runtime_after_browser_active_session_status"
  --arg confirmBrowserRuntimeAfterBrowserActiveSessionValidationStatus "$confirm_browser_runtime_after_browser_active_session_validation_status"
  --arg confirmBrowserRuntimeAfterBrowserActiveSessionValidationLeaseRenewalObserved "$confirm_browser_runtime_after_browser_active_session_validation_lease_renewal_observed"
  --arg confirmBrowserRuntimeAfterBrowserActiveSessionValidationRecoveryReentryObserved "$confirm_browser_runtime_after_browser_active_session_validation_recovery_reentry_observed"
  --arg confirmBrowserRuntimeAfterBrowserActiveSessionValidationRestartContinuityObserved "$confirm_browser_runtime_after_browser_active_session_validation_restart_continuity_observed"
  --arg confirmBrowserRuntimeAfterBrowserActiveSessionValidationDeviceProofRequired "$confirm_browser_runtime_after_browser_active_session_validation_device_proof_required"
  --arg confirmBrowserRuntimeAfterBrowserActiveSessionDeviceProofStatus "$confirm_browser_runtime_after_browser_active_session_device_proof_status"
  --arg confirmBrowserRuntimeAfterBrowserActiveSessionDeviceProofLiveProofRequired "$confirm_browser_runtime_after_browser_active_session_device_proof_live_proof_required"
  --arg confirmBrowserRuntimeAfterBrowserActiveSessionDeviceProofExpectedArtifactCount "$confirm_browser_runtime_after_browser_active_session_device_proof_expected_artifact_count"
  --arg confirmBrowserRuntimeAfterBrowserActiveSessionDeviceProofCapturedArtifactCount "$confirm_browser_runtime_after_browser_active_session_device_proof_captured_artifact_count"
  --argjson podSmokeExitCode "$pod_smoke_exit_code"
  --argjson podSmokeOk "$(bool_json "$pod_smoke_ok")"
  --argjson browserSmokeExecuted "$(bool_json "$browser_smoke_executed")"
  --argjson browserSmokeExitCode "$browser_smoke_exit_code"
  --argjson browserSmokeOk "$(bool_json "$browser_smoke_ok")"
  --argjson browserReplayReady "$(bool_json "$browser_replay_ready")"
  --argjson browserAuthCredentialPresent "$(bool_json "$browser_auth_credential_present")"
  --argjson browserRuntimeAfterBrowserLongLivedProcessReady "$(bool_json "$browser_runtime_after_browser_long_lived_process_ready")"
  --argjson browserRuntimeAfterBrowserActiveSessionObserved "$(bool_json "$browser_runtime_after_browser_active_session_observed")"
  --argjson browserRuntimeAfterBrowserActiveSessionRecoveryReentryReady "$(bool_json "$browser_runtime_after_browser_active_session_recovery_reentry_ready")"
  --argjson browserRuntimeAfterBrowserActiveSessionRestartContinuityReady "$(bool_json "$browser_runtime_after_browser_active_session_restart_continuity_ready")"
  --argjson browserRuntimeAfterBrowserActiveSessionDeviceProofObserved "$(bool_json "$browser_runtime_after_browser_active_session_device_proof_observed")"
  --argjson confirmBrowserSmokeRequired "$(bool_json "$confirm_browser_smoke_required")"
  --argjson confirmBrowserSmokeExecuted "$(bool_json "$confirm_browser_smoke_executed")"
  --argjson confirmBrowserSmokeExitCode "$confirm_browser_smoke_exit_code"
  --argjson confirmBrowserSmokeOk "$(bool_json "$confirm_browser_smoke_ok")"
  --argjson confirmBrowserReplayReady "$(bool_json "$confirm_browser_replay_ready")"
  --argjson confirmBrowserAuthCredentialPresent "$(bool_json "$confirm_browser_auth_credential_present")"
  --argjson confirmBrowserLiveProofReplayed "$(bool_json "$confirm_browser_live_proof_replayed")"
  --argjson confirmBrowserRuntimeAfterBrowserLongLivedProcessReady "$(bool_json "$confirm_browser_runtime_after_browser_long_lived_process_ready")"
  --argjson confirmBrowserRuntimeAfterBrowserActiveSessionObserved "$(bool_json "$confirm_browser_runtime_after_browser_active_session_observed")"
  --argjson confirmBrowserRuntimeAfterBrowserActiveSessionRecoveryReentryReady "$(bool_json "$confirm_browser_runtime_after_browser_active_session_recovery_reentry_ready")"
  --argjson confirmBrowserRuntimeAfterBrowserActiveSessionRestartContinuityReady "$(bool_json "$confirm_browser_runtime_after_browser_active_session_restart_continuity_ready")"
  --argjson confirmBrowserRuntimeAfterBrowserActiveSessionDeviceProofObserved "$(bool_json "$confirm_browser_runtime_after_browser_active_session_device_proof_observed")"
  --argjson confirmBrowserLiveProofContinuityChecked "$(bool_json "$confirm_browser_live_proof_continuity_checked")"
  --argjson confirmBrowserLiveProofContinuityPreserved "$(bool_json "$confirm_browser_live_proof_continuity_preserved")"
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

if [[ "$confirm_browser_smoke_executed" == "true" ]]; then
  jq_args+=(--slurpfile confirmBrowser "$CONFIRM_BROWSER_SMOKE_SUMMARY")
else
  jq_args+=(--argjson confirmBrowser null)
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
    confirmBrowserLaneSmoke: {
      required: $confirmBrowserSmokeRequired,
      executed: $confirmBrowserSmokeExecuted,
      exitCode: (if $confirmBrowserSmokeExecuted then $confirmBrowserSmokeExitCode else null end),
      ok: (if $confirmBrowserSmokeExecuted then $confirmBrowserSmokeOk else null end),
      failedChecks: (
        if ($confirmBrowserSmokeExecuted | not) or $confirmBrowserSmokeFailedChecks == "" then
          []
        else
          ($confirmBrowserSmokeFailedChecks | split(","))
        end
      ),
      mainlineStatus: (if $confirmBrowserMainlineStatus == "" then null else $confirmBrowserMainlineStatus end),
      recommendedNextSlice: (if $confirmBrowserRecommendedNextSlice == "" then null else $confirmBrowserRecommendedNextSlice end),
      liveProofReplayed: $confirmBrowserLiveProofReplayed,
      liveProofContinuity: {
        checked: $confirmBrowserLiveProofContinuityChecked,
        preserved: (
          if $confirmBrowserLiveProofContinuityChecked then
            $confirmBrowserLiveProofContinuityPreserved
          else
            null
          end
        ),
        failedChecks: (
          if ($confirmBrowserLiveProofContinuityChecked | not) or $confirmBrowserLiveProofContinuityFailedChecks == "" then
            []
          else
            ($confirmBrowserLiveProofContinuityFailedChecks | split(","))
          end
        ),
        initial: (
          if ($confirmBrowserLiveProofContinuityChecked | not) then
            null
          else
            {
              browserReplayReady: $browserReplayReady,
              browserAuthCredentialPresent: $browserAuthCredentialPresent,
              longLivedProcessReady: $browserRuntimeAfterBrowserLongLivedProcessReady,
              processStatus: (if $browserRuntimeAfterBrowserProcessStatus == "" then null else $browserRuntimeAfterBrowserProcessStatus end),
              supervisionStatus: (if $browserRuntimeAfterBrowserSupervisionStatus == "" then null else $browserRuntimeAfterBrowserSupervisionStatus end),
              activeSessionStatus: (if $browserRuntimeAfterBrowserActiveSessionStatus == "" then null else $browserRuntimeAfterBrowserActiveSessionStatus end),
              activeSessionObserved: $browserRuntimeAfterBrowserActiveSessionObserved,
              activeSessionRecoveryReentryReady: $browserRuntimeAfterBrowserActiveSessionRecoveryReentryReady,
              activeSessionRestartContinuityReady: $browserRuntimeAfterBrowserActiveSessionRestartContinuityReady,
              activeSessionValidationStatus: (if $browserRuntimeAfterBrowserActiveSessionValidationStatus == "" then null else $browserRuntimeAfterBrowserActiveSessionValidationStatus end),
              activeSessionValidationLeaseRenewalObserved: ($browserRuntimeAfterBrowserActiveSessionValidationLeaseRenewalObserved == "true"),
              activeSessionValidationRecoveryReentryObserved: ($browserRuntimeAfterBrowserActiveSessionValidationRecoveryReentryObserved == "true"),
              activeSessionValidationRestartContinuityObserved: ($browserRuntimeAfterBrowserActiveSessionValidationRestartContinuityObserved == "true"),
              activeSessionValidationDeviceProofRequired: ($browserRuntimeAfterBrowserActiveSessionValidationDeviceProofRequired == "true"),
              activeSessionDeviceProofStatus: (if $browserRuntimeAfterBrowserActiveSessionDeviceProofStatus == "" then null else $browserRuntimeAfterBrowserActiveSessionDeviceProofStatus end),
              activeSessionDeviceProofObserved: $browserRuntimeAfterBrowserActiveSessionDeviceProofObserved,
              activeSessionDeviceProofLiveProofRequired: ($browserRuntimeAfterBrowserActiveSessionDeviceProofLiveProofRequired == "true"),
              activeSessionDeviceProofExpectedArtifactCount: ($browserRuntimeAfterBrowserActiveSessionDeviceProofExpectedArtifactCount | tonumber),
              activeSessionDeviceProofCapturedArtifactCount: ($browserRuntimeAfterBrowserActiveSessionDeviceProofCapturedArtifactCount | tonumber)
            }
          end
        ),
        confirm: (
          if ($confirmBrowserLiveProofContinuityChecked | not) then
            null
          else
            {
              browserReplayReady: $confirmBrowserReplayReady,
              browserAuthCredentialPresent: $confirmBrowserAuthCredentialPresent,
              longLivedProcessReady: $confirmBrowserRuntimeAfterBrowserLongLivedProcessReady,
              processStatus: (if $confirmBrowserRuntimeAfterBrowserProcessStatus == "" then null else $confirmBrowserRuntimeAfterBrowserProcessStatus end),
              supervisionStatus: (if $confirmBrowserRuntimeAfterBrowserSupervisionStatus == "" then null else $confirmBrowserRuntimeAfterBrowserSupervisionStatus end),
              activeSessionStatus: (if $confirmBrowserRuntimeAfterBrowserActiveSessionStatus == "" then null else $confirmBrowserRuntimeAfterBrowserActiveSessionStatus end),
              activeSessionObserved: $confirmBrowserRuntimeAfterBrowserActiveSessionObserved,
              activeSessionRecoveryReentryReady: $confirmBrowserRuntimeAfterBrowserActiveSessionRecoveryReentryReady,
              activeSessionRestartContinuityReady: $confirmBrowserRuntimeAfterBrowserActiveSessionRestartContinuityReady,
              activeSessionValidationStatus: (if $confirmBrowserRuntimeAfterBrowserActiveSessionValidationStatus == "" then null else $confirmBrowserRuntimeAfterBrowserActiveSessionValidationStatus end),
              activeSessionValidationLeaseRenewalObserved: ($confirmBrowserRuntimeAfterBrowserActiveSessionValidationLeaseRenewalObserved == "true"),
              activeSessionValidationRecoveryReentryObserved: ($confirmBrowserRuntimeAfterBrowserActiveSessionValidationRecoveryReentryObserved == "true"),
              activeSessionValidationRestartContinuityObserved: ($confirmBrowserRuntimeAfterBrowserActiveSessionValidationRestartContinuityObserved == "true"),
              activeSessionValidationDeviceProofRequired: ($confirmBrowserRuntimeAfterBrowserActiveSessionValidationDeviceProofRequired == "true"),
              activeSessionDeviceProofStatus: (if $confirmBrowserRuntimeAfterBrowserActiveSessionDeviceProofStatus == "" then null else $confirmBrowserRuntimeAfterBrowserActiveSessionDeviceProofStatus end),
              activeSessionDeviceProofObserved: $confirmBrowserRuntimeAfterBrowserActiveSessionDeviceProofObserved,
              activeSessionDeviceProofLiveProofRequired: ($confirmBrowserRuntimeAfterBrowserActiveSessionDeviceProofLiveProofRequired == "true"),
              activeSessionDeviceProofExpectedArtifactCount: ($confirmBrowserRuntimeAfterBrowserActiveSessionDeviceProofExpectedArtifactCount | tonumber),
              activeSessionDeviceProofCapturedArtifactCount: ($confirmBrowserRuntimeAfterBrowserActiveSessionDeviceProofCapturedArtifactCount | tonumber)
            }
          end
        )
      },
      replayReady: (if $confirmBrowserSmokeExecuted then $confirmBrowserReplayReady else null end),
      authCredentialPresent: (if $confirmBrowserSmokeExecuted then $confirmBrowserAuthCredentialPresent else null end),
      stdoutPath: (if $confirmBrowserSmokeExecuted then $confirmBrowserSmokeStdoutPath else null end),
      summaryPath: (if $confirmBrowserSmokeExecuted then $confirmBrowserSmokeSummaryPath else null end),
      summary: (
        if $confirmBrowser == null then
          null
        else
          $confirmBrowser[0]
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
printf 'runtime_doctor.confirm_browser_smoke.required=%s executed=%s ok=%s live_proof_replayed=%s exit_code=%s failed_checks=%s\n' "$confirm_browser_smoke_required" "$confirm_browser_smoke_executed" "$confirm_browser_smoke_ok" "$confirm_browser_live_proof_replayed" "$confirm_browser_smoke_exit_code" "${confirm_browser_smoke_failed_checks:-none}"
printf 'runtime_doctor.confirm_browser_smoke.continuity_checked=%s preserved=%s failed_checks=%s\n' "$confirm_browser_live_proof_continuity_checked" "$confirm_browser_live_proof_continuity_preserved" "${confirm_browser_live_proof_continuity_failed_checks:-none}"
printf 'runtime_doctor.classification=%s\n' "$classification"
if [[ -n "$recommended_action" ]]; then
  printf 'runtime_doctor.recommended_action=%s\n' "$recommended_action"
fi
if [[ -n "$recommended_command" ]]; then
  printf 'runtime_doctor.recommended_command=%s\n' "$recommended_command"
fi
printf 'artifacts.summary=%s\n' "$SUMMARY_JSON"
