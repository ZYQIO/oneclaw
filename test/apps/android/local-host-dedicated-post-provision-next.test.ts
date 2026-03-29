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
  "apps/android/scripts/local-host-dedicated-post-provision-next.sh",
);
const tempRoots: string[] = [];

function writeExecutableScript(filePath: string, content: string) {
  writeFileSync(filePath, content);
  chmodSync(filePath, 0o755);
}

function buildSummaryScript(
  defaultSummary: unknown,
  stdoutLines: string[] = [],
  launchedSummary?: unknown,
): string {
  const defaultJson = JSON.stringify(defaultSummary, null, 2);
  const launchedJson = JSON.stringify(launchedSummary ?? defaultSummary, null, 2);
  const stdout = stdoutLines.map((line) => `echo ${JSON.stringify(line)}`).join("\n");
  return `#!/usr/bin/env bash
set -euo pipefail
mkdir -p "$OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR"
if [[ "\${1:-}" == "--launch" ]]; then
cat <<'EOF' >"$OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR/summary.json"
${launchedJson}
EOF
else
cat <<'EOF' >"$OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR/summary.json"
${defaultJson}
EOF
fi
${stdout}
`;
}

function runScenario(options: {
  json?: boolean;
  postProvisionSummary: unknown;
  postProvisionLaunchSummary?: unknown;
  testdpcQrSummary?: unknown;
  testdpcKioskSummary?: unknown;
}) {
  const tempRoot = mkdtempSync(
    path.join(os.tmpdir(), "openclaw-dedicated-post-provision-next-"),
  );
  tempRoots.push(tempRoot);

  const artifactDir = path.join(tempRoot, "artifacts");
  mkdirSync(artifactDir, { recursive: true });

  const postProvisionScript = path.join(tempRoot, "post-provision.sh");
  const testdpcQrScript = path.join(tempRoot, "testdpc-qr.sh");
  const testdpcKioskScript = path.join(tempRoot, "testdpc-kiosk.sh");

  writeExecutableScript(
    postProvisionScript,
    buildSummaryScript(
      options.postProvisionSummary,
      ["post_provision.ready=true"],
      options.postProvisionLaunchSummary,
    ),
  );
  writeExecutableScript(
    testdpcQrScript,
    buildSummaryScript(options.testdpcQrSummary ?? { checksumMode: "package" }, [
      "testdpc.qr.generated=true",
    ]),
  );
  writeExecutableScript(
    testdpcKioskScript,
    buildSummaryScript(options.testdpcKioskSummary ?? { kiosk: { readyForApply: true } }, [
      "testdpc.kiosk.ready=true",
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
        OPENCLAW_ANDROID_LOCAL_HOST_DEDICATED_POST_PROVISION_SCRIPT: postProvisionScript,
        OPENCLAW_ANDROID_LOCAL_HOST_DEDICATED_TESTDPC_QR_SCRIPT: testdpcQrScript,
        OPENCLAW_ANDROID_LOCAL_HOST_DEDICATED_TESTDPC_KIOSK_SCRIPT: testdpcKioskScript,
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

describe("local-host-dedicated-post-provision-next", () => {
  it("routes testdpc-kiosk recommendations through the kiosk dry-run", () => {
    const { result, summary } = runScenario({
      postProvisionSummary: {
        recommendedAction: "testdpc-kiosk",
        recommendedCommand: "pnpm android:local-host:dedicated:testdpc-kiosk",
      },
      testdpcKioskSummary: {
        kiosk: {
          readyForApply: false,
        },
      },
    });

    expect(summary.postProvision.recommendedAction).toBe("testdpc-kiosk");
    expect(summary.next.action).toBe("testdpc-kiosk");
    expect(summary.next.executed).toBe(true);
    expect(summary.next.summary.kiosk.readyForApply).toBe(false);
    expect(result.stdout).toContain("dedicated.post_provision_next.action=testdpc-kiosk");
  });

  it("routes launch-openclaw recommendations back through post-provision --launch", () => {
    const { summary } = runScenario({
      postProvisionSummary: {
        recommendedAction: "launch-openclaw",
        recommendedCommand: "pnpm android:local-host:dedicated:post-provision -- --launch",
        viability: {
          postProvisionReady: true,
        },
      },
      postProvisionLaunchSummary: {
        recommendedAction: "healthy",
        launch: {
          attempted: true,
          succeeded: true,
        },
        viability: {
          postProvisionReady: true,
        },
      },
    });

    expect(summary.next.action).toBe("launch-openclaw");
    expect(summary.next.scriptArg).toBe("--launch");
    expect(summary.next.summary.launch.succeeded).toBe(true);
  });

  it("prints combined JSON output when requested", () => {
    const { result } = runScenario({
      json: true,
      postProvisionSummary: {
        recommendedAction: "testdpc-qr",
        recommendedCommand: "pnpm android:local-host:dedicated:testdpc-qr",
      },
      testdpcQrSummary: {
        checksumMode: "package",
        payloadPath: "/tmp/testdpc-qr.json",
      },
    });

    const parsed = JSON.parse(result.stdout);
    expect(parsed.next.action).toBe("testdpc-qr");
    expect(parsed.next.summary.checksumMode).toBe("package");
  });
});
