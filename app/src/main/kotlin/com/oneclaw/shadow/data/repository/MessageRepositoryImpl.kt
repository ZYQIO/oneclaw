package com.oneclaw.shadow.data.repository

import com.oneclaw.shadow.core.model.Message
import com.oneclaw.shadow.core.repository.MessageRepository
import com.oneclaw.shadow.data.local.dao.MessageDao
import com.oneclaw.shadow.data.local.mapper.toDomain
import com.oneclaw.shadow.data.local.mapper.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MessageRepositoryImpl(
    private val messageDao: MessageDao
) : MessageRepository {

    override fun getMessagesForSession(sessionId: String): Flow<List<Message>> =
        messageDao.getMessagesForSession(sessionId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun addMessage(message: Message): Message {
        messageDao.insert(message.toEntity())
        return message
    }

    override suspend fun updateMessage(message: Message) {
        messageDao.update(message.toEntity())
    }

    override suspend fun deleteMessagesForSession(sessionId: String) {
        messageDao.deleteForSession(sessionId)
    }

    override suspend fun getMessageCount(sessionId: String): Int =
        messageDao.getMessageCount(sessionId)

    override suspend fun getMessagesSnapshot(sessionId: String): List<Message> =
        messageDao.getMessagesSnapshot(sessionId).map { it.toDomain() }

    override suspend fun deleteMessage(id: String) {
        messageDao.delete(id)
    }
}
