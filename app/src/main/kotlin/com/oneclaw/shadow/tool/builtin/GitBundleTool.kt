package com.oneclaw.shadow.tool.builtin

import com.oneclaw.shadow.core.model.ToolDefinition
import com.oneclaw.shadow.core.model.ToolParameter
import com.oneclaw.shadow.core.model.ToolParametersSchema
import com.oneclaw.shadow.core.model.ToolResult
import com.oneclaw.shadow.data.git.AppGitRepository
import com.oneclaw.shadow.tool.engine.Tool
import java.io.File

class GitBundleTool(
    private val appGitRepository: AppGitRepository
) : Tool {

    override val definition = ToolDefinition(
        name = "git_bundle",
        description = "Export the entire git repository as a bundle file for backup or transfer. " +
            "The output path is relative to the app's file storage root.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "output_path" to ToolParameter(
                    type = "string",
                    description = "Relative path for the output bundle file " +
                        "(e.g. \"exports/oneclaw.bundle\"). Parent directories are created automatically."
                )
            ),
            required = listOf("output_path")
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 60
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        val outputPath = (parameters["output_path"] as? String)?.trim()
        if (outputPath.isNullOrEmpty()) {
            return ToolResult.error("validation_error", "Parameter 'output_path' is required.")
        }

        // Prevent path traversal
        val repoDir = appGitRepository.repoDir
        val outputFile = File(repoDir, outputPath).canonicalFile
        if (!outputFile.canonicalPath.startsWith(repoDir.canonicalPath)) {
            return ToolResult.error("security_error", "Output path must be within app storage.")
        }

        return try {
            appGitRepository.bundle(outputFile)
            val sizeKb = outputFile.length() / 1024
            ToolResult.success(
                "Bundle created at '$outputPath' (${sizeKb} KB). " +
                    "Absolute path: ${outputFile.absolutePath}"
            )
        } catch (e: IllegalStateException) {
            ToolResult.error("git_error", e.message ?: "Bundle failed")
        } catch (e: Exception) {
            ToolResult.error("git_error", "git bundle failed: ${e.message}")
        }
    }
}
