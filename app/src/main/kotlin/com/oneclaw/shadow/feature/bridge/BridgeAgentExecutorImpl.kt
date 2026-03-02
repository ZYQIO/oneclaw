package com.oneclaw.shadow.feature.bridge

import com.oneclaw.shadow.bridge.BridgeAgentExecutor
import com.oneclaw.shadow.bridge.BridgeMessage
import com.oneclaw.shadow.core.model.AttachmentType
import com.oneclaw.shadow.core.repository.AgentRepository
import com.oneclaw.shadow.data.local.AttachmentFileManager
import com.oneclaw.shadow.feature.chat.ChatEvent
import com.oneclaw.shadow.feature.chat.usecase.SendMessageUseCase
import kotlinx.coroutines.CancellationException
import java.io.File
import java.util.UUID

class BridgeAgentExecutorImpl(
    private val sendMessageUseCase: SendMessageUseCase,
    private val agentRepository: AgentRepository
) : BridgeAgentExecutor {

    override suspend fun executeMessage(
        conversationId: String,
        userMessage: String,
        imagePaths: List<String>
    ): BridgeMessage? {
        val agentId = resolveAgentId()
        val pendingAttachments = imagePaths.mapNotNull { path ->
            val file = File(path)
            if (!file.exists()) return@mapNotNull null
            AttachmentFileManager.PendingAttachment(
                id = UUID.randomUUID().toString(),
                type = AttachmentType.IMAGE,
                fileName = file.name,
                mimeType = mimeTypeFromExtension(file.extension),
                fileSize = file.length(),
                filePath = path,
                thumbnailPath = null,
                width = null,
                height = null,
                durationMs = null
            )
        }
        var lastResponseContent: String? = null
        var lastResponseTimestamp: Long = 0L

        try {
            sendMessageUseCase.execute(
                sessionId = conversationId,
                userText = userMessage,
                agentId = agentId,
                pendingAttachments = pendingAttachments
            ).collect { event ->
                when (event) {
                    is ChatEvent.ResponseComplete -> {
                        lastResponseContent = event.message.content
                        lastResponseTimestamp = event.message.createdAt
                    }
                    else -> { /* other events not needed by bridge */ }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Agent execution failed; return null so caller can fall back
            return null
        }

        val content = lastResponseContent
        return if (content != null && content.isNotBlank()) {
            BridgeMessage(content = content, timestamp = lastResponseTimestamp)
        } else {
            null
        }
    }

    private fun mimeTypeFromExtension(ext: String): String = when (ext.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png"         -> "image/png"
        "gif"         -> "image/gif"
        "webp"        -> "image/webp"
        "bmp"         -> "image/bmp"
        else          -> "image/jpeg"
    }

    private suspend fun resolveAgentId(): String {
        val builtIn = agentRepository.getBuiltInAgents()
        return builtIn.firstOrNull()?.id
            ?: agentRepository.getAllAgents()
                .let { flow ->
                    var result: String? = null
                    flow.collect { agents -> result = agents.firstOrNull()?.id }
                    result
                }
            ?: "default"
    }
}
