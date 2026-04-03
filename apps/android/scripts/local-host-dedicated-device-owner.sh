#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
READINESS_SCRIPT="$SCRIPT_DIR/local-host-dedicated-readiness.sh"
DPC_COMPONENT="${OPENCLAW_ANDROID_DPC_COMPONENT:-com.afwsamples.testdpc/.DeviceAdminReceiver}"
ADB_BIN="${OPENCLAW_ANDROID_LOCAL_HOST_ADB_BIN:-adb}"
ARTIFACT_DIR="${OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR:-$(mktemp -d -t openclaw-android-device-owner.XXXXXX)}"
SUMMARY_JSON="$ARTIFACT_DIR/summary.json"
READINESS_DIR="$ARTIFACT_DIR/readiness"
APPLY=false

usage() {
  cat <<'EOF'
Usage:
  [OPENCLAW_ANDROID_LOCAL_HOST_ADB_BIN=/path/to/adb] \
  [OPENCLAW_ANDROID_DPC_COMPONENT=com.afwsamples.testdpc/.DeviceAdminReceiver] \
  ./apps/android/scripts/local-host-dedicated-device-owner.sh

  [OPENCLAW_ANDROID_DPC_COMPONENT=...] \
  ./apps/android/scripts/local-host-dedicated-device-owner.sh --apply

What it does:
  1. Reuses the dedicated-readiness probe
  2. In dry-run mode, prints whether adb device-owner provisioning is ready
  3. In apply mode, runs adb shell dpm set-device-owner only if the phone is ready

Notes:
  - Dry-run is the default
  - --apply is stateful and should only be used on a spare phone
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ "${1:-}" == "--apply" ]]; then
  APPLY=true
elif [[ -n "${1:-}" ]]; then
  echo "Unknown argument: $1" >&2
  usage >&2
  exit 1
fi

require_cmd() {
  local name=$1
  if [[ "$name" == */* || "$name" == *:* ]]; then
    if [[ -x "$name" || -f "$name" ]]; then
      return 0
    fi
  elif command -v "$name" >/dev/null 2>&1; then
    return 0
  fi
  echo "$name required but missing." >&2
  exit 1
}

require_cmd "$ADB_BIN"
require_cmd jq
ADB_BIN_DISPLAY="$(printf '%q' "$ADB_BIN")"

mkdir -p "$ARTIFACT_DIR" "$READINESS_DIR"

OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR="$READINESS_DIR" \
OPENCLAW_ANDROID_DPC_COMPONENT="$DPC_COMPONENT" \
  bash "$READINESS_SCRIPT" >/dev/null

READINESS_SUMMARY="$READINESS_DIR/summary.json"
if [[ ! -f "$READINESS_SUMMARY" ]]; then
  echo "Missing readiness summary: $READINESS_SUMMARY" >&2
  exit 1
fi

device_owner_ready="$(jq -r '.viability.deviceOwnerViaAdbReady // false' "$READINESS_SUMMARY")"
preferred_path="$(jq -r '.viability.preferredPath // ""' "$READINESS_SUMMARY")"
blockers_json="$(jq -c '.viability.deviceOwnerBlockers // []' "$READINESS_SUMMARY")"
blockers_count="$(jq -r '.viability.deviceOwnerBlockers | length' "$READINESS_SUMMARY")"
already_has_owner="$(jq -r '.state.hasDeviceOwner // false' "$READINESS_SUMMARY")"
owners_output="$(jq -r '.raw.owners // ""' "$READINESS_SUMMARY")"
provision_command="$ADB_BIN_DISPLAY shell dpm set-device-owner '$DPC_COMPONENT'"
apply_attempted=false
apply_succeeded=false
apply_exit_code=0
apply_stdout=""

if [[ "$APPLY" == "true" ]]; then
  apply_attempted=true
  if [[ "$device_owner_ready" != "true" ]]; then
    apply_exit_code=2
    apply_stdout="device-owner provisioning is not ready; see blockers"
  elif [[ "$already_has_owner" == "true" ]]; then
    apply_exit_code=3
    apply_stdout="device already has an owner: $owners_output"
  else
    set +e
    apply_stdout="$("$ADB_BIN" shell dpm set-device-owner "$DPC_COMPONENT" 2>&1)"
    apply_exit_code=$?
    set -e
    if [[ "$apply_exit_code" -eq 0 ]]; then
      apply_succeeded=true
    fi
  fi
fi

jq -n \
  --arg dpcComponent "$DPC_COMPONENT" \
  --arg provisionCommand "$provision_command" \
  --arg preferredPath "$preferred_path" \
  --argjson deviceOwnerReady "$( [[ "$device_owner_ready" == "true" ]] && printf 'true' || printf 'false' )" \
  --argjson blockers "$blockers_json" \
  --argjson applyRequested "$( [[ "$APPLY" == "true" ]] && printf 'true' || printf 'false' )" \
  --argjson applyAttempted "$( [[ "$apply_attempted" == "true" ]] && printf 'true' || printf 'false' )" \
  --argjson applySucceeded "$( [[ "$apply_succeeded" == "true" ]] && printf 'true' || printf 'false' )" \
  --argjson applyExitCode "$apply_exit_code" \
  --arg applyOutput "$apply_stdout" \
  --slurpfile readiness "$READINESS_SUMMARY" \
  '{
    dpcComponent: $dpcComponent,
    provisionCommand: $provisionCommand,
    readiness: $readiness[0],
    deviceOwnerReady: $deviceOwnerReady,
    preferredPath: $preferredPath,
    blockers: $blockers,
    applyRequested: $applyRequested,
    applyAttempted: $applyAttempted,
    applySucceeded: $applySucceeded,
    applyExitCode: $applyExitCode,
    applyOutput: (if $applyOutput == "" then null else $applyOutput end)
  }' >"$SUMMARY_JSON"

printf 'device_owner.ready=%s preferred_path=%s blockers=%s\n' \
  "$device_owner_ready" "$preferred_path" "$blockers_count"

if [[ "$device_owner_ready" == "true" ]]; then
  echo "device_owner.next=$provision_command"
else
  jq -r '.viability.deviceOwnerBlockers[]' "$READINESS_SUMMARY" | sed 's/^/device_owner.blocker=/' || true
fi

if [[ "$APPLY" == "true" ]]; then
  printf 'device_owner.apply_succeeded=%s apply_exit_code=%s\n' "$apply_succeeded" "$apply_exit_code"
  if [[ -n "$apply_stdout" ]]; then
    printf 'device_owner.apply_output=%s\n' "$apply_stdout"
  fi
fi

echo "artifacts.summary=$SUMMARY_JSON"
