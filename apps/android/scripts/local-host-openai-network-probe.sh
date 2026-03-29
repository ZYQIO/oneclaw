#!/usr/bin/env bash
set -euo pipefail

ADB_BIN="${OPENCLAW_ANDROID_LOCAL_HOST_ADB_BIN:-adb}"
HOSTS="${OPENCLAW_ANDROID_LOCAL_HOST_OPENAI_NETWORK_HOSTS:-chatgpt.com auth.openai.com}"
PORT="${OPENCLAW_ANDROID_LOCAL_HOST_OPENAI_NETWORK_PORT:-443}"
TIMEOUT_SEC="${OPENCLAW_ANDROID_LOCAL_HOST_OPENAI_NETWORK_TIMEOUT_SEC:-5}"
ARTIFACT_DIR="${OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR:-$(mktemp -d -t openclaw-android-openai-network.XXXXXX)}"

usage() {
  cat <<'EOF'
Usage:
  [OPENCLAW_ANDROID_LOCAL_HOST_ADB_BIN=adb] \
  [OPENCLAW_ANDROID_LOCAL_HOST_OPENAI_NETWORK_HOSTS="chatgpt.com auth.openai.com"] \
  [OPENCLAW_ANDROID_LOCAL_HOST_OPENAI_NETWORK_PORT=443] \
  [OPENCLAW_ANDROID_LOCAL_HOST_OPENAI_NETWORK_TIMEOUT_SEC=5] \
  ./apps/android/scripts/local-host-openai-network-probe.sh

What it does:
  1. Verifies adb sees at least one connected Android device
  2. Captures device getprop lines related to DNS/httpdns tuning
  3. Probes TCP 443 reachability from the phone itself with toybox netcat
  4. Tests each configured host over both IPv4 and IPv6
  5. Writes a summary.json with classification plus next-step hints

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
  if ! command -v "$name" >/dev/null 2>&1; then
    echo "$name required but missing." >&2
    exit 1
  fi
}

require_cmd "$ADB_BIN"
require_cmd jq

device_count="$("$ADB_BIN" devices | awk 'NR>1 && $2=="device" {c+=1} END {print c+0}')"
if [[ "$device_count" -lt 1 ]]; then
  echo "No connected Android device (adb state=device)." >&2
  exit 1
fi

mkdir -p "$ARTIFACT_DIR"
dns_props_txt="$ARTIFACT_DIR/device-dns-props.txt"
results_jsonl="$ARTIFACT_DIR/results.jsonl"
summary_json="$ARTIFACT_DIR/summary.json"
: >"$results_jsonl"

trim_cr() {
  tr -d '\r'
}

classify_probe_status() {
  local exit_code=$1
  local output=$2
  if [[ "$exit_code" -eq 0 ]]; then
    printf 'reachable'
    return 0
  fi
  if [[ "$output" == *"Timeout"* ]]; then
    printf 'timeout'
    return 0
  fi
  if [[ "$output" == *"bad address"* || "$output" == *"No address associated with hostname"* || "$output" == *"Name or service not known"* || "$output" == *"Temporary failure in name resolution"* ]]; then
    printf 'dns_error'
    return 0
  fi
  printf 'error'
}

probe_host_family() {
  local host=$1
  local family_flag=$2
  local family_name=$3
  local output
  local exit_code
  if output="$("$ADB_BIN" shell toybox nc "$family_flag" -z -w "$TIMEOUT_SEC" "$host" "$PORT" 2>&1 | trim_cr)"; then
    exit_code=0
  else
    exit_code=$?
  fi
  local status
  status="$(classify_probe_status "$exit_code" "$output")"
  local reachable=false
  if [[ "$status" == "reachable" ]]; then
    reachable=true
  fi
  jq -nc \
    --arg host "$host" \
    --arg family "$family_name" \
    --arg status "$status" \
    --arg output "$output" \
    --argjson reachable "$reachable" \
    '{
      host: $host,
      family: $family,
      reachable: $reachable,
      status: $status,
      output: (if $output == "" then null else $output end)
    }' >>"$results_jsonl"

  printf 'probe.host=%s family=%s reachable=%s status=%s\n' "$host" "$family_name" "$reachable" "$status"
}

echo "openai_network.device_count=$device_count"
echo "openai_network.probe=starting"

"$ADB_BIN" shell getprop | trim_cr | grep -E 'dns|httpdns|v4priority' >"$dns_props_txt" || true

read -r -a host_array <<<"$HOSTS"
for host in "${host_array[@]}"; do
  probe_host_family "$host" "-4" "ipv4"
  probe_host_family "$host" "-6" "ipv6"
done

host_any_reachable() {
  local host=$1
  jq -s --arg host "$host" 'map(select(.host == $host and .reachable == true)) | length > 0' "$results_jsonl"
}

host_family_reachable() {
  local host=$1
  local family=$2
  jq -r -s --arg host "$host" --arg family "$family" '
    map(select(.host == $host and .family == $family) | .reachable)
    | if length == 0 then "false" else (.[0] | tostring) end
  ' "$results_jsonl"
}

chatgpt_any="$(host_any_reachable "chatgpt.com")"
auth_any="$(host_any_reachable "auth.openai.com")"
chatgpt_ipv4="$(host_family_reachable "chatgpt.com" "ipv4")"
chatgpt_ipv6="$(host_family_reachable "chatgpt.com" "ipv6")"
auth_ipv4="$(host_family_reachable "auth.openai.com" "ipv4")"
auth_ipv6="$(host_family_reachable "auth.openai.com" "ipv6")"

classification="partial_network_state"
recommended_action="inspect-summary"
recommended_command="cat \"$summary_json\""

if [[ "$chatgpt_any" == "false" && "$auth_any" == "true" ]]; then
  classification="responses_host_unreachable"
  recommended_action="check-chatgpt-path"
  recommended_command="adb shell 'toybox nc -4 -z -w $TIMEOUT_SEC chatgpt.com $PORT; toybox nc -6 -z -w $TIMEOUT_SEC chatgpt.com $PORT'"
elif [[ "$chatgpt_any" == "true" && "$auth_any" == "false" ]]; then
  classification="auth_host_unreachable"
  recommended_action="check-auth-path"
  recommended_command="adb shell 'toybox nc -4 -z -w $TIMEOUT_SEC auth.openai.com $PORT; toybox nc -6 -z -w $TIMEOUT_SEC auth.openai.com $PORT'"
elif [[ "$chatgpt_any" == "false" && "$auth_any" == "false" ]]; then
  classification="openai_hosts_unreachable"
  recommended_action="check-device-network"
  recommended_command="adb shell 'getprop | grep -E \"dns|httpdns|v4priority\"'"
elif [[ "$chatgpt_ipv4" == "true" && "$chatgpt_ipv6" == "false" ]]; then
  classification="responses_ipv6_unreachable"
  recommended_action="prefer-ipv4-or-fix-ipv6"
  recommended_command="pnpm android:local-host:smoke"
elif [[ "$chatgpt_ipv4" == "false" && "$chatgpt_ipv6" == "true" ]]; then
  classification="responses_ipv4_unreachable"
  recommended_action="check-ipv4-path"
  recommended_command="pnpm android:local-host:smoke"
elif [[ "$chatgpt_any" == "true" && "$auth_any" == "true" && "$auth_ipv4" == "true" && "$auth_ipv6" == "true" ]]; then
  classification="all_hosts_reachable"
  recommended_action="rerun-smoke"
  recommended_command="pnpm android:local-host:smoke"
fi

jq -n \
  --arg classification "$classification" \
  --arg recommendedAction "$recommended_action" \
  --arg recommendedCommand "$recommended_command" \
  --arg port "$PORT" \
  --argjson timeoutSec "$TIMEOUT_SEC" \
  --slurpfile probes "$results_jsonl" \
  --rawfile dnsProps "$dns_props_txt" \
  '{
    classification: $classification,
    recommendedAction: $recommendedAction,
    recommendedCommand: $recommendedCommand,
    port: ($port | tonumber),
    timeoutSec: $timeoutSec,
    probes: $probes,
    dnsProps: (
      $dnsProps
      | split("\n")
      | map(select(length > 0))
    )
  }' >"$summary_json"

echo "openai_network.classification=$classification"
echo "openai_network.recommended_action=$recommended_action"
echo "openai_network.recommended_command=$recommended_command"
echo "artifacts.dir=$ARTIFACT_DIR"
echo "artifacts.dns=$dns_props_txt"
echo "artifacts.results=$results_jsonl"
echo "artifacts.summary=$summary_json"
