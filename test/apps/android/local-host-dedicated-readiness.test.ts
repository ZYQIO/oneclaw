import { spawnSync } from "node:child_process";
import { mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync, chmodSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { afterEach, describe, expect, it } from "vitest";

type Scenario = {
  accountsCount: number;
  dpcInstalled: boolean;
  hasDeviceOwner: boolean;
  userCount: number;
};

const repoRoot = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../../..",
);
const scriptPath = path.join(
  repoRoot,
  "apps/android/scripts/local-host-dedicated-readiness.sh",
);
const tempRoots: string[] = [];

function buildFakeAdbScript(scenario: Scenario): string {
  const accountLines =
    scenario.accountsCount > 0
      ? Array.from({ length: scenario.accountsCount }, (_, index) =>
          `  Account {name=user${index + 1}@example.com, type=com.example}`,
        ).join("\n")
      : "Accounts: none";
  const userLines = Array.from({ length: scenario.userCount }, (_, index) => {
    const label = index === 0 ? "Owner" : `User${index}`;
    return `\tUserInfo{${index}:${label}:13} running`;
  }).join("\n");
  const ownersOutput = scenario.hasDeviceOwner
    ? "Device Owner: admin=ComponentInfo{com.afwsamples.testdpc/com.afwsamples.testdpc.DeviceAdminReceiver} name=TestDPC package=com.afwsamples.testdpc"
    : "no owners";

  return `#!/usr/bin/env bash
set -euo pipefail

if [[ "$1" == "devices" ]]; then
  cat <<'EOF'
List of devices attached
SERIAL123\tdevice
EOF
  exit 0
fi

if [[ "$1" == "shell" ]]; then
  shift
  case "$*" in
    "getprop ro.product.manufacturer") echo "OPPO" ;;
    "getprop ro.product.brand") echo "OPPO" ;;
    "getprop ro.product.model") echo "PFEM10" ;;
    "getprop ro.build.version.release") echo "15" ;;
    "getprop ro.build.version.sdk") echo "35" ;;
    "getprop ro.boot.flash.locked") echo "1" ;;
    "getprop ro.boot.verifiedbootstate") echo "green" ;;
    "getprop ro.oem_unlock_supported") echo "1" ;;
    "getprop ro.build.fingerprint") echo "oppo/pfem10/fingerprint" ;;
    "settings get global device_provisioned") echo "1" ;;
    "settings get secure user_setup_complete") echo "1" ;;
    "pm list users")
      cat <<'EOF'
Users:
${userLines}
EOF
      ;;
    "dpm list-owners")
      cat <<'EOF'
${ownersOutput}
EOF
      ;;
    "dumpsys account")
      cat <<'EOF'
${accountLines}
EOF
      ;;
    "pm path com.afwsamples.testdpc")
      if [[ "${scenario.dpcInstalled ? "true" : "false"}" == "true" ]]; then
        echo "package:/data/app/com.afwsamples.testdpc/base.apk"
      else
        exit 1
      fi
      ;;
    "pm path ai.openclaw.app")
      echo "package:/data/app/ai.openclaw.app/base.apk"
      ;;
    *)
      echo "unexpected adb shell command: $*" >&2
      exit 1
      ;;
  esac
  exit 0
fi

echo "unexpected adb invocation: $*" >&2
exit 1
`;
}

function runScenario(scenario: Scenario) {
  const tempRoot = mkdtempSync(
    path.join(os.tmpdir(), "openclaw-dedicated-readiness-"),
  );
  tempRoots.push(tempRoot);

  const binDir = path.join(tempRoot, "bin");
  const artifactDir = path.join(tempRoot, "artifacts");
  mkdirSync(binDir, { recursive: true });
  mkdirSync(artifactDir, { recursive: true });

  const adbPath = path.join(binDir, "adb");
  writeFileSync(adbPath, buildFakeAdbScript(scenario));
  chmodSync(adbPath, 0o755);

  const result = spawnSync("bash", [scriptPath], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR: artifactDir,
      PATH: `${binDir}:${process.env.PATH ?? ""}`,
    },
  });

  expect(result.status).toBe(0);

  const summaryPath = path.join(artifactDir, "summary.json");
  const summary = JSON.parse(readFileSync(summaryPath, "utf8"));
  return { stdout: result.stdout, summary };
}

afterEach(() => {
  for (const tempRoot of tempRoots.splice(0)) {
    rmSync(tempRoot, { force: true, recursive: true });
  }
});

describe("local-host-dedicated-readiness", () => {
  it("recommends the device-owner wrapper when adb provisioning is ready", () => {
    const { stdout, summary } = runScenario({
      accountsCount: 0,
      dpcInstalled: true,
      hasDeviceOwner: false,
      userCount: 1,
    });

    expect(summary.viability.deviceOwnerViaAdbReady).toBe(true);
    expect(summary.recommendedAction).toBe("device-owner");
    expect(summary.recommendedCommand).toBe(
      "pnpm android:local-host:dedicated:device-owner",
    );
    expect(stdout).toContain("dedicated.recommended_action=device-owner");
  });

  it("recommends installing TestDPC when the DPC is still missing", () => {
    const { summary } = runScenario({
      accountsCount: 3,
      dpcInstalled: false,
      hasDeviceOwner: false,
      userCount: 1,
    });

    expect(summary.viability.deviceOwnerViaAdbReady).toBe(false);
    expect(summary.recommendedAction).toBe("testdpc-install");
    expect(summary.recommendedCommand).toBe(
      "pnpm android:local-host:dedicated:testdpc-install",
    );
  });

  it("recommends the QR provisioning lane once TestDPC is present but adb owner is blocked", () => {
    const { summary } = runScenario({
      accountsCount: 2,
      dpcInstalled: true,
      hasDeviceOwner: false,
      userCount: 1,
    });

    expect(summary.viability.deviceOwnerViaAdbReady).toBe(false);
    expect(summary.recommendedAction).toBe("testdpc-qr");
    expect(summary.recommendedCommand).toBe(
      "pnpm android:local-host:dedicated:testdpc-qr",
    );
  });

  it("recommends post-provision checks once the device already has an owner", () => {
    const { summary } = runScenario({
      accountsCount: 0,
      dpcInstalled: true,
      hasDeviceOwner: true,
      userCount: 1,
    });

    expect(summary.state.hasDeviceOwner).toBe(true);
    expect(summary.recommendedAction).toBe("post-provision");
    expect(summary.recommendedCommand).toBe(
      "pnpm android:local-host:dedicated:post-provision",
    );
  });
});
