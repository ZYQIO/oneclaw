package com.oneclaw.shadow.tool.builtin.config

import com.oneclaw.shadow.core.model.ToolDefinition
import com.oneclaw.shadow.core.model.ToolParameter
import com.oneclaw.shadow.core.model.ToolParametersSchema
import com.oneclaw.shadow.core.model.ToolResult
import com.oneclaw.shadow.core.repository.ProviderRepository
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.data.security.ApiKeyStorage
import com.oneclaw.shadow.tool.engine.Tool

class FetchModelsTool(
    private val providerRepository: ProviderRepository,
    private val apiKeyStorage: ApiKeyStorage
) : Tool {

    override val definition = ToolDefinition(
        name = "fetch_models",
        description = "Fetch and refresh the model list from the provider's API. " +
            "Requires an API key to be configured for the provider.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "provider_id" to ToolParameter(
                    type = "string",
                    description = "ID of the provider to fetch models from"
                )
            ),
            required = listOf("provider_id")
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 30
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        val providerId = (parameters["provider_id"] as? String)?.trim()
        if (providerId.isNullOrEmpty()) {
            return ToolResult.error("validation_error", "Parameter 'provider_id' is required.")
        }

        val provider = providerRepository.getProviderById(providerId)
            ?: return ToolResult.error("not_found", "Provider not found with ID '$providerId'.")

        if (!apiKeyStorage.hasApiKey(providerId)) {
            return ToolResult.error(
                "api_key_required",
                "API key not configured for provider '${provider.name}'. " +
                "Please set it in Settings > Providers before fetching models."
            )
        }

        return when (val result = providerRepository.fetchModelsFromApi(providerId)) {
            is AppResult.Success -> {
                val models = result.data
                ToolResult.success(
                    "Successfully fetched ${models.size} model(s) from '${provider.name}':\n" +
                    models.joinToString("\n") { "- ${it.displayName ?: it.id}" }
                )
            }
            is AppResult.Error -> ToolResult.error(
                "fetch_failed",
                "Failed to fetch models from '${provider.name}': ${result.message}"
            )
        }
    }
}
