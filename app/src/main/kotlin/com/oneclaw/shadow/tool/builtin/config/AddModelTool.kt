package com.oneclaw.shadow.tool.builtin.config

import com.oneclaw.shadow.core.model.ToolDefinition
import com.oneclaw.shadow.core.model.ToolParameter
import com.oneclaw.shadow.core.model.ToolParametersSchema
import com.oneclaw.shadow.core.model.ToolResult
import com.oneclaw.shadow.core.repository.ProviderRepository
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.tool.engine.Tool

class AddModelTool(
    private val providerRepository: ProviderRepository
) : Tool {

    override val definition = ToolDefinition(
        name = "add_model",
        description = "Add a model manually to a provider. Useful for models not returned by the API.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "provider_id" to ToolParameter(
                    type = "string",
                    description = "ID of the provider to add the model to"
                ),
                "model_id" to ToolParameter(
                    type = "string",
                    description = "The model identifier (e.g., 'gpt-4-turbo', 'claude-sonnet-4-20250514')"
                ),
                "display_name" to ToolParameter(
                    type = "string",
                    description = "Optional human-readable display name for the model"
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

        val displayName = (parameters["display_name"] as? String)?.trim()

        val provider = providerRepository.getProviderById(providerId)
            ?: return ToolResult.error("not_found", "Provider not found with ID '$providerId'.")

        return when (val result = providerRepository.addManualModel(providerId, modelId, displayName)) {
            is AppResult.Success -> ToolResult.success(
                "Model '${displayName ?: modelId}' added to provider '${provider.name}'."
            )
            is AppResult.Error -> ToolResult.error("add_failed", "Failed to add model: ${result.message}")
        }
    }
}
