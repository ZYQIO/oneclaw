#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
ARTIFACT_DIR="${OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR:-$(mktemp -d -t openclaw-android-dedicated-post-provision.XXXXXX)}"
PACKAGE_NAME="${OPENCLAW_ANDROID_PACKAGE:-ai.openclaw.app}"
MAIN_ACTIVITY_COMPONENT="${OPENCLAW_ANDROID_MAIN_ACTIVITY:-$PACKAGE_NAME/.MainActivity}"
APK_PATH_OVERRIDE="${OPENCLAW_ANDROID_APK_PATH:-}"
SUMMARY_JSON="$ARTIFACT_DIR/summary.json"
LAUNCH=false

usage() {
  cat <<'EOF'
Usage:
  ./apps/android/scripts/local-host-dedicated-post-provision-check.sh
  ./apps/android/scripts/local-host-dedicated-post-provision-check.sh --launch

What it does:
  1. Reads adb state for device-owner and lock-task status
  2. Inspects the installed OpenClaw package and launcher activity
  3. Reads plain shared_prefs via run-as when available
  4. Summarizes whether post-provision dedicated-device setup is complete

Optional behavior:
  --launch   Launch OpenClaw before the final lock-task snapshot

Environment overrides:
  OPENCLAW_ANDROID_PACKAGE=ai.openclaw.app
  OPENCLAW_ANDROID_MAIN_ACTIVITY=ai.openclaw.app/.MainActivity
  OPENCLAW_ANDROID_APK_PATH=/path/to/openclaw.apk
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h | --help)
      usage
      exit 0
      ;;
    --launch)
      LAUNCH=true
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

require_cmd adb
require_cmd jq

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

shell_prop() {
  local key=$1
  adb shell getprop "$key" | trim_cr
}

xml_boolean_value() {
  local xml=$1
  local key=$2
  printf '%s\n' "$xml" | sed -n "s/.*<boolean name=\"$key\" value=\"\\([^\"]*\\)\" \\/>.*/\\1/p" | head -n 1
}

xml_string_value() {
  local xml=$1
  local key=$2
  printf '%s\n' "$xml" | sed -n "s/.*<string name=\"$key\">\\(.*\\)<\\/string>.*/\\1/p" | head -n 1
}

xml_int_value() {
  local xml=$1
  local key=$2
  printf '%s\n' "$xml" | sed -n "s/.*<int name=\"$key\" value=\"\\([^\"]*\\)\" \\/>.*/\\1/p" | head -n 1
}

resolve_local_apk_path() {
  if [[ -n "$APK_PATH_OVERRIDE" ]]; then
    printf '%s\n' "$APK_PATH_OVERRIDE"
    return
  fi
  find "$REPO_ROOT/apps/android/app/build/outputs/apk" -name 'openclaw-*.apk' 2>/dev/null | tail -n 1
}

manifest_activity_name() {
  local component=$1
  local short_name="${component#*/}"
  if [[ "$short_name" == .* ]]; then
    printf '%s%s\n' "$PACKAGE_NAME" "$short_name"
  else
    printf '%s\n' "$short_name"
  fi
}

lock_task_mode_label() {
  case "${1:-}" in
    3 | if_whitelisted) printf 'if_whitelisted' ;;
    normal) printf 'normal' ;;
    never) printf 'never' ;;
    always) printf 'always' ;;
    *) printf '' ;;
  esac
}

parse_lock_task_packages_json() {
  local activity_dump=$1
  local raw_block
  raw_block="$(
    printf '%s\n' "$activity_dump" | awk '
      /mLockTaskPackages \(userId:packages\)=/ {
        capture=1
        sub(/^.*=/, "", $0)
        print
        next
      }
      capture {
        if ($0 ~ /^[[:space:]]*$/) exit
        if ($0 ~ /^[[:space:]]*OplusLockTaskController:/) exit
        print
      }
    '
  )"

  printf '%s\n' "$raw_block" |
    tr '[],' '   ' |
    awk '
      {
        for (i = 1; i <= NF; i += 1) {
          token = $i
          if (token ~ /^[0-9]+:[A-Za-z0-9._]+$/) {
            sub(/^[0-9]+:/, "", token)
          }
          if (token ~ /^[A-Za-z0-9._]+$/ && token != "mLockTaskPackages") {
            print token
          }
        }
      }
    ' |
    sort -u |
    jq -Rsc 'split("\n") | map(select(length > 0))'
}

mkdir -p "$ARTIFACT_DIR"

manufacturer="$(shell_prop ro.product.manufacturer)"
brand="$(shell_prop ro.product.brand)"
model="$(shell_prop ro.product.model)"
android_release="$(shell_prop ro.build.version.release)"
sdk_int="$(shell_prop ro.build.version.sdk)"

owners_output="$(adb shell dpm list-owners 2>&1 | trim_cr || true)"
device_policy_dump="$(adb shell dumpsys device_policy | trim_cr)"
activity_dump="$(adb shell dumpsys activity activities | trim_cr)"
package_dump="$(adb shell dumpsys package "$PACKAGE_NAME" | trim_cr || true)"
resolve_output="$(adb shell cmd package resolve-activity --brief "$PACKAGE_NAME" 2>&1 | trim_cr || true)"

package_installed=false
if adb shell pm path "$PACKAGE_NAME" >/dev/null 2>&1; then
  package_installed=true
fi

resolved_activity="$(printf '%s\n' "$resolve_output" | awk 'NF {line=$0} END {print line}')"
launcher_activity_matches_expected=false
if [[ "$resolved_activity" == "$MAIN_ACTIVITY_COMPONENT" ]]; then
  launcher_activity_matches_expected=true
fi

device_owner_type="$(printf '%s\n' "$device_policy_dump" | sed -n 's/.*Device Owner Type: \(.*\)$/\1/p' | head -n 1)"
has_any_owner=false
if [[ "$owners_output" != "no owners" && -n "$owners_output" ]]; then
  has_any_owner=true
fi
has_device_owner=false
if [[ -n "$device_owner_type" && "$device_owner_type" != "-1" ]]; then
  has_device_owner=true
fi
if [[ "$owners_output" == *"Device Owner"* ]]; then
  has_device_owner=true
fi

lock_task_mode_state="$(printf '%s\n' "$activity_dump" | sed -n 's/.*mLockTaskModeState=\(.*\)$/\1/p' | head -n 1)"
lock_task_packages_json="$(parse_lock_task_packages_json "$activity_dump")"
lock_task_package_allowlisted="$(jq -n --argjson packages "$lock_task_packages_json" --arg pkg "$PACKAGE_NAME" '$packages | index($pkg) != null')"

run_as_available=false
plain_prefs_xml=""
if adb shell run-as "$PACKAGE_NAME" ls shared_prefs >/dev/null 2>&1; then
  run_as_available=true
  plain_prefs_xml="$(adb shell run-as "$PACKAGE_NAME" cat shared_prefs/openclaw.node.xml 2>/dev/null | trim_cr || true)"
fi

dedicated_enabled=""
onboarding_completed=""
gateway_connection_mode=""
remote_access_enabled=""
remote_access_port=""
display_name=""
if [[ "$run_as_available" == "true" && -n "$plain_prefs_xml" ]]; then
  dedicated_enabled="$(xml_boolean_value "$plain_prefs_xml" "localHost.dedicatedDeployment.enabled")"
  onboarding_completed="$(xml_boolean_value "$plain_prefs_xml" "onboarding.completed")"
  gateway_connection_mode="$(xml_string_value "$plain_prefs_xml" "gateway.connection.mode")"
  remote_access_enabled="$(xml_boolean_value "$plain_prefs_xml" "localHost.remoteAccess.enabled")"
  remote_access_port="$(xml_int_value "$plain_prefs_xml" "localHost.remoteAccess.port")"
  display_name="$(xml_string_value "$plain_prefs_xml" "node.displayName")"
fi

local_apk_path="$(resolve_local_apk_path)"
manifest_lock_task_mode_raw=""
manifest_lock_task_mode_label=""
if [[ -n "$local_apk_path" && -f "$local_apk_path" ]] && command -v apkanalyzer >/dev/null 2>&1; then
  full_manifest_activity_name="$(manifest_activity_name "$MAIN_ACTIVITY_COMPONENT")"
  manifest_block="$(
    apkanalyzer manifest print "$local_apk_path" | awk -v activity="$full_manifest_activity_name" '
      $0 ~ ("android:name=\"" activity "\"") { capture=1 }
      capture { print }
      capture && /<\/activity>/ { exit }
    '
  )"
  manifest_lock_task_mode_raw="$(printf '%s\n' "$manifest_block" | sed -n 's/.*android:lockTaskMode=\"\([^\"]*\)\".*/\1/p' | head -n 1)"
  manifest_lock_task_mode_label="$(lock_task_mode_label "$manifest_lock_task_mode_raw")"
fi

launch_attempted=false
launch_succeeded=false
launch_output=""
top_activity_after_launch=""
lock_task_mode_state_after_launch="$lock_task_mode_state"
if [[ "$LAUNCH" == "true" ]]; then
  launch_attempted=true
  set +e
  launch_output="$(adb shell am start -W -n "$MAIN_ACTIVITY_COMPONENT" 2>&1 | trim_cr)"
  launch_exit_code=$?
  set -e
  if [[ "$launch_exit_code" -eq 0 ]]; then
    launch_succeeded=true
    sleep 2
    activity_dump_after_launch="$(adb shell dumpsys activity activities | trim_cr)"
    top_activity_after_launch="$(printf '%s\n' "$activity_dump_after_launch" | grep -m1 -E 'topResumedActivity|mResumedActivity' || true)"
    lock_task_mode_state_after_launch="$(printf '%s\n' "$activity_dump_after_launch" | sed -n 's/.*mLockTaskModeState=\(.*\)$/\1/p' | head -n 1)"
  fi
fi

dedicated_enabled_true=false
onboarding_completed_true=false
local_host_mode=false
if [[ "$dedicated_enabled" == "true" ]]; then
  dedicated_enabled_true=true
fi
if [[ "$onboarding_completed" == "true" ]]; then
  onboarding_completed_true=true
fi
if [[ "$gateway_connection_mode" == "localHost" ]]; then
  local_host_mode=true
fi

manifest_supports_auto_lock_task=false
if [[ "$manifest_lock_task_mode_label" == "if_whitelisted" ]]; then
  manifest_supports_auto_lock_task=true
fi

lock_task_allowlisted=false
if [[ "$lock_task_package_allowlisted" == "true" ]]; then
  lock_task_allowlisted=true
fi

post_provision_ready=false
if [[ "$has_device_owner" == "true" && "$package_installed" == "true" && "$launcher_activity_matches_expected" == "true" && "$lock_task_allowlisted" == "true" && "$dedicated_enabled_true" == "true" && "$onboarding_completed_true" == "true" && "$local_host_mode" == "true" && "$manifest_supports_auto_lock_task" == "true" ]]; then
  post_provision_ready=true
fi

recommendations_json='[]'
if [[ "$has_device_owner" != "true" ]]; then
  recommendations_json="$(jq -cn --argjson prev "$recommendations_json" '$prev + ["finish dedicated-device provisioning so the phone has a real Device Owner before expecting kiosk behavior"]')"
fi
if [[ "$lock_task_allowlisted" != "true" ]]; then
  recommendations_json="$(jq -cn --argjson prev "$recommendations_json" --arg pkg "$PACKAGE_NAME" '$prev + [("allowlist " + $pkg + " in the DPC lock-task policy via DevicePolicyManager.setLockTaskPackages()")]')"
fi
if [[ "$dedicated_enabled_true" != "true" ]]; then
  recommendations_json="$(jq -cn --argjson prev "$recommendations_json" '$prev + ["enable OpenClaw dedicated deployment inside the app"]')"
fi
if [[ "$onboarding_completed_true" != "true" ]]; then
  recommendations_json="$(jq -cn --argjson prev "$recommendations_json" '$prev + ["finish onboarding inside OpenClaw before expecting dedicated auto-entry"]')"
fi
if [[ "$local_host_mode" != "true" ]]; then
  recommendations_json="$(jq -cn --argjson prev "$recommendations_json" '$prev + ["switch the app to local-host gateway mode before relying on dedicated auto-entry"]')"
fi
if [[ "$manifest_supports_auto_lock_task" != "true" ]]; then
  recommendations_json="$(jq -cn --argjson prev "$recommendations_json" '$prev + ["install an OpenClaw build whose MainActivity uses android:lockTaskMode=\"if_whitelisted\""]')"
fi
if [[ "$run_as_available" != "true" ]]; then
  recommendations_json="$(jq -cn --argjson prev "$recommendations_json" '$prev + ["run-as is unavailable on this build; verify dedicated deployment, onboarding, and gateway mode from the app UI or remote /status"]')"
fi
if [[ "$post_provision_ready" == "true" && "$lock_task_mode_state_after_launch" == "NONE" ]]; then
  recommendations_json="$(jq -cn --argjson prev "$recommendations_json" '$prev + ["relaunch OpenClaw after the DPC allowlist is applied so the activity can enter lock-task mode"]')"
fi

jq -n \
  --arg manufacturer "$manufacturer" \
  --arg brand "$brand" \
  --arg model "$model" \
  --arg androidRelease "$android_release" \
  --arg sdkInt "$sdk_int" \
  --arg ownersOutput "$owners_output" \
  --arg deviceOwnerType "${device_owner_type:-}" \
  --arg resolvedActivity "$resolved_activity" \
  --arg expectedActivity "$MAIN_ACTIVITY_COMPONENT" \
  --arg packageName "$PACKAGE_NAME" \
  --arg packageDump "$package_dump" \
  --arg gatewayConnectionMode "${gateway_connection_mode:-}" \
  --arg dedicatedEnabledState "${dedicated_enabled:-}" \
  --arg onboardingCompletedState "${onboarding_completed:-}" \
  --arg remoteAccessEnabledState "${remote_access_enabled:-}" \
  --arg remoteAccessPort "${remote_access_port:-}" \
  --arg displayName "${display_name:-}" \
  --arg manifestLockTaskModeRaw "${manifest_lock_task_mode_raw:-}" \
  --arg manifestLockTaskModeLabel "${manifest_lock_task_mode_label:-}" \
  --arg localApkPath "${local_apk_path:-}" \
  --arg lockTaskModeState "${lock_task_mode_state:-}" \
  --arg lockTaskModeStateAfterLaunch "${lock_task_mode_state_after_launch:-}" \
  --arg topActivityAfterLaunch "${top_activity_after_launch:-}" \
  --arg launchOutput "${launch_output:-}" \
  --argjson hasAnyOwner "$(bool_json "$has_any_owner")" \
  --argjson hasDeviceOwner "$(bool_json "$has_device_owner")" \
  --argjson packageInstalled "$(bool_json "$package_installed")" \
  --argjson launcherActivityMatchesExpected "$(bool_json "$launcher_activity_matches_expected")" \
  --argjson runAsAvailable "$(bool_json "$run_as_available")" \
  --argjson localHostMode "$(bool_json "$local_host_mode")" \
  --argjson lockTaskPackageAllowlisted "$(bool_json "$lock_task_allowlisted")" \
  --argjson manifestSupportsAutoLockTask "$(bool_json "$manifest_supports_auto_lock_task")" \
  --argjson postProvisionReady "$(bool_json "$post_provision_ready")" \
  --argjson launchAttempted "$(bool_json "$launch_attempted")" \
  --argjson launchSucceeded "$(bool_json "$launch_succeeded")" \
  --argjson lockTaskPackages "$lock_task_packages_json" \
  --argjson recommendations "$recommendations_json" \
  '{
    device: {
      manufacturer: $manufacturer,
      brand: $brand,
      model: $model,
      androidRelease: $androidRelease,
      sdkInt: ($sdkInt | tonumber)
    },
    ownership: {
      hasAnyOwner: $hasAnyOwner,
      hasDeviceOwner: $hasDeviceOwner,
      deviceOwnerType: (if $deviceOwnerType == "" then null else $deviceOwnerType end),
      ownersOutput: $ownersOutput
    },
    app: {
      packageName: $packageName,
      packageInstalled: $packageInstalled,
      expectedActivity: $expectedActivity,
      resolvedActivity: (if $resolvedActivity == "" then null else $resolvedActivity end),
      launcherActivityMatchesExpected: $launcherActivityMatchesExpected,
      displayName: (if $displayName == "" then null else $displayName end),
      runAsAvailable: $runAsAvailable,
      dedicatedEnabled: (if $dedicatedEnabledState == "" then null else $dedicatedEnabledState == "true" end),
      onboardingCompleted: (if $onboardingCompletedState == "" then null else $onboardingCompletedState == "true" end),
      gatewayConnectionMode: (if $gatewayConnectionMode == "" then null else $gatewayConnectionMode end),
      localHostMode: (if $gatewayConnectionMode == "" then null else $localHostMode end),
      remoteAccessEnabled: (if $remoteAccessEnabledState == "" then null else $remoteAccessEnabledState == "true" end),
      remoteAccessPort: (if $remoteAccessPort == "" then null else ($remoteAccessPort | tonumber) end)
    },
    lockTask: {
      modeState: (if $lockTaskModeState == "" then null else $lockTaskModeState end),
      modeStateAfterLaunch: (if $lockTaskModeStateAfterLaunch == "" then null else $lockTaskModeStateAfterLaunch end),
      packageAllowlisted: $lockTaskPackageAllowlisted,
      packages: $lockTaskPackages,
      manifestSupportsAutoLockTask: $manifestSupportsAutoLockTask,
      manifestLockTaskModeRaw: (if $manifestLockTaskModeRaw == "" then null else $manifestLockTaskModeRaw end),
      manifestLockTaskModeLabel: (if $manifestLockTaskModeLabel == "" then null else $manifestLockTaskModeLabel end),
      localApkPath: (if $localApkPath == "" then null else $localApkPath end)
    },
    launch: {
      attempted: $launchAttempted,
      succeeded: $launchSucceeded,
      topActivityAfterLaunch: (if $topActivityAfterLaunch == "" then null else $topActivityAfterLaunch end),
      output: (if $launchOutput == "" then null else $launchOutput end)
    },
    viability: {
      postProvisionReady: $postProvisionReady
    },
    recommendations: $recommendations
  }' >"$SUMMARY_JSON"

printf 'dedicated.post_provision.device=%s %s %s android=%s sdk=%s\n' \
  "$manufacturer" "$brand" "$model" "$android_release" "$sdk_int"
printf 'dedicated.post_provision.owner=%s device_owner=%s lock_task_allowlisted=%s lock_task_mode=%s\n' \
  "$has_any_owner" "$has_device_owner" "$lock_task_allowlisted" "${lock_task_mode_state:-unknown}"
printf 'dedicated.post_provision.app=%s dedicated=%s onboarding=%s gateway=%s manifest_lock_task=%s\n' \
  "$package_installed" "${dedicated_enabled:-unknown}" "${onboarding_completed:-unknown}" "${gateway_connection_mode:-unknown}" "${manifest_lock_task_mode_label:-unknown}"
printf 'dedicated.post_provision.ready=%s resolved_activity=%s\n' \
  "$post_provision_ready" "${resolved_activity:-unknown}"
if [[ "$LAUNCH" == "true" ]]; then
  printf 'dedicated.post_provision.launch=%s lock_task_mode_after_launch=%s\n' \
    "$launch_succeeded" "${lock_task_mode_state_after_launch:-unknown}"
fi
echo "artifacts.summary=$SUMMARY_JSON"
