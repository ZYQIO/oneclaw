package com.oneclaw.shadow.feature.session.usecase

import com.oneclaw.shadow.core.model.AgentConstants
import com.oneclaw.shadow.core.model.Session
import com.oneclaw.shadow.core.repository.SessionRepository

/**
 * Creates a new session in the database. Called when the first message is sent.
 * Implements lazy session creation: the session does not exist in DB until this is called.
 */
class CreateSessionUseCase(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(
        agentId: String = AgentConstants.GENERAL_ASSISTANT_ID
    ): Session {
        val session = Session(
            id = "",
            title = "New Conversation",
            currentAgentId = agentId,
            messageCount = 0,
            lastMessagePreview = null,
            isActive = false,
            deletedAt = null,
            createdAt = 0,
            updatedAt = 0
        )
        return sessionRepository.createSession(session)
    }
}
