package com.oneclaw.shadow.feature.agent.usecase

import com.oneclaw.shadow.core.model.AgentConstants
import com.oneclaw.shadow.core.repository.AgentRepository
import com.oneclaw.shadow.core.repository.SessionRepository
import com.oneclaw.shadow.core.util.AppResult

class DeleteAgentUseCase(
    private val agentRepository: AgentRepository,
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(agentId: String): AppResult<Unit> {
        // Update sessions referencing this agent to fall back to General Assistant
        sessionRepository.updateAgentForSessions(
            oldAgentId = agentId,
            newAgentId = AgentConstants.GENERAL_ASSISTANT_ID
        )
        return agentRepository.deleteAgent(agentId)
    }
}
