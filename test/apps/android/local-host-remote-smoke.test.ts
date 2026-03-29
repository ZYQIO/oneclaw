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
  chatResponse: unknown;
  invokeResponse?: unknown;
  statusResponse?: unknown;
  capabilitiesResponse?: unknown;
};

const repoRoot = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../../..",
);
const scriptPath = path.join(
  repoRoot,
  "apps/android/scripts/local-host-remote-smoke.sh",
);
const tempRoots: string[] = [];

function buildFakeCurlScript(scenario: Scenario): string {
  const statusJson = JSON.stringify(
    scenario.statusResponse ?? {
      ok: true,
      mode: "local",
      host: {
        codexAuthConfigured: true,
        sessionCount: 1,
        activeRunCount: 0,
      },
      remoteAccess: {
        advancedEnabled: true,
        writeEnabled: true,
      },
    },
    null,
    2,
  );
  const capsJson = JSON.stringify(
    scenario.capabilitiesResponse ?? {
      commands: ["device.status"],
    },
    null,
    2,
  );
  const chatJson = JSON.stringify(scenario.chatResponse, null, 2);
  const invokeJson = JSON.stringify(
    scenario.invokeResponse ?? {
      ok: true,
      payload: {
        command: "device.status",
      },
    },
    null,
    2,
  );

  return `#!/usr/bin/env bash
set -euo pipefail

url=""
while (($#)); do
  case "$1" in
    --fail|--silent|--show-error)
      shift
      ;;
    -H|-X|--max-time|-d)
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
${capsJson}
EOF
    ;;
  */api/local-host/v1/chat/send-wait)
    cat <<'EOF'
${chatJson}
EOF
    ;;
  */api/local-host/v1/invoke)
    cat <<'EOF'
${invokeJson}
EOF
    ;;
  *)
    echo "unexpected curl url: $url" >&2
    exit 1
    ;;
esac
`;
}

function runScenario(scenario: Scenario) {
  const tempRoot = mkdtempSync(
    path.join(os.tmpdir(), "openclaw-local-host-remote-smoke-"),
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

describe("local-host-remote-smoke", () => {
  it("reports success only when chat and invoke both succeed", () => {
    const { result, summary } = runScenario({
      chatResponse: {
        ok: true,
        timedOut: false,
        runId: "run-success",
        payload: {
          state: "final",
          message: {
            content: [{ text: "Android local host is healthy." }],
          },
        },
      },
    });

    expect(result.status).toBe(0);
    expect(result.stdout).toContain("local_host.smoke=completed");
    expect(summary.ok).toBe(true);
    expect(summary.failedChecks).toEqual([]);
    expect(summary.chat.state).toBe("final");
    expect(summary.invoke.ok).toBe(true);
  });

  it("fails with a classified diagnostic when chat returns an IPv6 OpenAI connect timeout", () => {
    const { result, summary } = runScenario({
      chatResponse: {
        ok: true,
        timedOut: false,
        runId: "run-timeout",
        payload: {
          state: "error",
          errorMessage:
            "failed to connect to chatgpt.com/2a03:2880:f12a:83:face:b00c:0:25de (port 443) after 10000ms",
        },
      },
    });

    expect(result.status).toBe(1);
    expect(result.stdout).toContain("local_host.smoke=failed");
    expect(result.stdout).toContain("chat.error_class=openai_connect_timeout");
    expect(result.stdout).toContain("chat.error_host=chatgpt.com");
    expect(result.stdout).toContain("chat.error_address_family=ipv6");
    expect(summary.ok).toBe(false);
    expect(summary.failedChecks).toEqual(["chat"]);
    expect(summary.chat.errorClass).toBe("openai_connect_timeout");
    expect(summary.chat.errorHost).toBe("chatgpt.com");
    expect(summary.chat.errorAddressFamily).toBe("ipv6");
  });

  it("fails when invoke returns ok=false even if chat already succeeded", () => {
    const { result, summary } = runScenario({
      chatResponse: {
        ok: true,
        timedOut: false,
        runId: "run-success",
        payload: {
          state: "final",
          message: {
            content: [{ text: "Android local host is healthy." }],
          },
        },
      },
      invokeResponse: {
        ok: false,
        error: {
          message: "device.status is currently disabled",
        },
      },
    });

    expect(result.status).toBe(1);
    expect(result.stdout).toContain("local_host.smoke=failed");
    expect(result.stdout).toContain("smoke.failed_checks=invoke");
    expect(summary.ok).toBe(false);
    expect(summary.failedChecks).toEqual(["invoke"]);
    expect(summary.invoke.ok).toBe(false);
    expect(summary.invoke.error).toBe("device.status is currently disabled");
  });
});
