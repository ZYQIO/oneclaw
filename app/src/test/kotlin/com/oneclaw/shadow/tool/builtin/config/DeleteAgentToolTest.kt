package com.oneclaw.shadow.tool.builtin.config

import com.oneclaw.shadow.core.model.Agent
import com.oneclaw.shadow.core.model.ToolResultStatus
import com.oneclaw.shadow.core.repository.AgentRepository
import com.oneclaw.shadow.core.util.AppResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeleteAgentToolTest {

    private lateinit var agentRepository: AgentRepository
    private lateinit var tool: DeleteAgentTool

    private val customAgent = Agent(
        id = "agent-1",
        name = "My Agent",
        description = null,
        systemPrompt = "You are a helpful assistant.",
        preferredProviderId = null,
        preferredModelId = null,
        isBuiltIn = false,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    private val builtInAgent = customAgent.copy(
        id = "agent-builtin",
        name = "Default",
        isBuiltIn = true
    )

    @BeforeEach
    fun setup() {
        agentRepository = mockk()
        tool = DeleteAgentTool(agentRepository)
    }

    @Test
    fun `missing agent_id returns validation error`() = runTest {
        val result = tool.execute(emptyMap())

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
        assertTrue(result.errorMessage!!.contains("agent_id"))
    }

    @Test
    fun `agent not found returns not_found error`() = runTest {
        coEvery { agentRepository.getAgentById("nonexistent") } returns null

        val result = tool.execute(mapOf("agent_id" to "nonexistent"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("not_found", result.errorType)
        assertTrue(result.errorMessage!!.contains("nonexistent"))
    }

    @Test
    fun `built-in agent cannot be deleted`() = runTest {
        coEvery { agentRepository.getAgentById("agent-builtin") } returns builtInAgent

        val result = tool.execute(mapOf("agent_id" to "agent-builtin"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("permission_denied", result.errorType)
        assertTrue(result.errorMessage!!.contains("Built-in") || result.errorMessage!!.contains("cannot be deleted"))
    }

    @Test
    fun `custom agent is deleted successfully`() = runTest {
        coEvery { agentRepository.getAgentById("agent-1") } returns customAgent
        coEvery { agentRepository.deleteAgent("agent-1") } returns AppResult.Success(Unit)

        val result = tool.execute(mapOf("agent_id" to "agent-1"))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("My Agent"))
        assertTrue(result.result!!.contains("deleted"))
    }

    @Test
    fun `repository error returns deletion_failed error`() = runTest {
        coEvery { agentRepository.getAgentById("agent-1") } returns customAgent
        coEvery { agentRepository.deleteAgent("agent-1") } returns AppResult.Error(
            message = "Database constraint error"
        )

        val result = tool.execute(mapOf("agent_id" to "agent-1"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("deletion_failed", result.errorType)
        assertTrue(result.errorMessage!!.contains("Database constraint error"))
    }

    @Test
    fun `definition has correct tool name`() {
        assertEquals("delete_agent", tool.definition.name)
    }

    @Test
    fun `definition requires agent_id`() {
        val required = tool.definition.parametersSchema.required
        assertTrue(required.contains("agent_id"))
    }
}
