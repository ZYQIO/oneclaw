#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STABILITY_SCRIPT="${OPENCLAW_ANDROID_LOCAL_HOST_EMBEDDED_RUNTIME_POD_STABILITY_SCRIPT:-$SCRIPT_DIR/local-host-embedded-runtime-pod-stability.sh}"
ARTIFACT_DIR="${OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR:-$(mktemp -d -t openclaw-android-runtime-soak.XXXXXX)}"
ITERATIONS="${OPENCLAW_ANDROID_LOCAL_HOST_SOAK_ITERATIONS:-5}"
DELAY_SEC="${OPENCLAW_ANDROID_LOCAL_HOST_SOAK_DELAY_SEC:-0}"
ADB_BIN="${OPENCLAW_ANDROID_LOCAL_HOST_ADB_BIN:-}"
APP_PACKAGE="${OPENCLAW_ANDROID_LOCAL_HOST_APP_PACKAGE:-}"
APP_COMPONENT="${OPENCLAW_ANDROID_LOCAL_HOST_APP_COMPONENT:-}"
JSON=false

usage() {
  cat <<'EOF'
Usage:
  ./apps/android/scripts/local-host-embedded-runtime-pod-soak.sh
  ./apps/android/scripts/local-host-embedded-runtime-pod-soak.sh --json
  ./apps/android/scripts/local-host-embedded-runtime-pod-soak.sh --iterations 7 --delay-sec 2

What it does:
  1. Reuses the embedded-runtime pod stability wrapper
  2. Defaults to a longer five-iteration soak
  3. Always inserts an explicit app restart perturbation between iterations
  4. Keeps the same JSON/artifact surface as the stability wrapper

Notes:
  - This is the default hardening lane once the shorter repeatability check is already green
  - OPENCLAW_ANDROID_LOCAL_HOST_TOKEN and browser-related env overrides still pass through to stability/doctor
EOF
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
    --adb-bin)
      if [[ $# -lt 2 ]]; then
        echo "--adb-bin requires a value." >&2
        exit 1
      fi
      ADB_BIN=$2
      shift 2
      ;;
    --app-package)
      if [[ $# -lt 2 ]]; then
        echo "--app-package requires a value." >&2
        exit 1
      fi
      APP_PACKAGE=$2
      shift 2
      ;;
    --app-component)
      if [[ $# -lt 2 ]]; then
        echo "--app-component requires a value." >&2
        exit 1
      fi
      APP_COMPONENT=$2
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

if ! is_positive_integer "$ITERATIONS"; then
  echo "Iterations must be a positive integer." >&2
  exit 1
fi

if ! is_nonnegative_number "$DELAY_SEC"; then
  echo "Delay seconds must be a non-negative number." >&2
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "jq required but missing." >&2
  exit 1
fi

stability_args=(
  --iterations "$ITERATIONS"
  --delay-sec "$DELAY_SEC"
  --restart-app-between-iterations
)

if [[ "$JSON" == "true" ]]; then
  stability_args+=(--json)
fi

if [[ -n "$ADB_BIN" ]]; then
  stability_args+=(--adb-bin "$ADB_BIN")
fi

if [[ -n "$APP_PACKAGE" ]]; then
  stability_args+=(--app-package "$APP_PACKAGE")
fi

if [[ -n "$APP_COMPONENT" ]]; then
  stability_args+=(--app-component "$APP_COMPONENT")
fi

summary_path="$ARTIFACT_DIR/summary.json"
stdout_path="$(mktemp -t openclaw-android-runtime-soak.stdout.XXXXXX)"
tmp_summary_path="$(mktemp -t openclaw-android-runtime-soak.summary.XXXXXX)"

trap 'rm -f "$stdout_path" "$tmp_summary_path"' EXIT

stability_exit_code=0
if OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR="$ARTIFACT_DIR" \
  bash "$STABILITY_SCRIPT" "${stability_args[@]}" >"$stdout_path"; then
  stability_exit_code=0
else
  stability_exit_code=$?
fi

if [[ -f "$summary_path" ]]; then
  jq \
    --arg packageCommand "pnpm android:local-host:embedded-runtime-pod:soak" \
    --arg stabilityPackageCommand "pnpm android:local-host:embedded-runtime-pod:stability" \
    --argjson defaultIterations 5 \
    '.packageCommand = $packageCommand
    | .stabilityPackageCommand = $stabilityPackageCommand
    | .defaultIterations = $defaultIterations
    | .restartAppBetweenIterations = true' \
    "$summary_path" >"$tmp_summary_path"
  mv "$tmp_summary_path" "$summary_path"
fi

if [[ "$JSON" == "true" ]]; then
  if [[ -f "$summary_path" ]]; then
    cat "$summary_path"
  else
    cat "$stdout_path"
  fi
else
  cat "$stdout_path"
fi

exit "$stability_exit_code"
