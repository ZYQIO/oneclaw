package com.oneclaw.shadow.feature.session.usecase

import com.oneclaw.shadow.core.model.AgentConstants
import com.oneclaw.shadow.core.model.Session
import com.oneclaw.shadow.core.repository.SessionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CreateSessionUseCaseTest {

    private lateinit var sessionRepository: SessionRepository
    private lateinit var useCase: CreateSessionUseCase

    private val now = System.currentTimeMillis()

    private fun makeSession(id: String, agentId: String = AgentConstants.GENERAL_ASSISTANT_ID) = Session(
        id = id,
        title = "New Conversation",
        currentAgentId = agentId,
        messageCount = 0,
        lastMessagePreview = null,
        isActive = false,
        deletedAt = null,
        createdAt = now,
        updatedAt = now
    )

    @BeforeEach
    fun setup() {
        sessionRepository = mockk()
    }

    @Test
    fun `invoke creates session with default agent id`() = runTest {
        val createdSession = makeSession("session-1")
        coEvery { sessionRepository.createSession(any()) } returns createdSession

        useCase = CreateSessionUseCase(sessionRepository)
        val result = useCase()

        assertEquals("session-1", result.id)
        assertEquals(AgentConstants.GENERAL_ASSISTANT_ID, result.currentAgentId)
    }

    @Test
    fun `invoke creates session with provided agent id`() = runTest {
        val customAgentId = "agent-custom"
        val createdSession = makeSession("session-2", customAgentId)
        val slot = slot<Session>()
        coEvery { sessionRepository.createSession(capture(slot)) } returns createdSession

        useCase = CreateSessionUseCase(sessionRepository)
        useCase(agentId = customAgentId)

        assertEquals(customAgentId, slot.captured.currentAgentId)
    }

    @Test
    fun `invoke calls repository createSession exactly once`() = runTest {
        val createdSession = makeSession("session-1")
        coEvery { sessionRepository.createSession(any()) } returns createdSession

        useCase = CreateSessionUseCase(sessionRepository)
        useCase()

        coVerify(exactly = 1) { sessionRepository.createSession(any()) }
    }

    @Test
    fun `invoke returns session returned by repository`() = runTest {
        val expected = makeSession("session-abc")
        coEvery { sessionRepository.createSession(any()) } returns expected

        useCase = CreateSessionUseCase(sessionRepository)
        val result = useCase()

        assertEquals(expected, result)
    }
}
