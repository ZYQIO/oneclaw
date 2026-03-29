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
  results: Record<string, { exitCode: number; output?: string }>;
  dnsProps?: string[];
};

const repoRoot = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../../..",
);
const scriptPath = path.join(
  repoRoot,
  "apps/android/scripts/local-host-openai-network-probe.sh",
);
const tempRoots: string[] = [];

function buildFakeAdbScript(scenario: Scenario): string {
  const dnsProps = (scenario.dnsProps ?? [
    "[persist.sys.dns.v4priority]: [cea.cofcoko.com]",
    "[persist.sys.ocloud.url]: [https://httpdns.ocloud.oppomobile.com]",
  ]).join("\n");
  const cases = Object.entries(scenario.results)
    .map(([command, result]) => {
      const output = result.output ?? "";
      const printed = output.length > 0 ? `printf '%s\\n' ${JSON.stringify(output)}` : ":";
      return `    ${JSON.stringify(command)})
      ${printed}
      exit ${result.exitCode}
      ;;`;
    })
    .join("\n");

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
    "getprop")
      cat <<'EOF'
${dnsProps}
EOF
      exit 0
      ;;
${cases}
    *)
      echo "unexpected adb shell command: $*" >&2
      exit 1
      ;;
  esac
fi

echo "unexpected adb invocation: $*" >&2
exit 1
`;
}

function runScenario(scenario: Scenario) {
  const tempRoot = mkdtempSync(
    path.join(os.tmpdir(), "openclaw-openai-network-probe-"),
  );
  tempRoots.push(tempRoot);

  const artifactDir = path.join(tempRoot, "artifacts");
  const binDir = path.join(tempRoot, "bin");
  mkdirSync(artifactDir, { recursive: true });
  mkdirSync(binDir, { recursive: true });

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

describe("local-host-openai-network-probe", () => {
  it("classifies the device as healthy when both OpenAI hosts are reachable over IPv4 and IPv6", () => {
    const { result, summary } = runScenario({
      results: {
        "toybox nc -4 -z -w 5 chatgpt.com 443": { exitCode: 0 },
        "toybox nc -6 -z -w 5 chatgpt.com 443": { exitCode: 0 },
        "toybox nc -4 -z -w 5 auth.openai.com 443": { exitCode: 0 },
        "toybox nc -6 -z -w 5 auth.openai.com 443": { exitCode: 0 },
      },
    });

    expect(result.stdout).toContain("openai_network.classification=all_hosts_reachable");
    expect(summary.classification).toBe("all_hosts_reachable");
    expect(summary.recommendedAction).toBe("rerun-smoke");
    expect(summary.probes).toHaveLength(4);
    expect(summary.dnsProps).toContain(
      "[persist.sys.ocloud.url]: [https://httpdns.ocloud.oppomobile.com]",
    );
  });

  it("classifies chatgpt.com as the blocked responses host when auth stays reachable", () => {
    const { result, summary } = runScenario({
      results: {
        "toybox nc -4 -z -w 5 chatgpt.com 443": {
          exitCode: 1,
          output: "nc: Timeout",
        },
        "toybox nc -6 -z -w 5 chatgpt.com 443": {
          exitCode: 1,
          output: "nc: Timeout",
        },
        "toybox nc -4 -z -w 5 auth.openai.com 443": { exitCode: 0 },
        "toybox nc -6 -z -w 5 auth.openai.com 443": { exitCode: 0 },
      },
    });

    expect(result.stdout).toContain(
      "openai_network.classification=responses_host_unreachable",
    );
    expect(result.stdout).toContain(
      "openai_network.recommended_action=check-chatgpt-path",
    );
    expect(summary.classification).toBe("responses_host_unreachable");
    expect(summary.recommendedAction).toBe("check-chatgpt-path");
    expect(summary.recommendedCommand).toContain("chatgpt.com 443");
    expect(
      summary.probes.find(
        (probe: { host: string; family: string }) =>
          probe.host === "chatgpt.com" && probe.family === "ipv6",
      )?.status,
    ).toBe("timeout");
  });

  it("calls out the narrower IPv6-only responses failure case", () => {
    const { summary } = runScenario({
      results: {
        "toybox nc -4 -z -w 5 chatgpt.com 443": { exitCode: 0 },
        "toybox nc -6 -z -w 5 chatgpt.com 443": {
          exitCode: 1,
          output: "nc: Timeout",
        },
        "toybox nc -4 -z -w 5 auth.openai.com 443": { exitCode: 0 },
        "toybox nc -6 -z -w 5 auth.openai.com 443": { exitCode: 0 },
      },
    });

    expect(summary.classification).toBe("responses_ipv6_unreachable");
    expect(summary.recommendedAction).toBe("prefer-ipv4-or-fix-ipv6");
  });
});
