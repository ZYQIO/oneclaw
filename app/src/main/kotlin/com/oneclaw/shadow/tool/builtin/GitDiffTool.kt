package com.oneclaw.shadow.tool.builtin

import com.oneclaw.shadow.core.model.ToolDefinition
import com.oneclaw.shadow.core.model.ToolParameter
import com.oneclaw.shadow.core.model.ToolParametersSchema
import com.oneclaw.shadow.core.model.ToolResult
import com.oneclaw.shadow.data.git.AppGitRepository
import com.oneclaw.shadow.tool.engine.Tool

class GitDiffTool(
    private val appGitRepository: AppGitRepository
) : Tool {

    override val definition = ToolDefinition(
        name = "git_diff",
        description = "Show the diff between two commits. " +
            "If to_sha is omitted, compares from_sha against HEAD.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "from_sha" to ToolParameter(
                    type = "string",
                    description = "The base commit SHA (full or abbreviated)."
                ),
                "to_sha" to ToolParameter(
                    type = "string",
                    description = "The target commit SHA. Defaults to HEAD if omitted."
                )
            ),
            required = listOf("from_sha")
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 30
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        val fromSha = (parameters["from_sha"] as? String)?.trim()
        if (fromSha.isNullOrEmpty()) {
            return ToolResult.error("validation_error", "Parameter 'from_sha' is required.")
        }
        val toSha = (parameters["to_sha"] as? String)?.trim()?.takeIf { it.isNotEmpty() }

        return try {
            val output = appGitRepository.diff(fromSha, toSha)
            ToolResult.success(output)
        } catch (e: Exception) {
            ToolResult.error("git_error", "git diff failed: ${e.message}")
        }
    }
}
