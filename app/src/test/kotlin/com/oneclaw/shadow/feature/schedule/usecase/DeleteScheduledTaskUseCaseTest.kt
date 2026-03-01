package com.oneclaw.shadow.feature.schedule.usecase

import com.oneclaw.shadow.core.repository.ScheduledTaskRepository
import com.oneclaw.shadow.feature.schedule.alarm.AlarmScheduler
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeleteScheduledTaskUseCaseTest {

    private lateinit var repository: ScheduledTaskRepository
    private lateinit var alarmScheduler: AlarmScheduler
    private lateinit var useCase: DeleteScheduledTaskUseCase

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        alarmScheduler = mockk(relaxed = true)
        useCase = DeleteScheduledTaskUseCase(repository, alarmScheduler)
    }

    @Test
    fun `cancels alarm and deletes from repository`() = runTest {
        useCase("task-42")

        verify { alarmScheduler.cancelTask("task-42") }
        coVerify { repository.deleteTask("task-42") }
    }

    @Test
    fun `calls both cancelTask and deleteTask with correct id`() = runTest {
        val taskId = "specific-task-id"

        useCase(taskId)

        verify(exactly = 1) { alarmScheduler.cancelTask(taskId) }
        coVerify(exactly = 1) { repository.deleteTask(taskId) }
    }
}
