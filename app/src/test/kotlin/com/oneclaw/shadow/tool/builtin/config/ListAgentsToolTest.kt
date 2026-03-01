package com.oneclaw.shadow.tool.builtin.config

import com.oneclaw.shadow.core.model.Agent
import com.oneclaw.shadow.core.model.ToolResultStatus
import com.oneclaw.shadow.core.repository.AgentRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ListAgentsToolTest {

    private lateinit var agentRepository: AgentRepository
    private lateinit var tool: ListAgentsTool

    private val customAgent = Agent(
        id = "agent-1",
        name = "My Agent",
        description = "A helpful agent",
        systemPrompt = "You are a helpful assistant.",
        preferredProviderId = "provider-1",
        preferredModelId = "gpt-4o",
        isBuiltIn = false,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    private val builtInAgent = Agent(
        id = "agent-builtin",
        name = "Default",
        description = null,
        systemPrompt = "You are the default assistant.",
        preferredProviderId = null,
        preferredModelId = null,
        isBuiltIn = true,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    @BeforeEach
    fun setup() {
        agentRepository = mockk()
        tool = ListAgentsTool(agentRepository)
    }

    @Test
    fun `empty agents list returns no agents message`() = runTest {
        coEvery { agentRepository.getAllAgents() } returns flowOf(emptyList())

        val result = tool.execute(emptyMap())

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("No agents"))
    }

    @Test
    fun `agents are listed with details`() = runTest {
        coEvery { agentRepository.getAllAgents() } returns flowOf(listOf(customAgent))

        val result = tool.execute(emptyMap())

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("agent-1"))
        assertTrue(result.result!!.contains("My Agent"))
        assertTrue(result.result!!.contains("A helpful agent"))
    }

    @Test
    fun `built-in agent shows built-in flag`() = runTest {
        coEvery { agentRepository.getAllAgents() } returns flowOf(listOf(builtInAgent))

        val result = tool.execute(emptyMap())

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("built-in"))
    }

    @Test
    fun `preferred provider and model are shown`() = runTest {
        coEvery { agentRepository.getAllAgents() } returns flowOf(listOf(customAgent))

        val result = tool.execute(emptyMap())

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("provider-1"))
        assertTrue(result.result!!.contains("gpt-4o"))
    }

    @Test
    fun `multiple agents show count`() = runTest {
        coEvery { agentRepository.getAllAgents() } returns flowOf(listOf(customAgent, builtInAgent))

        val result = tool.execute(emptyMap())

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("2"))
    }

    @Test
    fun `system prompt is truncated if long`() = runTest {
        val agentWithLongPrompt = customAgent.copy(
            systemPrompt = "A".repeat(200)
        )
        coEvery { agentRepository.getAllAgents() } returns flowOf(listOf(agentWithLongPrompt))

        val result = tool.execute(emptyMap())

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        // Should contain truncation indicator
        assertTrue(result.result!!.contains("...") || result.result!!.length < 500)
    }

    @Test
    fun `definition has correct tool name`() {
        assertEquals("list_agents", tool.definition.name)
    }

    @Test
    fun `definition has no required parameters`() {
        assertTrue(tool.definition.parametersSchema.required.isEmpty())
    }
}
