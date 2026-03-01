package com.oneclaw.shadow.tool.builtin.config

import com.oneclaw.shadow.core.model.ToolDefinition
import com.oneclaw.shadow.core.model.ToolParameter
import com.oneclaw.shadow.core.model.ToolParametersSchema
import com.oneclaw.shadow.core.model.ToolResult
import com.oneclaw.shadow.core.repository.ProviderRepository
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.tool.engine.Tool

class DeleteProviderTool(
    private val providerRepository: ProviderRepository
) : Tool {

    override val definition = ToolDefinition(
        name = "delete_provider",
        description = "Delete a provider and all its associated models. " +
            "Pre-configured providers cannot be deleted, only deactivated.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "provider_id" to ToolParameter(
                    type = "string",
                    description = "ID of the provider to delete"
                )
            ),
            required = listOf("provider_id")
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 10
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        val providerId = (parameters["provider_id"] as? String)?.trim()
        if (providerId.isNullOrEmpty()) {
            return ToolResult.error("validation_error", "Parameter 'provider_id' is required.")
        }

        val existing = providerRepository.getProviderById(providerId)
            ?: return ToolResult.error("not_found", "Provider not found with ID '$providerId'.")

        if (existing.isPreConfigured) {
            return ToolResult.error(
                "permission_denied",
                "Pre-configured provider '${existing.name}' cannot be deleted. " +
                "Use update_provider to deactivate it instead."
            )
        }

        return when (val result = providerRepository.deleteProvider(providerId)) {
            is AppResult.Success -> ToolResult.success(
                "Provider '${existing.name}' and all its associated models have been deleted."
            )
            is AppResult.Error -> ToolResult.error("deletion_failed", "Failed to delete provider: ${result.message}")
        }
    }
}
