package com.oneclaw.shadow.feature.schedule.usecase

import com.oneclaw.shadow.core.repository.ScheduledTaskRepository
import com.oneclaw.shadow.feature.schedule.alarm.AlarmScheduler
import com.oneclaw.shadow.feature.schedule.alarm.NextTriggerCalculator

class ToggleScheduledTaskUseCase(
    private val repository: ScheduledTaskRepository,
    private val alarmScheduler: AlarmScheduler
) {
    suspend operator fun invoke(taskId: String, enabled: Boolean) {
        val task = repository.getTaskById(taskId) ?: return

        if (enabled) {
            val nextTriggerAt = NextTriggerCalculator.calculate(task) ?: return
            val updated = task.copy(isEnabled = true, nextTriggerAt = nextTriggerAt)
            repository.updateTask(updated)
            alarmScheduler.scheduleTask(updated)
        } else {
            alarmScheduler.cancelTask(taskId)
            repository.setEnabled(taskId, false)
        }
    }
}
