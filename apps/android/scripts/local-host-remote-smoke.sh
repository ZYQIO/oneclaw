#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"

BASE_URL="${OPENCLAW_ANDROID_LOCAL_HOST_BASE_URL:-}"
TOKEN="${OPENCLAW_ANDROID_LOCAL_HOST_TOKEN:-}"
MESSAGE="${OPENCLAW_ANDROID_LOCAL_HOST_MESSAGE:-Summarize the current device status in one sentence.}"
WAIT_MS="${OPENCLAW_ANDROID_LOCAL_HOST_WAIT_MS:-30000}"
INVOKE_COMMAND="${OPENCLAW_ANDROID_LOCAL_HOST_INVOKE_COMMAND:-device.status}"
INVOKE_PARAMS="${OPENCLAW_ANDROID_LOCAL_HOST_INVOKE_PARAMS:-}"
USE_ADB_FORWARD="${OPENCLAW_ANDROID_LOCAL_HOST_USE_ADB_FORWARD:-0}"
PORT="${OPENCLAW_ANDROID_LOCAL_HOST_PORT:-3945}"
ARTIFACT_DIR="${OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR:-$(mktemp -d -t openclaw-android-local-host.XXXXXX)}"

usage() {
  cat <<'EOF'
Usage:
  OPENCLAW_ANDROID_LOCAL_HOST_TOKEN=<token> \
  [OPENCLAW_ANDROID_LOCAL_HOST_BASE_URL=http://127.0.0.1:3945] \
  [OPENCLAW_ANDROID_LOCAL_HOST_USE_ADB_FORWARD=1] \
  [OPENCLAW_ANDROID_LOCAL_HOST_PORT=3945] \
  [OPENCLAW_ANDROID_LOCAL_HOST_MESSAGE="Hello"] \
  [OPENCLAW_ANDROID_LOCAL_HOST_INVOKE_COMMAND=device.status] \
  [OPENCLAW_ANDROID_LOCAL_HOST_INVOKE_PARAMS='{"includePermissions":true}'] \
  ./apps/android/scripts/local-host-remote-smoke.sh

What it does:
  1. Optionally runs adb forward tcp:<port> tcp:<port>
  2. Calls /status and prints a compact readiness summary
  3. Calls /chat/send-wait
  4. Calls /invoke/capabilities
  5. Calls /invoke with the configured command

Requirements:
  - curl
  - jq
  - adb (only when OPENCLAW_ANDROID_LOCAL_HOST_USE_ADB_FORWARD=1)
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "curl required but missing." >&2
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "jq required but missing." >&2
  exit 1
fi

if [[ "$USE_ADB_FORWARD" == "1" ]]; then
  if ! command -v adb >/dev/null 2>&1; then
    echo "adb required when OPENCLAW_ANDROID_LOCAL_HOST_USE_ADB_FORWARD=1." >&2
    exit 1
  fi
  device_count="$(adb devices | awk 'NR>1 && $2=="device" {c+=1} END {print c+0}')"
  if [[ "$device_count" -lt 1 ]]; then
    echo "No connected Android device (adb state=device)." >&2
    exit 1
  fi
  adb forward "tcp:$PORT" "tcp:$PORT" >/dev/null
  if [[ -z "$BASE_URL" ]]; then
    BASE_URL="http://127.0.0.1:$PORT"
  fi
fi

if [[ -z "$BASE_URL" ]]; then
  BASE_URL="http://127.0.0.1:$PORT"
fi

BASE_URL="${BASE_URL%/}"

if [[ -z "$TOKEN" ]]; then
  echo "OPENCLAW_ANDROID_LOCAL_HOST_TOKEN is required." >&2
  usage >&2
  exit 1
fi

auth_header="Authorization: Bearer $TOKEN"
mkdir -p "$ARTIFACT_DIR"
status_json="$ARTIFACT_DIR/status.json"
chat_json="$ARTIFACT_DIR/chat.json"
caps_json="$ARTIFACT_DIR/capabilities.json"
invoke_json="$ARTIFACT_DIR/invoke.json"
summary_json="$ARTIFACT_DIR/summary.json"

smoke_failed=0
failed_checks=()
chat_error_message=""
chat_error_class=""
chat_error_host=""
chat_error_address=""
chat_error_address_family=""
chat_hint=""

record_failure() {
  local check=$1
  smoke_failed=1
  failed_checks+=("$check")
}

detect_address_family() {
  local address=$1
  if [[ -z "$address" ]]; then
    return 0
  fi
  if [[ "$address" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    printf 'ipv4'
    return 0
  fi
  if [[ "$address" == *:* ]]; then
    printf 'ipv6'
  fi
}

classify_chat_error() {
  local chat_ok=$1
  local chat_timed_out=$2
  local chat_state=$3
  local error_message=$4

  chat_error_message="$error_message"
  chat_error_class=""
  chat_error_host=""
  chat_error_address=""
  chat_error_address_family=""
  chat_hint=""

  if [[ "$error_message" =~ failed\ to\ connect\ to\ ([^/[:space:]]+)/([^[:space:]]+) ]]; then
    chat_error_host="${BASH_REMATCH[1]}"
    chat_error_address="${BASH_REMATCH[2]}"
    chat_error_address="${chat_error_address#[}"
    chat_error_address="${chat_error_address%]}"
    chat_error_address_family="$(detect_address_family "$chat_error_address")"
  fi

  if [[ "$chat_timed_out" == "true" ]]; then
    chat_error_class="local_host_wait_timeout"
    chat_hint="Increase OPENCLAW_ANDROID_LOCAL_HOST_WAIT_MS or inspect device-side Codex connectivity before retrying."
    return 0
  fi

  if [[ "$chat_state" == "final" && "$chat_ok" == "true" ]]; then
    return 0
  fi

  if [[ "$error_message" == *"usage_limit_reached"* || "$error_message" == *"429"* || "$error_message" == *"rate limit"* ]]; then
    chat_error_class="openai_usage_limit"
    chat_hint="The phone reached the local host successfully, but the OpenAI/Codex account hit a usage limit."
    return 0
  fi

  if [[ "$error_message" == *"Unable to resolve host"* || "$error_message" == *"No address associated with hostname"* || "$error_message" == *"hostname not found"* ]]; then
    chat_error_class="openai_dns_error"
    chat_hint="The phone could not resolve the OpenAI host name. Check DNS, VPN, or captive-portal state."
    return 0
  fi

  if [[ "$error_message" == *"SSLHandshakeException"* || "$error_message" == *"handshake"* || "$error_message" == *"certificate"* ]]; then
    chat_error_class="openai_tls_error"
    chat_hint="The phone reached the OpenAI host but TLS negotiation failed. Check intercepting proxies or network filtering."
    return 0
  fi

  if [[ "$error_message" == *"failed to connect to"* && "$error_message" == *"after"* && "$error_message" == *"ms"* ]]; then
    chat_error_class="openai_connect_timeout"
    if [[ "$chat_error_address_family" == "ipv6" ]]; then
      chat_hint="The phone reached local-host successfully, but outbound Codex traffic timed out on an IPv6 path. Check IPv6 reachability, VPN/proxy behavior, or force a working IPv4 route before retrying."
    else
      chat_hint="The phone reached local-host successfully, but outbound Codex traffic timed out before OpenAI responded. Check network reachability, VPN/proxy behavior, or firewall rules."
    fi
    return 0
  fi

  if [[ "$error_message" == *"401"* || "$error_message" == *"403"* || "$error_message" == *"unauthorized"* || "$error_message" == *"invalid_grant"* || "$error_message" == *"invalid_token"* ]]; then
    chat_error_class="openai_auth_error"
    chat_hint="The local-host path is up, but the phone-side Codex credential is invalid or expired. Re-run auth refresh/sync."
    return 0
  fi

  case "$chat_state" in
    error)
      chat_error_class="chat_error"
      chat_hint="The local-host request reached the phone, but the chat run ended in error. Inspect chat.json for the upstream failure message."
      ;;
    aborted)
      chat_error_class="chat_aborted"
      chat_hint="The local-host request reached the phone, but the chat run was aborted before final output."
      ;;
    "")
      if [[ "$chat_ok" != "true" ]]; then
        chat_error_class="chat_not_ok"
        chat_hint="The local-host request returned ok=false without a terminal final state. Inspect chat.json for details."
      fi
      ;;
    *)
      chat_error_class="chat_non_final_state"
      chat_hint="The local-host request returned a non-final chat state. Inspect chat.json for the terminal payload."
      ;;
  esac
}

write_summary() {
  local failed_checks_json='[]'
  if [[ ${#failed_checks[@]} -gt 0 ]]; then
    failed_checks_json="$(printf '%s\n' "${failed_checks[@]}" | jq -R . | jq -s .)"
  fi

  jq -n \
    --arg baseUrl "$BASE_URL" \
    --arg smokeOk "$([[ "$smoke_failed" == "0" ]] && printf 'true' || printf 'false')" \
    --argjson failedChecks "$failed_checks_json" \
    --arg statusOk "$status_ok" \
    --arg statusMode "$status_mode" \
    --arg statusCodex "$status_codex" \
    --arg statusSessions "$status_sessions" \
    --arg statusRuns "$status_runs" \
    --arg statusAdvanced "$status_advanced" \
    --arg statusWrite "$status_write" \
    --arg chatOk "$chat_ok" \
    --arg chatTimedOut "$chat_timed_out" \
    --arg chatRunId "$chat_run_id" \
    --arg chatState "$chat_state" \
    --arg chatPreview "$chat_text" \
    --arg chatErrorMessage "$chat_error_message" \
    --arg chatErrorClass "$chat_error_class" \
    --arg chatErrorHost "$chat_error_host" \
    --arg chatErrorAddress "$chat_error_address" \
    --arg chatErrorAddressFamily "$chat_error_address_family" \
    --arg chatHint "$chat_hint" \
    --arg invokeCommand "$INVOKE_COMMAND" \
    --arg invokeOk "$invoke_ok" \
    --arg invokeError "$invoke_error" \
    '{
      ok: ($smokeOk == "true"),
      baseUrl: $baseUrl,
      failedChecks: $failedChecks,
      status: {
        ok: ($statusOk == "true"),
        mode: (if $statusMode == "" then null else $statusMode end),
        codexAuthConfigured: ($statusCodex == "true"),
        sessionCount: ($statusSessions | tonumber),
        activeRunCount: ($statusRuns | tonumber),
        advancedEnabled: ($statusAdvanced == "true"),
        writeEnabled: ($statusWrite == "true")
      },
      chat: {
        ok: ($chatOk == "true"),
        timedOut: ($chatTimedOut == "true"),
        runId: (if $chatRunId == "" then null else $chatRunId end),
        state: (if $chatState == "" then null else $chatState end),
        preview: (if $chatPreview == "" then null else $chatPreview end),
        errorMessage: (if $chatErrorMessage == "" then null else $chatErrorMessage end),
        errorClass: (if $chatErrorClass == "" then null else $chatErrorClass end),
        errorHost: (if $chatErrorHost == "" then null else $chatErrorHost end),
        errorAddress: (if $chatErrorAddress == "" then null else $chatErrorAddress end),
        errorAddressFamily: (if $chatErrorAddressFamily == "" then null else $chatErrorAddressFamily end),
        hint: (if $chatHint == "" then null else $chatHint end)
      },
      invoke: {
        command: $invokeCommand,
        ok: ($invokeOk == "true"),
        error: (if $invokeError == "" then null else $invokeError end)
      }
    }' >"$summary_json"
}

print_artifacts() {
  echo "artifacts.dir=$ARTIFACT_DIR"
  echo "artifacts.status=$status_json"
  echo "artifacts.chat=$chat_json"
  echo "artifacts.capabilities=$caps_json"
  echo "artifacts.invoke=$invoke_json"
  echo "artifacts.summary=$summary_json"
}

post_json() {
  local url=$1
  local body=$2
  curl --fail --silent --show-error \
    -H "$auth_header" \
    -H 'Content-Type: application/json' \
    -X POST \
    "$url" \
    -d "$body"
}

get_json() {
  local url=$1
  curl --fail --silent --show-error \
    -H "$auth_header" \
    "$url"
}

echo "local_host.base_url=$BASE_URL"
echo "local_host.smoke=starting"

get_json "$BASE_URL/api/local-host/v1/status" | tee "$status_json" >/dev/null

status_ok="$(jq -r '.ok // false' "$status_json")"
status_mode="$(jq -r '.mode // ""' "$status_json")"
status_codex="$(jq -r '.host.codexAuthConfigured // false' "$status_json")"
status_sessions="$(jq -r '.host.sessionCount // 0' "$status_json")"
status_runs="$(jq -r '.host.activeRunCount // 0' "$status_json")"
status_advanced="$(jq -r '.remoteAccess.advancedEnabled // false' "$status_json")"
status_write="$(jq -r '.remoteAccess.writeEnabled // false' "$status_json")"

if [[ "$status_ok" != "true" ]]; then
  echo "Remote /status returned ok=false." >&2
  cat "$status_json" >&2
  exit 1
fi

printf 'status.ok=%s mode=%s codex=%s sessions=%s active_runs=%s advanced=%s write=%s\n' \
  "$status_ok" "$status_mode" "$status_codex" "$status_sessions" "$status_runs" "$status_advanced" "$status_write"

get_json "$BASE_URL/api/local-host/v1/invoke/capabilities" | tee "$caps_json" >/dev/null

if ! jq -e --arg cmd "$INVOKE_COMMAND" '.commands | index($cmd)' "$caps_json" >/dev/null; then
  echo "Configured invoke command is not currently allowed: $INVOKE_COMMAND" >&2
  jq '.' "$caps_json" >&2
  exit 1
fi

echo "invoke.command=$INVOKE_COMMAND allowed=true"

chat_body="$(jq -cn --arg message "$MESSAGE" --argjson waitMs "$WAIT_MS" '{message:$message, waitMs:$waitMs}')"
post_json "$BASE_URL/api/local-host/v1/chat/send-wait" "$chat_body" | tee "$chat_json" >/dev/null

chat_ok="$(jq -r '.ok // false' "$chat_json")"
chat_timed_out="$(jq -r '.timedOut // false' "$chat_json")"
chat_run_id="$(jq -r '.runId // ""' "$chat_json")"
chat_state="$(jq -r '.payload.state // .state // ""' "$chat_json")"
chat_text="$(jq -r '.payload.message.content[0].text // ""' "$chat_json")"
chat_error_message="$(jq -r '.payload.errorMessage // .error.message // ""' "$chat_json")"
classify_chat_error "$chat_ok" "$chat_timed_out" "$chat_state" "$chat_error_message"

printf 'chat.ok=%s timed_out=%s run_id=%s state=%s\n' "$chat_ok" "$chat_timed_out" "$chat_run_id" "$chat_state"
if [[ -n "$chat_text" ]]; then
  echo "chat.preview=$chat_text"
fi
if [[ -n "$chat_error_message" ]]; then
  echo "chat.error=$chat_error_message"
fi
if [[ -n "$chat_error_class" ]]; then
  echo "chat.error_class=$chat_error_class"
fi
if [[ -n "$chat_error_host" ]]; then
  echo "chat.error_host=$chat_error_host"
fi
if [[ -n "$chat_error_address_family" ]]; then
  echo "chat.error_address_family=$chat_error_address_family"
fi
if [[ -n "$chat_hint" ]]; then
  echo "chat.hint=$chat_hint"
fi
if [[ "$chat_ok" != "true" || "$chat_timed_out" == "true" || "$chat_state" != "final" ]]; then
  record_failure "chat"
fi

invoke_body="$(jq -cn --arg command "$INVOKE_COMMAND" --arg params "$INVOKE_PARAMS" '
  if ($params | length) > 0 then
    {command:$command, params:($params | fromjson)}
  else
    {command:$command}
  end
')"

post_json "$BASE_URL/api/local-host/v1/invoke" "$invoke_body" | tee "$invoke_json" >/dev/null

invoke_ok="$(jq -r '.ok // false' "$invoke_json")"
invoke_error="$(jq -r '.error.message // ""' "$invoke_json")"

printf 'invoke.ok=%s command=%s\n' "$invoke_ok" "$INVOKE_COMMAND"
if [[ -n "$invoke_error" ]]; then
  echo "invoke.error=$invoke_error"
fi
if [[ "$invoke_ok" != "true" ]]; then
  record_failure "invoke"
fi

write_summary

if [[ "$smoke_failed" == "1" ]]; then
  failed_checks_csv="$(IFS=,; echo "${failed_checks[*]}")"
  echo "local_host.smoke=failed"
  echo "smoke.failed_checks=$failed_checks_csv"
  print_artifacts
  jq '.' "$summary_json" >&2
  exit 1
fi

echo "local_host.smoke=completed"
print_artifacts
