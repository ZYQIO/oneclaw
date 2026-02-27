package com.oneclaw.shadow.data.remote.adapter

import com.oneclaw.shadow.core.model.AiModel
import com.oneclaw.shadow.core.model.ConnectionTestResult
import com.oneclaw.shadow.core.model.ToolDefinition
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.core.util.ErrorCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient

class OpenAiAdapter(
    private val client: OkHttpClient
) : ModelApiAdapter {

    override fun sendMessageStream(
        apiBaseUrl: String,
        apiKey: String,
        modelId: String,
        messages: List<ApiMessage>,
        tools: List<ToolDefinition>?,
        systemPrompt: String?
    ): Flow<StreamEvent> = flow {
        // TODO: Implement in Phase 2 (RFC-001)
        emit(StreamEvent.Error("Not implemented", "NOT_IMPLEMENTED"))
        emit(StreamEvent.Done)
    }

    override suspend fun listModels(
        apiBaseUrl: String,
        apiKey: String
    ): AppResult<List<AiModel>> {
        // TODO: Implement in Phase 2 (RFC-003)
        return AppResult.Error(message = "Not implemented yet", code = ErrorCode.PROVIDER_ERROR)
    }

    override suspend fun testConnection(
        apiBaseUrl: String,
        apiKey: String
    ): AppResult<ConnectionTestResult> {
        // TODO: Implement in Phase 2 (RFC-003)
        return AppResult.Error(message = "Not implemented yet", code = ErrorCode.PROVIDER_ERROR)
    }

    override fun formatToolDefinitions(tools: List<ToolDefinition>): Any {
        // TODO: Implement in Phase 2 (RFC-004)
        return emptyList<Any>()
    }

    override suspend fun generateSimpleCompletion(
        apiBaseUrl: String,
        apiKey: String,
        modelId: String,
        prompt: String,
        maxTokens: Int
    ): AppResult<String> {
        // TODO: Implement in Phase 2 (RFC-005)
        return AppResult.Error(message = "Not implemented yet", code = ErrorCode.PROVIDER_ERROR)
    }
}
