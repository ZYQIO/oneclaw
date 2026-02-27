package com.oneclaw.shadow.feature.session.usecase

import com.oneclaw.shadow.core.repository.SessionRepository
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.core.util.ErrorCode

/**
 * Renames a session title. Validates that the title is non-empty and within length limit.
 */
class RenameSessionUseCase(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(sessionId: String, newTitle: String): AppResult<Unit> {
        val trimmed = newTitle.trim()
        if (trimmed.isBlank()) {
            return AppResult.Error(
                message = "Session title cannot be empty.",
                code = ErrorCode.VALIDATION_ERROR
            )
        }
        if (trimmed.length > 200) {
            return AppResult.Error(
                message = "Session title is too long (max 200 characters).",
                code = ErrorCode.VALIDATION_ERROR
            )
        }
        sessionRepository.updateTitle(sessionId, trimmed)
        return AppResult.Success(Unit)
    }
}
