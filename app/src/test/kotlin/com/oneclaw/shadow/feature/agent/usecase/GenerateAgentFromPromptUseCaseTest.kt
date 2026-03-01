package com.oneclaw.shadow.feature.agent.usecase

import com.oneclaw.shadow.core.model.AiModel
import com.oneclaw.shadow.core.model.ModelSource
import com.oneclaw.shadow.core.model.Provider
import com.oneclaw.shadow.core.model.ProviderType
import com.oneclaw.shadow.core.repository.ProviderRepository
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.data.remote.adapter.ModelApiAdapter
import com.oneclaw.shadow.data.remote.adapter.ModelApiAdapterFactory
import com.oneclaw.shadow.data.security.ApiKeyStorage
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GenerateAgentFromPromptUseCaseTest {

    private lateinit var providerRepository: ProviderRepository
    private lateinit var apiKeyStorage: ApiKeyStorage
    private lateinit var adapterFactory: ModelApiAdapterFactory
    private lateinit var adapter: ModelApiAdapter
    private lateinit var useCase: GenerateAgentFromPromptUseCase

    private val testModel = AiModel(
        id = "gpt-4o",
        displayName = "GPT-4o",
        providerId = "provider-openai",
        isDefault = true,
        source = ModelSource.PRESET
    )

    private val testProvider = Provider(
        id = "provider-openai",
        name = "OpenAI",
        type = ProviderType.OPENAI,
        apiBaseUrl = "https://api.openai.com/v1",
        isPreConfigured = true,
        isActive = true,
        createdAt = 0L,
        updatedAt = 0L
    )

    @BeforeEach
    fun setup() {
        providerRepository = mockk()
        apiKeyStorage = mockk()
        adapterFactory = mockk()
        adapter = mockk()
        useCase = GenerateAgentFromPromptUseCase(providerRepository, apiKeyStorage, adapterFactory)

        every { adapterFactory.getAdapter(ProviderType.OPENAI) } returns adapter
    }

    @Test
    fun `returns error when no default model configured`() = runTest {
        every { providerRepository.getGlobalDefaultModel() } returns flowOf(null)

        val result = useCase("A Python debugging assistant")

        assertTrue(result is AppResult.Error)
        assertTrue((result as AppResult.Error).message.contains("No default model"))
    }

    @Test
    fun `returns error when provider not found`() = runTest {
        every { providerRepository.getGlobalDefaultModel() } returns flowOf(testModel)
        coEvery { providerRepository.getProviderById("provider-openai") } returns null

        val result = useCase("A Python debugging assistant")

        assertTrue(result is AppResult.Error)
        assertTrue((result as AppResult.Error).message.contains("Provider not found"))
    }

    @Test
    fun `returns error when API key not configured`() = runTest {
        every { providerRepository.getGlobalDefaultModel() } returns flowOf(testModel)
        coEvery { providerRepository.getProviderById("provider-openai") } returns testProvider
        every { apiKeyStorage.getApiKey("provider-openai") } returns null

        val result = useCase("A Python debugging assistant")

        assertTrue(result is AppResult.Error)
        assertTrue((result as AppResult.Error).message.contains("API key not configured"))
    }

    @Test
    fun `returns error when API call fails`() = runTest {
        every { providerRepository.getGlobalDefaultModel() } returns flowOf(testModel)
        coEvery { providerRepository.getProviderById("provider-openai") } returns testProvider
        every { apiKeyStorage.getApiKey("provider-openai") } returns "sk-test-key"
        coEvery {
            adapter.generateSimpleCompletion(any(), any(), any(), any(), any())
        } returns AppResult.Error(message = "Network error")

        val result = useCase("A Python debugging assistant")

        assertTrue(result is AppResult.Error)
        assertTrue((result as AppResult.Error).message.contains("Network error"))
    }

    @Test
    fun `returns error when response contains malformed JSON`() = runTest {
        every { providerRepository.getGlobalDefaultModel() } returns flowOf(testModel)
        coEvery { providerRepository.getProviderById("provider-openai") } returns testProvider
        every { apiKeyStorage.getApiKey("provider-openai") } returns "sk-test-key"
        coEvery {
            adapter.generateSimpleCompletion(any(), any(), any(), any(), any())
        } returns AppResult.Success("This is not JSON at all")

        val result = useCase("A Python debugging assistant")

        assertTrue(result is AppResult.Error)
        assertTrue((result as AppResult.Error).message.contains("Failed to parse"))
    }

    @Test
    fun `successfully parses plain JSON response`() = runTest {
        val jsonResponse = """{"name":"Python Debug Helper","description":"Helps debug Python code","systemPrompt":"You are an expert Python developer..."}"""
        every { providerRepository.getGlobalDefaultModel() } returns flowOf(testModel)
        coEvery { providerRepository.getProviderById("provider-openai") } returns testProvider
        every { apiKeyStorage.getApiKey("provider-openai") } returns "sk-test-key"
        coEvery {
            adapter.generateSimpleCompletion(any(), any(), any(), any(), any())
        } returns AppResult.Success(jsonResponse)

        val result = useCase("A Python debugging assistant")

        assertTrue(result is AppResult.Success)
        val generated = (result as AppResult.Success).data
        assertEquals("Python Debug Helper", generated.name)
        assertEquals("Helps debug Python code", generated.description)
        assertEquals("You are an expert Python developer...", generated.systemPrompt)
    }

    @Test
    fun `successfully parses JSON wrapped in markdown code block`() = runTest {
        val jsonResponse = """
            ```json
            {"name":"Python Debug Helper","description":"Helps debug Python code","systemPrompt":"You are an expert Python developer..."}
            ```
        """.trimIndent()
        every { providerRepository.getGlobalDefaultModel() } returns flowOf(testModel)
        coEvery { providerRepository.getProviderById("provider-openai") } returns testProvider
        every { apiKeyStorage.getApiKey("provider-openai") } returns "sk-test-key"
        coEvery {
            adapter.generateSimpleCompletion(any(), any(), any(), any(), any())
        } returns AppResult.Success(jsonResponse)

        val result = useCase("A Python debugging assistant")

        assertTrue(result is AppResult.Success)
        val generated = (result as AppResult.Success).data
        assertEquals("Python Debug Helper", generated.name)
    }

    @Test
    fun `successfully parses JSON wrapped in generic code block`() = runTest {
        val jsonResponse = """
            ```
            {"name":"Code Reviewer","description":"Reviews code quality","systemPrompt":"You review code for quality..."}
            ```
        """.trimIndent()
        every { providerRepository.getGlobalDefaultModel() } returns flowOf(testModel)
        coEvery { providerRepository.getProviderById("provider-openai") } returns testProvider
        every { apiKeyStorage.getApiKey("provider-openai") } returns "sk-test-key"
        coEvery {
            adapter.generateSimpleCompletion(any(), any(), any(), any(), any())
        } returns AppResult.Success(jsonResponse)

        val result = useCase("A code review assistant")

        assertTrue(result is AppResult.Success)
        val generated = (result as AppResult.Success).data
        assertEquals("Code Reviewer", generated.name)
    }
}
