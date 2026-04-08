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
  "apps/android/scripts/local-host-embedded-runtime-pod-stability.sh",
);
const tempRoots: string[] = [];

function buildDoctorSummary(
  overrides: {
    classification?: string;
    recommendedAction?: string;
    browserMainlineStatus?: string;
    browserRecommendedNextSlice?: string;
    confirmMainlineStatus?: string;
    confirmRecommendedNextSlice?: string;
    liveProofReplayed?: boolean;
    continuityPreserved?: boolean;
    continuityFailedChecks?: string[];
    validationLeaseRenewalObserved?: boolean;
    validationRecoveryReentryObserved?: boolean;
    validationRestartContinuityObserved?: boolean;
    deviceProofExpectedArtifactCount?: number;
    deviceProofCapturedArtifactCount?: number;
  } = {},
) {
  return {
    classification:
      overrides.classification ??
      "process_runtime_active_session_live_proof_captured",
    recommendedAction:
      overrides.recommendedAction ?? "preserve-live-proof-baseline",
    recommendedCommand: null,
    podSmoke: {
      summary: {
        status: {
          manifestVersion: "0.17.0",
          verifiedFileCount: 26,
        },
      },
    },
    browserLaneSmoke: {
      ok: true,
      mainlineStatus:
        overrides.browserMainlineStatus ??
        "process_runtime_active_session_live_proof_captured",
      recommendedNextSlice:
        overrides.browserRecommendedNextSlice ?? "process_runtime_lane_hardening",
      summary: {
        runtimeExecuteAfterBrowser: {
          activeSessionObserved: true,
          activeSessionValidationStatus: "validated",
          activeSessionValidationLeaseRenewalObserved:
            overrides.validationLeaseRenewalObserved ?? true,
          activeSessionValidationRecoveryReentryObserved:
            overrides.validationRecoveryReentryObserved ?? true,
          activeSessionValidationRestartContinuityObserved:
            overrides.validationRestartContinuityObserved ?? true,
          activeSessionDeviceProofStatus: "verified",
          activeSessionDeviceProofExpectedArtifactCount:
            overrides.deviceProofExpectedArtifactCount ?? 3,
          activeSessionDeviceProofCapturedArtifactCount:
            overrides.deviceProofCapturedArtifactCount ?? 3,
        },
      },
    },
    confirmBrowserLaneSmoke: {
      required: true,
      executed: true,
      ok: true,
      mainlineStatus:
        overrides.confirmMainlineStatus ??
        "process_runtime_active_session_live_proof_captured",
      recommendedNextSlice:
        overrides.confirmRecommendedNextSlice ?? "process_runtime_lane_hardening",
      liveProofReplayed: overrides.liveProofReplayed ?? true,
      liveProofContinuity: {
        checked: true,
        preserved: overrides.continuityPreserved ?? true,
        failedChecks: overrides.continuityFailedChecks ?? [],
      },
    },
  };
}

function buildDoctorScript(
  tempRoot: string,
  runs: Array<{ summary?: unknown; exitCode?: number }>,
) {
  const countFile = path.join(tempRoot, "doctor-count.txt");
  const cases = runs
    .map((run, index) => {
      const summaryJson = JSON.stringify(run.summary ?? {}, null, 2);
      return `  ${index + 1})
    cat <<'EOF' >"$OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR/summary.json"
${summaryJson}
EOF
    if [[ "$json_mode" == "true" ]]; then
      cat <<'EOF'
${summaryJson}
EOF
    fi
    exit ${run.exitCode ?? 0}
    ;;`;
    })
    .join("\n");

  return `#!/usr/bin/env bash
set -euo pipefail

mkdir -p "$OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR"

count=0
if [[ -f "${countFile}" ]]; then
  count="$(cat "${countFile}")"
fi
count=$((count + 1))
printf '%s' "$count" >"${countFile}"

json_mode=false
for arg in "$@"; do
  if [[ "$arg" == "--json" ]]; then
    json_mode=true
  fi
done

case "$count" in
${cases}
  *)
    echo "unexpected stability doctor iteration: $count" >&2
    exit 1
    ;;
esac
`;
}

function buildAdbScript(
  tempRoot: string,
  options: { failOnCall?: number } = {},
) {
  const countFile = path.join(tempRoot, "adb-count.txt");
  const logFile = path.join(tempRoot, "adb-log.txt");
  return `#!/usr/bin/env bash
set -euo pipefail

count=0
if [[ -f "${countFile}" ]]; then
  count="$(cat "${countFile}")"
fi
count=$((count + 1))
printf '%s' "$count" >"${countFile}"
printf '%s\\n' "$*" >>"${logFile}"

if [[ "$count" == "${options.failOnCall ?? 0}" ]]; then
  echo "adb failure on call $count" >&2
  exit 1
fi

if [[ "$#" -ge 4 && "$1" == "shell" && "$2" == "am" && "$3" == "start" ]]; then
  echo "Status: ok"
fi
`;
}

function writeExecutableScript(filePath: string, content: string) {
  writeFileSync(filePath, content);
  chmodSync(filePath, 0o755);
}

function runScenario(options: {
  json?: boolean;
  iterations?: number;
  runs: Array<{ summary?: unknown; exitCode?: number }>;
  restartAppBetweenIterations?: boolean;
  adbFailOnCall?: number;
}) {
  const tempRoot = mkdtempSync(
    path.join(os.tmpdir(), "openclaw-runtime-stability-"),
  );
  tempRoots.push(tempRoot);

  const artifactDir = path.join(tempRoot, "artifacts");
  mkdirSync(artifactDir, { recursive: true });

  const doctorScript = path.join(tempRoot, "doctor.sh");
  writeExecutableScript(doctorScript, buildDoctorScript(tempRoot, options.runs));

  let adbScript: string | undefined;
  if (options.restartAppBetweenIterations) {
    adbScript = path.join(tempRoot, "adb");
    writeExecutableScript(
      adbScript,
      buildAdbScript(tempRoot, { failOnCall: options.adbFailOnCall }),
    );
  }

  const result = spawnSync(
    "bash",
    [
      scriptPath,
      "--iterations",
      String(options.iterations ?? options.runs.length),
      "--delay-sec",
      "0",
      ...(options.restartAppBetweenIterations
        ? ["--restart-app-between-iterations"]
        : []),
      ...(options.json ? ["--json"] : []),
    ],
    {
      cwd: repoRoot,
      encoding: "utf8",
      env: {
        ...process.env,
        OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR: artifactDir,
        OPENCLAW_ANDROID_LOCAL_HOST_EMBEDDED_RUNTIME_POD_DOCTOR_SCRIPT:
          doctorScript,
        ...(adbScript
          ? { OPENCLAW_ANDROID_LOCAL_HOST_ADB_BIN: adbScript }
          : {}),
      },
    },
  );

  const summary = JSON.parse(
    readFileSync(path.join(artifactDir, "summary.json"), "utf8"),
  );
  return { result, summary, tempRoot };
}

afterEach(() => {
  for (const tempRoot of tempRoots.splice(0)) {
    rmSync(tempRoot, { recursive: true, force: true });
  }
});

describe("local-host-embedded-runtime-pod-stability", () => {
  it("reports stable when every doctor iteration preserves live proof", () => {
    const { result, summary } = runScenario({
      runs: [
        { summary: buildDoctorSummary() },
        { summary: buildDoctorSummary() },
      ],
    });

    expect(result.status).toBe(0);
    expect(result.stdout).toContain("runtime_pod_stability.ok=true");
    expect(summary.ok).toBe(true);
    expect(summary.passedIterationCount).toBe(2);
    expect(summary.failedIterationCount).toBe(0);
    expect(summary.liveProofReplayedCount).toBe(2);
    expect(summary.continuityPreservedCount).toBe(2);
    expect(summary.stableCapturedArtifactCount).toBe(3);
  });

  it("can perturb the lane with an app restart between iterations", () => {
    const { result, summary, tempRoot } = runScenario({
      runs: [
        { summary: buildDoctorSummary() },
        { summary: buildDoctorSummary() },
      ],
      restartAppBetweenIterations: true,
    });

    expect(result.status).toBe(0);
    expect(summary.ok).toBe(true);
    expect(summary.perturbationMode).toBe("app_restart_between_iterations");
    expect(summary.perturbationAppliedCount).toBe(1);
    expect(summary.perturbationFailureCount).toBe(0);
    expect(summary.iterations[0].betweenIterationsPerturbation.executed).toBe(true);
    expect(summary.iterations[0].betweenIterationsPerturbation.ok).toBe(true);
    expect(summary.iterations[0].betweenIterationsPerturbation.appPackage).toBe(
      "ai.openclaw.app",
    );
    const restartLog = readFileSync(path.join(tempRoot, "adb-log.txt"), "utf8");
    expect(restartLog).toContain("shell am force-stop ai.openclaw.app");
    expect(restartLog).toContain("shell am start -W -n ai.openclaw.app/.MainActivity");
  });

  it("fails when a later doctor iteration drops back to hardening pending", () => {
    const { result, summary } = runScenario({
      runs: [
        { summary: buildDoctorSummary() },
        {
          summary: buildDoctorSummary({
            classification: "process_runtime_lane_hardening_pending",
            recommendedAction: "confirm-live-proof-replay",
            confirmMainlineStatus: "process_runtime_active_session_live_proof_captured",
            liveProofReplayed: false,
            continuityPreserved: false,
            continuityFailedChecks: ["activeSessionDeviceProofCapturedArtifactCount"],
            deviceProofCapturedArtifactCount: 2,
          }),
        },
      ],
    });

    expect(result.status).toBe(1);
    expect(result.stderr).toContain("runtime_pod_stability=failed");
    expect(summary.ok).toBe(false);
    expect(summary.failedIterationCount).toBe(1);
    expect(summary.failedIterations[0].iteration).toBe(2);
    expect(summary.failedIterations[0].failureReasons).toContain(
      "classification_unexpected",
    );
    expect(summary.failedIterations[0].failureReasons).toContain(
      "live_proof_not_replayed",
    );
    expect(summary.failedIterations[0].failureReasons).toContain(
      "live_proof_continuity_not_preserved",
    );
  });

  it("fails when the between-iteration app restart perturbation fails", () => {
    const { result, summary } = runScenario({
      runs: [
        { summary: buildDoctorSummary() },
        { summary: buildDoctorSummary() },
      ],
      restartAppBetweenIterations: true,
      adbFailOnCall: 2,
    });

    expect(result.status).toBe(1);
    expect(summary.ok).toBe(false);
    expect(summary.perturbationAppliedCount).toBe(1);
    expect(summary.perturbationFailureCount).toBe(1);
    expect(summary.failedIterations[0].iteration).toBe(1);
    expect(summary.failedIterations[0].failureReasons).toContain(
      "between_iteration_perturbation_failed",
    );
  });

  it("prints combined JSON when requested", () => {
    const { result } = runScenario({
      json: true,
      runs: [{ summary: buildDoctorSummary() }],
    });

    expect(result.status).toBe(0);
    const parsed = JSON.parse(result.stdout);
    expect(parsed.packageCommand).toBe(
      "pnpm android:local-host:embedded-runtime-pod:stability",
    );
    expect(parsed.ok).toBe(true);
    expect(parsed.iterations[0].classification).toBe(
      "process_runtime_active_session_live_proof_captured",
    );
  });
});
