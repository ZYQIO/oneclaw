package com.oneclaw.shadow.tool.builtin.config

import com.oneclaw.shadow.core.model.Provider
import com.oneclaw.shadow.core.model.ProviderType
import com.oneclaw.shadow.core.model.ToolResultStatus
import com.oneclaw.shadow.core.repository.ProviderRepository
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

class ListProvidersToolTest {

    private lateinit var providerRepository: ProviderRepository
    private lateinit var apiKeyStorage: ApiKeyStorage
    private lateinit var tool: ListProvidersTool

    private val sampleProvider = Provider(
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
        tool = ListProvidersTool(providerRepository, apiKeyStorage)
    }

    @Test
    fun `empty providers list returns no providers message`() = runTest {
        coEvery { providerRepository.getAllProviders() } returns flowOf(emptyList())

        val result = tool.execute(emptyMap())

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("No providers configured"))
    }

    @Test
    fun `single provider is listed with all details`() = runTest {
        coEvery { providerRepository.getAllProviders() } returns flowOf(listOf(sampleProvider))
        every { apiKeyStorage.hasApiKey("provider-1") } returns true

        val result = tool.execute(emptyMap())

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("provider-1"))
        assertTrue(result.result!!.contains("My OpenAI"))
        assertTrue(result.result!!.contains("OPENAI"))
        assertTrue(result.result!!.contains("https://api.openai.com/v1"))
        assertTrue(result.result!!.contains("configured"))
    }

    @Test
    fun `provider without api key shows NOT SET`() = runTest {
        coEvery { providerRepository.getAllProviders() } returns flowOf(listOf(sampleProvider))
        every { apiKeyStorage.hasApiKey("provider-1") } returns false

        val result = tool.execute(emptyMap())

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("NOT SET"))
    }

    @Test
    fun `multiple providers are all listed`() = runTest {
        val provider2 = sampleProvider.copy(
            id = "provider-2",
            name = "Anthropic",
            type = ProviderType.ANTHROPIC
        )
        coEvery { providerRepository.getAllProviders() } returns flowOf(listOf(sampleProvider, provider2))
        every { apiKeyStorage.hasApiKey(any()) } returns false

        val result = tool.execute(emptyMap())

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("2 provider(s)"))
        assertTrue(result.result!!.contains("My OpenAI"))
        assertTrue(result.result!!.contains("Anthropic"))
    }

    @Test
    fun `pre-configured provider shows pre-configured flag`() = runTest {
        val preConfigured = sampleProvider.copy(isPreConfigured = true)
        coEvery { providerRepository.getAllProviders() } returns flowOf(listOf(preConfigured))
        every { apiKeyStorage.hasApiKey(any()) } returns false

        val result = tool.execute(emptyMap())

        assertEquals(ToolResultStatus.SUCCESS, result.status)
        assertTrue(result.result!!.contains("true"))
    }

    @Test
    fun `definition has correct tool name`() {
        assertEquals("list_providers", tool.definition.name)
    }

    @Test
    fun `definition has no required parameters`() {
        assertTrue(tool.definition.parametersSchema.required.isEmpty())
    }
}
