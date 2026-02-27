package com.oneclaw.shadow.core.repository

import com.oneclaw.shadow.core.model.Message
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun getMessagesForSession(sessionId: String): Flow<List<Message>>
    suspend fun addMessage(message: Message): Message
    suspend fun updateMessage(message: Message)
    suspend fun deleteMessagesForSession(sessionId: String)
    suspend fun getMessageCount(sessionId: String): Int
    suspend fun getMessagesSnapshot(sessionId: String): List<Message>
    suspend fun deleteMessage(id: String)
}
