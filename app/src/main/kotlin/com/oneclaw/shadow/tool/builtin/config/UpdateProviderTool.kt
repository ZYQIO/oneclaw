package com.oneclaw.shadow.tool.builtin.config

import com.oneclaw.shadow.core.model.ToolDefinition
import com.oneclaw.shadow.core.model.ToolParameter
import com.oneclaw.shadow.core.model.ToolParametersSchema
import com.oneclaw.shadow.core.model.ToolResult
import com.oneclaw.shadow.core.repository.ProviderRepository
import com.oneclaw.shadow.tool.engine.Tool

/**
 * Partial update semantics: only provided fields are changed.
 */
class UpdateProviderTool(
    private val providerRepository: ProviderRepository
) : Tool {

    override val definition = ToolDefinition(
        name = "update_provider",
        description = "Update an existing provider's configuration. Only provided fields are changed; " +
            "omitted fields retain their current values. Cannot change provider type.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "provider_id" to ToolParameter(
                    type = "string",
                    description = "ID of the provider to update"
                ),
                "name" to ToolParameter(
                    type = "string",
                    description = "New display name"
                ),
                "api_base_url" to ToolParameter(
                    type = "string",
                    description = "New API base URL"
                ),
                "is_active" to ToolParameter(
                    type = "boolean",
                    description = "Whether the provider is active"
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

        val changes = mutableListOf<String>()

        val newName = (parameters["name"] as? String)?.trim()
        if (newName != null && newName != existing.name) {
            if (newName.isEmpty()) {
                return ToolResult.error("validation_error", "Provider name cannot be empty.")
            }
            changes.add("name ('${existing.name}' -> '$newName')")
        }

        val newUrl = (parameters["api_base_url"] as? String)?.trim()
        if (newUrl != null && newUrl != existing.apiBaseUrl) {
            if (newUrl.isEmpty()) {
                return ToolResult.error("validation_error", "API base URL cannot be empty.")
            }
            changes.add("api_base_url ('${existing.apiBaseUrl}' -> '$newUrl')")
        }

        val newIsActive = parameters["is_active"] as? Boolean
        if (newIsActive != null && newIsActive != existing.isActive) {
            changes.add("is_active (${existing.isActive} -> $newIsActive)")
        }

        if (changes.isEmpty()) {
            return ToolResult.success("No changes to apply. Provider '${existing.name}' is unchanged.")
        }

        val updated = existing.copy(
            name = newName?.ifEmpty { existing.name } ?: existing.name,
            apiBaseUrl = newUrl?.ifEmpty { existing.apiBaseUrl } ?: existing.apiBaseUrl,
            isActive = newIsActive ?: existing.isActive,
            updatedAt = System.currentTimeMillis()
        )

        providerRepository.updateProvider(updated)

        return ToolResult.success(
            "Provider '${updated.name}' updated successfully. Changed: ${changes.joinToString(", ")}."
        )
    }
}
