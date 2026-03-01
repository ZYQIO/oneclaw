package com.oneclaw.shadow.tool.builtin.config

import com.oneclaw.shadow.core.model.ToolDefinition
import com.oneclaw.shadow.core.model.ToolParameter
import com.oneclaw.shadow.core.model.ToolParametersSchema
import com.oneclaw.shadow.core.model.ToolResult
import com.oneclaw.shadow.core.repository.AgentRepository
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.tool.engine.Tool

class DeleteAgentTool(
    private val agentRepository: AgentRepository
) : Tool {

    override val definition = ToolDefinition(
        name = "delete_agent",
        description = "Delete a custom agent. Built-in agents cannot be deleted.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "agent_id" to ToolParameter(
                    type = "string",
                    description = "ID of the agent to delete"
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
                "Built-in agent '${existing.name}' cannot be deleted."
            )
        }

        return when (val result = agentRepository.deleteAgent(agentId)) {
            is AppResult.Success -> ToolResult.success(
                "Agent '${existing.name}' has been deleted."
            )
            is AppResult.Error -> ToolResult.error("deletion_failed", "Failed to delete agent: ${result.message}")
        }
    }
}
