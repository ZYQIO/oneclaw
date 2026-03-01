package com.oneclaw.shadow.tool.builtin.config

import com.oneclaw.shadow.core.model.ToolDefinition
import com.oneclaw.shadow.core.model.ToolParameter
import com.oneclaw.shadow.core.model.ToolParametersSchema
import com.oneclaw.shadow.core.model.ToolResult
import com.oneclaw.shadow.core.repository.SettingsRepository
import com.oneclaw.shadow.core.theme.ThemeManager
import com.oneclaw.shadow.core.theme.ThemeMode
import com.oneclaw.shadow.tool.engine.Tool

/**
 * For known keys (e.g., theme_mode), validates the value and
 * applies side effects (e.g., ThemeManager.setThemeMode).
 */
class SetConfigTool(
    private val settingsRepository: SettingsRepository,
    private val themeManager: ThemeManager
) : Tool {

    companion object {
        val KNOWN_KEY_VALUES = mapOf(
            "theme_mode" to listOf("system", "light", "dark")
        )
    }

    override val definition = ToolDefinition(
        name = "set_config",
        description = "Set an app configuration value. " +
            "For theme_mode: allowed values are 'system', 'light', 'dark'. " +
            "Changes take effect immediately.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "key" to ToolParameter(
                    type = "string",
                    description = "The configuration key to set"
                ),
                "value" to ToolParameter(
                    type = "string",
                    description = "The value to set"
                )
            ),
            required = listOf("key", "value")
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 10
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        val key = (parameters["key"] as? String)?.trim()
        if (key.isNullOrEmpty()) {
            return ToolResult.error("validation_error", "Parameter 'key' is required.")
        }

        val value = (parameters["value"] as? String)?.trim()
        if (value.isNullOrEmpty()) {
            return ToolResult.error("validation_error", "Parameter 'value' is required and cannot be empty.")
        }

        // Validate known keys
        val allowedValues = KNOWN_KEY_VALUES[key]
        if (allowedValues != null && value !in allowedValues) {
            return ToolResult.error(
                "validation_error",
                "Invalid value '$value' for key '$key'. Allowed values: ${allowedValues.joinToString(", ")}."
            )
        }

        // Apply side effects for known keys
        when (key) {
            "theme_mode" -> {
                val mode = ThemeMode.fromKey(value)
                themeManager.setThemeMode(mode)
                return ToolResult.success("Theme mode set to '$value'. The change has been applied.")
            }
        }

        // Generic key-value storage
        settingsRepository.setString(key, value)
        return ToolResult.success("Configuration '$key' set to '$value'.")
    }
}
