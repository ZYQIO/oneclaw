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

class RenameSessionUseCaseTest {

    private lateinit var sessionRepository: SessionRepository
    private lateinit var useCase: RenameSessionUseCase

    @BeforeEach
    fun setup() {
        sessionRepository = mockk()
        useCase = RenameSessionUseCase(sessionRepository)
    }

    @Test
    fun `invoke returns success for valid title`() = runTest {
        coEvery { sessionRepository.updateTitle("session-1", "New Title") } returns Unit

        val result = useCase("session-1", "New Title")

        assertTrue(result is AppResult.Success)
        coVerify { sessionRepository.updateTitle("session-1", "New Title") }
    }

    @Test
    fun `invoke trims whitespace before saving`() = runTest {
        coEvery { sessionRepository.updateTitle("session-1", "Trimmed Title") } returns Unit

        val result = useCase("session-1", "  Trimmed Title  ")

        assertTrue(result is AppResult.Success)
        coVerify { sessionRepository.updateTitle("session-1", "Trimmed Title") }
    }

    @Test
    fun `invoke returns validation error for blank title`() = runTest {
        val result = useCase("session-1", "   ")

        assertTrue(result is AppResult.Error)
        assertEquals(ErrorCode.VALIDATION_ERROR, (result as AppResult.Error).code)
    }

    @Test
    fun `invoke returns validation error for empty title`() = runTest {
        val result = useCase("session-1", "")

        assertTrue(result is AppResult.Error)
        assertEquals(ErrorCode.VALIDATION_ERROR, (result as AppResult.Error).code)
    }

    @Test
    fun `invoke returns validation error for title exceeding 200 characters`() = runTest {
        val longTitle = "a".repeat(201)

        val result = useCase("session-1", longTitle)

        assertTrue(result is AppResult.Error)
        assertEquals(ErrorCode.VALIDATION_ERROR, (result as AppResult.Error).code)
    }

    @Test
    fun `invoke succeeds for title of exactly 200 characters`() = runTest {
        val maxTitle = "a".repeat(200)
        coEvery { sessionRepository.updateTitle("session-1", maxTitle) } returns Unit

        val result = useCase("session-1", maxTitle)

        assertTrue(result is AppResult.Success)
    }

    @Test
    fun `invoke does not call repository when title is blank`() = runTest {
        useCase("session-1", "")

        coVerify(exactly = 0) { sessionRepository.updateTitle(any(), any()) }
    }
}
