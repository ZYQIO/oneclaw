#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOKEN_SCRIPT="${OPENCLAW_ANDROID_LOCAL_HOST_TOKEN_SCRIPT:-}"
SMOKE_SCRIPT="${OPENCLAW_ANDROID_LOCAL_HOST_SMOKE_SCRIPT:-$SCRIPT_DIR/local-host-remote-smoke.sh}"
OPENAI_NETWORK_SCRIPT="${OPENCLAW_ANDROID_LOCAL_HOST_OPENAI_NETWORK_SCRIPT:-$SCRIPT_DIR/local-host-openai-network-probe.sh}"
ARTIFACT_DIR="${OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR:-$(mktemp -d -t openclaw-android-local-host-doctor.XXXXXX)}"
JSON=false

usage() {
  cat <<'EOF'
Usage:
  ./apps/android/scripts/local-host-doctor.sh
  ./apps/android/scripts/local-host-doctor.sh --json

What it does:
  1. Reuses OPENCLAW_ANDROID_LOCAL_HOST_TOKEN when already provided
  2. Otherwise bootstraps the debug local-host token over trusted adb
  3. Runs the local-host smoke script
  4. If smoke fails with openai_connect_timeout, runs the OpenAI network probe
  5. Writes one combined summary.json for the whole diagnosis pass

Notes:
  - This wrapper is meant to diagnose and summarize, so it can still exit 0 even when the health result is unhealthy
  - The token bootstrap path is debug-only; release builds still need a manually supplied token
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

run_token_helper() {
  if [[ -n "$TOKEN_SCRIPT" ]]; then
    "$TOKEN_SCRIPT"
  else
    pnpm exec tsx "$SCRIPT_DIR/local-host-token.ts" --json
  fi
}

require_cmd bash
require_cmd jq

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

SMOKE_DIR="$ARTIFACT_DIR/smoke"
SMOKE_STDOUT="$ARTIFACT_DIR/smoke.stdout.txt"
mkdir -p "$SMOKE_DIR"
smoke_exit_code=0
if OPENCLAW_ANDROID_LOCAL_HOST_TOKEN="$token" \
  OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR="$SMOKE_DIR" \
  bash "$SMOKE_SCRIPT" >"$SMOKE_STDOUT" 2>&1; then
  smoke_exit_code=0
else
  smoke_exit_code=$?
fi

SMOKE_SUMMARY="$SMOKE_DIR/summary.json"
if [[ ! -f "$SMOKE_SUMMARY" ]]; then
  echo "Missing smoke summary: $SMOKE_SUMMARY" >&2
  cat "$SMOKE_STDOUT" >&2 || true
  exit 1
fi

smoke_ok="$(jq -r '.ok // false' "$SMOKE_SUMMARY")"
smoke_error_class="$(jq -r '.chat.errorClass // ""' "$SMOKE_SUMMARY")"
smoke_failed_checks="$(jq -r '[.failedChecks[]?] | join(",")' "$SMOKE_SUMMARY")"

network_probe_executed=false
network_probe_exit_code=0
network_classification=""
recommended_action=""
recommended_command=""
NETWORK_DIR="$ARTIFACT_DIR/openai-network"
NETWORK_STDOUT="$ARTIFACT_DIR/openai-network.stdout.txt"
NETWORK_SUMMARY="$NETWORK_DIR/summary.json"

if [[ "$smoke_error_class" == "openai_connect_timeout" ]]; then
  mkdir -p "$NETWORK_DIR"
  if OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR="$NETWORK_DIR" \
    bash "$OPENAI_NETWORK_SCRIPT" >"$NETWORK_STDOUT" 2>&1; then
    network_probe_executed=true
    network_probe_exit_code=0
  else
    network_probe_executed=true
    network_probe_exit_code=$?
  fi

  if [[ -f "$NETWORK_SUMMARY" ]]; then
    network_classification="$(jq -r '.classification // ""' "$NETWORK_SUMMARY")"
    recommended_action="$(jq -r '.recommendedAction // ""' "$NETWORK_SUMMARY")"
    recommended_command="$(jq -r '.recommendedCommand // ""' "$NETWORK_SUMMARY")"
  fi
fi

classification="local_host_unhealthy"
if [[ "$smoke_ok" == "true" ]]; then
  classification="local_host_healthy"
  recommended_action="none"
  recommended_command=""
elif [[ -n "$network_classification" ]]; then
  classification="$network_classification"
elif [[ -n "$smoke_error_class" ]]; then
  classification="$smoke_error_class"
  recommended_action="inspect-smoke-summary"
  recommended_command="cat \"$SMOKE_SUMMARY\""
else
  recommended_action="inspect-smoke-summary"
  recommended_command="cat \"$SMOKE_SUMMARY\""
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
  --arg smokeStdoutPath "$SMOKE_STDOUT"
  --arg smokeSummaryPath "$SMOKE_SUMMARY"
  --arg networkStdoutPath "$NETWORK_STDOUT"
  --arg networkSummaryPath "$NETWORK_SUMMARY"
  --arg smokeFailedChecks "$smoke_failed_checks"
  --arg smokeErrorClass "$smoke_error_class"
  --argjson smokeExitCode "$smoke_exit_code"
  --argjson smokeOk "$(bool_json "$smoke_ok")"
  --argjson networkProbeExecuted "$(bool_json "$network_probe_executed")"
  --argjson networkProbeExitCode "$network_probe_exit_code"
  --slurpfile smoke "$SMOKE_SUMMARY"
)

if [[ -n "$token_json" ]]; then
  jq_args+=(--arg tokenJson "$token_json")
else
  jq_args+=(--arg tokenJson "")
fi

if [[ -f "$NETWORK_SUMMARY" ]]; then
  jq_args+=(--slurpfile network "$NETWORK_SUMMARY")
else
  jq_args+=(--argjson network null)
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
    smoke: {
      exitCode: $smokeExitCode,
      ok: $smokeOk,
      failedChecks: (
        if $smokeFailedChecks == "" then
          []
        else
          ($smokeFailedChecks | split(","))
        end
      ),
      errorClass: (if $smokeErrorClass == "" then null else $smokeErrorClass end),
      stdoutPath: $smokeStdoutPath,
      summaryPath: $smokeSummaryPath,
      summary: $smoke[0]
    },
    openaiNetwork: {
      executed: $networkProbeExecuted,
      exitCode: (if $networkProbeExecuted then $networkProbeExitCode else null end),
      stdoutPath: (if $networkProbeExecuted then $networkStdoutPath else null end),
      summaryPath: (
        if $networkProbeExecuted and $network != null then
          $networkSummaryPath
        else
          null
        end
      ),
      summary: (
        if $network == null then
          null
        else
          $network[0]
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

printf 'doctor.token_source=%s\n' "$token_source"
printf 'doctor.smoke.ok=%s exit_code=%s failed_checks=%s\n' "$smoke_ok" "$smoke_exit_code" "${smoke_failed_checks:-none}"
printf 'doctor.openai_network.executed=%s\n' "$network_probe_executed"
printf 'doctor.classification=%s\n' "$classification"
if [[ -n "$recommended_action" ]]; then
  printf 'doctor.recommended_action=%s\n' "$recommended_action"
fi
if [[ -n "$recommended_command" ]]; then
  printf 'doctor.recommended_command=%s\n' "$recommended_command"
fi
printf 'artifacts.summary=%s\n' "$SUMMARY_JSON"
