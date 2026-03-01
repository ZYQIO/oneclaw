package com.oneclaw.shadow.tool.builtin.config

import com.oneclaw.shadow.core.model.ToolDefinition
import com.oneclaw.shadow.core.model.ToolParameter
import com.oneclaw.shadow.core.model.ToolParametersSchema
import com.oneclaw.shadow.core.model.ToolResult
import com.oneclaw.shadow.core.repository.SettingsRepository
import com.oneclaw.shadow.tool.engine.Tool

class GetConfigTool(
    private val settingsRepository: SettingsRepository
) : Tool {

    companion object {
        val KNOWN_KEYS = mapOf(
            "theme_mode" to "App theme mode (system/light/dark)"
        )
    }

    override val definition = ToolDefinition(
        name = "get_config",
        description = "Read an app configuration setting. " +
            "Known keys: theme_mode (system/light/dark). " +
            "Returns the current value or 'not set' if the key has no value.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "key" to ToolParameter(
                    type = "string",
                    description = "The configuration key to read"
                )
            ),
            required = listOf("key")
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 10
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        val key = (parameters["key"] as? String)?.trim()
        if (key.isNullOrEmpty()) {
            return ToolResult.error("validation_error", "Parameter 'key' is required.")
        }

        val value = settingsRepository.getString(key)
        val knownInfo = KNOWN_KEYS[key]

        return if (value != null) {
            val desc = if (knownInfo != null) " ($knownInfo)" else ""
            ToolResult.success("$key$desc = $value")
        } else {
            val sb = StringBuilder("Key '$key' is not set.")
            if (knownInfo == null) {
                sb.append("\n\nKnown configuration keys:\n")
                KNOWN_KEYS.forEach { (k, desc) ->
                    sb.append("- $k: $desc\n")
                }
            }
            ToolResult.success(sb.toString())
        }
    }
}
