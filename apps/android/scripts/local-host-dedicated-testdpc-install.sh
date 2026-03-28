#!/usr/bin/env bash
set -euo pipefail

ARTIFACT_DIR="${OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR:-$(mktemp -d -t openclaw-android-testdpc-install.XXXXXX)}"
TESTDPC_REPO="${OPENCLAW_ANDROID_TESTDPC_REPO:-googlesamples/android-testdpc}"
TESTDPC_PACKAGE="${OPENCLAW_ANDROID_TESTDPC_PACKAGE:-com.afwsamples.testdpc}"
TESTDPC_POLICY_ACTIVITY="${OPENCLAW_ANDROID_TESTDPC_POLICY_ACTIVITY:-$TESTDPC_PACKAGE/.PolicyManagementActivity}"
TESTDPC_APK_OVERRIDE="${OPENCLAW_ANDROID_TESTDPC_APK:-}"
RELEASE_JSON="$ARTIFACT_DIR/testdpc-release.json"
APK_PATH="$ARTIFACT_DIR/testdpc-latest.apk"
SUMMARY_JSON="$ARTIFACT_DIR/summary.json"
APPLY=false
LAUNCH=false

usage() {
  cat <<'EOF'
Usage:
  ./apps/android/scripts/local-host-dedicated-testdpc-install.sh
  ./apps/android/scripts/local-host-dedicated-testdpc-install.sh --apply
  ./apps/android/scripts/local-host-dedicated-testdpc-install.sh --apply --launch

What it does:
  1. Fetches the latest public TestDPC GitHub release metadata
  2. Downloads the latest APK locally, unless OPENCLAW_ANDROID_TESTDPC_APK is set
  3. In dry-run mode, prints the exact adb install command and leaves an artifact summary
  4. In apply mode, installs or updates TestDPC on the connected phone
  5. Optionally launches TestDPC's policy activity after install

Important notes:
  - Installing TestDPC alone does not make it Device Owner
  - The current spare-phone path still needs account cleanup or reset before Device Owner can succeed

Environment overrides:
  OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR=/tmp/openclaw-dpc
  OPENCLAW_ANDROID_TESTDPC_REPO=googlesamples/android-testdpc
  OPENCLAW_ANDROID_TESTDPC_PACKAGE=com.afwsamples.testdpc
  OPENCLAW_ANDROID_TESTDPC_APK=/path/to/TestDPC.apk
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h | --help)
      usage
      exit 0
      ;;
    --apply)
      APPLY=true
      shift
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
require_cmd curl
require_cmd jq

device_count="$(adb devices | awk 'NR>1 && $2=="device" {c+=1} END {print c+0}')"
if [[ "$device_count" -lt 1 ]]; then
  echo "No connected Android device (adb state=device)." >&2
  exit 1
fi

mkdir -p "$ARTIFACT_DIR"

curl -fsSL "https://api.github.com/repos/$TESTDPC_REPO/releases/latest" >"$RELEASE_JSON"

release_tag="$(jq -r '.tag_name // ""' "$RELEASE_JSON")"
asset_name="$(jq -r '[.assets[] | select(.name | endswith(".apk"))][0].name // ""' "$RELEASE_JSON")"
asset_url="$(jq -r '[.assets[] | select(.name | endswith(".apk"))][0].browser_download_url // ""' "$RELEASE_JSON")"

if [[ -z "$asset_name" || -z "$asset_url" ]]; then
  echo "Unable to find an APK asset in the latest TestDPC release." >&2
  exit 1
fi

resolved_apk_path="$APK_PATH"
if [[ -n "$TESTDPC_APK_OVERRIDE" ]]; then
  if [[ ! -f "$TESTDPC_APK_OVERRIDE" ]]; then
    echo "OPENCLAW_ANDROID_TESTDPC_APK does not exist: $TESTDPC_APK_OVERRIDE" >&2
    exit 1
  fi
  resolved_apk_path="$TESTDPC_APK_OVERRIDE"
else
  curl -fL --retry 3 --retry-delay 1 "$asset_url" -o "$APK_PATH"
fi

installed=false
installed_version=""
if adb shell pm path "$TESTDPC_PACKAGE" >/dev/null 2>&1; then
  installed=true
  installed_version="$(adb shell dumpsys package "$TESTDPC_PACKAGE" 2>/dev/null | trim_cr | sed -n 's/^[[:space:]]*versionName=//p' | head -n 1)"
fi

owners_output="$(adb shell dpm list-owners 2>&1 | trim_cr || true)"
is_device_owner=false
if [[ "$owners_output" == *"Device Owner"* && "$owners_output" == *"$TESTDPC_PACKAGE"* ]]; then
  is_device_owner=true
fi

install_command="adb install -r -d '$resolved_apk_path'"
launch_command="adb shell am start -n '$TESTDPC_POLICY_ACTIVITY'"

install_attempted=false
install_succeeded=false
install_output=""
launch_attempted=false
launch_succeeded=false
launch_output=""
if [[ "$APPLY" == "true" ]]; then
  install_attempted=true
  set +e
  install_output="$(adb install -r -d "$resolved_apk_path" 2>&1 | trim_cr)"
  install_exit_code=$?
  set -e
  if [[ "$install_exit_code" -eq 0 ]]; then
    install_succeeded=true
  fi
fi

if [[ "$LAUNCH" == "true" ]]; then
  launch_attempted=true
  if [[ "$APPLY" == "true" && "$install_succeeded" != "true" ]]; then
    launch_output="Skipped launch because install did not succeed."
  else
    set +e
    launch_output="$(adb shell am start -n "$TESTDPC_POLICY_ACTIVITY" 2>&1 | trim_cr)"
    launch_exit_code=$?
    set -e
    if [[ "$launch_exit_code" -eq 0 ]]; then
      launch_succeeded=true
    fi
  fi
fi

recommendations_json='[]'
if [[ "$installed" != "true" && "$APPLY" != "true" ]]; then
  recommendations_json="$(jq -cn --argjson prev "$recommendations_json" '$prev + ["Run with --apply to install TestDPC onto the connected phone"]')"
fi
if [[ "$is_device_owner" != "true" ]]; then
  recommendations_json="$(jq -cn --argjson prev "$recommendations_json" '$prev + ["Installing TestDPC is only the first step; Device Owner still needs a reset or account-clean device"]')"
fi
recommendations_json="$(jq -cn --argjson prev "$recommendations_json" '$prev + ["Use pnpm android:local-host:dedicated:testdpc-kiosk after TestDPC becomes Device Owner and OpenClaw should be allowlisted for lock-task"]')"

jq -n \
  --arg repo "$TESTDPC_REPO" \
  --arg packageName "$TESTDPC_PACKAGE" \
  --arg policyActivity "$TESTDPC_POLICY_ACTIVITY" \
  --arg releaseTag "$release_tag" \
  --arg assetName "$asset_name" \
  --arg assetUrl "$asset_url" \
  --arg apkPath "$resolved_apk_path" \
  --arg installedVersion "${installed_version:-}" \
  --arg installCommand "$install_command" \
  --arg launchCommand "$launch_command" \
  --arg ownersOutput "$owners_output" \
  --arg installOutput "${install_output:-}" \
  --arg launchOutput "${launch_output:-}" \
  --argjson installed "$(bool_json "$installed")" \
  --argjson isDeviceOwner "$(bool_json "$is_device_owner")" \
  --argjson installAttempted "$(bool_json "$install_attempted")" \
  --argjson installSucceeded "$(bool_json "$install_succeeded")" \
  --argjson launchAttempted "$(bool_json "$launch_attempted")" \
  --argjson launchSucceeded "$(bool_json "$launch_succeeded")" \
  --argjson recommendations "$recommendations_json" \
  '{
    testdpc: {
      repo: $repo,
      packageName: $packageName,
      policyActivity: $policyActivity,
      installed: $installed,
      installedVersion: (if $installedVersion == "" then null else $installedVersion end),
      isDeviceOwner: $isDeviceOwner,
      ownersOutput: $ownersOutput,
      latestRelease: {
        tagName: $releaseTag,
        assetName: $assetName,
        assetUrl: $assetUrl,
        apkPath: $apkPath
      }
    },
    install: {
      attempted: $installAttempted,
      succeeded: $installSucceeded,
      command: $installCommand,
      output: (if $installOutput == "" then null else $installOutput end)
    },
    launch: {
      attempted: $launchAttempted,
      succeeded: $launchSucceeded,
      command: $launchCommand,
      output: (if $launchOutput == "" then null else $launchOutput end)
    },
    recommendations: $recommendations
  }' >"$SUMMARY_JSON"

printf 'testdpc.install.installed=%s device_owner=%s\n' "$installed" "$is_device_owner"
printf 'testdpc.install.release=%s asset=%s\n' "${release_tag:-unknown}" "$asset_name"
printf 'testdpc.install.apk=%s\n' "$resolved_apk_path"
if [[ -n "$installed_version" ]]; then
  printf 'testdpc.install.installed_version=%s\n' "$installed_version"
fi
if [[ "$APPLY" == "true" ]]; then
  printf 'testdpc.install.apply_succeeded=%s\n' "$install_succeeded"
fi
if [[ "$LAUNCH" == "true" ]]; then
  printf 'testdpc.install.launch_succeeded=%s\n' "$launch_succeeded"
fi
printf 'testdpc.install.command=%s\n' "$install_command"
printf 'artifacts.summary=%s\n' "$SUMMARY_JSON"
