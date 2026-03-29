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
  "apps/android/scripts/local-host-doctor.sh",
);
const tempRoots: string[] = [];

function writeExecutableScript(filePath: string, content: string) {
  writeFileSync(filePath, content);
  chmodSync(filePath, 0o755);
}

function buildTokenScript(token: string) {
  return `#!/usr/bin/env bash
set -euo pipefail
cat <<'EOF'
{"token":${JSON.stringify(token)},"appPackage":"ai.openclaw.app"}
EOF
`;
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

function runScenario(options: {
  envToken?: string;
  json?: boolean;
  tokenScriptToken?: string;
  smokeSummary: unknown;
  smokeExitCode?: number;
  smokeStdoutLines?: string[];
  openaiNetworkSummary?: unknown;
  openaiNetworkExitCode?: number;
  openaiNetworkStdoutLines?: string[];
}) {
  const tempRoot = mkdtempSync(path.join(os.tmpdir(), "openclaw-local-host-doctor-"));
  tempRoots.push(tempRoot);

  const artifactDir = path.join(tempRoot, "artifacts");
  mkdirSync(artifactDir, { recursive: true });

  const smokeScript = path.join(tempRoot, "smoke.sh");
  const openaiNetworkScript = path.join(tempRoot, "openai-network.sh");
  const tokenScript = path.join(tempRoot, "token.sh");

  writeExecutableScript(
    smokeScript,
    buildSummaryScript(options.smokeSummary, {
      exitCode: options.smokeExitCode,
      stdoutLines: options.smokeStdoutLines,
    }),
  );
  writeExecutableScript(
    openaiNetworkScript,
    buildSummaryScript(
      options.openaiNetworkSummary ?? {
        classification: "all_hosts_reachable",
        recommendedAction: "rerun-smoke",
        recommendedCommand: "pnpm android:local-host:smoke",
      },
      {
        exitCode: options.openaiNetworkExitCode,
        stdoutLines: options.openaiNetworkStdoutLines,
      },
    ),
  );
  writeExecutableScript(
    tokenScript,
    buildTokenScript(options.tokenScriptToken ?? `ocrt_${"a".repeat(64)}`),
  );

  const result = spawnSync(
    "bash",
    [scriptPath, ...(options.json ? ["--json"] : [])],
    {
      cwd: repoRoot,
      encoding: "utf8",
      env: {
        ...process.env,
        ...(options.envToken
          ? { OPENCLAW_ANDROID_LOCAL_HOST_TOKEN: options.envToken }
          : {}),
        OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR: artifactDir,
        OPENCLAW_ANDROID_LOCAL_HOST_TOKEN_SCRIPT: tokenScript,
        OPENCLAW_ANDROID_LOCAL_HOST_SMOKE_SCRIPT: smokeScript,
        OPENCLAW_ANDROID_LOCAL_HOST_OPENAI_NETWORK_SCRIPT: openaiNetworkScript,
      },
    },
  );

  expect(result.status).toBe(0);

  const summary = JSON.parse(readFileSync(path.join(artifactDir, "summary.json"), "utf8"));
  return { result, summary };
}

afterEach(() => {
  for (const tempRoot of tempRoots.splice(0)) {
    rmSync(tempRoot, { recursive: true, force: true });
  }
});

describe("local-host-doctor", () => {
  it("reuses an existing token and reports a healthy smoke run without the network probe", () => {
    const { result, summary } = runScenario({
      envToken: `ocrt_${"b".repeat(64)}`,
      smokeSummary: {
        ok: true,
        failedChecks: [],
        chat: {
          errorClass: null,
        },
      },
      smokeStdoutLines: ["local_host.smoke=completed"],
    });

    expect(result.stdout).toContain("doctor.token_source=env");
    expect(result.stdout).toContain("doctor.smoke.ok=true");
    expect(result.stdout).toContain("doctor.openai_network.executed=false");
    expect(summary.classification).toBe("local_host_healthy");
    expect(summary.token.source).toBe("env");
    expect(summary.openaiNetwork.executed).toBe(false);
  });

  it("bootstraps a token and runs the network probe after an openai_connect_timeout smoke failure", () => {
    const { result, summary } = runScenario({
      smokeSummary: {
        ok: false,
        failedChecks: ["chat"],
        chat: {
          errorClass: "openai_connect_timeout",
        },
      },
      smokeExitCode: 1,
      openaiNetworkSummary: {
        classification: "responses_host_unreachable",
        recommendedAction: "check-chatgpt-path",
        recommendedCommand: "adb shell 'toybox nc -4 -z -w 5 chatgpt.com 443'",
      },
    });

    expect(result.stdout).toContain("doctor.token_source=bootstrap");
    expect(result.stdout).toContain("doctor.openai_network.executed=true");
    expect(result.stdout).toContain(
      "doctor.classification=responses_host_unreachable",
    );
    expect(summary.classification).toBe("responses_host_unreachable");
    expect(summary.token.source).toBe("bootstrap");
    expect(summary.smoke.exitCode).toBe(1);
    expect(summary.smoke.errorClass).toBe("openai_connect_timeout");
    expect(summary.openaiNetwork.executed).toBe(true);
    expect(summary.openaiNetwork.summary.classification).toBe(
      "responses_host_unreachable",
    );
  });

  it("prints the combined JSON summary when requested", () => {
    const { result } = runScenario({
      json: true,
      smokeSummary: {
        ok: false,
        failedChecks: ["chat"],
        chat: {
          errorClass: "openai_connect_timeout",
        },
      },
      smokeExitCode: 1,
      openaiNetworkSummary: {
        classification: "responses_ipv6_unreachable",
        recommendedAction: "prefer-ipv4-or-fix-ipv6",
        recommendedCommand: "pnpm android:local-host:smoke",
      },
    });

    const parsed = JSON.parse(result.stdout);
    expect(parsed.classification).toBe("responses_ipv6_unreachable");
    expect(parsed.openaiNetwork.executed).toBe(true);
    expect(parsed.smoke.errorClass).toBe("openai_connect_timeout");
  });
});
