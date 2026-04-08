#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCTOR_SCRIPT="${OPENCLAW_ANDROID_LOCAL_HOST_EMBEDDED_RUNTIME_POD_DOCTOR_SCRIPT:-$SCRIPT_DIR/local-host-embedded-runtime-pod-doctor.sh}"
ARTIFACT_DIR="${OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR:-$(mktemp -d -t openclaw-android-runtime-stability.XXXXXX)}"
ITERATIONS="${OPENCLAW_ANDROID_LOCAL_HOST_STABILITY_ITERATIONS:-3}"
DELAY_SEC="${OPENCLAW_ANDROID_LOCAL_HOST_STABILITY_DELAY_SEC:-0}"
JSON=false

usage() {
  cat <<'EOF'
Usage:
  ./apps/android/scripts/local-host-embedded-runtime-pod-stability.sh
  ./apps/android/scripts/local-host-embedded-runtime-pod-stability.sh --json
  ./apps/android/scripts/local-host-embedded-runtime-pod-stability.sh --iterations 5 --delay-sec 2

What it does:
  1. Runs the embedded-runtime pod doctor multiple times
  2. Stores each doctor run in its own artifact directory
  3. Checks that every run still converges to the captured live-proof state
  4. Writes one combined summary that reports whether the replay stayed stable

Notes:
  - This wrapper exits non-zero when any iteration regresses
  - OPENCLAW_ANDROID_LOCAL_HOST_TOKEN and browser-related env overrides still pass through to doctor
  - Each iteration writes its own summary.json under artifacts/iterations/<nn>/
EOF
}

require_cmd() {
  local name=$1
  if ! command -v "$name" >/dev/null 2>&1; then
    echo "$name required but missing." >&2
    exit 1
  fi
}

is_positive_integer() {
  [[ "${1:-}" =~ ^[1-9][0-9]*$ ]]
}

is_nonnegative_number() {
  [[ "${1:-}" =~ ^[0-9]+([.][0-9]+)?$ ]]
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
    --iterations)
      if [[ $# -lt 2 ]]; then
        echo "--iterations requires a value." >&2
        exit 1
      fi
      ITERATIONS=$2
      shift 2
      ;;
    --delay-sec)
      if [[ $# -lt 2 ]]; then
        echo "--delay-sec requires a value." >&2
        exit 1
      fi
      DELAY_SEC=$2
      shift 2
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

require_cmd bash
require_cmd jq

if ! is_positive_integer "$ITERATIONS"; then
  echo "Iterations must be a positive integer." >&2
  exit 1
fi

if ! is_nonnegative_number "$DELAY_SEC"; then
  echo "Delay seconds must be a non-negative number." >&2
  exit 1
fi

mkdir -p "$ARTIFACT_DIR/iterations"

meta_files=()

for ((i = 1; i <= ITERATIONS; i += 1)); do
  iteration_id="$(printf '%02d' "$i")"
  iteration_dir="$ARTIFACT_DIR/iterations/$iteration_id"
  stdout_path="$iteration_dir/doctor.stdout.txt"
  summary_path="$iteration_dir/summary.json"
  iteration_meta_path="$iteration_dir/iteration.json"

  mkdir -p "$iteration_dir"

  doctor_exit_code=0
  if OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR="$iteration_dir" \
    bash "$DOCTOR_SCRIPT" --json >"$stdout_path"; then
    doctor_exit_code=0
  else
    doctor_exit_code=$?
  fi

  if [[ -f "$summary_path" ]]; then
    jq -n \
      --argjson iteration "$i" \
      --arg iterationId "$iteration_id" \
      --arg stdoutPath "$stdout_path" \
      --arg summaryPath "$summary_path" \
      --argjson exitCode "$doctor_exit_code" \
      --slurpfile doctor "$summary_path" '
      def add_reason($condition; $label):
        if $condition then [$label] else [] end;
      $doctor[0] as $d
      | {
          iteration: $iteration,
          iterationId: $iterationId,
          exitCode: $exitCode,
          stdoutPath: $stdoutPath,
          summaryPath: $summaryPath,
          classification: ($d.classification // null),
          recommendedAction: ($d.recommendedAction // null),
          recommendedCommand: ($d.recommendedCommand // null),
          manifestVersion: ($d.podSmoke.summary.status.manifestVersion // null),
          verifiedFileCount: ($d.podSmoke.summary.status.verifiedFileCount // null),
          browserLaneSmoke: {
            ok: ($d.browserLaneSmoke.ok // null),
            mainlineStatus: ($d.browserLaneSmoke.mainlineStatus // null),
            recommendedNextSlice: ($d.browserLaneSmoke.recommendedNextSlice // null),
            activeSessionObserved: ($d.browserLaneSmoke.summary.runtimeExecuteAfterBrowser.activeSessionObserved // null),
            activeSessionValidationStatus: ($d.browserLaneSmoke.summary.runtimeExecuteAfterBrowser.activeSessionValidationStatus // null),
            activeSessionValidationLeaseRenewalObserved: ($d.browserLaneSmoke.summary.runtimeExecuteAfterBrowser.activeSessionValidationLeaseRenewalObserved // null),
            activeSessionValidationRecoveryReentryObserved: ($d.browserLaneSmoke.summary.runtimeExecuteAfterBrowser.activeSessionValidationRecoveryReentryObserved // null),
            activeSessionValidationRestartContinuityObserved: ($d.browserLaneSmoke.summary.runtimeExecuteAfterBrowser.activeSessionValidationRestartContinuityObserved // null),
            activeSessionDeviceProofStatus: ($d.browserLaneSmoke.summary.runtimeExecuteAfterBrowser.activeSessionDeviceProofStatus // null),
            activeSessionDeviceProofExpectedArtifactCount: ($d.browserLaneSmoke.summary.runtimeExecuteAfterBrowser.activeSessionDeviceProofExpectedArtifactCount // null),
            activeSessionDeviceProofCapturedArtifactCount: ($d.browserLaneSmoke.summary.runtimeExecuteAfterBrowser.activeSessionDeviceProofCapturedArtifactCount // null)
          },
          confirmBrowserLaneSmoke: {
            required: ($d.confirmBrowserLaneSmoke.required // null),
            executed: ($d.confirmBrowserLaneSmoke.executed // null),
            ok: ($d.confirmBrowserLaneSmoke.ok // null),
            mainlineStatus: ($d.confirmBrowserLaneSmoke.mainlineStatus // null),
            recommendedNextSlice: ($d.confirmBrowserLaneSmoke.recommendedNextSlice // null),
            liveProofReplayed: ($d.confirmBrowserLaneSmoke.liveProofReplayed // null),
            liveProofContinuity: {
              checked: ($d.confirmBrowserLaneSmoke.liveProofContinuity.checked // null),
              preserved: ($d.confirmBrowserLaneSmoke.liveProofContinuity.preserved // null),
              failedChecks: ($d.confirmBrowserLaneSmoke.liveProofContinuity.failedChecks // [])
            }
          }
        } as $iterationSummary
      | $iterationSummary + {
          failureReasons: (
            add_reason($exitCode != 0; "doctor_exit_nonzero")
            + add_reason(($iterationSummary.classification // "") != "process_runtime_active_session_live_proof_captured"; "classification_unexpected")
            + add_reason(($iterationSummary.recommendedAction // "") != "preserve-live-proof-baseline"; "recommended_action_unexpected")
            + add_reason(($iterationSummary.browserLaneSmoke.ok // false) != true; "browser_lane_unhealthy")
            + add_reason(($iterationSummary.browserLaneSmoke.mainlineStatus // "") != "process_runtime_active_session_live_proof_captured"; "browser_mainline_status_unexpected")
            + add_reason(($iterationSummary.browserLaneSmoke.recommendedNextSlice // "") != "process_runtime_lane_hardening"; "browser_next_slice_unexpected")
            + add_reason(($iterationSummary.browserLaneSmoke.activeSessionObserved // false) != true; "active_session_not_observed")
            + add_reason(($iterationSummary.browserLaneSmoke.activeSessionValidationStatus // "") != "validated"; "validation_status_unexpected")
            + add_reason(($iterationSummary.browserLaneSmoke.activeSessionValidationLeaseRenewalObserved // false) != true; "validation_lease_renewal_not_observed")
            + add_reason(($iterationSummary.browserLaneSmoke.activeSessionValidationRecoveryReentryObserved // false) != true; "validation_recovery_reentry_not_observed")
            + add_reason(($iterationSummary.browserLaneSmoke.activeSessionValidationRestartContinuityObserved // false) != true; "validation_restart_continuity_not_observed")
            + add_reason(($iterationSummary.browserLaneSmoke.activeSessionDeviceProofStatus // "") != "verified"; "device_proof_status_unexpected")
            + add_reason((($iterationSummary.browserLaneSmoke.activeSessionDeviceProofExpectedArtifactCount // 0) >= 1) | not; "device_proof_expected_artifact_count_invalid")
            + add_reason((($iterationSummary.browserLaneSmoke.activeSessionDeviceProofCapturedArtifactCount // -1) >= ($iterationSummary.browserLaneSmoke.activeSessionDeviceProofExpectedArtifactCount // 0)) | not; "device_proof_captured_artifact_count_invalid")
            + add_reason(($iterationSummary.confirmBrowserLaneSmoke.required // false) != true; "confirm_browser_not_required")
            + add_reason(($iterationSummary.confirmBrowserLaneSmoke.executed // false) != true; "confirm_browser_not_executed")
            + add_reason(($iterationSummary.confirmBrowserLaneSmoke.ok // false) != true; "confirm_browser_unhealthy")
            + add_reason(($iterationSummary.confirmBrowserLaneSmoke.mainlineStatus // "") != "process_runtime_active_session_live_proof_captured"; "confirm_mainline_status_unexpected")
            + add_reason(($iterationSummary.confirmBrowserLaneSmoke.recommendedNextSlice // "") != "process_runtime_lane_hardening"; "confirm_next_slice_unexpected")
            + add_reason(($iterationSummary.confirmBrowserLaneSmoke.liveProofReplayed // false) != true; "live_proof_not_replayed")
            + add_reason(($iterationSummary.confirmBrowserLaneSmoke.liveProofContinuity.checked // false) != true; "live_proof_continuity_not_checked")
            + add_reason(($iterationSummary.confirmBrowserLaneSmoke.liveProofContinuity.preserved // false) != true; "live_proof_continuity_not_preserved")
          )
        }
      | .ok = (.failureReasons | length == 0)
      ' >"$iteration_meta_path"
  else
    jq -n \
      --argjson iteration "$i" \
      --arg iterationId "$iteration_id" \
      --arg stdoutPath "$stdout_path" \
      --arg summaryPath "$summary_path" \
      --argjson exitCode "$doctor_exit_code" '
      {
        iteration: $iteration,
        iterationId: $iterationId,
        exitCode: $exitCode,
        stdoutPath: $stdoutPath,
        summaryPath: null,
        classification: null,
        recommendedAction: null,
        recommendedCommand: null,
        manifestVersion: null,
        verifiedFileCount: null,
        browserLaneSmoke: null,
        confirmBrowserLaneSmoke: null,
        failureReasons: (
          if $exitCode == 0 then
            ["missing_doctor_summary"]
          else
            ["doctor_exit_nonzero", "missing_doctor_summary"]
          end
        ),
        ok: false
      }
      ' >"$iteration_meta_path"
  fi

  meta_files+=("$iteration_meta_path")

  if [[ "$i" -lt "$ITERATIONS" && "$DELAY_SEC" != "0" ]]; then
    sleep "$DELAY_SEC"
  fi
done

SUMMARY_JSON="$ARTIFACT_DIR/summary.json"
jq -s \
  --arg artifactDir "$ARTIFACT_DIR" \
  --arg packageCommand "pnpm android:local-host:embedded-runtime-pod:stability" \
  --arg doctorPackageCommand "pnpm android:local-host:embedded-runtime-pod:doctor" \
  --arg doctorScript "$DOCTOR_SCRIPT" \
  --argjson iterationsRequested "$ITERATIONS" \
  --argjson delaySec "$DELAY_SEC" '
  . as $iterations
  | {
      ok: all($iterations[]; .ok == true),
      packageCommand: $packageCommand,
      doctorPackageCommand: $doctorPackageCommand,
      doctorScriptPath: $doctorScript,
      iterationsRequested: $iterationsRequested,
      delaySec: $delaySec,
      passedIterationCount: ($iterations | map(select(.ok == true)) | length),
      failedIterationCount: ($iterations | map(select(.ok != true)) | length),
      failedIterations: (
        $iterations
        | map(select(.ok != true) | {
            iteration,
            iterationId,
            classification,
            failureReasons,
            summaryPath
          })
      ),
      classifications: ($iterations | map(select(.classification != null) | .classification) | unique),
      manifestVersions: ($iterations | map(select(.manifestVersion != null) | .manifestVersion) | unique),
      verifiedFileCounts: ($iterations | map(select(.verifiedFileCount != null) | .verifiedFileCount) | unique),
      recommendedNextSlices: (
        [
          $iterations[] | .browserLaneSmoke?.recommendedNextSlice,
          $iterations[] | .confirmBrowserLaneSmoke?.recommendedNextSlice
        ]
        | map(select(. != null))
        | unique
      ),
      liveProofReplayedCount: (
        $iterations
        | map(select(.confirmBrowserLaneSmoke?.liveProofReplayed == true))
        | length
      ),
      continuityPreservedCount: (
        $iterations
        | map(select(.confirmBrowserLaneSmoke?.liveProofContinuity?.preserved == true))
        | length
      ),
      stableExpectedArtifactCount: (
        $iterations
        | map(select(.browserLaneSmoke?.activeSessionDeviceProofExpectedArtifactCount != null) | .browserLaneSmoke.activeSessionDeviceProofExpectedArtifactCount)
        | unique
        | if length == 1 then .[0] else null end
      ),
      stableCapturedArtifactCount: (
        $iterations
        | map(select(.browserLaneSmoke?.activeSessionDeviceProofCapturedArtifactCount != null) | .browserLaneSmoke.activeSessionDeviceProofCapturedArtifactCount)
        | unique
        | if length == 1 then .[0] else null end
      ),
      recommendedAction: (
        if all($iterations[]; .ok == true) then
          "preserve-live-proof-baseline"
        else
          "inspect-stability-iterations"
        end
      ),
      recommendedCommand: (
        if all($iterations[]; .ok == true) then
          null
        else
          ("cat \"" + $artifactDir + "/summary.json\"")
        end
      ),
      iterations: $iterations,
      artifacts: {
        rootDir: $artifactDir,
        iterationDir: ($artifactDir + "/iterations")
      }
    }
  ' "${meta_files[@]}" >"$SUMMARY_JSON"

overall_ok="$(jq -r '.ok' "$SUMMARY_JSON")"

if [[ "$JSON" == "true" ]]; then
  cat "$SUMMARY_JSON"
  if [[ "$overall_ok" == "true" ]]; then
    exit 0
  fi
  exit 1
fi

printf 'runtime_pod_stability.ok=%s iterations=%s passed=%s failed=%s\n' \
  "$overall_ok" \
  "$(jq -r '.iterationsRequested' "$SUMMARY_JSON")" \
  "$(jq -r '.passedIterationCount' "$SUMMARY_JSON")" \
  "$(jq -r '.failedIterationCount' "$SUMMARY_JSON")"

jq -r '
  .iterations[]
  | "runtime_pod_stability.iteration=\(.iterationId) ok=\(.ok) classification=\(.classification // "null") continuity=\(.confirmBrowserLaneSmoke.liveProofContinuity.preserved // "null") artifacts=\(.browserLaneSmoke.activeSessionDeviceProofCapturedArtifactCount // "null")/\(.browserLaneSmoke.activeSessionDeviceProofExpectedArtifactCount // "null") failed_reasons=\((.failureReasons | if length == 0 then "none" else join(",") end))"
' "$SUMMARY_JSON"

printf 'runtime_pod_stability.classifications=%s\n' \
  "$(jq -r '.classifications | if length == 0 then "none" else join(",") end' "$SUMMARY_JSON")"
printf 'runtime_pod_stability.next_slices=%s\n' \
  "$(jq -r '.recommendedNextSlices | if length == 0 then "none" else join(",") end' "$SUMMARY_JSON")"
printf 'runtime_pod_stability.summary=%s\n' "$SUMMARY_JSON"

if [[ "$overall_ok" == "true" ]]; then
  exit 0
fi

echo "runtime_pod_stability=failed" >&2
exit 1
