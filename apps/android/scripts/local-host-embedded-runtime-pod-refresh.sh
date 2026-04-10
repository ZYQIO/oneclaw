#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ARTIFACT_DIR="${OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR:-$(mktemp -d -t openclaw-android-runtime-refresh.XXXXXX)}"
PNPM_BIN="${OPENCLAW_ANDROID_LOCAL_HOST_PNPM_BIN:-pnpm}"
ADB_BIN="${OPENCLAW_ANDROID_LOCAL_HOST_ADB_BIN:-adb}"
TOKEN_HELPER="${OPENCLAW_ANDROID_LOCAL_HOST_TOKEN_HELPER:-}"
APK_PATH="${OPENCLAW_ANDROID_LOCAL_HOST_DEBUG_APK_PATH:-}"
DEFAULT_APK_DIR="$SCRIPT_DIR/../app/build/outputs/apk/debug"
ITERATIONS="${OPENCLAW_ANDROID_LOCAL_HOST_REFRESH_ITERATIONS:-5}"
DELAY_SEC="${OPENCLAW_ANDROID_LOCAL_HOST_REFRESH_DELAY_SEC:-0}"
SERIAL="${ANDROID_SERIAL:-}"
APP_PACKAGE="${OPENCLAW_ANDROID_LOCAL_HOST_APP_PACKAGE:-ai.openclaw.app}"
APP_COMPONENT="${OPENCLAW_ANDROID_LOCAL_HOST_APP_COMPONENT:-ai.openclaw.app/.MainActivity}"
ANDROID_SDK_DIR=""
JSON=false

usage() {
  cat <<'EOF'
Usage:
  ./apps/android/scripts/local-host-embedded-runtime-pod-refresh.sh
  ./apps/android/scripts/local-host-embedded-runtime-pod-refresh.sh --json
  ./apps/android/scripts/local-host-embedded-runtime-pod-refresh.sh --iterations 7 --delay-sec 2

What it does:
  1. Reinstalls the current Android debug app
  2. Falls back to adb install -r -d when pnpm android:install fails on this device
  3. Re-exports the local-host bearer token over trusted adb
  4. Runs the formal embedded-runtime pod soak wrapper with that fresh token

Notes:
  - This is the default reinstall perturbation lane once soak is already the preferred hardening lane
  - The exported bearer token is bridged into soak but never written into the wrapper artifact bundle
  - Pass --serial when you need to force one adb target; the wrapper also forwards ANDROID_SERIAL to child commands
EOF
}

require_cmd() {
  local name=$1
  if [[ "$name" == */* ]]; then
    if [[ ! -x "$name" ]]; then
      echo "$name required but missing." >&2
      exit 1
    fi
    return
  fi
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

bool_json() {
  if [[ "${1:-false}" == "true" ]]; then
    printf 'true'
  else
    printf 'false'
  fi
}

run_with_optional_serial() {
  local -a env_args=()
  if [[ -n "$SERIAL" ]]; then
    env_args+=(ANDROID_SERIAL="$SERIAL")
  fi
  if [[ -n "$ANDROID_SDK_DIR" ]]; then
    env_args+=(
      ANDROID_HOME="$ANDROID_SDK_DIR"
      ANDROID_SDK_ROOT="$ANDROID_SDK_DIR"
    )
  fi
  env "${env_args[@]}" "$@"
}

resolve_android_sdk_dir() {
  if [[ -n "${ANDROID_HOME:-}" ]]; then
    printf '%s' "$ANDROID_HOME"
    return 0
  fi
  if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
    printf '%s' "$ANDROID_SDK_ROOT"
    return 0
  fi

  local local_properties="$SCRIPT_DIR/../local.properties"
  if [[ -f "$local_properties" ]]; then
    local sdk_dir
    sdk_dir="$(sed -n 's/^sdk\.dir=//p' "$local_properties" | head -n 1)"
    if [[ -n "$sdk_dir" ]]; then
      printf '%s' "$sdk_dir"
      return 0
    fi
  fi

  local candidates=(
    "$HOME/Library/Android/sdk"
    "$HOME/Android/Sdk"
  )
  local candidate
  for candidate in "${candidates[@]}"; do
    if [[ -d "$candidate" ]]; then
      printf '%s' "$candidate"
      return 0
    fi
  done

  return 1
}

resolve_debug_apk_path() {
  if [[ -n "$APK_PATH" ]]; then
    printf '%s' "$APK_PATH"
    return 0
  fi

  if [[ ! -d "$DEFAULT_APK_DIR" ]]; then
    return 1
  fi

  local candidate
  candidate="$(find "$DEFAULT_APK_DIR" -maxdepth 1 -type f -name '*-debug.apk' | sort | tail -n 1)"
  if [[ -n "$candidate" ]]; then
    printf '%s' "$candidate"
    return 0
  fi

  return 1
}

run_token_helper() {
  local token_args=(--json --adb-bin "$ADB_BIN")
  if [[ -n "$SERIAL" ]]; then
    token_args+=(--serial "$SERIAL")
  fi
  if [[ -n "$APP_PACKAGE" ]]; then
    token_args+=(--app-package "$APP_PACKAGE")
  fi
  if [[ -n "$APP_COMPONENT" ]]; then
    token_args+=(--app-component "$APP_COMPONENT")
  fi

  if [[ -n "$TOKEN_HELPER" ]]; then
    if [[ -x "$TOKEN_HELPER" ]]; then
      run_with_optional_serial "$TOKEN_HELPER" "${token_args[@]}"
    else
      run_with_optional_serial "$PNPM_BIN" exec tsx "$TOKEN_HELPER" "${token_args[@]}"
    fi
    return
  fi

  run_with_optional_serial "$PNPM_BIN" exec tsx "$SCRIPT_DIR/local-host-token.ts" "${token_args[@]}"
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
    --serial)
      if [[ $# -lt 2 ]]; then
        echo "--serial requires a value." >&2
        exit 1
      fi
      SERIAL=$2
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
    --apk-path)
      if [[ $# -lt 2 ]]; then
        echo "--apk-path requires a value." >&2
        exit 1
      fi
      APK_PATH=$2
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
require_cmd "$PNPM_BIN"
require_cmd "$ADB_BIN"

if ! is_positive_integer "$ITERATIONS"; then
  echo "Iterations must be a positive integer." >&2
  exit 1
fi

if ! is_nonnegative_number "$DELAY_SEC"; then
  echo "Delay seconds must be a non-negative number." >&2
  exit 1
fi

ANDROID_SDK_DIR="$(resolve_android_sdk_dir || true)"

mkdir -p "$ARTIFACT_DIR"

SUMMARY_JSON="$ARTIFACT_DIR/summary.json"
INSTALL_STDOUT="$ARTIFACT_DIR/install.stdout.txt"
INSTALL_STDERR="$ARTIFACT_DIR/install.stderr.txt"
ASSEMBLE_STDOUT="$ARTIFACT_DIR/assemble.stdout.txt"
ASSEMBLE_STDERR="$ARTIFACT_DIR/assemble.stderr.txt"
ADB_INSTALL_STDOUT="$ARTIFACT_DIR/adb-install.stdout.txt"
ADB_INSTALL_STDERR="$ARTIFACT_DIR/adb-install.stderr.txt"
TOKEN_METADATA_JSON="$ARTIFACT_DIR/token-export.json"
TOKEN_STDERR="$ARTIFACT_DIR/token.stderr.txt"
SOAK_DIR="$ARTIFACT_DIR/soak"
SOAK_STDOUT="$ARTIFACT_DIR/soak.stdout.txt"
SOAK_STDERR="$ARTIFACT_DIR/soak.stderr.txt"
SOAK_SUMMARY="$SOAK_DIR/summary.json"
TOKEN_STDOUT_TMP="$(mktemp -t openclaw-android-runtime-refresh-token.XXXXXX)"

trap 'rm -f "$TOKEN_STDOUT_TMP"' EXIT

install_primary_exit_code=0
install_primary_ok=false
install_ok=false
install_final_method=""
install_fallback_used=false
install_apk_available_for_fallback=false
install_assemble_executed=false
install_assemble_exit_code=null
install_fallback_exit_code=null
install_fallback_ok=false
token_exit_code=null
token_ok=false
token_exported=false
token_redacted=false
token=""
soak_exit_code=null
soak_executed=false
soak_ok=false

if run_with_optional_serial "$PNPM_BIN" android:install >"$INSTALL_STDOUT" 2>"$INSTALL_STDERR"; then
  install_primary_ok=true
  install_ok=true
  install_final_method="pnpm_android_install"
else
  install_primary_exit_code=$?
fi

if [[ "$install_ok" != "true" ]]; then
  install_fallback_used=true
  APK_PATH="$(resolve_debug_apk_path || true)"
  if [[ -n "$APK_PATH" && -f "$APK_PATH" ]]; then
    install_apk_available_for_fallback=true
  else
    install_assemble_executed=true
    if run_with_optional_serial "$PNPM_BIN" android:assemble >"$ASSEMBLE_STDOUT" 2>"$ASSEMBLE_STDERR"; then
      install_assemble_exit_code=0
    else
      install_assemble_exit_code=$?
    fi
  fi

  APK_PATH="$(resolve_debug_apk_path || true)"
  if [[ -n "$APK_PATH" && -f "$APK_PATH" ]]; then
    install_apk_available_for_fallback=true
    if [[ -n "$SERIAL" ]]; then
      if "$ADB_BIN" -s "$SERIAL" install -r -d "$APK_PATH" >"$ADB_INSTALL_STDOUT" 2>"$ADB_INSTALL_STDERR"; then
        install_fallback_exit_code=0
        install_fallback_ok=true
        install_ok=true
        install_final_method="adb_install_debug_apk"
      else
        install_fallback_exit_code=$?
      fi
    elif "$ADB_BIN" install -r -d "$APK_PATH" >"$ADB_INSTALL_STDOUT" 2>"$ADB_INSTALL_STDERR"; then
      install_fallback_exit_code=0
      install_fallback_ok=true
      install_ok=true
      install_final_method="adb_install_debug_apk"
    else
      install_fallback_exit_code=$?
    fi
  fi
fi

if [[ "$install_ok" == "true" ]]; then
  if run_token_helper >"$TOKEN_STDOUT_TMP" 2>"$TOKEN_STDERR"; then
    token_exit_code=0
    token="$(jq -r '.token // ""' "$TOKEN_STDOUT_TMP")"
    if [[ -n "$token" ]]; then
      token_ok=true
      token_exported=true
      token_redacted=true
      jq \
        '.token = "REDACTED"
        | .tokenRedacted = true
        | .tokenExported = true' \
        "$TOKEN_STDOUT_TMP" >"$TOKEN_METADATA_JSON"
    else
      token_exit_code=1
    fi
  else
    token_exit_code=$?
  fi
fi

if [[ "$token_ok" == "true" ]]; then
  mkdir -p "$SOAK_DIR"
  soak_args=(
    android:local-host:embedded-runtime-pod:soak
    --
    --json
    --iterations "$ITERATIONS"
    --delay-sec "$DELAY_SEC"
    --adb-bin "$ADB_BIN"
    --app-package "$APP_PACKAGE"
    --app-component "$APP_COMPONENT"
  )
  soak_executed=true
  if OPENCLAW_ANDROID_LOCAL_HOST_TOKEN="$token" \
    OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR="$SOAK_DIR" \
    run_with_optional_serial "$PNPM_BIN" "${soak_args[@]}" >"$SOAK_STDOUT" 2>"$SOAK_STDERR"; then
    soak_exit_code=0
    soak_ok=true
  else
    soak_exit_code=$?
  fi
fi

if [[ -z "$APK_PATH" ]]; then
  APK_PATH="$(resolve_debug_apk_path || true)"
fi

jq_args=(
  -n
  --arg script "local-host-embedded-runtime-pod-refresh.sh"
  --arg command "./apps/android/scripts/local-host-embedded-runtime-pod-refresh.sh"
  --arg packageCommand "pnpm android:local-host:embedded-runtime-pod:refresh"
  --arg installPackageCommand "pnpm android:install"
  --arg assemblePackageCommand "pnpm android:assemble"
  --arg tokenPackageCommand "pnpm android:local-host:token"
  --arg soakPackageCommand "pnpm android:local-host:embedded-runtime-pod:soak"
  --arg artifactDir "$ARTIFACT_DIR"
  --arg apkPath "$APK_PATH"
  --arg adbBin "$ADB_BIN"
  --arg androidSdkRoot "$ANDROID_SDK_DIR"
  --arg serial "$SERIAL"
  --arg appPackage "$APP_PACKAGE"
  --arg appComponent "$APP_COMPONENT"
  --arg installStdoutPath "$INSTALL_STDOUT"
  --arg installStderrPath "$INSTALL_STDERR"
  --arg assembleStdoutPath "$ASSEMBLE_STDOUT"
  --arg assembleStderrPath "$ASSEMBLE_STDERR"
  --arg adbInstallStdoutPath "$ADB_INSTALL_STDOUT"
  --arg adbInstallStderrPath "$ADB_INSTALL_STDERR"
  --arg tokenMetadataPath "$TOKEN_METADATA_JSON"
  --arg tokenStderrPath "$TOKEN_STDERR"
  --arg soakStdoutPath "$SOAK_STDOUT"
  --arg soakStderrPath "$SOAK_STDERR"
  --arg soakSummaryPath "$SOAK_SUMMARY"
  --arg installFinalMethod "$install_final_method"
  --argjson iterationsRequested "$ITERATIONS"
  --argjson installPrimaryExitCode "$install_primary_exit_code"
  --argjson installAssembleExitCode "$install_assemble_exit_code"
  --argjson installFallbackExitCode "$install_fallback_exit_code"
  --argjson tokenExitCode "$token_exit_code"
  --argjson soakExitCode "$soak_exit_code"
  --argjson installPrimaryOk "$(bool_json "$install_primary_ok")"
  --argjson installOk "$(bool_json "$install_ok")"
  --argjson installFallbackUsed "$(bool_json "$install_fallback_used")"
  --argjson installFallbackOk "$(bool_json "$install_fallback_ok")"
  --argjson installAssembleExecuted "$(bool_json "$install_assemble_executed")"
  --argjson installApkAvailableForFallback "$(bool_json "$install_apk_available_for_fallback")"
  --argjson tokenOk "$(bool_json "$token_ok")"
  --argjson tokenExported "$(bool_json "$token_exported")"
  --argjson tokenRedacted "$(bool_json "$token_redacted")"
  --argjson soakExecuted "$(bool_json "$soak_executed")"
  --argjson soakOk "$(bool_json "$soak_ok")"
)

if [[ -f "$TOKEN_METADATA_JSON" ]]; then
  jq_args+=(--slurpfile tokenMetadata "$TOKEN_METADATA_JSON")
else
  jq_args+=(--argjson tokenMetadata null)
fi

if [[ -f "$SOAK_SUMMARY" ]]; then
  jq_args+=(--slurpfile soakSummary "$SOAK_SUMMARY")
else
  jq_args+=(--argjson soakSummary null)
fi

jq "${jq_args[@]}" '
  {
    script: $script,
    command: $command,
    packageCommand: $packageCommand,
    mode: "reinstall_token_soak",
    artifactRoot: $artifactDir,
    ok: ($installOk and $tokenOk and $soakOk),
    iterationsRequested: $iterationsRequested,
    androidSdkRoot: (if $androidSdkRoot == "" then null else $androidSdkRoot end),
    install: {
      ok: $installOk,
      finalMethod: (if $installFinalMethod == "" then null else $installFinalMethod end),
      apkPath: (if $apkPath == "" then null else $apkPath end),
      primary: {
        packageCommand: $installPackageCommand,
        attempted: true,
        ok: $installPrimaryOk,
        exitCode: $installPrimaryExitCode,
        stdoutPath: $installStdoutPath,
        stderrPath: $installStderrPath
      },
      fallback: {
        used: $installFallbackUsed,
        ok: $installFallbackOk,
        apkAvailableForFallback: $installApkAvailableForFallback,
        assembleExecuted: $installAssembleExecuted,
        assemblePackageCommand: $assemblePackageCommand,
        assembleExitCode: $installAssembleExitCode,
        assembleStdoutPath: (if $installAssembleExecuted then $assembleStdoutPath else null end),
        assembleStderrPath: (if $installAssembleExecuted then $assembleStderrPath else null end),
        adbInstallCommand: (
          if (($installFallbackUsed | not) or $apkPath == "") then
            null
          elif $serial == "" then
            ($adbBin + " install -r -d " + $apkPath)
          else
            ($adbBin + " -s " + $serial + " install -r -d " + $apkPath)
          end
        ),
        adbInstallExitCode: $installFallbackExitCode,
        adbInstallStdoutPath: (if $installFallbackUsed then $adbInstallStdoutPath else null end),
        adbInstallStderrPath: (if $installFallbackUsed then $adbInstallStderrPath else null end)
      }
    },
    token: {
      ok: $tokenOk,
      packageCommand: $tokenPackageCommand,
      exitCode: $tokenExitCode,
      metadataPath: (if $tokenOk then $tokenMetadataPath else null end),
      stderrPath: $tokenStderrPath,
      tokenExported: $tokenExported,
      tokenRedacted: $tokenRedacted,
      metadata: (
        if $tokenMetadata == null then
          null
        else
          $tokenMetadata[0]
        end
      )
    },
    soak: {
      executed: $soakExecuted,
      ok: $soakOk,
      packageCommand: $soakPackageCommand,
      exitCode: $soakExitCode,
      stdoutPath: (if $soakExecuted then $soakStdoutPath else null end),
      stderrPath: (if $soakExecuted then $soakStderrPath else null end),
      summaryPath: (if $soakSummary == null then null else $soakSummaryPath end),
      summary: (
        if $soakSummary == null then
          null
        else
          $soakSummary[0]
        end
      )
    },
    classifications: (
      if $soakSummary == null then
        []
      else
        ($soakSummary[0].classifications // [])
      end
    ),
    recommendedNextSlices: (
      if $soakSummary == null then
        []
      else
        ($soakSummary[0].recommendedNextSlices // [])
      end
    ),
    passedIterationCount: (
      if $soakSummary == null then
        null
      else
        ($soakSummary[0].passedIterationCount // null)
      end
    ),
    failedIterationCount: (
      if $soakSummary == null then
        null
      else
        ($soakSummary[0].failedIterationCount // null)
      end
    ),
    perturbationMode: (
      if $soakSummary == null then
        null
      else
        ($soakSummary[0].perturbationMode // null)
      end
    ),
    perturbationAppliedCount: (
      if $soakSummary == null then
        null
      else
        ($soakSummary[0].perturbationAppliedCount // null)
      end
    ),
    perturbationFailureCount: (
      if $soakSummary == null then
        null
      else
        ($soakSummary[0].perturbationFailureCount // null)
      end
    ),
    stableExpectedArtifactCount: (
      if $soakSummary == null then
        null
      else
        ($soakSummary[0].stableExpectedArtifactCount // null)
      end
    ),
    stableCapturedArtifactCount: (
      if $soakSummary == null then
        null
      else
        ($soakSummary[0].stableCapturedArtifactCount // null)
      end
    )
  }
' >"$SUMMARY_JSON"

if [[ "$JSON" == "true" ]]; then
  cat "$SUMMARY_JSON"
else
  printf 'runtime_pod_refresh.install=%s\n' "$install_ok"
  printf 'runtime_pod_refresh.install_method=%s\n' "${install_final_method:-unknown}"
  printf 'runtime_pod_refresh.token=%s\n' "$token_ok"
  printf 'runtime_pod_refresh.token_redacted=%s\n' "$token_redacted"
  printf 'runtime_pod_refresh.soak=%s\n' "$soak_ok"
  if [[ -f "$SOAK_SUMMARY" ]]; then
    printf 'runtime_pod_refresh.classifications=%s\n' "$(jq -r '[.classifications[]?] | join(",")' "$SOAK_SUMMARY")"
    printf 'runtime_pod_refresh.passed=%s\n' "$(jq -r '.passedIterationCount // 0' "$SOAK_SUMMARY")"
    printf 'runtime_pod_refresh.failed=%s\n' "$(jq -r '.failedIterationCount // 0' "$SOAK_SUMMARY")"
  fi
  printf 'artifacts.summary=%s\n' "$SUMMARY_JSON"
fi

if [[ "$install_ok" != "true" ]]; then
  exit 1
fi

if [[ "$token_ok" != "true" ]]; then
  exit 1
fi

exit "${soak_exit_code:-0}"
