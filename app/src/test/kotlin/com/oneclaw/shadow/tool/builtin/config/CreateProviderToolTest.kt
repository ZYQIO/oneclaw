package com.oneclaw.shadow.tool.builtin.config

import com.oneclaw.shadow.core.model.ToolResultStatus
import com.oneclaw.shadow.core.repository.ProviderRepository
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CreateProviderToolTest {

    private lateinit var providerRepository: ProviderRepository
    private lateinit var tool: CreateProviderTool

    @BeforeEach
    fun setup() {
        providerRepository = mockk()
        tool = CreateProviderTool(providerRepository)
    }

    private fun baseParams(overrides: Map<String, Any?> = emptyMap()): Map<String, Any?> {
        val defaults: Map<String, Any?> = mapOf(
            "name" to "My OpenAI",
            "type" to "OPENAI",
            "api_base_url" to "https://api.openai.com/v1"
        )
        return defaults + overrides
    }

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
    }

    @Test
    fun `invalid type returns validation error`() = runTest {
        val result = tool.execute(baseParams(mapOf("type" to "CUSTOM")))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
        assertTrue(result.errorMessage!!.contains("OPENAI") || result.errorMessage!!.contains("type"))
    }

    @Test
    fun `missing type returns validation error`() = runTest {
        val result = tool.execute(baseParams(mapOf("type" to null)))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
    }

    @Test
    fun `missing api_base_url returns validation error`() = runTest {
        val result = tool.execute(baseParams(mapOf("api_base_url" to null)))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
        assertTrue(result.errorMessage!!.contains("api_base_url"))
    }

    @Test
    fun `empty api_base_url returns validation error`() = runTest {
        val result = tool.execute(baseParams(mapOf("api_base_url" to "   ")))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
    }

    @Test
    fun `valid OPENAI provider is created successfully`() = runTest {
        coJustRun { providerRepository.createProvider(any()) }

        val result = tool.execute(baseParams())

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("My OpenAI"))
        assertTrue(result.result!!.contains("created successfully"))
        coVerify { providerRepository.createProvider(any()) }
    }

    @Test
    fun `valid ANTHROPIC provider is created successfully`() = runTest {
        coJustRun { providerRepository.createProvider(any()) }

        val result = tool.execute(baseParams(mapOf("type" to "ANTHROPIC")))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
    }

    @Test
    fun `valid GEMINI provider is created successfully`() = runTest {
        coJustRun { providerRepository.createProvider(any()) }

        val result = tool.execute(baseParams(mapOf("type" to "GEMINI")))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
    }

    @Test
    fun `type is case-insensitive`() = runTest {
        coJustRun { providerRepository.createProvider(any()) }

        val result = tool.execute(baseParams(mapOf("type" to "openai")))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
    }

    @Test
    fun `success result includes API key reminder`() = runTest {
        coJustRun { providerRepository.createProvider(any()) }

        val result = tool.execute(baseParams())

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("API key") || result.result!!.contains("Settings"))
    }

    @Test
    fun `definition has correct tool name`() {
        assertEquals("create_provider", tool.definition.name)
    }

    @Test
    fun `definition requires name, type, and api_base_url`() {
        val required = tool.definition.parametersSchema.required
        assertTrue(required.contains("name"))
        assertTrue(required.contains("type"))
        assertTrue(required.contains("api_base_url"))
    }
}
