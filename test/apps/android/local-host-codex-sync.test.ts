import { describe, expect, it } from "vitest";
import type { AuthProfileStore, OAuthCredential } from "../../../src/agents/auth-profiles/types.js";
import {
  parseCli,
  planCodexSync,
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
});
