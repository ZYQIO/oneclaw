package com.oneclaw.shadow.feature.session.usecase

import com.oneclaw.shadow.core.repository.SessionRepository
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.core.util.ErrorCode

/**
 * Soft-deletes multiple sessions at once.
 */
class BatchDeleteSessionsUseCase(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(sessionIds: List<String>): AppResult<Unit> {
        if (sessionIds.isEmpty()) {
            return AppResult.Error(
                message = "No sessions selected.",
                code = ErrorCode.VALIDATION_ERROR
            )
        }
        sessionRepository.softDeleteSessions(sessionIds)
        return AppResult.Success(Unit)
    }
}
