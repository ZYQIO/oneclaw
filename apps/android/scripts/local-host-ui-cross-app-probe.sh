#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${OPENCLAW_ANDROID_LOCAL_HOST_BASE_URL:-}"
TOKEN="${OPENCLAW_ANDROID_LOCAL_HOST_TOKEN:-}"
PORT="${OPENCLAW_ANDROID_LOCAL_HOST_PORT:-3945}"
APP_PACKAGE="${OPENCLAW_ANDROID_LOCAL_HOST_UI_APP_PACKAGE:-ai.openclaw.app}"
APP_COMPONENT="${OPENCLAW_ANDROID_LOCAL_HOST_UI_APP_COMPONENT:-ai.openclaw.app/.MainActivity}"
TARGET_PACKAGE="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PACKAGE:-com.android.settings}"
OBSERVE_WINDOW_MS="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_OBSERVE_WINDOW_MS:-5000}"
POLL_INTERVAL_MS="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_POLL_INTERVAL_MS:-500}"
REQUEST_TIMEOUT_SEC="${OPENCLAW_ANDROID_LOCAL_HOST_UI_REQUEST_TIMEOUT_SEC:-5}"
RECOVERY_WAIT_MS="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_RECOVERY_WAIT_MS:-1500}"
FOLLOW_UP_PRESET="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PRESET:-}"
FOLLOW_UP_WAIT_TEXT="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_WAIT_TEXT:-}"
FOLLOW_UP_WAIT_MATCH_MODE="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_WAIT_MATCH_MODE:-contains}"
FOLLOW_UP_WAIT_TIMEOUT_MS="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_WAIT_TIMEOUT_MS:-10000}"
FOLLOW_UP_WAIT_POLL_INTERVAL_MS="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_WAIT_POLL_INTERVAL_MS:-250}"
FOLLOW_UP_TAP_TEXT="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_TAP_TEXT:-}"
FOLLOW_UP_TAP_CONTENT_DESCRIPTION="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_TAP_CONTENT_DESCRIPTION:-}"
FOLLOW_UP_TAP_RESOURCE_ID="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_TAP_RESOURCE_ID:-}"
FOLLOW_UP_TAP_MATCH_MODE="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_TAP_MATCH_MODE:-contains}"
FOLLOW_UP_TAP_INDEX="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_TAP_INDEX:-0}"
FOLLOW_UP_INPUT_VALUE="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_INPUT_VALUE:-}"
FOLLOW_UP_INPUT_TEXT="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_INPUT_TEXT:-}"
FOLLOW_UP_INPUT_CONTENT_DESCRIPTION="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_INPUT_CONTENT_DESCRIPTION:-}"
FOLLOW_UP_INPUT_RESOURCE_ID="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_INPUT_RESOURCE_ID:-}"
FOLLOW_UP_INPUT_MATCH_MODE="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_INPUT_MATCH_MODE:-contains}"
FOLLOW_UP_INPUT_INDEX="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_INPUT_INDEX:-0}"
FOLLOW_UP_SWIPE_START_X="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_START_X:-}"
FOLLOW_UP_SWIPE_START_Y="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_START_Y:-}"
FOLLOW_UP_SWIPE_END_X="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_END_X:-}"
FOLLOW_UP_SWIPE_END_Y="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_END_Y:-}"
FOLLOW_UP_SWIPE_START_X_RATIO="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_START_X_RATIO:-}"
FOLLOW_UP_SWIPE_START_Y_RATIO="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_START_Y_RATIO:-}"
FOLLOW_UP_SWIPE_END_X_RATIO="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_END_X_RATIO:-}"
FOLLOW_UP_SWIPE_END_Y_RATIO="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_END_Y_RATIO:-}"
FOLLOW_UP_SWIPE_DURATION_MS="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_DURATION_MS:-250}"
FOLLOW_UP_FOREGROUND_TIMEOUT_MS="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_FOLLOW_UP_FOREGROUND_TIMEOUT_MS:-5000}"
FOLLOW_UP_FOREGROUND_POLL_INTERVAL_MS="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_FOLLOW_UP_FOREGROUND_POLL_INTERVAL_MS:-250}"
FOLLOW_UP_SETTLE_MS="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_FOLLOW_UP_SETTLE_MS:-1000}"
RESET_TARGET_BEFORE_LAUNCH="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_FORCE_STOP_TARGET_BEFORE_LAUNCH:-false}"
ARTIFACT_DIR="${OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR:-$(mktemp -d -t openclaw-android-local-host-cross-app.XXXXXX)}"
DESCRIBE_ONLY=false

FOLLOW_UP_WAIT_TEXT_RAW="$FOLLOW_UP_WAIT_TEXT"
FOLLOW_UP_TAP_TEXT_RAW="$FOLLOW_UP_TAP_TEXT"
FOLLOW_UP_TAP_CONTENT_DESCRIPTION_RAW="$FOLLOW_UP_TAP_CONTENT_DESCRIPTION"
FOLLOW_UP_TAP_RESOURCE_ID_RAW="$FOLLOW_UP_TAP_RESOURCE_ID"
FOLLOW_UP_INPUT_VALUE_RAW="$FOLLOW_UP_INPUT_VALUE"
FOLLOW_UP_INPUT_TEXT_RAW="$FOLLOW_UP_INPUT_TEXT"
FOLLOW_UP_INPUT_CONTENT_DESCRIPTION_RAW="$FOLLOW_UP_INPUT_CONTENT_DESCRIPTION"
FOLLOW_UP_INPUT_RESOURCE_ID_RAW="$FOLLOW_UP_INPUT_RESOURCE_ID"
FOLLOW_UP_SWIPE_START_X_RAW="$FOLLOW_UP_SWIPE_START_X"
FOLLOW_UP_SWIPE_START_Y_RAW="$FOLLOW_UP_SWIPE_START_Y"
FOLLOW_UP_SWIPE_END_X_RAW="$FOLLOW_UP_SWIPE_END_X"
FOLLOW_UP_SWIPE_END_Y_RAW="$FOLLOW_UP_SWIPE_END_Y"
FOLLOW_UP_SWIPE_START_X_RATIO_RAW="$FOLLOW_UP_SWIPE_START_X_RATIO"
FOLLOW_UP_SWIPE_START_Y_RATIO_RAW="$FOLLOW_UP_SWIPE_START_Y_RATIO"
FOLLOW_UP_SWIPE_END_X_RATIO_RAW="$FOLLOW_UP_SWIPE_END_X_RATIO"
FOLLOW_UP_SWIPE_END_Y_RATIO_RAW="$FOLLOW_UP_SWIPE_END_Y_RATIO"
FOLLOW_UP_SWIPE_DURATION_MS_RAW="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_DURATION_MS:-}"

usage() {
  cat <<'EOF'
Usage:
  OPENCLAW_ANDROID_LOCAL_HOST_TOKEN=<token> \
  [OPENCLAW_ANDROID_LOCAL_HOST_PORT=3945] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PACKAGE=com.android.settings] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_OBSERVE_WINDOW_MS=5000] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_POLL_INTERVAL_MS=500] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PRESET=settings-search-input] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_WAIT_TEXT="Settings"] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_TAP_RESOURCE_ID="com.example:id/search"] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_INPUT_VALUE="openclaw"] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_INPUT_RESOURCE_ID="com.example:id/search_src_text"] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_START_X=720] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_START_Y=2600] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_END_X=720] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_END_Y=900] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_START_X_RATIO=0.5] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_START_Y_RATIO=0.81] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_END_X_RATIO=0.5] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_END_Y_RATIO=0.28] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_DURATION_MS=350] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_FORCE_STOP_TARGET_BEFORE_LAUNCH=true] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_FOLLOW_UP_FOREGROUND_TIMEOUT_MS=5000] \
  [OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_FOLLOW_UP_FOREGROUND_POLL_INTERVAL_MS=250] \
  ./apps/android/scripts/local-host-ui-cross-app-probe.sh

  ./apps/android/scripts/local-host-ui-cross-app-probe.sh --describe

What it does:
  1. Requires adb plus a reachable local-host bearer token
  2. Verifies /status and /invoke/capabilities, then foregrounds OpenClaw
  3. Calls ui.launchApp for the target package
  4. Optionally runs wait/swipe/tap/inputText follow-up actions inside the launched app
  5. Polls both adb foreground activity state and remote /status over time
  6. Restores OpenClaw with adb and verifies the host is reachable again

Follow-up note:
  - The optional wait/tap/input selectors are app and OEM specific.
  - Swipe coordinates are also device specific unless you already validated them on the same screen.
  - Swipe ratios are resolved against the current target-window bounds and are safer across screen sizes.
  - Target-state reset stays opt-in: use force-stop only when you intentionally want a clean app surface before launch.
  - Keep the current 30s reachability proof separate from follow-up-action proof.
  - Presets seed common follow-up envs but explicit env overrides still win.

Requirements:
  - curl
  - jq
  - adb
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

while [[ $# -gt 0 ]]; do
  case "$1" in
    --describe)
      DESCRIBE_ONLY=true
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

apply_follow_up_preset() {
  case "$FOLLOW_UP_PRESET" in
    "")
      ;;
    settings-search-input)
      TARGET_PACKAGE="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PACKAGE:-com.android.settings}"
      if [[ -z "$FOLLOW_UP_TAP_TEXT_RAW$FOLLOW_UP_TAP_CONTENT_DESCRIPTION_RAW$FOLLOW_UP_TAP_RESOURCE_ID_RAW" ]]; then
        FOLLOW_UP_TAP_RESOURCE_ID="com.android.settings:id/searchView"
      fi
      if [[ -z "$FOLLOW_UP_INPUT_VALUE_RAW" ]]; then
        FOLLOW_UP_INPUT_VALUE="openclaw"
      fi
      if [[ -z "$FOLLOW_UP_INPUT_TEXT_RAW$FOLLOW_UP_INPUT_CONTENT_DESCRIPTION_RAW$FOLLOW_UP_INPUT_RESOURCE_ID_RAW" ]]; then
        FOLLOW_UP_INPUT_RESOURCE_ID="com.android.settings:id/search_src_text"
      fi
      ;;
    settings-home-swipe-up)
      TARGET_PACKAGE="${OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PACKAGE:-com.android.settings}"
      if [[ -z "$FOLLOW_UP_SWIPE_START_X_RAW$FOLLOW_UP_SWIPE_START_Y_RAW$FOLLOW_UP_SWIPE_END_X_RAW$FOLLOW_UP_SWIPE_END_Y_RAW$FOLLOW_UP_SWIPE_START_X_RATIO_RAW$FOLLOW_UP_SWIPE_START_Y_RATIO_RAW$FOLLOW_UP_SWIPE_END_X_RATIO_RAW$FOLLOW_UP_SWIPE_END_Y_RATIO_RAW" ]]; then
        FOLLOW_UP_SWIPE_START_X_RATIO="0.5"
        FOLLOW_UP_SWIPE_START_Y_RATIO="0.81"
        FOLLOW_UP_SWIPE_END_X_RATIO="0.5"
        FOLLOW_UP_SWIPE_END_Y_RATIO="0.28"
      fi
      if [[ -z "$FOLLOW_UP_SWIPE_DURATION_MS_RAW" ]]; then
        FOLLOW_UP_SWIPE_DURATION_MS="350"
      fi
      ;;
    *)
      echo "Unsupported cross-app preset: $FOLLOW_UP_PRESET" >&2
      exit 1
      ;;
  esac
}

require_cmd jq
apply_follow_up_preset

validate_match_mode() {
  local raw=$1
  case "$raw" in
    exact | contains) ;;
    *)
      echo "Unsupported match mode: $raw (expected exact or contains)." >&2
      exit 1
      ;;
  esac
}

validate_match_mode "$FOLLOW_UP_WAIT_MATCH_MODE"
validate_match_mode "$FOLLOW_UP_TAP_MATCH_MODE"
validate_match_mode "$FOLLOW_UP_INPUT_MATCH_MODE"

validate_non_negative_number() {
  local name=$1
  local raw=$2
  if ! jq -en --arg value "$raw" '$value | tonumber | if . < 0 then error("negative") else . end' >/dev/null 2>&1; then
    echo "$name must be a non-negative number." >&2
    exit 1
  fi
}

validate_non_negative_integer() {
  local name=$1
  local raw=$2
  if ! [[ "$raw" =~ ^[0-9]+$ ]]; then
    echo "$name must be a non-negative integer." >&2
    exit 1
  fi
}

validate_ratio_number() {
  local name=$1
  local raw=$2
  if ! jq -en --arg value "$raw" '$value | tonumber | if . < 0 or . > 1 then error("range") else . end' >/dev/null 2>&1; then
    echo "$name must be a number between 0 and 1." >&2
    exit 1
  fi
}

validate_boolean() {
  local name=$1
  local raw=$2
  case "$raw" in
    true | false) ;;
    *)
      echo "$name must be true or false." >&2
      exit 1
      ;;
  esac
}

validate_boolean "OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_FORCE_STOP_TARGET_BEFORE_LAUNCH" "$RESET_TARGET_BEFORE_LAUNCH"

follow_up_wait_requested=false
if [[ -n "$FOLLOW_UP_WAIT_TEXT" ]]; then
  follow_up_wait_requested=true
fi

follow_up_tap_requested=false
if [[ -n "$FOLLOW_UP_TAP_TEXT$FOLLOW_UP_TAP_CONTENT_DESCRIPTION$FOLLOW_UP_TAP_RESOURCE_ID" ]]; then
  follow_up_tap_requested=true
fi

follow_up_input_selector_requested=false
if [[ -n "$FOLLOW_UP_INPUT_TEXT$FOLLOW_UP_INPUT_CONTENT_DESCRIPTION$FOLLOW_UP_INPUT_RESOURCE_ID" ]]; then
  follow_up_input_selector_requested=true
fi

follow_up_input_requested=false
if [[ -n "$FOLLOW_UP_INPUT_VALUE" || "$follow_up_input_selector_requested" == "true" ]]; then
  follow_up_input_requested=true
fi

follow_up_swipe_requested=false
if [[ -n "$FOLLOW_UP_SWIPE_START_X$FOLLOW_UP_SWIPE_START_Y$FOLLOW_UP_SWIPE_END_X$FOLLOW_UP_SWIPE_END_Y$FOLLOW_UP_SWIPE_START_X_RATIO$FOLLOW_UP_SWIPE_START_Y_RATIO$FOLLOW_UP_SWIPE_END_X_RATIO$FOLLOW_UP_SWIPE_END_Y_RATIO" ]]; then
  follow_up_swipe_requested=true
fi

follow_up_swipe_coordinate_mode="none"
if [[ -n "$FOLLOW_UP_SWIPE_START_X$FOLLOW_UP_SWIPE_START_Y$FOLLOW_UP_SWIPE_END_X$FOLLOW_UP_SWIPE_END_Y" ]]; then
  follow_up_swipe_coordinate_mode="absolute"
elif [[ -n "$FOLLOW_UP_SWIPE_START_X_RATIO$FOLLOW_UP_SWIPE_START_Y_RATIO$FOLLOW_UP_SWIPE_END_X_RATIO$FOLLOW_UP_SWIPE_END_Y_RATIO" ]]; then
  follow_up_swipe_coordinate_mode="ratio"
fi

if [[ "$follow_up_input_selector_requested" == "true" && -z "$FOLLOW_UP_INPUT_VALUE" ]]; then
  echo "OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_INPUT_VALUE is required when input selectors are set." >&2
  exit 1
fi

if [[ "$follow_up_swipe_requested" == "true" ]]; then
  if [[ "$follow_up_swipe_coordinate_mode" == "absolute" ]]; then
    if [[ -z "$FOLLOW_UP_SWIPE_START_X" ]]; then
      echo "OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_START_X is required when any absolute swipe coordinate is set." >&2
      exit 1
    fi
    if [[ -z "$FOLLOW_UP_SWIPE_START_Y" ]]; then
      echo "OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_START_Y is required when any absolute swipe coordinate is set." >&2
      exit 1
    fi
    if [[ -z "$FOLLOW_UP_SWIPE_END_X" ]]; then
      echo "OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_END_X is required when any absolute swipe coordinate is set." >&2
      exit 1
    fi
    if [[ -z "$FOLLOW_UP_SWIPE_END_Y" ]]; then
      echo "OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_END_Y is required when any absolute swipe coordinate is set." >&2
      exit 1
    fi
    validate_non_negative_number "OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_START_X" "$FOLLOW_UP_SWIPE_START_X"
    validate_non_negative_number "OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_START_Y" "$FOLLOW_UP_SWIPE_START_Y"
    validate_non_negative_number "OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_END_X" "$FOLLOW_UP_SWIPE_END_X"
    validate_non_negative_number "OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_END_Y" "$FOLLOW_UP_SWIPE_END_Y"
  elif [[ "$follow_up_swipe_coordinate_mode" == "ratio" ]]; then
    if [[ -z "$FOLLOW_UP_SWIPE_START_X_RATIO" ]]; then
      echo "OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_START_X_RATIO is required when any relative swipe coordinate is set." >&2
      exit 1
    fi
    if [[ -z "$FOLLOW_UP_SWIPE_START_Y_RATIO" ]]; then
      echo "OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_START_Y_RATIO is required when any relative swipe coordinate is set." >&2
      exit 1
    fi
    if [[ -z "$FOLLOW_UP_SWIPE_END_X_RATIO" ]]; then
      echo "OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_END_X_RATIO is required when any relative swipe coordinate is set." >&2
      exit 1
    fi
    if [[ -z "$FOLLOW_UP_SWIPE_END_Y_RATIO" ]]; then
      echo "OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_END_Y_RATIO is required when any relative swipe coordinate is set." >&2
      exit 1
    fi
    validate_ratio_number "OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_START_X_RATIO" "$FOLLOW_UP_SWIPE_START_X_RATIO"
    validate_ratio_number "OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_START_Y_RATIO" "$FOLLOW_UP_SWIPE_START_Y_RATIO"
    validate_ratio_number "OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_END_X_RATIO" "$FOLLOW_UP_SWIPE_END_X_RATIO"
    validate_ratio_number "OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_END_Y_RATIO" "$FOLLOW_UP_SWIPE_END_Y_RATIO"
  fi
  validate_non_negative_integer "OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_DURATION_MS" "$FOLLOW_UP_SWIPE_DURATION_MS"
fi

follow_up_requested=false
if [[ "$follow_up_wait_requested" == "true" || "$follow_up_swipe_requested" == "true" || "$follow_up_tap_requested" == "true" || "$follow_up_input_requested" == "true" ]]; then
  follow_up_requested=true
fi

follow_up_mode_parts=()
if [[ "$follow_up_wait_requested" == "true" ]]; then
  follow_up_mode_parts+=("wait")
fi
if [[ "$follow_up_swipe_requested" == "true" ]]; then
  follow_up_mode_parts+=("swipe")
fi
if [[ "$follow_up_tap_requested" == "true" ]]; then
  follow_up_mode_parts+=("tap")
fi
if [[ "$follow_up_input_requested" == "true" ]]; then
  follow_up_mode_parts+=("input")
fi
if [[ "${#follow_up_mode_parts[@]}" -eq 0 ]]; then
  follow_up_mode="none"
else
  follow_up_mode="$(IFS='+'; printf '%s' "${follow_up_mode_parts[*]}")"
fi

cross_app_preset="base"
if [[ -n "$FOLLOW_UP_PRESET" ]]; then
  cross_app_preset="preset:$FOLLOW_UP_PRESET"
elif [[ "$follow_up_requested" == "true" ]]; then
  cross_app_preset="follow-up:${follow_up_mode}"
fi

if [[ "$DESCRIBE_ONLY" == "true" ]]; then
  printf 'cross_app.describe=enabled\n'
  printf 'cross_app.script=%s\n' "local-host-ui-cross-app-probe.sh"
  printf 'cross_app.command=%s\n' "./apps/android/scripts/local-host-ui-cross-app-probe.sh"
  printf 'cross_app.preset=%s\n' "$cross_app_preset"
  printf 'cross_app.follow_up.preset=%s\n' "${FOLLOW_UP_PRESET:-<none>}"
  printf 'cross_app.target_package=%s\n' "$TARGET_PACKAGE"
  printf 'cross_app.follow_up_mode=%s\n' "$follow_up_mode"
  printf 'cross_app.observe_window_ms=%s\n' "$OBSERVE_WINDOW_MS"
  printf 'cross_app.poll_interval_ms=%s\n' "$POLL_INTERVAL_MS"
  printf 'cross_app.recovery_wait_ms=%s\n' "$RECOVERY_WAIT_MS"
  printf 'cross_app.target_reset_before_launch=%s\n' "$RESET_TARGET_BEFORE_LAUNCH"
  printf 'cross_app.run_env.OPENCLAW_ANDROID_LOCAL_HOST_PORT=%s\n' "$PORT"
  printf 'cross_app.run_env.OPENCLAW_ANDROID_LOCAL_HOST_UI_APP_PACKAGE=%s\n' "$APP_PACKAGE"
  printf 'cross_app.run_env.OPENCLAW_ANDROID_LOCAL_HOST_UI_APP_COMPONENT=%s\n' "$APP_COMPONENT"
  printf 'cross_app.run_env.OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PACKAGE=%s\n' "$TARGET_PACKAGE"
  printf 'cross_app.run_env.OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_OBSERVE_WINDOW_MS=%s\n' "$OBSERVE_WINDOW_MS"
  printf 'cross_app.run_env.OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_POLL_INTERVAL_MS=%s\n' "$POLL_INTERVAL_MS"
  printf 'cross_app.run_env.OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_RECOVERY_WAIT_MS=%s\n' "$RECOVERY_WAIT_MS"
  printf 'cross_app.run_env.OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_FORCE_STOP_TARGET_BEFORE_LAUNCH=%s\n' "$RESET_TARGET_BEFORE_LAUNCH"
  printf 'cross_app.follow_up.wait_requested=%s\n' "$follow_up_wait_requested"
  printf 'cross_app.follow_up.tap_requested=%s\n' "$follow_up_tap_requested"
  printf 'cross_app.follow_up.input_requested=%s\n' "$follow_up_input_requested"
  printf 'cross_app.follow_up.swipe_requested=%s\n' "$follow_up_swipe_requested"
  printf 'cross_app.follow_up.input_selector_requested=%s\n' "$follow_up_input_selector_requested"
  printf 'cross_app.follow_up.wait_match_mode=%s\n' "$FOLLOW_UP_WAIT_MATCH_MODE"
  printf 'cross_app.follow_up.tap_match_mode=%s\n' "$FOLLOW_UP_TAP_MATCH_MODE"
  printf 'cross_app.follow_up.input_match_mode=%s\n' "$FOLLOW_UP_INPUT_MATCH_MODE"
  printf 'cross_app.follow_up.swipe_coordinate_mode=%s\n' "$follow_up_swipe_coordinate_mode"
  printf 'cross_app.follow_up.swipe_start_x=%s\n' "${FOLLOW_UP_SWIPE_START_X:-<empty>}"
  printf 'cross_app.follow_up.swipe_start_y=%s\n' "${FOLLOW_UP_SWIPE_START_Y:-<empty>}"
  printf 'cross_app.follow_up.swipe_end_x=%s\n' "${FOLLOW_UP_SWIPE_END_X:-<empty>}"
  printf 'cross_app.follow_up.swipe_end_y=%s\n' "${FOLLOW_UP_SWIPE_END_Y:-<empty>}"
  printf 'cross_app.follow_up.swipe_start_x_ratio=%s\n' "${FOLLOW_UP_SWIPE_START_X_RATIO:-<empty>}"
  printf 'cross_app.follow_up.swipe_start_y_ratio=%s\n' "${FOLLOW_UP_SWIPE_START_Y_RATIO:-<empty>}"
  printf 'cross_app.follow_up.swipe_end_x_ratio=%s\n' "${FOLLOW_UP_SWIPE_END_X_RATIO:-<empty>}"
  printf 'cross_app.follow_up.swipe_end_y_ratio=%s\n' "${FOLLOW_UP_SWIPE_END_Y_RATIO:-<empty>}"
  printf 'cross_app.follow_up.swipe_duration_ms=%s\n' "$FOLLOW_UP_SWIPE_DURATION_MS"
  printf 'cross_app.follow_up.foreground_timeout_ms=%s\n' "$FOLLOW_UP_FOREGROUND_TIMEOUT_MS"
  printf 'cross_app.follow_up.foreground_poll_interval_ms=%s\n' "$FOLLOW_UP_FOREGROUND_POLL_INTERVAL_MS"
  printf 'cross_app.follow_up.settle_ms=%s\n' "$FOLLOW_UP_SETTLE_MS"
  printf 'cross_app.follow_up.wait_text=%s\n' "${FOLLOW_UP_WAIT_TEXT:-<empty>}"
  printf 'cross_app.follow_up.tap_text=%s\n' "${FOLLOW_UP_TAP_TEXT:-<empty>}"
  printf 'cross_app.follow_up.tap_resource_id=%s\n' "${FOLLOW_UP_TAP_RESOURCE_ID:-<empty>}"
  printf 'cross_app.follow_up.input_value=%s\n' "${FOLLOW_UP_INPUT_VALUE:-<empty>}"
  printf 'cross_app.follow_up.input_resource_id=%s\n' "${FOLLOW_UP_INPUT_RESOURCE_ID:-<empty>}"
  if [[ -n "$FOLLOW_UP_PRESET" ]]; then
    printf 'cross_app.rerun_hint=%s\n' "OPENCLAW_ANDROID_LOCAL_HOST_TOKEN=<token> OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PRESET=$FOLLOW_UP_PRESET ./apps/android/scripts/local-host-ui-cross-app-probe.sh"
  else
    printf 'cross_app.rerun_hint=%s\n' "OPENCLAW_ANDROID_LOCAL_HOST_TOKEN=<token> ./apps/android/scripts/local-host-ui-cross-app-probe.sh"
  fi
  exit 0
fi

if [[ -z "$TOKEN" ]]; then
  echo "OPENCLAW_ANDROID_LOCAL_HOST_TOKEN is required." >&2
  usage >&2
  exit 1
fi

require_cmd curl
require_cmd adb

follow_up_requested_count=0
if [[ "$follow_up_wait_requested" == "true" ]]; then
  follow_up_requested_count=$((follow_up_requested_count + 1))
fi
if [[ "$follow_up_swipe_requested" == "true" ]]; then
  follow_up_requested_count=$((follow_up_requested_count + 1))
fi
if [[ "$follow_up_tap_requested" == "true" ]]; then
  follow_up_requested_count=$((follow_up_requested_count + 1))
fi
if [[ "$follow_up_input_requested" == "true" ]]; then
  follow_up_requested_count=$((follow_up_requested_count + 1))
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
BASE_URL="${BASE_URL%/}"

AUTH_HEADER="Authorization: Bearer $TOKEN"
mkdir -p "$ARTIFACT_DIR"

STATUS_JSON="$ARTIFACT_DIR/status.json"
CAPABILITIES_JSON="$ARTIFACT_DIR/capabilities.json"
LAUNCH_SELF_JSON="$ARTIFACT_DIR/ui-launch-self.json"
LAUNCH_TARGET_JSON="$ARTIFACT_DIR/ui-launch-target.json"
TIMELINE_JSONL="$ARTIFACT_DIR/timeline.jsonl"
FOLLOW_UP_WAIT_JSON="$ARTIFACT_DIR/ui-follow-up-wait.json"
FOLLOW_UP_PRE_SWIPE_STATE_JSON="$ARTIFACT_DIR/ui-follow-up-pre-swipe-state.json"
FOLLOW_UP_SWIPE_JSON="$ARTIFACT_DIR/ui-follow-up-swipe.json"
FOLLOW_UP_TAP_JSON="$ARTIFACT_DIR/ui-follow-up-tap.json"
FOLLOW_UP_INPUT_JSON="$ARTIFACT_DIR/ui-follow-up-input.json"
FOLLOW_UP_FOREGROUND_STATE_JSON="$ARTIFACT_DIR/ui-follow-up-foreground-state.json"
FOLLOW_UP_STATE_JSON="$ARTIFACT_DIR/ui-follow-up-state.json"
RECOVERY_STATUS_JSON="$ARTIFACT_DIR/recovery-status.json"
RECOVERY_STATUS_CODE="$ARTIFACT_DIR/recovery-status.code"
RECOVERY_STATE_JSON="$ARTIFACT_DIR/recovery-state.json"
SUMMARY_JSON="$ARTIFACT_DIR/summary.json"

target_reset_applied=false

sleep_seconds_from_ms() {
  local ms=$1
  awk "BEGIN { printf \"%.3f\", $ms / 1000 }"
}

get_json() {
  local url=$1
  curl --fail --silent --show-error --max-time "$REQUEST_TIMEOUT_SEC" \
    -H "$AUTH_HEADER" \
    "$url"
}

post_json() {
  local url=$1
  local body=$2
  local max_time=${3:-$REQUEST_TIMEOUT_SEC}
  curl --fail --silent --show-error --max-time "$max_time" \
    -H "$AUTH_HEADER" \
    -H 'Content-Type: application/json' \
    -X POST \
    "$url" \
    -d "$body"
}

invoke_command() {
  local command=$1
  local params_json=${2:-}
  local output_file=$3
  local max_time=${4:-$REQUEST_TIMEOUT_SEC}
  local body
  body="$(jq -cn --arg command "$command" --arg params "$params_json" '
    if ($params | length) > 0 then
      {command:$command, params:($params | fromjson)}
    else
      {command:$command}
    end
  ')"
  post_json "$BASE_URL/api/local-host/v1/invoke" "$body" "$max_time" | tee "$output_file" >/dev/null
}

status_probe() {
  local response_file=$1
  local code_file=$2
  curl --silent --show-error --max-time "$REQUEST_TIMEOUT_SEC" \
    -o "$response_file" \
    -w '%{http_code}' \
    -H "$AUTH_HEADER" \
    "$BASE_URL/api/local-host/v1/status" >"$code_file" || true
}

current_top_activity_line() {
  local activity_dump
  activity_dump="$(adb shell dumpsys activity activities | tr -d '\r')"
  awk '/topResumedActivity/ {print; exit}' <<<"$activity_dump"
}

current_focus_line() {
  local window_dump
  window_dump="$(adb shell dumpsys window windows | tr -d '\r')"
  awk '/mCurrentFocus=/ {print; exit}' <<<"$window_dump"
}

current_top_package() {
  local top_line=$1
  if [[ -z "$top_line" ]]; then
    printf '%s' ""
    return 0
  fi
  printf '%s\n' "$top_line" | sed -n 's/.* u0 \([^/ ]*\)\/.*/\1/p'
}

assert_allowed_command() {
  local command=$1
  if ! jq -e --arg command "$command" '.commands | index($command)' "$CAPABILITIES_JSON" >/dev/null; then
    echo "Required UI command is not enabled for remote access: $command" >&2
    jq '.' "$CAPABILITIES_JSON" >&2
    exit 1
  fi
}

extract_visible_text_sample_json() {
  local snapshot_file=$1
  jq -c '[.payload.visibleText[]? | strings | select(length > 0)][0:8]' "$snapshot_file" 2>/dev/null || printf '%s' '[]'
}

extract_active_window_bounds_json() {
  local snapshot_file=$1
  jq -ec '
    (.payload.nodes // [])
    | map(select(.bounds != null))
    | if length == 0 then
        error("missing bounds")
      else
        {
          left: (map(.bounds.left) | min),
          top: (map(.bounds.top) | min),
          right: (map(.bounds.right) | max),
          bottom: (map(.bounds.bottom) | max)
        }
      end
  ' "$snapshot_file" 2>/dev/null
}

resolve_swipe_coordinates_json() {
  local snapshot_file=$1
  if [[ "$follow_up_swipe_coordinate_mode" == "absolute" ]]; then
    jq -cn \
      --arg startX "$FOLLOW_UP_SWIPE_START_X" \
      --arg startY "$FOLLOW_UP_SWIPE_START_Y" \
      --arg endX "$FOLLOW_UP_SWIPE_END_X" \
      --arg endY "$FOLLOW_UP_SWIPE_END_Y" \
      '{
        startX: ($startX | tonumber),
        startY: ($startY | tonumber),
        endX: ($endX | tonumber),
        endY: ($endY | tonumber),
        coordinateMode: "absolute",
        bounds: null
      }'
    return 0
  fi

  local bounds_json
  if ! bounds_json="$(extract_active_window_bounds_json "$snapshot_file")"; then
    echo "Unable to derive swipe bounds from the current target window snapshot." >&2
    jq '.' "$snapshot_file" >&2
    exit 1
  fi

  jq -cn \
    --argjson bounds "$bounds_json" \
    --arg startXRatio "$FOLLOW_UP_SWIPE_START_X_RATIO" \
    --arg startYRatio "$FOLLOW_UP_SWIPE_START_Y_RATIO" \
    --arg endXRatio "$FOLLOW_UP_SWIPE_END_X_RATIO" \
    --arg endYRatio "$FOLLOW_UP_SWIPE_END_Y_RATIO" \
    '
      ($bounds.right - $bounds.left) as $width
      | ($bounds.bottom - $bounds.top) as $height
      | if $width <= 0 or $height <= 0 then
          error("invalid bounds")
        else
          {
            startX: ($bounds.left + ($width * ($startXRatio | tonumber))),
            startY: ($bounds.top + ($height * ($startYRatio | tonumber))),
            endX: ($bounds.left + ($width * ($endXRatio | tonumber))),
            endY: ($bounds.top + ($height * ($endYRatio | tonumber))),
            coordinateMode: "ratio",
            bounds: $bounds
          }
        end
    '
}

echo "local_host.base_url=$BASE_URL"
echo "local_host.ui_cross_app_probe=starting"
echo "artifacts.dir=$ARTIFACT_DIR"

get_json "$BASE_URL/api/local-host/v1/status" | tee "$STATUS_JSON" >/dev/null
if [[ "$(jq -r '.ok // false' "$STATUS_JSON")" != "true" ]]; then
  echo "Remote /status returned ok=false." >&2
  cat "$STATUS_JSON" >&2
  exit 1
fi

status_ui_available="$(jq -r '.host.uiAutomationAvailable // false' "$STATUS_JSON")"
status_ui_enabled="$(jq -r '.host.uiAutomation.enabled // false' "$STATUS_JSON")"
status_ui_connected="$(jq -r '.host.uiAutomation.serviceConnected // false' "$STATUS_JSON")"
status_write="$(jq -r '.remoteAccess.writeEnabled // false' "$STATUS_JSON")"
if [[ "$status_ui_available" != "true" || "$status_ui_enabled" != "true" || "$status_ui_connected" != "true" || "$status_write" != "true" ]]; then
  echo "UI automation or write-tier readiness is missing." >&2
  jq '.' "$STATUS_JSON" >&2
  exit 1
fi

get_json "$BASE_URL/api/local-host/v1/invoke/capabilities" | tee "$CAPABILITIES_JSON" >/dev/null
for required_command in ui.launchApp ui.state; do
  assert_allowed_command "$required_command"
done
if [[ "$follow_up_wait_requested" == "true" ]]; then
  assert_allowed_command "ui.waitForText"
fi
if [[ "$follow_up_swipe_requested" == "true" ]]; then
  assert_allowed_command "ui.swipe"
fi
if [[ "$follow_up_tap_requested" == "true" ]]; then
  assert_allowed_command "ui.tap"
fi
if [[ "$follow_up_input_requested" == "true" ]]; then
  assert_allowed_command "ui.inputText"
fi

invoke_command "ui.launchApp" "$(jq -cn --arg packageName "$APP_PACKAGE" '{packageName:$packageName}')" "$LAUNCH_SELF_JSON"
if ! jq -e --arg package "$APP_PACKAGE" '
  .ok == true and
  .payload.action == "launchApp" and
  .payload.launched == true and
  .payload.packageName == $package
' "$LAUNCH_SELF_JSON" >/dev/null; then
  echo "ui.launchApp failed to foreground OpenClaw before the cross-app probe." >&2
  jq '.' "$LAUNCH_SELF_JSON" >&2
  exit 1
fi

if [[ "$RESET_TARGET_BEFORE_LAUNCH" == "true" ]]; then
  adb shell am force-stop "$TARGET_PACKAGE" >/dev/null
  target_reset_applied=true
fi

invoke_command "ui.launchApp" "$(jq -cn --arg packageName "$TARGET_PACKAGE" '{packageName:$packageName}')" "$LAUNCH_TARGET_JSON"
if ! jq -e --arg package "$TARGET_PACKAGE" '
  .ok == true and
  .payload.action == "launchApp" and
  .payload.launched == true and
  .payload.packageName == $package
' "$LAUNCH_TARGET_JSON" >/dev/null; then
  echo "ui.launchApp failed for the target cross-app package." >&2
  jq '.' "$LAUNCH_TARGET_JSON" >&2
  exit 1
fi

follow_up_wait_ok=false
follow_up_wait_matched_text=""
follow_up_swipe_ok=false
follow_up_swipe_strategy=""
follow_up_swipe_pre_package=""
follow_up_swipe_coordinate_mode_json='null'
follow_up_swipe_start_x_json='null'
follow_up_swipe_start_y_json='null'
follow_up_swipe_end_x_json='null'
follow_up_swipe_end_y_json='null'
follow_up_swipe_duration_ms_json='null'
follow_up_swipe_bounds_json='null'
follow_up_swipe_before_visible_text_json='null'
follow_up_swipe_after_visible_text_json='null'
follow_up_swipe_visible_text_changed_json='null'
follow_up_tap_ok=false
follow_up_tap_strategy=""
follow_up_input_ok=false
follow_up_input_strategy=""
follow_up_foreground_ready=false
follow_up_foreground_attempts=0
follow_up_foreground_package=""
follow_up_state_ok=false
follow_up_state_package=""

wait_for_follow_up_target_foreground() {
  local max_attempts
  max_attempts=$(( (FOLLOW_UP_FOREGROUND_TIMEOUT_MS + FOLLOW_UP_FOREGROUND_POLL_INTERVAL_MS - 1) / FOLLOW_UP_FOREGROUND_POLL_INTERVAL_MS ))
  if [[ "$max_attempts" -lt 1 ]]; then
    max_attempts=1
  fi

  for ((attempt = 1; attempt <= max_attempts; attempt++)); do
    follow_up_foreground_attempts=$attempt
    invoke_command "ui.state" "" "$FOLLOW_UP_FOREGROUND_STATE_JSON"
    follow_up_foreground_package="$(jq -r '.payload.packageName // ""' "$FOLLOW_UP_FOREGROUND_STATE_JSON" 2>/dev/null || printf '%s' "")"
    if [[ "$follow_up_foreground_package" == "$TARGET_PACKAGE" ]]; then
      follow_up_foreground_ready=true
      return 0
    fi
    if (( attempt < max_attempts )); then
      sleep "$(sleep_seconds_from_ms "$FOLLOW_UP_FOREGROUND_POLL_INTERVAL_MS")"
    fi
  done

  echo "Cross-app follow-up target package never became active before the follow-up actions." >&2
  jq '.' "$FOLLOW_UP_FOREGROUND_STATE_JSON" >&2
  exit 1
}

if [[ "$follow_up_requested" == "true" ]]; then
  echo "cross_app.follow_up=requested"
  wait_for_follow_up_target_foreground

  if [[ "$follow_up_wait_requested" == "true" ]]; then
    follow_up_wait_request_timeout_sec="$(
      awk "BEGIN { printf \"%.0f\", ($FOLLOW_UP_WAIT_TIMEOUT_MS / 1000) + 5 }"
    )"
    invoke_command \
      "ui.waitForText" \
      "$(jq -cn \
        --arg text "$FOLLOW_UP_WAIT_TEXT" \
        --arg packageName "$TARGET_PACKAGE" \
        --arg matchMode "$FOLLOW_UP_WAIT_MATCH_MODE" \
        --argjson timeoutMs "$FOLLOW_UP_WAIT_TIMEOUT_MS" \
        --argjson pollIntervalMs "$FOLLOW_UP_WAIT_POLL_INTERVAL_MS" \
        '{text:$text, packageName:$packageName, matchMode:$matchMode, timeoutMs:$timeoutMs, pollIntervalMs:$pollIntervalMs}')" \
      "$FOLLOW_UP_WAIT_JSON" \
      "$follow_up_wait_request_timeout_sec"
    if ! jq -e --arg package "$TARGET_PACKAGE" '
      .ok == true and
      .payload.packageName == $package and
      .payload.wait.matched == true
    ' "$FOLLOW_UP_WAIT_JSON" >/dev/null; then
      echo "Cross-app follow-up wait did not match inside the target package." >&2
      jq '.' "$FOLLOW_UP_WAIT_JSON" >&2
      exit 1
    fi
    follow_up_wait_ok=true
    follow_up_wait_matched_text="$(jq -r '.payload.wait.matchedText // ""' "$FOLLOW_UP_WAIT_JSON" 2>/dev/null || printf '%s' "")"
  fi

  if [[ "$follow_up_swipe_requested" == "true" ]]; then
    resolved_swipe_json='null'
    invoke_command "ui.state" "" "$FOLLOW_UP_PRE_SWIPE_STATE_JSON"
    if ! jq -e --arg package "$TARGET_PACKAGE" '
      .ok == true and
      .payload.packageName == $package
    ' "$FOLLOW_UP_PRE_SWIPE_STATE_JSON" >/dev/null; then
      echo "Cross-app follow-up pre-swipe state no longer reports the target package on top." >&2
      jq '.' "$FOLLOW_UP_PRE_SWIPE_STATE_JSON" >&2
      exit 1
    fi
    follow_up_swipe_pre_package="$(jq -r '.payload.packageName // ""' "$FOLLOW_UP_PRE_SWIPE_STATE_JSON" 2>/dev/null || printf '%s' "")"
    follow_up_swipe_before_visible_text_json="$(extract_visible_text_sample_json "$FOLLOW_UP_PRE_SWIPE_STATE_JSON")"
    resolved_swipe_json="$(resolve_swipe_coordinates_json "$FOLLOW_UP_PRE_SWIPE_STATE_JSON")"
    follow_up_swipe_coordinate_mode_json="$(jq -c '.coordinateMode' <<<"$resolved_swipe_json")"
    follow_up_swipe_start_x_json="$(jq -c '.startX' <<<"$resolved_swipe_json")"
    follow_up_swipe_start_y_json="$(jq -c '.startY' <<<"$resolved_swipe_json")"
    follow_up_swipe_end_x_json="$(jq -c '.endX' <<<"$resolved_swipe_json")"
    follow_up_swipe_end_y_json="$(jq -c '.endY' <<<"$resolved_swipe_json")"
    follow_up_swipe_duration_ms_json="$FOLLOW_UP_SWIPE_DURATION_MS"
    follow_up_swipe_bounds_json="$(jq -c '.bounds' <<<"$resolved_swipe_json")"

    invoke_command \
      "ui.swipe" \
      "$(jq -cn \
        --argjson startX "$follow_up_swipe_start_x_json" \
        --argjson startY "$follow_up_swipe_start_y_json" \
        --argjson endX "$follow_up_swipe_end_x_json" \
        --argjson endY "$follow_up_swipe_end_y_json" \
        --arg durationMs "$FOLLOW_UP_SWIPE_DURATION_MS" \
        --arg packageName "$TARGET_PACKAGE" \
        '{
          startX: $startX,
          startY: $startY,
          endX: $endX,
          endY: $endY,
          durationMs: ($durationMs | tonumber),
          packageName: $packageName
        }')" \
      "$FOLLOW_UP_SWIPE_JSON"
    if ! jq -e --arg package "$TARGET_PACKAGE" '
      .ok == true and
      .payload.action == "swipe" and
      .payload.performed == true and
      .payload.packageName == $package
    ' "$FOLLOW_UP_SWIPE_JSON" >/dev/null; then
      echo "Cross-app follow-up swipe did not complete inside the target package." >&2
      jq '.' "$FOLLOW_UP_SWIPE_JSON" >&2
      exit 1
    fi
    follow_up_swipe_ok=true
    follow_up_swipe_strategy="$(jq -r '.payload.strategy // ""' "$FOLLOW_UP_SWIPE_JSON" 2>/dev/null || printf '%s' "")"
    if [[ "$follow_up_tap_requested" == "true" || "$follow_up_input_requested" == "true" ]]; then
      sleep "$(sleep_seconds_from_ms "$FOLLOW_UP_SETTLE_MS")"
    fi
  fi

  if [[ "$follow_up_tap_requested" == "true" ]]; then
    invoke_command \
      "ui.tap" \
      "$(jq -cn \
        --arg text "$FOLLOW_UP_TAP_TEXT" \
        --arg contentDescription "$FOLLOW_UP_TAP_CONTENT_DESCRIPTION" \
        --arg resourceId "$FOLLOW_UP_TAP_RESOURCE_ID" \
        --arg packageName "$TARGET_PACKAGE" \
        --arg matchMode "$FOLLOW_UP_TAP_MATCH_MODE" \
        --argjson index "$FOLLOW_UP_TAP_INDEX" \
        '{
          packageName:$packageName,
          matchMode:$matchMode,
          index:$index
        }
        + (if $text != "" then {text:$text} else {} end)
        + (if $contentDescription != "" then {contentDescription:$contentDescription} else {} end)
        + (if $resourceId != "" then {resourceId:$resourceId} else {} end)')" \
      "$FOLLOW_UP_TAP_JSON"
    if ! jq -e --arg package "$TARGET_PACKAGE" '
      .ok == true and
      .payload.action == "tap" and
      .payload.performed == true and
      .payload.packageName == $package
    ' "$FOLLOW_UP_TAP_JSON" >/dev/null; then
      echo "Cross-app follow-up tap did not complete inside the target package." >&2
      jq '.' "$FOLLOW_UP_TAP_JSON" >&2
      exit 1
    fi
    follow_up_tap_ok=true
    follow_up_tap_strategy="$(jq -r '.payload.strategy // ""' "$FOLLOW_UP_TAP_JSON" 2>/dev/null || printf '%s' "")"
  fi

  if [[ "$follow_up_input_requested" == "true" ]]; then
    invoke_command \
      "ui.inputText" \
      "$(jq -cn \
        --arg value "$FOLLOW_UP_INPUT_VALUE" \
        --arg text "$FOLLOW_UP_INPUT_TEXT" \
        --arg contentDescription "$FOLLOW_UP_INPUT_CONTENT_DESCRIPTION" \
        --arg resourceId "$FOLLOW_UP_INPUT_RESOURCE_ID" \
        --arg packageName "$TARGET_PACKAGE" \
        --arg matchMode "$FOLLOW_UP_INPUT_MATCH_MODE" \
        --argjson index "$FOLLOW_UP_INPUT_INDEX" \
        '{
          value:$value,
          packageName:$packageName,
          matchMode:$matchMode,
          index:$index
        }
        + (if $text != "" then {text:$text} else {} end)
        + (if $contentDescription != "" then {contentDescription:$contentDescription} else {} end)
        + (if $resourceId != "" then {resourceId:$resourceId} else {} end)')" \
      "$FOLLOW_UP_INPUT_JSON"
    if ! jq -e --arg package "$TARGET_PACKAGE" '
      .ok == true and
      .payload.action == "inputText" and
      .payload.performed == true and
      .payload.packageName == $package
    ' "$FOLLOW_UP_INPUT_JSON" >/dev/null; then
      echo "Cross-app follow-up input did not complete inside the target package." >&2
      jq '.' "$FOLLOW_UP_INPUT_JSON" >&2
      exit 1
    fi
    follow_up_input_ok=true
    follow_up_input_strategy="$(jq -r '.payload.strategy // ""' "$FOLLOW_UP_INPUT_JSON" 2>/dev/null || printf '%s' "")"
  fi

  if [[ "$follow_up_swipe_requested" == "true" || "$follow_up_tap_requested" == "true" || "$follow_up_input_requested" == "true" ]]; then
    sleep "$(sleep_seconds_from_ms "$FOLLOW_UP_SETTLE_MS")"
  fi

  invoke_command "ui.state" "" "$FOLLOW_UP_STATE_JSON"
  if ! jq -e --arg package "$TARGET_PACKAGE" '
    .ok == true and
    .payload.packageName == $package
  ' "$FOLLOW_UP_STATE_JSON" >/dev/null; then
    echo "Cross-app follow-up state check no longer reports the target package on top." >&2
    jq '.' "$FOLLOW_UP_STATE_JSON" >&2
    exit 1
  fi
  follow_up_state_ok=true
  follow_up_state_package="$(jq -r '.payload.packageName // ""' "$FOLLOW_UP_STATE_JSON" 2>/dev/null || printf '%s' "")"
  if [[ "$follow_up_swipe_requested" == "true" ]]; then
    follow_up_swipe_after_visible_text_json="$(extract_visible_text_sample_json "$FOLLOW_UP_STATE_JSON")"
    follow_up_swipe_visible_text_changed_json="$(
      jq -en \
        --argjson before "$follow_up_swipe_before_visible_text_json" \
        --argjson after "$follow_up_swipe_after_visible_text_json" \
        '$before != $after'
    )"
  fi
fi

rounds=$(( (OBSERVE_WINDOW_MS + POLL_INTERVAL_MS - 1) / POLL_INTERVAL_MS ))
status_success_count=0
target_top_count=0
first_target_round=-1
first_status_failure_round=-1
: >"$TIMELINE_JSONL"

for ((round = 1; round <= rounds; round++)); do
  probe_response="$ARTIFACT_DIR/status-round-$round.json"
  probe_code="$ARTIFACT_DIR/status-round-$round.code"
  status_probe "$probe_response" "$probe_code"
  http_code="$(cat "$probe_code" 2>/dev/null || printf '%s' "000")"
  status_ok=false
  if [[ "$http_code" == "200" ]]; then
    status_ok="$(jq -r '.ok // false' "$probe_response" 2>/dev/null || printf '%s' "false")"
  fi

  top_line="$(current_top_activity_line)"
  focus_line="$(current_focus_line)"
  top_package="$(current_top_package "$top_line")"
  target_on_top=false
  if [[ "$top_package" == "$TARGET_PACKAGE" ]]; then
    target_on_top=true
    target_top_count=$((target_top_count + 1))
    if [[ "$first_target_round" -lt 0 ]]; then
      first_target_round=$round
    fi
  fi

  if [[ "$status_ok" == "true" ]]; then
    status_success_count=$((status_success_count + 1))
  elif [[ "$first_status_failure_round" -lt 0 ]]; then
    first_status_failure_round=$round
  fi

  jq -n \
    --argjson round "$round" \
    --arg httpCode "$http_code" \
    --argjson statusOk "$( [[ "$status_ok" == "true" ]] && printf 'true' || printf 'false' )" \
    --arg topPackage "$top_package" \
    --arg topLine "$top_line" \
    --arg focusLine "$focus_line" \
    --arg targetPackage "$TARGET_PACKAGE" \
    --argjson targetOnTop "$( [[ "$target_on_top" == "true" ]] && printf 'true' || printf 'false' )" \
    '{
      round: $round,
      httpCode: $httpCode,
      statusOk: $statusOk,
      topPackage: (if $topPackage == "" then null else $topPackage end),
      topLine: (if $topLine == "" then null else $topLine end),
      focusLine: (if $focusLine == "" then null else $focusLine end),
      targetPackage: $targetPackage,
      targetOnTop: $targetOnTop
    }' >>"$TIMELINE_JSONL"
  printf '\n' >>"$TIMELINE_JSONL"

  if (( round < rounds )); then
    sleep "$(sleep_seconds_from_ms "$POLL_INTERVAL_MS")"
  fi
done

classification="launch_accepted_not_foregrounded"
if [[ "$target_top_count" -gt 0 && "$first_status_failure_round" -lt 0 ]]; then
  classification="foregrounded_host_reachable"
elif [[ "$target_top_count" -gt 0 && "$first_status_failure_round" -ge 0 ]]; then
  classification="foregrounded_then_remote_unreachable"
fi

adb shell am start -n "$APP_COMPONENT" >/dev/null
sleep "$(sleep_seconds_from_ms "$RECOVERY_WAIT_MS")"
status_probe "$RECOVERY_STATUS_JSON" "$RECOVERY_STATUS_CODE"
recovery_http_code="$(cat "$RECOVERY_STATUS_CODE" 2>/dev/null || printf '%s' "000")"
recovery_ok=false
if [[ "$recovery_http_code" == "200" ]]; then
  recovery_ok="$(jq -r '.ok // false' "$RECOVERY_STATUS_JSON" 2>/dev/null || printf '%s' "false")"
fi

invoke_command "ui.state" "" "$RECOVERY_STATE_JSON"
recovered_package="$(jq -r '.payload.packageName // ""' "$RECOVERY_STATE_JSON" 2>/dev/null || printf '%s' "")"

jq -n \
  --arg appPackage "$APP_PACKAGE" \
  --arg targetPackage "$TARGET_PACKAGE" \
  --arg classification "$classification" \
  --argjson targetResetBeforeLaunch "$( [[ "$RESET_TARGET_BEFORE_LAUNCH" == "true" ]] && printf 'true' || printf 'false' )" \
  --argjson targetResetApplied "$( [[ "$target_reset_applied" == "true" ]] && printf 'true' || printf 'false' )" \
  --argjson observeWindowMs "$OBSERVE_WINDOW_MS" \
  --argjson pollIntervalMs "$POLL_INTERVAL_MS" \
  --argjson rounds "$rounds" \
  --argjson statusSuccessCount "$status_success_count" \
  --argjson targetTopCount "$target_top_count" \
  --argjson firstTargetRound "$first_target_round" \
  --argjson firstStatusFailureRound "$first_status_failure_round" \
  --arg recoveryHttpCode "$recovery_http_code" \
  --argjson recoveryOk "$( [[ "$recovery_ok" == "true" ]] && printf 'true' || printf 'false' )" \
  --arg recoveredPackage "$recovered_package" \
  --arg followUpWaitText "$FOLLOW_UP_WAIT_TEXT" \
  --arg followUpWaitMatchedText "$follow_up_wait_matched_text" \
  --arg followUpSwipeStrategy "$follow_up_swipe_strategy" \
  --arg followUpSwipePrePackage "$follow_up_swipe_pre_package" \
  --arg followUpTapText "$FOLLOW_UP_TAP_TEXT" \
  --arg followUpTapContentDescription "$FOLLOW_UP_TAP_CONTENT_DESCRIPTION" \
  --arg followUpTapResourceId "$FOLLOW_UP_TAP_RESOURCE_ID" \
  --arg followUpTapStrategy "$follow_up_tap_strategy" \
  --arg followUpInputText "$FOLLOW_UP_INPUT_TEXT" \
  --arg followUpInputContentDescription "$FOLLOW_UP_INPUT_CONTENT_DESCRIPTION" \
  --arg followUpInputResourceId "$FOLLOW_UP_INPUT_RESOURCE_ID" \
  --arg followUpInputStrategy "$follow_up_input_strategy" \
  --arg followUpForegroundPackage "$follow_up_foreground_package" \
  --arg followUpStatePackage "$follow_up_state_package" \
  --arg followUpMode "$follow_up_mode" \
  --argjson followUpRequested "$( [[ "$follow_up_requested" == "true" ]] && printf 'true' || printf 'false' )" \
  --argjson followUpWaitRequested "$( [[ "$follow_up_wait_requested" == "true" ]] && printf 'true' || printf 'false' )" \
  --argjson followUpWaitOk "$( [[ "$follow_up_wait_ok" == "true" ]] && printf 'true' || printf 'false' )" \
  --argjson followUpSwipeRequested "$( [[ "$follow_up_swipe_requested" == "true" ]] && printf 'true' || printf 'false' )" \
  --argjson followUpSwipeOk "$( [[ "$follow_up_swipe_ok" == "true" ]] && printf 'true' || printf 'false' )" \
  --argjson followUpSwipeCoordinateMode "$follow_up_swipe_coordinate_mode_json" \
  --argjson followUpSwipeStartX "$follow_up_swipe_start_x_json" \
  --argjson followUpSwipeStartY "$follow_up_swipe_start_y_json" \
  --argjson followUpSwipeEndX "$follow_up_swipe_end_x_json" \
  --argjson followUpSwipeEndY "$follow_up_swipe_end_y_json" \
  --argjson followUpSwipeDurationMs "$follow_up_swipe_duration_ms_json" \
  --argjson followUpSwipeBounds "$follow_up_swipe_bounds_json" \
  --argjson followUpSwipeVisibleTextBefore "$follow_up_swipe_before_visible_text_json" \
  --argjson followUpSwipeVisibleTextAfter "$follow_up_swipe_after_visible_text_json" \
  --argjson followUpSwipeVisibleTextChanged "$follow_up_swipe_visible_text_changed_json" \
  --argjson followUpTapRequested "$( [[ "$follow_up_tap_requested" == "true" ]] && printf 'true' || printf 'false' )" \
  --argjson followUpTapOk "$( [[ "$follow_up_tap_ok" == "true" ]] && printf 'true' || printf 'false' )" \
  --argjson followUpInputRequested "$( [[ "$follow_up_input_requested" == "true" ]] && printf 'true' || printf 'false' )" \
  --argjson followUpInputOk "$( [[ "$follow_up_input_ok" == "true" ]] && printf 'true' || printf 'false' )" \
  --argjson followUpForegroundReady "$( [[ "$follow_up_foreground_ready" == "true" ]] && printf 'true' || printf 'false' )" \
  --argjson followUpForegroundAttempts "$follow_up_foreground_attempts" \
  --argjson followUpForegroundTimeoutMs "$FOLLOW_UP_FOREGROUND_TIMEOUT_MS" \
  --argjson followUpForegroundPollIntervalMs "$FOLLOW_UP_FOREGROUND_POLL_INTERVAL_MS" \
  --argjson followUpStateOk "$( [[ "$follow_up_state_ok" == "true" ]] && printf 'true' || printf 'false' )" \
  --argjson followUpRequestedCount "$follow_up_requested_count" \
  --argjson followUpInputValueLength "${#FOLLOW_UP_INPUT_VALUE}" \
  '{
    appPackage: $appPackage,
    targetPackage: $targetPackage,
    classification: $classification,
    targetResetBeforeLaunch: $targetResetBeforeLaunch,
    targetResetApplied: $targetResetApplied,
    observeWindowMs: $observeWindowMs,
    pollIntervalMs: $pollIntervalMs,
    rounds: $rounds,
    statusSuccessCount: $statusSuccessCount,
    targetTopCount: $targetTopCount,
    firstTargetRound: $firstTargetRound,
    firstStatusFailureRound: $firstStatusFailureRound,
    followUp: {
      mode: $followUpMode,
      requested: $followUpRequested,
      requestedCount: $followUpRequestedCount,
      waitRequested: $followUpWaitRequested,
      waitText: (if $followUpWaitText == "" then null else $followUpWaitText end),
      waitMatchedText: (if $followUpWaitMatchedText == "" then null else $followUpWaitMatchedText end),
      waitOk: $followUpWaitOk,
      swipeRequested: $followUpSwipeRequested,
      swipeCoordinateMode: $followUpSwipeCoordinateMode,
      swipeStartX: $followUpSwipeStartX,
      swipeStartY: $followUpSwipeStartY,
      swipeEndX: $followUpSwipeEndX,
      swipeEndY: $followUpSwipeEndY,
      swipeDurationMs: $followUpSwipeDurationMs,
      swipeStrategy: (if $followUpSwipeStrategy == "" then null else $followUpSwipeStrategy end),
      swipeOk: $followUpSwipeOk,
      swipePrePackage: (if $followUpSwipePrePackage == "" then null else $followUpSwipePrePackage end),
      swipeBounds: $followUpSwipeBounds,
      swipeVisibleTextBefore: $followUpSwipeVisibleTextBefore,
      swipeVisibleTextAfter: $followUpSwipeVisibleTextAfter,
      swipeVisibleTextChanged: $followUpSwipeVisibleTextChanged,
      foregroundReady: $followUpForegroundReady,
      foregroundAttempts: (if $followUpRequested then $followUpForegroundAttempts else null end),
      foregroundTimeoutMs: (if $followUpRequested then $followUpForegroundTimeoutMs else null end),
      foregroundPollIntervalMs: (if $followUpRequested then $followUpForegroundPollIntervalMs else null end),
      foregroundPackage: (if $followUpForegroundPackage == "" then null else $followUpForegroundPackage end),
      tapRequested: $followUpTapRequested,
      tapText: (if $followUpTapText == "" then null else $followUpTapText end),
      tapContentDescription: (if $followUpTapContentDescription == "" then null else $followUpTapContentDescription end),
      tapResourceId: (if $followUpTapResourceId == "" then null else $followUpTapResourceId end),
      tapStrategy: (if $followUpTapStrategy == "" then null else $followUpTapStrategy end),
      tapOk: $followUpTapOk,
      inputRequested: $followUpInputRequested,
      inputText: (if $followUpInputText == "" then null else $followUpInputText end),
      inputContentDescription: (if $followUpInputContentDescription == "" then null else $followUpInputContentDescription end),
      inputResourceId: (if $followUpInputResourceId == "" then null else $followUpInputResourceId end),
      inputStrategy: (if $followUpInputStrategy == "" then null else $followUpInputStrategy end),
      inputValueLength: (if $followUpInputRequested then $followUpInputValueLength else null end),
      inputOk: $followUpInputOk,
      stateOk: $followUpStateOk,
      statePackage: (if $followUpStatePackage == "" then null else $followUpStatePackage end)
    },
    recovery: {
      httpCode: $recoveryHttpCode,
      ok: $recoveryOk,
      recoveredPackage: (if $recoveredPackage == "" then null else $recoveredPackage end)
    }
  }' >"$SUMMARY_JSON"

printf 'cross_app.target=%s classification=%s target_top_rounds=%s status_success_rounds=%s\n' \
  "$TARGET_PACKAGE" "$classification" "$target_top_count" "$status_success_count"
printf 'cross_app.first_target_round=%s first_status_failure_round=%s\n' \
  "$first_target_round" "$first_status_failure_round"
if [[ "$follow_up_requested" == "true" ]]; then
  printf 'cross_app.follow_up_mode=%s foreground_ready=%s wait_ok=%s swipe_ok=%s tap_ok=%s input_ok=%s swipe_text_changed=%s state_ok=%s\n' \
    "$follow_up_mode" \
    "$follow_up_foreground_ready" "$follow_up_wait_ok" "$follow_up_swipe_ok" "$follow_up_tap_ok" "$follow_up_input_ok" "$follow_up_swipe_visible_text_changed_json" "$follow_up_state_ok"
fi
printf 'recovery.http_code=%s recovery.ok=%s recovered_package=%s\n' \
  "$recovery_http_code" "$recovery_ok" "${recovered_package:-unknown}"

echo "local_host.ui_cross_app_probe=completed"
echo "artifacts.status=$STATUS_JSON"
echo "artifacts.timeline=$TIMELINE_JSONL"
echo "artifacts.summary=$SUMMARY_JSON"
