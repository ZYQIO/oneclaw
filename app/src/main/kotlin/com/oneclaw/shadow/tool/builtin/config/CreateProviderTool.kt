package com.oneclaw.shadow.tool.builtin.config

import com.oneclaw.shadow.core.model.Provider
import com.oneclaw.shadow.core.model.ProviderType
import com.oneclaw.shadow.core.model.ToolDefinition
import com.oneclaw.shadow.core.model.ToolParameter
import com.oneclaw.shadow.core.model.ToolParametersSchema
import com.oneclaw.shadow.core.model.ToolResult
import com.oneclaw.shadow.core.repository.ProviderRepository
import com.oneclaw.shadow.tool.engine.Tool

class CreateProviderTool(
    private val providerRepository: ProviderRepository
) : Tool {

    override val definition = ToolDefinition(
        name = "create_provider",
        description = "Create a new AI provider. After creation, the user must set the API key " +
            "in Settings > Providers. Supported types: OPENAI, ANTHROPIC, GEMINI.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "name" to ToolParameter(
                    type = "string",
                    description = "Display name for the provider (e.g., 'My OpenAI')"
                ),
                "type" to ToolParameter(
                    type = "string",
                    description = "Provider type",
                    enum = listOf("OPENAI", "ANTHROPIC", "GEMINI")
                ),
                "api_base_url" to ToolParameter(
                    type = "string",
                    description = "API base URL (e.g., 'https://api.openai.com/v1')"
                )
            ),
            required = listOf("name", "type", "api_base_url")
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 10
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        val name = (parameters["name"] as? String)?.trim()
        if (name.isNullOrEmpty()) {
            return ToolResult.error("validation_error", "Parameter 'name' is required.")
        }

        val typeStr = (parameters["type"] as? String)?.trim()?.uppercase()
        val type = try {
            ProviderType.valueOf(typeStr ?: "")
        } catch (e: IllegalArgumentException) {
            return ToolResult.error(
                "validation_error",
                "Parameter 'type' must be one of: OPENAI, ANTHROPIC, GEMINI."
            )
        }

        val apiBaseUrl = (parameters["api_base_url"] as? String)?.trim()
        if (apiBaseUrl.isNullOrEmpty()) {
            return ToolResult.error("validation_error", "Parameter 'api_base_url' is required.")
        }

        val now = System.currentTimeMillis()
        val provider = Provider(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            type = type,
            apiBaseUrl = apiBaseUrl,
            isPreConfigured = false,
            isActive = false,
            createdAt = now,
            updatedAt = now
        )

        providerRepository.createProvider(provider)

        return ToolResult.success(
            "Provider '${provider.name}' created successfully (ID: ${provider.id}). " +
            "Please go to Settings > Providers to set the API key before using this provider."
        )
    }
}
