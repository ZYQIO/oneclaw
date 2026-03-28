#!/usr/bin/env bash
set -euo pipefail

ARTIFACT_DIR="${OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR:-$(mktemp -d -t openclaw-android-testdpc-qr.XXXXXX)}"
TESTDPC_REPO="${OPENCLAW_ANDROID_TESTDPC_REPO:-googlesamples/android-testdpc}"
TESTDPC_COMPONENT="${OPENCLAW_ANDROID_DPC_COMPONENT:-com.afwsamples.testdpc/.DeviceAdminReceiver}"
TESTDPC_APK_OVERRIDE="${OPENCLAW_ANDROID_TESTDPC_APK:-}"
GENERATOR_SCRIPT="apps/android/scripts/local-host-dedicated-provisioning-qr.ts"
RELEASE_JSON="$ARTIFACT_DIR/testdpc-release.json"
APK_PATH="$ARTIFACT_DIR/testdpc-latest.apk"

usage() {
  cat <<'EOF'
Usage:
  ./apps/android/scripts/local-host-dedicated-testdpc-qr.sh [generator flags...]

What it does:
  1. Fetches the latest public TestDPC GitHub release metadata
  2. Downloads the latest APK asset locally
  3. Computes the package checksum
  4. Generates an Android dedicated-device provisioning QR payload and ASCII QR

Examples:
  pnpm android:local-host:dedicated:testdpc-qr
  pnpm android:local-host:dedicated:testdpc-qr -- --wifi-ssid MyWiFi --wifi-security WPA --wifi-password secret

Environment overrides:
  OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR=/tmp/openclaw-dpc
  OPENCLAW_ANDROID_TESTDPC_REPO=googlesamples/android-testdpc
  OPENCLAW_ANDROID_DPC_COMPONENT=com.afwsamples.testdpc/.DeviceAdminReceiver
  OPENCLAW_ANDROID_TESTDPC_APK=/path/to/TestDPC.apk
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

require_cmd() {
  local name=$1
  if ! command -v "$name" >/dev/null 2>&1; then
    echo "$name required but missing." >&2
    exit 1
  fi
}

require_cmd curl
require_cmd jq
require_cmd pnpm

mkdir -p "$ARTIFACT_DIR"

curl -fsSL "https://api.github.com/repos/$TESTDPC_REPO/releases/latest" >"$RELEASE_JSON"

release_tag="$(jq -r '.tag_name // ""' "$RELEASE_JSON")"
asset_name="$(jq -r '[.assets[] | select(.name | endswith(".apk"))][0].name // ""' "$RELEASE_JSON")"
asset_url="$(jq -r '[.assets[] | select(.name | endswith(".apk"))][0].browser_download_url // ""' "$RELEASE_JSON")"

if [[ -z "$asset_name" || -z "$asset_url" ]]; then
  echo "Unable to find an APK asset in the latest TestDPC release." >&2
  exit 1
fi

generator_apk_path="$APK_PATH"
if [[ -n "$TESTDPC_APK_OVERRIDE" ]]; then
  if [[ ! -f "$TESTDPC_APK_OVERRIDE" ]]; then
    echo "OPENCLAW_ANDROID_TESTDPC_APK does not exist: $TESTDPC_APK_OVERRIDE" >&2
    exit 1
  fi
  generator_apk_path="$TESTDPC_APK_OVERRIDE"
else
  curl -fL --retry 3 --retry-delay 1 "$asset_url" -o "$APK_PATH"
fi

printf 'testdpc.release=%s asset=%s\n' "${release_tag:-unknown}" "$asset_name"
printf 'testdpc.download_url=%s\n' "$asset_url"
printf 'testdpc.apk=%s\n' "$generator_apk_path"

pnpm exec tsx "$GENERATOR_SCRIPT" \
  --component "$TESTDPC_COMPONENT" \
  --download-url "$asset_url" \
  --apk "$generator_apk_path" \
  --artifact-dir "$ARTIFACT_DIR" \
  "$@"
