package com.oneclaw.shadow.feature.session.usecase

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

class BatchDeleteSessionsUseCaseTest {

    private lateinit var sessionRepository: SessionRepository
    private lateinit var useCase: BatchDeleteSessionsUseCase

    @BeforeEach
    fun setup() {
        sessionRepository = mockk()
        useCase = BatchDeleteSessionsUseCase(sessionRepository)
    }

    @Test
    fun `invoke returns success with multiple session ids`() = runTest {
        val ids = listOf("session-1", "session-2", "session-3")
        coEvery { sessionRepository.softDeleteSessions(ids) } returns Unit

        val result = useCase(ids)

        assertTrue(result is AppResult.Success)
        coVerify { sessionRepository.softDeleteSessions(ids) }
    }

    @Test
    fun `invoke returns success with single session id`() = runTest {
        val ids = listOf("session-1")
        coEvery { sessionRepository.softDeleteSessions(ids) } returns Unit

        val result = useCase(ids)

        assertTrue(result is AppResult.Success)
    }

    @Test
    fun `invoke returns validation error when list is empty`() = runTest {
        val result = useCase(emptyList())

        assertTrue(result is AppResult.Error)
        assertEquals(ErrorCode.VALIDATION_ERROR, (result as AppResult.Error).code)
    }

    @Test
    fun `invoke does not call repository when list is empty`() = runTest {
        useCase(emptyList())

        coVerify(exactly = 0) { sessionRepository.softDeleteSessions(any()) }
    }
}
