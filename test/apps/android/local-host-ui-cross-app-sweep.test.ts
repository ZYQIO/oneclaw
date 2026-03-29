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
  "apps/android/scripts/local-host-ui-cross-app-sweep.sh",
);

function runDescribe(env: NodeJS.ProcessEnv = {}) {
  const result = spawnSync("bash", [scriptPath, "--describe"], {
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

describe("local-host-ui-cross-app-sweep --describe", () => {
  it("reports rerunnable default commands", () => {
    const summary = runDescribe();

    expect(summary.command).toBe("./apps/android/scripts/local-host-ui-cross-app-sweep.sh");
    expect(summary.recommendedCommand).toContain(
      "OPENCLAW_ANDROID_LOCAL_HOST_TOKEN=<token>",
    );
    expect(summary.probeCommand).toContain(
      "OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_OBSERVE_WINDOW_MS=\\<window-ms\\>",
    );
  });

  it("preserves preset, reset helper, and sweep overrides in replay commands", () => {
    const summary = runDescribe({
      OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PRESET: "settings-home-swipe-up",
      OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_FORCE_STOP_TARGET_BEFORE_LAUNCH:
        "true",
      OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWEEP_WINDOWS_MS: "4000,9000",
      OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_STOP_ON_FIRST_NON_REACHABLE:
        "false",
    });

    expect(summary.recommendedCommand).toContain(
      "OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PRESET=settings-home-swipe-up",
    );
    expect(summary.recommendedCommand).toContain(
      "OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_FORCE_STOP_TARGET_BEFORE_LAUNCH=true",
    );
    expect(summary.recommendedCommand).toContain(
      "OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWEEP_WINDOWS_MS=4000\\,9000",
    );
    expect(summary.recommendedCommand).toContain(
      "OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_STOP_ON_FIRST_NON_REACHABLE=false",
    );
    expect(summary.probeCommand).toContain(
      "OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PRESET=settings-home-swipe-up",
    );
    expect(summary.probeCommand).toContain(
      "OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_FORCE_STOP_TARGET_BEFORE_LAUNCH=true",
    );
  });
});
