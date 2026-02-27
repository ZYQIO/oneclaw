package com.oneclaw.shadow.feature.session.usecase

import com.oneclaw.shadow.core.repository.SessionRepository
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.core.util.ErrorCode

/**
 * Soft-deletes a single session. Returns success or error.
 * The session is hidden from the list but can be restored within the undo window.
 */
class DeleteSessionUseCase(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(sessionId: String): AppResult<Unit> {
        val session = sessionRepository.getSessionById(sessionId)
            ?: return AppResult.Error(
                message = "Session not found.",
                code = ErrorCode.VALIDATION_ERROR
            )
        sessionRepository.softDeleteSession(session.id)
        return AppResult.Success(Unit)
    }
}
