package com.oneclaw.shadow.tool.builtin.config

import com.oneclaw.shadow.core.model.AiModel
import com.oneclaw.shadow.core.model.ModelSource
import com.oneclaw.shadow.core.model.Provider
import com.oneclaw.shadow.core.model.ProviderType
import com.oneclaw.shadow.core.model.ToolResultStatus
import com.oneclaw.shadow.core.repository.ProviderRepository
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.data.security.ApiKeyStorage
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FetchModelsToolTest {

    private lateinit var providerRepository: ProviderRepository
    private lateinit var apiKeyStorage: ApiKeyStorage
    private lateinit var tool: FetchModelsTool

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
        apiKeyStorage = mockk()
        tool = FetchModelsTool(providerRepository, apiKeyStorage)
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
    fun `no api key returns api_key_required error`() = runTest {
        coEvery { providerRepository.getProviderById("provider-1") } returns provider
        every { apiKeyStorage.hasApiKey("provider-1") } returns false

        val result = tool.execute(mapOf("provider_id" to "provider-1"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("api_key_required", result.errorType)
        assertTrue(result.errorMessage!!.contains("API key"))
    }

    @Test
    fun `successful fetch returns model list`() = runTest {
        val models = listOf(
            AiModel("gpt-4o", "GPT-4o", "provider-1", false, ModelSource.DYNAMIC),
            AiModel("gpt-3.5-turbo", "GPT-3.5 Turbo", "provider-1", false, ModelSource.DYNAMIC)
        )
        coEvery { providerRepository.getProviderById("provider-1") } returns provider
        every { apiKeyStorage.hasApiKey("provider-1") } returns true
        coEvery { providerRepository.fetchModelsFromApi("provider-1") } returns AppResult.Success(models)

        val result = tool.execute(mapOf("provider_id" to "provider-1"))

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("2"))
        assertTrue(result.result!!.contains("gpt-4o") || result.result!!.contains("GPT-4o"))
    }

    @Test
    fun `api error returns fetch_failed error`() = runTest {
        coEvery { providerRepository.getProviderById("provider-1") } returns provider
        every { apiKeyStorage.hasApiKey("provider-1") } returns true
        coEvery { providerRepository.fetchModelsFromApi("provider-1") } returns AppResult.Error(
            message = "Unauthorized"
        )

        val result = tool.execute(mapOf("provider_id" to "provider-1"))

        assertEquals(ToolResultStatus.ERROR, result.status)
        assertEquals("fetch_failed", result.errorType)
        assertTrue(result.errorMessage!!.contains("Unauthorized"))
    }

    @Test
    fun `definition has correct tool name`() {
        assertEquals("fetch_models", tool.definition.name)
    }

    @Test
    fun `definition requires provider_id`() {
        assertTrue(tool.definition.parametersSchema.required.contains("provider_id"))
    }

    @Test
    fun `definition has longer timeout for API call`() {
        assertTrue(tool.definition.timeoutSeconds >= 30)
    }
}
