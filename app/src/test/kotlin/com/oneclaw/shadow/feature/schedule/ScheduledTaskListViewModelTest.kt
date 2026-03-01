package com.oneclaw.shadow.feature.schedule

import com.oneclaw.shadow.core.model.ScheduleType
import com.oneclaw.shadow.core.model.ScheduledTask
import com.oneclaw.shadow.core.repository.ScheduledTaskRepository
import com.oneclaw.shadow.feature.schedule.usecase.DeleteScheduledTaskUseCase
import com.oneclaw.shadow.feature.schedule.usecase.ToggleScheduledTaskUseCase
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduledTaskListViewModelTest {

    private lateinit var repository: ScheduledTaskRepository
    private lateinit var toggleUseCase: ToggleScheduledTaskUseCase
    private lateinit var deleteUseCase: DeleteScheduledTaskUseCase
    private val testDispatcher = StandardTestDispatcher()

    private val testTask = ScheduledTask(
        id = "task-1",
        name = "Test",
        agentId = "agent-1",
        prompt = "Hello",
        scheduleType = ScheduleType.DAILY,
        hour = 8,
        minute = 0,
        dayOfWeek = null,
        dateMillis = null,
        isEnabled = true,
        lastExecutionAt = null,
        lastExecutionStatus = null,
        lastExecutionSessionId = null,
        nextTriggerAt = 1000L,
        createdAt = 100L,
        updatedAt = 100L
    )

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        toggleUseCase = mockk(relaxed = true)
        deleteUseCase = mockk(relaxed = true)
        every { repository.getAllTasks() } returns flowOf(listOf(testTask))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads tasks from repository`() = runTest {
        val viewModel = ScheduledTaskListViewModel(repository, toggleUseCase, deleteUseCase)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.tasks.size)
        assertEquals("task-1", state.tasks[0].id)
    }

    @Test
    fun `toggleTask calls toggle use case`() = runTest {
        val viewModel = ScheduledTaskListViewModel(repository, toggleUseCase, deleteUseCase)
        advanceUntilIdle()

        viewModel.toggleTask("task-1", false)
        advanceUntilIdle()

        coVerify { toggleUseCase("task-1", false) }
    }

    @Test
    fun `deleteTask calls delete use case`() = runTest {
        val viewModel = ScheduledTaskListViewModel(repository, toggleUseCase, deleteUseCase)
        advanceUntilIdle()

        viewModel.deleteTask("task-1")
        advanceUntilIdle()

        coVerify { deleteUseCase("task-1") }
    }
}
