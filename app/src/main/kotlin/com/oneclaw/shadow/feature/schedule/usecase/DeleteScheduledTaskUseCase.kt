package com.oneclaw.shadow.feature.schedule.usecase

import com.oneclaw.shadow.core.repository.ScheduledTaskRepository
import com.oneclaw.shadow.feature.schedule.alarm.AlarmScheduler

class DeleteScheduledTaskUseCase(
    private val repository: ScheduledTaskRepository,
    private val alarmScheduler: AlarmScheduler
) {
    suspend operator fun invoke(taskId: String) {
        alarmScheduler.cancelTask(taskId)
        repository.deleteTask(taskId)
    }
}
