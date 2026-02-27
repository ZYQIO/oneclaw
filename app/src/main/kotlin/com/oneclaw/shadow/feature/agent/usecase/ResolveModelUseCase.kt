package com.oneclaw.shadow.feature.agent.usecase

import com.oneclaw.shadow.core.model.ResolvedModel
import com.oneclaw.shadow.core.repository.AgentRepository
import com.oneclaw.shadow.core.repository.ProviderRepository
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.core.util.ErrorCode
import kotlinx.coroutines.flow.first

class ResolveModelUseCase(
    private val agentRepository: AgentRepository,
    private val providerRepository: ProviderRepository
) {
    suspend operator fun invoke(agentId: String): AppResult<ResolvedModel> {
        val agent = agentRepository.getAgentById(agentId)
            ?: return AppResult.Error(
                message = "Agent not found.",
                code = ErrorCode.VALIDATION_ERROR
            )

        // 1. Try agent's preferred model/provider
        if (agent.preferredProviderId != null && agent.preferredModelId != null) {
            val provider = providerRepository.getProviderById(agent.preferredProviderId)
            if (provider != null && provider.isActive) {
                val models = providerRepository.getModelsForProvider(provider.id)
                val model = models.find { it.id == agent.preferredModelId }
                if (model != null) {
                    return AppResult.Success(ResolvedModel(model = model, provider = provider))
                }
            }
        }

        // 2. Fall back to global default
        val defaultModel = providerRepository.getGlobalDefaultModel().first()
            ?: return AppResult.Error(
                message = "No model configured. Please set up a provider in Settings.",
                code = ErrorCode.VALIDATION_ERROR
            )
        val provider = providerRepository.getProviderById(defaultModel.providerId)
        if (provider != null && provider.isActive) {
            return AppResult.Success(ResolvedModel(model = defaultModel, provider = provider))
        }

        return AppResult.Error(
            message = "No model configured. Please set up a provider in Settings.",
            code = ErrorCode.VALIDATION_ERROR
        )
    }
}
