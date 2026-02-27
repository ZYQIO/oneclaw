package com.oneclaw.shadow.data.remote.adapter

sealed class StreamEvent {
    data class TextDelta(val text: String) : StreamEvent()
    data class ThinkingDelta(val text: String) : StreamEvent()
    data class ToolCallStart(val toolCallId: String, val toolName: String) : StreamEvent()
    data class ToolCallDelta(val toolCallId: String, val argumentsDelta: String) : StreamEvent()
    data class ToolCallEnd(val toolCallId: String) : StreamEvent()
    data class Usage(val inputTokens: Int, val outputTokens: Int) : StreamEvent()
    data class Error(val message: String, val code: String?) : StreamEvent()
    data object Done : StreamEvent()
}
