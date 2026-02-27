package com.oneclaw.shadow.data.remote.adapter

sealed class ApiMessage {
    data class User(val content: String) : ApiMessage()
    data class Assistant(
        val content: String?,
        val toolCalls: List<ApiToolCall>? = null
    ) : ApiMessage()
    data class ToolResult(
        val toolCallId: String,
        val content: String
    ) : ApiMessage()
}

data class ApiToolCall(
    val id: String,
    val name: String,
    val arguments: String // JSON string
)
