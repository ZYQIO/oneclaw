package com.oneclaw.shadow.feature.schedule.usecase

import com.oneclaw.shadow.core.model.ScheduledTask
import com.oneclaw.shadow.core.repository.ScheduledTaskRepository
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.core.util.ErrorCode
import com.oneclaw.shadow.feature.schedule.alarm.AlarmScheduler
import com.oneclaw.shadow.feature.schedule.alarm.NextTriggerCalculator

class UpdateScheduledTaskUseCase(
    private val repository: ScheduledTaskRepository,
    private val alarmScheduler: AlarmScheduler
) {
    suspend operator fun invoke(task: ScheduledTask): AppResult<Unit> {
        if (task.name.isBlank()) {
            return AppResult.Error(
                message = "Task name is required.",
                code = ErrorCode.VALIDATION_ERROR
            )
        }
        if (task.prompt.isBlank()) {
            return AppResult.Error(
                message = "Prompt is required.",
                code = ErrorCode.VALIDATION_ERROR
            )
        }

        // Cancel old alarm
        alarmScheduler.cancelTask(task.id)

        val nextTriggerAt = if (task.isEnabled) {
            NextTriggerCalculator.calculate(task)
        } else {
            null
        }

        val updated = task.copy(nextTriggerAt = nextTriggerAt)
        repository.updateTask(updated)

        if (updated.isEnabled && nextTriggerAt != null) {
            alarmScheduler.scheduleTask(updated)
        }

        return AppResult.Success(Unit)
    }
}
