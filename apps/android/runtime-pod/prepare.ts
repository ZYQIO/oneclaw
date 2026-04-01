import { createHash } from "node:crypto";
import { mkdir, readdir, readFile, rm, stat, writeFile } from "node:fs/promises";
import path from "node:path";

export type RuntimePodStage = {
  name: string;
  source: string;
  destination: string;
};

export type RuntimePodSpec = {
  podId: string;
  podName: string;
  version: string;
  description: string;
  stages: RuntimePodStage[];
};

export type RuntimePodFileEntry = {
  stage: string;
  relativePath: string;
  sourcePath: string;
  stagedPath: string;
  sizeBytes: number;
  sha256: string;
};

export type RuntimePodPrepareOptions = {
  repoRoot: string;
  sourceDir: string;
  artifactDir: string;
  clean?: boolean;
};

export type RuntimePodPrepareResult = {
  repoRoot: string;
  specPath: string;
  manifestPath: string;
  layoutPath: string;
  stagedRoot: string;
  sourceDir: string;
  artifactDir: string;
  podId: string;
  podName: string;
  version: string;
  stages: RuntimePodStage[];
  files: RuntimePodFileEntry[];
};

function toPosixRelative(filePath: string, baseDir: string): string {
  return path.relative(baseDir, filePath).split(path.sep).join("/");
}

async function hashFile(filePath: string): Promise<string> {
  const bytes = await readFile(filePath);
  return createHash("sha256").update(bytes).digest("hex");
}

async function walkFiles(rootDir: string): Promise<string[]> {
  const entries = await readdir(rootDir, { withFileTypes: true });
  const files: string[] = [];
  for (const entry of entries) {
    const fullPath = path.join(rootDir, entry.name);
    if (entry.isDirectory()) {
      files.push(...(await walkFiles(fullPath)));
      continue;
    }
    if (entry.isFile()) {
      files.push(fullPath);
    }
  }
  return files;
}

async function copyFileTree(sourceDir: string, destinationDir: string): Promise<void> {
  await mkdir(destinationDir, { recursive: true });
  const sourceFiles = await walkFiles(sourceDir);
  for (const sourcePath of sourceFiles) {
    const relativePath = path.relative(sourceDir, sourcePath);
    const targetPath = path.join(destinationDir, relativePath);
    await mkdir(path.dirname(targetPath), { recursive: true });
    await writeFile(targetPath, await readFile(sourcePath));
  }
}

export async function prepareRuntimePod(
  options: RuntimePodPrepareOptions,
): Promise<RuntimePodPrepareResult> {
  const specPath = path.join(options.sourceDir, "pod-spec.json");
  const specText = await readFile(specPath, "utf8");
  const spec = JSON.parse(specText) as RuntimePodSpec;

  const stagedRoot = path.join(options.artifactDir, "staged");
  const manifestPath = path.join(options.artifactDir, "manifest.json");
  const layoutPath = path.join(options.artifactDir, "layout.json");

  if (options.clean) {
    await rm(options.artifactDir, { recursive: true, force: true });
  }
  await mkdir(options.artifactDir, { recursive: true });
  await mkdir(stagedRoot, { recursive: true });

  const files: RuntimePodFileEntry[] = [];
  for (const stage of spec.stages) {
    const stageSourceDir = path.join(options.sourceDir, stage.source);
    const stageDestinationDir = path.join(stagedRoot, stage.destination);
    await copyFileTree(stageSourceDir, stageDestinationDir);

    const stageFiles = await walkFiles(stageSourceDir);
    for (const sourcePath of stageFiles) {
      const relativePath = toPosixRelative(sourcePath, stageSourceDir);
      const stagedPath = path.join(stageDestinationDir, relativePath);
      const sizeBytes = (await stat(sourcePath)).size;
      files.push({
        stage: stage.name,
        relativePath: `${stage.destination}/${relativePath}`,
        sourcePath,
        stagedPath,
        sizeBytes,
        sha256: await hashFile(sourcePath),
      });
    }
  }

  files.sort((left, right) => left.relativePath.localeCompare(right.relativePath));

  const layout = {
    repoRoot: options.repoRoot,
    sourceDir: options.sourceDir,
    stagedRoot,
    artifactDir: options.artifactDir,
    directories: spec.stages.map((stage) => ({
      name: stage.name,
      source: path.join(options.sourceDir, stage.source),
      destination: path.join(stagedRoot, stage.destination),
    })),
    files: files.map((file) => ({
      stage: file.stage,
      relativePath: file.relativePath,
      stagedPath: file.stagedPath,
      sizeBytes: file.sizeBytes,
      sha256: file.sha256,
    })),
  };

  const manifest = {
    schemaVersion: 1,
    repoRoot: options.repoRoot,
    podId: spec.podId,
    podName: spec.podName,
    version: spec.version,
    description: spec.description,
    sourceDir: options.sourceDir,
    artifactDir: options.artifactDir,
    stagedRoot,
    stageCount: spec.stages.length,
    fileCount: files.length,
    stages: spec.stages,
    files: layout.files,
  };

  await writeFile(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, "utf8");
  await writeFile(layoutPath, `${JSON.stringify(layout, null, 2)}\n`, "utf8");

  return {
    repoRoot: options.repoRoot,
    specPath,
    manifestPath,
    layoutPath,
    stagedRoot,
    sourceDir: options.sourceDir,
    artifactDir: options.artifactDir,
    podId: spec.podId,
    podName: spec.podName,
    version: spec.version,
    stages: spec.stages,
    files,
  };
}
