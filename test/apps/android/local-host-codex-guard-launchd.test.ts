import path from "node:path";
import { describe, expect, it } from "vitest";
import {
  parseCli,
  resolveGuardLaunchdPlan,
} from "../../../apps/android/scripts/local-host-codex-guard-launchd.js";

describe("parseCli", () => {
  it("defaults to install with adb-forward transport", () => {
    const options = parseCli(["--env-file", "./guard.env"]);

    expect(options.command).toBe("install");
    expect(options.envFile).toBe("./guard.env");
    expect(options.transport).toBe("adb-forward");
    expect(options.port).toBe(3945);
  });

  it("parses status and direct transport overrides", () => {
    const options = parseCli([
      "status",
      "--transport",
      "direct",
      "--base-url",
      "http://127.0.0.1:4950",
      "--json",
    ]);

    expect(options.command).toBe("status");
    expect(options.transport).toBe("direct");
    expect(options.baseUrl).toBe("http://127.0.0.1:4950");
    expect(options.json).toBe(true);
  });
});

describe("resolveGuardLaunchdPlan", () => {
  it("builds a default adb-forward launch plan without embedding secrets", () => {
    const plan = resolveGuardLaunchdPlan(
      {
        envFile: "~/secrets/android-codex-guard.env",
        repoRoot: "/repo/openclaw",
        stateDir: "~/.openclaw/android-local-host-codex-guard",
        label: "ai.openclaw.android-local-host-codex-guard",
        transport: "adb-forward",
        adbBin: "/opt/android/platform-tools/adb",
        port: 3945,
        source: "desktop-codex-guard-launchd",
      },
      {
        HOME: "/Users/tester",
      },
    );

    expect(plan.wrapperPath).toBe(
      "/Users/tester/.openclaw/android-local-host-codex-guard/run.sh",
    );
    expect(plan.plistPath).toBe(
      "/Users/tester/Library/LaunchAgents/ai.openclaw.android-local-host-codex-guard.plist",
    );
    expect(plan.commandArgs).toEqual([
      "apps/android/scripts/local-host-codex-sync.ts",
      "--use-adb-forward",
      "--wait-for-device",
      "--adb-bin",
      "/opt/android/platform-tools/adb",
      "--watch",
      "--json",
      "--artifact-dir",
      path.join("/Users/tester/.openclaw/android-local-host-codex-guard", "artifacts"),
      "--source",
      "desktop-codex-guard-launchd",
    ]);
    expect(plan.wrapperScript).toContain(". '/Users/tester/secrets/android-codex-guard.env'");
    expect(plan.wrapperScript).not.toContain("OPENCLAW_ANDROID_LOCAL_HOST_TOKEN=");
    expect(plan.plist).toContain("<string>ai.openclaw.android-local-host-codex-guard</string>");
    expect(plan.plist).toContain(`<string>${plan.wrapperPath}</string>`);
  });

  it("omits adb args in direct transport mode and preserves overrides", () => {
    const plan = resolveGuardLaunchdPlan(
      {
        envFile: "/tmp/guard.env",
        repoRoot: "/repo/openclaw",
        stateDir: "/tmp/openclaw-guard",
        artifactDir: "/tmp/openclaw-guard/custom-artifacts",
        label: "com.example.codex-guard",
        transport: "direct",
        baseUrl: "http://127.0.0.1:4950",
        port: 4950,
        watchIntervalMs: 45000,
        devicePollIntervalMs: 2000,
        source: "custom-launchd",
      },
      {
        HOME: "/Users/tester",
      },
    );

    expect(plan.commandArgs).toEqual([
      "apps/android/scripts/local-host-codex-sync.ts",
      "--watch",
      "--json",
      "--artifact-dir",
      "/tmp/openclaw-guard/custom-artifacts",
      "--source",
      "custom-launchd",
      "--base-url",
      "http://127.0.0.1:4950",
      "--watch-interval-ms",
      "45000",
      "--device-poll-interval-ms",
      "2000",
    ]);
    expect(plan.wrapperScript).not.toContain("--use-adb-forward");
    expect(plan.wrapperScript).not.toContain("--adb-bin");
  });
});
