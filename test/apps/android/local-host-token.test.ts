import { describe, expect, it } from "vitest";
import {
  formatShellExport,
  parseBroadcastResult,
  parseCli,
  parseConnectedDeviceCount,
  parseTokenValue,
} from "../../../apps/android/scripts/local-host-token.js";

describe("parseCli", () => {
  it("uses the default debug export settings", () => {
    const options = parseCli([]);

    expect(options.adbBin).toBe("adb");
    expect(options.appPackage).toBe("ai.openclaw.app");
    expect(options.appComponent).toBe("ai.openclaw.app/.MainActivity");
    expect(options.exportAction).toBe("ai.openclaw.app.action.EXPORT_LOCAL_HOST_TOKEN");
    expect(options.cachePath).toBe("cache/debug-local-host-token.txt");
    expect(options.launch).toBe(true);
    expect(options.json).toBe(false);
    expect(options.shellExport).toBe(false);
  });

  it("parses serial, json, export, and no-launch overrides", () => {
    const options = parseCli([
      "--serial",
      "device-123",
      "--json",
      "--export",
      "--no-launch",
    ]);

    expect(options.serial).toBe("device-123");
    expect(options.json).toBe(true);
    expect(options.shellExport).toBe(true);
    expect(options.launch).toBe(false);
  });
});

describe("parseConnectedDeviceCount", () => {
  it("counts only adb-ready devices", () => {
    const count = parseConnectedDeviceCount([
      "List of devices attached",
      "emulator-5554          device product:sdk_gphone64",
      "ZX1G22 unauthorized usb:1-1",
      "PFEM10                 device usb:1-1 product:PFEM10",
      "",
    ].join("\n"));

    expect(count).toBe(2);
  });
});

describe("parseBroadcastResult", () => {
  it("extracts result code and data path from am broadcast output", () => {
    const result = parseBroadcastResult(
      'Broadcast completed: result=1, data="/data/user/0/ai.openclaw.app/cache/debug-local-host-token.txt"',
    );

    expect(result.resultCode).toBe(1);
    expect(result.resultData).toBe(
      "/data/user/0/ai.openclaw.app/cache/debug-local-host-token.txt",
    );
  });
});

describe("parseTokenValue", () => {
  it("accepts valid trimmed token output", () => {
    expect(parseTokenValue(`  ocrt_${"a".repeat(64)}\n`)).toBe(`ocrt_${"a".repeat(64)}`);
  });

  it("rejects invalid token output", () => {
    expect(() => parseTokenValue("debug-token")).toThrow(
      "Invalid local-host token output: debug-token",
    );
  });
});

describe("formatShellExport", () => {
  it("renders a reusable shell assignment", () => {
    expect(formatShellExport(`ocrt_${"b".repeat(64)}`)).toBe(
      `OPENCLAW_ANDROID_LOCAL_HOST_TOKEN='ocrt_${"b".repeat(64)}'`,
    );
  });
});
