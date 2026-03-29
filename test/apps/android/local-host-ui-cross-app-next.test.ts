import { spawnSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const repoRoot = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../../..",
);
const scriptPath = path.join(
  repoRoot,
  "apps/android/scripts/local-host-ui-cross-app-next.sh",
);

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

describe("local-host-ui-cross-app-next --describe", () => {
  it("defaults to the settings-search-input preset in probe mode", () => {
    const summary = runDescribe();

    expect(summary.mode).toBe("probe");
    expect(summary.preset).toBe("settings-search-input");
    expect(summary.recommendedCommand).toContain(
      "pnpm android:local-host:ui:cross-app:next",
    );
    expect(summary.probeDescribe["cross_app.preset"]).toBe("preset:settings-search-input");
    expect(summary.probeDescribe["cross_app.follow_up_mode"]).toBe("wait+tap+input");
  });

  it("preserves explicit env overrides while keeping the default preset", () => {
    const summary = runDescribe([], {
      OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PACKAGE: "com.example.settings",
      OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_INPUT_VALUE: "codex",
    });

    expect(summary.probeDescribe["cross_app.target_package"]).toBe("com.example.settings");
    expect(summary.probeDescribe["cross_app.follow_up.input_value"]).toBe("codex");
  });

  it("switches into sweep mode when requested", () => {
    const summary = runDescribe(["--sweep"]);

    expect(summary.mode).toBe("sweep");
    expect(summary.sweepDescribe).not.toBeNull();
    expect(summary.recommendedCommand).toContain("-- --sweep");
  });
});
