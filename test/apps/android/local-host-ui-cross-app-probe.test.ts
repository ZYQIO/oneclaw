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
  "apps/android/scripts/local-host-ui-cross-app-probe.sh",
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
  const values = new Map<string, string>();
  for (const line of result.stdout.trim().split("\n")) {
    const separator = line.indexOf("=");
    if (separator <= 0) {continue;}
    values.set(line.slice(0, separator), line.slice(separator + 1));
  }
  return values;
}

function runDescribeFailure(env: NodeJS.ProcessEnv = {}) {
  return spawnSync("bash", [scriptPath, "--describe"], {
    cwd: repoRoot,
    encoding: "utf8",
    env: {
      ...process.env,
      ...env,
    },
  });
}

describe("local-host-ui-cross-app-probe --describe", () => {
  it("reports the base preset when no follow-up is configured", () => {
    const values = runDescribe();

    expect(values.get("cross_app.preset")).toBe("base");
    expect(values.get("cross_app.follow_up.preset")).toBe("<none>");
    expect(values.get("cross_app.follow_up_mode")).toBe("none");
    expect(values.get("cross_app.follow_up.swipe_coordinate_mode")).toBe("none");
    expect(values.get("cross_app.follow_up.foreground_timeout_ms")).toBe("5000");
    expect(values.get("cross_app.follow_up.foreground_poll_interval_ms")).toBe("250");
  });

  it("expands the settings-search-input preset into a resource-id tap+input flow", () => {
    const values = runDescribe({
      OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PRESET: "settings-search-input",
    });

    expect(values.get("cross_app.preset")).toBe("preset:settings-search-input");
    expect(values.get("cross_app.target_package")).toBe("com.android.settings");
    expect(values.get("cross_app.follow_up_mode")).toBe("tap+input");
    expect(values.get("cross_app.follow_up.wait_text")).toBe("<empty>");
    expect(values.get("cross_app.follow_up.tap_text")).toBe("<empty>");
    expect(values.get("cross_app.follow_up.tap_resource_id")).toBe(
      "com.android.settings:id/searchView",
    );
    expect(values.get("cross_app.follow_up.input_value")).toBe("openclaw");
    expect(values.get("cross_app.follow_up.input_resource_id")).toBe(
      "com.android.settings:id/search_src_text",
    );
    expect(values.get("cross_app.rerun_hint")).toContain(
      "OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PRESET=settings-search-input",
    );
  });

  it("keeps explicit env overrides ahead of the preset defaults", () => {
    const values = runDescribe({
      OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PRESET: "settings-search-input",
      OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_INPUT_VALUE: "codex",
      OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_TAP_TEXT: "Search settings",
    });

    expect(values.get("cross_app.follow_up.tap_text")).toBe("Search settings");
    expect(values.get("cross_app.follow_up.tap_resource_id")).toBe("<empty>");
    expect(values.get("cross_app.follow_up.input_value")).toBe("codex");
  });

  it("reports swipe follow-up configuration when coordinates are provided", () => {
    const values = runDescribe({
      OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_START_X: "720",
      OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_START_Y: "2600",
      OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_END_X: "720",
      OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_END_Y: "900",
      OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_DURATION_MS: "350",
    });

    expect(values.get("cross_app.preset")).toBe("follow-up:swipe");
    expect(values.get("cross_app.follow_up_mode")).toBe("swipe");
    expect(values.get("cross_app.follow_up.swipe_requested")).toBe("true");
    expect(values.get("cross_app.follow_up.swipe_coordinate_mode")).toBe("absolute");
    expect(values.get("cross_app.follow_up.swipe_start_x")).toBe("720");
    expect(values.get("cross_app.follow_up.swipe_end_y")).toBe("900");
    expect(values.get("cross_app.follow_up.swipe_duration_ms")).toBe("350");
  });

  it("expands the settings-home-swipe-up preset into a ratio-based swipe flow", () => {
    const values = runDescribe({
      OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PRESET: "settings-home-swipe-up",
    });

    expect(values.get("cross_app.preset")).toBe("preset:settings-home-swipe-up");
    expect(values.get("cross_app.target_package")).toBe("com.android.settings");
    expect(values.get("cross_app.follow_up_mode")).toBe("swipe");
    expect(values.get("cross_app.follow_up.swipe_coordinate_mode")).toBe("ratio");
    expect(values.get("cross_app.follow_up.swipe_start_x")).toBe("<empty>");
    expect(values.get("cross_app.follow_up.swipe_start_x_ratio")).toBe("0.5");
    expect(values.get("cross_app.follow_up.swipe_start_y_ratio")).toBe("0.81");
    expect(values.get("cross_app.follow_up.swipe_end_y_ratio")).toBe("0.28");
    expect(values.get("cross_app.follow_up.swipe_duration_ms")).toBe("350");
  });

  it("keeps explicit swipe coordinates ahead of the swipe preset defaults", () => {
    const values = runDescribe({
      OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PRESET: "settings-home-swipe-up",
      OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_START_X: "700",
      OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_START_Y: "2500",
      OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_END_X: "700",
      OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_END_Y: "950",
      OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_DURATION_MS: "320",
    });

    expect(values.get("cross_app.follow_up.swipe_coordinate_mode")).toBe("absolute");
    expect(values.get("cross_app.follow_up.swipe_start_x")).toBe("700");
    expect(values.get("cross_app.follow_up.swipe_start_x_ratio")).toBe("<empty>");
    expect(values.get("cross_app.follow_up.swipe_duration_ms")).toBe("320");
  });

  it("fails clearly when swipe coordinates are incomplete", () => {
    const result = runDescribeFailure({
      OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_START_X: "720",
    });

    expect(result.status).not.toBe(0);
    expect(result.stderr).toContain(
      "OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_START_Y is required",
    );
  });

  it("fails clearly when swipe ratios are incomplete", () => {
    const result = runDescribeFailure({
      OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_START_X_RATIO: "0.5",
    });

    expect(result.status).not.toBe(0);
    expect(result.stderr).toContain(
      "OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWIPE_START_Y_RATIO is required",
    );
  });
});
