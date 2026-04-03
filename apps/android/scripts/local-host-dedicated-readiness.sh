#!/usr/bin/env bash
set -euo pipefail

ARTIFACT_DIR="${OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR:-$(mktemp -d -t openclaw-android-dedicated-readiness.XXXXXX)}"
DPC_COMPONENT="${OPENCLAW_ANDROID_DPC_COMPONENT:-com.afwsamples.testdpc/.DeviceAdminReceiver}"
ADB_BIN="${OPENCLAW_ANDROID_LOCAL_HOST_ADB_BIN:-adb}"
SUMMARY_JSON="$ARTIFACT_DIR/summary.json"

usage() {
  cat <<'EOF'
Usage:
  [OPENCLAW_ANDROID_LOCAL_HOST_ADB_BIN=/path/to/adb] \
  [OPENCLAW_ANDROID_DPC_COMPONENT=com.afwsamples.testdpc/.DeviceAdminReceiver] \
  ./apps/android/scripts/local-host-dedicated-readiness.sh

What it does:
  1. Reads the current Android device state over adb
  2. Checks whether adb-based device-owner setup is likely ready
  3. Summarizes whether the current phone is better suited for
     device-owner, root/systemize, or custom-ROM work next

Requirements:
  - adb
  - jq
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
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

shell_quote() {
  printf '%q' "$1"
}

bool_json() {
  if [[ "${1:-false}" == "true" ]]; then
    printf 'true'
  else
    printf 'false'
  fi
}

recommend_dedicated_action() {
  local has_device_owner=$1
  local device_owner_via_adb_ready=$2
  local dpc_installed=$3

  if [[ "$has_device_owner" == "true" ]]; then
    printf 'post-provision'
    return
  fi

  if [[ "$device_owner_via_adb_ready" == "true" ]]; then
    if [[ "$dpc_installed" == "true" ]]; then
      printf 'device-owner'
    else
      printf 'testdpc-install'
    fi
    return
  fi

  if [[ "$dpc_installed" == "true" ]]; then
    printf 'testdpc-qr'
  else
    printf 'testdpc-install'
  fi
}

build_recommended_command() {
  case "${1:-}" in
    device-owner)
      printf 'pnpm android:local-host:dedicated:device-owner'
      ;;
    testdpc-install)
      printf 'pnpm android:local-host:dedicated:testdpc-install'
      ;;
    testdpc-qr)
      printf 'pnpm android:local-host:dedicated:testdpc-qr'
      ;;
    post-provision)
      printf 'pnpm android:local-host:dedicated:post-provision'
      ;;
    *)
      printf ''
      ;;
  esac
}

require_cmd "$ADB_BIN"
require_cmd jq

device_count="$("$ADB_BIN" devices | awk 'NR>1 && $2=="device" {c+=1} END {print c+0}')"
if [[ "$device_count" -lt 1 ]]; then
  echo "No connected Android device (adb state=device)." >&2
  exit 1
fi

mkdir -p "$ARTIFACT_DIR"

trim_cr() {
  tr -d '\r'
}

shell_prop() {
  local key=$1
  "$ADB_BIN" shell getprop "$key" | trim_cr
}

manufacturer="$(shell_prop ro.product.manufacturer)"
brand="$(shell_prop ro.product.brand)"
model="$(shell_prop ro.product.model)"
android_release="$(shell_prop ro.build.version.release)"
sdk_int="$(shell_prop ro.build.version.sdk)"
bootloader_locked="$(shell_prop ro.boot.flash.locked)"
verified_boot_state="$(shell_prop ro.boot.verifiedbootstate)"
oem_unlock_supported="$(shell_prop ro.oem_unlock_supported)"
fingerprint="$(shell_prop ro.build.fingerprint)"
device_provisioned="$("$ADB_BIN" shell settings get global device_provisioned | trim_cr)"
user_setup_complete="$("$ADB_BIN" shell settings get secure user_setup_complete | trim_cr)"
users_output="$("$ADB_BIN" shell pm list users | trim_cr)"
owners_output="$("$ADB_BIN" shell dpm list-owners 2>&1 | trim_cr || true)"
accounts_output="$("$ADB_BIN" shell dumpsys account | trim_cr)"
dpc_package="${DPC_COMPONENT%%/*}"
dpc_installed=false
openclaw_installed=false

if "$ADB_BIN" shell pm path "$dpc_package" >/dev/null 2>&1; then
  dpc_installed=true
fi
if "$ADB_BIN" shell pm path ai.openclaw.app >/dev/null 2>&1; then
  openclaw_installed=true
fi

user_count="$(printf '%s\n' "$users_output" | awk '/UserInfo\{/ {c+=1} END {print c+0}')"
accounts_count="$(printf '%s\n' "$accounts_output" | awk '/^[[:space:]]+Account \{/ {c+=1} END {print c+0}')"
has_device_owner=false
if [[ "$owners_output" != "no owners" && -n "$owners_output" ]]; then
  has_device_owner=true
fi

device_owner_via_adb_ready=false
if [[ "$has_device_owner" == "false" && "$accounts_count" -eq 0 && "$user_count" -eq 1 ]]; then
  device_owner_via_adb_ready=true
fi

device_owner_blockers_json="$(jq -cn '[]')"
if [[ "$has_device_owner" == "true" ]]; then
  device_owner_blockers_json="$(jq -cn --argjson prev "$device_owner_blockers_json" '$prev + ["device already has an owner"]')"
fi
if [[ "$accounts_count" -gt 0 ]]; then
  device_owner_blockers_json="$(jq -cn --argjson prev "$device_owner_blockers_json" --arg count "$accounts_count" '$prev + [("device still has " + $count + " configured accounts")]')"
fi
if [[ "$user_count" -gt 1 ]]; then
  device_owner_blockers_json="$(jq -cn --argjson prev "$device_owner_blockers_json" --arg count "$user_count" '$prev + [("device has " + $count + " users")]')"
fi
if [[ "$dpc_installed" != "true" ]]; then
  device_owner_blockers_json="$(jq -cn --argjson prev "$device_owner_blockers_json" --arg component "$DPC_COMPONENT" '$prev + [("DPC component not installed: " + $component)]')"
fi

systemize_root_required=true
custom_rom_required_for_priv_app=true
custom_build_required_for_system_service=true

preferred_path="device_owner_first"
if [[ "$device_owner_via_adb_ready" != "true" && "$bootloader_locked" == "1" ]]; then
  preferred_path="device_owner_after_reset_or_account_cleanup"
fi
if [[ "$device_owner_via_adb_ready" != "true" && "$bootloader_locked" != "1" ]]; then
  preferred_path="device_owner_or_root_experiment"
fi

root_lane_friction="high"
if [[ "$bootloader_locked" != "1" ]]; then
  root_lane_friction="medium"
fi
if [[ -z "$oem_unlock_supported" ]]; then
  root_lane_friction="${root_lane_friction}_unknown_oem_unlock_support"
fi

recommendations_json="$(jq -cn '[]')"
if [[ "$device_owner_via_adb_ready" == "true" ]]; then
  recommendations_json="$(jq -cn --argjson prev "$recommendations_json" --arg component "$DPC_COMPONENT" '$prev + [("try adb-based device-owner provisioning next with " + $component)]')"
else
  recommendations_json="$(jq -cn --argjson prev "$recommendations_json" '$prev + ["clear accounts or factory-reset the phone before adb device-owner provisioning"]')"
fi
recommendations_json="$(jq -cn --argjson prev "$recommendations_json" '$prev + ["treat root/systemize as the second lane, not the first, on this locked OPPO phone"]')"
recommendations_json="$(jq -cn --argjson prev "$recommendations_json" '$prev + ["treat custom-ROM priv-app preload and true system_server integration as later-stage experiments"]')"

recommended_action="$(recommend_dedicated_action "$has_device_owner" "$device_owner_via_adb_ready" "$dpc_installed")"
recommended_command="$(build_recommended_command "$recommended_action")"
if [[ -n "${OPENCLAW_ANDROID_LOCAL_HOST_ADB_BIN:-}" && -n "$recommended_command" ]]; then
  recommended_command="OPENCLAW_ANDROID_LOCAL_HOST_ADB_BIN=$(shell_quote "$ADB_BIN") $recommended_command"
fi

jq -n \
  --arg manufacturer "$manufacturer" \
  --arg brand "$brand" \
  --arg model "$model" \
  --arg androidRelease "$android_release" \
  --arg sdkInt "$sdk_int" \
  --arg bootloaderLocked "$bootloader_locked" \
  --arg verifiedBootState "$verified_boot_state" \
  --arg oemUnlockSupported "$oem_unlock_supported" \
  --arg fingerprint "$fingerprint" \
  --arg deviceProvisioned "$device_provisioned" \
  --arg userSetupComplete "$user_setup_complete" \
  --arg usersOutput "$users_output" \
  --arg ownersOutput "$owners_output" \
  --arg dpcComponent "$DPC_COMPONENT" \
  --argjson userCount "$user_count" \
  --argjson accountsCount "$accounts_count" \
  --argjson hasDeviceOwner "$( [[ "$has_device_owner" == "true" ]] && printf 'true' || printf 'false' )" \
  --argjson dpcInstalled "$( [[ "$dpc_installed" == "true" ]] && printf 'true' || printf 'false' )" \
  --argjson openclawInstalled "$( [[ "$openclaw_installed" == "true" ]] && printf 'true' || printf 'false' )" \
  --argjson deviceOwnerViaAdbReady "$( [[ "$device_owner_via_adb_ready" == "true" ]] && printf 'true' || printf 'false' )" \
  --argjson systemizeRootRequired "$( [[ "$systemize_root_required" == "true" ]] && printf 'true' || printf 'false' )" \
  --argjson customRomRequiredForPrivApp "$( [[ "$custom_rom_required_for_priv_app" == "true" ]] && printf 'true' || printf 'false' )" \
  --argjson customBuildRequiredForSystemService "$( [[ "$custom_build_required_for_system_service" == "true" ]] && printf 'true' || printf 'false' )" \
  --arg preferredPath "$preferred_path" \
  --arg rootLaneFriction "$root_lane_friction" \
  --arg recommendedAction "$recommended_action" \
  --arg recommendedCommand "$recommended_command" \
  --argjson deviceOwnerBlockers "$device_owner_blockers_json" \
  --argjson recommendations "$recommendations_json" \
  '{
    device: {
      manufacturer: $manufacturer,
      brand: $brand,
      model: $model,
      androidRelease: $androidRelease,
      sdkInt: ($sdkInt | tonumber),
      bootloaderLocked: ($bootloaderLocked == "1"),
      verifiedBootState: (if $verifiedBootState == "" then null else $verifiedBootState end),
      oemUnlockSupported: (if $oemUnlockSupported == "" then null else $oemUnlockSupported end),
      fingerprint: $fingerprint
    },
    state: {
      deviceProvisioned: ($deviceProvisioned == "1"),
      userSetupComplete: ($userSetupComplete == "1"),
      userCount: $userCount,
      accountsCount: $accountsCount,
      hasDeviceOwner: $hasDeviceOwner,
      dpcComponent: $dpcComponent,
      dpcInstalled: $dpcInstalled,
      openclawInstalled: $openclawInstalled
    },
    viability: {
      deviceOwnerViaAdbReady: $deviceOwnerViaAdbReady,
      deviceOwnerBlockers: $deviceOwnerBlockers,
      systemizeRootRequired: $systemizeRootRequired,
      customRomRequiredForPrivApp: $customRomRequiredForPrivApp,
      customBuildRequiredForSystemService: $customBuildRequiredForSystemService,
      preferredPath: $preferredPath,
      rootLaneFriction: $rootLaneFriction
    },
    recommendedAction: $recommendedAction,
    recommendedCommand: (if $recommendedCommand == "" then null else $recommendedCommand end),
    raw: {
      users: $usersOutput,
      owners: $ownersOutput
    },
    recommendations: $recommendations
  }' >"$SUMMARY_JSON"

printf 'dedicated.device=%s %s %s android=%s sdk=%s\n' \
  "$manufacturer" "$brand" "$model" "$android_release" "$sdk_int"
printf 'dedicated.owner_via_adb_ready=%s accounts=%s users=%s dpc_installed=%s\n' \
  "$device_owner_via_adb_ready" "$accounts_count" "$user_count" "$dpc_installed"
printf 'dedicated.bootloader_locked=%s verified_boot=%s preferred_path=%s root_lane_friction=%s\n' \
  "${bootloader_locked:-unknown}" "${verified_boot_state:-unknown}" "$preferred_path" "$root_lane_friction"
printf 'dedicated.recommended_action=%s\n' "$recommended_action"
if [[ -n "$recommended_command" ]]; then
  printf 'dedicated.recommended_command=%s\n' "$recommended_command"
fi
echo "artifacts.summary=$SUMMARY_JSON"
