import { mkdtemp, readFile, readdir, stat } from "node:fs/promises";
import path from "node:path";
import { tmpdir } from "node:os";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import { prepareRuntimePod } from "./prepare.ts";

async function listFiles(rootDir: string): Promise<string[]> {
  const entries = await readdir(rootDir, { withFileTypes: true });
  const files: string[] = [];
  for (const entry of entries) {
    const fullPath = path.join(rootDir, entry.name);
    if (entry.isDirectory()) {
      files.push(...(await listFiles(fullPath)));
      continue;
    }
    if (entry.isFile()) {
      files.push(fullPath);
    }
  }
  return files;
}

describe("prepareRuntimePod", () => {
  it("stages the runtime pod assets and writes stable artifacts", async () => {
    const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..", "..");
    const sourceDir = path.join(repoRoot, "apps", "android", "runtime-pod");
    const artifactDir = await mkdtemp(path.join(tmpdir(), "openclaw-runtime-pod-"));

    const result = await prepareRuntimePod({
      repoRoot,
      sourceDir,
      artifactDir,
      clean: true,
    });

    expect(result.podId).toBe("ai.openclaw.android.embedded-runtime-pod");
    expect(result.stages).toHaveLength(6);
    expect(result.files).toHaveLength(26);

    const manifestText = await readFile(result.manifestPath, "utf8");
    const layoutText = await readFile(result.layoutPath, "utf8");
    const manifest = JSON.parse(manifestText) as { fileCount: number; stageCount: number; version: string };
    const layout = JSON.parse(layoutText) as { files: Array<{ relativePath: string }> };

    expect(manifest.version).toBe("0.10.0");
    expect(manifest.stageCount).toBe(6);
    expect(manifest.fileCount).toBe(26);
    expect(layout.files.map((file) => file.relativePath)).toEqual([
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

    const stagedFiles = await listFiles(result.stagedRoot);
    expect(stagedFiles).toHaveLength(26);
    for (const entry of stagedFiles) {
      expect((await stat(entry)).isFile()).toBe(true);
    }
  });
});
