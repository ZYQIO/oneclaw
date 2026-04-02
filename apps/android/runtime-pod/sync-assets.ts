import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { prepareRuntimePod, type RuntimePodPrepareOptions, type RuntimePodPrepareResult } from "./prepare.ts";

export type RuntimePodAssetSyncOptions = RuntimePodPrepareOptions & {
  targetDir: string;
};

export type RuntimePodAssetManifestFile = {
  stage: string;
  relativePath: string;
  assetPath: string;
  sizeBytes: number;
  sha256: string;
};

export type RuntimePodAssetSyncResult = RuntimePodPrepareResult & {
  assetRoot: string;
  assetManifestPath: string;
  assetLayoutPath: string;
  assetFiles: RuntimePodAssetManifestFile[];
};

async function copyFile(sourcePath: string, destinationPath: string): Promise<void> {
  await mkdir(path.dirname(destinationPath), { recursive: true });
  await writeFile(destinationPath, await readFile(sourcePath));
}

export async function syncRuntimePodAssets(
  options: RuntimePodAssetSyncOptions,
): Promise<RuntimePodAssetSyncResult> {
  const prepared = await prepareRuntimePod(options);
  const assetRoot = path.join(options.targetDir, "embedded-runtime-pod");
  const assetManifestPath = path.join(assetRoot, "manifest.json");
  const assetLayoutPath = path.join(assetRoot, "layout.json");

  if (options.clean) {
    await rm(assetRoot, { recursive: true, force: true });
  }
  await mkdir(assetRoot, { recursive: true });

  const assetFiles = prepared.files.map((file) => {
    const assetRelativePath = path.posix.join("staged", file.relativePath);
    return {
      stage: file.stage,
      relativePath: file.relativePath,
      assetPath: path.posix.join("embedded-runtime-pod", assetRelativePath),
      sizeBytes: file.sizeBytes,
      sha256: file.sha256,
    };
  });

  for (const file of prepared.files) {
    const assetRelativePath = path.join("staged", file.relativePath);
    await copyFile(file.stagedPath, path.join(assetRoot, assetRelativePath));
  }

  const manifest = {
    schemaVersion: 1,
    podId: prepared.podId,
    podName: prepared.podName,
    version: prepared.version,
    description: "Sanitized Android asset manifest for Embedded Runtime Pod extraction.",
    assetManifestPath: "embedded-runtime-pod/manifest.json",
    assetLayoutPath: "embedded-runtime-pod/layout.json",
    assetBasePath: "embedded-runtime-pod/staged",
    stageCount: prepared.stages.length,
    fileCount: assetFiles.length,
    stages: prepared.stages.map((stage) => ({
      name: stage.name,
      destination: stage.destination,
      assetPath: path.posix.join("embedded-runtime-pod", "staged", stage.destination),
    })),
    files: assetFiles,
  };

  const layout = {
    podId: prepared.podId,
    podName: prepared.podName,
    version: prepared.version,
    assetRoot: "embedded-runtime-pod",
    stagedAssetRoot: "embedded-runtime-pod/staged",
    directories: prepared.stages.map((stage) => ({
      name: stage.name,
      destination: stage.destination,
      assetPath: path.posix.join("embedded-runtime-pod", "staged", stage.destination),
    })),
    files: assetFiles,
  };

  await writeFile(assetManifestPath, `${JSON.stringify(manifest, null, 2)}\n`, "utf8");
  await writeFile(assetLayoutPath, `${JSON.stringify(layout, null, 2)}\n`, "utf8");

  return {
    ...prepared,
    assetRoot,
    assetManifestPath,
    assetLayoutPath,
    assetFiles,
  };
}
