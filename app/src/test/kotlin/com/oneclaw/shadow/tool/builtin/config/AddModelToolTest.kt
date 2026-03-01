package com.oneclaw.shadow.tool.builtin.config

import com.oneclaw.shadow.core.model.Provider
import com.oneclaw.shadow.core.model.ProviderType
import com.oneclaw.shadow.core.model.ToolResultStatus
import com.oneclaw.shadow.core.repository.ProviderRepository
import com.oneclaw.shadow.core.util.AppResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AddModelToolTest {

    private lateinit var providerRepository: ProviderRepository
    private lateinit var tool: AddModelTool

    private val provider = Provider(
        id = "provider-1",
        name = "My OpenAI",
        type = ProviderType.OPENAI,
        apiBaseUrl = "https://api.openai.com/v1",
        isPreConfigured = false,
        isActive = true,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    @BeforeEach
    fun setup() {
        providerRepository = mockk()
        tool = AddModelTool(providerRepository)
    }

    @Test
    fun `missing provider_id returns validation error`() = runTest {
        val result = tool.execute(mapOf("model_id" to "gpt-4-turbo"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
        assertTrue(result.errorMessage!!.contains("provider_id"))
    }

    @Test
    fun `missing model_id returns validation error`() = runTest {
        val result = tool.execute(mapOf("provider_id" to "provider-1"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
        assertTrue(result.errorMessage!!.contains("model_id"))
    }

    @Test
    fun `empty model_id returns validation error`() = runTest {
        val result = tool.execute(mapOf("provider_id" to "provider-1", "model_id" to "   "))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
    }

    @Test
    fun `provider not found returns not_found error`() = runTest {
        coEvery { providerRepository.getProviderById("nonexistent") } returns null

        val result = tool.execute(mapOf("provider_id" to "nonexistent", "model_id" to "gpt-4-turbo"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("not_found", result.errorType)
    }

    @Test
    fun `model added successfully without display name`() = runTest {
        coEvery { providerRepository.getProviderById("provider-1") } returns provider
        coEvery { providerRepository.addManualModel("provider-1", "gpt-4-turbo", null) } returns AppResult.Success(Unit)

        val result = tool.execute(mapOf("provider_id" to "provider-1", "model_id" to "gpt-4-turbo"))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("gpt-4-turbo"))
        assertTrue(result.result!!.contains("My OpenAI"))
    }

    @Test
    fun `model added successfully with display name`() = runTest {
        coEvery { providerRepository.getProviderById("provider-1") } returns provider
        coEvery { providerRepository.addManualModel("provider-1", "gpt-4-turbo", "GPT-4 Turbo") } returns AppResult.Success(Unit)

        val result = tool.execute(mapOf(
            "provider_id" to "provider-1",
            "model_id" to "gpt-4-turbo",
            "display_name" to "GPT-4 Turbo"
        ))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("GPT-4 Turbo"))
    }

    @Test
    fun `repository error returns add_failed error`() = runTest {
        coEvery { providerRepository.getProviderById("provider-1") } returns provider
        coEvery { providerRepository.addManualModel(any(), any(), any()) } returns AppResult.Error(
            message = "Model already exists"
        )

        val result = tool.execute(mapOf("provider_id" to "provider-1", "model_id" to "gpt-4-turbo"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("add_failed", result.errorType)
        assertTrue(result.errorMessage!!.contains("already exists"))
    }

    @Test
    fun `definition has correct tool name`() {
        assertEquals("add_model", tool.definition.name)
    }

    @Test
    fun `definition requires provider_id and model_id`() {
        val required = tool.definition.parametersSchema.required
        assertTrue(required.contains("provider_id"))
        assertTrue(required.contains("model_id"))
    }
}
