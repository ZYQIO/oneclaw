package com.oneclaw.shadow.tool.builtin

import com.oneclaw.shadow.core.model.ToolDefinition
import com.oneclaw.shadow.core.model.ToolParameter
import com.oneclaw.shadow.core.model.ToolParametersSchema
import com.oneclaw.shadow.core.model.ToolResult
import com.oneclaw.shadow.data.git.AppGitRepository
import com.oneclaw.shadow.tool.engine.Tool
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GitLogTool(
    private val appGitRepository: AppGitRepository
) : Tool {

    override val definition = ToolDefinition(
        name = "git_log",
        description = "Show the git commit history for the app's file storage. " +
            "Optionally filter by file path and limit the number of commits shown.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "path" to ToolParameter(
                    type = "string",
                    description = "Optional relative file path to filter history for a specific file " +
                        "(e.g. \"memory/MEMORY.md\"). Omit to show all commits."
                ),
                "max_count" to ToolParameter(
                    type = "integer",
                    description = "Maximum number of commits to return. Default: 20."
                )
            ),
            required = emptyList()
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 30
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        val path = (parameters["path"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val maxCount = parseIntParam(parameters["max_count"])?.coerceIn(1, 200) ?: 20

        return try {
            val entries = appGitRepository.log(path, maxCount)
            if (entries.isEmpty()) {
                return ToolResult.success("No commits found${if (path != null) " for $path" else ""}.")
            }
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            val lines = entries.joinToString("\n") { entry ->
                val date = dateFormat.format(Date(entry.authorTime))
                "${entry.shortSha} | $date | ${entry.message}"
            }
            ToolResult.success(lines)
        } catch (e: Exception) {
            ToolResult.error("git_error", "git log failed: ${e.message}")
        }
    }

    private fun parseIntParam(value: Any?): Int? = when (value) {
        is Int -> value
        is Long -> value.toInt()
        is Double -> value.toInt()
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }
}
