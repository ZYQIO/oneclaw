package com.oneclaw.shadow.tool.builtin.config

import com.oneclaw.shadow.core.model.ToolDefinition
import com.oneclaw.shadow.core.model.ToolParameter
import com.oneclaw.shadow.core.model.ToolParametersSchema
import com.oneclaw.shadow.core.model.ToolResult
import com.oneclaw.shadow.tool.engine.Tool
import com.oneclaw.shadow.tool.js.EnvironmentVariableStore

/**
 * Combines list, set, and delete operations into a single tool
 * since env vars are simple key-value pairs.
 */
class ManageEnvVarTool(
    private val envVarStore: EnvironmentVariableStore
) : Tool {

    override val definition = ToolDefinition(
        name = "manage_env_var",
        description = "Manage JavaScript tool environment variables. " +
            "Actions: 'list' shows all variable keys, " +
            "'set' creates or updates a variable, " +
            "'delete' removes a variable. " +
            "Values are stored securely in encrypted preferences.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "action" to ToolParameter(
                    type = "string",
                    description = "The action to perform",
                    enum = listOf("list", "set", "delete")
                ),
                "key" to ToolParameter(
                    type = "string",
                    description = "Variable name (required for 'set' and 'delete')"
                ),
                "value" to ToolParameter(
                    type = "string",
                    description = "Variable value (required for 'set')"
                )
            ),
            required = listOf("action")
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 10
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        val action = (parameters["action"] as? String)?.trim()?.lowercase()

        return when (action) {
            "list" -> listEnvVars()
            "set" -> setEnvVar(parameters)
            "delete" -> deleteEnvVar(parameters)
            else -> ToolResult.error(
                "validation_error",
                "Parameter 'action' must be one of: list, set, delete."
            )
        }
    }

    private fun listEnvVars(): ToolResult {
        val keys = envVarStore.getKeys()
        if (keys.isEmpty()) {
            return ToolResult.success("No environment variables configured.")
        }
        val sb = StringBuilder("Environment variables (${keys.size}):\n")
        keys.sorted().forEach { key ->
            sb.append("- $key = ****\n")
        }
        sb.append("\nValues are masked for security. Use 'set' to update a value.")
        return ToolResult.success(sb.toString())
    }

    private fun setEnvVar(parameters: Map<String, Any?>): ToolResult {
        val key = (parameters["key"] as? String)?.trim()
        if (key.isNullOrEmpty()) {
            return ToolResult.error("validation_error", "Parameter 'key' is required for 'set' action.")
        }
        val value = (parameters["value"] as? String)
        if (value == null) {
            return ToolResult.error("validation_error", "Parameter 'value' is required for 'set' action.")
        }
        envVarStore.set(key, value)
        return ToolResult.success("Environment variable '$key' has been set.")
    }

    private fun deleteEnvVar(parameters: Map<String, Any?>): ToolResult {
        val key = (parameters["key"] as? String)?.trim()
        if (key.isNullOrEmpty()) {
            return ToolResult.error("validation_error", "Parameter 'key' is required for 'delete' action.")
        }
        envVarStore.delete(key)
        return ToolResult.success("Environment variable '$key' has been deleted.")
    }
}
