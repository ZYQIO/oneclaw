package com.oneclaw.shadow.feature.agent.usecase

import com.oneclaw.shadow.core.model.Agent
import com.oneclaw.shadow.core.repository.AgentRepository
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.core.util.ErrorCode
import com.oneclaw.shadow.feature.agent.AgentValidator

class CreateAgentUseCase(
    private val agentRepository: AgentRepository
) {
    suspend operator fun invoke(
        name: String,
        description: String?,
        systemPrompt: String,
        toolIds: List<String>,
        preferredProviderId: String?,
        preferredModelId: String?
    ): AppResult<Agent> {
        AgentValidator.validateName(name)?.let {
            return AppResult.Error(message = it, code = ErrorCode.VALIDATION_ERROR)
        }
        AgentValidator.validateSystemPrompt(systemPrompt)?.let {
            return AppResult.Error(message = it, code = ErrorCode.VALIDATION_ERROR)
        }
        AgentValidator.validatePreferredModel(preferredProviderId, preferredModelId)?.let {
            return AppResult.Error(message = it, code = ErrorCode.VALIDATION_ERROR)
        }

        val agent = Agent(
            id = "",
            name = name.trim(),
            description = description?.trim()?.ifBlank { null },
            systemPrompt = systemPrompt.trim(),
            toolIds = toolIds,
            preferredProviderId = preferredProviderId,
            preferredModelId = preferredModelId,
            isBuiltIn = false,
            createdAt = 0,
            updatedAt = 0
        )
        val created = agentRepository.createAgent(agent)
        return AppResult.Success(created)
    }
}
