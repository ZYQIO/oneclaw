package com.oneclaw.shadow.core.model

data class ScheduledTask(
    val id: String,
    val name: String,
    val agentId: String,
    val prompt: String,
    val scheduleType: ScheduleType,
    val hour: Int,
    val minute: Int,
    val dayOfWeek: Int?,
    val dateMillis: Long?,
    val isEnabled: Boolean,
    val lastExecutionAt: Long?,
    val lastExecutionStatus: ExecutionStatus?,
    val lastExecutionSessionId: String?,
    val nextTriggerAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

enum class ScheduleType {
    ONE_TIME,
    DAILY,
    WEEKLY
}

enum class ExecutionStatus {
    RUNNING,
    SUCCESS,
    FAILED
}
