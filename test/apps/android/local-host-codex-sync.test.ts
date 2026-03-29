import { describe, expect, it } from "vitest";
import type { AuthProfileStore, OAuthCredential } from "../../../src/agents/auth-profiles/types.js";
import {
  parseCli,
  planCodexSync,
  runCodexSyncWatch,
  selectDesktopCodexProfile,
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

  it("parses watch settings", () => {
    const options = parseCli([
      "--token",
      "secret-token",
      "--watch",
      "--watch-interval-ms",
      "45000",
      "--watch-max-runs",
      "3",
    ]);

    expect(options.watch).toBe(true);
    expect(options.watchIntervalMs).toBe(45_000);
    expect(options.watchMaxRuns).toBe(3);
  });
});

describe("runCodexSyncWatch", () => {
  it("runs the requested number of watch iterations and only sleeps between them", async () => {
    const seenIterations: number[] = [];
    const sleepCalls: number[] = [];

    await runCodexSyncWatch(
      {
        token: "secret-token",
        baseUrl: "http://127.0.0.1:3945",
        port: 3945,
        useAdbForward: false,
        watch: true,
        watchIntervalMs: 12_000,
        watchMaxRuns: 3,
        force: false,
        json: false,
        source: "desktop-codex-sync",
      },
      {
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
        onIteration(summary) {
          seenIterations.push(summary.iteration);
        },
      },
    );

    expect(seenIterations).toEqual([1, 2, 3]);
    expect(sleepCalls).toEqual([12_000, 12_000]);
  });
});
