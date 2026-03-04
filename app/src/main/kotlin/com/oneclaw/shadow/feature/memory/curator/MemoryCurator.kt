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
 * 0. Consolidate yesterday's fragmented daily log (if needed)
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

    /**
     * Consolidate yesterday's daily log if it has multiple summary blocks.
     * Multiple blocks occur because DailyLogWriter appends a new block each time
     * a session ends or the app goes to background.
     */
    internal suspend fun consolidateYesterdayLog(today: LocalDate) {
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
        memoryFileStorage.writeDailyLog(yesterday, response.trim())
        Log.d(TAG, "Yesterday's daily log consolidated: ${log.length} -> ${response.length} chars")
    }

    internal suspend fun callLlm(prompt: String): String? {
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
