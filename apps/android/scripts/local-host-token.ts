import { spawnSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { parseArgs } from "node:util";

export type LocalHostTokenCliOptions = {
  adbBin: string;
  serial?: string;
  appPackage: string;
  appComponent: string;
  exportAction: string;
  cachePath: string;
  launch: boolean;
  json: boolean;
  shellExport: boolean;
};

export type LocalHostTokenExportResult = {
  token: string;
  appPackage: string;
  appComponent: string;
  exportAction: string;
  cachePath: string;
  launchedApp: boolean;
  serial?: string;
};

export type BroadcastResult = {
  resultCode?: number;
  resultData?: string;
};

const DEFAULT_ADB_BIN = "adb";
const DEFAULT_APP_PACKAGE = "ai.openclaw.app";
const DEFAULT_APP_COMPONENT = "ai.openclaw.app/.MainActivity";
const DEFAULT_EXPORT_ACTION = "ai.openclaw.app.action.EXPORT_LOCAL_HOST_TOKEN";
const DEFAULT_CACHE_PATH = "cache/debug-local-host-token.txt";
const TOKEN_REGEX = /^ocrt_[0-9a-f]{64}$/;

function usage(): string {
  return [
    "Usage:",
    "  pnpm android:local-host:token",
    "  pnpm android:local-host:token -- --export",
    "  pnpm android:local-host:token -- --json",
    "",
    "Options:",
    "  --adb-bin <path>",
    "  --serial <adb-serial>",
    "  --app-package <package>",
    "  --app-component <component>",
    "  --export-action <intent-action>",
    "  --cache-path <path>",
    "  --no-launch",
    "  --export",
    "  --json",
    "",
    "Notes:",
    "  - This helper is for trusted local debug flows only.",
    "  - It launches the app, triggers the debug-only adb export bridge, then reads the token back through run-as.",
    "  - Default output is the raw token so you can wrap it with command substitution.",
  ].join("\n");
}

function trimOptional(value: string | undefined): string | undefined {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

export function parseCli(
  argv: string[],
  env: NodeJS.ProcessEnv = process.env,
): LocalHostTokenCliOptions {
  const parsed = parseArgs({
    args: argv.filter((arg) => arg !== "--"),
    options: {
      "adb-bin": { type: "string" },
      serial: { type: "string" },
      "app-package": { type: "string" },
      "app-component": { type: "string" },
      "export-action": { type: "string" },
      "cache-path": { type: "string" },
      "no-launch": { type: "boolean", default: false },
      export: { type: "boolean", default: false },
      json: { type: "boolean", default: false },
      help: { type: "boolean", short: "h" },
    },
    allowPositionals: false,
  });
  if (parsed.values.help) {
    console.log(usage());
    process.exit(0);
  }

  return {
    adbBin: trimOptional(parsed.values["adb-bin"] ?? env.OPENCLAW_ANDROID_LOCAL_HOST_ADB_BIN) ??
      DEFAULT_ADB_BIN,
    serial: trimOptional(parsed.values.serial ?? env.ANDROID_SERIAL),
    appPackage: trimOptional(parsed.values["app-package"]) ?? DEFAULT_APP_PACKAGE,
    appComponent: trimOptional(parsed.values["app-component"]) ?? DEFAULT_APP_COMPONENT,
    exportAction: trimOptional(parsed.values["export-action"]) ?? DEFAULT_EXPORT_ACTION,
    cachePath: trimOptional(parsed.values["cache-path"]) ?? DEFAULT_CACHE_PATH,
    launch: !parsed.values["no-launch"],
    json: parsed.values.json,
    shellExport: parsed.values.export,
  };
}

export function parseConnectedDeviceCount(raw: string): number {
  return raw
    .split(/\r?\n/)
    .filter((line) => /^\S+\s+device(?:\s|$)/.test(line.trim()))
    .length;
}

export function parseBroadcastResult(raw: string): BroadcastResult {
  const resultCodeMatch = raw.match(/Broadcast completed: result=(-?\d+)/);
  const resultDataMatch = raw.match(/data="([^"]+)"/);
  const rawResultCode = resultCodeMatch?.[1];
  return {
    resultCode: rawResultCode ? Number.parseInt(rawResultCode, 10) : undefined,
    resultData: resultDataMatch?.[1],
  };
}

export function parseTokenValue(raw: string): string {
  const token = raw.trim();
  if (!TOKEN_REGEX.test(token)) {
    throw new Error(`Invalid local-host token output: ${token || "<empty>"}`);
  }
  return token;
}

export function formatShellExport(token: string): string {
  return `OPENCLAW_ANDROID_LOCAL_HOST_TOKEN='${token}'`;
}

function runAdb(
  adbBin: string,
  args: string[],
  serial?: string,
): ReturnType<typeof spawnSync> {
  const fullArgs = serial ? ["-s", serial, ...args] : args;
  return spawnSync(adbBin, fullArgs, {
    encoding: "utf8",
  });
}

function assertAdbSuccess(
  label: string,
  result: ReturnType<typeof spawnSync>,
): void {
  if (result.error) {
    throw result.error;
  }
  if (result.status !== 0) {
    const stderr = result.stderr?.trim();
    const stdout = result.stdout?.trim();
    throw new Error(
      [
        `${label} failed.`,
        stderr || stdout ? `output: ${stderr || stdout}` : undefined,
      ].filter(Boolean).join(" "),
    );
  }
}

export function runLocalHostTokenExport(
  options: LocalHostTokenCliOptions,
): LocalHostTokenExportResult {
  const devices = runAdb(options.adbBin, ["devices", "-l"], options.serial);
  assertAdbSuccess("adb devices", devices);
  const deviceCount = parseConnectedDeviceCount(devices.stdout);
  if (deviceCount < 1) {
    throw new Error("No connected Android device (adb state=device).");
  }
  if (deviceCount > 1 && !options.serial) {
    throw new Error("Multiple Android devices detected. Pass --serial or set ANDROID_SERIAL.");
  }

  if (options.launch) {
    const launch = runAdb(
      options.adbBin,
      ["shell", "am", "start", "-W", "-n", options.appComponent],
      options.serial,
    );
    assertAdbSuccess("adb start", launch);
  }

  const broadcast = runAdb(
    options.adbBin,
    [
      "shell",
      "am",
      "broadcast",
      "--receiver-registered-only",
      "-a",
      options.exportAction,
      "-p",
      options.appPackage,
    ],
    options.serial,
  );
  assertAdbSuccess("adb broadcast", broadcast);

  const broadcastResult = parseBroadcastResult(`${broadcast.stdout}\n${broadcast.stderr}`);
  if (
    broadcastResult.resultCode != null &&
    broadcastResult.resultCode !== 1
  ) {
    throw new Error(
      `Token export bridge returned result=${broadcastResult.resultCode}: ${broadcastResult.resultData ?? "unknown error"}`,
    );
  }

  const cachePath =
    trimOptional(broadcastResult.resultData)?.startsWith("/")
      ? trimOptional(broadcastResult.resultData)!
      : options.cachePath;

  const readback = runAdb(
    options.adbBin,
    ["shell", "run-as", options.appPackage, "cat", cachePath],
    options.serial,
  );
  assertAdbSuccess("adb run-as cat", readback);
  const token = parseTokenValue(readback.stdout);

  return {
    token,
    appPackage: options.appPackage,
    appComponent: options.appComponent,
    exportAction: options.exportAction,
    cachePath,
    launchedApp: options.launch,
    serial: options.serial,
  };
}

function main(): void {
  try {
    const options = parseCli(process.argv.slice(2));
    const result = runLocalHostTokenExport(options);
    if (options.json) {
      console.log(JSON.stringify(result));
      return;
    }
    if (options.shellExport) {
      console.log(formatShellExport(result.token));
      return;
    }
    console.log(result.token);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.error(message);
    process.exit(1);
  }
}

const scriptPath = fileURLToPath(import.meta.url);
if (process.argv[1] && path.resolve(process.argv[1]) === scriptPath) {
  main();
}
