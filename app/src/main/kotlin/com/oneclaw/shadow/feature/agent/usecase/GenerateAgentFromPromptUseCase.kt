package com.oneclaw.shadow.feature.agent.usecase

import com.oneclaw.shadow.core.repository.ProviderRepository
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.data.remote.adapter.ModelApiAdapterFactory
import com.oneclaw.shadow.data.security.ApiKeyStorage
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class GenerateAgentFromPromptUseCase(
    private val providerRepository: ProviderRepository,
    private val apiKeyStorage: ApiKeyStorage,
    private val adapterFactory: ModelApiAdapterFactory
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend operator fun invoke(userPrompt: String): AppResult<GeneratedAgent> {
        val defaultModel = providerRepository.getGlobalDefaultModel().first()
            ?: return AppResult.Error(message = "No default model configured. Please set up a provider in Settings.")

        val provider = providerRepository.getProviderById(defaultModel.providerId)
            ?: return AppResult.Error(message = "Provider not found.")

        val apiKey = apiKeyStorage.getApiKey(provider.id)
            ?: return AppResult.Error(message = "API key not configured for ${provider.name}.")

        val prompt = buildPrompt(userPrompt)
        val adapter = adapterFactory.getAdapter(provider.type)
        val response = adapter.generateSimpleCompletion(
            apiBaseUrl = provider.apiBaseUrl,
            apiKey = apiKey,
            modelId = defaultModel.id,
            prompt = prompt,
            maxTokens = 1500
        )

        return when (response) {
            is AppResult.Success -> parseGeneratedAgent(response.data)
            is AppResult.Error -> AppResult.Error(message = response.message)
        }
    }

    private fun buildPrompt(userPrompt: String): String = """
        $GENERATION_SYSTEM_PROMPT

        User request: Create an AI agent based on this description:

        $userPrompt
    """.trimIndent()

    private fun parseGeneratedAgent(responseText: String): AppResult<GeneratedAgent> {
        return try {
            val jsonStr = extractJson(responseText)
            val parsed = json.decodeFromString<GeneratedAgentJson>(jsonStr)
            AppResult.Success(
                GeneratedAgent(
                    name = parsed.name,
                    description = parsed.description,
                    systemPrompt = parsed.systemPrompt
                )
            )
        } catch (e: Exception) {
            AppResult.Error(message = "Failed to parse generated agent: ${e.message}")
        }
    }

    private fun extractJson(text: String): String {
        val jsonBlock = Regex("```json\\s*([\\s\\S]*?)```").find(text)
        if (jsonBlock != null) return jsonBlock.groupValues[1].trim()
        val genericBlock = Regex("```\\s*([\\s\\S]*?)```").find(text)
        if (genericBlock != null) return genericBlock.groupValues[1].trim()
        return text.trim()
    }

    companion object {
        private val GENERATION_SYSTEM_PROMPT = """
            You are an AI agent configuration generator. Given a user's description of what kind
            of AI agent they want, generate a complete agent configuration.

            Respond with ONLY a JSON object (no markdown, no explanation) with these fields:
            - "name": A concise agent name (2-5 words)
            - "description": A one-sentence description of what this agent does
            - "systemPrompt": A detailed system prompt (200-500 words) that instructs the AI
              to behave as described. The system prompt should be specific, actionable, and
              include guidelines for tone, expertise areas, and behavior boundaries.

            Example response:
            {"name": "Python Debug Helper", "description": "Helps debug Python code and suggests fixes", "systemPrompt": "You are an expert Python developer..."}
        """.trimIndent()
    }
}

data class GeneratedAgent(
    val name: String,
    val description: String,
    val systemPrompt: String
)

@Serializable
private data class GeneratedAgentJson(
    val name: String,
    val description: String,
    val systemPrompt: String
)
