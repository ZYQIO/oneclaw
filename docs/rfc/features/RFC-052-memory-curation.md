# RFC-052: Memory Curation System

## Document Information
- **RFC ID**: RFC-052
- **Related PRD**: [FEAT-052 (Memory Curation System)](../../prd/features/FEAT-052-memory-curation.md)
- **Extends**: [RFC-049 (Memory Quality Improvement)](RFC-049-memory-quality.md), [RFC-013 (Memory System)](RFC-013-memory-system.md)
- **Depends On**: [RFC-049 (Memory Quality)](RFC-049-memory-quality.md), [RFC-013 (Memory System)](RFC-013-memory-system.md)
- **Created**: 2026-03-04
- **Last Updated**: 2026-03-04
- **Status**: Draft
- **Author**: TBD

## Overview

### Background

The current memory system has two write paths that directly append to MEMORY.md, causing quality degradation over time:

1. **DailyLogWriter** (`DailyLogWriter.kt:149-151`): After summarizing a session, it calls `longTermMemoryManager.appendMemory(longTermFacts)` to promote extracted "Long-term Facts" into MEMORY.md. This is a blind append with no quality filtering, no section routing, and no deduplication beyond the basic substring check in `SaveMemoryTool`.

2. **SaveMemoryTool** (`SaveMemoryTool.kt:101-105`): The AI can directly save content via the `save_memory` tool with category routing. While this path has prompt guardrails (FEAT-049), the AI still occasionally saves transient or low-value information.

Field observation on a Pixel device (March 4, 2026) shows MEMORY.md with:
- An orphan entry `*   User's name is Ben.` at the bottom (outside any section, using `*` instead of `-` format), duplicating the User Profile entry
- Expired temporal entries: "Candidate interview scheduled Tuesday, March 2, 2026 at 8:30 AM" still present on March 4
- Workflow section bloated with Gmail cleanup operational details (specific email addresses, label IDs, skill version numbers)
- Performance review deadlines that have passed

The root cause: DailyLogWriter promotes facts without quality review, and there is no periodic curation process to clean up stale entries.

### Goals

1. **Decouple DailyLogWriter from MEMORY.md** -- Daily log is the short-term episodic store; it should not directly write to MEMORY.md
2. **Introduce MemoryCurator** -- A daily background task (WorkManager) that reviews recent daily logs and makes deliberate, high-quality updates to MEMORY.md
3. **Rename Workflow -> Habits/Routines** -- Clarify section semantics to prevent operational detail accumulation
4. **Strengthen compaction and save prompts** -- More specific rules for the LLM
5. **User-configurable curation schedule** -- Time picker in Memory Screen, default 3:00 AM

### Non-Goals
- Semantic deduplication (embedding-based) -- MEMORY.md stays small, not worth the complexity
- TTL/expiration markers on individual entries -- risks deleting permanent important memories
- Per-save LLM quality gate -- too expensive and adds latency to every save_memory call
- Changes to MemoryInjector, HybridSearchEngine, or UpdateMemoryTool

## Technical Design

### Architecture Overview

```
Before (current):
  Session end -> DailyLogWriter -> writes daily log + appends MEMORY.md
  save_memory tool -> appends MEMORY.md (with category)
  Day change -> MemoryCompactor -> compacts MEMORY.md (if > 3000 chars)

After (this RFC):
  Session end -> DailyLogWriter -> writes daily log ONLY (no MEMORY.md touch)
  save_memory tool -> appends MEMORY.md (with category, enhanced prompt)
  WorkManager 3:00 AM -> MemoryCurationWorker -> MemoryCurator:
      1. consolidate yesterday's fragmented daily log into one summary
      2. read last 3 days of daily logs + current MEMORY.md
      3. LLM curation pass -> updates MEMORY.md (or NO_CHANGES)
      4. MemoryCompactor.compactIfNeeded()
```

```
New component diagram:

┌──────────────────────────────────────────────────────────┐
│  WorkManager (AndroidX)                                  │
│  └── MemoryCurationWorker (CoroutineWorker)              │
│      └── MemoryCurator.curate()                          │
│          ├── consolidateYesterdayLog()                    │
│          │   ├── MemoryFileStorage.readDailyLog()        │
│          │   ├── LLM API call (consolidation prompt)     │
│          │   └── MemoryFileStorage.writeDailyLog()       │
│          ├── MemoryFileStorage.readDailyLog() x N days   │
│          ├── LongTermMemoryManager.readMemory()          │
│          ├── LLM API call (curation prompt)              │
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

Modified components:

┌──────────────────────────────────────────────────────────┐
│  DailyLogWriter (MODIFIED)                               │
│  └── writeDailyLog() -- no longer writes to MEMORY.md    │
│  └── summarization prompt -- no "Long-term Facts" section│
│                                                          │
│  MemorySections (MODIFIED)                               │
│  └── "Workflow" -> "Habits/Routines"                     │
│                                                          │
│  MemoryCompactor (MODIFIED)                              │
│  └── buildCompactionPrompt() -- enhanced rules           │
│                                                          │
│  SaveMemoryTool (MODIFIED)                               │
│  └── definition.description -- enhanced DO NOT save list │
│  └── category mapping -- "habits"/"routines" added       │
│                                                          │
│  MemoryManager (MODIFIED)                                │
│  └── curate() -- new method delegating to MemoryCurator  │
│                                                          │
│  MemoryModule (MODIFIED)                                 │
│  └── register MemoryCurator, CurationScheduler           │
│                                                          │
│  MemoryScreen (MODIFIED)                                 │
│  └── curation schedule display + time picker             │
└──────────────────────────────────────────────────────────┘
```

---

### Change 1: DailyLogWriter Decoupling

**File**: `feature/memory/log/DailyLogWriter.kt`

Remove the MEMORY.md write path and simplify the summarization prompt.

**Current flow** (lines 139-152):
```kotlin
val (dailySummary, longTermFacts) = parseSummarizationResponse(responseText)

if (dailySummary.isNotBlank()) {
    memoryFileStorage.appendToDailyLog(today, dailySummary)
    indexChunks(dailySummary, "daily_log", today)
}

if (longTermFacts.isNotBlank()) {
    longTermMemoryManager.appendMemory(longTermFacts)  // <-- THIS IS THE PROBLEM
    indexChunks(longTermFacts, "long_term", null)
}
```

**New flow**:
```kotlin
// Write daily summary to daily log (only)
if (dailySummary.isNotBlank()) {
    memoryFileStorage.appendToDailyLog(today, dailySummary)
    indexChunks(dailySummary, "daily_log", today)
}

// Long-term facts are NO LONGER promoted here.
// The MemoryCurator will review daily logs periodically and
// promote high-quality facts to MEMORY.md.
```

**Simplified summarization prompt** (remove "Long-term Facts" section):

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

**Simplified response parser** (no longer needs to extract long-term facts):

```kotlin
internal fun parseSummarizationResponse(response: String): String {
    val dailySummaryRegex = Regex(
        "## Daily Summary\\s*\\n([\\s\\S]*?)$",
        RegexOption.IGNORE_CASE
    )
    return dailySummaryRegex.find(response)?.groupValues?.get(1)?.trim() ?: response.trim()
}
```

**Constructor change**: Remove `longTermMemoryManager` dependency from `DailyLogWriter`. It no longer needs it.

```kotlin
class DailyLogWriter(
    private val messageRepository: MessageRepository,
    private val sessionRepository: SessionRepository,
    private val agentRepository: AgentRepository,
    private val providerRepository: ProviderRepository,
    private val apiKeyStorage: ApiKeyStorage,
    private val adapterFactory: ModelApiAdapterFactory,
    private val memoryFileStorage: MemoryFileStorage,
    // REMOVED: private val longTermMemoryManager: LongTermMemoryManager,
    private val memoryIndexDao: MemoryIndexDao,
    private val embeddingEngine: EmbeddingEngine
)
```

**DI update** in `MemoryModule.kt`:
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
        // REMOVED: longTermMemoryManager = get(),
        memoryIndexDao = get(),
        embeddingEngine = get()
    )
}
```

---

### Change 2: MemoryCurator (New Component)

**New file**: `feature/memory/curator/MemoryCurator.kt`

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
 * Periodic memory curator that reviews recent daily logs and makes
 * deliberate, high-quality updates to MEMORY.md.
 *
 * Runs once per day via WorkManager (MemoryCurationWorker).
 *
 * Flow:
 * 1. Read last N days of daily logs
 * 2. Read current MEMORY.md
 * 3. Send both to LLM with curation prompt
 * 4. If LLM returns changes, overwrite MEMORY.md
 * 5. Run compaction if needed
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
     * Run the curation pass.
     * Returns true if MEMORY.md was updated, false otherwise.
     */
    suspend fun curate(): Boolean = withContext(Dispatchers.IO) {
        try {
            val today = LocalDate.now()

            // 0. Consolidate yesterday's fragmented daily log (if needed)
            consolidateYesterdayLog(today)

            // 1. Gather recent daily logs (now using consolidated version)
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

            // 2. Read current MEMORY.md
            val currentMemory = longTermMemoryManager.readMemory()

            // 3. Build and send curation prompt
            val prompt = buildCurationPrompt(currentMemory, recentLogs, today.toString())
            val response = callLlm(prompt) ?: return@withContext false

            // 4. Check for NO_CHANGES sentinel
            if (response.trim().startsWith(NO_CHANGES_SENTINEL, ignoreCase = true)) {
                Log.d(TAG, "Curation determined no changes needed")
                return@withContext false
            }

            // 5. Validate response
            if (response.isBlank() || response.length < 50) {
                Log.w(TAG, "Curation returned suspiciously short response, skipping")
                return@withContext false
            }
            if (!response.trimStart().startsWith("# Long-term Memory")) {
                Log.w(TAG, "Curation response doesn't start with expected header, skipping")
                return@withContext false
            }

            // 6. Overwrite MEMORY.md with curated content
            longTermMemoryManager.writeMemory(response.trim())
            Log.d(TAG, "Memory curation completed, MEMORY.md updated")

            // 7. Run compaction if needed (in case curation made it larger)
            try {
                memoryCompactor.compactIfNeeded()
            } catch (_: Exception) {
                // Non-fatal
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
     * Consolidate yesterday's daily log if it has multiple summary blocks.
     * Multiple blocks occur because DailyLogWriter appends a new block each time
     * a session ends or the app goes to background.
     */
    private suspend fun consolidateYesterdayLog(today: LocalDate) {
        val yesterday = today.minusDays(1).toString()
        val log = memoryFileStorage.readDailyLog(yesterday) ?: return

        // Check if there are multiple blocks (separated by ---)
        val blocks = log.split(Regex("\n---\n")).filter { it.isNotBlank() }
        if (blocks.size <= 1) {
            Log.d(TAG, "Yesterday's log has ${blocks.size} block(s), no consolidation needed")
            return
        }

        Log.d(TAG, "Consolidating yesterday's log ($yesterday): ${blocks.size} blocks")

        val prompt = buildConsolidationPrompt(yesterday, log)
        val response = callLlm(prompt) ?: return

        // Validate response
        if (response.isBlank() || response.length < 30) {
            Log.w(TAG, "Consolidation returned suspiciously short response, skipping")
            return
        }

        // Overwrite yesterday's daily log with consolidated version
        // MemoryFileStorage.writeDailyLog will auto-commit to git
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

**New method required in MemoryFileStorage** for consolidation (overwrite instead of append):

```kotlin
/**
 * Overwrite a daily log file with new content and auto-commit to git.
 * Used by MemoryCurator for daily log consolidation.
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

This complements the existing `appendToDailyLog()` method. The consolidation flow is:
1. `readDailyLog(yesterday)` -- read fragmented version
2. LLM merges into single summary
3. `writeDailyLog(yesterday, consolidated)` -- overwrite with clean version
4. Git auto-commit preserves both the fragmented and consolidated versions in history

---

### Change 3: MemoryCurationWorker (WorkManager)

**New file**: `feature/memory/curator/MemoryCurationWorker.kt`

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
 * WorkManager worker that runs daily memory curation.
 * Uses KoinComponent for dependency injection since WorkManager
 * workers cannot use constructor injection with Koin.
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

### Change 4: CurationScheduler

**New file**: `feature/memory/curator/CurationScheduler.kt`

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
 * Manages the WorkManager schedule for daily memory curation.
 * Stores the user's preferred curation time in SharedPreferences.
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
     * Get the currently configured curation time.
     */
    fun getCurationTime(): Pair<Int, Int> {
        val hour = prefs.getInt(KEY_HOUR, DEFAULT_HOUR)
        val minute = prefs.getInt(KEY_MINUTE, DEFAULT_MINUTE)
        return hour to minute
    }

    /**
     * Set the curation time and reschedule the WorkManager task.
     */
    fun setCurationTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_HOUR, hour)
            .putInt(KEY_MINUTE, minute)
            .apply()
        scheduleCuration(hour, minute)
    }

    /**
     * Schedule (or reschedule) the daily curation task.
     * Should be called at app startup and whenever the user changes the time.
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
     * Cancel the curation schedule.
     */
    fun cancelCuration() {
        WorkManager.getInstance(context).cancelUniqueWork(MemoryCurationWorker.WORK_NAME)
    }

    /**
     * Calculate the delay in milliseconds from now until the next occurrence
     * of the target time.
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

### Change 5: Rename Workflow -> Habits/Routines

**File**: `feature/memory/longterm/LongTermMemoryManager.kt`

```kotlin
object MemorySections {
    const val HEADER = "# Long-term Memory"
    val STANDARD_SECTIONS = listOf(
        "User Profile",
        "Preferences",
        "Interests",
        "Habits/Routines",  // Changed from "Workflow"
        "Projects",
        "Notes"
    )
}
```

**File**: `tool/builtin/SaveMemoryTool.kt` (category mapping update)

```kotlin
val sectionName = if (category != null) {
    when (category) {
        "profile" -> "User Profile"
        "preferences" -> "Preferences"
        "interests" -> "Interests"
        "habits", "routines", "workflow" -> "Habits/Routines"  // Accept all three
        "projects" -> "Projects"
        else -> "Notes"
    }
} else null
```

Also update the `category` parameter description:
```kotlin
"category" to ToolParameter(
    type = "string",
    description = "The memory section to place this entry in. " +
        "One of: profile, preferences, interests, habits, projects, notes. " +
        "Defaults to notes if not specified."
)
```

**Migration**: The curation prompt and compaction prompt both use "Habits/Routines" as the section name. When processing a MEMORY.md that still has "## Workflow", they will naturally rename it during the next curation or compaction pass. No explicit migration code needed.

---

### Change 6: Enhance Compaction Prompt

**File**: `feature/memory/compaction/MemoryCompactor.kt`

Replace `buildCompactionPrompt()`:

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

Key additions vs. the current prompt:
- Today's date injected so the LLM can identify expired entries
- Explicit rule to remove expired temporal entries (rule 4)
- Max 10 entries per section (rule 8)
- Habits/Routines section explicitly excludes operational config
- Renamed from "Workflow" to "Habits/Routines"

---

### Change 7: Enhance SaveMemoryTool Prompt

**File**: `tool/builtin/SaveMemoryTool.kt`

Add to the "DO NOT save" list in `definition.description`:

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

New additions (last 3 items in "DO NOT save"):
- Daily log-level information -- these are already searchable via HybridSearchEngine
- Operational configuration details
- Scheduled events with specific dates

---

### Change 8: Wire into MemoryManager

**File**: `feature/memory/MemoryManager.kt`

Add a new method for curation:

```kotlin
/**
 * Run memory curation (MemoryCurator).
 * Called by MemoryCurationWorker on daily schedule.
 * Returns true if MEMORY.md was updated.
 */
suspend fun curateMemory(): Boolean {
    val curated = memoryCurator?.curate() ?: return false
    if (curated) {
        try { rebuildIndex() } catch (_: Exception) {}
    }
    return curated
}
```

Add `memoryCurator` to the constructor:

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
    private val memoryCurator: MemoryCurator? = null  // NEW
)
```

---

### Change 9: DI Registration

**File**: `di/MemoryModule.kt`

```kotlin
val memoryModule = module {
    // ... existing registrations ...

    // RFC-052: Memory curator
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

    // RFC-052: Curation scheduler
    single { CurationScheduler(androidContext()) }

    // Update MemoryManager to include curator
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
            memoryCurator = get()  // NEW
        )
    }

    // Update DailyLogWriter (remove longTermMemoryManager)
    single {
        DailyLogWriter(
            messageRepository = get(),
            sessionRepository = get(),
            agentRepository = get(),
            providerRepository = get(),
            apiKeyStorage = get(),
            adapterFactory = get(),
            memoryFileStorage = get(),
            // REMOVED: longTermMemoryManager = get(),
            memoryIndexDao = get(),
            embeddingEngine = get()
        )
    }
}
```

---

### Change 10: App Initialization

**File**: `OneclawApplication.kt` (or wherever the Application class is)

Register the curation schedule at app startup:

```kotlin
class OneclawApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // ... existing initialization ...

        // RFC-052: Schedule daily memory curation
        val curationScheduler: CurationScheduler by inject()
        curationScheduler.scheduleCuration()
    }
}
```

This is idempotent -- `ExistingPeriodicWorkPolicy.UPDATE` ensures that calling `scheduleCuration()` repeatedly does not create duplicate tasks.

---

### Change 11: Memory Screen UI

**File**: `feature/memory/ui/MemoryScreen.kt`

Add a curation schedule section to the Memory Screen. This can be placed in the "Stats" tab or as a standalone settings row.

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

**File**: `feature/memory/ui/MemoryViewModel.kt`

Add state and action for curation schedule:

```kotlin
class MemoryViewModel(
    private val memoryManager: MemoryManager,
    private val curationScheduler: CurationScheduler
) : ViewModel() {

    // ... existing state ...

    private val _curationTime = MutableStateFlow(curationScheduler.getCurationTime())
    val curationTime: StateFlow<Pair<Int, Int>> = _curationTime.asStateFlow()

    fun updateCurationTime(hour: Int, minute: Int) {
        curationScheduler.setCurationTime(hour, minute)
        _curationTime.value = hour to minute
    }
}
```

---

## Implementation Steps

### Step 1: DailyLogWriter Decoupling
1. [ ] Remove `longTermMemoryManager` from `DailyLogWriter` constructor
2. [ ] Simplify `buildSummarizationPrompt()` -- remove "Long-term Facts" section
3. [ ] Simplify `parseSummarizationResponse()` -- return only daily summary string
4. [ ] Remove MEMORY.md append logic from `writeDailyLog()`
5. [ ] Update `MemoryModule.kt` DI registration
6. [ ] Update existing `DailyLogWriterParsingTest` and other unit tests

### Step 2: MemoryCurator + Daily Log Consolidation
1. [ ] Create `feature/memory/curator/` package
2. [ ] Add `writeDailyLog()` method to `MemoryFileStorage`
3. [ ] Implement `MemoryCurator` with `curate()` method, curation prompt, and `consolidateYesterdayLog()`
4. [ ] Implement consolidation prompt (`buildConsolidationPrompt()`)
5. [ ] Implement `MemoryCurationWorker` (CoroutineWorker)
6. [ ] Implement `CurationScheduler` with SharedPreferences and WorkManager
7. [ ] Register in `MemoryModule.kt`
8. [ ] Add `curateMemory()` to `MemoryManager`
9. [ ] Call `curationScheduler.scheduleCuration()` in Application.onCreate
10. [ ] Write unit tests for `MemoryCurator` (prompt building, response parsing, NO_CHANGES handling)
11. [ ] Write unit tests for daily log consolidation (multi-block, single-block, no-log scenarios)
9. [ ] Write unit tests for `CurationScheduler.calculateInitialDelay()`

### Step 3: Section Rename + Prompt Enhancement
1. [ ] Rename "Workflow" to "Habits/Routines" in `MemorySections.STANDARD_SECTIONS`
2. [ ] Update `SaveMemoryTool` category mapping (add "habits", "routines" aliases)
3. [ ] Update `SaveMemoryTool` description (enhanced DO NOT save list)
4. [ ] Update `MemoryCompactor.buildCompactionPrompt()` with new rules
5. [ ] Update unit tests that reference "Workflow" section name

### Step 4: Memory Screen UI
1. [ ] Add `CurationScheduleSection` composable
2. [ ] Add curation time state to `MemoryViewModel`
3. [ ] Wire time picker to `CurationScheduler.setCurationTime()`
4. [ ] Add `TimePickerDialog` composable (or use Material 3 TimePicker)

### Step 5: Testing
1. [ ] Run `./gradlew test` -- all unit tests pass
2. [ ] Run `./gradlew connectedAndroidTest` -- all instrumented tests pass
3. [ ] Update Roborazzi screenshot tests for Memory Screen changes
4. [ ] Manual test: verify DailyLogWriter no longer writes to MEMORY.md
5. [ ] Manual test: verify curation runs on schedule
6. [ ] Manual test: verify daily log consolidation merges multi-block logs
7. [ ] Manual test: verify time picker works and reschedules WorkManager

---

## Testing

### Unit Tests: DailyLogWriter (modified)
- `parseSummarizationResponse` returns only daily summary string
- `buildSummarizationPrompt` does not contain "Long-term Facts"
- `writeDailyLog` writes to daily log but not MEMORY.md
- `writeDailyLog` indexes daily log chunks only (no "long_term" source type)

### Unit Tests: MemoryCurator
- `curate()` with daily logs containing new repeated facts: updates MEMORY.md
- `curate()` with no relevant new facts: LLM returns NO_CHANGES, MEMORY.md untouched
- `curate()` with no daily logs in last N days: returns false early, no LLM call
- `curate()` with expired entries in MEMORY.md: LLM removes them
- `curate()` when LLM returns empty/short response: MEMORY.md untouched
- `curate()` when LLM response missing header: MEMORY.md untouched
- `buildCurationPrompt` includes current memory, recent logs, and today's date

### Unit Tests: Daily Log Consolidation
- `consolidateYesterdayLog` with multi-block log: calls LLM and overwrites with merged version
- `consolidateYesterdayLog` with single-block log: skips consolidation, no LLM call
- `consolidateYesterdayLog` with no yesterday log: returns early, no LLM call
- `consolidateYesterdayLog` when LLM returns empty/short response: original log preserved
- `buildConsolidationPrompt` includes date and raw log content

### Unit Tests: MemoryFileStorage (new method)
- `writeDailyLog` overwrites existing daily log file
- `writeDailyLog` triggers git auto-commit

### Unit Tests: CurationScheduler
- `calculateInitialDelay` when target time is later today: delay < 24 hours
- `calculateInitialDelay` when target time has already passed today: delay wraps to tomorrow
- `getCurationTime` returns default (3:00) when no preference set
- `setCurationTime` persists to SharedPreferences

### Unit Tests: MemorySections
- `STANDARD_SECTIONS` contains "Habits/Routines" (not "Workflow")

### Unit Tests: SaveMemoryTool (modified)
- Category "habits" maps to "Habits/Routines"
- Category "routines" maps to "Habits/Routines"
- Category "workflow" maps to "Habits/Routines" (backward compat)

### Unit Tests: MemoryCompactor (modified prompt)
- Compaction prompt includes today's date
- Compaction prompt contains "expired temporal entries" rule
- Compaction prompt contains "Maximum 10 entries per section" rule

### Unit Tests: MemoryManager
- `curateMemory()` delegates to MemoryCurator and rebuilds index on success
- `curateMemory()` does not rebuild index when no changes
- `curateMemory()` returns false gracefully when curator is null

---

## Migration Strategy

No data migration is needed. The changes are backward-compatible:

1. **DailyLogWriter**: Simply stops writing to MEMORY.md. Existing MEMORY.md content is preserved.
2. **Section rename**: The curation and compaction prompts will naturally rename "## Workflow" to "## Habits/Routines" on the next pass. No explicit migration code.
3. **WorkManager**: First call to `scheduleCuration()` creates the periodic task. If the app was previously installed without this feature, the task is simply created on the next app launch.
4. **SharedPreferences**: Default values (3:00 AM) are used until the user changes them.

---

## Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| Curation prompt too aggressive -- removes entries user wants to keep | Medium | Git history (MemoryFileStorage auto-commit) provides rollback; user can view history in GitHistoryScreen |
| Curation prompt too conservative -- never updates MEMORY.md | Low | Monitor via daily log analysis; adjust prompt wording if updates are too rare |
| WorkManager task not running (Doze, battery optimization) | Low | WorkManager handles Doze automatically; curation is not time-critical; a few hours delay is fine |
| LLM API cost of daily curation | Low | One API call per day with ~2000 token budget; comparable to a single user message |
| Race condition: user edits MEMORY.md via save_memory/update_memory while curation is running | Low | Curation runs at 3 AM when the user is likely asleep; git history preserves both versions |
| Curation LLM hallucinates content | Medium | Response validation (length check, header check); git history for rollback |

---

## Change History

| Date | Version | Changes | Owner |
|------|---------|---------|-------|
| 2026-03-04 | 0.1 | Initial version | - |
| 2026-03-04 | 0.2 | Add daily log consolidation (consolidateYesterdayLog, writeDailyLog, consolidation prompt) | - |
