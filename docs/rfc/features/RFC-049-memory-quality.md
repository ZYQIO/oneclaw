# RFC-049: Memory Quality Improvement

## Document Information
- **RFC ID**: RFC-049
- **Related PRD**: [FEAT-049 (Memory Quality Improvement)](../../prd/features/FEAT-049-memory-quality.md)
- **Extends**: [RFC-023 (Memory System Enhancement)](RFC-023-memory-enhancement.md)
- **Depends On**: [RFC-023 (Memory System Enhancement)](RFC-023-memory-enhancement.md), [RFC-013 (Memory System)](RFC-013-memory.md), [RFC-004 (Tool System)](RFC-004-tool-system.md)
- **Created**: 2026-03-02
- **Last Updated**: 2026-03-02
- **Status**: Draft
- **Author**: TBD

## Overview

### Background

The `save_memory` tool (RFC-023) enables the AI to write to MEMORY.md during conversations. However, field observation reveals severe quality degradation: the AI saves transient state (model switches), duplicates the same fact multiple times, and records trivial visual observations. The root cause is twofold:

1. **Vague tool description** -- "Save important information" gives the LLM no concrete criteria for filtering
2. **Append-only architecture** -- No mechanism to check existing content, update entries, or compact duplicates

Real-world MEMORY.md from a Pixel 6a device after one week of use:
- 6 model-switch records (same fact, slightly different wording each time)
- 3 duplicate entries about email cleanup preferences
- Visual observations ("User has a colorful patterned area rug")
- Zero structured organization

### Goals

Three progressive phases:

1. **Phase 1 (Prompt Guardrails)**: Rewrite the `save_memory` tool description to establish explicit save/skip criteria. Zero code logic changes -- pure prompt engineering.
2. **Phase 2 (Read-Before-Write + Update)**: Add `update_memory` tool for in-place edits and deletions. Enhance `save_memory` to return existing memory context.
3. **Phase 3 (Structured Memory + Compaction)**: Organize MEMORY.md into semantic sections, add LLM-driven compaction to merge duplicates and prune stale entries.

### Non-Goals
- Knowledge graph or entity-relationship extraction (Zep/Graphiti style)
- Embedding-based automatic deduplication at write time
- Full memory versioning, undo, or conflict resolution UI (but pre-compaction backups are included)
- Changes to daily log writer or hybrid search engine
- Changes to memory injection (MemoryInjector) in Phase 1 or 2

## Technical Design

### Architecture Overview

```
Phase 1: Prompt-only change
┌─────────────────────────────────────────────┐
│  SaveMemoryTool.kt                          │
│  └── definition.description  ← REWRITTEN   │
└─────────────────────────────────────────────┘

Phase 2: New tool + enhanced save response
┌─────────────────────────────────────────────┐
│  SaveMemoryTool.kt                          │
│  └── execute() returns existing content     │
│                                             │
│  UpdateMemoryTool.kt              (new)     │
│  └── replace / delete entries               │
│                                             │
│  LongTermMemoryManager.kt                   │
│  └── replaceMemory()             (new)      │
│  └── deleteMemoryEntry()         (new)      │
│                                             │
│  ToolModule.kt                              │
│  └── register UpdateMemoryTool              │
└─────────────────────────────────────────────┘

Phase 3: Structured sections + compaction with backup
┌─────────────────────────────────────────────┐
│  SaveMemoryTool.kt                          │
│  └── optional "category" parameter          │
│                                             │
│  MemoryCompactor.kt              (new)      │
│  └── compact() -- LLM-driven merge         │
│  └── backup before overwrite                │
│                                             │
│  LongTermMemoryManager.kt                   │
│  └── readSections()              (new)      │
│  └── writeWithSections()         (new)      │
│                                             │
│  MemoryFileStorage.kt                       │
│  └── createBackup()              (new)      │
│  └── pruneOldBackups()           (new)      │
│                                             │
│  MemoryManager.kt                           │
│  └── compactMemory()             (new)      │
│                                             │
│  MemoryTriggerManager.kt                    │
│  └── onDayChange() triggers compact         │
└─────────────────────────────────────────────┘
```

### Phase 1: Prompt Guardrails

#### Changed File: `tool/builtin/SaveMemoryTool.kt`

The only change is the `description` string in `ToolDefinition`. No logic changes.

**Current description** (RFC-023):
```
Save important information to long-term memory (MEMORY.md). Use this when
the user asks you to remember something, or when you identify critical
information that should persist across conversations. The content will be
appended to MEMORY.md and available in future conversations.
```

**New description**:
```kotlin
override val definition = ToolDefinition(
    name = "save_memory",
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

Before saving, verify:
1. Will this still be relevant 30 days from now?
2. Is this already in the memory section of the system prompt? If yes, do not duplicate.
3. Is this a confirmed pattern (2+ occurrences) or a one-time event?

Write concise, factual entries. Prefer updating existing facts over adding new entries that contradict old ones.""",
    parametersSchema = ToolParametersSchema(
        properties = mapOf(
            "content" to ToolParameter(
                type = "string",
                description = "The text to save to long-term memory. Must be concise, factual, " +
                    "and self-contained. Avoid duplicating existing memory entries. Max 5,000 characters."
            )
        ),
        required = listOf("content")
    ),
    requiredPermissions = emptyList(),
    timeoutSeconds = 10
)
```

Key changes:
- Explicit SAVE/DO NOT SAVE lists replace the vague "important information"
- Pre-save verification checklist embedded in the description
- "2+ conversations" threshold for preference detection
- Explicit instruction to check existing memory before saving
- Instruction to prefer updating over duplicating

**Parameter description** is also tightened: "Must be concise, factual, and self-contained. Avoid duplicating existing memory entries."

#### Testing Phase 1

No new unit tests required -- only the description string changes. All existing `SaveMemoryToolTest` cases pass because validation logic is untouched.

Manual verification:
1. Switch model 3 times in conversation, verify AI does NOT call save_memory
2. Share a screenshot, verify AI does NOT save visual observations
3. Tell AI the same preference twice across conversations, verify only one entry
4. Explicitly say "remember that I use PostgreSQL 16" -- verify it IS saved

---

### Phase 2: Read-Before-Write + Update Memory Tool

#### 2A. Enhanced save_memory Response

**File**: `tool/builtin/SaveMemoryTool.kt`

After a successful save, the tool now returns a summary of existing memory to reinforce awareness:

```kotlin
override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
    val content = (parameters["content"] as? String)?.trim()
    if (content.isNullOrEmpty()) {
        return ToolResult.error(
            "validation_error",
            "Parameter 'content' is required and must be non-empty."
        )
    }
    if (content.length > MAX_CONTENT_LENGTH) {
        return ToolResult.error(
            "validation_error",
            "Parameter 'content' must be $MAX_CONTENT_LENGTH characters or less. " +
                "Current length: ${content.length}."
        )
    }

    // Phase 2: Read existing memory before saving
    val existingMemory = memoryManager.readLongTermMemory()

    // Check for obvious duplication (substring match)
    val contentNormalized = content.lowercase().trim()
    val existingNormalized = existingMemory.lowercase()
    if (contentNormalized.length > 20 && existingNormalized.contains(contentNormalized)) {
        return ToolResult.error(
            "duplicate_detected",
            "This content already exists in MEMORY.md. Use update_memory to modify existing entries."
        )
    }

    val result = memoryManager.saveToLongTermMemory(content)
    return result.fold(
        onSuccess = {
            val memoryPreview = if (existingMemory.length > 500) {
                existingMemory.take(500) + "\n... (truncated, ${existingMemory.length} chars total)"
            } else {
                existingMemory
            }
            ToolResult.success(
                "Memory saved successfully.\n\n" +
                    "Current MEMORY.md content (for reference -- avoid saving duplicates):\n" +
                    memoryPreview
            )
        },
        onFailure = { e ->
            ToolResult.error("save_failed", "Failed to save memory: ${e.message}")
        }
    )
}
```

Key changes:
- Reads existing memory before saving
- Performs a basic substring deduplication check -- if the exact content (normalized) already exists, reject with a helpful error
- Returns existing memory preview in the success response so the AI sees what is already stored

**New method in MemoryManager**:

```kotlin
/**
 * Read the current content of MEMORY.md.
 */
suspend fun readLongTermMemory(): String = withContext(Dispatchers.IO) {
    longTermMemoryManager.readMemory()
}
```

#### 2B. New UpdateMemoryTool

**File**: `tool/builtin/UpdateMemoryTool.kt`

```kotlin
package com.oneclaw.shadow.tool.builtin

import com.oneclaw.shadow.core.model.ToolDefinition
import com.oneclaw.shadow.core.model.ToolParameter
import com.oneclaw.shadow.core.model.ToolParametersSchema
import com.oneclaw.shadow.core.model.ToolResult
import com.oneclaw.shadow.feature.memory.MemoryManager
import com.oneclaw.shadow.tool.engine.Tool

class UpdateMemoryTool(
    private val memoryManager: MemoryManager
) : Tool {

    override val definition = ToolDefinition(
        name = "update_memory",
        description = """Update or delete an existing entry in long-term memory (MEMORY.md).

Use this tool to:
- Correct outdated information (e.g., update a preference that has changed)
- Remove entries that are no longer relevant
- Merge duplicate entries into one

Provide the exact text to find (old_text) and the replacement text (new_text).
To delete an entry, set new_text to empty string.
The old_text must match exactly (after trimming whitespace) -- use the memory section in the system prompt to find the precise text.""",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "old_text" to ToolParameter(
                    type = "string",
                    description = "The exact text to find in MEMORY.md. Must match an existing entry. " +
                        "Use the memory section in the system prompt to find the precise wording."
                ),
                "new_text" to ToolParameter(
                    type = "string",
                    description = "The replacement text. Set to empty string to delete the entry."
                )
            ),
            required = listOf("old_text", "new_text")
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 10
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        val oldText = (parameters["old_text"] as? String)?.trim()
        if (oldText.isNullOrEmpty()) {
            return ToolResult.error(
                "validation_error",
                "Parameter 'old_text' is required and must be non-empty."
            )
        }

        val newText = (parameters["new_text"] as? String)?.trim() ?: ""

        if (oldText == newText) {
            return ToolResult.error(
                "validation_error",
                "old_text and new_text are identical. No update needed."
            )
        }

        val result = memoryManager.updateLongTermMemory(oldText, newText)
        return result.fold(
            onSuccess = { matchCount ->
                when {
                    matchCount == 0 -> ToolResult.error(
                        "not_found",
                        "The specified text was not found in MEMORY.md. " +
                            "Check the memory section in the system prompt for the exact wording."
                    )
                    matchCount > 1 -> ToolResult.error(
                        "ambiguous_match",
                        "The specified text matches $matchCount locations in MEMORY.md. " +
                            "Provide more surrounding context to make the match unique."
                    )
                    else -> {
                        val action = if (newText.isEmpty()) "deleted" else "updated"
                        ToolResult.success("Memory entry $action successfully.")
                    }
                }
            },
            onFailure = { e ->
                ToolResult.error("update_failed", "Failed to update memory: ${e.message}")
            }
        )
    }
}
```

#### 2C. New Methods in LongTermMemoryManager

**File**: `feature/memory/longterm/LongTermMemoryManager.kt`

```kotlin
/**
 * Replace a specific text in MEMORY.md.
 * Returns the number of occurrences found.
 * Only performs the replacement if exactly 1 match is found.
 */
suspend fun replaceMemoryEntry(oldText: String, newText: String): Int =
    withContext(Dispatchers.IO) {
        val content = memoryFileStorage.readMemoryFile() ?: return@withContext 0
        val trimmedOld = oldText.trim()

        // Count occurrences
        val matchCount = countOccurrences(content, trimmedOld)
        if (matchCount != 1) return@withContext matchCount

        // Perform replacement
        val newContent = if (newText.isEmpty()) {
            // Deletion: remove the line(s) containing the old text and clean up blank lines
            content.replace(trimmedOld, "")
                .replace(Regex("\n{3,}"), "\n\n")  // Collapse excessive blank lines
                .trim() + "\n"
        } else {
            content.replace(trimmedOld, newText.trim())
        }

        memoryFileStorage.writeMemoryFile(newContent)
        1
    }

private fun countOccurrences(text: String, target: String): Int {
    var count = 0
    var index = 0
    while (true) {
        index = text.indexOf(target, index)
        if (index < 0) break
        count++
        index += target.length
    }
    return count
}
```

#### 2D. New Method in MemoryManager

```kotlin
/**
 * Update or delete an entry in long-term memory (MEMORY.md).
 * Returns Result containing the match count (0 = not found, 1 = success, >1 = ambiguous).
 */
suspend fun updateLongTermMemory(oldText: String, newText: String): Result<Int> =
    withContext(Dispatchers.IO) {
        try {
            val matchCount = longTermMemoryManager.replaceMemoryEntry(oldText, newText)

            // Reindex if the update was successful
            if (matchCount == 1) {
                try {
                    rebuildIndex()
                } catch (_: Exception) {
                    // Indexing failure is non-fatal
                }
            }

            Result.success(matchCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
```

#### 2E. DI Registration

**File**: `di/ToolModule.kt`

```kotlin
// RFC-049: update_memory built-in tool
single { UpdateMemoryTool(get()) }

single {
    ToolRegistry().apply {
        // ... existing registrations ...

        // RFC-049: Register update_memory
        try {
            register(get<UpdateMemoryTool>(), ToolSourceInfo.BUILTIN)
        } catch (e: Exception) {
            Log.e("ToolModule", "Failed to register update_memory: ${e.message}")
        }
    }
}
```

#### Testing Phase 2

**Unit Tests: UpdateMemoryTool**
- Execute with valid old_text and new_text: returns success
- Execute with old_text not found: returns `not_found` error
- Execute with old_text matching 2+ locations: returns `ambiguous_match` error
- Execute with empty old_text: returns `validation_error`
- Execute with identical old_text and new_text: returns `validation_error`
- Execute with empty new_text (deletion): returns success, entry removed
- Execute when updateLongTermMemory fails: returns `update_failed` error

**Unit Tests: SaveMemoryTool (Phase 2 enhancements)**
- Execute with content that already exists in MEMORY.md: returns `duplicate_detected` error
- Execute with valid content: success response includes memory preview
- Short content (<20 chars) skips deduplication check

**Unit Tests: LongTermMemoryManager.replaceMemoryEntry**
- Replace single occurrence: content updated, returns 1
- Target not found: returns 0, content unchanged
- Multiple occurrences: returns count, content unchanged
- Deletion (empty new_text): entry removed, blank lines collapsed

**Unit Tests: MemoryManager.updateLongTermMemory**
- Successful update triggers rebuildIndex
- Failed update does not trigger rebuildIndex
- Indexing failure does not cause overall failure

---

### Phase 3: Structured Memory with Compaction

#### 3A. Sectioned MEMORY.md Format

Define a standard structure for MEMORY.md:

```markdown
# Long-term Memory

## User Profile
- Software engineer, maker/builder personality

## Preferences
- Prefers dark mode in all apps
- Creative writing: prefers longer-form content

## Interests
- Card game: Sushi Go
- Stock price data retrieval

## Workflow
- Gmail: wants promotional email cleanup automation
- Screenshots: prefers 900x2048, adequate JS render wait time

## Projects
(empty)

## Notes
(entries that don't fit other categories)
```

Standard sections (defined as constants):
```kotlin
object MemorySections {
    const val HEADER = "# Long-term Memory"
    val STANDARD_SECTIONS = listOf(
        "User Profile",
        "Preferences",
        "Interests",
        "Workflow",
        "Projects",
        "Notes"
    )
}
```

#### 3B. Enhanced save_memory with Category

Add optional `category` parameter:

```kotlin
override val definition = ToolDefinition(
    name = "save_memory",
    description = "...",  // Phase 1 description preserved
    parametersSchema = ToolParametersSchema(
        properties = mapOf(
            "content" to ToolParameter(
                type = "string",
                description = "The text to save. Must be concise and factual. Max 5,000 characters."
            ),
            "category" to ToolParameter(
                type = "string",
                description = "The memory section to place this entry in. " +
                    "One of: profile, preferences, interests, workflow, projects, notes. " +
                    "Defaults to 'notes' if not specified."
            )
        ),
        required = listOf("content")
    ),
    // ...
)
```

Category mapping in execute():
```kotlin
val category = (parameters["category"] as? String)?.trim()?.lowercase() ?: "notes"
val sectionName = when (category) {
    "profile" -> "User Profile"
    "preferences" -> "Preferences"
    "interests" -> "Interests"
    "workflow" -> "Workflow"
    "projects" -> "Projects"
    else -> "Notes"
}
```

#### 3C. LongTermMemoryManager Section Operations

**File**: `feature/memory/longterm/LongTermMemoryManager.kt`

```kotlin
/**
 * Append content under a specific section in MEMORY.md.
 * Creates the section if it doesn't exist.
 */
suspend fun appendToSection(content: String, sectionName: String) =
    withContext(Dispatchers.IO) {
        val existing = memoryFileStorage.readMemoryFile()
        val newContent = if (existing.isNullOrBlank()) {
            // Create initial structured document
            buildStructuredMemory(mapOf(sectionName to content))
        } else if (!existing.contains("## $sectionName")) {
            // Section doesn't exist -- append it
            "$existing\n## $sectionName\n$content\n"
        } else {
            // Insert before the next section header or at end of file
            insertIntoSection(existing, sectionName, content)
        }
        memoryFileStorage.writeMemoryFile(newContent)
    }

/**
 * Read memory content parsed by section.
 */
suspend fun readSections(): Map<String, String> = withContext(Dispatchers.IO) {
    val content = memoryFileStorage.readMemoryFile() ?: return@withContext emptyMap()
    parseSections(content)
}

private fun parseSections(content: String): Map<String, String> {
    val sections = mutableMapOf<String, String>()
    val lines = content.lines()
    var currentSection = ""
    val sectionContent = StringBuilder()

    for (line in lines) {
        if (line.startsWith("## ")) {
            // Save previous section
            if (currentSection.isNotEmpty()) {
                sections[currentSection] = sectionContent.toString().trim()
            }
            currentSection = line.removePrefix("## ").trim()
            sectionContent.clear()
        } else if (currentSection.isNotEmpty() && !line.startsWith("# ")) {
            sectionContent.appendLine(line)
        }
    }
    // Save last section
    if (currentSection.isNotEmpty()) {
        sections[currentSection] = sectionContent.toString().trim()
    }
    return sections
}

private fun insertIntoSection(content: String, sectionName: String, newEntry: String): String {
    val lines = content.lines().toMutableList()
    val sectionHeader = "## $sectionName"
    val headerIndex = lines.indexOfFirst { it.trim() == sectionHeader }
    if (headerIndex < 0) return "$content\n$sectionHeader\n$newEntry\n"

    // Find the end of this section (next ## header or end of file)
    var insertIndex = lines.size
    for (i in (headerIndex + 1) until lines.size) {
        if (lines[i].startsWith("## ")) {
            insertIndex = i
            break
        }
    }

    // Insert before the next section, after any trailing blank line
    val entryLine = if (newEntry.startsWith("- ")) newEntry else "- $newEntry"
    lines.add(insertIndex, entryLine)
    return lines.joinToString("\n")
}

private fun buildStructuredMemory(initialEntries: Map<String, String>): String {
    val builder = StringBuilder("# Long-term Memory\n\n")
    for (section in MemorySections.STANDARD_SECTIONS) {
        builder.appendLine("## $section")
        val entry = initialEntries[section]
        if (entry != null) {
            builder.appendLine(if (entry.startsWith("- ")) entry else "- $entry")
        }
        builder.appendLine()
    }
    return builder.toString()
}
```

#### 3D. Pre-Compaction Backup

**File**: `feature/memory/storage/MemoryFileStorage.kt`

Before every compaction, the original MEMORY.md is backed up with a timestamp. Backups are stored in the same `memory/` directory with the naming convention `MEMORY_backup_<ISO-timestamp>.md`. A retention policy keeps only the most recent N backups to prevent unbounded storage growth.

```kotlin
// New methods in MemoryFileStorage

companion object {
    const val MAX_BACKUPS = 5
    private const val BACKUP_PREFIX = "MEMORY_backup_"
    private const val BACKUP_SUFFIX = ".md"
}

/**
 * Create a timestamped backup of MEMORY.md.
 * Returns the backup file name, or null if there is nothing to back up.
 */
fun createBackup(): String? {
    val content = readMemoryFile() ?: return null
    if (content.isBlank()) return null

    val timestamp = java.time.LocalDateTime.now()
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
    val backupName = "$BACKUP_PREFIX$timestamp$BACKUP_SUFFIX"
    val backupFile = File(memoryDir, backupName)
    backupFile.writeText(content)
    return backupName
}

/**
 * Prune old backups, keeping only the most recent [maxBackups].
 */
fun pruneOldBackups(maxBackups: Int = MAX_BACKUPS) {
    val backups = memoryDir.listFiles { file ->
        file.name.startsWith(BACKUP_PREFIX) && file.name.endsWith(BACKUP_SUFFIX)
    }?.sortedByDescending { it.lastModified() } ?: return

    if (backups.size > maxBackups) {
        backups.drop(maxBackups).forEach { it.delete() }
    }
}

/**
 * List all backup files, most recent first.
 */
fun listBackups(): List<String> {
    return memoryDir.listFiles { file ->
        file.name.startsWith(BACKUP_PREFIX) && file.name.endsWith(BACKUP_SUFFIX)
    }?.sortedByDescending { it.lastModified() }
        ?.map { it.name }
        ?: emptyList()
}

/**
 * Restore MEMORY.md from a specific backup file.
 * Returns true if restored successfully.
 */
fun restoreFromBackup(backupName: String): Boolean {
    val backupFile = File(memoryDir, backupName)
    if (!backupFile.exists()) return false
    val content = backupFile.readText()
    if (content.isBlank()) return false
    writeMemoryFile(content)
    return true
}
```

Backup retention policy:
- **Default retention**: 5 most recent backups (configurable via `MAX_BACKUPS`)
- **Pruning**: Runs automatically after each successful compaction
- **Naming**: `MEMORY_backup_2026-03-02_14-30-00.md` -- human-readable, sortable
- **Restore**: Available programmatically and from the memory settings screen (future UI)

#### 3E. Memory Compaction

**File**: `feature/memory/compaction/MemoryCompactor.kt` (new)

```kotlin
package com.oneclaw.shadow.feature.memory.compaction

import com.oneclaw.shadow.data.remote.adapter.ModelApiAdapterFactory
import com.oneclaw.shadow.feature.memory.longterm.LongTermMemoryManager
import com.oneclaw.shadow.feature.memory.storage.MemoryFileStorage

/**
 * LLM-driven memory compaction.
 * Reads MEMORY.md, backs it up, sends it to the LLM with a compaction prompt,
 * and overwrites with the compacted result.
 */
class MemoryCompactor(
    private val longTermMemoryManager: LongTermMemoryManager,
    private val memoryFileStorage: MemoryFileStorage,
    private val adapterFactory: ModelApiAdapterFactory
) {
    companion object {
        const val SIZE_THRESHOLD_CHARS = 3_000
        const val MAX_INPUT_CHARS = 10_000
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

    private suspend fun compact(content: String): Boolean {
        // 1. Backup before compaction -- this is the safety net
        memoryFileStorage.createBackup()

        val truncated = if (content.length > MAX_INPUT_CHARS) {
            content.take(MAX_INPUT_CHARS) + "\n\n[... truncated ...]"
        } else {
            content
        }

        val prompt = buildCompactionPrompt(truncated)

        // 2. Call LLM for compaction
        val response = try {
            adapterFactory.createDefaultAdapter().sendSimpleMessage(prompt)
        } catch (e: Exception) {
            return false
        }

        // 3. Validate the response
        if (response.isBlank() || response.length < 50) {
            // Compaction produced empty or suspiciously short result -- keep original
            return false
        }

        // 4. Overwrite with compacted content
        longTermMemoryManager.writeMemory(response)

        // 5. Prune old backups (keep most recent N)
        memoryFileStorage.pruneOldBackups()

        return true
    }

    private fun buildCompactionPrompt(content: String): String = """
You are a memory compaction assistant. Your job is to clean up and reorganize a user's long-term memory file.

## Input
The following is the current content of MEMORY.md:

```
$content
```

## Instructions
1. MERGE duplicate entries -- if the same fact appears multiple times, keep only the most recent/accurate version
2. REMOVE contradictions -- if two entries conflict, keep only the latest one
3. REMOVE transient information -- model preferences, temporary settings, one-time observations
4. PRESERVE entries that the user explicitly asked to remember
5. ORGANIZE into these standard sections:
   - ## User Profile (profession, background, personality)
   - ## Preferences (stable preferences for tools, UI, workflow)
   - ## Interests (hobbies, topics of interest)
   - ## Workflow (recurring tasks, automation preferences, tool usage patterns)
   - ## Projects (ongoing projects, tech stack details)
   - ## Notes (anything that doesn't fit above)
6. Write concise bullet points (- prefix) under each section
7. Remove empty sections entirely
8. Keep the header "# Long-term Memory" at the top

## Output
Return ONLY the compacted MEMORY.md content. No explanation, no commentary. Start with "# Long-term Memory".
""".trimIndent()
}
```

#### 3F. Wire Compaction into MemoryManager

**File**: `feature/memory/MemoryManager.kt`

```kotlin
class MemoryManager(
    private val dailyLogWriter: DailyLogWriter,
    private val longTermMemoryManager: LongTermMemoryManager,
    private val hybridSearchEngine: HybridSearchEngine,
    private val memoryInjector: MemoryInjector,
    private val memoryIndexDao: MemoryIndexDao,
    private val memoryFileStorage: MemoryFileStorage,
    private val embeddingEngine: EmbeddingEngine,
    private val memoryCompactor: MemoryCompactor? = null  // Phase 3, nullable for backward compat
) {
    // ... existing methods ...

    /**
     * Run memory compaction if needed.
     * Called by MemoryTriggerManager on app background (if threshold exceeded).
     */
    suspend fun compactMemoryIfNeeded(): Boolean {
        val compacted = memoryCompactor?.compactIfNeeded() ?: return false
        if (compacted) {
            try { rebuildIndex() } catch (_: Exception) {}
        }
        return compacted
    }

    /**
     * Force memory compaction (manual trigger from settings).
     */
    suspend fun forceCompactMemory(): Boolean {
        val compacted = memoryCompactor?.forceCompact() ?: return false
        if (compacted) {
            try { rebuildIndex() } catch (_: Exception) {}
        }
        return compacted
    }
}
```

#### 3G. Wire Auto-Compaction into MemoryTriggerManager

**File**: `feature/memory/trigger/MemoryTriggerManager.kt`

Add compaction after the daily log flush in `onDayChangeForActiveSession`. This runs at most once per day (when the app returns to foreground on a new date), avoiding unnecessary file I/O on every app-background event.

```kotlin
fun onDayChangeForActiveSession() {
    scope.launch {
        flushActiveSession()
        // Phase 3: Compact memory if it has grown too large (at most once per day)
        try {
            memoryManager.compactMemoryIfNeeded()
        } catch (_: Exception) {
            // Non-fatal
        }
    }
}
```

`onAppBackground()` remains unchanged -- it only flushes the daily log, no compaction.

#### Testing Phase 3

**Unit Tests: LongTermMemoryManager section operations**
- `appendToSection` with empty file: creates structured document
- `appendToSection` to existing section: inserts entry correctly
- `appendToSection` to non-existent section: creates section
- `readSections` parses all sections correctly
- `parseSections` handles edge cases (empty sections, no sections)

**Unit Tests: MemoryFileStorage backup**
- `createBackup` with existing MEMORY.md: creates timestamped backup file
- `createBackup` with empty MEMORY.md: returns null
- `createBackup` with no MEMORY.md: returns null
- `pruneOldBackups` with 7 backups (max 5): deletes the 2 oldest
- `pruneOldBackups` with 3 backups (max 5): deletes nothing
- `listBackups` returns files sorted most-recent-first
- `restoreFromBackup` with valid backup: overwrites MEMORY.md, returns true
- `restoreFromBackup` with non-existent file: returns false

**Unit Tests: MemoryCompactor**
- `compactIfNeeded` with content below threshold: returns false, no backup created
- `compactIfNeeded` with content above threshold: creates backup, calls LLM, overwrites
- `compact` with empty LLM response: keeps original content (backup still exists as safety net)
- `compact` with very short LLM response: keeps original content
- `forceCompact` with empty memory: returns false
- `forceCompact` with content: creates backup, calls LLM regardless of size
- `compact` success: `pruneOldBackups` is called after overwrite

**Unit Tests: MemoryManager compaction**
- `compactMemoryIfNeeded` triggers rebuildIndex on success
- `compactMemoryIfNeeded` does not rebuild on skip
- `forceCompactMemory` triggers rebuildIndex on success
- Null compactor returns false gracefully

**Unit Tests: SaveMemoryTool with category**
- Save with category "preferences": entry placed in Preferences section
- Save with category "profile": entry placed in User Profile section
- Save with unknown category: entry placed in Notes section
- Save without category: entry placed in Notes section

---

## Implementation Steps

### Phase 1: Prompt Guardrails
1. [ ] Rewrite `SaveMemoryTool.definition.description` with explicit save/skip criteria
2. [ ] Update `content` parameter description to emphasize conciseness and dedup
3. [ ] Run all existing SaveMemoryTool unit tests (must pass unchanged)
4. [ ] Manual test: model switch, screenshot, duplicate -- verify AI behavior

### Phase 2: Read-Before-Write + Update
1. [ ] Add `readLongTermMemory()` method to MemoryManager
2. [ ] Enhance `SaveMemoryTool.execute()` with existing-memory read, dedup check, and preview response
3. [ ] Add `replaceMemoryEntry()` and `countOccurrences()` to LongTermMemoryManager
4. [ ] Add `updateLongTermMemory()` method to MemoryManager
5. [ ] Create `UpdateMemoryTool` in `tool/builtin/`
6. [ ] Register `UpdateMemoryTool` in ToolModule.kt
7. [ ] Write unit tests for UpdateMemoryTool (7 cases)
8. [ ] Write unit tests for enhanced SaveMemoryTool (3 cases)
9. [ ] Write unit tests for LongTermMemoryManager.replaceMemoryEntry (4 cases)
10. [ ] Write unit tests for MemoryManager.updateLongTermMemory (3 cases)
11. [ ] Manual test: update preference, delete entry, duplicate rejection

### Phase 3: Structured Memory + Compaction
1. [ ] Define `MemorySections` constants object
2. [ ] Add section operations to LongTermMemoryManager: `appendToSection`, `readSections`, helpers
3. [ ] Add optional `category` parameter to SaveMemoryTool
4. [ ] Update SaveMemoryTool.execute() to use `appendToSection` when category provided
5. [ ] Add backup methods to MemoryFileStorage: `createBackup`, `pruneOldBackups`, `listBackups`, `restoreFromBackup`
6. [ ] Create `MemoryCompactor` class with compaction prompt and pre-compaction backup
7. [ ] Add `compactMemoryIfNeeded()` and `forceCompactMemory()` to MemoryManager
8. [ ] Wire auto-compaction into MemoryTriggerManager.onDayChangeForActiveSession
9. [ ] Register MemoryCompactor in DI (MemoryModule.kt)
10. [ ] Write unit tests for section operations (5 cases)
11. [ ] Write unit tests for MemoryFileStorage backup (8 cases)
12. [ ] Write unit tests for MemoryCompactor (7 cases)
13. [ ] Write unit tests for MemoryManager compaction (3 cases)
14. [ ] Write unit tests for SaveMemoryTool with category (4 cases)
15. [ ] Manual test: seed duplicates, trigger compaction, verify backup created and cleanup performed
16. [ ] Manual test: restore from backup after bad compaction
17. [ ] Manual test: save with categories, verify section placement

## Migration Strategy

**Phase 1**: No migration needed. Existing MEMORY.md files continue to work. The AI simply becomes more selective about what it saves.

**Phase 2**: No migration needed. `update_memory` works on any MEMORY.md format. The dedup check in `save_memory` works on unstructured content.

**Phase 3**: First compaction automatically restructures existing unstructured MEMORY.md into sections. No manual migration required. The compaction prompt handles both structured and unstructured input.

## Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| Phase 1 prompt is too restrictive -- AI never saves anything | Medium | Monitor save frequency; adjust prompt wording if saves drop to zero |
| Phase 2 dedup check has false positives (blocks legitimate saves) | Low | 20-char minimum for dedup; only exact substring match |
| Phase 2 update_memory partial text match fails | Low | Instruction to use system prompt memory for exact text |
| Phase 3 compaction LLM hallucinates content | Medium | Pre-compaction backup allows rollback; validate response length |
| Phase 3 compaction removes user-requested memory | Medium | Pre-compaction backup + "preserve user-requested entries" in prompt |
| Phase 3 compaction API cost | Low | Triggered only on day change (at most once per day) + size threshold (3,000 chars) |
| Phase 3 backup files accumulate storage | Low | Retention policy: max 5 backups, oldest auto-pruned |

## Change History

| Date | Version | Changes | Owner |
|------|---------|---------|-------|
| 2026-03-02 | 0.1 | Initial version | - |
| 2026-03-02 | 0.2 | Add pre-compaction backup mechanism with retention policy | - |
| 2026-03-02 | 0.3 | Change compaction trigger from onAppBackground to onDayChange (at most once per day) | - |
