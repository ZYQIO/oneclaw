package com.oneclaw.shadow.tool.builtin.config

import com.oneclaw.shadow.core.model.ToolDefinition
import com.oneclaw.shadow.core.model.ToolParameter
import com.oneclaw.shadow.core.model.ToolParametersSchema
import com.oneclaw.shadow.core.model.ToolResult
import com.oneclaw.shadow.tool.engine.Tool
import com.oneclaw.shadow.tool.engine.ToolEnabledStateStore
import com.oneclaw.shadow.tool.engine.ToolRegistry

class SetToolEnabledTool(
    private val toolRegistry: ToolRegistry,
    private val toolEnabledStateStore: ToolEnabledStateStore
) : Tool {

    override val definition = ToolDefinition(
        name = "set_tool_enabled",
        description = "Enable or disable a specific tool or tool group. " +
            "When a group is disabled, all tools in that group are effectively disabled.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "name" to ToolParameter(
                    type = "string",
                    description = "Name of the tool or group to enable/disable"
                ),
                "enabled" to ToolParameter(
                    type = "boolean",
                    description = "Whether to enable (true) or disable (false)"
                ),
                "type" to ToolParameter(
                    type = "string",
                    description = "Whether 'name' refers to a 'tool' or a 'group'. Default: 'tool'.",
                    enum = listOf("tool", "group"),
                    default = "tool"
                )
            ),
            required = listOf("name", "enabled")
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 10
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        val name = (parameters["name"] as? String)?.trim()
        if (name.isNullOrEmpty()) {
            return ToolResult.error("validation_error", "Parameter 'name' is required.")
        }

        val enabled = parameters["enabled"] as? Boolean
            ?: return ToolResult.error("validation_error", "Parameter 'enabled' is required and must be a boolean.")

        val type = (parameters["type"] as? String)?.trim()?.lowercase() ?: "tool"

        return when (type) {
            "tool" -> {
                if (!toolRegistry.hasTool(name)) {
                    return ToolResult.error("not_found", "Tool '$name' is not registered.")
                }
                toolEnabledStateStore.setToolEnabled(name, enabled)
                ToolResult.success(
                    "Tool '$name' has been ${if (enabled) "enabled" else "disabled"}."
                )
            }
            "group" -> {
                toolEnabledStateStore.setGroupEnabled(name, enabled)
                ToolResult.success(
                    "Tool group '$name' has been ${if (enabled) "enabled" else "disabled"}. " +
                    "All tools in this group are now effectively ${if (enabled) "enabled" else "disabled"}."
                )
            }
            else -> {
                ToolResult.error(
                    "validation_error",
                    "Parameter 'type' must be 'tool' or 'group'."
                )
            }
        }
    }
}
