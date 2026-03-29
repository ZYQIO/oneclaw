#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
POST_PROVISION_SCRIPT="${OPENCLAW_ANDROID_LOCAL_HOST_DEDICATED_POST_PROVISION_SCRIPT:-$SCRIPT_DIR/local-host-dedicated-post-provision-check.sh}"
TESTDPC_QR_SCRIPT="${OPENCLAW_ANDROID_LOCAL_HOST_DEDICATED_TESTDPC_QR_SCRIPT:-$SCRIPT_DIR/local-host-dedicated-testdpc-qr.sh}"
TESTDPC_KIOSK_SCRIPT="${OPENCLAW_ANDROID_LOCAL_HOST_DEDICATED_TESTDPC_KIOSK_SCRIPT:-$SCRIPT_DIR/local-host-dedicated-testdpc-kiosk.sh}"
ARTIFACT_DIR="${OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR:-$(mktemp -d -t openclaw-android-dedicated-post-provision-next.XXXXXX)}"
POST_DIR="$ARTIFACT_DIR/post-provision"
NEXT_DIR="$ARTIFACT_DIR/next"
POST_STDOUT="$ARTIFACT_DIR/post-provision.stdout.txt"
NEXT_STDOUT="$ARTIFACT_DIR/next.stdout.txt"
SUMMARY_JSON="$ARTIFACT_DIR/summary.json"
JSON=false

usage() {
  cat <<'EOF'
Usage:
  ./apps/android/scripts/local-host-dedicated-post-provision-next.sh
  ./apps/android/scripts/local-host-dedicated-post-provision-next.sh --json

What it does:
  1. Runs the dedicated post-provision checker
  2. Reads recommendedAction / recommendedCommand from the summary
  3. Runs the safe next dry-run automatically
  4. Writes one combined summary for the post-provision state plus the next step

Notes:
  - The wrapper only runs dry-run-safe follow-up commands
  - Stateful flags like `--apply` still need to be run manually on the underlying helper
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

script_for_action() {
  case "${1:-}" in
    testdpc-qr)
      printf '%s\n' "$TESTDPC_QR_SCRIPT"
      ;;
    testdpc-kiosk)
      printf '%s\n' "$TESTDPC_KIOSK_SCRIPT"
      ;;
    launch-openclaw)
      printf '%s\n' "$POST_PROVISION_SCRIPT"
      ;;
    *)
      printf ''
      ;;
  esac
}

args_for_action() {
  case "${1:-}" in
    launch-openclaw)
      printf '%s\n' "--launch"
      ;;
    *)
      printf ''
      ;;
  esac
}

require_cmd bash
require_cmd jq

mkdir -p "$ARTIFACT_DIR" "$POST_DIR"

OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR="$POST_DIR" \
  bash "$POST_PROVISION_SCRIPT" >"$POST_STDOUT"

POST_SUMMARY="$POST_DIR/summary.json"
if [[ ! -f "$POST_SUMMARY" ]]; then
  echo "Missing post-provision summary: $POST_SUMMARY" >&2
  exit 1
fi

recommended_action="$(jq -r '.recommendedAction // ""' "$POST_SUMMARY")"
recommended_command="$(jq -r '.recommendedCommand // ""' "$POST_SUMMARY")"
next_script="$(script_for_action "$recommended_action")"
next_arg="$(args_for_action "$recommended_action")"
next_executed=false
next_summary_path=""

if [[ -n "$next_script" ]]; then
  mkdir -p "$NEXT_DIR"
  if [[ -n "$next_arg" ]]; then
    OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR="$NEXT_DIR" \
      bash "$next_script" "$next_arg" >"$NEXT_STDOUT"
  else
    OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR="$NEXT_DIR" \
      bash "$next_script" >"$NEXT_STDOUT"
  fi
  next_executed=true
  if [[ -f "$NEXT_DIR/summary.json" ]]; then
    next_summary_path="$NEXT_DIR/summary.json"
  fi
fi

jq_args=(
  -n
  --arg artifactDir "$ARTIFACT_DIR"
  --arg recommendedAction "$recommended_action"
  --arg recommendedCommand "$recommended_command"
  --arg nextScript "$next_script"
  --arg nextArg "$next_arg"
  --arg postStdoutPath "$POST_STDOUT"
  --arg nextStdoutPath "$NEXT_STDOUT"
  --arg nextSummaryPath "$next_summary_path"
  --argjson nextExecuted "$(bool_json "$next_executed")"
  --slurpfile postSummary "$POST_SUMMARY"
)

if [[ -n "$next_summary_path" ]]; then
  jq_args+=(--slurpfile nextSummary "$next_summary_path")
else
  jq_args+=(--argjson nextSummary null)
fi

jq "${jq_args[@]}" '
  {
    postProvision: $postSummary[0],
    next: {
      action: (if $recommendedAction == "" then null else $recommendedAction end),
      command: (if $recommendedCommand == "" then null else $recommendedCommand end),
      scriptPath: (if $nextScript == "" then null else $nextScript end),
      scriptArg: (if $nextArg == "" then null else $nextArg end),
      executed: $nextExecuted,
      stdoutPath: (if $nextExecuted then $nextStdoutPath else null end),
      summaryPath: (if $nextSummaryPath == "" then null else $nextSummaryPath end),
      summary: (
        if $nextSummary == null then
          null
        else
          $nextSummary[0]
        end
      )
    },
    artifacts: {
      rootDir: $artifactDir,
      postStdoutPath: $postStdoutPath
    }
  }
' >"$SUMMARY_JSON"

if [[ "$JSON" == "true" ]]; then
  cat "$SUMMARY_JSON"
  exit 0
fi

printf 'dedicated.post_provision_next.action=%s\n' "${recommended_action:-unknown}"
if [[ -n "$recommended_command" ]]; then
  printf 'dedicated.post_provision_next.command=%s\n' "$recommended_command"
fi
printf 'dedicated.post_provision_next.executed=%s\n' "$next_executed"
if [[ -n "$next_script" ]]; then
  printf 'dedicated.post_provision_next.script=%s\n' "$next_script"
fi
if [[ -n "$next_summary_path" ]]; then
  printf 'dedicated.post_provision_next.summary=%s\n' "$next_summary_path"
fi
printf 'artifacts.summary=%s\n' "$SUMMARY_JSON"
