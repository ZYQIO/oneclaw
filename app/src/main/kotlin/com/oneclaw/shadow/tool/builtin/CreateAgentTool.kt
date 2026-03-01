package com.oneclaw.shadow.tool.builtin

import com.oneclaw.shadow.core.model.ToolDefinition
import com.oneclaw.shadow.core.model.ToolParameter
import com.oneclaw.shadow.core.model.ToolParametersSchema
import com.oneclaw.shadow.core.model.ToolResult
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.feature.agent.usecase.CreateAgentUseCase
import com.oneclaw.shadow.tool.engine.Tool

class CreateAgentTool(
    private val createAgentUseCase: CreateAgentUseCase
) : Tool {

    override val definition = ToolDefinition(
        name = "create_agent",
        description = "Create a new custom AI agent with a name, description, and system prompt. " +
            "Use this tool when the user asks you to create or set up a new agent during a conversation.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "name" to ToolParameter(
                    type = "string",
                    description = "The agent's display name (e.g., 'Python Debug Helper'). Max 100 characters."
                ),
                "description" to ToolParameter(
                    type = "string",
                    description = "A short description of what this agent does (optional)."
                ),
                "system_prompt" to ToolParameter(
                    type = "string",
                    description = "The system prompt that defines the agent's behavior, expertise, and tone. " +
                        "Should be detailed and specific (200-500 words recommended). Max 50,000 characters."
                )
            ),
            required = listOf("name", "system_prompt")
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 10
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        val name = (parameters["name"] as? String)?.trim()
        if (name.isNullOrEmpty()) {
            return ToolResult.error("validation_error", "Parameter 'name' is required and must be non-empty.")
        }
        if (name.length > 100) {
            return ToolResult.error("validation_error", "Parameter 'name' must be 100 characters or less.")
        }

        val systemPrompt = (parameters["system_prompt"] as? String)?.trim()
        if (systemPrompt.isNullOrEmpty()) {
            return ToolResult.error("validation_error", "Parameter 'system_prompt' is required and must be non-empty.")
        }
        if (systemPrompt.length > 50_000) {
            return ToolResult.error("validation_error", "Parameter 'system_prompt' must be 50,000 characters or less.")
        }

        val description = (parameters["description"] as? String)?.trim()

        return when (val result = createAgentUseCase(
            name = name,
            description = description?.ifBlank { null },
            systemPrompt = systemPrompt,
            preferredProviderId = null,
            preferredModelId = null
        )) {
            is AppResult.Success -> {
                val created = result.data
                ToolResult.success(
                    "Agent '${created.name}' created successfully (ID: ${created.id}). " +
                    "The user can find it in the Agent list and switch to it from the chat screen."
                )
            }
            is AppResult.Error -> ToolResult.error("creation_failed", "Failed to create agent: ${result.message}")
        }
    }
}
