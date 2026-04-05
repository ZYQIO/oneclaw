import { mkdtemp, readFile, stat } from "node:fs/promises";
import path from "node:path";
import { tmpdir } from "node:os";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import { syncRuntimePodAssets } from "./sync-assets.ts";

describe("syncRuntimePodAssets", () => {
  it("writes sanitized APK-safe asset metadata and staged files", async () => {
    const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..", "..");
    const sourceDir = path.join(repoRoot, "apps", "android", "runtime-pod");
    const artifactDir = await mkdtemp(path.join(tmpdir(), "openclaw-runtime-pod-artifacts-"));
    const targetDir = await mkdtemp(path.join(tmpdir(), "openclaw-runtime-pod-assets-"));

    const result = await syncRuntimePodAssets({
      repoRoot,
      sourceDir,
      artifactDir,
      targetDir,
      clean: true,
    });

    const manifest = JSON.parse(await readFile(result.assetManifestPath, "utf8")) as {
      assetBasePath: string;
      assetManifestPath: string;
      version: string;
      fileCount: number;
      files: Array<{ relativePath: string; assetPath: string }>;
    };
    const layoutText = await readFile(result.assetLayoutPath, "utf8");

    expect(manifest.assetBasePath).toBe("embedded-runtime-pod/staged");
    expect(manifest.assetManifestPath).toBe("embedded-runtime-pod/manifest.json");
    expect(manifest.version).toBe("0.12.0");
    expect(manifest.fileCount).toBe(26);
    expect(manifest.files.map((file) => file.relativePath)).toEqual([
      "bridge/manifest.json",
      "browser/auth/openai-codex-auth.json",
      "browser/manifest.json",
      "desktop/browser/manifest.json",
      "desktop/engine/manifest.json",
      "desktop/environment/manifest.json",
      "desktop/manifest.json",
      "desktop/plugins/manifest.json",
      "desktop/plugins/openclaw-plugin-host-placeholder.json",
      "desktop/profiles/openclaw-desktop-host.json",
      "desktop/supervisor/manifest.json",
      "desktop/tools/manifest.json",
      "runtime/config/runtime-env.json",
      "runtime/engine/manifest.json",
      "runtime/manifest.json",
      "runtime/tasks/plugin-allowlist-inspect.json",
      "runtime/tasks/runtime-smoke.json",
      "runtime/tasks/tool-brief-inspect.json",
      "toolkit/command-policy.json",
      "toolkit/manifest.json",
      "toolkit/tools/packaged-brief-inspector.json",
      "workspace/content-index.json",
      "workspace/manifest.json",
      "workspace/notes/runtime-brief.txt",
      "workspace/templates/handoff-template.md",
      "workspace/templates/offline-task-brief.md",
    ]);
    expect(manifest.files.every((file) => file.assetPath.startsWith("embedded-runtime-pod/staged/"))).toBe(true);
    expect((await stat(path.join(result.assetRoot, "staged", "bridge", "manifest.json"))).isFile()).toBe(true);
    expect(JSON.stringify(manifest)).not.toContain(repoRoot);
    expect(JSON.stringify(manifest)).not.toContain(artifactDir);
    expect(JSON.stringify(manifest)).not.toContain(targetDir);
    expect(layoutText).not.toContain(repoRoot);
    expect(layoutText).not.toContain(artifactDir);
    expect(layoutText).not.toContain(targetDir);
  });
});
