package com.oneclaw.shadow.tool.engine

import com.oneclaw.shadow.core.model.ToolDefinition

class ToolRegistry {
    private val tools = mutableMapOf<String, ToolEntry>()

    fun register(name: String, definition: ToolDefinition) {
        tools[name] = ToolEntry(definition)
    }

    fun getToolDefinition(name: String): ToolDefinition? = tools[name]?.definition

    fun getAllToolDefinitions(): List<ToolDefinition> = tools.values.map { it.definition }

    fun getToolsByIds(ids: List<String>): List<ToolDefinition> =
        ids.mapNotNull { tools[it]?.definition }

    fun hasTools(): Boolean = tools.isNotEmpty()

    private data class ToolEntry(val definition: ToolDefinition)
}
