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
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { afterEach, describe, expect, it } from "vitest";

const repoRoot = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../../..",
);
const scriptPath = path.join(
  repoRoot,
  "apps/android/scripts/local-host-embedded-runtime-pod-doctor.sh",
);
const tempRoots: string[] = [];

function writeExecutableScript(filePath: string, content: string) {
  writeFileSync(filePath, content);
  chmodSync(filePath, 0o755);
}

function buildSummaryScript(
  summary: unknown,
  options: {
    exitCode?: number;
    stdoutLines?: string[];
  } = {},
) {
  const summaryJson = JSON.stringify(summary, null, 2);
  const stdout = (options.stdoutLines ?? [])
    .map((line) => `echo ${JSON.stringify(line)}`)
    .join("\n");
  return `#!/usr/bin/env bash
set -euo pipefail
mkdir -p "$OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR"
cat <<'EOF' >"$OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR/summary.json"
${summaryJson}
EOF
${stdout}
exit ${options.exitCode ?? 0}
`;
}

function buildBrowserSummaryScript(
  startSummary: unknown,
  confirmSummary: unknown,
  options: {
    startExitCode?: number;
    confirmExitCode?: number;
  } = {},
) {
  const startJson = JSON.stringify(startSummary, null, 2);
  const confirmJson = JSON.stringify(confirmSummary, null, 2);
  return `#!/usr/bin/env bash
set -euo pipefail
mkdir -p "$OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR"
if [[ "\${OPENCLAW_ANDROID_LOCAL_HOST_BROWSER_START:-1}" == "0" ]]; then
cat <<'EOF' >"$OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR/summary.json"
${confirmJson}
EOF
echo "browser_lane.mode=confirm"
exit ${options.confirmExitCode ?? 0}
fi
cat <<'EOF' >"$OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR/summary.json"
${startJson}
EOF
echo "browser_lane.mode=start"
exit ${options.startExitCode ?? 0}
`;
}

function buildBrowserSummary(overrides: Record<string, unknown> = {}) {
  return {
    ok: true,
    failedChecks: [],
    hint: null,
    browserDescribeAfter: {
      replayReady: true,
      authCredentialPresent: true,
    },
    runtimeDescribeAfter: {
      mainlineStatus: "process_runtime_active_session_live_proof_captured",
      recommendedNextSlice: "process_runtime_lane_hardening",
    },
    runtimeExecuteAfterBrowser: {
      longLivedProcessReady: true,
      processStatus: "standby",
      supervisionStatus: "active",
      activeSessionStatus: "ready",
      activeSessionObserved: true,
      activeSessionRecoveryReentryReady: true,
      activeSessionRestartContinuityReady: true,
      activeSessionValidationStatus: "validated",
      activeSessionValidationLeaseRenewalObserved: true,
      activeSessionValidationRecoveryReentryObserved: true,
      activeSessionValidationRestartContinuityObserved: true,
      activeSessionValidationDeviceProofRequired: false,
      activeSessionDeviceProofStatus: "verified",
      activeSessionDeviceProofObserved: true,
      activeSessionDeviceProofLiveProofRequired: false,
      activeSessionDeviceProofExpectedArtifactCount: 3,
      activeSessionDeviceProofCapturedArtifactCount: 3,
    },
    ...overrides,
  };
}

function runScenario(options: {
  json?: boolean;
  envToken?: string;
  podSummary?: unknown;
  browserStartSummary?: unknown;
  browserConfirmSummary?: unknown;
  podExitCode?: number;
  browserStartExitCode?: number;
  browserConfirmExitCode?: number;
}) {
  const tempRoot = mkdtempSync(
    path.join(os.tmpdir(), "openclaw-runtime-pod-doctor-"),
  );
  tempRoots.push(tempRoot);

  const artifactDir = path.join(tempRoot, "artifacts");
  mkdirSync(artifactDir, { recursive: true });

  const podSmokeScript = path.join(tempRoot, "pod-smoke.sh");
  const browserSmokeScript = path.join(tempRoot, "browser-smoke.sh");

  writeExecutableScript(
    podSmokeScript,
    buildSummaryScript(
      options.podSummary ?? {
        ok: true,
        failedChecks: [],
        hint: null,
      },
      {
        exitCode: options.podExitCode,
        stdoutLines: ["pod.smoke=completed"],
      },
    ),
  );
  writeExecutableScript(
    browserSmokeScript,
    buildBrowserSummaryScript(
      options.browserStartSummary ?? buildBrowserSummary(),
      options.browserConfirmSummary ?? buildBrowserSummary(),
      {
        startExitCode: options.browserStartExitCode,
        confirmExitCode: options.browserConfirmExitCode,
      },
    ),
  );

  const result = spawnSync(
    "bash",
    [scriptPath, ...(options.json ? ["--json"] : [])],
    {
      cwd: repoRoot,
      encoding: "utf8",
      env: {
        ...process.env,
        OPENCLAW_ANDROID_LOCAL_HOST_TOKEN:
          options.envToken ?? `ocrt_${"c".repeat(64)}`,
        OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR: artifactDir,
        OPENCLAW_ANDROID_LOCAL_HOST_POD_SMOKE_SCRIPT: podSmokeScript,
        OPENCLAW_ANDROID_LOCAL_HOST_BROWSER_SMOKE_SCRIPT: browserSmokeScript,
      },
    },
  );

  expect(result.status).toBe(0);

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

describe("local-host-embedded-runtime-pod-doctor", () => {
  it("keeps the live-proof classification when confirm replay preserves continuity", () => {
    const { result, summary } = runScenario({});

    expect(result.stdout).toContain(
      "runtime_doctor.confirm_browser_smoke.continuity_checked=true preserved=true",
    );
    expect(summary.classification).toBe(
      "process_runtime_active_session_live_proof_captured",
    );
    expect(summary.confirmBrowserLaneSmoke.required).toBe(true);
    expect(summary.confirmBrowserLaneSmoke.executed).toBe(true);
    expect(summary.confirmBrowserLaneSmoke.liveProofReplayed).toBe(true);
    expect(summary.confirmBrowserLaneSmoke.liveProofContinuity.checked).toBe(
      true,
    );
    expect(summary.confirmBrowserLaneSmoke.liveProofContinuity.preserved).toBe(
      true,
    );
    expect(summary.confirmBrowserLaneSmoke.liveProofContinuity.failedChecks).toEqual(
      [],
    );
  });

  it("downgrades to hardening pending when confirm replay loses restart continuity", () => {
    const { summary } = runScenario({
      browserConfirmSummary: buildBrowserSummary({
        runtimeExecuteAfterBrowser: {
          longLivedProcessReady: true,
          processStatus: "standby",
          supervisionStatus: "active",
          activeSessionStatus: "ready",
          activeSessionObserved: true,
          activeSessionRecoveryReentryReady: true,
          activeSessionRestartContinuityReady: false,
          activeSessionValidationStatus: "validated",
          activeSessionValidationLeaseRenewalObserved: true,
          activeSessionValidationRecoveryReentryObserved: true,
          activeSessionValidationRestartContinuityObserved: true,
          activeSessionValidationDeviceProofRequired: false,
          activeSessionDeviceProofStatus: "verified",
          activeSessionDeviceProofObserved: true,
          activeSessionDeviceProofLiveProofRequired: false,
          activeSessionDeviceProofExpectedArtifactCount: 3,
          activeSessionDeviceProofCapturedArtifactCount: 3,
        },
      }),
    });

    expect(summary.classification).toBe(
      "process_runtime_lane_hardening_pending",
    );
    expect(summary.confirmBrowserLaneSmoke.liveProofReplayed).toBe(false);
    expect(summary.confirmBrowserLaneSmoke.liveProofContinuity.checked).toBe(
      true,
    );
    expect(summary.confirmBrowserLaneSmoke.liveProofContinuity.preserved).toBe(
      false,
    );
    expect(summary.confirmBrowserLaneSmoke.liveProofContinuity.failedChecks).toContain(
      "activeSessionRestartContinuityReady",
    );
  });

  it("downgrades to hardening pending when confirm replay loses proof artifacts", () => {
    const { summary } = runScenario({
      browserConfirmSummary: buildBrowserSummary({
        runtimeExecuteAfterBrowser: {
          longLivedProcessReady: true,
          processStatus: "standby",
          supervisionStatus: "active",
          activeSessionStatus: "ready",
          activeSessionObserved: true,
          activeSessionRecoveryReentryReady: true,
          activeSessionRestartContinuityReady: true,
          activeSessionValidationStatus: "validated",
          activeSessionValidationLeaseRenewalObserved: true,
          activeSessionValidationRecoveryReentryObserved: true,
          activeSessionValidationRestartContinuityObserved: true,
          activeSessionValidationDeviceProofRequired: false,
          activeSessionDeviceProofStatus: "verified",
          activeSessionDeviceProofObserved: true,
          activeSessionDeviceProofLiveProofRequired: false,
          activeSessionDeviceProofExpectedArtifactCount: 3,
          activeSessionDeviceProofCapturedArtifactCount: 2,
        },
      }),
    });

    expect(summary.classification).toBe(
      "process_runtime_lane_hardening_pending",
    );
    expect(summary.confirmBrowserLaneSmoke.liveProofReplayed).toBe(false);
    expect(summary.confirmBrowserLaneSmoke.liveProofContinuity.checked).toBe(
      true,
    );
    expect(summary.confirmBrowserLaneSmoke.liveProofContinuity.preserved).toBe(
      false,
    );
    expect(summary.confirmBrowserLaneSmoke.liveProofContinuity.failedChecks).toContain(
      "activeSessionDeviceProofCapturedArtifactCount",
    );
  });

  it("prints the combined JSON summary when requested", () => {
    const { result } = runScenario({ json: true });

    const parsed = JSON.parse(result.stdout);
    expect(parsed.classification).toBe(
      "process_runtime_active_session_live_proof_captured",
    );
    expect(parsed.confirmBrowserLaneSmoke.liveProofContinuity.preserved).toBe(
      true,
    );
  });
});
