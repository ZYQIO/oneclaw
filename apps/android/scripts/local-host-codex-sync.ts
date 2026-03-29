import { execFileSync } from "node:child_process";
import { parseArgs } from "node:util";
import { resolveAuthProfileOrder } from "../../../src/agents/auth-profiles/order.js";
import { ensureAuthProfileStore } from "../../../src/agents/auth-profiles/store.js";
import type { AuthProfileStore, OAuthCredential } from "../../../src/agents/auth-profiles/types.js";
import { loadConfig } from "../../../src/config/io.js";
import type { OpenClawConfig } from "../../../src/config/config.js";

export type SyncCliOptions = {
  agentDir?: string;
  baseUrl: string;
  token: string;
  port: number;
  useAdbForward: boolean;
  watch: boolean;
  watchIntervalMs: number;
  watchMaxRuns?: number;
  force: boolean;
  json: boolean;
  source: string;
};

export type PhoneCodexStatusSnapshot = {
  provider?: string;
  configured?: boolean;
  refreshed?: boolean;
  imported?: boolean;
  accountIdPresent?: boolean;
  emailHint?: string;
  expiresAt?: number;
  expiresInMs?: number;
  expired?: boolean;
  refreshRecommended?: boolean;
  previousExpiresAt?: number;
  source?: string;
};

export type DesktopCodexProfile = {
  profileId: string;
  credential: OAuthCredential;
};

export type SyncPlan = {
  action: "skip" | "import" | "import-and-refresh";
  reason:
    | "forced"
    | "phone-missing-auth"
    | "phone-auth-expired"
    | "phone-auth-refresh-recommended"
    | "phone-auth-healthy";
};

type CodexImportPayload = {
  access: string;
  refresh: string;
  expires: number;
  accountId: string;
  email?: string;
  source: string;
};

type SyncSummary = {
  baseUrl: string;
  adbForwarded: boolean;
  desktop: {
    profileId: string;
    emailHint?: string;
    expiresAt: number;
    refreshRecommended: boolean;
  };
  phoneBefore: PhoneCodexStatusSnapshot;
  action: SyncPlan["action"];
  reason: SyncPlan["reason"];
  imported: boolean;
  refreshedAfterImport: boolean;
  phoneAfter?: PhoneCodexStatusSnapshot;
};

export type WatchIterationSummary = SyncSummary & {
  iteration: number;
  timestamp: string;
};

const DEFAULT_PORT = 3945;
const DEFAULT_SOURCE = "desktop-codex-sync";
const DESKTOP_REFRESH_WINDOW_MS = 30_000;
const DEFAULT_WATCH_INTERVAL_MS = 30_000;
const MIN_WATCH_INTERVAL_MS = 1_000;

function usage(): string {
  return [
    "Usage:",
    "  OPENCLAW_ANDROID_LOCAL_HOST_TOKEN=<token> \\",
    "  [OPENCLAW_ANDROID_LOCAL_HOST_BASE_URL=http://127.0.0.1:3945] \\",
    "  [OPENCLAW_ANDROID_LOCAL_HOST_USE_ADB_FORWARD=1] \\",
    "  pnpm android:local-host:codex-sync",
    "",
    "Options:",
    "  --base-url <url>",
    "  --token <token>",
    "  --port <port>",
    "  --agent-dir <dir>",
    "  --use-adb-forward",
    "  --watch",
    "  --watch-interval-ms <ms>",
    "  --watch-max-runs <count>",
    "  --force",
    "  --source <label>",
    "  --json",
    "",
    "What it does:",
    "  1. Reads the preferred desktop openai-codex OAuth credential",
    "  2. Checks the phone's /auth/codex/status snapshot",
    "  3. Imports desktop auth to the phone only when phone auth is missing/stale, or when --force is used",
    "  4. Optionally refreshes on-phone auth immediately when the desktop credential is already near expiry",
    "  5. With --watch, keeps polling and auto-refills again whenever the phone auth later goes stale",
  ].join("\n");
}

function readBoolEnv(value: string | undefined, fallback = false): boolean {
  const normalized = value?.trim().toLowerCase();
  if (!normalized) {
    return fallback;
  }
  return normalized === "1" || normalized === "true" || normalized === "yes" || normalized === "on";
}

function parsePort(value: string | undefined): number {
  const trimmed = value?.trim();
  if (!trimmed) {
    return DEFAULT_PORT;
  }
  const parsed = Number.parseInt(trimmed, 10);
  if (!Number.isInteger(parsed) || parsed < 1 || parsed > 65_535) {
    throw new Error(`Invalid port: ${value}`);
  }
  return parsed;
}

function parsePositiveInteger(
  value: string | undefined,
  label: string,
  minimum = 1,
): number {
  const trimmed = value?.trim();
  if (!trimmed) {
    throw new Error(`Missing ${label}.`);
  }
  const parsed = Number.parseInt(trimmed, 10);
  if (!Number.isInteger(parsed) || parsed < minimum) {
    throw new Error(`Invalid ${label}: ${value}`);
  }
  return parsed;
}

function parseOptionalPositiveInteger(
  value: string | undefined,
  label: string,
  minimum = 1,
): number | undefined {
  const trimmed = value?.trim();
  if (!trimmed) {
    return undefined;
  }
  return parsePositiveInteger(trimmed, label, minimum);
}

function trimOptional(value: string | undefined): string | undefined {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

function normalizeBaseUrl(value: string): string {
  const url = new URL(value.trim());
  if (url.protocol !== "http:" && url.protocol !== "https:") {
    throw new Error(`Unsupported base URL protocol: ${url.protocol}`);
  }
  return url.toString().replace(/\/$/, "");
}

function normalizeCliArgs(argv: string[]): string[] {
  return argv[0] === "--" ? argv.slice(1) : argv;
}

export function parseCli(argv: string[], env: NodeJS.ProcessEnv = process.env): SyncCliOptions {
  const normalizedArgv = normalizeCliArgs(argv);
  const parsed = parseArgs({
    args: normalizedArgv,
    options: {
      "agent-dir": { type: "string" },
      "base-url": { type: "string" },
      token: { type: "string" },
      port: { type: "string" },
      "use-adb-forward": { type: "boolean", default: false },
      watch: { type: "boolean", default: false },
      "watch-interval-ms": { type: "string" },
      "watch-max-runs": { type: "string" },
      force: { type: "boolean", default: false },
      json: { type: "boolean", default: false },
      source: { type: "string" },
      help: { type: "boolean", short: "h" },
    },
    allowPositionals: false,
  });

  if (parsed.values.help) {
    console.log(usage());
    process.exit(0);
  }

  const port = parsePort(parsed.values.port ?? env.OPENCLAW_ANDROID_LOCAL_HOST_PORT);
  const useAdbForward =
    parsed.values["use-adb-forward"] || readBoolEnv(env.OPENCLAW_ANDROID_LOCAL_HOST_USE_ADB_FORWARD);
  const watch =
    parsed.values.watch || readBoolEnv(env.OPENCLAW_ANDROID_LOCAL_HOST_CODEX_SYNC_WATCH);
  const watchIntervalMs = parseOptionalPositiveInteger(
    parsed.values["watch-interval-ms"] ?? env.OPENCLAW_ANDROID_LOCAL_HOST_CODEX_SYNC_INTERVAL_MS,
    "--watch-interval-ms",
    MIN_WATCH_INTERVAL_MS,
  ) ?? DEFAULT_WATCH_INTERVAL_MS;
  const watchMaxRuns = parseOptionalPositiveInteger(
    parsed.values["watch-max-runs"] ?? env.OPENCLAW_ANDROID_LOCAL_HOST_CODEX_SYNC_MAX_RUNS,
    "--watch-max-runs",
    1,
  );
  const baseUrl = normalizeBaseUrl(
    trimOptional(parsed.values["base-url"] ?? env.OPENCLAW_ANDROID_LOCAL_HOST_BASE_URL) ??
      `http://127.0.0.1:${port}`,
  );
  const token = trimOptional(parsed.values.token ?? env.OPENCLAW_ANDROID_LOCAL_HOST_TOKEN);
  if (!token) {
    throw new Error("OPENCLAW_ANDROID_LOCAL_HOST_TOKEN or --token is required.");
  }

  return {
    agentDir: trimOptional(parsed.values["agent-dir"]),
    baseUrl,
    token,
    port,
    useAdbForward,
    watch,
    watchIntervalMs,
    ...(watchMaxRuns != null ? { watchMaxRuns } : {}),
    force: parsed.values.force ?? false,
    json: parsed.values.json ?? false,
    source: trimOptional(parsed.values.source) ?? DEFAULT_SOURCE,
  };
}

function hasText(value: string | undefined): boolean {
  return (value?.trim().length ?? 0) > 0;
}

function isOpenAICodexProvider(value: string | undefined): boolean {
  return value?.trim().toLowerCase() === "openai-codex";
}

function isUsableDesktopCredential(credential: unknown): credential is OAuthCredential {
  if (!credential || typeof credential !== "object") {
    return false;
  }
  const typed = credential as Partial<OAuthCredential>;
  return (
    typed.type === "oauth" &&
    isOpenAICodexProvider(typed.provider) &&
    hasText(typed.access) &&
    hasText(typed.refresh) &&
    typeof typed.expires === "number" &&
    Number.isFinite(typed.expires) &&
    typed.expires > 0 &&
    hasText(typed.accountId)
  );
}

export function selectDesktopCodexProfile(params: {
  store: AuthProfileStore;
  cfg?: OpenClawConfig;
}): DesktopCodexProfile {
  const ordered = resolveAuthProfileOrder({
    cfg: params.cfg,
    store: params.store,
    provider: "openai-codex",
  });
  const fallbacks = Object.keys(params.store.profiles).filter((profileId) => !ordered.includes(profileId));
  for (const profileId of [...ordered, ...fallbacks]) {
    const credential = params.store.profiles[profileId];
    if (isUsableDesktopCredential(credential)) {
      return {
        profileId,
        credential,
      };
    }
  }
  throw new Error(
    "No usable desktop openai-codex OAuth credential found. Run `openclaw models auth --provider openai-codex` first.",
  );
}

function maskEmail(value: string | undefined): string | undefined {
  const trimmed = value?.trim();
  if (!trimmed) {
    return undefined;
  }
  const atIndex = trimmed.indexOf("@");
  if (atIndex <= 0 || atIndex >= trimmed.length - 1) {
    return "***";
  }
  const local = trimmed.slice(0, atIndex);
  const domain = trimmed.slice(atIndex + 1);
  const maskedLocal =
    local.length <= 1 ? `${local[0]}***` : `${local[0]}***${local[local.length - 1]}`;
  return `${maskedLocal}@${domain}`;
}

function shouldRefreshSoon(expires: number, now = Date.now()): boolean {
  return expires <= now + DESKTOP_REFRESH_WINDOW_MS;
}

export function planCodexSync(params: {
  phoneStatus: PhoneCodexStatusSnapshot;
  desktopCredential: OAuthCredential;
  force?: boolean;
  now?: number;
}): SyncPlan {
  const force = params.force === true;
  const nearExpiry = shouldRefreshSoon(params.desktopCredential.expires, params.now);
  if (force) {
    return {
      action: nearExpiry ? "import-and-refresh" : "import",
      reason: "forced",
    };
  }
  if (params.phoneStatus.configured !== true) {
    return {
      action: nearExpiry ? "import-and-refresh" : "import",
      reason: "phone-missing-auth",
    };
  }
  if (params.phoneStatus.expired === true) {
    return {
      action: nearExpiry ? "import-and-refresh" : "import",
      reason: "phone-auth-expired",
    };
  }
  if (params.phoneStatus.refreshRecommended === true) {
    return {
      action: nearExpiry ? "import-and-refresh" : "import",
      reason: "phone-auth-refresh-recommended",
    };
  }
  return {
    action: "skip",
    reason: "phone-auth-healthy",
  };
}

function buildCodexImportPayload(params: {
  credential: OAuthCredential;
  source: string;
}): CodexImportPayload {
  const access = params.credential.access.trim();
  const refresh = params.credential.refresh.trim();
  const accountId = params.credential.accountId?.trim();
  if (!access || !refresh || !accountId) {
    throw new Error("Desktop openai-codex OAuth credential is missing access/refresh/accountId.");
  }
  return {
    access,
    refresh,
    expires: params.credential.expires,
    accountId,
    email: trimOptional(params.credential.email),
    source: params.source,
  };
}

function requireAdb(): void {
  try {
    execFileSync("adb", ["version"], {
      stdio: "ignore",
    });
  } catch {
    throw new Error("adb is required when --use-adb-forward is enabled.");
  }
}

function ensureAdbForward(port: number): void {
  requireAdb();
  const devices = execFileSync("adb", ["devices"], {
    encoding: "utf8",
  });
  const connected = devices
    .split("\n")
    .slice(1)
    .map((line) => line.trim())
    .filter(Boolean)
    .filter((line) => /\sdevice$/.test(line)).length;
  if (connected < 1) {
    throw new Error("No connected Android device (adb state=device).");
  }
  execFileSync("adb", ["forward", `tcp:${port}`, `tcp:${port}`], {
    stdio: "ignore",
  });
}

async function requestJson<T>(params: {
  baseUrl: string;
  token: string;
  path: string;
  method?: "GET" | "POST";
  body?: unknown;
}): Promise<T> {
  const url = new URL(params.path, `${params.baseUrl}/`);
  const response = await fetch(url, {
    method: params.method ?? "GET",
    headers: {
      Authorization: `Bearer ${params.token}`,
      Accept: "application/json",
      ...(params.body === undefined ? {} : { "Content-Type": "application/json" }),
    },
    body: params.body === undefined ? undefined : JSON.stringify(params.body),
  });
  const text = await response.text();
  const parsed = text ? safeJsonParse(text) : null;
  if (!response.ok) {
    const message =
      parsed && typeof parsed === "object" && "error" in parsed && typeof parsed.error === "string"
        ? parsed.error
        : text.trim() || `HTTP ${response.status}`;
    throw new Error(`${params.method ?? "GET"} ${params.path} failed (${response.status}): ${message}`);
  }
  if (parsed == null) {
    throw new Error(`${params.method ?? "GET"} ${params.path} returned an empty response.`);
  }
  return parsed as T;
}

function safeJsonParse(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    throw new Error(`Remote endpoint returned invalid JSON: ${text.slice(0, 160)}`);
  }
}

function printSummary(
  summary: SyncSummary,
  options?: {
    iteration?: number;
    timestamp?: string;
  },
): void {
  if (options?.iteration != null) {
    const prefix =
      options.timestamp != null
        ? `watch.iteration=${options.iteration} timestamp=${options.timestamp}`
        : `watch.iteration=${options.iteration}`;
    console.log(prefix);
  }
  console.log(`local_host.base_url=${summary.baseUrl}`);
  console.log(`desktop.profile=${summary.desktop.profileId}`);
  if (summary.desktop.emailHint) {
    console.log(`desktop.email_hint=${summary.desktop.emailHint}`);
  }
  console.log(
    `desktop.refresh_recommended=${String(summary.desktop.refreshRecommended)} expires_at=${summary.desktop.expiresAt}`,
  );
  console.log(
    `phone.before configured=${String(summary.phoneBefore.configured === true)} expired=${String(summary.phoneBefore.expired === true)} refresh_recommended=${String(summary.phoneBefore.refreshRecommended === true)}`,
  );
  console.log(`sync.action=${summary.action} reason=${summary.reason}`);
  console.log(`sync.imported=${String(summary.imported)} refreshed_after_import=${String(summary.refreshedAfterImport)}`);
  if (summary.phoneAfter) {
    console.log(
      `phone.after configured=${String(summary.phoneAfter.configured === true)} expired=${String(summary.phoneAfter.expired === true)} refresh_recommended=${String(summary.phoneAfter.refreshRecommended === true)}`,
    );
  }
}

function prepareTransport(options: SyncCliOptions): void {
  if (options.useAdbForward) {
    ensureAdbForward(options.port);
  }
}

async function executeCodexSync(options: SyncCliOptions): Promise<SyncSummary> {
  const cfg = loadConfig();
  const store = ensureAuthProfileStore(options.agentDir, { allowKeychainPrompt: false });
  const desktop = selectDesktopCodexProfile({ cfg, store });
  const phoneBefore = await requestJson<PhoneCodexStatusSnapshot>({
    baseUrl: options.baseUrl,
    token: options.token,
    path: "api/local-host/v1/auth/codex/status",
  });
  const plan = planCodexSync({
    phoneStatus: phoneBefore,
    desktopCredential: desktop.credential,
    force: options.force,
  });

  let imported = false;
  let refreshedAfterImport = false;
  let phoneAfter: PhoneCodexStatusSnapshot | undefined;

  if (plan.action !== "skip") {
    await requestJson<PhoneCodexStatusSnapshot>({
      baseUrl: options.baseUrl,
      token: options.token,
      path: "api/local-host/v1/auth/codex/import",
      method: "POST",
      body: buildCodexImportPayload({
        credential: desktop.credential,
        source: options.source,
      }),
    });
    imported = true;
    if (plan.action === "import-and-refresh") {
      phoneAfter = await requestJson<PhoneCodexStatusSnapshot>({
        baseUrl: options.baseUrl,
        token: options.token,
        path: "api/local-host/v1/auth/codex/refresh",
        method: "POST",
      });
      refreshedAfterImport = true;
    } else {
      phoneAfter = await requestJson<PhoneCodexStatusSnapshot>({
        baseUrl: options.baseUrl,
        token: options.token,
        path: "api/local-host/v1/auth/codex/status",
      });
    }
  }

  return {
    baseUrl: options.baseUrl,
    adbForwarded: options.useAdbForward,
    desktop: {
      profileId: desktop.profileId,
      emailHint: maskEmail(desktop.credential.email),
      expiresAt: desktop.credential.expires,
      refreshRecommended: shouldRefreshSoon(desktop.credential.expires),
    },
    phoneBefore,
    action: plan.action,
    reason: plan.reason,
    imported,
    refreshedAfterImport,
    ...(phoneAfter ? { phoneAfter } : {}),
  };
}

export async function runCodexSync(options: SyncCliOptions): Promise<SyncSummary> {
  prepareTransport(options);
  return await executeCodexSync(options);
}

async function sleepMs(delayMs: number): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, delayMs));
}

export async function runCodexSyncWatch(
  options: SyncCliOptions,
  deps: {
    executeSync?: (options: SyncCliOptions) => Promise<SyncSummary>;
    sleep?: (delayMs: number) => Promise<void>;
    onIteration?: (summary: WatchIterationSummary) => void | Promise<void>;
  } = {},
): Promise<void> {
  prepareTransport(options);
  const executeSync = deps.executeSync ?? executeCodexSync;
  const sleep = deps.sleep ?? sleepMs;
  const watchMaxRuns = options.watchMaxRuns;
  let iteration = 0;

  while (true) {
    iteration += 1;
    const summary = await executeSync(options);
    const iterationSummary: WatchIterationSummary = {
      ...summary,
      iteration,
      timestamp: new Date().toISOString(),
    };
    await deps.onIteration?.(iterationSummary);

    if (watchMaxRuns != null && iteration >= watchMaxRuns) {
      return;
    }
    await sleep(options.watchIntervalMs);
  }
}

export async function main(argv = process.argv.slice(2)): Promise<void> {
  const options = parseCli(argv);
  if (options.watch) {
    if (!options.json) {
      console.log(
        `watch.enabled=true interval_ms=${options.watchIntervalMs} max_runs=${options.watchMaxRuns ?? "unbounded"}`,
      );
    }
    await runCodexSyncWatch(options, {
      onIteration(summary) {
        if (options.json) {
          console.log(JSON.stringify(summary));
          return;
        }
        printSummary(summary, {
          iteration: summary.iteration,
          timestamp: summary.timestamp,
        });
      },
    });
    return;
  }
  const summary = await runCodexSync(options);
  if (options.json) {
    console.log(JSON.stringify(summary, null, 2));
    return;
  }
  printSummary(summary);
}

if (import.meta.main) {
  await main().catch((error: unknown) => {
    const message = error instanceof Error ? error.message : String(error);
    console.error(message);
    process.exit(1);
  });
}
