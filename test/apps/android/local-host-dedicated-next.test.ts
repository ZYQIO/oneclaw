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
  "apps/android/scripts/local-host-dedicated-next.sh",
);
const tempRoots: string[] = [];

function runDescribe(args: string[] = [], env: NodeJS.ProcessEnv = {}) {
  const result = spawnSync("bash", [scriptPath, "--describe", ...args], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      ...env,
    },
  });
  expect(result.status).toBe(0);
  return JSON.parse(result.stdout);
}

function writeExecutableScript(filePath: string, content: string) {
  writeFileSync(filePath, content);
  chmodSync(filePath, 0o755);
}

function buildSummaryScript(summary: unknown, stdoutLines: string[] = []): string {
  const summaryJson = JSON.stringify(summary, null, 2);
  const stdout = stdoutLines.map((line) => `echo ${JSON.stringify(line)}`).join("\n");
  return `#!/usr/bin/env bash
set -euo pipefail
mkdir -p "$OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR"
cat <<'EOF' >"$OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR/summary.json"
${summaryJson}
EOF
${stdout}
`;
}

function runScenario(options: {
  json?: boolean;
  readinessSummary: unknown;
  deviceOwnerSummary?: unknown;
  testdpcInstallSummary?: unknown;
  testdpcQrSummary?: unknown;
  postProvisionSummary?: unknown;
}) {
  const tempRoot = mkdtempSync(
    path.join(os.tmpdir(), "openclaw-dedicated-next-"),
  );
  tempRoots.push(tempRoot);

  const artifactDir = path.join(tempRoot, "artifacts");
  mkdirSync(artifactDir, { recursive: true });

  const readinessScript = path.join(tempRoot, "readiness.sh");
  const deviceOwnerScript = path.join(tempRoot, "device-owner.sh");
  const testdpcInstallScript = path.join(tempRoot, "testdpc-install.sh");
  const testdpcQrScript = path.join(tempRoot, "testdpc-qr.sh");
  const postProvisionScript = path.join(tempRoot, "post-provision.sh");

  writeExecutableScript(
    readinessScript,
    buildSummaryScript(options.readinessSummary, ["readiness.ok=true"]),
  );
  writeExecutableScript(
    deviceOwnerScript,
    buildSummaryScript(options.deviceOwnerSummary ?? { deviceOwnerReady: true }, [
      "device_owner.ready=true",
    ]),
  );
  writeExecutableScript(
    testdpcInstallScript,
    buildSummaryScript(options.testdpcInstallSummary ?? { install: { command: "adb install" } }, [
      "testdpc.install.command=adb install",
    ]),
  );
  writeExecutableScript(
    testdpcQrScript,
    buildSummaryScript(options.testdpcQrSummary ?? { checksumMode: "package" }, [
      "testdpc.qr.generated=true",
    ]),
  );
  writeExecutableScript(
    postProvisionScript,
    buildSummaryScript(options.postProvisionSummary ?? { packageInstalled: true }, [
      "post_provision.ready=true",
    ]),
  );

  const result = spawnSync(
    "bash",
    [
      scriptPath,
      ...(options.json ? ["--json"] : []),
    ],
    {
      cwd: repoRoot,
      encoding: "utf8",
      env: {
        ...process.env,
        OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR: artifactDir,
        OPENCLAW_ANDROID_LOCAL_HOST_DEDICATED_READINESS_SCRIPT: readinessScript,
        OPENCLAW_ANDROID_LOCAL_HOST_DEDICATED_DEVICE_OWNER_SCRIPT: deviceOwnerScript,
        OPENCLAW_ANDROID_LOCAL_HOST_DEDICATED_TESTDPC_INSTALL_SCRIPT: testdpcInstallScript,
        OPENCLAW_ANDROID_LOCAL_HOST_DEDICATED_TESTDPC_QR_SCRIPT: testdpcQrScript,
        OPENCLAW_ANDROID_LOCAL_HOST_DEDICATED_POST_PROVISION_SCRIPT: postProvisionScript,
      },
    },
  );

  expect(result.status).toBe(0);

  const summaryPath = path.join(artifactDir, "summary.json");
  const summary = JSON.parse(readFileSync(summaryPath, "utf8"));
  return { result, summary };
}

afterEach(() => {
  for (const tempRoot of tempRoots.splice(0)) {
    rmSync(tempRoot, { force: true, recursive: true });
  }
});

describe("local-host-dedicated-next", () => {
  it("describes the wrapper layout without touching adb-dependent helpers", () => {
    const summary = runDescribe([], {
      OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR: "/tmp/openclaw-dedicated-next-describe",
      OPENCLAW_ANDROID_LOCAL_HOST_DEDICATED_READINESS_SCRIPT: "/tmp/readiness.sh",
      OPENCLAW_ANDROID_LOCAL_HOST_DEDICATED_TESTDPC_INSTALL_SCRIPT:
        "/tmp/testdpc-install.sh",
    });

    expect(summary.describeOnly).toBe(true);
    expect(summary.packageCommand).toBe("pnpm android:local-host:dedicated:next");
    expect(summary.readiness.scriptPath).toBe("/tmp/readiness.sh");
    expect(summary.next.assumedAction).toBeNull();
    expect(summary.actionMap["testdpc-install"].scriptPath).toBe(
      "/tmp/testdpc-install.sh",
    );
    expect(summary.artifacts.readinessDir).toBe("readiness");
  });

  it("can preview an assumed next action offline", () => {
    const summary = runDescribe(["--assume-action", "testdpc-qr"], {
      OPENCLAW_ANDROID_LOCAL_HOST_DEDICATED_TESTDPC_QR_SCRIPT: "/tmp/testdpc-qr.sh",
    });

    expect(summary.next.assumedAction).toBe("testdpc-qr");
    expect(summary.next.assumedCommand).toBe(
      "pnpm android:local-host:dedicated:testdpc-qr",
    );
    expect(summary.next.scriptPath).toBe("/tmp/testdpc-qr.sh");
  });

  it("reuses readiness and runs the recommended device-owner dry-run", () => {
    const { result, summary } = runScenario({
      readinessSummary: {
        recommendedAction: "device-owner",
        recommendedCommand: "pnpm android:local-host:dedicated:device-owner",
        viability: {
          deviceOwnerViaAdbReady: true,
        },
      },
      deviceOwnerSummary: {
        deviceOwnerReady: true,
        provisionCommand:
          "adb shell dpm set-device-owner 'com.afwsamples.testdpc/.DeviceAdminReceiver'",
      },
    });

    expect(summary.readiness.recommendedAction).toBe("device-owner");
    expect(summary.next.action).toBe("device-owner");
    expect(summary.next.command).toBe(
      "pnpm android:local-host:dedicated:device-owner",
    );
    expect(summary.next.executed).toBe(true);
    expect(summary.next.summary.deviceOwnerReady).toBe(true);
    expect(result.stdout).toContain("dedicated.next.action=device-owner");
  });

  it("routes testdpc-install recommendations through the install dry-run", () => {
    const { summary } = runScenario({
      readinessSummary: {
        recommendedAction: "testdpc-install",
        recommendedCommand: "pnpm android:local-host:dedicated:testdpc-install",
      },
      testdpcInstallSummary: {
        install: {
          attempted: false,
          command: "adb install -r -d '/tmp/testdpc.apk'",
        },
      },
    });

    expect(summary.next.action).toBe("testdpc-install");
    expect(summary.next.summary.install.command).toBe(
      "adb install -r -d '/tmp/testdpc.apk'",
    );
  });

  it("prints combined JSON output when requested", () => {
    const { result } = runScenario({
      json: true,
      readinessSummary: {
        recommendedAction: "testdpc-qr",
        recommendedCommand: "pnpm android:local-host:dedicated:testdpc-qr",
      },
      testdpcQrSummary: {
        checksumMode: "package",
        payloadPath: "/tmp/payload.json",
      },
    });

    const parsed = JSON.parse(result.stdout);
    expect(parsed.packageCommand).toBe("pnpm android:local-host:dedicated:next");
    expect(parsed.next.action).toBe("testdpc-qr");
    expect(parsed.next.summary.checksumMode).toBe("package");
  });
});
