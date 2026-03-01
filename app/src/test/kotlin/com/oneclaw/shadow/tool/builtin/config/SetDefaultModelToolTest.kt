package com.oneclaw.shadow.tool.builtin.config

import com.oneclaw.shadow.core.model.AiModel
import com.oneclaw.shadow.core.model.ModelSource
import com.oneclaw.shadow.core.model.Provider
import com.oneclaw.shadow.core.model.ProviderType
import com.oneclaw.shadow.core.model.ToolResultStatus
import com.oneclaw.shadow.core.repository.ProviderRepository
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SetDefaultModelToolTest {

    private lateinit var providerRepository: ProviderRepository
    private lateinit var tool: SetDefaultModelTool

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

    private val model = AiModel(
        id = "gpt-4o",
        displayName = "GPT-4o",
        providerId = "provider-1",
        isDefault = false,
        source = ModelSource.DYNAMIC
    )

    @BeforeEach
    fun setup() {
        providerRepository = mockk()
        tool = SetDefaultModelTool(providerRepository)
    }

    @Test
    fun `missing provider_id returns validation error`() = runTest {
        val result = tool.execute(mapOf("model_id" to "gpt-4o"))

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
    fun `provider not found returns not_found error`() = runTest {
        coEvery { providerRepository.getProviderById("nonexistent") } returns null

        val result = tool.execute(mapOf("provider_id" to "nonexistent", "model_id" to "gpt-4o"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("not_found", result.errorType)
    }

    @Test
    fun `model not found for provider returns not_found error`() = runTest {
        coEvery { providerRepository.getProviderById("provider-1") } returns provider
        coEvery { providerRepository.getModelsForProvider("provider-1") } returns emptyList()

        val result = tool.execute(mapOf("provider_id" to "provider-1", "model_id" to "gpt-4o"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("not_found", result.errorType)
        assertTrue(result.errorMessage!!.contains("gpt-4o"))
    }

    @Test
    fun `setting default model succeeds`() = runTest {
        coEvery { providerRepository.getProviderById("provider-1") } returns provider
        coEvery { providerRepository.getModelsForProvider("provider-1") } returns listOf(model)
        coJustRun { providerRepository.setGlobalDefaultModel(modelId = "gpt-4o", providerId = "provider-1") }

        val result = tool.execute(mapOf("provider_id" to "provider-1", "model_id" to "gpt-4o"))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("GPT-4o") || result.result!!.contains("gpt-4o"))
        assertTrue(result.result!!.contains("My OpenAI"))
        coVerify { providerRepository.setGlobalDefaultModel(modelId = "gpt-4o", providerId = "provider-1") }
    }

    @Test
    fun `success message mentions provider name`() = runTest {
        coEvery { providerRepository.getProviderById("provider-1") } returns provider
        coEvery { providerRepository.getModelsForProvider("provider-1") } returns listOf(model)
        coJustRun { providerRepository.setGlobalDefaultModel(any(), any()) }

        val result = tool.execute(mapOf("provider_id" to "provider-1", "model_id" to "gpt-4o"))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("My OpenAI"))
    }

    @Test
    fun `definition has correct tool name`() {
        assertEquals("set_default_model", tool.definition.name)
    }

    @Test
    fun `definition requires provider_id and model_id`() {
        val required = tool.definition.parametersSchema.required
        assertTrue(required.contains("provider_id"))
        assertTrue(required.contains("model_id"))
    }
}
