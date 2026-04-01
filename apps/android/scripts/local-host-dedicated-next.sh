#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
READINESS_SCRIPT="${OPENCLAW_ANDROID_LOCAL_HOST_DEDICATED_READINESS_SCRIPT:-$SCRIPT_DIR/local-host-dedicated-readiness.sh}"
DEVICE_OWNER_SCRIPT="${OPENCLAW_ANDROID_LOCAL_HOST_DEDICATED_DEVICE_OWNER_SCRIPT:-$SCRIPT_DIR/local-host-dedicated-device-owner.sh}"
TESTDPC_INSTALL_SCRIPT="${OPENCLAW_ANDROID_LOCAL_HOST_DEDICATED_TESTDPC_INSTALL_SCRIPT:-$SCRIPT_DIR/local-host-dedicated-testdpc-install.sh}"
TESTDPC_QR_SCRIPT="${OPENCLAW_ANDROID_LOCAL_HOST_DEDICATED_TESTDPC_QR_SCRIPT:-$SCRIPT_DIR/local-host-dedicated-testdpc-qr.sh}"
POST_PROVISION_SCRIPT="${OPENCLAW_ANDROID_LOCAL_HOST_DEDICATED_POST_PROVISION_SCRIPT:-$SCRIPT_DIR/local-host-dedicated-post-provision-check.sh}"
ARTIFACT_DIR="${OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR:-$(mktemp -d -t openclaw-android-dedicated-next.XXXXXX)}"
READINESS_DIR="$ARTIFACT_DIR/readiness"
NEXT_DIR="$ARTIFACT_DIR/next"
READINESS_STDOUT="$ARTIFACT_DIR/readiness.stdout.txt"
NEXT_STDOUT="$ARTIFACT_DIR/next.stdout.txt"
SUMMARY_JSON="$ARTIFACT_DIR/summary.json"
JSON=false
DESCRIBE_ONLY=false
ASSUME_ACTION=""

usage() {
  cat <<'EOF'
Usage:
  ./apps/android/scripts/local-host-dedicated-next.sh
  ./apps/android/scripts/local-host-dedicated-next.sh --json
  ./apps/android/scripts/local-host-dedicated-next.sh --describe
  ./apps/android/scripts/local-host-dedicated-next.sh --describe --assume-action testdpc-install

What it does:
  1. Runs the dedicated readiness probe
  2. Reads recommendedAction / recommendedCommand from readiness
  3. Runs the recommended dedicated dry-run command automatically
  4. Writes a combined summary that includes both readiness and next-step results

Notes:
  - This wrapper keeps the recommended next step in dry-run mode
  - Stateful flags such as --apply still need to be run manually on the underlying command
  - Artifacts live under OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR or a temp directory
  - --describe stays offline and only previews the wrapper layout plus optional assumed next action
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
    --describe)
      DESCRIBE_ONLY=true
      shift
      ;;
    --assume-action)
      if [[ $# -lt 2 ]]; then
        echo "--assume-action requires a value." >&2
        exit 1
      fi
      ASSUME_ACTION=$2
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

command_for_action() {
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

script_for_action() {
  case "${1:-}" in
    device-owner)
      printf '%s\n' "$DEVICE_OWNER_SCRIPT"
      ;;
    testdpc-install)
      printf '%s\n' "$TESTDPC_INSTALL_SCRIPT"
      ;;
    testdpc-qr)
      printf '%s\n' "$TESTDPC_QR_SCRIPT"
      ;;
    post-provision)
      printf '%s\n' "$POST_PROVISION_SCRIPT"
      ;;
    *)
      printf ''
      ;;
  esac
}

require_cmd bash
require_cmd jq

if [[ -n "$ASSUME_ACTION" ]]; then
  if [[ "$DESCRIBE_ONLY" != "true" ]]; then
    echo "--assume-action is only supported together with --describe." >&2
    exit 1
  fi
  if [[ -z "$(script_for_action "$ASSUME_ACTION")" ]]; then
    echo "Unsupported assumed action: $ASSUME_ACTION" >&2
    exit 1
  fi
fi

if [[ "$DESCRIBE_ONLY" == "true" ]]; then
  jq -n \
    --arg script "local-host-dedicated-next.sh" \
    --arg command "./apps/android/scripts/local-host-dedicated-next.sh" \
    --arg packageCommand "pnpm android:local-host:dedicated:next" \
    --arg artifactRoot "$ARTIFACT_DIR" \
    --arg summaryPath "$SUMMARY_JSON" \
    --arg readinessScript "$READINESS_SCRIPT" \
    --arg readinessPackageCommand "pnpm android:local-host:dedicated:readiness" \
    --arg readinessSummaryPath "$READINESS_DIR/summary.json" \
    --arg readinessStdoutPath "$READINESS_STDOUT" \
    --arg nextDir "$NEXT_DIR" \
    --arg nextSummaryPath "$NEXT_DIR/summary.json" \
    --arg nextStdoutPath "$NEXT_STDOUT" \
    --arg assumedAction "$ASSUME_ACTION" \
    --arg assumedCommand "$(command_for_action "$ASSUME_ACTION")" \
    --arg assumedScript "$(script_for_action "$ASSUME_ACTION")" \
    --arg deviceOwnerScript "$DEVICE_OWNER_SCRIPT" \
    --arg testdpcInstallScript "$TESTDPC_INSTALL_SCRIPT" \
    --arg testdpcQrScript "$TESTDPC_QR_SCRIPT" \
    --arg postProvisionScript "$POST_PROVISION_SCRIPT" \
    '{
      script: $script,
      command: $command,
      packageCommand: $packageCommand,
      mode: "readiness-next",
      artifactRoot: $artifactRoot,
      summaryPath: $summaryPath,
      describeOnly: true,
      note: "Actual recommendedAction and recommendedCommand come from the readiness summary at runtime.",
      readiness: {
        packageCommand: $readinessPackageCommand,
        scriptPath: $readinessScript,
        summaryPath: $readinessSummaryPath,
        stdoutPath: $readinessStdoutPath
      },
      next: {
        assumedAction: (if $assumedAction == "" then null else $assumedAction end),
        assumedCommand: (if $assumedCommand == "" then null else $assumedCommand end),
        scriptPath: (if $assumedScript == "" then null else $assumedScript end),
        scriptArg: null,
        summaryPath: $nextSummaryPath,
        stdoutPath: $nextStdoutPath
      },
      actionMap: {
        "device-owner": {
          packageCommand: "pnpm android:local-host:dedicated:device-owner",
          scriptPath: $deviceOwnerScript
        },
        "testdpc-install": {
          packageCommand: "pnpm android:local-host:dedicated:testdpc-install",
          scriptPath: $testdpcInstallScript
        },
        "testdpc-qr": {
          packageCommand: "pnpm android:local-host:dedicated:testdpc-qr",
          scriptPath: $testdpcQrScript
        },
        "post-provision": {
          packageCommand: "pnpm android:local-host:dedicated:post-provision",
          scriptPath: $postProvisionScript
        }
      },
      artifacts: {
        readinessDir: "readiness",
        nextDir: "next"
      }
    }'
  exit 0
fi

mkdir -p "$ARTIFACT_DIR" "$READINESS_DIR"

OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR="$READINESS_DIR" \
  bash "$READINESS_SCRIPT" >"$READINESS_STDOUT"

READINESS_SUMMARY="$READINESS_DIR/summary.json"
if [[ ! -f "$READINESS_SUMMARY" ]]; then
  echo "Missing readiness summary: $READINESS_SUMMARY" >&2
  exit 1
fi

recommended_action="$(jq -r '.recommendedAction // ""' "$READINESS_SUMMARY")"
recommended_command="$(jq -r '.recommendedCommand // ""' "$READINESS_SUMMARY")"
next_script="$(script_for_action "$recommended_action")"
next_executed=false
next_summary_path=""

if [[ -n "$next_script" ]]; then
  mkdir -p "$NEXT_DIR"
  OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR="$NEXT_DIR" \
    bash "$next_script" >"$NEXT_STDOUT"
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
  --arg readinessStdoutPath "$READINESS_STDOUT"
  --arg nextStdoutPath "$NEXT_STDOUT"
  --arg nextSummaryPath "$next_summary_path"
  --argjson nextExecuted "$(bool_json "$next_executed")"
  --slurpfile readiness "$READINESS_SUMMARY"
)

if [[ -n "$next_summary_path" ]]; then
  jq_args+=(--slurpfile nextSummary "$next_summary_path")
else
  jq_args+=(--argjson nextSummary null)
fi

jq "${jq_args[@]}" '
  {
    script: "local-host-dedicated-next.sh",
    command: "./apps/android/scripts/local-host-dedicated-next.sh",
    packageCommand: "pnpm android:local-host:dedicated:next",
    mode: "readiness-next",
    artifactRoot: $artifactDir,
    describeOnly: false,
    readiness: $readiness[0],
    next: {
      action: (if $recommendedAction == "" then null else $recommendedAction end),
      command: (if $recommendedCommand == "" then null else $recommendedCommand end),
      scriptPath: (if $nextScript == "" then null else $nextScript end),
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
      readinessStdoutPath: $readinessStdoutPath
    }
  }
' >"$SUMMARY_JSON"

if [[ "$JSON" == "true" ]]; then
  cat "$SUMMARY_JSON"
  exit 0
fi

printf 'dedicated.next.action=%s\n' "${recommended_action:-unknown}"
if [[ -n "$recommended_command" ]]; then
  printf 'dedicated.next.command=%s\n' "$recommended_command"
fi
printf 'dedicated.next.executed=%s\n' "$next_executed"
if [[ -n "$next_script" ]]; then
  printf 'dedicated.next.script=%s\n' "$next_script"
fi
if [[ -n "$next_summary_path" ]]; then
  printf 'dedicated.next.summary=%s\n' "$next_summary_path"
fi
printf 'artifacts.summary=%s\n' "$SUMMARY_JSON"
