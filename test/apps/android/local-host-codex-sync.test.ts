import { describe, expect, it } from "vitest";
import type { AuthProfileStore, OAuthCredential } from "../../../src/agents/auth-profiles/types.js";
import {
  countConnectedAdbDevicesFromOutput,
  parseCli,
  planCodexSync,
  runCodexSyncWatch,
  selectDesktopCodexProfile,
  waitForAdbDevice,
} from "../../../apps/android/scripts/local-host-codex-sync.js";

function oauthCredential(
  overrides: Partial<OAuthCredential> = {},
): OAuthCredential {
  return {
    type: "oauth",
    provider: "openai-codex",
    access: "access-token",
    refresh: "refresh-token",
    expires: 200_000,
    accountId: "acct_123",
    ...overrides,
  };
}

describe("selectDesktopCodexProfile", () => {
  it("prefers the ordered usable openai-codex oauth profile", () => {
    const store: AuthProfileStore = {
      version: 1,
      profiles: {
        "openai-codex:token": {
          type: "token",
          provider: "openai-codex",
          token: "token-only",
          expires: 200_000,
        },
        "openai-codex:oauth": oauthCredential({ email: "person@example.com" }),
      },
      order: {
        "openai-codex": ["openai-codex:token", "openai-codex:oauth"],
      },
    };

    const selected = selectDesktopCodexProfile({ store });

    expect(selected.profileId).toBe("openai-codex:oauth");
    expect(selected.credential.email).toBe("person@example.com");
  });
});

describe("planCodexSync", () => {
  it("skips import when the phone auth is still healthy", () => {
    const plan = planCodexSync({
      phoneStatus: {
        configured: true,
        expired: false,
        refreshRecommended: false,
      },
      desktopCredential: oauthCredential({ expires: 300_000 }),
      now: 100_000,
    });

    expect(plan).toEqual({
      action: "skip",
      reason: "phone-auth-healthy",
    });
  });

  it("imports and refreshes when the phone is missing auth and desktop auth is near expiry", () => {
    const plan = planCodexSync({
      phoneStatus: {
        configured: false,
      },
      desktopCredential: oauthCredential({ expires: 120_000 }),
      now: 100_000,
    });

    expect(plan).toEqual({
      action: "import-and-refresh",
      reason: "phone-missing-auth",
    });
  });
});

describe("parseCli", () => {
  it("ignores pnpm's forwarded separator", () => {
    const options = parseCli(["--", "--token", "secret-token"]);

    expect(options.token).toBe("secret-token");
    expect(options.baseUrl).toBe("http://127.0.0.1:3945");
  });

  it("ignores forwarded separators after preset wrapper args", () => {
    const options = parseCli([
      "--use-adb-forward",
      "--wait-for-device",
      "--watch",
      "--",
      "--json",
      "--token",
      "secret-token",
    ]);

    expect(options.useAdbForward).toBe(true);
    expect(options.waitForDevice).toBe(true);
    expect(options.watch).toBe(true);
    expect(options.json).toBe(true);
  });

  it("parses watch settings", () => {
    const options = parseCli([
      "--token",
      "secret-token",
      "--wait-for-device",
      "--device-poll-interval-ms",
      "2000",
      "--watch",
      "--watch-interval-ms",
      "45000",
      "--watch-max-runs",
      "3",
    ]);

    expect(options.waitForDevice).toBe(true);
    expect(options.devicePollIntervalMs).toBe(2_000);
    expect(options.watch).toBe(true);
    expect(options.watchIntervalMs).toBe(45_000);
    expect(options.watchMaxRuns).toBe(3);
  });
});

describe("countConnectedAdbDevicesFromOutput", () => {
  it("counts only fully connected adb devices", () => {
    const count = countConnectedAdbDevicesFromOutput(
      [
        "List of devices attached",
        "emulator-5554\tdevice",
        "R5CX1234ABC\tunauthorized",
        "192.168.1.2:5555\toffline",
        "ZX1G22\tdevice product:pixel model:Pixel transport_id:3",
        "",
      ].join("\n"),
    );

    expect(count).toBe(2);
  });
});

describe("runCodexSyncWatch", () => {
  it("runs the requested number of watch iterations and only sleeps between them", async () => {
    const seenIterations: number[] = [];
    const sleepCalls: number[] = [];
    const preparedIterations: number[] = [];
    const lifecycleStates: string[] = [];

    await runCodexSyncWatch(
      {
        token: "secret-token",
        baseUrl: "http://127.0.0.1:3945",
        port: 3945,
        useAdbForward: false,
        waitForDevice: false,
        devicePollIntervalMs: 3_000,
        watch: true,
        watchIntervalMs: 12_000,
        watchMaxRuns: 3,
        force: false,
        json: false,
        source: "desktop-codex-sync",
      },
      {
        async prepareTransport() {
          preparedIterations.push(preparedIterations.length + 1);
        },
        async executeSync() {
          return {
            baseUrl: "http://127.0.0.1:3945",
            adbForwarded: false,
            desktop: {
              profileId: "openai-codex:oauth",
              expiresAt: 200_000,
              refreshRecommended: false,
            },
            phoneBefore: {
              configured: true,
              expired: false,
              refreshRecommended: false,
            },
            action: "skip",
            reason: "phone-auth-healthy",
            imported: false,
            refreshedAfterImport: false,
          };
        },
        async sleep(delayMs) {
          sleepCalls.push(delayMs);
        },
        onLifecycleEvent(event) {
          lifecycleStates.push(event.state);
        },
        onIteration(summary) {
          seenIterations.push(summary.iteration);
          expect(summary.kind).toBe("iteration");
        },
      },
    );

    expect(seenIterations).toEqual([1, 2, 3]);
    expect(preparedIterations).toEqual([1, 2, 3]);
    expect(sleepCalls).toEqual([12_000, 12_000]);
    expect(lifecycleStates).toEqual(["started"]);
  });

  it("continues after recoverable watch errors", async () => {
    const successIterations: number[] = [];
    const errorIterations: Array<{ iteration: number; error: string }> = [];
    const lifecycleStates: Array<{ state: string; iteration?: number; error?: string }> = [];
    let attempts = 0;

    await runCodexSyncWatch(
      {
        token: "secret-token",
        baseUrl: "http://127.0.0.1:3945",
        port: 3945,
        useAdbForward: false,
        waitForDevice: false,
        devicePollIntervalMs: 3_000,
        watch: true,
        watchIntervalMs: 5_000,
        watchMaxRuns: 2,
        force: false,
        json: false,
        source: "desktop-codex-sync",
      },
      {
        async executeSync() {
          attempts += 1;
          if (attempts === 1) {
            throw new Error("fetch failed");
          }
          return {
            baseUrl: "http://127.0.0.1:3945",
            adbForwarded: false,
            desktop: {
              profileId: "openai-codex:oauth",
              expiresAt: 200_000,
              refreshRecommended: false,
            },
            phoneBefore: {
              configured: true,
              expired: false,
              refreshRecommended: false,
            },
            action: "skip",
            reason: "phone-auth-healthy",
            imported: false,
            refreshedAfterImport: false,
          };
        },
        async sleep() {},
        onLifecycleEvent(event) {
          lifecycleStates.push({
            state: event.state,
            iteration: event.iteration,
            error: event.error,
          });
        },
        onIteration(summary) {
          successIterations.push(summary.iteration);
          expect(summary.kind).toBe("iteration");
        },
        onIterationError(summary) {
          expect(summary.kind).toBe("error");
          errorIterations.push({
            iteration: summary.iteration,
            error: summary.error,
          });
        },
      },
    );

    expect(errorIterations).toEqual([
      {
        iteration: 1,
        error: "fetch failed",
      },
    ]);
    expect(successIterations).toEqual([2]);
    expect(lifecycleStates).toEqual([
      {
        state: "started",
        iteration: undefined,
        error: undefined,
      },
      {
        state: "recoverable_error",
        iteration: 1,
        error: "fetch failed",
      },
      {
        state: "recovered",
        iteration: 2,
        error: undefined,
      },
    ]);
  });

  it("emits waiting and connected lifecycle events when device wait is needed", async () => {
    const lifecycleStates: string[] = [];
    let pollCount = 0;

    await waitForAdbDevice(
      {
        token: "secret-token",
        baseUrl: "http://127.0.0.1:3945",
        port: 3945,
        useAdbForward: true,
        waitForDevice: true,
        devicePollIntervalMs: 1000,
        watch: true,
        watchIntervalMs: 5000,
        watchMaxRuns: 1,
        force: false,
        json: false,
        source: "desktop-codex-sync",
      },
      {
        requireAdbFn() {},
        async sleep() {},
        connectedDeviceCount() {
          pollCount += 1;
          return pollCount >= 2 ? 1 : 0;
        },
        onLifecycleEvent(event) {
          lifecycleStates.push(event.state);
        },
      },
    );

    expect(lifecycleStates).toEqual(["waiting_for_device", "device_connected"]);
  });
});
