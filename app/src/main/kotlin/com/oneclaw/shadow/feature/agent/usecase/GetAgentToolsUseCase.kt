package com.oneclaw.shadow.feature.agent.usecase

import com.oneclaw.shadow.core.model.ToolDefinition
import com.oneclaw.shadow.tool.engine.ToolRegistry

class GetAgentToolsUseCase(
    private val toolRegistry: ToolRegistry
) {
    operator fun invoke(agentId: String): List<ToolDefinition> {
        return toolRegistry.getAllToolDefinitions()
    }
}
