#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
POST_PROVISION_SCRIPT="$SCRIPT_DIR/local-host-dedicated-post-provision-check.sh"
ARTIFACT_DIR="${OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR:-$(mktemp -d -t openclaw-android-testdpc-kiosk.XXXXXX)}"
TESTDPC_PACKAGE="${OPENCLAW_ANDROID_TESTDPC_PACKAGE:-com.afwsamples.testdpc}"
TESTDPC_POLICY_ACTIVITY="${OPENCLAW_ANDROID_TESTDPC_POLICY_ACTIVITY:-$TESTDPC_PACKAGE/.PolicyManagementActivity}"
TESTDPC_KIOSK_ACTIVITY="${OPENCLAW_ANDROID_TESTDPC_KIOSK_ACTIVITY:-$TESTDPC_PACKAGE/.policy.locktask.KioskModeActivity}"
TESTDPC_KIOSK_EXTRA_KEY="${OPENCLAW_ANDROID_TESTDPC_KIOSK_EXTRA_KEY:-com.afwsamples.testdpc.policy.locktask.LOCKED_APP_PACKAGE_LIST}"
LOCKED_PACKAGES_CSV="${OPENCLAW_ANDROID_LOCK_TASK_PACKAGES:-ai.openclaw.app}"
SUMMARY_JSON="$ARTIFACT_DIR/summary.json"
OPEN_POLICY=false
APPLY=false

usage() {
  cat <<'EOF'
Usage:
  ./apps/android/scripts/local-host-dedicated-testdpc-kiosk.sh
  ./apps/android/scripts/local-host-dedicated-testdpc-kiosk.sh --open-policy
  ./apps/android/scripts/local-host-dedicated-testdpc-kiosk.sh --apply

What it does:
  1. Checks whether TestDPC is installed and acting as Device Owner
  2. Reuses the dedicated post-provision checker to inspect OpenClaw state
  3. In dry-run mode, prints the exact adb commands needed for TestDPC kiosk mode
  4. In apply mode, enables TestDPC's KioskModeActivity and launches it with a package allowlist

Important side effects in apply mode:
  - TestDPC's KioskModeActivity becomes the persistent HOME activity
  - TestDPC also adds itself as a kiosk backdoor package
  - This is only meant for a spare phone that is already provisioned

Environment overrides:
  OPENCLAW_ANDROID_TESTDPC_PACKAGE=com.afwsamples.testdpc
  OPENCLAW_ANDROID_LOCK_TASK_PACKAGES=ai.openclaw.app,com.android.settings
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h | --help)
      usage
      exit 0
      ;;
    --open-policy)
      OPEN_POLICY=true
      shift
      ;;
    --apply)
      APPLY=true
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

trim_cr() {
  tr -d '\r'
}

bool_json() {
  if [[ "${1:-false}" == "true" ]]; then
    printf 'true'
  else
    printf 'false'
  fi
}

require_cmd adb
require_cmd jq

device_count="$(adb devices | awk 'NR>1 && $2=="device" {c+=1} END {print c+0}')"
if [[ "$device_count" -lt 1 ]]; then
  echo "No connected Android device (adb state=device)." >&2
  exit 1
fi

mkdir -p "$ARTIFACT_DIR"

normalized_packages_json="$(
  printf '%s' "$LOCKED_PACKAGES_CSV" |
    jq -R 'split(",") | map(gsub("^[[:space:]]+|[[:space:]]+$"; "")) | map(select(length > 0))'
)"
if [[ "$(jq 'length' <<<"$normalized_packages_json")" -lt 1 ]]; then
  echo "OPENCLAW_ANDROID_LOCK_TASK_PACKAGES must contain at least one package." >&2
  exit 1
fi
normalized_packages_csv="$(jq -r 'join(",")' <<<"$normalized_packages_json")"

owners_output="$(adb shell dpm list-owners 2>&1 | trim_cr || true)"
testdpc_installed=false
if adb shell pm path "$TESTDPC_PACKAGE" >/dev/null 2>&1; then
  testdpc_installed=true
fi

testdpc_is_device_owner=false
if [[ "$owners_output" == *"Device Owner"* && "$owners_output" == *"$TESTDPC_PACKAGE"* ]]; then
  testdpc_is_device_owner=true
fi

precheck_dir="$ARTIFACT_DIR/post-provision-before"
mkdir -p "$precheck_dir"
OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR="$precheck_dir" \
  bash "$POST_PROVISION_SCRIPT" >/dev/null
precheck_summary="$precheck_dir/summary.json"

openclaw_installed="$(jq -r '.app.packageInstalled // false' "$precheck_summary")"
openclaw_dedicated_enabled="$(jq -r '.app.dedicatedEnabled // null' "$precheck_summary")"
openclaw_onboarding_completed="$(jq -r '.app.onboardingCompleted // null' "$precheck_summary")"
openclaw_gateway_mode="$(jq -r '.app.gatewayConnectionMode // null' "$precheck_summary")"
precheck_ready="$(jq -r '.viability.postProvisionReady // false' "$precheck_summary")"

blockers_json='[]'
if [[ "$testdpc_installed" != "true" ]]; then
  blockers_json="$(jq -cn --argjson prev "$blockers_json" --arg pkg "$TESTDPC_PACKAGE" '$prev + [("TestDPC is not installed: " + $pkg)]')"
fi
if [[ "$testdpc_is_device_owner" != "true" ]]; then
  blockers_json="$(jq -cn --argjson prev "$blockers_json" --arg pkg "$TESTDPC_PACKAGE" '$prev + [("TestDPC is not the active Device Owner: " + $pkg)]')"
fi
if [[ "$openclaw_installed" != "true" ]]; then
  blockers_json="$(jq -cn --argjson prev "$blockers_json" '$prev + ["OpenClaw is not installed on the connected phone"]')"
fi

ready_for_apply=true
if [[ "$(jq 'length' <<<"$blockers_json")" -gt 0 ]]; then
  ready_for_apply=false
fi

enable_command="adb shell pm enable '$TESTDPC_KIOSK_ACTIVITY'"
launch_command="adb shell am start -n '$TESTDPC_KIOSK_ACTIVITY' --esa '$TESTDPC_KIOSK_EXTRA_KEY' '$normalized_packages_csv'"
open_policy_command="adb shell am start -n '$TESTDPC_POLICY_ACTIVITY'"

policy_open_attempted=false
policy_open_succeeded=false
policy_open_output=""
if [[ "$OPEN_POLICY" == "true" ]]; then
  policy_open_attempted=true
  set +e
  policy_open_output="$(adb shell am start -n "$TESTDPC_POLICY_ACTIVITY" 2>&1 | trim_cr)"
  policy_open_exit_code=$?
  set -e
  if [[ "$policy_open_exit_code" -eq 0 ]]; then
    policy_open_succeeded=true
  fi
fi

apply_attempted=false
apply_succeeded=false
enable_output=""
launch_output=""
postcheck_summary=""
if [[ "$APPLY" == "true" ]]; then
  apply_attempted=true
  if [[ "$ready_for_apply" != "true" ]]; then
    :
  else
    set +e
    enable_output="$(adb shell pm enable "$TESTDPC_KIOSK_ACTIVITY" 2>&1 | trim_cr)"
    enable_exit_code=$?
    if [[ "$enable_exit_code" -eq 0 ]]; then
      launch_output="$(adb shell am start -n "$TESTDPC_KIOSK_ACTIVITY" --esa "$TESTDPC_KIOSK_EXTRA_KEY" "$normalized_packages_csv" 2>&1 | trim_cr)"
      launch_exit_code=$?
    else
      launch_exit_code=1
    fi
    set -e

    if [[ "${enable_exit_code:-1}" -eq 0 && "${launch_exit_code:-1}" -eq 0 ]]; then
      apply_succeeded=true
      sleep 2
      postcheck_dir="$ARTIFACT_DIR/post-provision-after"
      mkdir -p "$postcheck_dir"
      OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR="$postcheck_dir" \
        bash "$POST_PROVISION_SCRIPT" --launch >/dev/null
      postcheck_summary="$postcheck_dir/summary.json"
    fi
  fi
fi

recommendations_json='[]'
if [[ "$ready_for_apply" != "true" ]]; then
  recommendations_json="$(jq -cn --argjson prev "$recommendations_json" --argjson blockers "$blockers_json" '$prev + $blockers')"
fi
if [[ "$testdpc_installed" == "true" && "$testdpc_is_device_owner" == "true" ]]; then
  recommendations_json="$(jq -cn --argjson prev "$recommendations_json" '$prev + ["If you want a manual path instead of the helper, open TestDPC and use Lock task -> Manage lock task list"]')"
fi
recommendations_json="$(jq -cn --argjson prev "$recommendations_json" '$prev + ["Remember that TestDPC kiosk mode makes TestDPC the persistent HOME activity and keeps TestDPC itself as the kiosk backdoor package"]')"

jq -n \
  --arg testdpcPackage "$TESTDPC_PACKAGE" \
  --arg policyActivity "$TESTDPC_POLICY_ACTIVITY" \
  --arg kioskActivity "$TESTDPC_KIOSK_ACTIVITY" \
  --arg kioskExtraKey "$TESTDPC_KIOSK_EXTRA_KEY" \
  --arg ownersOutput "$owners_output" \
  --arg enableCommand "$enable_command" \
  --arg launchCommand "$launch_command" \
  --arg openPolicyCommand "$open_policy_command" \
  --arg openClawDedicatedEnabled "$openclaw_dedicated_enabled" \
  --arg openClawOnboardingCompleted "$openclaw_onboarding_completed" \
  --arg openClawGatewayMode "$openclaw_gateway_mode" \
  --arg policyOpenOutput "${policy_open_output:-}" \
  --arg enableOutput "${enable_output:-}" \
  --arg launchOutput "${launch_output:-}" \
  --arg precheckSummary "$precheck_summary" \
  --arg postcheckSummary "${postcheck_summary:-}" \
  --argjson testdpcInstalled "$(bool_json "$testdpc_installed")" \
  --argjson testdpcIsDeviceOwner "$(bool_json "$testdpc_is_device_owner")" \
  --argjson openclawInstalled "$(bool_json "$openclaw_installed")" \
  --argjson precheckReady "$(bool_json "$precheck_ready")" \
  --argjson readyForApply "$(bool_json "$ready_for_apply")" \
  --argjson policyOpenAttempted "$(bool_json "$policy_open_attempted")" \
  --argjson policyOpenSucceeded "$(bool_json "$policy_open_succeeded")" \
  --argjson applyAttempted "$(bool_json "$apply_attempted")" \
  --argjson applySucceeded "$(bool_json "$apply_succeeded")" \
  --argjson lockedPackages "$normalized_packages_json" \
  --argjson blockers "$blockers_json" \
  --argjson recommendations "$recommendations_json" \
  '{
    testdpc: {
      packageName: $testdpcPackage,
      installed: $testdpcInstalled,
      isDeviceOwner: $testdpcIsDeviceOwner,
      ownersOutput: $ownersOutput,
      policyActivity: $policyActivity,
      kioskActivity: $kioskActivity,
      kioskExtraKey: $kioskExtraKey
    },
    openclaw: {
      installed: $openclawInstalled,
      dedicatedEnabled: (if $openClawDedicatedEnabled == "null" then null else $openClawDedicatedEnabled == "true" end),
      onboardingCompleted: (if $openClawOnboardingCompleted == "null" then null else $openClawOnboardingCompleted == "true" end),
      gatewayConnectionMode: (if $openClawGatewayMode == "null" then null else $openClawGatewayMode end)
    },
    kiosk: {
      lockedPackages: $lockedPackages,
      readyForApply: $readyForApply,
      precheckReady: $precheckReady,
      sideEffects: [
        "TestDPC KioskModeActivity becomes the persistent HOME activity",
        "TestDPC adds itself to the kiosk backdoor package list"
      ],
      openPolicyCommand: $openPolicyCommand,
      enableCommand: $enableCommand,
      launchCommand: $launchCommand
    },
    runs: {
      policyOpenAttempted: $policyOpenAttempted,
      policyOpenSucceeded: $policyOpenSucceeded,
      policyOpenOutput: (if $policyOpenOutput == "" then null else $policyOpenOutput end),
      applyAttempted: $applyAttempted,
      applySucceeded: $applySucceeded,
      enableOutput: (if $enableOutput == "" then null else $enableOutput end),
      launchOutput: (if $launchOutput == "" then null else $launchOutput end)
    },
    precheckSummary: $precheckSummary,
    postcheckSummary: (if $postcheckSummary == "" then null else $postcheckSummary end),
    blockers: $blockers,
    recommendations: $recommendations
  }' >"$SUMMARY_JSON"

printf 'testdpc.kiosk.installed=%s device_owner=%s openclaw_installed=%s ready_for_apply=%s\n' \
  "$testdpc_installed" "$testdpc_is_device_owner" "$openclaw_installed" "$ready_for_apply"
printf 'testdpc.kiosk.packages=%s\n' "$normalized_packages_csv"
printf 'testdpc.kiosk.precheck_ready=%s dedicated=%s onboarding=%s gateway=%s\n' \
  "$precheck_ready" "$openclaw_dedicated_enabled" "$openclaw_onboarding_completed" "$openclaw_gateway_mode"

if [[ "$ready_for_apply" == "true" ]]; then
  echo "testdpc.kiosk.next.enable=$enable_command"
  echo "testdpc.kiosk.next.launch=$launch_command"
else
  jq -r '.[]' <<<"$blockers_json" | sed 's/^/testdpc.kiosk.blocker=/' || true
fi

if [[ "$OPEN_POLICY" == "true" ]]; then
  printf 'testdpc.kiosk.policy_open=%s\n' "$policy_open_succeeded"
fi

if [[ "$APPLY" == "true" ]]; then
  printf 'testdpc.kiosk.apply_succeeded=%s\n' "$apply_succeeded"
fi

echo "artifacts.summary=$SUMMARY_JSON"
