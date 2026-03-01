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

class UpdateAgentToolTest {

    private lateinit var agentRepository: AgentRepository
    private lateinit var tool: UpdateAgentTool

    private val customAgent = Agent(
        id = "agent-1",
        name = "My Agent",
        description = "A helpful agent",
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
        tool = UpdateAgentTool(agentRepository)
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
    }

    @Test
    fun `built-in agent cannot be modified`() = runTest {
        coEvery { agentRepository.getAgentById("agent-builtin") } returns builtInAgent

        val result = tool.execute(mapOf("agent_id" to "agent-builtin", "name" to "New Name"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("permission_denied", result.errorType)
        assertTrue(result.errorMessage!!.contains("Built-in") || result.errorMessage!!.contains("cannot be modified"))
    }

    @Test
    fun `no changes returns unchanged message`() = runTest {
        coEvery { agentRepository.getAgentById("agent-1") } returns customAgent

        val result = tool.execute(mapOf("agent_id" to "agent-1"))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("No changes") || result.result!!.contains("unchanged"))
    }

    @Test
    fun `empty name returns validation error`() = runTest {
        coEvery { agentRepository.getAgentById("agent-1") } returns customAgent

        val result = tool.execute(mapOf("agent_id" to "agent-1", "name" to "   "))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
    }

    @Test
    fun `name exceeding 100 chars returns validation error`() = runTest {
        coEvery { agentRepository.getAgentById("agent-1") } returns customAgent

        val result = tool.execute(mapOf("agent_id" to "agent-1", "name" to "A".repeat(101)))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
        assertTrue(result.errorMessage!!.contains("100"))
    }

    @Test
    fun `empty system_prompt returns validation error`() = runTest {
        coEvery { agentRepository.getAgentById("agent-1") } returns customAgent

        val result = tool.execute(mapOf("agent_id" to "agent-1", "system_prompt" to "   "))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
        assertTrue(result.errorMessage!!.contains("System prompt") || result.errorMessage!!.contains("system_prompt"))
    }

    @Test
    fun `system_prompt exceeding 50000 chars returns validation error`() = runTest {
        coEvery { agentRepository.getAgentById("agent-1") } returns customAgent

        val result = tool.execute(mapOf("agent_id" to "agent-1", "system_prompt" to "A".repeat(50_001)))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
        assertTrue(result.errorMessage!!.contains("50,000"))
    }

    @Test
    fun `successful partial update returns success with changed fields`() = runTest {
        coEvery { agentRepository.getAgentById("agent-1") } returns customAgent
        coEvery { agentRepository.updateAgent(any()) } returns AppResult.Success(Unit)

        val result = tool.execute(mapOf("agent_id" to "agent-1", "name" to "Updated Agent"))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("Updated Agent"))
        assertTrue(result.result!!.contains("name"))
    }

    @Test
    fun `repository error returns update_failed error`() = runTest {
        coEvery { agentRepository.getAgentById("agent-1") } returns customAgent
        coEvery { agentRepository.updateAgent(any()) } returns AppResult.Error(message = "DB error")

        val result = tool.execute(mapOf("agent_id" to "agent-1", "name" to "Updated Agent"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("update_failed", result.errorType)
        assertTrue(result.errorMessage!!.contains("DB error"))
    }

    @Test
    fun `clearing preferred provider with empty string works`() = runTest {
        val agentWithProvider = customAgent.copy(preferredProviderId = "provider-1")
        coEvery { agentRepository.getAgentById("agent-1") } returns agentWithProvider
        coEvery { agentRepository.updateAgent(any()) } returns AppResult.Success(Unit)

        val result = tool.execute(mapOf("agent_id" to "agent-1", "preferred_provider_id" to ""))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
    }

    @Test
    fun `definition has correct tool name`() {
        assertEquals("update_agent", tool.definition.name)
    }

    @Test
    fun `definition requires agent_id`() {
        val required = tool.definition.parametersSchema.required
        assertEquals(listOf("agent_id"), required)
    }
}
