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
    expect(result.stages).toHaveLength(4);
    expect(result.files).toHaveLength(11);

    const manifestText = await readFile(result.manifestPath, "utf8");
    const layoutText = await readFile(result.layoutPath, "utf8");
    const manifest = JSON.parse(manifestText) as { fileCount: number; stageCount: number };
    const layout = JSON.parse(layoutText) as { files: Array<{ relativePath: string }> };

    expect(manifest.stageCount).toBe(4);
    expect(manifest.fileCount).toBe(11);
    expect(layout.files.map((file) => file.relativePath)).toEqual([
      "bridge/manifest.json",
      "runtime/config/runtime-env.json",
      "runtime/engine/manifest.json",
      "runtime/manifest.json",
      "runtime/tasks/runtime-smoke.json",
      "toolkit/manifest.json",
      "workspace/content-index.json",
      "workspace/manifest.json",
      "workspace/notes/runtime-brief.txt",
      "workspace/templates/handoff-template.md",
      "workspace/templates/offline-task-brief.md",
    ]);

    const stagedFiles = await listFiles(result.stagedRoot);
    expect(stagedFiles).toHaveLength(11);
    for (const entry of stagedFiles) {
      expect((await stat(entry)).isFile()).toBe(true);
    }
  });
});
