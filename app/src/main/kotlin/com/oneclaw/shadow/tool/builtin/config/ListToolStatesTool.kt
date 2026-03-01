package com.oneclaw.shadow.tool.builtin.config

import com.oneclaw.shadow.core.model.ToolDefinition
import com.oneclaw.shadow.core.model.ToolParametersSchema
import com.oneclaw.shadow.core.model.ToolResult
import com.oneclaw.shadow.tool.engine.Tool
import com.oneclaw.shadow.tool.engine.ToolEnabledStateStore
import com.oneclaw.shadow.tool.engine.ToolRegistry

class ListToolStatesTool(
    private val toolRegistry: ToolRegistry,
    private val toolEnabledStateStore: ToolEnabledStateStore
) : Tool {

    override val definition = ToolDefinition(
        name = "list_tool_states",
        description = "List all registered tools with their enabled/disabled status, " +
            "organized by group. Shows tool name, group, and whether it is enabled.",
        parametersSchema = ToolParametersSchema(
            properties = emptyMap(),
            required = emptyList()
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 10
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        val allToolNames = toolRegistry.getAllToolNames()
        if (allToolNames.isEmpty()) {
            return ToolResult.success("No tools registered.")
        }

        // Get tools organized into groups (TOOL_GROUP source type)
        val groups = toolRegistry.getToolGroups() // Map<String, List<String>>

        // Collect tools that belong to a group
        val groupedToolNames = groups.values.flatten().toSet()

        // Collect ungrouped (BUILTIN / JS_EXTENSION) tools
        val ungroupedTools = allToolNames.filter { it !in groupedToolNames }

        val sb = StringBuilder("Registered tools:\n")

        // Show grouped tools
        groups.entries.sortedBy { it.key }.forEach { (groupName, toolNames) ->
            val groupEnabled = toolEnabledStateStore.isGroupEnabled(groupName)
            sb.append("\n[Group: $groupName] ${if (groupEnabled) "ENABLED" else "DISABLED"}")
            toolNames.sorted().forEach { toolName ->
                val toolEnabled = toolEnabledStateStore.isToolEnabled(toolName)
                val status = when {
                    !groupEnabled -> "DISABLED (group disabled)"
                    !toolEnabled -> "DISABLED"
                    else -> "ENABLED"
                }
                sb.append("\n  - $toolName: $status")
            }
            sb.append("\n")
        }

        // Show ungrouped tools (built-in and JS extensions)
        if (ungroupedTools.isNotEmpty()) {
            sb.append("\n[Ungrouped tools]")
            ungroupedTools.sorted().forEach { toolName ->
                val toolEnabled = toolEnabledStateStore.isToolEnabled(toolName)
                val status = if (toolEnabled) "ENABLED" else "DISABLED"
                sb.append("\n  - $toolName: $status")
            }
            sb.append("\n")
        }

        return ToolResult.success(sb.toString())
    }
}
