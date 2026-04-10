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
  "apps/android/scripts/local-host-embedded-runtime-pod-soak.sh",
);
const tempRoots: string[] = [];

function writeExecutableScript(filePath: string, content: string) {
  writeFileSync(filePath, content);
  chmodSync(filePath, 0o755);
}

function buildStabilityScript(tempRoot: string) {
  const logPath = path.join(tempRoot, "stability-log.json");
  return `#!/usr/bin/env bash
set -euo pipefail

mkdir -p "$OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR"
node -e 'const fs = require("node:fs"); fs.writeFileSync(process.argv[1], JSON.stringify({ args: process.argv.slice(2) }))' "${logPath}" "$@"

cat <<'JSON' >"$OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR/summary.json"
{"ok":true,"packageCommand":"pnpm android:local-host:embedded-runtime-pod:stability","perturbationMode":"app_restart_between_iterations","passedIterationCount":5,"failedIterationCount":0}
JSON

if [[ " $* " == *" --json "* ]]; then
  cat "$OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR/summary.json"
else
  echo "runtime_pod_stability.ok=true iterations=5 passed=5 failed=0"
fi
`;
}

function runScenario(args: string[] = []) {
  const tempRoot = mkdtempSync(
    path.join(os.tmpdir(), "openclaw-runtime-soak-"),
  );
  tempRoots.push(tempRoot);

  const artifactDir = path.join(tempRoot, "artifacts");
  mkdirSync(artifactDir, { recursive: true });

  const stabilityScript = path.join(tempRoot, "stability.sh");
  writeExecutableScript(stabilityScript, buildStabilityScript(tempRoot));

  const result = spawnSync("bash", [scriptPath, ...args], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR: artifactDir,
      OPENCLAW_ANDROID_LOCAL_HOST_EMBEDDED_RUNTIME_POD_STABILITY_SCRIPT:
        stabilityScript,
    },
  });

  const log = JSON.parse(
    readFileSync(path.join(tempRoot, "stability-log.json"), "utf8"),
  );

  return { result, log };
}

afterEach(() => {
  for (const tempRoot of tempRoots.splice(0)) {
    rmSync(tempRoot, { recursive: true, force: true });
  }
});

describe("local-host-embedded-runtime-pod-soak", () => {
  it("defaults to a five-iteration restart-perturbation soak", () => {
    const { result, log } = runScenario();

    expect(result.status).toBe(0);
    expect(log.args).toEqual([
      "--iterations",
      "5",
      "--delay-sec",
      "0",
      "--restart-app-between-iterations",
    ]);
  });

  it("forwards json and explicit overrides to stability", () => {
    const { result, log } = runScenario([
      "--json",
      "--iterations",
      "7",
      "--delay-sec",
      "2",
      "--adb-bin",
      "/tmp/fake-adb",
      "--app-package",
      "example.app",
      "--app-component",
      "example.app/.MainActivity",
    ]);

    expect(result.status).toBe(0);
    expect(JSON.parse(result.stdout).ok).toBe(true);
    const parsed = JSON.parse(result.stdout);
    expect(parsed.packageCommand).toBe(
      "pnpm android:local-host:embedded-runtime-pod:soak",
    );
    expect(parsed.stabilityPackageCommand).toBe(
      "pnpm android:local-host:embedded-runtime-pod:stability",
    );
    expect(parsed.defaultIterations).toBe(5);
    expect(parsed.restartAppBetweenIterations).toBe(true);
    expect(log.args).toEqual([
      "--iterations",
      "7",
      "--delay-sec",
      "2",
      "--restart-app-between-iterations",
      "--json",
      "--adb-bin",
      "/tmp/fake-adb",
      "--app-package",
      "example.app",
      "--app-component",
      "example.app/.MainActivity",
    ]);
  });
});
