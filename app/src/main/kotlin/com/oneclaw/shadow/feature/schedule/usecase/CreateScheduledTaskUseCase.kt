package com.oneclaw.shadow.feature.schedule.usecase

import com.oneclaw.shadow.core.model.ScheduledTask
import com.oneclaw.shadow.core.repository.ScheduledTaskRepository
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.core.util.ErrorCode
import com.oneclaw.shadow.feature.schedule.alarm.AlarmScheduler
import com.oneclaw.shadow.feature.schedule.alarm.NextTriggerCalculator

class CreateScheduledTaskUseCase(
    private val repository: ScheduledTaskRepository,
    private val alarmScheduler: AlarmScheduler
) {
    suspend operator fun invoke(task: ScheduledTask): AppResult<ScheduledTask> {
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

        val nextTriggerAt = NextTriggerCalculator.calculate(task)
            ?: return AppResult.Error(
                message = "Scheduled time is in the past.",
                code = ErrorCode.VALIDATION_ERROR
            )

        val taskWithTrigger = task.copy(
            nextTriggerAt = nextTriggerAt,
            isEnabled = true
        )

        val created = repository.createTask(taskWithTrigger)
        alarmScheduler.scheduleTask(created)

        return AppResult.Success(created)
    }
}
