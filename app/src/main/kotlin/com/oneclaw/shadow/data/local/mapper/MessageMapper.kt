package com.oneclaw.shadow.data.local.mapper

import com.oneclaw.shadow.core.model.Message
import com.oneclaw.shadow.core.model.MessageType
import com.oneclaw.shadow.core.model.ToolCallStatus
import com.oneclaw.shadow.data.local.entity.MessageEntity

fun MessageEntity.toDomain(): Message = Message(
    id = id,
    sessionId = sessionId,
    type = MessageType.valueOf(type),
    content = content,
    thinkingContent = thinkingContent,
    toolCallId = toolCallId,
    toolName = toolName,
    toolInput = toolInput,
    toolOutput = toolOutput,
    toolStatus = toolStatus?.let { ToolCallStatus.valueOf(it) },
    toolDurationMs = toolDurationMs,
    tokenCountInput = tokenCountInput,
    tokenCountOutput = tokenCountOutput,
    modelId = modelId,
    providerId = providerId,
    createdAt = createdAt
)

fun Message.toEntity(): MessageEntity = MessageEntity(
    id = id,
    sessionId = sessionId,
    type = type.name,
    content = content,
    thinkingContent = thinkingContent,
    toolCallId = toolCallId,
    toolName = toolName,
    toolInput = toolInput,
    toolOutput = toolOutput,
    toolStatus = toolStatus?.name,
    toolDurationMs = toolDurationMs,
    tokenCountInput = tokenCountInput,
    tokenCountOutput = tokenCountOutput,
    modelId = modelId,
    providerId = providerId,
    createdAt = createdAt
)
