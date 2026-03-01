package com.oneclaw.shadow.tool.builtin.config

import com.oneclaw.shadow.core.model.ToolDefinition
import com.oneclaw.shadow.core.model.ToolParametersSchema
import com.oneclaw.shadow.core.model.ToolResult
import com.oneclaw.shadow.core.repository.AgentRepository
import com.oneclaw.shadow.tool.engine.Tool
import kotlinx.coroutines.flow.first

class ListAgentsTool(
    private val agentRepository: AgentRepository
) : Tool {

    override val definition = ToolDefinition(
        name = "list_agents",
        description = "List all configured AI agents with their details including " +
            "ID, name, description, whether it is built-in, and preferred provider/model.",
        parametersSchema = ToolParametersSchema(
            properties = emptyMap(),
            required = emptyList()
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 10
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        val agents = agentRepository.getAllAgents().first()
        if (agents.isEmpty()) {
            return ToolResult.success("No agents configured.")
        }

        val sb = StringBuilder("Found ${agents.size} agent(s):\n")
        agents.forEachIndexed { index, agent ->
            sb.append("\n${index + 1}. [id: ${agent.id}] ${agent.name}")
            if (agent.isBuiltIn) sb.append(" (built-in)")
            if (!agent.description.isNullOrBlank()) {
                sb.append("\n   Description: ${agent.description}")
            }
            sb.append("\n   System Prompt: ${agent.systemPrompt.take(100)}${if (agent.systemPrompt.length > 100) "..." else ""}")
            if (agent.preferredProviderId != null) {
                sb.append("\n   Preferred Provider ID: ${agent.preferredProviderId}")
            }
            if (agent.preferredModelId != null) {
                sb.append("\n   Preferred Model ID: ${agent.preferredModelId}")
            }
            sb.append("\n")
        }
        return ToolResult.success(sb.toString())
    }
}
