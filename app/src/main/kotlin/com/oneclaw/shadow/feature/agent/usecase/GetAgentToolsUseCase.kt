package com.oneclaw.shadow.feature.agent.usecase

import com.oneclaw.shadow.core.model.ToolDefinition
import com.oneclaw.shadow.core.repository.AgentRepository
import com.oneclaw.shadow.tool.engine.ToolRegistry

class GetAgentToolsUseCase(
    private val agentRepository: AgentRepository,
    private val toolRegistry: ToolRegistry
) {
    suspend operator fun invoke(agentId: String): List<ToolDefinition> {
        val agent = agentRepository.getAgentById(agentId) ?: return emptyList()
        return toolRegistry.getToolDefinitionsByNames(agent.toolIds)
    }
}
