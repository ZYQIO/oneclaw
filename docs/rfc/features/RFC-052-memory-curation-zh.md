# RFC-052: 记忆策展系统

## 文档信息
- **RFC ID**: RFC-052
- **相关 PRD**: [FEAT-052 (记忆策展系统)](../../prd/features/FEAT-052-memory-curation-zh.md)
- **扩展**: [RFC-049 (记忆质量改进)](RFC-049-memory-quality-zh.md), [RFC-013 (记忆系统)](RFC-013-memory-system-zh.md)
- **依赖于**: [RFC-049 (记忆质量)](RFC-049-memory-quality-zh.md), [RFC-013 (记忆系统)](RFC-013-memory-system-zh.md)
- **创建日期**: 2026-03-04
- **最后更新**: 2026-03-04
- **状态**: 草稿
- **作者**: TBD

## 概述

### 背景

当前记忆系统有两条写入路径直接追加到 MEMORY.md，导致质量随时间退化：

1. **DailyLogWriter** (`DailyLogWriter.kt:149-151`): 在总结会话后，调用 `longTermMemoryManager.appendMemory(longTermFacts)` 将提取的"长期事实"推送到 MEMORY.md。这是一种无质量过滤、无 section 路由、无去重的盲目追加（`SaveMemoryTool` 中的基本子串检查除外）。

2. **SaveMemoryTool** (`SaveMemoryTool.kt:101-105`): AI 可以通过 `save_memory` 工具直接保存内容，带有分类路由。虽然这条路径有提示词防护（FEAT-049），但 AI 偶尔仍会保存临时或低价值信息。

在 Pixel 设备上的实地观察（2026年3月4日）显示 MEMORY.md 存在以下问题：
- 文件底部有一条孤儿条目 `*   User's name is Ben.`（在所有 section 之外，使用 `*` 而非 `-` 格式），与 User Profile 中的条目重复
- 过期的时间性条目："Candidate interview scheduled Tuesday, March 2, 2026 at 8:30 AM" 在3月4日仍然存在
- Workflow section 膨胀了 Gmail 清理操作细节（具体邮箱地址、标签 ID、技能版本号）
- 已过期的绩效评估截止日期

根本原因：DailyLogWriter 在无质量审查的情况下推送事实，且没有定期策展流程来清理过时条目。

### 目标

1. **将 DailyLogWriter 与 MEMORY.md 解耦** -- 每日日志是短期情景存储；不应直接写入 MEMORY.md
2. **引入 MemoryCurator** -- 每日后台任务（WorkManager），审查最近的日志并对 MEMORY.md 进行审慎、高质量的更新
3. **重命名 Workflow -> Habits/Routines** -- 明确 section 语义，防止操作细节积累
4. **加强压缩和保存提示词** -- 给 LLM 更具体的规则
5. **用户可配置的策展时间** -- Memory 界面中的时间选择器，默认凌晨 3:00

### 非目标
- 语义去重（基于 embedding）-- MEMORY.md 保持小体积，不值得增加复杂性
- 个别条目的 TTL/过期标记 -- 有误删重要永久记忆的风险
- 每次 save_memory 调用的 LLM 质量门控 -- 太贵且增加每次调用的延迟
- MemoryInjector、HybridSearchEngine 或 UpdateMemoryTool 的变更

## 技术设计

### 架构概览

```
变更前（当前）：
  会话结束 -> DailyLogWriter -> 写入每日日志 + 追加 MEMORY.md
  save_memory 工具 -> 追加 MEMORY.md（带分类）
  日期变更 -> MemoryCompactor -> 压缩 MEMORY.md（如果 > 3000 字符）

变更后（本 RFC）：
  会话结束 -> DailyLogWriter -> 只写入每日日志（不碰 MEMORY.md）
  save_memory 工具 -> 追加 MEMORY.md（带分类，增强提示词）
  WorkManager 凌晨 3:00 -> MemoryCurationWorker -> MemoryCurator:
      1. 合并整理昨天碎片化的每日日志为一份摘要
      2. 读取最近 3 天的每日日志 + 当前 MEMORY.md
      3. LLM 策展审查 -> 更新 MEMORY.md（或 NO_CHANGES）
      4. MemoryCompactor.compactIfNeeded()
```

```
新组件图：

┌──────────────────────────────────────────────────────────┐
│  WorkManager (AndroidX)                                  │
│  └── MemoryCurationWorker (CoroutineWorker)              │
│      └── MemoryCurator.curate()                          │
│          ├── consolidateYesterdayLog()                    │
│          │   ├── MemoryFileStorage.readDailyLog()        │
│          │   ├── LLM API 调用（合并提示词）              │
│          │   └── MemoryFileStorage.writeDailyLog()       │
│          ├── MemoryFileStorage.readDailyLog() x N 天     │
│          ├── LongTermMemoryManager.readMemory()          │
│          ├── LLM API 调用（策展提示词）                   │
│          ├── LongTermMemoryManager.writeMemory()         │
│          └── MemoryCompactor.compactIfNeeded()           │
│                                                          │
│  CurationSchedulePreferences (SharedPreferences)         │
│  └── hour: Int = 3, minute: Int = 0                      │
│                                                          │
│  CurationScheduler                                       │
│  └── scheduleCuration(hour, minute)                      │
│  └── cancelCuration()                                    │
└──────────────────────────────────────────────────────────┘

修改的组件：

┌──────────────────────────────────────────────────────────┐
│  DailyLogWriter（修改）                                   │
│  └── writeDailyLog() -- 不再写入 MEMORY.md               │
│  └── 摘要提示词 -- 无 "Long-term Facts" section          │
│                                                          │
│  MemorySections（修改）                                   │
│  └── "Workflow" -> "Habits/Routines"                     │
│                                                          │
│  MemoryCompactor（修改）                                  │
│  └── buildCompactionPrompt() -- 增强规则                 │
│                                                          │
│  SaveMemoryTool（修改）                                   │
│  └── definition.description -- 增强"不要保存"列表        │
│  └── category 映射 -- 添加 "habits"/"routines"           │
│                                                          │
│  MemoryManager（修改）                                    │
│  └── curate() -- 新方法，委托给 MemoryCurator            │
│                                                          │
│  MemoryModule（修改）                                     │
│  └── 注册 MemoryCurator, CurationScheduler               │
│                                                          │
│  MemoryScreen（修改）                                     │
│  └── 策展时间显示 + 时间选择器                            │
└──────────────────────────────────────────────────────────┘
```

---

### 变更 1: DailyLogWriter 解耦

**文件**: `feature/memory/log/DailyLogWriter.kt`

移除 MEMORY.md 写入路径并简化摘要提示词。

**当前流程**（第 139-152 行）：
```kotlin
val (dailySummary, longTermFacts) = parseSummarizationResponse(responseText)

if (dailySummary.isNotBlank()) {
    memoryFileStorage.appendToDailyLog(today, dailySummary)
    indexChunks(dailySummary, "daily_log", today)
}

if (longTermFacts.isNotBlank()) {
    longTermMemoryManager.appendMemory(longTermFacts)  // <-- 这是问题所在
    indexChunks(longTermFacts, "long_term", null)
}
```

**新流程**：
```kotlin
// 将每日摘要写入每日日志（仅此而已）
if (dailySummary.isNotBlank()) {
    memoryFileStorage.appendToDailyLog(today, dailySummary)
    indexChunks(dailySummary, "daily_log", today)
}

// 长期事实不再在此处推送。
// MemoryCurator 将定期审查每日日志并
// 将高质量事实提升到 MEMORY.md。
```

**简化的摘要提示词**（移除 "Long-term Facts" section）：

```kotlin
private fun buildSummarizationPrompt(conversationText: String): String {
    return """
        |Summarize the following conversation concisely. Provide:
        |
        |## Daily Summary
        |A concise summary of key topics discussed, decisions made, tasks completed,
        |and any notable information. Use bullet points.
        |
        |---
        |Conversation:
        |$conversationText
    """.trimMargin()
}
```

**简化的响应解析器**（不再需要提取长期事实）：

```kotlin
internal fun parseSummarizationResponse(response: String): String {
    val dailySummaryRegex = Regex(
        "## Daily Summary\\s*\\n([\\s\\S]*?)$",
        RegexOption.IGNORE_CASE
    )
    return dailySummaryRegex.find(response)?.groupValues?.get(1)?.trim() ?: response.trim()
}
```

**构造函数变更**：从 `DailyLogWriter` 中移除 `longTermMemoryManager` 依赖。

```kotlin
class DailyLogWriter(
    private val messageRepository: MessageRepository,
    private val sessionRepository: SessionRepository,
    private val agentRepository: AgentRepository,
    private val providerRepository: ProviderRepository,
    private val apiKeyStorage: ApiKeyStorage,
    private val adapterFactory: ModelApiAdapterFactory,
    private val memoryFileStorage: MemoryFileStorage,
    // 已移除: private val longTermMemoryManager: LongTermMemoryManager,
    private val memoryIndexDao: MemoryIndexDao,
    private val embeddingEngine: EmbeddingEngine
)
```

**DI 更新**（`MemoryModule.kt`）：
```kotlin
single {
    DailyLogWriter(
        messageRepository = get(),
        sessionRepository = get(),
        agentRepository = get(),
        providerRepository = get(),
        apiKeyStorage = get(),
        adapterFactory = get(),
        memoryFileStorage = get(),
        // 已移除: longTermMemoryManager = get(),
        memoryIndexDao = get(),
        embeddingEngine = get()
    )
}
```

---

### 变更 2: MemoryCurator（新组件）

**新文件**: `feature/memory/curator/MemoryCurator.kt`

```kotlin
package com.oneclaw.shadow.feature.memory.curator

import android.util.Log
import com.oneclaw.shadow.core.repository.ProviderRepository
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.data.remote.adapter.ModelApiAdapterFactory
import com.oneclaw.shadow.data.security.ApiKeyStorage
import com.oneclaw.shadow.feature.memory.compaction.MemoryCompactor
import com.oneclaw.shadow.feature.memory.longterm.LongTermMemoryManager
import com.oneclaw.shadow.feature.memory.storage.MemoryFileStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 定期记忆策展器，审查最近的每日日志并对 MEMORY.md 进行
 * 审慎、高质量的更新。
 *
 * 每天通过 WorkManager (MemoryCurationWorker) 运行一次。
 *
 * 流程：
 * 1. 读取最近 N 天的每日日志
 * 2. 读取当前 MEMORY.md
 * 3. 将两者发送给 LLM（策展提示词）
 * 4. 如果 LLM 返回变更，覆盖 MEMORY.md
 * 5. 如需运行压缩
 */
class MemoryCurator(
    private val memoryFileStorage: MemoryFileStorage,
    private val longTermMemoryManager: LongTermMemoryManager,
    private val memoryCompactor: MemoryCompactor,
    private val providerRepository: ProviderRepository,
    private val apiKeyStorage: ApiKeyStorage,
    private val adapterFactory: ModelApiAdapterFactory
) {
    companion object {
        private const val TAG = "MemoryCurator"
        const val LOOKBACK_DAYS = 3
        const val NO_CHANGES_SENTINEL = "NO_CHANGES"
        private const val MAX_TOKENS = 2_000
    }

    /**
     * 运行策展审查。
     * 如果 MEMORY.md 被更新返回 true，否则返回 false。
     */
    suspend fun curate(): Boolean = withContext(Dispatchers.IO) {
        try {
            val today = LocalDate.now()

            // 0. 合并整理昨天碎片化的每日日志（如有需要）
            consolidateYesterdayLog(today)

            // 1. 收集最近的每日日志（现在使用合并后的版本）
            val recentLogs = buildString {
                for (i in 0 until LOOKBACK_DAYS) {
                    val date = today.minusDays(i.toLong()).toString()
                    val log = memoryFileStorage.readDailyLog(date) ?: continue
                    if (log.isNotBlank()) {
                        appendLine("--- $date ---")
                        appendLine(log.trim())
                        appendLine()
                    }
                }
            }.trim()

            if (recentLogs.isBlank()) {
                Log.d(TAG, "No recent daily logs found, skipping curation")
                return@withContext false
            }

            // 2. 读取当前 MEMORY.md
            val currentMemory = longTermMemoryManager.readMemory()

            // 3. 构建并发送策展提示词
            val prompt = buildCurationPrompt(currentMemory, recentLogs, today.toString())
            val response = callLlm(prompt) ?: return@withContext false

            // 4. 检查 NO_CHANGES 哨兵值
            if (response.trim().startsWith(NO_CHANGES_SENTINEL, ignoreCase = true)) {
                Log.d(TAG, "Curation determined no changes needed")
                return@withContext false
            }

            // 5. 验证响应
            if (response.isBlank() || response.length < 50) {
                Log.w(TAG, "Curation returned suspiciously short response, skipping")
                return@withContext false
            }
            if (!response.trimStart().startsWith("# Long-term Memory")) {
                Log.w(TAG, "Curation response doesn't start with expected header, skipping")
                return@withContext false
            }

            // 6. 用策展内容覆盖 MEMORY.md
            longTermMemoryManager.writeMemory(response.trim())
            Log.d(TAG, "Memory curation completed, MEMORY.md updated")

            // 7. 如需运行压缩（以防策展使文件变大）
            try {
                memoryCompactor.compactIfNeeded()
            } catch (_: Exception) {
                // 非致命
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Memory curation failed", e)
            false
        }
    }

    private suspend fun callLlm(prompt: String): String? {
        return try {
            val defaultModel = providerRepository.getGlobalDefaultModel().first()
                ?: return null
            val provider = providerRepository.getProviderById(defaultModel.providerId)
                ?: return null
            if (!provider.isActive) return null
            val apiKey = apiKeyStorage.getApiKey(provider.id) ?: return null
            val adapter = adapterFactory.getAdapter(provider.type)
            when (val result = adapter.generateSimpleCompletion(
                apiBaseUrl = provider.apiBaseUrl,
                apiKey = apiKey,
                modelId = defaultModel.id,
                prompt = prompt,
                maxTokens = MAX_TOKENS
            )) {
                is AppResult.Success -> result.data
                is AppResult.Error -> {
                    Log.w(TAG, "Curation LLM call failed: ${result.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Curation LLM call exception", e)
            null
        }
    }

    /**
     * 如果昨天的每日日志有多个摘要块，合并整理为一份。
     * DailyLogWriter 每次会话结束或 app 进后台时都会追加新块，
     * 导致碎片化。
     */
    private suspend fun consolidateYesterdayLog(today: LocalDate) {
        val yesterday = today.minusDays(1).toString()
        val log = memoryFileStorage.readDailyLog(yesterday) ?: return

        // 检查是否有多个块（用 --- 分隔）
        val blocks = log.split(Regex("\n---\n")).filter { it.isNotBlank() }
        if (blocks.size <= 1) {
            Log.d(TAG, "Yesterday's log has ${blocks.size} block(s), no consolidation needed")
            return
        }

        Log.d(TAG, "Consolidating yesterday's log ($yesterday): ${blocks.size} blocks")

        val prompt = buildConsolidationPrompt(yesterday, log)
        val response = callLlm(prompt) ?: return

        // 验证响应
        if (response.isBlank() || response.length < 30) {
            Log.w(TAG, "Consolidation returned suspiciously short response, skipping")
            return
        }

        // 用合并版本覆盖昨天的每日日志
        // MemoryFileStorage.writeDailyLog 会自动提交到 git
        memoryFileStorage.writeDailyLog(yesterday, response.trim())
        Log.d(TAG, "Yesterday's daily log consolidated: ${log.length} -> ${response.length} chars")
    }

    internal fun buildConsolidationPrompt(date: String, rawLog: String): String = """
You are a daily log consolidation assistant. A user's daily conversation log for $date contains multiple fragmented summary blocks (each written when a session ended). Merge them into a single coherent daily summary.

## Raw Daily Log
```
$rawLog
```

## Instructions
1. Merge all summary blocks into ONE coherent daily summary
2. Remove duplication -- if the same topic appears in multiple blocks, combine into one entry
3. Preserve all distinct facts, decisions, and events from the day
4. Use bullet points (- prefix)
5. Group related items together
6. Keep the header "# Daily Log - $date"
7. Do NOT add information that wasn't in the original blocks
8. Be concise but complete

## Output
Return ONLY the consolidated daily log. Start with "# Daily Log - $date".
""".trimIndent()

    internal fun buildCurationPrompt(
        currentMemory: String,
        recentDailyLogs: String,
        todayDate: String
    ): String = """
You are a memory curation assistant. Your job is to review a user's recent conversation summaries and decide whether the long-term memory file needs updating.

## Current Long-term Memory (MEMORY.md)
```
$currentMemory
```

## Recent Daily Logs (last $LOOKBACK_DAYS days)
```
$recentDailyLogs
```

## Today's Date
$todayDate

## Instructions

Compare the daily logs against the current MEMORY.md and decide what changes (if any) are needed.

### ADD new entries ONLY when:
- A fact or preference appears consistently across 2+ different days of logs (confirmed pattern)
- The user explicitly asked the AI to remember something (look for phrases like "remember that", "save this")
- Important stable personal context not yet in memory (profession, family, key accounts)

### UPDATE existing entries when:
- Recent logs show that an existing fact has changed (e.g., preference updated, project status changed)
- An entry can be made more concise or accurate based on new information

### REMOVE entries when:
- They contain dates that have already passed (today is $todayDate): expired deadlines, completed interviews, past events
- They are transient/operational: specific email addresses, filter rules, label IDs, skill version numbers, API endpoints
- They duplicate information already present elsewhere in the file

### SECTION RULES:
- Use these standard sections: User Profile, Preferences, Interests, Habits/Routines, Projects, Notes
- **Habits/Routines**: Store ONLY behavioral patterns (e.g., "Runs a daily Gmail cleanup at 11 PM"). Do NOT store operational configuration (email addresses, label IDs, version numbers).
- Each section: max 10 bullet-point entries
- Remove empty sections
- Use "- " prefix for all entries
- Keep "# Long-term Memory" as the header

### IMPORTANT:
- If NO changes are needed, respond with exactly: NO_CHANGES
- If changes are needed, return the COMPLETE updated MEMORY.md content
- Do NOT add one-time events from daily logs (e.g., "user cleaned Gmail today" is NOT worth remembering)
- Do NOT add information that is only useful for a few days
- Start your response with either "NO_CHANGES" or "# Long-term Memory"
""".trimIndent()
}
```

**MemoryFileStorage 中需要的新方法**（用于合并整理 -- 覆盖而非追加）：

```kotlin
/**
 * 用新内容覆盖每日日志文件并自动提交到 git。
 * 由 MemoryCurator 在每日日志合并整理时使用。
 */
fun writeDailyLog(date: String, content: String) {
    val file = File(dailyLogDir, "$date.md")
    file.writeText(content)
    CoroutineScope(Dispatchers.IO).launch {
        try {
            appGitRepository.commitFile(
                "memory/daily/$date.md",
                "log: consolidate daily log $date"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Git commit after writeDailyLog failed: ${e.message}")
        }
    }
}
```

这是现有 `appendToDailyLog()` 方法的补充。合并整理流程为：
1. `readDailyLog(yesterday)` -- 读取碎片化版本
2. LLM 合并为单份摘要
3. `writeDailyLog(yesterday, consolidated)` -- 用干净版本覆盖
4. Git 自动提交在历史中保留碎片化和合并后的两个版本

---

### 变更 3: MemoryCurationWorker (WorkManager)

**新文件**: `feature/memory/curator/MemoryCurationWorker.kt`

```kotlin
package com.oneclaw.shadow.feature.memory.curator

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.oneclaw.shadow.feature.memory.MemoryManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * WorkManager worker，运行每日记忆策展。
 * 使用 KoinComponent 进行依赖注入，因为 WorkManager worker
 * 无法使用 Koin 的构造函数注入。
 */
class MemoryCurationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val memoryCurator: MemoryCurator by inject()

    companion object {
        const val WORK_NAME = "memory_curation_daily"
        private const val TAG = "MemoryCurationWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting daily memory curation")
        return try {
            val updated = memoryCurator.curate()
            Log.d(TAG, "Memory curation completed. Updated: $updated")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Memory curation failed", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Log.w(TAG, "Memory curation failed after $runAttemptCount attempts, giving up")
                Result.failure()
            }
        }
    }
}
```

---

### 变更 4: CurationScheduler

**新文件**: `feature/memory/curator/CurationScheduler.kt`

```kotlin
package com.oneclaw.shadow.feature.memory.curator

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * 管理每日记忆策展的 WorkManager 调度。
 * 将用户首选策展时间存储在 SharedPreferences 中。
 */
class CurationScheduler(
    private val context: Context
) {
    companion object {
        private const val TAG = "CurationScheduler"
        private const val PREFS_NAME = "memory_curation_prefs"
        private const val KEY_HOUR = "curation_hour"
        private const val KEY_MINUTE = "curation_minute"
        const val DEFAULT_HOUR = 3
        const val DEFAULT_MINUTE = 0
    }

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 获取当前配置的策展时间。
     */
    fun getCurationTime(): Pair<Int, Int> {
        val hour = prefs.getInt(KEY_HOUR, DEFAULT_HOUR)
        val minute = prefs.getInt(KEY_MINUTE, DEFAULT_MINUTE)
        return hour to minute
    }

    /**
     * 设置策展时间并重新调度 WorkManager 任务。
     */
    fun setCurationTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_HOUR, hour)
            .putInt(KEY_MINUTE, minute)
            .apply()
        scheduleCuration(hour, minute)
    }

    /**
     * 调度（或重新调度）每日策展任务。
     * 应在 app 启动时和用户更改时间时调用。
     */
    fun scheduleCuration(
        hour: Int = prefs.getInt(KEY_HOUR, DEFAULT_HOUR),
        minute: Int = prefs.getInt(KEY_MINUTE, DEFAULT_MINUTE)
    ) {
        val initialDelay = calculateInitialDelay(hour, minute)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<MemoryCurationWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            MemoryCurationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )

        Log.d(TAG, "Curation scheduled at %02d:%02d (initial delay: ${initialDelay / 1000 / 60} min)".format(hour, minute))
    }

    /**
     * 取消策展调度。
     */
    fun cancelCuration() {
        WorkManager.getInstance(context).cancelUniqueWork(MemoryCurationWorker.WORK_NAME)
    }

    /**
     * 计算从现在到下一次目标时间的延迟（毫秒）。
     */
    internal fun calculateInitialDelay(hour: Int, minute: Int): Long {
        val now = LocalDateTime.now()
        val targetToday = now.toLocalDate().atTime(LocalTime.of(hour, minute))
        val target = if (now.isBefore(targetToday)) {
            targetToday
        } else {
            targetToday.plusDays(1)
        }
        return Duration.between(now, target).toMillis()
    }
}
```

---

### 变更 5: 重命名 Workflow -> Habits/Routines

**文件**: `feature/memory/longterm/LongTermMemoryManager.kt`

```kotlin
object MemorySections {
    const val HEADER = "# Long-term Memory"
    val STANDARD_SECTIONS = listOf(
        "User Profile",
        "Preferences",
        "Interests",
        "Habits/Routines",  // 从 "Workflow" 改为
        "Projects",
        "Notes"
    )
}
```

**文件**: `tool/builtin/SaveMemoryTool.kt`（category 映射更新）

```kotlin
val sectionName = if (category != null) {
    when (category) {
        "profile" -> "User Profile"
        "preferences" -> "Preferences"
        "interests" -> "Interests"
        "habits", "routines", "workflow" -> "Habits/Routines"  // 接受三种写法
        "projects" -> "Projects"
        else -> "Notes"
    }
} else null
```

同时更新 `category` 参数描述：
```kotlin
"category" to ToolParameter(
    type = "string",
    description = "The memory section to place this entry in. " +
        "One of: profile, preferences, interests, habits, projects, notes. " +
        "Defaults to notes if not specified."
)
```

**迁移**：策展提示词和压缩提示词都使用 "Habits/Routines" 作为 section 名称。处理仍然包含 "## Workflow" 的 MEMORY.md 时，它们会在下次策展或压缩时自然重命名。无需显式迁移代码。

---

### 变更 6: 增强压缩提示词

**文件**: `feature/memory/compaction/MemoryCompactor.kt`

替换 `buildCompactionPrompt()`：

```kotlin
private fun buildCompactionPrompt(content: String): String = """
You are a memory compaction assistant. Your job is to clean up and reorganize a user's long-term memory file.

## Input
The following is the current content of MEMORY.md:

```
$content
```

## Today's Date
${java.time.LocalDate.now()}

## Instructions
1. MERGE duplicate entries -- if the same fact appears multiple times, keep only the most recent/accurate version
2. REMOVE contradictions -- if two entries conflict, keep only the latest one
3. REMOVE transient information -- model preferences, temporary settings, one-time observations
4. REMOVE expired temporal entries -- any entry containing a date that has already passed (deadlines, appointments, interviews, events)
5. PRESERVE entries that the user explicitly asked to remember
6. ORGANIZE into these standard sections:
   - ## User Profile (name, profession, location, family, accounts)
   - ## Preferences (stable preferences for tools, UI, interaction style)
   - ## Interests (hobbies, topics of interest)
   - ## Habits/Routines (recurring behavioral patterns ONLY -- NOT operational config like email addresses, label IDs, or version numbers)
   - ## Projects (ongoing projects, tech stack details)
   - ## Notes (anything that doesn't fit above)
7. Write concise bullet points (- prefix) under each section
8. Maximum 10 entries per section
9. Remove empty sections entirely
10. Keep the header "# Long-term Memory" at the top

## Output
Return ONLY the compacted MEMORY.md content. No explanation, no commentary. Start with "# Long-term Memory".
""".trimIndent()
```

与当前提示词相比的关键新增：
- 注入今天日期，使 LLM 能识别过期条目
- 明确的删除过期时间条目规则（规则 4）
- 每个 section 最多 10 条（规则 8）
- Habits/Routines section 明确排除操作配置
- 从 "Workflow" 重命名为 "Habits/Routines"

---

### 变更 7: 增强 SaveMemoryTool 提示词

**文件**: `tool/builtin/SaveMemoryTool.kt`

在 `definition.description` 的"不要保存"列表中添加：

```kotlin
description = """Save information to persistent long-term memory (MEMORY.md). This content is injected into the system prompt of ALL future conversations, so be highly selective.

SAVE when:
- User explicitly asks you to remember something
- You identify a STABLE user preference confirmed across 2+ conversations (not a one-time setting change)
- Important personal context: profession, domain expertise, key ongoing projects
- Recurring workflow patterns you have observed multiple times

DO NOT save:
- Transient state: model selection, temporary config, session-level settings
- One-time observations: screenshot contents, visual environment details, room decor
- Frequently changing info: "currently working on X", "today's task is Y"
- Information already present in the system prompt memory section (check before saving!)
- Inferred traits from a single interaction
- Information already captured in daily conversation logs (episodic memory is handled separately)
- Operational configuration: specific email addresses, filter rules, label IDs, API endpoints, version numbers
- Scheduled events with specific dates (these expire and become noise; use calendar tools instead)

Before saving, verify:
1. Will this still be relevant 30 days from now?
2. Is this already in the memory section of the system prompt? If yes, do not duplicate.
3. Is this a confirmed pattern (2+ occurrences) or a one-time event?

Write concise, factual entries. Prefer updating existing facts over adding new entries that contradict old ones."""
```

新增（"不要保存"列表的最后 3 项）：
- 每日日志级别信息 -- 已可通过 HybridSearchEngine 搜索
- 操作配置细节
- 带具体日期的计划事件

---

### 变更 8: 接入 MemoryManager

**文件**: `feature/memory/MemoryManager.kt`

添加策展新方法：

```kotlin
/**
 * 运行记忆策展（MemoryCurator）。
 * 由 MemoryCurationWorker 在每日计划中调用。
 * 如果 MEMORY.md 被更新返回 true。
 */
suspend fun curateMemory(): Boolean {
    val curated = memoryCurator?.curate() ?: return false
    if (curated) {
        try { rebuildIndex() } catch (_: Exception) {}
    }
    return curated
}
```

向构造函数添加 `memoryCurator`：

```kotlin
class MemoryManager(
    private val dailyLogWriter: DailyLogWriter,
    private val longTermMemoryManager: LongTermMemoryManager,
    private val hybridSearchEngine: HybridSearchEngine,
    private val memoryInjector: MemoryInjector,
    private val memoryIndexDao: MemoryIndexDao,
    private val memoryFileStorage: MemoryFileStorage,
    private val embeddingEngine: EmbeddingEngine,
    private val memoryCompactor: MemoryCompactor? = null,
    private val memoryCurator: MemoryCurator? = null  // 新增
)
```

---

### 变更 9: DI 注册

**文件**: `di/MemoryModule.kt`

```kotlin
val memoryModule = module {
    // ... 现有注册 ...

    // RFC-052: 记忆策展器
    single {
        MemoryCurator(
            memoryFileStorage = get(),
            longTermMemoryManager = get(),
            memoryCompactor = get(),
            providerRepository = get(),
            apiKeyStorage = get(),
            adapterFactory = get()
        )
    }

    // RFC-052: 策展调度器
    single { CurationScheduler(androidContext()) }

    // 更新 MemoryManager 以包含策展器
    single {
        MemoryManager(
            dailyLogWriter = get(),
            longTermMemoryManager = get(),
            hybridSearchEngine = get(),
            memoryInjector = get(),
            memoryIndexDao = get(),
            memoryFileStorage = get(),
            embeddingEngine = get(),
            memoryCompactor = get(),
            memoryCurator = get()  // 新增
        )
    }

    // 更新 DailyLogWriter（移除 longTermMemoryManager）
    single {
        DailyLogWriter(
            messageRepository = get(),
            sessionRepository = get(),
            agentRepository = get(),
            providerRepository = get(),
            apiKeyStorage = get(),
            adapterFactory = get(),
            memoryFileStorage = get(),
            // 已移除: longTermMemoryManager = get(),
            memoryIndexDao = get(),
            embeddingEngine = get()
        )
    }
}
```

---

### 变更 10: App 初始化

**文件**: `OneclawApplication.kt`（或 Application 类所在文件）

在 app 启动时注册策展调度：

```kotlin
class OneclawApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // ... 现有初始化 ...

        // RFC-052: 调度每日记忆策展
        val curationScheduler: CurationScheduler by inject()
        curationScheduler.scheduleCuration()
    }
}
```

这是幂等的 -- `ExistingPeriodicWorkPolicy.UPDATE` 确保重复调用 `scheduleCuration()` 不会创建重复任务。

---

### 变更 11: Memory 界面 UI

**文件**: `feature/memory/ui/MemoryScreen.kt`

在 Memory 界面中添加策展时间设置区域。可以放在 "Stats" tab 中或作为独立的设置行。

```kotlin
@Composable
fun CurationScheduleSection(
    curationHour: Int,
    curationMinute: Int,
    onTimeChanged: (Int, Int) -> Unit
) {
    var showTimePicker by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showTimePicker = true }
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Memory Curation",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Runs daily at %02d:%02d".format(curationHour, curationMinute),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = "Change time"
            )
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            initialHour = curationHour,
            initialMinute = curationMinute,
            onConfirm = { hour, minute ->
                onTimeChanged(hour, minute)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}
```

**文件**: `feature/memory/ui/MemoryViewModel.kt`

添加策展时间的状态和操作：

```kotlin
class MemoryViewModel(
    private val memoryManager: MemoryManager,
    private val curationScheduler: CurationScheduler
) : ViewModel() {

    // ... 现有状态 ...

    private val _curationTime = MutableStateFlow(curationScheduler.getCurationTime())
    val curationTime: StateFlow<Pair<Int, Int>> = _curationTime.asStateFlow()

    fun updateCurationTime(hour: Int, minute: Int) {
        curationScheduler.setCurationTime(hour, minute)
        _curationTime.value = hour to minute
    }
}
```

---

## 实现步骤

### 步骤 1: DailyLogWriter 解耦
1. [ ] 从 `DailyLogWriter` 构造函数中移除 `longTermMemoryManager`
2. [ ] 简化 `buildSummarizationPrompt()` -- 移除 "Long-term Facts" section
3. [ ] 简化 `parseSummarizationResponse()` -- 只返回每日摘要字符串
4. [ ] 从 `writeDailyLog()` 中移除 MEMORY.md 追加逻辑
5. [ ] 更新 `MemoryModule.kt` DI 注册
6. [ ] 更新现有 `DailyLogWriterParsingTest` 和其他单元测试

### 步骤 2: MemoryCurator + 每日日志合并
1. [ ] 创建 `feature/memory/curator/` 包
2. [ ] 向 `MemoryFileStorage` 添加 `writeDailyLog()` 方法
3. [ ] 实现带 `curate()` 方法、策展提示词和 `consolidateYesterdayLog()` 的 `MemoryCurator`
4. [ ] 实现合并提示词（`buildConsolidationPrompt()`）
5. [ ] 实现 `MemoryCurationWorker` (CoroutineWorker)
6. [ ] 实现带 SharedPreferences 和 WorkManager 的 `CurationScheduler`
7. [ ] 在 `MemoryModule.kt` 中注册
8. [ ] 向 `MemoryManager` 添加 `curateMemory()`
9. [ ] 在 Application.onCreate 中调用 `curationScheduler.scheduleCuration()`
10. [ ] 为 `MemoryCurator` 编写单元测试（提示词构建、响应解析、NO_CHANGES 处理）
11. [ ] 为每日日志合并编写单元测试（多块、单块、无日志场景）
12. [ ] 为 `CurationScheduler.calculateInitialDelay()` 编写单元测试

### 步骤 3: Section 重命名 + 提示词增强
1. [ ] 在 `MemorySections.STANDARD_SECTIONS` 中将 "Workflow" 重命名为 "Habits/Routines"
2. [ ] 更新 `SaveMemoryTool` category 映射（添加 "habits"、"routines" 别名）
3. [ ] 更新 `SaveMemoryTool` 描述（增强"不要保存"列表）
4. [ ] 更新 `MemoryCompactor.buildCompactionPrompt()` 新规则
5. [ ] 更新引用 "Workflow" section 名的单元测试

### 步骤 4: Memory 界面 UI
1. [ ] 添加 `CurationScheduleSection` composable
2. [ ] 向 `MemoryViewModel` 添加策展时间状态
3. [ ] 将时间选择器连接到 `CurationScheduler.setCurationTime()`
4. [ ] 添加 `TimePickerDialog` composable（或使用 Material 3 TimePicker）

### 步骤 5: 测试
1. [ ] 运行 `./gradlew test` -- 所有单元测试通过
2. [ ] 运行 `./gradlew connectedAndroidTest` -- 所有仪器测试通过
3. [ ] 为 Memory 界面变更更新 Roborazzi 截图测试
4. [ ] 手动测试：验证 DailyLogWriter 不再写入 MEMORY.md
5. [ ] 手动测试：验证策展按计划运行
6. [ ] 手动测试：验证每日日志合并将多块日志合并为一份
7. [ ] 手动测试：验证时间选择器工作并重新调度 WorkManager

---

## 测试

### 单元测试: DailyLogWriter（修改）
- `parseSummarizationResponse` 只返回每日摘要字符串
- `buildSummarizationPrompt` 不包含 "Long-term Facts"
- `writeDailyLog` 写入每日日志但不写入 MEMORY.md
- `writeDailyLog` 只索引每日日志块（无 "long_term" 来源类型）

### 单元测试: MemoryCurator
- `curate()` 每日日志含反复出现的新事实时：更新 MEMORY.md
- `curate()` 无相关新事实时：LLM 返回 NO_CHANGES，MEMORY.md 不变
- `curate()` 最近 N 天无每日日志时：提前返回 false，无 LLM 调用
- `curate()` MEMORY.md 含过期条目时：LLM 删除它们
- `curate()` LLM 返回空/短响应时：MEMORY.md 不变

### 单元测试: 每日日志合并
- `consolidateYesterdayLog` 多块日志时：调用 LLM 并用合并版本覆盖
- `consolidateYesterdayLog` 单块日志时：跳过合并，无 LLM 调用
- `consolidateYesterdayLog` 昨天无日志时：提前返回，无 LLM 调用
- `consolidateYesterdayLog` LLM 返回空/短响应时：保留原始日志
- `buildConsolidationPrompt` 包含日期和原始日志内容

### 单元测试: MemoryFileStorage（新方法）
- `writeDailyLog` 覆盖现有每日日志文件
- `writeDailyLog` 触发 git 自动提交
- `curate()` LLM 响应缺少头部时：MEMORY.md 不变
- `buildCurationPrompt` 包含当前记忆、近期日志和今天日期

### 单元测试: CurationScheduler
- `calculateInitialDelay` 目标时间在今天稍后时：延迟 < 24 小时
- `calculateInitialDelay` 目标时间已过今天时：延迟到明天
- `getCurationTime` 未设置偏好时返回默认值（3:00）
- `setCurationTime` 持久化到 SharedPreferences

### 单元测试: MemorySections
- `STANDARD_SECTIONS` 包含 "Habits/Routines"（非 "Workflow"）

### 单元测试: SaveMemoryTool（修改）
- Category "habits" 映射到 "Habits/Routines"
- Category "routines" 映射到 "Habits/Routines"
- Category "workflow" 映射到 "Habits/Routines"（向后兼容）

### 单元测试: MemoryCompactor（修改提示词）
- 压缩提示词包含今天日期
- 压缩提示词包含"过期时间条目"规则
- 压缩提示词包含"每个 section 最多 10 条"规则

### 单元测试: MemoryManager
- `curateMemory()` 委托给 MemoryCurator 并在成功时重建索引
- `curateMemory()` 无变更时不重建索引
- `curateMemory()` 策展器为 null 时优雅返回 false

---

## 迁移策略

无需数据迁移。变更向后兼容：

1. **DailyLogWriter**：简单地停止写入 MEMORY.md。现有 MEMORY.md 内容保留。
2. **Section 重命名**：策展和压缩提示词会在下次审查时自然将 "## Workflow" 重命名为 "## Habits/Routines"。无需显式迁移代码。
3. **WorkManager**：首次调用 `scheduleCuration()` 创建定期任务。如果 app 之前安装时没有此功能，任务会在下次 app 启动时创建。
4. **SharedPreferences**：在用户更改之前使用默认值（凌晨 3:00）。

---

## 风险评估

| 风险 | 严重程度 | 缓解措施 |
|------|----------|----------|
| 策展提示词过于激进 -- 删除用户想保留的条目 | 中 | Git 历史（MemoryFileStorage 自动提交）提供回滚；用户可在 GitHistoryScreen 查看历史 |
| 策展提示词过于保守 -- 从不更新 MEMORY.md | 低 | 通过每日日志分析监控；如果更新太少则调整提示词 |
| WorkManager 任务不运行（Doze、电池优化） | 低 | WorkManager 自动处理 Doze；策展非时间敏感；延迟几小时没问题 |
| 每日策展的 LLM API 成本 | 低 | 每天一次 API 调用，约 2000 token 预算；相当于单条用户消息 |
| 竞态条件：策展运行时用户通过 save_memory/update_memory 编辑 MEMORY.md | 低 | 策展在凌晨 3 点运行，用户可能在睡觉；git 历史保留两个版本 |
| 策展 LLM 幻觉内容 | 中 | 响应验证（长度检查、头部检查）；git 历史用于回滚 |

---

## 变更历史

| 日期 | 版本 | 变更 | 负责人 |
|------|------|------|--------|
| 2026-03-04 | 0.1 | 初始版本 | - |
| 2026-03-04 | 0.2 | 添加每日日志合并整理（consolidateYesterdayLog、writeDailyLog、合并提示词） | - |
