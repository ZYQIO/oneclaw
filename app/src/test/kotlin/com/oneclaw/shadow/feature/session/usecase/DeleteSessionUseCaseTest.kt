package com.oneclaw.shadow.feature.session.usecase

import com.oneclaw.shadow.core.model.Session
import com.oneclaw.shadow.core.repository.SessionRepository
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.core.util.ErrorCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeleteSessionUseCaseTest {

    private lateinit var sessionRepository: SessionRepository
    private lateinit var useCase: DeleteSessionUseCase

    private val now = System.currentTimeMillis()

    private val session = Session(
        id = "session-1",
        title = "Test Session",
        currentAgentId = "agent-general",
        messageCount = 3,
        lastMessagePreview = "Hello",
        isActive = false,
        deletedAt = null,
        createdAt = now,
        updatedAt = now
    )

    @BeforeEach
    fun setup() {
        sessionRepository = mockk()
        useCase = DeleteSessionUseCase(sessionRepository)
    }

    @Test
    fun `invoke returns success when session exists`() = runTest {
        coEvery { sessionRepository.getSessionById("session-1") } returns session
        coEvery { sessionRepository.softDeleteSession("session-1") } returns Unit

        val result = useCase("session-1")

        assertTrue(result is AppResult.Success)
        coVerify { sessionRepository.softDeleteSession("session-1") }
    }

    @Test
    fun `invoke returns validation error when session not found`() = runTest {
        coEvery { sessionRepository.getSessionById("nonexistent") } returns null

        val result = useCase("nonexistent")

        assertTrue(result is AppResult.Error)
        assertEquals(ErrorCode.VALIDATION_ERROR, (result as AppResult.Error).code)
    }

    @Test
    fun `invoke does not call softDelete when session not found`() = runTest {
        coEvery { sessionRepository.getSessionById("nonexistent") } returns null

        useCase("nonexistent")

        coVerify(exactly = 0) { sessionRepository.softDeleteSession(any()) }
    }
}
