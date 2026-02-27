package com.oneclaw.shadow.feature.session.usecase

import com.oneclaw.shadow.core.repository.SessionRepository

/**
 * Hard-deletes all soft-deleted sessions. Called on app startup.
 * Handles the case where the app was killed during the undo window.
 */
class CleanupSoftDeletedUseCase(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke() {
        sessionRepository.hardDeleteAllSoftDeleted()
    }
}
