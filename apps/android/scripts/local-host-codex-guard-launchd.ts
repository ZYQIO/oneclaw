import { spawnSync } from "node:child_process";
import { access, chmod, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { parseArgs } from "node:util";
import { buildLaunchAgentPlist } from "../../../src/daemon/launchd-plist.js";
import { resolveHomeDir, resolveUserPathWithHome } from "../../../src/daemon/paths.js";

export type GuardLaunchdCommand = "install" | "status" | "uninstall";

export type GuardLaunchdCliOptions = {
  command: GuardLaunchdCommand;
  envFile?: string;
  repoRoot?: string;
  stateDir?: string;
  artifactDir?: string;
  label: string;
  transport: "adb-forward" | "direct";
  adbBin?: string;
  baseUrl?: string;
  port: number;
  watchIntervalMs?: number;
  devicePollIntervalMs?: number;
  source: string;
  json: boolean;
};

export type GuardLaunchdPlan = {
  label: string;
  repoRoot: string;
  envFile?: string;
  stateDir: string;
  artifactDir: string;
  logDir: string;
  wrapperPath: string;
  plistPath: string;
  launchAgentsDir: string;
  stdoutPath: string;
  stderrPath: string;
  transport: "adb-forward" | "direct";
  adbBin?: string;
  commandArgs: string[];
  wrapperScript: string;
  plist: string;
};

export type GuardLaunchdStatus = {
  label: string;
  plistPath: string;
  wrapperPath: string;
  stateDir: string;
  artifactDir: string;
  installed: boolean;
  loaded: boolean;
  runtime?: {
    state?: string;
    pid?: number;
    lastExitStatus?: number;
    lastExitReason?: string;
  };
  latestArtifact?: unknown;
};

const DEFAULT_PORT = 3945;
const DEFAULT_LABEL = "ai.openclaw.android-local-host-codex-guard";
const DEFAULT_STATE_DIR = "~/.openclaw/android-local-host-codex-guard";
const DEFAULT_SOURCE = "desktop-codex-guard-launchd";
const DEFAULT_REPO_ROOT = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../../..",
);
const DEFAULT_WRAPPER_BASENAME = "run.sh";
const DEFAULT_STDOUT_BASENAME = "guard.log";
const DEFAULT_STDERR_BASENAME = "guard.err.log";
const DEFAULT_LOG_DIRNAME = "logs";
const DEFAULT_ARTIFACT_DIRNAME = "artifacts";
const REQUIRED_ENV_KEY = "OPENCLAW_ANDROID_LOCAL_HOST_TOKEN";

function usage(): string {
  return [
    "Usage:",
    "  pnpm android:local-host:codex-guard:launchd -- [install] --env-file <path>",
    "  pnpm android:local-host:codex-guard:launchd -- status",
    "  pnpm android:local-host:codex-guard:launchd -- uninstall",
    "",
    "Options:",
    "  --env-file <path>",
    "  --repo-root <path>",
    "  --state-dir <path>",
    "  --artifact-dir <path>",
    "  --label <launchd-label>",
    "  --transport <adb-forward|direct>",
    "  --adb-bin <path>",
    "  --base-url <url>",
    "  --port <port>",
    "  --watch-interval-ms <ms>",
    "  --device-poll-interval-ms <ms>",
    "  --source <label>",
    "  --json",
    "",
    "Notes:",
    "  - install requires an env file that exports OPENCLAW_ANDROID_LOCAL_HOST_TOKEN.",
    "  - default install mode uses adb forward + wait-for-device + watch.",
    "  - installed state lives under ~/.openclaw/android-local-host-codex-guard by default.",
  ].join("\n");
}

function trimOptional(value: string | undefined): string | undefined {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
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

function parseOptionalPositiveInteger(
  value: string | undefined,
  label: string,
): number | undefined {
  const trimmed = value?.trim();
  if (!trimmed) {
    return undefined;
  }
  const parsed = Number.parseInt(trimmed, 10);
  if (!Number.isInteger(parsed) || parsed < 1) {
    throw new Error(`Invalid ${label}: ${value}`);
  }
  return parsed;
}

export function parseCli(
  argv: string[],
  env: NodeJS.ProcessEnv = process.env,
): GuardLaunchdCliOptions {
  const parsed = parseArgs({
    args: argv.filter((arg) => arg !== "--"),
    options: {
      "env-file": { type: "string" },
      "repo-root": { type: "string" },
      "state-dir": { type: "string" },
      "artifact-dir": { type: "string" },
      label: { type: "string" },
      transport: { type: "string" },
      "adb-bin": { type: "string" },
      "base-url": { type: "string" },
      port: { type: "string" },
      "watch-interval-ms": { type: "string" },
      "device-poll-interval-ms": { type: "string" },
      source: { type: "string" },
      json: { type: "boolean", default: false },
      help: { type: "boolean", short: "h" },
    },
    allowPositionals: true,
  });
  if (parsed.values.help) {
    console.log(usage());
    process.exit(0);
  }

  const commandRaw = parsed.positionals[0]?.trim() || "install";
  if (commandRaw !== "install" && commandRaw !== "status" && commandRaw !== "uninstall") {
    throw new Error(`Unsupported command: ${commandRaw}`);
  }
  const transportRaw =
    trimOptional(parsed.values.transport ?? env.OPENCLAW_ANDROID_LOCAL_HOST_CODEX_GUARD_TRANSPORT) ??
    "adb-forward";
  if (transportRaw !== "adb-forward" && transportRaw !== "direct") {
    throw new Error(`Unsupported transport: ${transportRaw}`);
  }

  return {
    command: commandRaw,
    envFile: trimOptional(
      parsed.values["env-file"] ?? env.OPENCLAW_ANDROID_LOCAL_HOST_CODEX_GUARD_ENV_FILE,
    ),
    repoRoot: trimOptional(
      parsed.values["repo-root"] ?? env.OPENCLAW_ANDROID_LOCAL_HOST_CODEX_GUARD_REPO_ROOT,
    ),
    stateDir: trimOptional(
      parsed.values["state-dir"] ?? env.OPENCLAW_ANDROID_LOCAL_HOST_CODEX_GUARD_STATE_DIR,
    ),
    artifactDir: trimOptional(
      parsed.values["artifact-dir"] ?? env.OPENCLAW_ANDROID_LOCAL_HOST_CODEX_GUARD_ARTIFACT_DIR,
    ),
    label: trimOptional(parsed.values.label ?? env.OPENCLAW_ANDROID_LOCAL_HOST_CODEX_GUARD_LABEL) ??
      DEFAULT_LABEL,
    transport: transportRaw,
    adbBin: trimOptional(parsed.values["adb-bin"] ?? env.OPENCLAW_ANDROID_LOCAL_HOST_ADB_BIN),
    baseUrl: trimOptional(
      parsed.values["base-url"] ?? env.OPENCLAW_ANDROID_LOCAL_HOST_BASE_URL,
    ),
    port: parsePort(parsed.values.port ?? env.OPENCLAW_ANDROID_LOCAL_HOST_PORT),
    watchIntervalMs: parseOptionalPositiveInteger(
      parsed.values["watch-interval-ms"] ??
        env.OPENCLAW_ANDROID_LOCAL_HOST_CODEX_SYNC_INTERVAL_MS,
      "--watch-interval-ms",
    ),
    devicePollIntervalMs: parseOptionalPositiveInteger(
      parsed.values["device-poll-interval-ms"] ??
        env.OPENCLAW_ANDROID_LOCAL_HOST_CODEX_SYNC_DEVICE_POLL_INTERVAL_MS,
      "--device-poll-interval-ms",
    ),
    source:
      trimOptional(parsed.values.source ?? env.OPENCLAW_ANDROID_LOCAL_HOST_CODEX_GUARD_SOURCE) ??
      DEFAULT_SOURCE,
    json: parsed.values.json ?? false,
  };
}

function shellQuote(value: string): string {
  return `'${value.replaceAll("'", "'\\''")}'`;
}

function parseEnvAssignment(rawLine: string): { key: string; value: string } | null {
  const trimmed = rawLine.trim();
  if (!trimmed || trimmed.startsWith("#")) {
    return null;
  }
  const withoutExport = trimmed.startsWith("export ") ? trimmed.slice("export ".length).trim() : trimmed;
  const eqIndex = withoutExport.indexOf("=");
  if (eqIndex <= 0) {
    return null;
  }
  const key = withoutExport.slice(0, eqIndex).trim();
  if (!key) {
    return null;
  }
  let value = withoutExport.slice(eqIndex + 1).trim();
  if (
    value.length >= 2 &&
    ((value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'")))
  ) {
    value = value.slice(1, -1);
  }
  return { key, value };
}

async function readEnvFile(pathname: string): Promise<Record<string, string>> {
  const content = await readFile(pathname, "utf8");
  const environment: Record<string, string> = {};
  for (const rawLine of content.split(/\r?\n/)) {
    const parsed = parseEnvAssignment(rawLine);
    if (!parsed) {
      continue;
    }
    environment[parsed.key] = parsed.value;
  }
  return environment;
}

function resolveGuiDomain(): string {
  return typeof process.getuid === "function" ? `gui/${process.getuid()}` : "gui/501";
}

function spawnLaunchctl(args: string[]): {
  stdout: string;
  stderr: string;
  status: number | null;
} {
  const result = spawnSync("launchctl", args, {
    encoding: "utf8",
  });
  return {
    stdout: result.stdout ?? "",
    stderr: result.stderr ?? "",
    status: result.status,
  };
}

function parseLaunchctlPrint(output: string): GuardLaunchdStatus["runtime"] {
  const runtime: GuardLaunchdStatus["runtime"] = {};
  for (const rawLine of output.split("\n")) {
    const eqIndex = rawLine.indexOf("=");
    if (eqIndex <= 0) {
      continue;
    }
    const key = rawLine.slice(0, eqIndex).trim().toLowerCase();
    const value = rawLine.slice(eqIndex + 1).trim();
    if (!value) {
      continue;
    }
    if (key === "state") {
      runtime.state = value;
      continue;
    }
    if (key === "pid") {
      const pid = Number.parseInt(value, 10);
      if (Number.isInteger(pid) && pid > 0) {
        runtime.pid = pid;
      }
      continue;
    }
    if (key === "last exit status") {
      const status = Number.parseInt(value, 10);
      if (Number.isInteger(status)) {
        runtime.lastExitStatus = status;
      }
      continue;
    }
    if (key === "last exit reason") {
      runtime.lastExitReason = value;
    }
  }
  return runtime;
}

function buildGuardCommandArgs(params: {
  transport: "adb-forward" | "direct";
  artifactDir: string;
  adbBin?: string;
  baseUrl?: string;
  port: number;
  watchIntervalMs?: number;
  devicePollIntervalMs?: number;
  source: string;
}): string[] {
  const args = ["apps/android/scripts/local-host-codex-sync.ts"];
  if (params.transport === "adb-forward") {
    args.push("--use-adb-forward", "--wait-for-device");
    if (params.adbBin) {
      args.push("--adb-bin", params.adbBin);
    }
  }
  args.push("--watch", "--json", "--artifact-dir", params.artifactDir, "--source", params.source);
  if (params.baseUrl) {
    args.push("--base-url", params.baseUrl);
  } else if (params.port !== DEFAULT_PORT) {
    args.push("--port", String(params.port));
  }
  if (params.watchIntervalMs != null) {
    args.push("--watch-interval-ms", String(params.watchIntervalMs));
  }
  if (params.devicePollIntervalMs != null) {
    args.push("--device-poll-interval-ms", String(params.devicePollIntervalMs));
  }
  return args;
}

function buildWrapperScript(params: {
  repoRoot: string;
  envFile: string;
  nodeBin: string;
  tsxCliPath: string;
  commandArgs: string[];
}): string {
  const execArgs = [params.nodeBin, params.tsxCliPath, ...params.commandArgs]
    .map((value) => shellQuote(value))
    .join(" ");
  return [
    "#!/bin/sh",
    "set -eu",
    "umask 077",
    `cd ${shellQuote(params.repoRoot)}`,
    "set -a",
    `. ${shellQuote(params.envFile)}`,
    "set +a",
    `exec ${execArgs}`,
    "",
  ].join("\n");
}

export function resolveGuardLaunchdPlan(
  options: Omit<GuardLaunchdCliOptions, "command" | "json">,
  env: NodeJS.ProcessEnv = process.env,
): GuardLaunchdPlan {
  const home = resolveHomeDir(env);
  const repoRoot = resolveUserPathWithHome(options.repoRoot ?? DEFAULT_REPO_ROOT, home);
  const stateDir = resolveUserPathWithHome(options.stateDir ?? DEFAULT_STATE_DIR, home);
  const artifactDir = options.artifactDir
    ? resolveUserPathWithHome(options.artifactDir, home)
    : path.join(stateDir, DEFAULT_ARTIFACT_DIRNAME);
  const logDir = path.join(stateDir, DEFAULT_LOG_DIRNAME);
  const launchAgentsDir = path.join(home, "Library", "LaunchAgents");
  const wrapperPath = path.join(stateDir, DEFAULT_WRAPPER_BASENAME);
  const plistPath = path.join(launchAgentsDir, `${options.label}.plist`);
  const stdoutPath = path.join(logDir, DEFAULT_STDOUT_BASENAME);
  const stderrPath = path.join(logDir, DEFAULT_STDERR_BASENAME);
  const tsxCliPath = path.join(repoRoot, "node_modules", "tsx", "dist", "cli.mjs");
  const commandArgs = buildGuardCommandArgs({
    transport: options.transport,
    artifactDir,
    adbBin: options.adbBin,
    baseUrl: options.baseUrl,
    port: options.port,
    watchIntervalMs: options.watchIntervalMs,
    devicePollIntervalMs: options.devicePollIntervalMs,
    source: options.source,
  });
  const envFile = options.envFile
    ? resolveUserPathWithHome(options.envFile, home)
    : undefined;
  const wrapperScript = envFile
    ? buildWrapperScript({
        repoRoot,
        envFile,
        nodeBin: process.execPath,
        tsxCliPath,
        commandArgs,
      })
    : "";
  const plist = buildLaunchAgentPlist({
    label: options.label,
    comment: "OpenClaw Android local-host Codex auth guard",
    programArguments: [wrapperPath],
    workingDirectory: repoRoot,
    stdoutPath,
    stderrPath,
  });
  return {
    label: options.label,
    repoRoot,
    ...(envFile ? { envFile } : {}),
    stateDir,
    artifactDir,
    logDir,
    wrapperPath,
    plistPath,
    launchAgentsDir,
    stdoutPath,
    stderrPath,
    transport: options.transport,
    ...(options.adbBin ? { adbBin: options.adbBin } : {}),
    commandArgs,
    wrapperScript,
    plist,
  };
}

function resolveAdbBinOrThrow(
  options: Pick<GuardLaunchdCliOptions, "adbBin" | "transport">,
  env: NodeJS.ProcessEnv = process.env,
): string | undefined {
  if (options.transport !== "adb-forward") {
    return undefined;
  }
  const explicit = trimOptional(options.adbBin);
  if (explicit) {
    return explicit;
  }
  const sdkRoot = trimOptional(env.ANDROID_SDK_ROOT) ?? trimOptional(env.ANDROID_HOME);
  if (sdkRoot) {
    return path.join(sdkRoot, "platform-tools", "adb");
  }
  const which = spawnSync("which", ["adb"], {
    encoding: "utf8",
  });
  const fromPath = trimOptional(which.stdout);
  if (fromPath) {
    return fromPath;
  }
  throw new Error(
    "Could not resolve adb for launchd install. Pass --adb-bin <path> or set ANDROID_SDK_ROOT / ANDROID_HOME.",
  );
}

async function ensureFileExists(pathname: string, label: string): Promise<void> {
  try {
    await access(pathname);
  } catch {
    throw new Error(`${label} not found: ${pathname}`);
  }
}

async function installGuard(options: GuardLaunchdCliOptions): Promise<GuardLaunchdStatus> {
  if (!options.envFile) {
    throw new Error("--env-file is required for install.");
  }
  const adbBin = resolveAdbBinOrThrow(options);
  const plan = resolveGuardLaunchdPlan(
    {
      ...options,
      ...(adbBin ? { adbBin } : {}),
    },
    process.env,
  );
  const envVars = await readEnvFile(plan.envFile ?? options.envFile);
  if (!trimOptional(envVars[REQUIRED_ENV_KEY])) {
    throw new Error(`Env file must define ${REQUIRED_ENV_KEY}.`);
  }
  await ensureFileExists(plan.envFile ?? options.envFile, "Env file");
  await ensureFileExists(path.join(plan.repoRoot, "apps/android/scripts/local-host-codex-sync.ts"), "Guard script");
  await ensureFileExists(path.join(plan.repoRoot, "node_modules/tsx/dist/cli.mjs"), "tsx runtime");

  await mkdir(plan.stateDir, { recursive: true, mode: 0o700 });
  await chmod(plan.stateDir, 0o700);
  await mkdir(plan.logDir, { recursive: true, mode: 0o700 });
  await chmod(plan.logDir, 0o700);
  await mkdir(plan.artifactDir, { recursive: true, mode: 0o700 });
  await chmod(plan.artifactDir, 0o700);
  await mkdir(plan.launchAgentsDir, { recursive: true, mode: 0o755 });
  await chmod(plan.launchAgentsDir, 0o755);

  await writeFile(plan.wrapperPath, plan.wrapperScript, { mode: 0o700 });
  await chmod(plan.wrapperPath, 0o700);
  await writeFile(plan.plistPath, plan.plist, { mode: 0o644 });
  await chmod(plan.plistPath, 0o644);

  const domain = resolveGuiDomain();
  const serviceTarget = `${domain}/${plan.label}`;
  spawnLaunchctl(["bootout", serviceTarget]);
  spawnLaunchctl(["enable", serviceTarget]);
  const boot = spawnLaunchctl(["bootstrap", domain, plan.plistPath]);
  if (boot.status !== 0) {
    const detail = trimOptional(boot.stderr) ?? trimOptional(boot.stdout) ?? "unknown launchctl error";
    throw new Error(`launchctl bootstrap failed: ${detail}`);
  }

  return await readGuardStatusFromPlan(plan);
}

async function uninstallGuard(options: GuardLaunchdCliOptions): Promise<{
  label: string;
  plistPath: string;
  wrapperPath: string;
  removedPlist: boolean;
  removedWrapper: boolean;
}> {
  const plan = resolveGuardLaunchdPlan(options, process.env);
  const serviceTarget = `${resolveGuiDomain()}/${plan.label}`;
  spawnLaunchctl(["bootout", serviceTarget]);

  let removedPlist = false;
  try {
    await rm(plan.plistPath, { force: true });
    removedPlist = true;
  } catch {
    removedPlist = false;
  }

  let removedWrapper = false;
  try {
    await rm(plan.wrapperPath, { force: true });
    removedWrapper = true;
  } catch {
    removedWrapper = false;
  }

  return {
    label: plan.label,
    plistPath: plan.plistPath,
    wrapperPath: plan.wrapperPath,
    removedPlist,
    removedWrapper,
  };
}

async function readLatestArtifact(pathname: string): Promise<unknown> {
  try {
    const raw = await readFile(pathname, "utf8");
    return JSON.parse(raw);
  } catch {
    return undefined;
  }
}

async function readGuardStatusFromPlan(plan: GuardLaunchdPlan): Promise<GuardLaunchdStatus> {
  let installed = true;
  try {
    await access(plan.plistPath);
    await access(plan.wrapperPath);
  } catch {
    installed = false;
  }

  const list = spawnLaunchctl(["list"]);
  const loaded = list.status === 0 && list.stdout.split("\n").some((line) => line.includes(plan.label));
  const print = spawnLaunchctl(["print", `${resolveGuiDomain()}/${plan.label}`]);
  const runtime = print.status === 0 ? parseLaunchctlPrint(print.stdout) : undefined;
  const latestArtifact = await readLatestArtifact(path.join(plan.artifactDir, "latest.json"));

  return {
    label: plan.label,
    plistPath: plan.plistPath,
    wrapperPath: plan.wrapperPath,
    stateDir: plan.stateDir,
    artifactDir: plan.artifactDir,
    installed,
    loaded,
    ...(runtime && Object.keys(runtime).length > 0 ? { runtime } : {}),
    ...(latestArtifact !== undefined ? { latestArtifact } : {}),
  };
}

async function readGuardStatus(options: GuardLaunchdCliOptions): Promise<GuardLaunchdStatus> {
  const plan = resolveGuardLaunchdPlan(options, process.env);
  return await readGuardStatusFromPlan(plan);
}

function printInstallStatus(status: GuardLaunchdStatus): void {
  console.log(`label=${status.label}`);
  console.log(`installed=${String(status.installed)}`);
  console.log(`loaded=${String(status.loaded)}`);
  console.log(`plist.path=${status.plistPath}`);
  console.log(`wrapper.path=${status.wrapperPath}`);
  console.log(`state.dir=${status.stateDir}`);
  console.log(`artifact.dir=${status.artifactDir}`);
  if (status.runtime?.state) {
    console.log(`runtime.state=${status.runtime.state}`);
  }
  if (status.runtime?.pid != null) {
    console.log(`runtime.pid=${status.runtime.pid}`);
  }
}

function printStatus(status: GuardLaunchdStatus): void {
  printInstallStatus(status);
  if (status.runtime?.lastExitStatus != null) {
    console.log(`runtime.last_exit_status=${status.runtime.lastExitStatus}`);
  }
  if (status.runtime?.lastExitReason) {
    console.log(`runtime.last_exit_reason=${status.runtime.lastExitReason}`);
  }
  if (
    status.latestArtifact &&
    typeof status.latestArtifact === "object" &&
    "kind" in status.latestArtifact &&
    "timestamp" in status.latestArtifact
  ) {
    const artifact = status.latestArtifact as {
      kind?: string;
      timestamp?: string;
      iteration?: number;
    };
    if (artifact.kind) {
      console.log(`artifact.latest_kind=${artifact.kind}`);
    }
    if (artifact.timestamp) {
      console.log(`artifact.latest_timestamp=${artifact.timestamp}`);
    }
    if (artifact.iteration != null) {
      console.log(`artifact.latest_iteration=${artifact.iteration}`);
    }
  }
}

export async function main(argv = process.argv.slice(2)): Promise<void> {
  if (process.platform !== "darwin") {
    throw new Error("This helper only supports macOS launchd.");
  }

  const options = parseCli(argv);
  if (options.command === "install") {
    const status = await installGuard(options);
    if (options.json) {
      console.log(JSON.stringify(status, null, 2));
      return;
    }
    printInstallStatus(status);
    return;
  }
  if (options.command === "uninstall") {
    const removed = await uninstallGuard(options);
    if (options.json) {
      console.log(JSON.stringify(removed, null, 2));
      return;
    }
    console.log(`label=${removed.label}`);
    console.log(`removed.plist=${String(removed.removedPlist)} path=${removed.plistPath}`);
    console.log(`removed.wrapper=${String(removed.removedWrapper)} path=${removed.wrapperPath}`);
    return;
  }
  const status = await readGuardStatus(options);
  if (options.json) {
    console.log(JSON.stringify(status, null, 2));
    return;
  }
  printStatus(status);
}

if (import.meta.main) {
  await main().catch((error: unknown) => {
    const message = error instanceof Error ? error.message : String(error);
    console.error(message);
    process.exit(1);
  });
}
