package com.oneclaw.shadow.data.remote.adapter

import com.oneclaw.shadow.core.model.ProviderType
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ModelApiAdapterFactoryTest {

    private lateinit var factory: ModelApiAdapterFactory

    @BeforeEach
    fun setup() {
        factory = ModelApiAdapterFactory(OkHttpClient())
    }

    @Test
    fun `getAdapter returns OpenAiAdapter for OPENAI type`() {
        val adapter = factory.getAdapter(ProviderType.OPENAI)
        assertTrue(adapter is OpenAiAdapter)
    }

    @Test
    fun `getAdapter returns AnthropicAdapter for ANTHROPIC type`() {
        val adapter = factory.getAdapter(ProviderType.ANTHROPIC)
        assertTrue(adapter is AnthropicAdapter)
    }

    @Test
    fun `getAdapter returns GeminiAdapter for GEMINI type`() {
        val adapter = factory.getAdapter(ProviderType.GEMINI)
        assertTrue(adapter is GeminiAdapter)
    }

    @Test
    fun `getAdapter covers all ProviderType values`() {
        for (type in ProviderType.entries) {
            val adapter = factory.getAdapter(type)
            assertTrue(adapter is ModelApiAdapter, "No adapter for $type")
        }
    }
}
