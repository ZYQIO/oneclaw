#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${OPENCLAW_ANDROID_LOCAL_HOST_BASE_URL:-}"
TOKEN="${OPENCLAW_ANDROID_LOCAL_HOST_TOKEN:-}"
MESSAGE="${OPENCLAW_ANDROID_LOCAL_HOST_MESSAGE:-Reply with exactly 20 short numbered lines about why Android local-host streaming visibility matters.}"
WAIT_MS="${OPENCLAW_ANDROID_LOCAL_HOST_WAIT_MS:-30000}"
EVENT_WAIT_MS="${OPENCLAW_ANDROID_LOCAL_HOST_EVENT_WAIT_MS:-4000}"
THINKING="${OPENCLAW_ANDROID_LOCAL_HOST_THINKING:-low}"
USE_ADB_FORWARD="${OPENCLAW_ANDROID_LOCAL_HOST_USE_ADB_FORWARD:-0}"
ADB_BIN="${OPENCLAW_ANDROID_LOCAL_HOST_ADB_BIN:-adb}"
PORT="${OPENCLAW_ANDROID_LOCAL_HOST_PORT:-3945}"
ARTIFACT_DIR="${OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR:-$(mktemp -d -t openclaw-android-local-host-streaming.XXXXXX)}"

usage() {
  cat <<'EOF'
Usage:
  OPENCLAW_ANDROID_LOCAL_HOST_TOKEN=<token> \
  [OPENCLAW_ANDROID_LOCAL_HOST_BASE_URL=http://127.0.0.1:3945] \
  [OPENCLAW_ANDROID_LOCAL_HOST_USE_ADB_FORWARD=1] \
  [OPENCLAW_ANDROID_LOCAL_HOST_ADB_BIN=/path/to/adb] \
  [OPENCLAW_ANDROID_LOCAL_HOST_PORT=3945] \
  [OPENCLAW_ANDROID_LOCAL_HOST_MESSAGE="Reply with 20 short numbered lines"] \
  [OPENCLAW_ANDROID_LOCAL_HOST_WAIT_MS=30000] \
  [OPENCLAW_ANDROID_LOCAL_HOST_EVENT_WAIT_MS=4000] \
  [OPENCLAW_ANDROID_LOCAL_HOST_THINKING=low] \
  ./apps/android/scripts/local-host-streaming-smoke.sh

What it does:
  1. Optionally runs adb forward tcp:<port> tcp:<port>
  2. Calls /status
  3. Fast-forwards the /events cursor to the current tail
  4. Calls /chat/send with a long enough prompt
  5. Polls /events until the run reaches a terminal state
  6. Fails unless at least one chat delta arrives before final

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

has_cmd() {
  local name=$1
  if [[ "$name" == */* || "$name" == *:* ]]; then
    [[ -x "$name" || -f "$name" ]]
    return
  fi
  command -v "$name" >/dev/null 2>&1
}

require_cmd() {
  local name=$1
  if ! has_cmd "$name"; then
    echo "$name required but missing." >&2
    exit 1
  fi
}

require_cmd curl
require_cmd jq

if [[ "$USE_ADB_FORWARD" == "1" ]]; then
  require_cmd "$ADB_BIN"
  device_count="$("$ADB_BIN" devices | awk 'NR>1 && $2=="device" {c+=1} END {print c+0}')"
  if [[ "$device_count" -lt 1 ]]; then
    echo "No connected Android device (adb state=device)." >&2
    exit 1
  fi
  "$ADB_BIN" forward "tcp:$PORT" "tcp:$PORT" >/dev/null
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
send_json="$ARTIFACT_DIR/send.json"
events_jsonl="$ARTIFACT_DIR/events.jsonl"
summary_json="$ARTIFACT_DIR/summary.json"

get_json() {
  local url=$1
  curl --fail --silent --show-error \
    -H "$auth_header" \
    "$url"
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

bootstrap_cursor() {
  local cursor=0
  while true; do
    local response
    response="$(get_json "$BASE_URL/api/local-host/v1/events?cursor=$cursor&waitMs=0&limit=50")"
    local next_cursor
    next_cursor="$(jq -r '.nextCursor // 0' <<<"$response")"
    local has_more
    has_more="$(jq -r '.hasMore // false' <<<"$response")"
    cursor="$next_cursor"
    if [[ "$has_more" != "true" ]]; then
      printf '%s' "$cursor"
      return 0
    fi
  done
}

echo "local_host.base_url=$BASE_URL"
echo "local_host.streaming_smoke=starting"

get_json "$BASE_URL/api/local-host/v1/status" | tee "$status_json" >/dev/null
status_ok="$(jq -r '.ok // false' "$status_json")"
status_mode="$(jq -r '.mode // ""' "$status_json")"
status_codex="$(jq -r '.host.codexAuthConfigured // false' "$status_json")"
if [[ "$status_ok" != "true" ]]; then
  echo "Remote /status returned ok=false." >&2
  cat "$status_json" >&2
  exit 1
fi

printf 'status.ok=%s mode=%s codex=%s\n' "$status_ok" "$status_mode" "$status_codex"

cursor="$(bootstrap_cursor)"
echo "events.cursor.start=$cursor"

send_body="$(jq -cn --arg message "$MESSAGE" --arg thinking "$THINKING" '{message:$message, thinking:$thinking}')"
post_json "$BASE_URL/api/local-host/v1/chat/send" "$send_body" | tee "$send_json" >/dev/null

run_id="$(jq -r '.runId // ""' "$send_json")"
if [[ -z "$run_id" ]]; then
  echo "chat.send did not return a runId." >&2
  cat "$send_json" >&2
  exit 1
fi

echo "chat.run_id=$run_id"

delta_count=0
assistant_event_count=0
terminal_state=""
terminal_text=""
first_delta_preview=""
round_limit=$(( (WAIT_MS + EVENT_WAIT_MS - 1) / EVENT_WAIT_MS + 1 ))

for ((round = 1; round <= round_limit; round++)); do
  response="$(get_json "$BASE_URL/api/local-host/v1/events?cursor=$cursor&waitMs=$EVENT_WAIT_MS&limit=50")"
  printf '%s\n' "$response" >>"$events_jsonl"
  cursor="$(jq -r '.nextCursor // 0' <<<"$response")"

  while IFS=$'\t' read -r event_id event_name state stream text; do
    [[ -n "$event_id" ]] || continue
    if [[ "$event_name" == "chat" && "$state" == "delta" ]]; then
      delta_count=$((delta_count + 1))
      if [[ -z "$first_delta_preview" && -n "$text" ]]; then
        first_delta_preview="$text"
      fi
    fi
    if [[ "$event_name" == "agent" && "$stream" == "assistant" ]]; then
      assistant_event_count=$((assistant_event_count + 1))
    fi
    if [[ "$event_name" == "chat" && ( "$state" == "final" || "$state" == "error" || "$state" == "aborted" ) ]]; then
      terminal_state="$state"
      terminal_text="$text"
      break
    fi
  done < <(
    jq -r --arg runId "$run_id" '
      .events[]
      | select((.payload.runId // "") == $runId)
      | [
          (.id | tostring),
          .event,
          (.payload.state // ""),
          (.payload.stream // ""),
          (.payload.message.content[0].text // .payload.data.text // "")
        ]
      | @tsv
    ' <<<"$response"
  )

  if [[ -n "$terminal_state" ]]; then
    break
  fi
done

jq -n \
  --arg runId "$run_id" \
  --arg terminalState "$terminal_state" \
  --arg terminalText "$terminal_text" \
  --arg firstDeltaPreview "$first_delta_preview" \
  --argjson deltaCount "$delta_count" \
  --argjson assistantEventCount "$assistant_event_count" \
  --argjson waitMs "$WAIT_MS" \
  '{
    runId: $runId,
    deltaCount: $deltaCount,
    assistantEventCount: $assistantEventCount,
    terminalState: $terminalState,
    terminalText: $terminalText,
    firstDeltaPreview: $firstDeltaPreview,
    waitMs: $waitMs
  }' >"$summary_json"

printf 'streaming.delta_count=%s assistant_events=%s terminal_state=%s\n' \
  "$delta_count" "$assistant_event_count" "${terminal_state:-missing}"
if [[ -n "$first_delta_preview" ]]; then
  echo "streaming.first_delta_preview=$first_delta_preview"
fi
if [[ -n "$terminal_text" ]]; then
  echo "streaming.final_preview=$terminal_text"
fi

if [[ -z "$terminal_state" ]]; then
  echo "Timed out waiting for a terminal chat event." >&2
  jq '.' "$summary_json" >&2
  exit 1
fi

if [[ "$terminal_state" != "final" ]]; then
  echo "Run ended in non-final state: $terminal_state" >&2
  jq '.' "$summary_json" >&2
  exit 1
fi

if [[ "$delta_count" -lt 1 ]]; then
  echo "No chat delta events were observed before final." >&2
  jq '.' "$summary_json" >&2
  exit 1
fi

echo "local_host.streaming_smoke=completed"
echo "artifacts.dir=$ARTIFACT_DIR"
echo "artifacts.status=$status_json"
echo "artifacts.send=$send_json"
echo "artifacts.events=$events_jsonl"
echo "artifacts.summary=$summary_json"
