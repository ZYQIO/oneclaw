package com.oneclaw.shadow.data.remote.adapter

import com.oneclaw.shadow.core.model.ProviderType
import okhttp3.OkHttpClient

class ModelApiAdapterFactory(
    private val okHttpClient: OkHttpClient
) {
    fun getAdapter(providerType: ProviderType): ModelApiAdapter {
        return when (providerType) {
            ProviderType.OPENAI -> OpenAiAdapter(okHttpClient)
            ProviderType.ANTHROPIC -> AnthropicAdapter(okHttpClient)
            ProviderType.GEMINI -> GeminiAdapter(okHttpClient)
        }
    }
}
