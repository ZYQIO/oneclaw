# RFC-049: 记忆质量改进

## 文档信息
- **RFC ID**: RFC-049
- **关联 PRD**: [FEAT-049（记忆质量改进）](../../prd/features/FEAT-049-memory-quality.md)
- **扩展自**: [RFC-023（记忆系统增强）](RFC-023-memory-enhancement.md)
- **依赖于**: [RFC-023（记忆系统增强）](RFC-023-memory-enhancement.md)、[RFC-013（记忆系统）](RFC-013-memory.md)、[RFC-004（工具系统）](RFC-004-tool-system.md)
- **创建时间**: 2026-03-02
- **最后更新**: 2026-03-02
- **状态**: Draft
- **作者**: TBD

## 概述

### 背景

`save_memory` 工具（RFC-023）使 AI 能够在对话过程中向 MEMORY.md 写入内容。然而，实际观察揭示了严重的质量退化问题：AI 会保存瞬态状态（如模型切换记录），将同一事实重复保存多次，以及记录无关紧要的视觉观察内容。根本原因有两点：

1. **工具描述模糊** -- "Save important information" 没有为 LLM 提供任何具体的过滤标准
2. **仅追加架构** -- 没有机制用于检查现有内容、更新条目或合并重复项

某台 Pixel 6a 设备使用一周后的 MEMORY.md 真实案例：
- 6 条模型切换记录（同一事实，每次措辞略有不同）
- 3 条关于邮件清理偏好的重复条目
- 视觉观察内容（"User has a colorful patterned area rug"）
- 零结构化组织

### 目标

三个递进阶段：

1. **第一阶段（提示词护栏）**：重写 `save_memory` 工具描述，建立明确的保存/跳过标准。零代码逻辑变更——纯提示词工程。
2. **第二阶段（先读后写 + 更新）**：新增 `update_memory` 工具用于原地编辑和删除。增强 `save_memory` 使其返回现有记忆上下文。
3. **第三阶段（结构化记忆 + 压缩）**：将 MEMORY.md 组织为语义化章节，新增 LLM 驱动的压缩功能以合并重复项并清理过期条目。

### 非目标
- 知识图谱或实体关系抽取（Zep/Graphiti 风格）
- 基于嵌入向量的写入时自动去重
- 完整的记忆版本管理、撤销或冲突解决 UI（但包含压缩前备份）
- 修改每日日志写入器或混合搜索引擎
- 第一阶段或第二阶段修改记忆注入（MemoryInjector）

## 技术设计

### 架构概览

```
第一阶段：仅修改提示词
┌─────────────────────────────────────────────┐
│  SaveMemoryTool.kt                          │
│  └── definition.description  ← 重写        │
└─────────────────────────────────────────────┘

第二阶段：新工具 + 增强保存响应
┌─────────────────────────────────────────────┐
│  SaveMemoryTool.kt                          │
│  └── execute() 返回现有内容                  │
│                                             │
│  UpdateMemoryTool.kt              (新建)    │
│  └── replace / delete entries               │
│                                             │
│  LongTermMemoryManager.kt                   │
│  └── replaceMemory()             (新建)     │
│  └── deleteMemoryEntry()         (新建)     │
│                                             │
│  ToolModule.kt                              │
│  └── 注册 UpdateMemoryTool                  │
└─────────────────────────────────────────────┘

第三阶段：结构化章节 + 带备份的压缩
┌─────────────────────────────────────────────┐
│  SaveMemoryTool.kt                          │
│  └── 可选的 "category" 参数                  │
│                                             │
│  MemoryCompactor.kt              (新建)     │
│  └── compact() -- LLM 驱动的合并           │
│  └── 覆写前备份                              │
│                                             │
│  LongTermMemoryManager.kt                   │
│  └── readSections()              (新建)     │
│  └── writeWithSections()         (新建)     │
│                                             │
│  MemoryFileStorage.kt                       │
│  └── createBackup()              (新建)     │
│  └── pruneOldBackups()           (新建)     │
│                                             │
│  MemoryManager.kt                           │
│  └── compactMemory()             (新建)     │
│                                             │
│  MemoryTriggerManager.kt                    │
│  └── onAppBackground() 触发压缩             │
└─────────────────────────────────────────────┘
```

### 第一阶段：提示词护栏

#### 修改文件：`tool/builtin/SaveMemoryTool.kt`

唯一的改动是 `ToolDefinition` 中的 `description` 字符串。无逻辑变更。

**当前描述**（RFC-023）：
```
Save important information to long-term memory (MEMORY.md). Use this when
the user asks you to remember something, or when you identify critical
information that should persist across conversations. The content will be
appended to MEMORY.md and available in future conversations.
```

**新描述**：
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

关键改动：
- 明确的 SAVE/DO NOT SAVE 列表替代了模糊的"important information"
- 在描述中嵌入保存前验证清单
- 偏好检测设置了"2+ 次对话"的门槛
- 明确指示保存前检查现有记忆
- 指示优先更新而非重复添加

**参数描述**也得到收紧："Must be concise, factual, and self-contained. Avoid duplicating existing memory entries."

#### 第一阶段测试

无需新增单元测试——仅修改描述字符串。所有现有 `SaveMemoryToolTest` 用例均通过，因为验证逻辑未变动。

人工验证：
1. 在对话中切换模型 3 次，验证 AI 不会调用 save_memory
2. 分享截图，验证 AI 不会保存视觉观察内容
3. 跨两次对话表达同一个偏好，验证只产生一条记录
4. 明确说"remember that I use PostgreSQL 16"——验证确实被保存

---

### 第二阶段：先读后写 + update_memory 工具

#### 2A. 增强 save_memory 响应

**文件**：`tool/builtin/SaveMemoryTool.kt`

保存成功后，工具现在会返回现有记忆摘要，以强化 AI 的感知：

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

    // 第二阶段：保存前读取现有记忆
    val existingMemory = memoryManager.readLongTermMemory()

    // 检查明显重复（子字符串匹配）
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

关键改动：
- 保存前读取现有记忆
- 执行基础子字符串去重检查——若完全相同的内容（规范化后）已存在，则拒绝并返回友好错误
- 在成功响应中返回现有记忆预览，使 AI 能看到已存储的内容

**MemoryManager 中的新方法**：

```kotlin
/**
 * 读取 MEMORY.md 的当前内容。
 */
suspend fun readLongTermMemory(): String = withContext(Dispatchers.IO) {
    longTermMemoryManager.readMemory()
}
```

#### 2B. 新建 UpdateMemoryTool

**文件**：`tool/builtin/UpdateMemoryTool.kt`

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

#### 2C. LongTermMemoryManager 新增方法

**文件**：`feature/memory/longterm/LongTermMemoryManager.kt`

```kotlin
/**
 * 替换 MEMORY.md 中的特定文本。
 * 返回找到的匹配数量。
 * 仅当恰好找到 1 个匹配时才执行替换。
 */
suspend fun replaceMemoryEntry(oldText: String, newText: String): Int =
    withContext(Dispatchers.IO) {
        val content = memoryFileStorage.readMemoryFile() ?: return@withContext 0
        val trimmedOld = oldText.trim()

        // 统计出现次数
        val matchCount = countOccurrences(content, trimmedOld)
        if (matchCount != 1) return@withContext matchCount

        // 执行替换
        val newContent = if (newText.isEmpty()) {
            // 删除：移除包含旧文本的行，并清理多余空行
            content.replace(trimmedOld, "")
                .replace(Regex("\n{3,}"), "\n\n")  // 折叠过多的空行
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

#### 2D. MemoryManager 新增方法

```kotlin
/**
 * 更新或删除长期记忆（MEMORY.md）中的条目。
 * 返回 Result，包含匹配数量（0 = 未找到，1 = 成功，>1 = 匹配模糊）。
 */
suspend fun updateLongTermMemory(oldText: String, newText: String): Result<Int> =
    withContext(Dispatchers.IO) {
        try {
            val matchCount = longTermMemoryManager.replaceMemoryEntry(oldText, newText)

            // 更新成功时重建索引
            if (matchCount == 1) {
                try {
                    rebuildIndex()
                } catch (_: Exception) {
                    // 索引失败为非致命错误
                }
            }

            Result.success(matchCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
```

#### 2E. DI 注册

**文件**：`di/ToolModule.kt`

```kotlin
// RFC-049: update_memory 内置工具
single { UpdateMemoryTool(get()) }

single {
    ToolRegistry().apply {
        // ... 现有注册 ...

        // RFC-049: 注册 update_memory
        try {
            register(get<UpdateMemoryTool>(), ToolSourceInfo.BUILTIN)
        } catch (e: Exception) {
            Log.e("ToolModule", "Failed to register update_memory: ${e.message}")
        }
    }
}
```

#### 第二阶段测试

**单元测试：UpdateMemoryTool**
- 使用有效的 old_text 和 new_text 执行：返回成功
- 使用未找到的 old_text 执行：返回 `not_found` 错误
- old_text 匹配 2 处或以上：返回 `ambiguous_match` 错误
- 使用空 old_text 执行：返回 `validation_error`
- old_text 与 new_text 相同：返回 `validation_error`
- 使用空 new_text 执行（删除）：返回成功，条目被移除
- updateLongTermMemory 失败时：返回 `update_failed` 错误

**单元测试：SaveMemoryTool（第二阶段增强）**
- 使用 MEMORY.md 中已存在的内容执行：返回 `duplicate_detected` 错误
- 使用有效内容执行：成功响应包含记忆预览
- 短内容（小于 20 个字符）跳过去重检查

**单元测试：LongTermMemoryManager.replaceMemoryEntry**
- 替换单个匹配：内容已更新，返回 1
- 未找到目标：返回 0，内容不变
- 多个匹配：返回匹配数，内容不变
- 删除（空 new_text）：条目被移除，多余空行被折叠

**单元测试：MemoryManager.updateLongTermMemory**
- 成功更新触发 rebuildIndex
- 失败更新不触发 rebuildIndex
- 索引失败不导致整体失败

---

### 第三阶段：结构化记忆与压缩

#### 3A. MEMORY.md 章节格式

定义 MEMORY.md 的标准结构：

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

标准章节（定义为常量）：
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

#### 3B. save_memory 增加 category 参数

新增可选的 `category` 参数：

```kotlin
override val definition = ToolDefinition(
    name = "save_memory",
    description = "...",  // 保留第一阶段描述
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

execute() 中的类别映射：
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

#### 3C. LongTermMemoryManager 章节操作

**文件**：`feature/memory/longterm/LongTermMemoryManager.kt`

```kotlin
/**
 * 在 MEMORY.md 的指定章节下追加内容。
 * 若章节不存在则创建。
 */
suspend fun appendToSection(content: String, sectionName: String) =
    withContext(Dispatchers.IO) {
        val existing = memoryFileStorage.readMemoryFile()
        val newContent = if (existing.isNullOrBlank()) {
            // 创建初始结构化文档
            buildStructuredMemory(mapOf(sectionName to content))
        } else if (!existing.contains("## $sectionName")) {
            // 章节不存在——追加创建
            "$existing\n## $sectionName\n$content\n"
        } else {
            // 插入到下一个章节标题之前，或文件末尾
            insertIntoSection(existing, sectionName, content)
        }
        memoryFileStorage.writeMemoryFile(newContent)
    }

/**
 * 按章节读取并解析记忆内容。
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
            // 保存上一个章节
            if (currentSection.isNotEmpty()) {
                sections[currentSection] = sectionContent.toString().trim()
            }
            currentSection = line.removePrefix("## ").trim()
            sectionContent.clear()
        } else if (currentSection.isNotEmpty() && !line.startsWith("# ")) {
            sectionContent.appendLine(line)
        }
    }
    // 保存最后一个章节
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

    // 查找本章节末尾（下一个 ## 标题或文件末尾）
    var insertIndex = lines.size
    for (i in (headerIndex + 1) until lines.size) {
        if (lines[i].startsWith("## ")) {
            insertIndex = i
            break
        }
    }

    // 在下一章节之前插入，跳过尾部空行
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

#### 3D. 压缩前备份

**文件**：`feature/memory/storage/MemoryFileStorage.kt`

每次压缩前，原始 MEMORY.md 会以时间戳方式备份。备份存储在同一 `memory/` 目录下，命名规则为 `MEMORY_backup_<ISO时间戳>.md`。保留策略仅保留最近 N 份备份，防止存储无限增长。

```kotlin
// MemoryFileStorage 中的新方法

companion object {
    const val MAX_BACKUPS = 5
    private const val BACKUP_PREFIX = "MEMORY_backup_"
    private const val BACKUP_SUFFIX = ".md"
}

/**
 * 创建 MEMORY.md 的带时间戳备份。
 * 若无内容可备份则返回 null。
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
 * 清理旧备份，仅保留最近 [maxBackups] 份。
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
 * 列出所有备份文件，最新的排在最前。
 */
fun listBackups(): List<String> {
    return memoryDir.listFiles { file ->
        file.name.startsWith(BACKUP_PREFIX) && file.name.endsWith(BACKUP_SUFFIX)
    }?.sortedByDescending { it.lastModified() }
        ?.map { it.name }
        ?: emptyList()
}

/**
 * 从指定备份文件恢复 MEMORY.md。
 * 恢复成功返回 true。
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

备份保留策略：
- **默认保留数量**：最近 5 份备份（可通过 `MAX_BACKUPS` 配置）
- **清理时机**：每次成功压缩后自动执行
- **命名规则**：`MEMORY_backup_2026-03-02_14-30-00.md`——人类可读，可排序
- **恢复方式**：可通过代码调用，以及从记忆设置界面调用（未来 UI）

#### 3E. 记忆压缩

**文件**：`feature/memory/compaction/MemoryCompactor.kt`（新建）

```kotlin
package com.oneclaw.shadow.feature.memory.compaction

import com.oneclaw.shadow.data.remote.adapter.ModelApiAdapterFactory
import com.oneclaw.shadow.feature.memory.longterm.LongTermMemoryManager
import com.oneclaw.shadow.feature.memory.storage.MemoryFileStorage

/**
 * LLM 驱动的记忆压缩。
 * 读取 MEMORY.md，备份后发送给 LLM 执行压缩提示词，
 * 再用压缩结果覆写原文件。
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
     * 若 MEMORY.md 超过大小阈值则执行压缩。
     * 返回 true 表示已执行压缩，false 表示已跳过。
     */
    suspend fun compactIfNeeded(): Boolean {
        val content = longTermMemoryManager.readMemory()
        if (content.length < SIZE_THRESHOLD_CHARS) return false
        return compact(content)
    }

    /**
     * 强制执行压缩，不受大小限制。
     */
    suspend fun forceCompact(): Boolean {
        val content = longTermMemoryManager.readMemory()
        if (content.isBlank()) return false
        return compact(content)
    }

    private suspend fun compact(content: String): Boolean {
        // 1. 压缩前备份——这是安全兜底
        memoryFileStorage.createBackup()

        val truncated = if (content.length > MAX_INPUT_CHARS) {
            content.take(MAX_INPUT_CHARS) + "\n\n[... truncated ...]"
        } else {
            content
        }

        val prompt = buildCompactionPrompt(truncated)

        // 2. 调用 LLM 执行压缩
        val response = try {
            adapterFactory.createDefaultAdapter().sendSimpleMessage(prompt)
        } catch (e: Exception) {
            return false
        }

        // 3. 验证响应
        if (response.isBlank() || response.length < 50) {
            // 压缩结果为空或异常短——保留原始内容
            return false
        }

        // 4. 用压缩结果覆写
        longTermMemoryManager.writeMemory(response)

        // 5. 清理旧备份（保留最近 N 份）
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

#### 3F. 将压缩接入 MemoryManager

**文件**：`feature/memory/MemoryManager.kt`

```kotlin
class MemoryManager(
    private val dailyLogWriter: DailyLogWriter,
    private val longTermMemoryManager: LongTermMemoryManager,
    private val hybridSearchEngine: HybridSearchEngine,
    private val memoryInjector: MemoryInjector,
    private val memoryIndexDao: MemoryIndexDao,
    private val memoryFileStorage: MemoryFileStorage,
    private val embeddingEngine: EmbeddingEngine,
    private val memoryCompactor: MemoryCompactor? = null  // 第三阶段，可为 null 以保持向后兼容
) {
    // ... 现有方法 ...

    /**
     * 若需要则执行记忆压缩。
     * 由 MemoryTriggerManager 在应用切入后台时调用（若超过阈值）。
     */
    suspend fun compactMemoryIfNeeded(): Boolean {
        val compacted = memoryCompactor?.compactIfNeeded() ?: return false
        if (compacted) {
            try { rebuildIndex() } catch (_: Exception) {}
        }
        return compacted
    }

    /**
     * 强制执行记忆压缩（从设置界面手动触发）。
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

#### 3G. 将自动压缩接入 MemoryTriggerManager

**文件**：`feature/memory/trigger/MemoryTriggerManager.kt`

在 `onAppBackground` 的每日日志刷新之后添加压缩逻辑：

```kotlin
fun onAppBackground() {
    scope.launch {
        flushActiveSession()
        // 第三阶段：若记忆增长过大则执行压缩
        try {
            memoryManager.compactMemoryIfNeeded()
        } catch (_: Exception) {
            // 非致命错误
        }
    }
}
```

#### 第三阶段测试

**单元测试：LongTermMemoryManager 章节操作**
- `appendToSection` 文件为空：创建结构化文档
- `appendToSection` 向已有章节追加：正确插入条目
- `appendToSection` 向不存在的章节追加：创建该章节
- `readSections` 正确解析所有章节
- `parseSections` 处理边界情况（空章节、无章节）

**单元测试：MemoryFileStorage 备份**
- `createBackup` 在 MEMORY.md 存在时：创建带时间戳的备份文件
- `createBackup` 在 MEMORY.md 为空时：返回 null
- `createBackup` 在 MEMORY.md 不存在时：返回 null
- `pruneOldBackups` 存在 7 份备份（最大 5）：删除最旧的 2 份
- `pruneOldBackups` 存在 3 份备份（最大 5）：不删除任何内容
- `listBackups` 返回按最新时间排序的文件列表
- `restoreFromBackup` 使用有效备份：覆写 MEMORY.md，返回 true
- `restoreFromBackup` 使用不存在的文件：返回 false

**单元测试：MemoryCompactor**
- `compactIfNeeded` 内容低于阈值：返回 false，不创建备份
- `compactIfNeeded` 内容超过阈值：创建备份，调用 LLM，覆写文件
- `compact` LLM 返回空响应：保留原始内容（备份仍作为安全兜底存在）
- `compact` LLM 返回极短响应：保留原始内容
- `forceCompact` 记忆为空：返回 false
- `forceCompact` 有内容：无论大小均创建备份并调用 LLM
- `compact` 成功：覆写后调用 `pruneOldBackups`

**单元测试：MemoryManager 压缩**
- `compactMemoryIfNeeded` 成功时触发 rebuildIndex
- `compactMemoryIfNeeded` 跳过时不重建索引
- `forceCompactMemory` 成功时触发 rebuildIndex
- compactor 为 null 时优雅返回 false

**单元测试：带 category 的 SaveMemoryTool**
- 使用 category "preferences" 保存：条目放入 Preferences 章节
- 使用 category "profile" 保存：条目放入 User Profile 章节
- 使用未知 category 保存：条目放入 Notes 章节
- 不指定 category 保存：条目放入 Notes 章节

---

## 实施步骤

### 第一阶段：提示词护栏
1. [ ] 重写 `SaveMemoryTool.definition.description`，包含明确的保存/跳过标准
2. [ ] 更新 `content` 参数描述，强调简洁和去重
3. [ ] 运行所有现有 SaveMemoryTool 单元测试（必须全部通过）
4. [ ] 人工测试：模型切换、截图、重复输入——验证 AI 行为

### 第二阶段：先读后写 + 更新
1. [ ] 在 MemoryManager 中添加 `readLongTermMemory()` 方法
2. [ ] 增强 `SaveMemoryTool.execute()`，加入现有记忆读取、去重检查和预览响应
3. [ ] 在 LongTermMemoryManager 中添加 `replaceMemoryEntry()` 和 `countOccurrences()`
4. [ ] 在 MemoryManager 中添加 `updateLongTermMemory()` 方法
5. [ ] 在 `tool/builtin/` 中创建 `UpdateMemoryTool`
6. [ ] 在 ToolModule.kt 中注册 `UpdateMemoryTool`
7. [ ] 编写 UpdateMemoryTool 单元测试（7 个用例）
8. [ ] 编写增强版 SaveMemoryTool 单元测试（3 个用例）
9. [ ] 编写 LongTermMemoryManager.replaceMemoryEntry 单元测试（4 个用例）
10. [ ] 编写 MemoryManager.updateLongTermMemory 单元测试（3 个用例）
11. [ ] 人工测试：更新偏好、删除条目、重复拒绝

### 第三阶段：结构化记忆 + 压缩
1. [ ] 定义 `MemorySections` 常量对象
2. [ ] 在 LongTermMemoryManager 中添加章节操作：`appendToSection`、`readSections` 及辅助函数
3. [ ] 在 SaveMemoryTool 中添加可选 `category` 参数
4. [ ] 更新 SaveMemoryTool.execute()，在提供 category 时使用 `appendToSection`
5. [ ] 在 MemoryFileStorage 中添加备份方法：`createBackup`、`pruneOldBackups`、`listBackups`、`restoreFromBackup`
6. [ ] 创建 `MemoryCompactor` 类，包含压缩提示词和压缩前备份
7. [ ] 在 MemoryManager 中添加 `compactMemoryIfNeeded()` 和 `forceCompactMemory()`
8. [ ] 将自动压缩接入 MemoryTriggerManager.onAppBackground
9. [ ] 在 DI 中注册 MemoryCompactor（MemoryModule.kt）
10. [ ] 编写章节操作单元测试（5 个用例）
11. [ ] 编写 MemoryFileStorage 备份单元测试（8 个用例）
12. [ ] 编写 MemoryCompactor 单元测试（7 个用例）
13. [ ] 编写 MemoryManager 压缩单元测试（3 个用例）
14. [ ] 编写带 category 的 SaveMemoryTool 单元测试（4 个用例）
15. [ ] 人工测试：埋入重复数据，触发压缩，验证备份已创建且清理已执行
16. [ ] 人工测试：压缩结果异常后从备份恢复
17. [ ] 人工测试：带 category 保存，验证章节位置正确

## 迁移策略

**第一阶段**：无需迁移。现有 MEMORY.md 文件继续正常使用。AI 只是在保存内容时变得更加严格。

**第二阶段**：无需迁移。`update_memory` 适用于任何格式的 MEMORY.md。`save_memory` 中的去重检查也适用于非结构化内容。

**第三阶段**：首次压缩时会自动将现有非结构化 MEMORY.md 重组为分章节格式。无需手动迁移。压缩提示词能同时处理结构化和非结构化输入。

## 风险评估

| 风险 | 严重程度 | 缓解措施 |
|------|----------|----------|
| 第一阶段提示词过于严格——AI 几乎不保存任何内容 | 中 | 监控保存频率；若保存量降为零则调整提示词措辞 |
| 第二阶段去重检查误报（阻止合法保存） | 低 | 去重最低 20 个字符；仅精确子字符串匹配 |
| 第二阶段 update_memory 局部文本匹配失败 | 低 | 指导使用系统提示词中的记忆内容获取精确文本 |
| 第三阶段压缩 LLM 产生幻觉内容 | 中 | 压缩前备份允许回滚；验证响应长度 |
| 第三阶段压缩删除用户主动要求记住的内容 | 中 | 压缩前备份 + 提示词中的"保留用户要求记住的条目"指令 |
| 第三阶段每次切入后台都产生压缩 API 费用 | 低 | 大小阈值（3,000 个字符）防止频繁调用 |
| 第三阶段备份文件累积占用存储 | 低 | 保留策略：最多 5 份备份，最旧的自动清理 |

## 变更历史

| 日期 | 版本 | 变更内容 | 负责人 |
|------|------|----------|--------|
| 2026-03-02 | 0.1 | 初始版本 | - |
| 2026-03-02 | 0.2 | 新增压缩前备份机制及保留策略 | - |
