package com.oneclaw.shadow.tool.builtin.config

import com.oneclaw.shadow.core.model.ToolDefinition
import com.oneclaw.shadow.core.model.ToolParameter
import com.oneclaw.shadow.core.model.ToolParametersSchema
import com.oneclaw.shadow.core.model.ToolResult
import com.oneclaw.shadow.core.repository.AgentRepository
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.tool.engine.Tool

/**
 * Partial update semantics. Built-in agents cannot be modified.
 */
class UpdateAgentTool(
    private val agentRepository: AgentRepository
) : Tool {

    override val definition = ToolDefinition(
        name = "update_agent",
        description = "Update an existing agent's configuration. Only provided fields are changed. " +
            "Built-in agents cannot be modified.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "agent_id" to ToolParameter(
                    type = "string",
                    description = "ID of the agent to update"
                ),
                "name" to ToolParameter(
                    type = "string",
                    description = "New agent name (max 100 characters)"
                ),
                "description" to ToolParameter(
                    type = "string",
                    description = "New agent description"
                ),
                "system_prompt" to ToolParameter(
                    type = "string",
                    description = "New system prompt (max 50,000 characters)"
                ),
                "preferred_provider_id" to ToolParameter(
                    type = "string",
                    description = "ID of the preferred provider (empty string to clear)"
                ),
                "preferred_model_id" to ToolParameter(
                    type = "string",
                    description = "ID of the preferred model (empty string to clear)"
                )
            ),
            required = listOf("agent_id")
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 10
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        val agentId = (parameters["agent_id"] as? String)?.trim()
        if (agentId.isNullOrEmpty()) {
            return ToolResult.error("validation_error", "Parameter 'agent_id' is required.")
        }

        val existing = agentRepository.getAgentById(agentId)
            ?: return ToolResult.error("not_found", "Agent not found with ID '$agentId'.")

        if (existing.isBuiltIn) {
            return ToolResult.error(
                "permission_denied",
                "Built-in agent '${existing.name}' cannot be modified."
            )
        }

        val changes = mutableListOf<String>()

        val newName = parameters["name"] as? String
        if (newName != null) {
            val trimmed = newName.trim()
            if (trimmed.isEmpty()) {
                return ToolResult.error("validation_error", "Agent name cannot be empty.")
            }
            if (trimmed.length > 100) {
                return ToolResult.error("validation_error", "Agent name must be 100 characters or less.")
            }
            if (trimmed != existing.name) changes.add("name")
        }

        val newDescription = parameters["description"] as? String
        if (newDescription != null && newDescription.trim() != (existing.description ?: "")) {
            changes.add("description")
        }

        val newSystemPrompt = parameters["system_prompt"] as? String
        if (newSystemPrompt != null) {
            val trimmed = newSystemPrompt.trim()
            if (trimmed.isEmpty()) {
                return ToolResult.error("validation_error", "System prompt cannot be empty.")
            }
            if (trimmed.length > 50_000) {
                return ToolResult.error("validation_error", "System prompt must be 50,000 characters or less.")
            }
            if (trimmed != existing.systemPrompt) changes.add("system_prompt")
        }

        val newProviderId = parameters["preferred_provider_id"] as? String
        if (newProviderId != null) changes.add("preferred_provider_id")

        val newModelId = parameters["preferred_model_id"] as? String
        if (newModelId != null) changes.add("preferred_model_id")

        if (changes.isEmpty()) {
            return ToolResult.success("No changes to apply. Agent '${existing.name}' is unchanged.")
        }

        val updated = existing.copy(
            name = (parameters["name"] as? String)?.trim() ?: existing.name,
            description = if (parameters.containsKey("description"))
                (parameters["description"] as? String)?.trim()?.ifBlank { null }
            else existing.description,
            systemPrompt = (parameters["system_prompt"] as? String)?.trim() ?: existing.systemPrompt,
            preferredProviderId = if (parameters.containsKey("preferred_provider_id"))
                (newProviderId?.trim()?.ifEmpty { null })
            else existing.preferredProviderId,
            preferredModelId = if (parameters.containsKey("preferred_model_id"))
                (newModelId?.trim()?.ifEmpty { null })
            else existing.preferredModelId,
            updatedAt = System.currentTimeMillis()
        )

        return when (val result = agentRepository.updateAgent(updated)) {
            is AppResult.Success -> ToolResult.success(
                "Agent '${updated.name}' updated successfully. Changed: ${changes.joinToString(", ")}."
            )
            is AppResult.Error -> ToolResult.error("update_failed", "Failed to update agent: ${result.message}")
        }
    }
}
