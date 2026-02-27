package com.oneclaw.shadow.feature.session.usecase

import com.oneclaw.shadow.core.model.AiModel
import com.oneclaw.shadow.core.model.ModelSource
import com.oneclaw.shadow.core.model.Provider
import com.oneclaw.shadow.core.model.ProviderType
import com.oneclaw.shadow.core.repository.ProviderRepository
import com.oneclaw.shadow.core.repository.SessionRepository
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.data.remote.adapter.ModelApiAdapter
import com.oneclaw.shadow.data.remote.adapter.ModelApiAdapterFactory
import com.oneclaw.shadow.data.security.ApiKeyStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GenerateTitleUseCaseTest {

    private lateinit var sessionRepository: SessionRepository
    private lateinit var providerRepository: ProviderRepository
    private lateinit var apiKeyStorage: ApiKeyStorage
    private lateinit var adapterFactory: ModelApiAdapterFactory
    private lateinit var adapter: ModelApiAdapter
    private lateinit var useCase: GenerateTitleUseCase

    private val now = System.currentTimeMillis()

    private val provider = Provider(
        id = "provider-openai",
        name = "OpenAI",
        type = ProviderType.OPENAI,
        apiBaseUrl = "https://api.openai.com/v1",
        isPreConfigured = true,
        isActive = true,
        createdAt = now,
        updatedAt = now
    )

    private val models = listOf(
        AiModel("gpt-4o", "GPT-4o", "provider-openai", false, ModelSource.PRESET),
        AiModel("gpt-4o-mini", "GPT-4o Mini", "provider-openai", false, ModelSource.PRESET)
    )

    @BeforeEach
    fun setup() {
        sessionRepository = mockk(relaxed = true)
        providerRepository = mockk()
        apiKeyStorage = mockk()
        adapterFactory = mockk()
        adapter = mockk()
        useCase = GenerateTitleUseCase(sessionRepository, providerRepository, apiKeyStorage, adapterFactory)
    }

    // --- generateTruncatedTitle ---

    @Test
    fun `generateTruncatedTitle returns message as-is when shorter than 50 chars`() {
        val short = "Hello, how are you?"
        assertEquals(short, useCase.generateTruncatedTitle(short))
    }

    @Test
    fun `generateTruncatedTitle truncates at word boundary`() {
        val message = "This is a relatively long message that exceeds fifty characters limit for title"
        val result = useCase.generateTruncatedTitle(message)
        assert(result.endsWith("..."))
        assert(result.length <= 53) // 50 chars + "..."
    }

    @Test
    fun `generateTruncatedTitle returns exactly 50 chars without ellipsis`() {
        val exactly50 = "a".repeat(50)
        assertEquals(exactly50, useCase.generateTruncatedTitle(exactly50))
    }

    @Test
    fun `generateTruncatedTitle trims leading and trailing whitespace`() {
        val withSpaces = "  Hello world  "
        assertEquals("Hello world", useCase.generateTruncatedTitle(withSpaces))
    }

    // --- generateAiTitle ---

    @Test
    fun `generateAiTitle updates session title on success`() = runTest {
        coEvery { providerRepository.getProviderById("provider-openai") } returns provider
        coEvery { apiKeyStorage.getApiKey("provider-openai") } returns "sk-test"
        coEvery { providerRepository.getModelsForProvider("provider-openai") } returns models
        coEvery { adapterFactory.getAdapter(ProviderType.OPENAI) } returns adapter
        coEvery {
            adapter.generateSimpleCompletion(any(), any(), any(), any(), any())
        } returns AppResult.Success("Session About AI Capabilities")

        useCase.generateAiTitle(
            sessionId = "session-1",
            firstUserMessage = "Tell me about AI",
            firstAiResponse = "AI stands for artificial intelligence...",
            currentModelId = "gpt-4o",
            currentProviderId = "provider-openai"
        )

        coVerify { sessionRepository.setGeneratedTitle("session-1", "Session About AI Capabilities") }
    }

    @Test
    fun `generateAiTitle silently fails when provider not found`() = runTest {
        coEvery { providerRepository.getProviderById("provider-openai") } returns null

        useCase.generateAiTitle(
            sessionId = "session-1",
            firstUserMessage = "Hello",
            firstAiResponse = "Hi there",
            currentModelId = "gpt-4o",
            currentProviderId = "provider-openai"
        )

        coVerify(exactly = 0) { sessionRepository.setGeneratedTitle(any(), any()) }
    }

    @Test
    fun `generateAiTitle silently fails when api key not found`() = runTest {
        coEvery { providerRepository.getProviderById("provider-openai") } returns provider
        coEvery { apiKeyStorage.getApiKey("provider-openai") } returns null

        useCase.generateAiTitle(
            sessionId = "session-1",
            firstUserMessage = "Hello",
            firstAiResponse = "Hi there",
            currentModelId = "gpt-4o",
            currentProviderId = "provider-openai"
        )

        coVerify(exactly = 0) { sessionRepository.setGeneratedTitle(any(), any()) }
    }

    @Test
    fun `generateAiTitle silently fails when adapter returns error`() = runTest {
        coEvery { providerRepository.getProviderById("provider-openai") } returns provider
        coEvery { apiKeyStorage.getApiKey("provider-openai") } returns "sk-test"
        coEvery { providerRepository.getModelsForProvider("provider-openai") } returns models
        coEvery { adapterFactory.getAdapter(ProviderType.OPENAI) } returns adapter
        coEvery {
            adapter.generateSimpleCompletion(any(), any(), any(), any(), any())
        } returns AppResult.Error(message = "API error")

        useCase.generateAiTitle(
            sessionId = "session-1",
            firstUserMessage = "Hello",
            firstAiResponse = "Hi there",
            currentModelId = "gpt-4o",
            currentProviderId = "provider-openai"
        )

        coVerify(exactly = 0) { sessionRepository.setGeneratedTitle(any(), any()) }
    }

    @Test
    fun `generateAiTitle strips surrounding quotes from generated title`() = runTest {
        coEvery { providerRepository.getProviderById("provider-openai") } returns provider
        coEvery { apiKeyStorage.getApiKey("provider-openai") } returns "sk-test"
        coEvery { providerRepository.getModelsForProvider("provider-openai") } returns models
        coEvery { adapterFactory.getAdapter(ProviderType.OPENAI) } returns adapter
        coEvery {
            adapter.generateSimpleCompletion(any(), any(), any(), any(), any())
        } returns AppResult.Success("\"AI Discussion\"")

        useCase.generateAiTitle(
            sessionId = "session-1",
            firstUserMessage = "Tell me about AI",
            firstAiResponse = "Sure!",
            currentModelId = "gpt-4o",
            currentProviderId = "provider-openai"
        )

        coVerify { sessionRepository.setGeneratedTitle("session-1", "AI Discussion") }
    }

    // --- resolveLightweightModel ---

    @Test
    fun `resolveLightweightModel returns lightweight model when it exists`() = runTest {
        coEvery { providerRepository.getModelsForProvider("provider-openai") } returns models

        val result = useCase.resolveLightweightModel("provider-openai", ProviderType.OPENAI, "gpt-4o")

        assertEquals("gpt-4o-mini", result)
    }

    @Test
    fun `resolveLightweightModel falls back to current model when lightweight not in db`() = runTest {
        coEvery { providerRepository.getModelsForProvider("provider-openai") } returns listOf(
            AiModel("gpt-4o", "GPT-4o", "provider-openai", false, ModelSource.PRESET)
        )

        val result = useCase.resolveLightweightModel("provider-openai", ProviderType.OPENAI, "gpt-4o")

        assertEquals("gpt-4o", result)
    }
}
