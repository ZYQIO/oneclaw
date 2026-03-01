package com.oneclaw.shadow.tool.builtin.config

import com.oneclaw.shadow.core.model.AiModel
import com.oneclaw.shadow.core.model.ModelSource
import com.oneclaw.shadow.core.model.Provider
import com.oneclaw.shadow.core.model.ProviderType
import com.oneclaw.shadow.core.model.ToolResultStatus
import com.oneclaw.shadow.core.repository.ProviderRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ListModelsToolTest {

    private lateinit var providerRepository: ProviderRepository
    private lateinit var tool: ListModelsTool

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

    private val model1 = AiModel(
        id = "gpt-4o",
        displayName = "GPT-4o",
        providerId = "provider-1",
        isDefault = true,
        source = ModelSource.DYNAMIC,
        contextWindowSize = 128000
    )

    private val model2 = AiModel(
        id = "gpt-3.5-turbo",
        displayName = null,
        providerId = "provider-1",
        isDefault = false,
        source = ModelSource.MANUAL
    )

    @BeforeEach
    fun setup() {
        providerRepository = mockk()
        tool = ListModelsTool(providerRepository)
    }

    @Test
    fun `missing provider_id returns validation error`() = runTest {
        val result = tool.execute(emptyMap())

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("validation_error", result.errorType)
        assertTrue(result.errorMessage!!.contains("provider_id"))
    }

    @Test
    fun `provider not found returns not_found error`() = runTest {
        coEvery { providerRepository.getProviderById("nonexistent") } returns null

        val result = tool.execute(mapOf("provider_id" to "nonexistent"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("not_found", result.errorType)
    }

    @Test
    fun `empty model list returns no models message with suggestions`() = runTest {
        coEvery { providerRepository.getProviderById("provider-1") } returns provider
        coEvery { providerRepository.getModelsForProvider("provider-1") } returns emptyList()

        val result = tool.execute(mapOf("provider_id" to "provider-1"))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("No models found"))
        assertTrue(result.result!!.contains("fetch_models") || result.result!!.contains("add_model"))
    }

    @Test
    fun `models are listed with all details`() = runTest {
        coEvery { providerRepository.getProviderById("provider-1") } returns provider
        coEvery { providerRepository.getModelsForProvider("provider-1") } returns listOf(model1)

        val result = tool.execute(mapOf("provider_id" to "provider-1"))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("gpt-4o"))
        assertTrue(result.result!!.contains("GPT-4o"))
        assertTrue(result.result!!.contains("DYNAMIC"))
        assertTrue(result.result!!.contains("128000"))
    }

    @Test
    fun `model without display name shows model id`() = runTest {
        coEvery { providerRepository.getProviderById("provider-1") } returns provider
        coEvery { providerRepository.getModelsForProvider("provider-1") } returns listOf(model2)

        val result = tool.execute(mapOf("provider_id" to "provider-1"))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("gpt-3.5-turbo"))
    }

    @Test
    fun `multiple models show count`() = runTest {
        coEvery { providerRepository.getProviderById("provider-1") } returns provider
        coEvery { providerRepository.getModelsForProvider("provider-1") } returns listOf(model1, model2)

        val result = tool.execute(mapOf("provider_id" to "provider-1"))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("2"))
    }

    @Test
    fun `definition has correct tool name`() {
        assertEquals("list_models", tool.definition.name)
    }

    @Test
    fun `definition requires provider_id`() {
        assertTrue(tool.definition.parametersSchema.required.contains("provider_id"))
    }
}
