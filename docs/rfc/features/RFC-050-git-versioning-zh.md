# RFC-050: 基于 Git 的文件版本控制

## 文档信息
- **RFC ID**: RFC-050
- **相关 PRD**: [FEAT-050（基于 Git 的文件版本控制）](../../prd/features/FEAT-050-git-versioning.md)
- **依赖**: [RFC-013（记忆系统）](RFC-013-memory-system.md), [RFC-023（记忆增强）](RFC-023-memory-enhancement.md)
- **被依赖**: 无
- **创建时间**: 2026-03-02
- **最后更新**: 2026-03-02
- **状态**: 草稿

## 概述

本 RFC 描述了为所有存储在 `context.filesDir` 中的用户可见文本文件实现基于 git 的版本控制层。使用 JGit（纯 Java git 实现），因此 Android 上无需原生二进制文件。每次写入记忆文件或 AI 生成的 markdown 文件都会触发自动提交。移除 `MemoryFileStorage` 中的旧版轮换备份机制。向工具注册表添加五个 AI 可调用的 git 工具。

## 架构

### 新增组件

```
app/
└── data/
    └── git/
        ├── AppGitRepository.kt       # JGit 封装，核心 API
        └── GitGcWorker.kt            # WorkManager 任务，用于 git gc
feature/
└── memory/
    └── storage/
        └── MemoryFileStorage.kt      # 移除备份方法，添加 git commit 调用
tool/
└── builtin/
    ├── GitLogTool.kt
    ├── GitShowTool.kt
    ├── GitDiffTool.kt
    ├── GitRestoreTool.kt
    └── GitBundleTool.kt
```

### 依赖项添加

在 `app/build.gradle.kts` 中添加 JGit：
```kotlin
implementation("org.eclipse.jgit:org.eclipse.jgit:6.10.0.202406032230-r")
```

添加 ProGuard 规则以保留 JGit 基于反射的内部结构：
```
-keep class org.eclipse.jgit.** { *; }
-dontwarn org.eclipse.jgit.**
```

## AppGitRepository

`AppGitRepository` 是单例（注册在 `databaseModule` 或新的 `gitModule` 中），持有 JGit `Repository` 实例。

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

    /** 初始化或打开 git 仓库。在应用启动时调用一次。 */
    suspend fun initOrOpen(): Unit = withContext(Dispatchers.IO) { ... }

    /** 暂存所有已跟踪文件并创建提交。 */
    suspend fun commit(message: String): Unit = withContext(Dispatchers.IO) { ... }

    /** 暂存特定文件并创建提交。 */
    suspend fun commitFile(relativePath: String, message: String): Unit = withContext(Dispatchers.IO) { ... }

    /** 返回日志条目，可按路径筛选，并限制 maxCount 数量。 */
    suspend fun log(path: String? = null, maxCount: Int = 50): List<GitCommitEntry> = withContext(Dispatchers.IO) { ... }

    /** 返回给定提交 SHA 的完整 diff 文本。 */
    suspend fun show(sha: String): String = withContext(Dispatchers.IO) { ... }

    /** 返回两个提交之间的 diff（若 toSha 为 null，则与工作树比较）。 */
    suspend fun diff(fromSha: String, toSha: String?): String = withContext(Dispatchers.IO) { ... }

    /** 将特定文件恢复到给定提交 SHA 时的状态。 */
    suspend fun restore(relativePath: String, sha: String): Unit = withContext(Dispatchers.IO) { ... }

    /** 将 git bundle 写入给定输出文件。 */
    suspend fun bundle(outputFile: File): Unit = withContext(Dispatchers.IO) { ... }

    /** 运行 git gc。 */
    suspend fun gc(): Unit = withContext(Dispatchers.IO) { ... }
}

data class GitCommitEntry(
    val sha: String,
    val shortSha: String,
    val message: String,
    val authorTime: Long,   // epoch 毫秒
    val changedFiles: List<String>
)
```

### 初始化流程

```
应用启动（Application.onCreate 或 Koin 懒加载初始化）
  └── AppGitRepository.initOrOpen()
        ├── 若 .git/ 不存在：
        │     ├── Git.init().setDirectory(repoDir).call()
        │     ├── 写入 .gitignore
        │     ├── git add -A
        │     └── git commit "init: initialize memory repository"
        └── 否则：
              └── Git.open(repoDir)  // 打开已有仓库
```

提交者身份固定为：
```
name  = "OneClaw Agent"
email = "agent@oneclaw.local"
```

## 集成点

### MemoryFileStorage

移除：
- `createBackup()`
- `pruneOldBackups()`
- `restoreFromBackup()`
- `listBackups()`
- `MAX_BACKUPS`, `BACKUP_PREFIX`, `BACKUP_SUFFIX`, `BACKUP_TIMESTAMP_FORMAT`

修改 `writeMemoryFile(content)`，在写入后调用 `appGitRepository.commitFile("memory/MEMORY.md", "memory: update MEMORY.md")`。

修改 `appendToDailyLog(date, content)`，在追加后调用 `appGitRepository.commitFile("memory/daily/$date.md", "log: add daily log $date")`。

`MemoryFileStorage` 通过构造函数注入接收 `AppGitRepository`。

### LongTermMemoryManager

无需更改——它调用 `memoryFileStorage.writeMemoryFile()`，该方法现在会自动提交。

### write_file 工具（现有 AI 工具）

写入文件后，调用：
```kotlin
appGitRepository.commitFile(relativePath, "file: write $relativePath")
```

### delete_file 工具 / DeleteFileUseCase

删除文件后，调用：
```kotlin
appGitRepository.commitFile(relativePath, "file: delete $relativePath")
```

注意：对于删除操作，`git add` 步骤必须使用 `git rm --cached` 或带路径的 `AddCommand`，以便 git 暂存删除操作。

### FileBrowserViewModel（用户发起的删除）

与 delete_file 工具相同的处理模式。

## AI Git 工具

五个工具均实现 `Tool` 接口并注册在 `ToolModule` 中。

### git_log

```
输入 schema：
  path      （string，可选）-- 筛选涉及该相对路径的提交
  max_count （integer，可选，默认 20）-- 最多返回的提交数

输出：格式化的提交列表
  SHA | 日期 | 消息
  ...
```

### git_show

```
输入 schema：
  sha （string，必填）-- 完整或缩写的提交 SHA

输出：提交元数据 + 统一差异文本
```

### git_diff

```
输入 schema：
  from_sha （string，必填）-- 基础提交 SHA
  to_sha   （string，可选）-- 目标提交 SHA；省略时与工作树比较

输出：统一差异文本
```

### git_restore

```
输入 schema：
  path （string，必填）-- 要恢复的相对文件路径
  sha  （string，必填）-- 用于恢复的提交 SHA

输出：成功消息或错误描述
```

恢复后，自动提交，消息为 `file: restore <path> from <short-sha>`。

### git_bundle

```
输入 schema：
  output_path （string，必填）-- 写入 bundle 文件的相对路径
                                （必须在 filesDir 下，例如 "exports/oneclaw.bundle"）

输出：包含 bundle 大小的成功消息，或错误描述
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

调度（在应用启动时调用一次，替换任何已有的入队操作）：
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

添加新的 `gitModule`：
```kotlin
val gitModule = module {
    single { AppGitRepository(androidContext()) }
    single { GitGcWorker.Factory(get()) }
}
```

在 `startKoin` 中与现有八个模块一同注册 `gitModule`。

将 `AppGitRepository` 注入至：
- `MemoryFileStorage`
- `WriteFileTool`
- `DeleteFileTool`
- `FileBrowserViewModel`（或 `DeleteFileUseCase`）
- 五个 git 工具各自

## 文件浏览器：隐藏 .git

`UserFileStorage.listFiles()` 过滤掉名为 `.git` 和 `.gitignore` 的条目，使其不显示在文件界面中。

## 错误处理

- 若 git 提交失败（例如，无内容可提交），记录警告并继续；不向用户抛出或展示错误。
- 若仓库损坏，记录错误；不自动重新初始化（避免数据丢失）。
- 所有 git 工具的错误以结构化错误文本形式返回给 AI（而非作为异常抛出）。

## 迁移

| 场景 | 处理方式 |
|---|---|
| 全新安装 | `initOrOpen()` 创建新仓库并进行初始提交 |
| 升级（存在 filesDir，无 .git） | `initOrOpen()` 检测到缺少 `.git`，初始化并提交所有现有合规文件 |
| 升级（已有 .git） | `initOrOpen()` 打开已有仓库；无操作 |
| 旧版 MEMORY_backup_*.md 文件 | 保留原位；用户可手动删除；首次初始化时会提交到 git（不匹配任何排除模式）——在 `.gitignore` 中添加 `MEMORY_backup_*.md` |

## 测试

### 单元测试

- `AppGitRepositoryTest`：使用临时目录测试初始化、commit、log、show、diff、restore、bundle
- `GitLogToolTest`、`GitShowToolTest`、`GitDiffToolTest`、`GitRestoreToolTest`、`GitBundleToolTest`：输入解析和输出格式化

### 集成测试（Layer 1B）

- 端到端：写入 MEMORY.md → 验证日志中存在提交
- 端到端：追加每日日志 → 验证日志中存在提交
- 端到端：删除文件 → 验证提交记录了删除操作
- git_restore 工具：恢复文件并验证内容与历史版本一致

## 开放问题

无。所有设计决策已与产品负责人确认。
