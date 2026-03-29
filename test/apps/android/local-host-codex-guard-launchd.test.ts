import path from "node:path";
import { describe, expect, it } from "vitest";
import {
  buildGuardEnvFileContent,
  buildRecommendedCommand,
  parseCli,
  planGuardSetup,
  recommendGuardAction,
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

  it("parses write-env token seeding", () => {
    const options = parseCli([
      "write-env",
      "--token",
      "secret-token",
    ]);

    expect(options.command).toBe("write-env");
    expect(options.token).toBe("secret-token");
  });

  it("parses setup token seeding", () => {
    const options = parseCli([
      "setup",
      "--token",
      "secret-token",
    ]);

    expect(options.command).toBe("setup");
    expect(options.token).toBe("secret-token");
  });
});

describe("resolveGuardLaunchdPlan", () => {
  it("builds a default adb-forward launch plan without embedding secrets", () => {
    const plan = resolveGuardLaunchdPlan(
      {
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
    expect(plan.envFile).toBe(
      "/Users/tester/.openclaw/android-local-host-codex-guard/guard.env",
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
    expect(plan.wrapperScript).toContain(". '/Users/tester/.openclaw/android-local-host-codex-guard/guard.env'");
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

describe("buildGuardEnvFileContent", () => {
  it("writes a placeholder template when no token is provided", () => {
    const content = buildGuardEnvFileContent();

    expect(content).toContain("# OpenClaw Android local-host Codex guard");
    expect(content).toContain("OPENCLAW_ANDROID_LOCAL_HOST_TOKEN='<token-from-connect-tab>'");
  });

  it("embeds the provided token when seeded", () => {
    const content = buildGuardEnvFileContent("secret-token");

    expect(content).toContain("OPENCLAW_ANDROID_LOCAL_HOST_TOKEN='secret-token'");
  });
});

describe("recommendGuardAction", () => {
  it("asks for write-env before anything else when env is missing", () => {
    expect(
      recommendGuardAction({
        envFileExists: false,
        tokenConfigured: false,
        installed: false,
        loaded: false,
      }),
    ).toBe("write-env");
  });

  it("asks for token configuration when env exists but still has the placeholder", () => {
    expect(
      recommendGuardAction({
        envFileExists: true,
        tokenConfigured: false,
        installed: false,
        loaded: false,
      }),
    ).toBe("configure-token");
  });

  it("asks for install when env is ready but launchd is not installed", () => {
    expect(
      recommendGuardAction({
        envFileExists: true,
        tokenConfigured: true,
        installed: false,
        loaded: false,
      }),
    ).toBe("install");
  });

  it("asks for launchd investigation when installed but not loaded", () => {
    expect(
      recommendGuardAction({
        envFileExists: true,
        tokenConfigured: true,
        installed: true,
        loaded: false,
      }),
    ).toBe("check-launchagent");
  });

  it("reports healthy when everything is ready and loaded", () => {
    expect(
      recommendGuardAction({
        envFileExists: true,
        tokenConfigured: true,
        installed: true,
        loaded: true,
      }),
    ).toBe("healthy");
  });
});

describe("buildRecommendedCommand", () => {
  it("points missing env files at write-env", () => {
    expect(
      buildRecommendedCommand({
        action: "write-env",
        envFile: "/tmp/openclaw guard.env",
      }),
    ).toBe(
      "pnpm android:local-host:codex-guard:launchd -- write-env --env-file '/tmp/openclaw guard.env'",
    );
  });

  it("points placeholder env files at setup with a token placeholder", () => {
    expect(
      buildRecommendedCommand({
        action: "configure-token",
        envFile: "/tmp/guard.env",
      }),
    ).toBe(
      "pnpm android:local-host:codex-guard:launchd -- setup --env-file '/tmp/guard.env' --token '<token-from-connect-tab>'",
    );
  });

  it("points install and repair states at setup", () => {
    expect(
      buildRecommendedCommand({
        action: "install",
        envFile: "/tmp/guard.env",
      }),
    ).toBe(
      "pnpm android:local-host:codex-guard:launchd -- setup --env-file '/tmp/guard.env'",
    );
    expect(
      buildRecommendedCommand({
        action: "check-launchagent",
        envFile: "/tmp/guard.env",
      }),
    ).toBe(
      "pnpm android:local-host:codex-guard:launchd -- setup --env-file '/tmp/guard.env'",
    );
  });

  it("omits a command when guard is already healthy", () => {
    expect(
      buildRecommendedCommand({
        action: "healthy",
        envFile: "/tmp/guard.env",
      }),
    ).toBeUndefined();
  });
});

describe("planGuardSetup", () => {
  it("writes a template when env is missing and no token is provided", () => {
    expect(
      planGuardSetup({
        envFileExists: false,
        tokenConfigured: false,
        installed: false,
        loaded: false,
        tokenProvided: false,
      }),
    ).toEqual({
      setupActions: ["write-env-template"],
      shouldWriteEnv: true,
      writeEnvMode: "template",
      shouldInstall: false,
    });
  });

  it("writes a token and installs when token is provided for a fresh setup", () => {
    expect(
      planGuardSetup({
        envFileExists: false,
        tokenConfigured: false,
        installed: false,
        loaded: false,
        tokenProvided: true,
      }),
    ).toEqual({
      setupActions: ["write-env-token", "install"],
      shouldWriteEnv: true,
      writeEnvMode: "token",
      shouldInstall: true,
    });
  });

  it("repairs launchd when guard is ready but not loaded", () => {
    expect(
      planGuardSetup({
        envFileExists: true,
        tokenConfigured: true,
        installed: true,
        loaded: false,
        tokenProvided: false,
      }),
    ).toEqual({
      setupActions: ["install"],
      shouldWriteEnv: false,
      shouldInstall: true,
    });
  });

  it("does nothing when guard is already healthy", () => {
    expect(
      planGuardSetup({
        envFileExists: true,
        tokenConfigured: true,
        installed: true,
        loaded: true,
        tokenProvided: false,
      }),
    ).toEqual({
      setupActions: ["noop"],
      shouldWriteEnv: false,
      shouldInstall: false,
    });
  });
});
