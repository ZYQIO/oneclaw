package com.oneclaw.shadow.tool.builtin.config

import com.oneclaw.shadow.core.model.ToolDefinition
import com.oneclaw.shadow.core.model.ToolParameter
import com.oneclaw.shadow.core.model.ToolParametersSchema
import com.oneclaw.shadow.core.model.ToolResult
import com.oneclaw.shadow.core.repository.ProviderRepository
import com.oneclaw.shadow.data.security.ApiKeyStorage
import com.oneclaw.shadow.tool.engine.Tool
import kotlinx.coroutines.flow.first

class ListProvidersTool(
    private val providerRepository: ProviderRepository,
    private val apiKeyStorage: ApiKeyStorage
) : Tool {

    override val definition = ToolDefinition(
        name = "list_providers",
        description = "List all configured AI providers with their details including " +
            "ID, name, type, API base URL, active status, and whether an API key is set.",
        parametersSchema = ToolParametersSchema(
            properties = emptyMap(),
            required = emptyList()
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 10
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        val providers = providerRepository.getAllProviders().first()
        if (providers.isEmpty()) {
            return ToolResult.success("No providers configured.")
        }

        val sb = StringBuilder("Found ${providers.size} provider(s):\n")
        providers.forEachIndexed { index, provider ->
            val hasKey = apiKeyStorage.hasApiKey(provider.id)
            sb.append("\n${index + 1}. [id: ${provider.id}] ${provider.name}")
            sb.append("\n   Type: ${provider.type}")
            sb.append("\n   API Base URL: ${provider.apiBaseUrl}")
            sb.append("\n   Active: ${provider.isActive}")
            sb.append("\n   Pre-configured: ${provider.isPreConfigured}")
            sb.append("\n   API Key: ${if (hasKey) "configured" else "NOT SET"}")
            sb.append("\n")
        }
        return ToolResult.success(sb.toString())
    }
}
