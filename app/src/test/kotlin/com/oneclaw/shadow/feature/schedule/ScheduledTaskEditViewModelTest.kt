package com.oneclaw.shadow.feature.schedule

import androidx.lifecycle.SavedStateHandle
import com.oneclaw.shadow.core.model.Agent
import com.oneclaw.shadow.core.model.ScheduleType
import com.oneclaw.shadow.core.model.ScheduledTask
import com.oneclaw.shadow.core.repository.AgentRepository
import com.oneclaw.shadow.core.repository.ScheduledTaskRepository
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.feature.schedule.usecase.CreateScheduledTaskUseCase
import com.oneclaw.shadow.feature.schedule.usecase.UpdateScheduledTaskUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduledTaskEditViewModelTest {

    private lateinit var agentRepository: AgentRepository
    private lateinit var scheduledTaskRepository: ScheduledTaskRepository
    private lateinit var createUseCase: CreateScheduledTaskUseCase
    private lateinit var updateUseCase: UpdateScheduledTaskUseCase
    private val testDispatcher = StandardTestDispatcher()

    private val testAgent = Agent(
        id = "agent-1",
        name = "Test Agent",
        description = null,
        systemPrompt = "You are helpful.",
        toolIds = emptyList(),
        preferredProviderId = null,
        preferredModelId = null,
        isBuiltIn = false,
        createdAt = 100L,
        updatedAt = 100L
    )

    private val existingTask = ScheduledTask(
        id = "task-existing",
        name = "Existing Task",
        agentId = "agent-1",
        prompt = "Do something",
        scheduleType = ScheduleType.WEEKLY,
        hour = 9,
        minute = 30,
        dayOfWeek = 3,
        dateMillis = null,
        isEnabled = true,
        lastExecutionAt = null,
        lastExecutionStatus = null,
        lastExecutionSessionId = null,
        nextTriggerAt = null,
        createdAt = 100L,
        updatedAt = 100L
    )

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        agentRepository = mockk(relaxed = true)
        scheduledTaskRepository = mockk(relaxed = true)
        createUseCase = mockk(relaxed = true)
        updateUseCase = mockk(relaxed = true)
        every { agentRepository.getAllAgents() } returns flowOf(listOf(testAgent))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) =
        ScheduledTaskEditViewModel(
            savedStateHandle = savedStateHandle,
            agentRepository = agentRepository,
            scheduledTaskRepository = scheduledTaskRepository,
            createUseCase = createUseCase,
            updateUseCase = updateUseCase
        )

    @Test
    fun `init loads agents from repository`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.agents.size)
        assertEquals("agent-1", state.agents[0].id)
    }

    @Test
    fun `init loads existing task when taskId is present`() = runTest {
        coEvery { scheduledTaskRepository.getTaskById("task-existing") } returns existingTask

        val viewModel = createViewModel(SavedStateHandle(mapOf("taskId" to "task-existing")))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Existing Task", state.name)
        assertEquals("Do something", state.prompt)
        assertEquals(ScheduleType.WEEKLY, state.scheduleType)
        assertEquals(9, state.hour)
        assertEquals(30, state.minute)
        assertEquals(3, state.dayOfWeek)
        assertTrue(state.isEditing)
    }

    @Test
    fun `updateName updates state`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateName("My New Task")

        assertEquals("My New Task", viewModel.uiState.value.name)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `updatePrompt updates state`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updatePrompt("New prompt text")

        assertEquals("New prompt text", viewModel.uiState.value.prompt)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `updateScheduleType updates state`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateScheduleType(ScheduleType.ONE_TIME)

        assertEquals(ScheduleType.ONE_TIME, viewModel.uiState.value.scheduleType)
    }

    @Test
    fun `save calls createUseCase for new task`() = runTest {
        coEvery { createUseCase(any()) } returns AppResult.Success(existingTask)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateName("New Task")
        viewModel.updatePrompt("Do something")
        viewModel.save()
        advanceUntilIdle()

        coVerify { createUseCase(any()) }
    }

    @Test
    fun `save calls updateUseCase for existing task`() = runTest {
        coEvery { scheduledTaskRepository.getTaskById("task-existing") } returns existingTask
        coEvery { updateUseCase(any()) } returns AppResult.Success(Unit)

        val viewModel = createViewModel(SavedStateHandle(mapOf("taskId" to "task-existing")))
        advanceUntilIdle()

        viewModel.save()
        advanceUntilIdle()

        coVerify { updateUseCase(any()) }
    }

    @Test
    fun `save sets error message on failure`() = runTest {
        coEvery { createUseCase(any()) } returns AppResult.Error(message = "Name is required.")

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.save()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSaving)
        assertNotNull(state.errorMessage)
        assertEquals("Name is required.", state.errorMessage)
        assertFalse(state.savedSuccessfully)
    }

    @Test
    fun `save sets savedSuccessfully on success`() = runTest {
        coEvery { createUseCase(any()) } returns AppResult.Success(existingTask)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateName("Valid Task")
        viewModel.updatePrompt("Valid prompt")
        viewModel.save()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSaving)
        assertTrue(state.savedSuccessfully)
        assertNull(state.errorMessage)
    }
}
