import {
  chmodSync,
  existsSync,
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
  "apps/android/scripts/local-host-embedded-runtime-pod-refresh.sh",
);
const tempRoots: string[] = [];
const rawToken = `ocrt_${"1".repeat(64)}`;

function writeExecutableScript(filePath: string, content: string) {
  writeFileSync(filePath, content);
  chmodSync(filePath, 0o755);
}

function buildFakePnpm() {
  return `#!/usr/bin/env bash
set -euo pipefail

cmd="\${1:-}"
shift || true

case "$cmd" in
  android:install)
    printf '%s\\n' "$cmd $*" > "$FAKE_INSTALL_LOG"
    if [[ "\${FAKE_INSTALL_MODE:-success}" == "fail" ]]; then
      echo "InstallException: -99" >&2
      exit 1
    fi
    echo "install ok"
    ;;
  android:assemble)
    printf '%s\\n' "$cmd $*" > "$FAKE_ASSEMBLE_LOG"
    mkdir -p "$(dirname "$FAKE_APK_PATH")"
    printf 'apk' > "$FAKE_APK_PATH"
    echo "assemble ok"
    ;;
  android:local-host:token)
    printf '%s\\n' "$cmd $*" > "$FAKE_TOKEN_LOG"
    cat <<'JSON'
{"token":"${rawToken}","appPackage":"example.app","appComponent":"example.app/.MainActivity","exportAction":"example.export","cachePath":"cache/debug-local-host-token.txt","launchedApp":true,"serial":"PFEM10"}
JSON
    ;;
  android:local-host:embedded-runtime-pod:soak)
    printf '%s\\n' "$cmd $*" > "$FAKE_SOAK_LOG"
    printf '%s' "\${OPENCLAW_ANDROID_LOCAL_HOST_TOKEN:-}" > "$FAKE_SOAK_TOKEN_LOG"
    printf '%s' "\${ANDROID_SERIAL:-}" > "$FAKE_SOAK_SERIAL_LOG"
    mkdir -p "$OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR"
    cat <<'JSON' > "$OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR/summary.json"
{"ok":true,"packageCommand":"pnpm android:local-host:embedded-runtime-pod:soak","classifications":["process_runtime_active_session_live_proof_captured"],"recommendedNextSlices":["process_runtime_lane_hardening"],"passedIterationCount":5,"failedIterationCount":0,"perturbationMode":"app_restart_between_iterations","perturbationAppliedCount":4,"perturbationFailureCount":0,"stableExpectedArtifactCount":3,"stableCapturedArtifactCount":3}
JSON
    cat "$OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR/summary.json"
    ;;
  *)
    echo "unexpected fake pnpm command: $cmd" >&2
    exit 1
    ;;
esac
`;
}

function buildFakeAdb() {
  return `#!/usr/bin/env bash
set -euo pipefail
printf '%s\\n' "$*" >> "$FAKE_ADB_LOG"
exit 0
`;
}

function buildFakeTokenHelper() {
  return `#!/usr/bin/env bash
set -euo pipefail
printf '%s\\n' "$*" > "$FAKE_TOKEN_LOG"
cat <<'JSON'
{"token":"${rawToken}","appPackage":"example.app","appComponent":"example.app/.MainActivity","exportAction":"example.export","cachePath":"cache/debug-local-host-token.txt","launchedApp":true,"serial":"PFEM10"}
JSON
`;
}

function runScenario(args: string[] = [], installMode = "success") {
  const tempRoot = mkdtempSync(
    path.join(os.tmpdir(), "openclaw-runtime-refresh-"),
  );
  tempRoots.push(tempRoot);

  const artifactDir = path.join(tempRoot, "artifacts");
  mkdirSync(artifactDir, { recursive: true });

  const fakePnpm = path.join(tempRoot, "pnpm");
  const fakeAdb = path.join(tempRoot, "adb");
  const fakeTokenHelper = path.join(tempRoot, "token-helper");
  const apkPath = path.join(tempRoot, "app-debug.apk");

  writeExecutableScript(fakePnpm, buildFakePnpm());
  writeExecutableScript(fakeAdb, buildFakeAdb());
  writeExecutableScript(fakeTokenHelper, buildFakeTokenHelper());

  const result = spawnSync("bash", [scriptPath, ...args, "--apk-path", apkPath], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR: artifactDir,
      OPENCLAW_ANDROID_LOCAL_HOST_PNPM_BIN: fakePnpm,
      OPENCLAW_ANDROID_LOCAL_HOST_ADB_BIN: fakeAdb,
      OPENCLAW_ANDROID_LOCAL_HOST_TOKEN_HELPER: fakeTokenHelper,
      FAKE_INSTALL_MODE: installMode,
      FAKE_INSTALL_LOG: path.join(tempRoot, "install.log"),
      FAKE_ASSEMBLE_LOG: path.join(tempRoot, "assemble.log"),
      FAKE_TOKEN_LOG: path.join(tempRoot, "token.log"),
      FAKE_SOAK_LOG: path.join(tempRoot, "soak.log"),
      FAKE_SOAK_TOKEN_LOG: path.join(tempRoot, "soak-token.log"),
      FAKE_SOAK_SERIAL_LOG: path.join(tempRoot, "soak-serial.log"),
      FAKE_ADB_LOG: path.join(tempRoot, "adb.log"),
      FAKE_APK_PATH: apkPath,
    },
  });

  return { result, tempRoot, artifactDir, apkPath };
}

afterEach(() => {
  for (const tempRoot of tempRoots.splice(0)) {
    rmSync(tempRoot, { recursive: true, force: true });
  }
});

describe("local-host-embedded-runtime-pod-refresh", () => {
  it("reinstalls, redacts token output, and forwards the fresh token into soak", () => {
    const { result, tempRoot, artifactDir } = runScenario([
      "--json",
      "--iterations",
      "7",
      "--delay-sec",
      "2",
      "--serial",
      "PFEM10",
      "--app-package",
      "example.app",
      "--app-component",
      "example.app/.MainActivity",
    ]);

    expect(result.status).toBe(0);
    expect(result.stdout).not.toContain(rawToken);

    const parsed = JSON.parse(result.stdout);
    expect(parsed.packageCommand).toBe(
      "pnpm android:local-host:embedded-runtime-pod:refresh",
    );
    expect(parsed.ok).toBe(true);
    expect(parsed.install.ok).toBe(true);
    expect(parsed.install.finalMethod).toBe("pnpm_android_install");
    expect(parsed.token.ok).toBe(true);
    expect(parsed.token.tokenRedacted).toBe(true);
    expect(parsed.token.metadata.token).toBe("REDACTED");
    expect(parsed.soak.ok).toBe(true);
    expect(parsed.classifications).toEqual([
      "process_runtime_active_session_live_proof_captured",
    ]);
    expect(parsed.recommendedNextSlices).toEqual([
      "process_runtime_lane_hardening",
    ]);
    expect(parsed.passedIterationCount).toBe(5);
    expect(parsed.failedIterationCount).toBe(0);
    expect(parsed.perturbationAppliedCount).toBe(4);
    expect(parsed.stableCapturedArtifactCount).toBe(3);

    expect(
      readFileSync(path.join(tempRoot, "token.log"), "utf8"),
    ).toContain("--serial PFEM10");
    expect(
      readFileSync(path.join(tempRoot, "token.log"), "utf8"),
    ).toContain(`--adb-bin ${path.join(tempRoot, "adb")}`);
    expect(
      readFileSync(path.join(tempRoot, "soak.log"), "utf8"),
    ).toContain("--iterations 7 --delay-sec 2");
    expect(
      readFileSync(path.join(tempRoot, "soak.log"), "utf8"),
    ).toContain("--app-package example.app --app-component example.app/.MainActivity");
    expect(
      readFileSync(path.join(tempRoot, "soak-token.log"), "utf8"),
    ).toBe(rawToken);
    expect(
      readFileSync(path.join(tempRoot, "soak-serial.log"), "utf8"),
    ).toBe("PFEM10");
    expect(existsSync(path.join(tempRoot, "assemble.log"))).toBe(false);
    expect(existsSync(path.join(tempRoot, "adb.log"))).toBe(false);

    const redactedTokenArtifact = JSON.parse(
      readFileSync(path.join(artifactDir, "token-export.json"), "utf8"),
    );
    expect(redactedTokenArtifact.token).toBe("REDACTED");
    expect(JSON.stringify(redactedTokenArtifact)).not.toContain(rawToken);
  });

  it("falls back to adb install after pnpm android:install fails", () => {
    const { result, tempRoot, apkPath } = runScenario(
      ["--json", "--serial", "PFEM10"],
      "fail",
    );

    expect(result.status).toBe(0);

    const parsed = JSON.parse(result.stdout);
    expect(parsed.ok).toBe(true);
    expect(parsed.install.ok).toBe(true);
    expect(parsed.install.primary.ok).toBe(false);
    expect(parsed.install.primary.exitCode).toBe(1);
    expect(parsed.install.finalMethod).toBe("adb_install_debug_apk");
    expect(parsed.install.fallback.used).toBe(true);
    expect(parsed.install.fallback.ok).toBe(true);
    expect(parsed.install.fallback.assembleExecuted).toBe(true);
    expect(parsed.install.fallback.adbInstallCommand).toContain(
      `-s PFEM10 install -r -d ${apkPath}`,
    );
    expect(parsed.token.ok).toBe(true);
    expect(parsed.soak.ok).toBe(true);
    expect(existsSync(path.join(tempRoot, "assemble.log"))).toBe(true);
    expect(readFileSync(path.join(tempRoot, "adb.log"), "utf8")).toContain(
      `-s PFEM10 install -r -d ${apkPath}`,
    );
  });
});
