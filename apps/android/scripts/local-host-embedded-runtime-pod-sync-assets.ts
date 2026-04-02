import path from "node:path";
import { fileURLToPath } from "node:url";
import { parseArgs } from "node:util";
import { syncRuntimePodAssets } from "../runtime-pod/sync-assets.ts";

type CliOptions = {
  repoRoot: string;
  sourceDir: string;
  artifactDir: string;
  targetDir: string;
  clean: boolean;
  json: boolean;
};

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const defaultRepoRoot = path.resolve(scriptDir, "../../..");
const defaultSourceDir = path.join(defaultRepoRoot, "apps", "android", "runtime-pod");
const defaultArtifactDir = path.join(defaultRepoRoot, ".tmp", "android-runtime-pod");
const defaultTargetDir = path.join(defaultRepoRoot, ".tmp", "android-runtime-pod-assets");

function usage(): string {
  return [
    "Usage:",
    "  pnpm android:local-host:embedded-runtime-pod:sync-assets",
    "",
    "Options:",
    "  --repo-root <path>",
    "  --source-dir <path>",
    "  --artifact-dir <path>",
    "  --target-dir <path>",
    "  --no-clean",
    "  --json",
  ].join("\n");
}

function trimOptional(value: string | undefined): string | undefined {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

function parseCli(argv: string[]): CliOptions {
  const parsed = parseArgs({
    args: argv.filter((arg) => arg !== "--"),
    options: {
      "repo-root": { type: "string" },
      "source-dir": { type: "string" },
      "artifact-dir": { type: "string" },
      "target-dir": { type: "string" },
      "no-clean": { type: "boolean", default: false },
      json: { type: "boolean", default: false },
      help: { type: "boolean", short: "h" },
    },
    allowPositionals: false,
  });

  if (parsed.values.help) {
    console.log(usage());
    process.exit(0);
  }

  const repoRoot = path.resolve(trimOptional(parsed.values["repo-root"]) ?? defaultRepoRoot);
  return {
    repoRoot,
    sourceDir: path.resolve(trimOptional(parsed.values["source-dir"]) ?? defaultSourceDir),
    artifactDir: path.resolve(trimOptional(parsed.values["artifact-dir"]) ?? defaultArtifactDir),
    targetDir: path.resolve(trimOptional(parsed.values["target-dir"]) ?? defaultTargetDir),
    clean: !parsed.values["no-clean"],
    json: parsed.values.json ?? false,
  };
}

async function main(argv = process.argv.slice(2)): Promise<void> {
  const options = parseCli(argv);
  const result = await syncRuntimePodAssets(options);

  if (options.json) {
    console.log(JSON.stringify(result, null, 2));
    return;
  }

  console.log(`runtimePod.id=${result.podId}`);
  console.log(`runtimePod.version=${result.version}`);
  console.log(`runtimePod.assetRoot=${result.assetRoot}`);
  console.log(`runtimePod.assetManifest=${result.assetManifestPath}`);
  console.log(`runtimePod.assetLayout=${result.assetLayoutPath}`);
  console.log(`runtimePod.assetFileCount=${result.assetFiles.length}`);
}

await main();
