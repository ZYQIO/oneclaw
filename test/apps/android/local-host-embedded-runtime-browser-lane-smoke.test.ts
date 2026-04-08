import { spawnSync } from "node:child_process";
import {
  chmodSync,
  mkdtempSync,
  mkdirSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { afterEach, describe, expect, it } from "vitest";

type Scenario = {
  runtimeAfterBrowserPayload?: Record<string, unknown>;
};

const repoRoot = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../../..",
);
const scriptPath = path.join(
  repoRoot,
  "apps/android/scripts/local-host-embedded-runtime-browser-lane-smoke.sh",
);
const tempRoots: string[] = [];

function buildRuntimeAfterBrowserPayload(
  overrides: Record<string, unknown> = {},
) {
  return {
    command: "pod.runtime.execute",
    taskId: "runtime-smoke",
    runtimeHomeReady: true,
    desktopLongLivedProcessReady: true,
    desktopProcessStatus: "standby",
    desktopProcessSupervisionStatus: "active",
    desktopProcessActiveSessionStatus: "ready",
    desktopProcessActiveSessionObserved: true,
    desktopProcessActiveSessionRecoveryReentryReady: true,
    desktopProcessActiveSessionRestartContinuityReady: true,
    desktopProcessActiveSessionValidationStatus: "validated",
    desktopProcessActiveSessionValidationLeaseRenewalObserved: true,
    desktopProcessActiveSessionValidationRecoveryReentryObserved: true,
    desktopProcessActiveSessionValidationRestartContinuityObserved: true,
    desktopProcessActiveSessionValidationDeviceProofRequired: false,
    desktopProcessActiveSessionDeviceProofStatus: "verified",
    desktopProcessActiveSessionDeviceProofObserved: true,
    desktopProcessActiveSessionDeviceProofLiveProofRequired: false,
    desktopProcessActiveSessionDeviceProofExpectedArtifactCount: 3,
    desktopProcessActiveSessionDeviceProofCapturedArtifactCount: 3,
    ...overrides,
  };
}

function buildFakeCurlScript(scenario: Scenario): string {
  const statusJson = JSON.stringify(
    {
      ok: true,
      host: {
        embeddedRuntimePod: {
          ready: true,
          manifestVersion: "0.17.0",
        },
      },
    },
    null,
    2,
  );
  const capabilitiesJson = JSON.stringify(
    {
      writeEnabled: true,
      commands: [
        "pod.browser.auth.start",
        "pod.browser.describe",
        "pod.desktop.materialize",
        "pod.runtime.describe",
        "pod.runtime.execute",
      ],
    },
    null,
    2,
  );
  const desktopMaterializeJson = JSON.stringify(
    {
      ok: true,
      payload: {
        command: "pod.desktop.materialize",
        profileId: "openclaw-desktop-host",
        desktopHomeReady: true,
        executionCount: 1,
      },
    },
    null,
    2,
  );
  const runtimeBeforeBrowserJson = JSON.stringify(
    {
      ok: true,
      payload: {
        command: "pod.runtime.execute",
        taskId: "runtime-smoke",
        runtimeHomeReady: true,
      },
    },
    null,
    2,
  );
  const runtimeAfterBrowserJson = JSON.stringify(
    {
      ok: true,
      payload: buildRuntimeAfterBrowserPayload(
        scenario.runtimeAfterBrowserPayload ?? {},
      ),
    },
    null,
    2,
  );
  const toolExecuteJson = JSON.stringify(
    {
      ok: true,
      payload: {
        command: "pod.runtime.execute",
        taskId: "tool-brief-inspect",
        toolId: "openclaw-tool-brief-inspect",
      },
    },
    null,
    2,
  );
  const pluginExecuteJson = JSON.stringify(
    {
      ok: true,
      payload: {
        command: "pod.runtime.execute",
        taskId: "plugin-allowlist-inspect",
        pluginId: "openclaw-plugin-host-placeholder",
        runtimeHomeReady: true,
        executionCount: 1,
        pluginResultFilePath: "/tmp/openclaw/work/plugin-result.json",
        pluginResult: {
          profileSource: "packaged",
        },
        packagedPluginDescriptorPresent: true,
      },
    },
    null,
    2,
  );
  const browserDescribeBeforeJson = JSON.stringify(
    {
      ok: true,
      payload: {
        command: "pod.browser.describe",
        browserStatus: "ready",
        browserAuthFlowCount: 1,
        recommendedFlowId: "openai-codex-oauth",
        browserReplayReady: false,
        authInProgress: false,
        authCredentialPresent: false,
        stateFilePath: "/tmp/openclaw/browser-state.json",
        logFilePath: "/tmp/openclaw/browser-log.jsonl",
      },
    },
    null,
    2,
  );
  const browserDescribeAfterJson = JSON.stringify(
    {
      ok: true,
      payload: {
        command: "pod.browser.describe",
        browserStatus: "replayed",
        browserReplayReady: true,
        authInProgress: false,
        authCredentialPresent: true,
        lastLaunchStatus: "completed",
        lastLaunchFlowId: "openai-codex-oauth",
        stateFilePath: "/tmp/openclaw/browser-state.json",
        logFilePath: "/tmp/openclaw/browser-log.jsonl",
      },
    },
    null,
    2,
  );
  const browserStartJson = JSON.stringify(
    {
      ok: true,
      payload: {
        command: "pod.browser.auth.start",
        flowId: "openai-codex-oauth",
        launchStatus: "started",
        authInProgress: true,
      },
    },
    null,
    2,
  );
  const runtimeDescribeJson = JSON.stringify(
    {
      ok: true,
      payload: {
        command: "pod.runtime.describe",
        mainlineStatus: "process_runtime_active_session_live_proof_captured",
        recommendedNextSlice: "process_runtime_lane_hardening",
        browserReplayReady: true,
        browserLaunchStateCount: 1,
        runtimePluginExecutionStateCount: 1,
      },
    },
    null,
    2,
  );

  return `#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "$0")" && pwd)"
browser_describe_count_file="$script_dir/browser_describe_count.txt"
runtime_smoke_count_file="$script_dir/runtime_smoke_count.txt"

url=""
body=""
while (($#)); do
  case "$1" in
    --fail|--silent|--show-error)
      shift
      ;;
    -H|-X|--max-time)
      shift 2
      ;;
    -d)
      body="$2"
      shift 2
      ;;
    http://*|https://*)
      url="$1"
      shift
      ;;
    *)
      shift
      ;;
  esac
done

case "$url" in
  */api/local-host/v1/status)
    cat <<'EOF'
${statusJson}
EOF
    ;;
  */api/local-host/v1/invoke/capabilities)
    cat <<'EOF'
${capabilitiesJson}
EOF
    ;;
  */api/local-host/v1/invoke)
    case "$body" in
      *'"command":"pod.desktop.materialize"'*)
        cat <<'EOF'
${desktopMaterializeJson}
EOF
        ;;
      *'"command":"pod.runtime.execute"'*'"taskId":"runtime-smoke"'*)
        runtime_smoke_count=0
        if [[ -f "$runtime_smoke_count_file" ]]; then
          runtime_smoke_count="$(cat "$runtime_smoke_count_file")"
        fi
        runtime_smoke_count=$((runtime_smoke_count + 1))
        printf '%s' "$runtime_smoke_count" >"$runtime_smoke_count_file"
        if [[ "$runtime_smoke_count" -ge 2 ]]; then
          cat <<'EOF'
${runtimeAfterBrowserJson}
EOF
        else
          cat <<'EOF'
${runtimeBeforeBrowserJson}
EOF
        fi
        ;;
      *'"command":"pod.runtime.execute"'*'"taskId":"tool-brief-inspect"'*)
        cat <<'EOF'
${toolExecuteJson}
EOF
        ;;
      *'"command":"pod.runtime.execute"'*'"taskId":"plugin-allowlist-inspect"'*)
        cat <<'EOF'
${pluginExecuteJson}
EOF
        ;;
      *'"command":"pod.browser.describe"'*)
        browser_describe_count=0
        if [[ -f "$browser_describe_count_file" ]]; then
          browser_describe_count="$(cat "$browser_describe_count_file")"
        fi
        browser_describe_count=$((browser_describe_count + 1))
        printf '%s' "$browser_describe_count" >"$browser_describe_count_file"
        if [[ "$browser_describe_count" -ge 2 ]]; then
          cat <<'EOF'
${browserDescribeAfterJson}
EOF
        else
          cat <<'EOF'
${browserDescribeBeforeJson}
EOF
        fi
        ;;
      *'"command":"pod.browser.auth.start"'*)
        cat <<'EOF'
${browserStartJson}
EOF
        ;;
      *'"command":"pod.runtime.describe"'*)
        cat <<'EOF'
${runtimeDescribeJson}
EOF
        ;;
      *)
        echo "unexpected invoke body: $body" >&2
        exit 1
        ;;
    esac
    ;;
  *)
    echo "unexpected curl url: $url" >&2
    exit 1
    ;;
esac
`;
}

function runScenario(scenario: Scenario = {}) {
  const tempRoot = mkdtempSync(
    path.join(os.tmpdir(), "openclaw-browser-lane-smoke-"),
  );
  tempRoots.push(tempRoot);

  const artifactDir = path.join(tempRoot, "artifacts");
  const binDir = path.join(tempRoot, "bin");
  mkdirSync(artifactDir, { recursive: true });
  mkdirSync(binDir, { recursive: true });

  const curlPath = path.join(binDir, "curl");
  writeFileSync(curlPath, buildFakeCurlScript(scenario));
  chmodSync(curlPath, 0o755);

  const result = spawnSync("bash", [scriptPath], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR: artifactDir,
      OPENCLAW_ANDROID_LOCAL_HOST_BASE_URL: "http://127.0.0.1:3945",
      OPENCLAW_ANDROID_LOCAL_HOST_TOKEN: "test-token",
      PATH: `${binDir}:${process.env.PATH ?? ""}`,
    },
  });

  const summary = JSON.parse(
    readFileSync(path.join(artifactDir, "summary.json"), "utf8"),
  );
  return { result, summary };
}

afterEach(() => {
  for (const tempRoot of tempRoots.splice(0)) {
    rmSync(tempRoot, { recursive: true, force: true });
  }
});

describe("local-host-embedded-runtime-browser-lane-smoke", () => {
  it("records the validation observations and device-proof artifact counts on success", () => {
    const { result, summary } = runScenario();

    expect(result.status).toBe(0);
    expect(result.stdout).toContain("browser_lane.smoke=completed");
    expect(summary.ok).toBe(true);
    expect(
      summary.runtimeExecuteAfterBrowser.activeSessionValidationLeaseRenewalObserved,
    ).toBe(true);
    expect(
      summary.runtimeExecuteAfterBrowser
        .activeSessionValidationRecoveryReentryObserved,
    ).toBe(true);
    expect(
      summary.runtimeExecuteAfterBrowser
        .activeSessionValidationRestartContinuityObserved,
    ).toBe(true);
    expect(
      summary.runtimeExecuteAfterBrowser.activeSessionDeviceProofLiveProofRequired,
    ).toBe(false);
    expect(
      summary.runtimeExecuteAfterBrowser.activeSessionDeviceProofCapturedArtifactCount,
    ).toBe(3);
    expect(
      summary.runtimeExecuteAfterBrowser.activeSessionDeviceProofExpectedArtifactCount,
    ).toBe(3);
  });

  it("fails when the browser-aligned runtime summary no longer proves lease renewal", () => {
    const { result, summary } = runScenario({
      runtimeAfterBrowserPayload: {
        desktopProcessActiveSessionValidationLeaseRenewalObserved: false,
      },
    });

    expect(result.status).toBe(1);
    expect(result.stderr).toContain("browser_lane.smoke=failed");
    expect(summary.ok).toBe(false);
    expect(summary.failedChecks).toContain(
      "pod_runtime_execute_after_browser_validation_lease_renewal_not_observed",
    );
  });

  it("fails when captured proof artifacts fall below the expected count", () => {
    const { result, summary } = runScenario({
      runtimeAfterBrowserPayload: {
        desktopProcessActiveSessionDeviceProofCapturedArtifactCount: 2,
      },
    });

    expect(result.status).toBe(1);
    expect(result.stderr).toContain("browser_lane.smoke=failed");
    expect(summary.ok).toBe(false);
    expect(summary.failedChecks).toContain(
      "pod_runtime_execute_after_browser_device_proof_captured_artifact_count_invalid",
    );
  });
});
