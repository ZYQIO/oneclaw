import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { parseArgs } from "node:util";
import qrcode from "qrcode-terminal";

type CliOptions = {
  component: string;
  downloadUrl: string;
  artifactDir: string;
  apkPath?: string;
  packageChecksumBase64?: string;
  signatureChecksumBase64?: string;
  wifiSsid?: string;
  wifiSecurity?: string;
  wifiPassword?: string;
  ascii: boolean;
  json: boolean;
};

type ProvisioningPayload = Record<string, string>;

const DEFAULT_ARTIFACT_DIR = path.resolve(
  process.env.OPENCLAW_ANDROID_LOCAL_HOST_ARTIFACT_DIR ??
    path.join(process.cwd(), ".tmp", "android-dedicated-qr"),
);

function usage(): string {
  return [
    "Usage:",
    "  pnpm exec tsx apps/android/scripts/local-host-dedicated-provisioning-qr.ts \\",
    "    --component com.afwsamples.testdpc/.DeviceAdminReceiver \\",
    "    --download-url https://example.com/testdpc.apk \\",
    "    --apk /path/to/testdpc.apk",
    "",
    "Required:",
    "  --component <package/.Receiver>",
    "  --download-url <https://...apk>",
    "",
    "Checksum sources:",
    "  --apk <path>                         Compute package checksum from a local APK",
    "  --package-checksum-base64 <base64>  Use a precomputed package checksum",
    "  --signature-checksum-base64 <base64>",
    "",
    "Optional:",
    "  --artifact-dir <dir>",
    "  --wifi-ssid <ssid>",
    "  --wifi-security <NONE|WEP|WPA|EAP>",
    "  --wifi-password <password>",
    "  --no-ascii",
    "  --json",
  ].join("\n");
}

function trimOptional(value: string | undefined): string | undefined {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

function requireOption(value: string | undefined, flag: string): string {
  if (!value) {
    throw new Error(`Missing required ${flag}.`);
  }
  return value;
}

function normalizeWifiSecurity(value: string | undefined): string | undefined {
  const trimmed = trimOptional(value);
  if (!trimmed) {
    return undefined;
  }
  const normalized = trimmed.toUpperCase();
  const allowed = new Set(["NONE", "WEP", "WPA", "EAP"]);
  if (!allowed.has(normalized)) {
    throw new Error(`Unsupported --wifi-security value: ${trimmed}`);
  }
  return normalized;
}

function parseCli(argv: string[]): CliOptions {
  const parsed = parseArgs({
    args: argv,
    options: {
      component: { type: "string" },
      "download-url": { type: "string" },
      "artifact-dir": { type: "string" },
      apk: { type: "string" },
      "package-checksum-base64": { type: "string" },
      "signature-checksum-base64": { type: "string" },
      "wifi-ssid": { type: "string" },
      "wifi-security": { type: "string" },
      "wifi-password": { type: "string" },
      ascii: { type: "boolean", default: true },
      "no-ascii": { type: "boolean", default: false },
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
    component: requireOption(trimOptional(parsed.values.component), "--component"),
    downloadUrl: requireOption(trimOptional(parsed.values["download-url"]), "--download-url"),
    artifactDir: path.resolve(trimOptional(parsed.values["artifact-dir"]) ?? DEFAULT_ARTIFACT_DIR),
    apkPath: trimOptional(parsed.values.apk),
    packageChecksumBase64: trimOptional(parsed.values["package-checksum-base64"]),
    signatureChecksumBase64: trimOptional(parsed.values["signature-checksum-base64"]),
    wifiSsid: trimOptional(parsed.values["wifi-ssid"]),
    wifiSecurity: normalizeWifiSecurity(parsed.values["wifi-security"]),
    wifiPassword: trimOptional(parsed.values["wifi-password"]),
    ascii: parsed.values["no-ascii"] ? false : (parsed.values.ascii ?? true),
    json: parsed.values.json ?? false,
  };
}

async function renderQrAscii(data: string): Promise<string> {
  return await new Promise((resolve) => {
    qrcode.generate(data, { small: true }, (output: string) => {
      resolve(output);
    });
  });
}

async function computePackageChecksumBase64(apkPath: string): Promise<string> {
  const bytes = await readFile(apkPath);
  return createHash("sha256").update(bytes).digest("base64");
}

function buildPayload(options: CliOptions, packageChecksumBase64?: string): ProvisioningPayload {
  const payload: ProvisioningPayload = {
    "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": options.component,
    "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": options.downloadUrl,
  };

  if (packageChecksumBase64) {
    payload["android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM"] =
      packageChecksumBase64;
  }
  if (options.signatureChecksumBase64) {
    payload["android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM"] =
      options.signatureChecksumBase64;
  }
  if (options.wifiSsid) {
    payload["android.app.extra.PROVISIONING_WIFI_SSID"] = options.wifiSsid;
  }
  if (options.wifiSecurity) {
    payload["android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE"] = options.wifiSecurity;
  }
  if (options.wifiPassword) {
    payload["android.app.extra.PROVISIONING_WIFI_PASSWORD"] = options.wifiPassword;
  }
  return payload;
}

function resolveChecksumMode(
  options: CliOptions,
  computedPackageChecksumBase64: string | undefined,
): { mode: string; value: string } {
  if (computedPackageChecksumBase64) {
    return {
      mode: "package",
      value: computedPackageChecksumBase64,
    };
  }
  if (options.signatureChecksumBase64) {
    return {
      mode: "signature",
      value: options.signatureChecksumBase64,
    };
  }
  throw new Error(
    "Provide --apk, --package-checksum-base64, or --signature-checksum-base64 so setup wizard can verify the DPC.",
  );
}

async function main(argv = process.argv.slice(2)): Promise<void> {
  const options = parseCli(argv);
  const computedPackageChecksumBase64 =
    options.apkPath != null
      ? await computePackageChecksumBase64(options.apkPath)
      : options.packageChecksumBase64;

  const checksum = resolveChecksumMode(options, computedPackageChecksumBase64);
  const payload = buildPayload(options, computedPackageChecksumBase64);
  const payloadJson = JSON.stringify(payload, null, 2);
  const payloadMinified = JSON.stringify(payload);

  await mkdir(options.artifactDir, { recursive: true });

  const payloadPath = path.join(options.artifactDir, "payload.json");
  const payloadMinifiedPath = path.join(options.artifactDir, "payload.min.json");
  const summaryPath = path.join(options.artifactDir, "summary.json");

  await writeFile(payloadPath, `${payloadJson}\n`, "utf8");
  await writeFile(payloadMinifiedPath, `${payloadMinified}\n`, "utf8");

  let qrAscii: string | null = null;
  if (options.ascii) {
    qrAscii = await renderQrAscii(payloadMinified);
    await writeFile(path.join(options.artifactDir, "qr.txt"), qrAscii, "utf8");
  }

  const summary = {
    component: options.component,
    downloadUrl: options.downloadUrl,
    checksumMode: checksum.mode,
    checksumBase64: checksum.value,
    apkPath: options.apkPath ?? null,
    artifactDir: options.artifactDir,
    payloadPath,
    payloadMinifiedPath,
    qrAsciiPath: qrAscii ? path.join(options.artifactDir, "qr.txt") : null,
    wifi: {
      ssid: options.wifiSsid ?? null,
      security: options.wifiSecurity ?? null,
      passwordProvided: options.wifiPassword != null,
    },
  };
  await writeFile(summaryPath, `${JSON.stringify(summary, null, 2)}\n`, "utf8");

  if (options.json) {
    console.log(JSON.stringify(summary, null, 2));
    return;
  }

  console.log(`provisioning.component=${options.component}`);
  console.log(`provisioning.download_url=${options.downloadUrl}`);
  console.log(`provisioning.checksum_mode=${checksum.mode}`);
  console.log(`artifacts.payload=${payloadPath}`);
  console.log(`artifacts.summary=${summaryPath}`);
  if (options.wifiSsid) {
    console.log(
      `provisioning.wifi=${options.wifiSsid} security=${options.wifiSecurity ?? "unspecified"}`,
    );
  }
  if (qrAscii) {
    console.log("");
    process.stdout.write(qrAscii);
  }
}

await main();
