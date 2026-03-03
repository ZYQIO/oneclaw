package com.oneclaw.shadow.tool.builtin

import com.oneclaw.shadow.core.model.ToolDefinition
import com.oneclaw.shadow.core.model.ToolParameter
import com.oneclaw.shadow.core.model.ToolParametersSchema
import com.oneclaw.shadow.core.model.ToolResult
import com.oneclaw.shadow.data.git.AppGitRepository
import com.oneclaw.shadow.tool.engine.Tool

class GitRestoreTool(
    private val appGitRepository: AppGitRepository
) : Tool {

    override val definition = ToolDefinition(
        name = "git_restore",
        description = "Restore a specific file to its contents at a given commit. " +
            "This overwrites the current file and creates a new restore commit. " +
            "Use git_log to find the SHA of the commit you want to restore from.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "path" to ToolParameter(
                    type = "string",
                    description = "Relative path of the file to restore (e.g. \"memory/MEMORY.md\")."
                ),
                "sha" to ToolParameter(
                    type = "string",
                    description = "The commit SHA to restore the file from."
                )
            ),
            required = listOf("path", "sha")
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 30
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        val path = (parameters["path"] as? String)?.trim()
        if (path.isNullOrEmpty()) {
            return ToolResult.error("validation_error", "Parameter 'path' is required.")
        }
        val sha = (parameters["sha"] as? String)?.trim()
        if (sha.isNullOrEmpty()) {
            return ToolResult.error("validation_error", "Parameter 'sha' is required.")
        }

        return try {
            appGitRepository.restore(path, sha)
            appGitRepository.commitFile(path, "file: restore $path from ${sha.take(7)}")
            ToolResult.success("Successfully restored '$path' from commit ${sha.take(7)} and committed the change.")
        } catch (e: IllegalArgumentException) {
            ToolResult.error("not_found", e.message ?: "File not found in commit $sha")
        } catch (e: Exception) {
            ToolResult.error("git_error", "git restore failed: ${e.message}")
        }
    }
}
