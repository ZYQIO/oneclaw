package com.oneclaw.shadow.feature.bridge

import com.oneclaw.shadow.core.model.Agent
import com.oneclaw.shadow.core.repository.AgentRepository
import com.oneclaw.shadow.data.local.AttachmentFileManager
import com.oneclaw.shadow.feature.chat.usecase.SendMessageUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class BridgeAgentExecutorImplTest {

    private lateinit var sendMessageUseCase: SendMessageUseCase
    private lateinit var agentRepository: AgentRepository
    private lateinit var executor: BridgeAgentExecutorImpl

    private val tempFiles = mutableListOf<File>()

    @BeforeEach
    fun setUp() {
        sendMessageUseCase = mockk()
        agentRepository = mockk()

        val agent = mockk<Agent>(relaxed = true)
        coEvery { agentRepository.getBuiltInAgents() } returns listOf(agent)
        every { agent.id } returns "agent-1"

        executor = BridgeAgentExecutorImpl(sendMessageUseCase, agentRepository)
    }

    @AfterEach
    fun tearDown() {
        tempFiles.forEach { it.delete() }
    }

    private fun createTempFile(name: String = "test.jpg"): File {
        val file = Files.createTempFile(name.substringBeforeLast('.'), ".${name.substringAfterLast('.')}").toFile()
        file.writeText("fake image content")
        tempFiles.add(file)
        return file
    }

    @Test
    fun `executeMessage_withImagePaths_createsPendingAttachments`() = runTest {
        val attachmentsSlot = slot<List<AttachmentFileManager.PendingAttachment>>()
        every {
            sendMessageUseCase.execute(
                sessionId = any(),
                userText = any(),
                agentId = any(),
                pendingMessages = any(),
                pendingAttachments = capture(attachmentsSlot)
            )
        } returns emptyFlow()

        val tempFile = createTempFile("photo.jpg")
        executor.executeMessage(
            conversationId = "conv-1",
            userMessage = "describe this",
            imagePaths = listOf(tempFile.absolutePath)
        )

        verify {
            sendMessageUseCase.execute(
                sessionId = "conv-1",
                userText = "describe this",
                agentId = "agent-1",
                pendingMessages = any(),
                pendingAttachments = any()
            )
        }
        assertEquals(1, attachmentsSlot.captured.size)
        assertEquals("image/jpeg", attachmentsSlot.captured[0].mimeType)
        assertEquals(tempFile.name, attachmentsSlot.captured[0].fileName)
    }

    @Test
    fun `executeMessage_withMissingImageFile_skipsIt`() = runTest {
        val attachmentsSlot = slot<List<AttachmentFileManager.PendingAttachment>>()
        every {
            sendMessageUseCase.execute(
                sessionId = any(),
                userText = any(),
                agentId = any(),
                pendingMessages = any(),
                pendingAttachments = capture(attachmentsSlot)
            )
        } returns emptyFlow()

        executor.executeMessage(
            conversationId = "conv-1",
            userMessage = "text",
            imagePaths = listOf("/nonexistent/path/image.jpg")
        )

        assertTrue(attachmentsSlot.captured.isEmpty())
    }

    @Test
    fun `executeMessage_noImagePaths_forwardsEmpty`() = runTest {
        val attachmentsSlot = slot<List<AttachmentFileManager.PendingAttachment>>()
        every {
            sendMessageUseCase.execute(
                sessionId = any(),
                userText = any(),
                agentId = any(),
                pendingMessages = any(),
                pendingAttachments = capture(attachmentsSlot)
            )
        } returns emptyFlow()

        executor.executeMessage(
            conversationId = "conv-1",
            userMessage = "hello",
            imagePaths = emptyList()
        )

        assertTrue(attachmentsSlot.captured.isEmpty())
    }
}
