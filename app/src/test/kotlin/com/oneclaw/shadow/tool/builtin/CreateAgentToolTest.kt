package com.oneclaw.shadow.tool.builtin

import com.oneclaw.shadow.core.model.Agent
import com.oneclaw.shadow.core.model.ToolResultStatus
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.core.util.ErrorCode
import com.oneclaw.shadow.feature.agent.usecase.CreateAgentUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CreateAgentToolTest {

    private lateinit var createAgentUseCase: CreateAgentUseCase
    private lateinit var tool: CreateAgentTool

    @BeforeEach
    fun setup() {
        createAgentUseCase = mockk()
        tool = CreateAgentTool(createAgentUseCase)
    }

    private fun baseParams(overrides: Map<String, Any?> = emptyMap()): Map<String, Any?> {
        val defaults: Map<String, Any?> = mapOf(
            "name" to "Python Debug Helper",
            "system_prompt" to "You are an expert Python developer who helps debug code."
        )
        return defaults + overrides
    }

    private fun createdAgent(name: String = "Python Debug Helper") = Agent(
        id = "agent-123",
        name = name,
        description = null,
        systemPrompt = "You are an expert Python developer who helps debug code.",
        preferredProviderId = null,
        preferredModelId = null,
        isBuiltIn = false,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    @Test
    fun `missing name returns validation error`() = runTest {
        val result = tool.execute(baseParams(mapOf("name" to null)))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
        assertTrue(result.errorMessage!!.contains("name"))
    }

    @Test
    fun `empty name returns validation error`() = runTest {
        val result = tool.execute(baseParams(mapOf("name" to "   ")))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
        assertTrue(result.errorMessage!!.contains("name"))
    }

    @Test
    fun `name exceeding 100 chars returns validation error`() = runTest {
        val longName = "A".repeat(101)
        val result = tool.execute(baseParams(mapOf("name" to longName)))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
        assertTrue(result.errorMessage!!.contains("100"))
    }

    @Test
    fun `missing system_prompt returns validation error`() = runTest {
        val result = tool.execute(baseParams(mapOf("system_prompt" to null)))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
        assertTrue(result.errorMessage!!.contains("system_prompt"))
    }

    @Test
    fun `empty system_prompt returns validation error`() = runTest {
        val result = tool.execute(baseParams(mapOf("system_prompt" to "   ")))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
        assertTrue(result.errorMessage!!.contains("system_prompt"))
    }

    @Test
    fun `system_prompt exceeding 50000 chars returns validation error`() = runTest {
        val longPrompt = "A".repeat(50_001)
        val result = tool.execute(baseParams(mapOf("system_prompt" to longPrompt)))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
        assertTrue(result.errorMessage!!.contains("50,000"))
    }

    @Test
    fun `successful agent creation returns success message`() = runTest {
        val agent = createdAgent()
        coEvery { createAgentUseCase(any(), any(), any(), any(), any()) } returns AppResult.Success(agent)

        val result = tool.execute(baseParams())

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("Python Debug Helper"))
        assertTrue(result.result!!.contains("agent-123"))
    }

    @Test
    fun `optional description is passed when provided`() = runTest {
        val agent = createdAgent()
        coEvery { createAgentUseCase(any(), any(), any(), any(), any()) } returns AppResult.Success(agent)

        val result = tool.execute(baseParams(mapOf("description" to "Helps with Python debugging")))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
    }

    @Test
    fun `use case returning error propagates as tool error`() = runTest {
        coEvery { createAgentUseCase(any(), any(), any(), any(), any()) } returns AppResult.Error(
            message = "Agent name already exists.",
            code = ErrorCode.VALIDATION_ERROR
        )

        val result = tool.execute(baseParams())

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("creation_failed", result.errorType)
        assertTrue(result.errorMessage!!.contains("already exists"))
    }
}
