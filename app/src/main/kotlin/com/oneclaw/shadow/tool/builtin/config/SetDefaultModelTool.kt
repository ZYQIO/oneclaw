package com.oneclaw.shadow.tool.builtin.config

import com.oneclaw.shadow.core.model.ToolDefinition
import com.oneclaw.shadow.core.model.ToolParameter
import com.oneclaw.shadow.core.model.ToolParametersSchema
import com.oneclaw.shadow.core.model.ToolResult
import com.oneclaw.shadow.core.repository.ProviderRepository
import com.oneclaw.shadow.tool.engine.Tool

class SetDefaultModelTool(
    private val providerRepository: ProviderRepository
) : Tool {

    override val definition = ToolDefinition(
        name = "set_default_model",
        description = "Set the global default AI model used for conversations.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "provider_id" to ToolParameter(
                    type = "string",
                    description = "ID of the provider that owns the model"
                ),
                "model_id" to ToolParameter(
                    type = "string",
                    description = "ID of the model to set as default"
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
            ?: return ToolResult.error(
                "not_found",
                "Model '$modelId' not found for provider '${provider.name}'. " +
                "Use list_models to see available models."
            )

        providerRepository.setGlobalDefaultModel(modelId = modelId, providerId = providerId)

        return ToolResult.success(
            "Global default model set to '${model.displayName ?: model.id}' (provider: ${provider.name})."
        )
    }
}
