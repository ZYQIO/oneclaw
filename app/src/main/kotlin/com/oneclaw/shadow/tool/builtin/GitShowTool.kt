package com.oneclaw.shadow.tool.builtin

import com.oneclaw.shadow.core.model.ToolDefinition
import com.oneclaw.shadow.core.model.ToolParameter
import com.oneclaw.shadow.core.model.ToolParametersSchema
import com.oneclaw.shadow.core.model.ToolResult
import com.oneclaw.shadow.data.git.AppGitRepository
import com.oneclaw.shadow.tool.engine.Tool

class GitShowTool(
    private val appGitRepository: AppGitRepository
) : Tool {

    override val definition = ToolDefinition(
        name = "git_show",
        description = "Show the diff and metadata for a specific git commit. " +
            "Use git_log first to find the commit SHA.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "sha" to ToolParameter(
                    type = "string",
                    description = "The commit SHA to inspect (full or abbreviated, e.g. \"a1b2c3d\")."
                )
            ),
            required = listOf("sha")
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 30
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        val sha = (parameters["sha"] as? String)?.trim()
        if (sha.isNullOrEmpty()) {
            return ToolResult.error("validation_error", "Parameter 'sha' is required.")
        }

        return try {
            val output = appGitRepository.show(sha)
            ToolResult.success(output)
        } catch (e: Exception) {
            ToolResult.error("git_error", "git show failed: ${e.message}")
        }
    }
}
