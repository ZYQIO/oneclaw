package com.oneclaw.shadow.feature.memory.compaction

import com.oneclaw.shadow.core.repository.ProviderRepository
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.data.remote.adapter.ModelApiAdapterFactory
import com.oneclaw.shadow.data.security.ApiKeyStorage
import com.oneclaw.shadow.feature.memory.longterm.LongTermMemoryManager
import com.oneclaw.shadow.feature.memory.storage.MemoryFileStorage
import kotlinx.coroutines.flow.first

/**
 * LLM-driven memory compaction.
 * Reads MEMORY.md, backs it up, sends it to the LLM with a compaction prompt,
 * and overwrites with the compacted result.
 */
open class MemoryCompactor(
    internal val longTermMemoryManager: LongTermMemoryManager,
    internal val memoryFileStorage: MemoryFileStorage,
    private val providerRepository: ProviderRepository,
    private val apiKeyStorage: ApiKeyStorage,
    private val adapterFactory: ModelApiAdapterFactory
) {
    companion object {
        const val SIZE_THRESHOLD_CHARS = 3_000
        const val MAX_INPUT_CHARS = 10_000
        private const val MAX_TOKENS = 2_000
    }

    /**
     * Compact MEMORY.md if it exceeds the size threshold.
     * Returns true if compaction was performed, false if skipped.
     */
    suspend fun compactIfNeeded(): Boolean {
        val content = longTermMemoryManager.readMemory()
        if (content.length < SIZE_THRESHOLD_CHARS) return false
        return compact(content)
    }

    /**
     * Force compaction regardless of size.
     */
    suspend fun forceCompact(): Boolean {
        val content = longTermMemoryManager.readMemory()
        if (content.isBlank()) return false
        return compact(content)
    }

    /**
     * Call the LLM with a compaction prompt and return the result.
     * Extracted as an open method to allow overriding in tests.
     * Returns null if the call fails or if no model is configured.
     */
    internal open suspend fun callLlm(prompt: String): String? {
        return try {
            val defaultModel = providerRepository.getGlobalDefaultModel().first() ?: return null
            val provider = providerRepository.getProviderById(defaultModel.providerId) ?: return null
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
                is AppResult.Error -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun compact(content: String): Boolean {
        val truncated = if (content.length > MAX_INPUT_CHARS) {
            content.take(MAX_INPUT_CHARS) + "\n\n[... truncated ...]"
        } else {
            content
        }

        val prompt = buildCompactionPrompt(truncated)

        // 1. Call LLM for compaction
        val response = callLlm(prompt) ?: return false

        // 2. Validate the response -- keep original if suspiciously short or blank
        if (response.isBlank() || response.length < 50) {
            return false
        }

        // 3. Overwrite with compacted content (git history serves as the backup)
        longTermMemoryManager.writeMemory(response)

        return true
    }

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
Return ONLY the compacted MEMORY.md content. No explanation, no commentary. Start with "# Long-term Memory".""".trimIndent()
}
