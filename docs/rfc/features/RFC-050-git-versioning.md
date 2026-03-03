# RFC-050: Git-based File Versioning

## Document Information
- **RFC ID**: RFC-050
- **Related PRD**: [FEAT-050 (Git-based File Versioning)](../../prd/features/FEAT-050-git-versioning.md)
- **Depends On**: [RFC-013 (Memory System)](RFC-013-memory-system.md), [RFC-023 (Memory Enhancement)](RFC-023-memory-enhancement.md)
- **Depended On By**: None
- **Created**: 2026-03-02
- **Last Updated**: 2026-03-02
- **Status**: Draft

## Overview

This RFC describes the implementation of a git-based versioning layer for all user-facing text files stored in `context.filesDir`. JGit (pure Java git implementation) is used so no native binary is required on Android. Every write to memory files or AI-generated markdown files triggers an automatic commit. The legacy rotating-backup mechanism in `MemoryFileStorage` is removed. Five AI-callable git tools are added to the tool registry.

## Architecture

### New Components

```
app/
└── data/
    └── git/
        ├── AppGitRepository.kt       # JGit wrapper, core API
        └── GitGcWorker.kt            # WorkManager task for git gc
feature/
└── memory/
    └── storage/
        └── MemoryFileStorage.kt      # remove backup methods, add git commit calls
tool/
└── builtin/
    ├── GitLogTool.kt
    ├── GitShowTool.kt
    ├── GitDiffTool.kt
    ├── GitRestoreTool.kt
    └── GitBundleTool.kt
```

### Dependency Addition

Add JGit to `app/build.gradle.kts`:
```kotlin
implementation("org.eclipse.jgit:org.eclipse.jgit:6.10.0.202406032230-r")
```

Add ProGuard rules to keep JGit reflection-based internals:
```
-keep class org.eclipse.jgit.** { *; }
-dontwarn org.eclipse.jgit.**
```

## AppGitRepository

`AppGitRepository` is a singleton (registered in `databaseModule` or a new `gitModule`) that owns the JGit `Repository` instance.

```kotlin
class AppGitRepository(private val context: Context) {

    val repoDir: File get() = context.filesDir

    private val gitignoreContent = """
        attachments/
        bridge_images/
        *.jpg
        *.jpeg
        *.png
        *.gif
        *.webp
        *.bmp
        *.mp4
        *.onnx
        *.pdf
        *.db
        *.db-shm
        *.db-wal
        *.bundle
        *.zip
    """.trimIndent()

    /** Initialize or open the git repository. Called once at app startup. */
    suspend fun initOrOpen(): Unit = withContext(Dispatchers.IO) { ... }

    /** Stage all tracked files and create a commit. */
    suspend fun commit(message: String): Unit = withContext(Dispatchers.IO) { ... }

    /** Stage a specific file and create a commit. */
    suspend fun commitFile(relativePath: String, message: String): Unit = withContext(Dispatchers.IO) { ... }

    /** Return log entries, optionally filtered by path, limited by maxCount. */
    suspend fun log(path: String? = null, maxCount: Int = 50): List<GitCommitEntry> = withContext(Dispatchers.IO) { ... }

    /** Return the full diff text for a given commit SHA. */
    suspend fun show(sha: String): String = withContext(Dispatchers.IO) { ... }

    /** Return the diff between two commits (or commit vs working tree if toSha is null). */
    suspend fun diff(fromSha: String, toSha: String?): String = withContext(Dispatchers.IO) { ... }

    /** Restore a specific file to the state at the given commit SHA. */
    suspend fun restore(relativePath: String, sha: String): Unit = withContext(Dispatchers.IO) { ... }

    /** Write a git bundle to the given output file. */
    suspend fun bundle(outputFile: File): Unit = withContext(Dispatchers.IO) { ... }

    /** Run git gc. */
    suspend fun gc(): Unit = withContext(Dispatchers.IO) { ... }
}

data class GitCommitEntry(
    val sha: String,
    val shortSha: String,
    val message: String,
    val authorTime: Long,   // epoch millis
    val changedFiles: List<String>
)
```

### Initialization Flow

```
App startup (Application.onCreate or Koin lazy init)
  └── AppGitRepository.initOrOpen()
        ├── if .git/ does not exist:
        │     ├── Git.init().setDirectory(repoDir).call()
        │     ├── write .gitignore
        │     ├── git add -A
        │     └── git commit "init: initialize memory repository"
        └── else:
              └── Git.open(repoDir)  // open existing repo
```

The committer identity is fixed:
```
name  = "OneClaw Agent"
email = "agent@oneclaw.local"
```

## Integration Points

### MemoryFileStorage

Remove:
- `createBackup()`
- `pruneOldBackups()`
- `restoreFromBackup()`
- `listBackups()`
- `MAX_BACKUPS`, `BACKUP_PREFIX`, `BACKUP_SUFFIX`, `BACKUP_TIMESTAMP_FORMAT`

Modify `writeMemoryFile(content)` to call `appGitRepository.commitFile("memory/MEMORY.md", "memory: update MEMORY.md")` after writing.

Modify `appendToDailyLog(date, content)` to call `appGitRepository.commitFile("memory/daily/$date.md", "log: add daily log $date")` after appending.

`MemoryFileStorage` receives `AppGitRepository` via constructor injection.

### LongTermMemoryManager

No changes needed — it calls `memoryFileStorage.writeMemoryFile()`, which now auto-commits.

### write_file Tool (existing AI tool)

After writing the file, call:
```kotlin
appGitRepository.commitFile(relativePath, "file: write $relativePath")
```

### delete_file Tool / DeleteFileUseCase

After deleting the file, call:
```kotlin
appGitRepository.commitFile(relativePath, "file: delete $relativePath")
```

Note: for deletions, the `git add` step must use `git rm --cached` or `AddCommand` with the path so git stages the removal.

### FileBrowserViewModel (user-initiated delete)

Same pattern as delete_file tool.

## AI Git Tools

All five tools implement the `Tool` interface and are registered in `ToolModule`.

### git_log

```
Input schema:
  path      (string, optional)  -- filter to commits touching this relative path
  max_count (integer, optional, default 20) -- max commits to return

Output: formatted list of commits
  SHA | date | message
  ...
```

### git_show

```
Input schema:
  sha (string, required) -- full or abbreviated commit SHA

Output: commit metadata + unified diff text
```

### git_diff

```
Input schema:
  from_sha (string, required) -- base commit SHA
  to_sha   (string, optional) -- target commit SHA; omit to diff against working tree

Output: unified diff text
```

### git_restore

```
Input schema:
  path (string, required) -- relative file path to restore
  sha  (string, required) -- commit SHA to restore from

Output: success message or error description
```

After restoring, automatically commits with message `file: restore <path> from <short-sha>`.

### git_bundle

```
Input schema:
  output_path (string, required) -- relative path where bundle file should be written
                                    (must be under filesDir, e.g. "exports/oneclaw.bundle")

Output: success message with bundle size, or error description
```

## GitGcWorker

```kotlin
class GitGcWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        appGitRepository.gc()
        appGitRepository.commit("gc: repository maintenance")
        return Result.success()
    }
}
```

Scheduling (called once at app startup, replaces any existing enqueue):
```kotlin
val gcRequest = PeriodicWorkRequestBuilder<GitGcWorker>(30, TimeUnit.DAYS)
    .setConstraints(
        Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
    )
    .build()

WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "git_gc",
    ExistingPeriodicWorkPolicy.KEEP,
    gcRequest
)
```

## Koin DI

Add a new `gitModule`:
```kotlin
val gitModule = module {
    single { AppGitRepository(androidContext()) }
    single { GitGcWorker.Factory(get()) }
}
```

Register `gitModule` in `startKoin` alongside the existing eight modules.

Inject `AppGitRepository` into:
- `MemoryFileStorage`
- `WriteFileTool`
- `DeleteFileTool`
- `FileBrowserViewModel` (or `DeleteFileUseCase`)
- Each of the five git tools

## File Browser: Hiding .git

`UserFileStorage.listFiles()` filters out entries named `.git` and `.gitignore` so they do not appear in the Files UI.

## Error Handling

- If a git commit fails (e.g., nothing to commit), log a warning and continue; do not throw or surface the error to the user.
- If the repo is corrupt, log the error; re-initialization is not automatic (avoid data loss).
- All git tool errors are returned as structured error text to the AI (not thrown as exceptions).

## Migration

| Scenario | Handling |
|---|---|
| Fresh install | `initOrOpen()` creates new repo with initial commit |
| Upgrade (has filesDir, no .git) | `initOrOpen()` detects missing `.git`, inits, and commits all existing eligible files |
| Upgrade (already has .git) | `initOrOpen()` opens existing repo; no-op |
| Legacy MEMORY_backup_*.md files | Left in place; user can delete manually; they will be committed to git on first init (they match no exclude pattern) — add `MEMORY_backup_*.md` to `.gitignore` |

## Testing

### Unit Tests

- `AppGitRepositoryTest`: init, commit, log, show, diff, restore, bundle using a temp directory
- `GitLogToolTest`, `GitShowToolTest`, `GitDiffToolTest`, `GitRestoreToolTest`, `GitBundleToolTest`: input parsing and output formatting

### Integration Tests (Layer 1B)

- End-to-end: write MEMORY.md → verify commit exists in log
- End-to-end: append daily log → verify commit exists in log
- End-to-end: delete file → verify commit records removal
- git_restore tool: restore a file and verify content matches historical version

## Open Questions

None. All design decisions confirmed with product owner.
