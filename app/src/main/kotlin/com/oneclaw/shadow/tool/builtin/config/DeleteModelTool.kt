package com.oneclaw.shadow.tool.builtin.config

import com.oneclaw.shadow.core.model.ModelSource
import com.oneclaw.shadow.core.model.ToolDefinition
import com.oneclaw.shadow.core.model.ToolParameter
import com.oneclaw.shadow.core.model.ToolParametersSchema
import com.oneclaw.shadow.core.model.ToolResult
import com.oneclaw.shadow.core.repository.ProviderRepository
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.tool.engine.Tool

class DeleteModelTool(
    private val providerRepository: ProviderRepository
) : Tool {

    override val definition = ToolDefinition(
        name = "delete_model",
        description = "Delete a manually-added model from a provider. " +
            "Only MANUAL models can be deleted; DYNAMIC and PRESET models are managed by the system.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "provider_id" to ToolParameter(
                    type = "string",
                    description = "ID of the provider that owns the model"
                ),
                "model_id" to ToolParameter(
                    type = "string",
                    description = "ID of the model to delete"
                )
            ),
            required = listOf("provider_id", "model_id")
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 10
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        val providerId = (parameters["provider_id"] as? String)?.trim()
        if (providerId.isNullOrEmpty()) {
            return ToolResult.error("validation_error", "Parameter 'provider_id' is required.")
        }

        val modelId = (parameters["model_id"] as? String)?.trim()
        if (modelId.isNullOrEmpty()) {
            return ToolResult.error("validation_error", "Parameter 'model_id' is required.")
        }

        val provider = providerRepository.getProviderById(providerId)
            ?: return ToolResult.error("not_found", "Provider not found with ID '$providerId'.")

        val models = providerRepository.getModelsForProvider(providerId)
        val model = models.find { it.id == modelId }
            ?: return ToolResult.error("not_found", "Model '$modelId' not found for provider '${provider.name}'.")

        if (model.source != ModelSource.MANUAL) {
            return ToolResult.error(
                "permission_denied",
                "Model '${model.displayName ?: model.id}' is a ${model.source} model and cannot be deleted. " +
                "Only MANUAL models can be deleted."
            )
        }

        return when (val result = providerRepository.deleteManualModel(providerId, modelId)) {
            is AppResult.Success -> ToolResult.success(
                "Model '${model.displayName ?: model.id}' deleted from provider '${provider.name}'."
            )
            is AppResult.Error -> ToolResult.error("deletion_failed", "Failed to delete model: ${result.message}")
        }
    }
}
