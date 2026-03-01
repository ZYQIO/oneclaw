package com.oneclaw.shadow.data.local.mapper

import com.oneclaw.shadow.core.model.ExecutionStatus
import com.oneclaw.shadow.core.model.ScheduleType
import com.oneclaw.shadow.core.model.ScheduledTask
import com.oneclaw.shadow.data.local.entity.ScheduledTaskEntity

fun ScheduledTaskEntity.toDomain(): ScheduledTask = ScheduledTask(
    id = id,
    name = name,
    agentId = agentId,
    prompt = prompt,
    scheduleType = ScheduleType.valueOf(scheduleType),
    hour = hour,
    minute = minute,
    dayOfWeek = dayOfWeek,
    dateMillis = dateMillis,
    isEnabled = isEnabled,
    lastExecutionAt = lastExecutionAt,
    lastExecutionStatus = lastExecutionStatus?.let { ExecutionStatus.valueOf(it) },
    lastExecutionSessionId = lastExecutionSessionId,
    nextTriggerAt = nextTriggerAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun ScheduledTask.toEntity(): ScheduledTaskEntity = ScheduledTaskEntity(
    id = id,
    name = name,
    agentId = agentId,
    prompt = prompt,
    scheduleType = scheduleType.name,
    hour = hour,
    minute = minute,
    dayOfWeek = dayOfWeek,
    dateMillis = dateMillis,
    isEnabled = isEnabled,
    lastExecutionAt = lastExecutionAt,
    lastExecutionStatus = lastExecutionStatus?.name,
    lastExecutionSessionId = lastExecutionSessionId,
    nextTriggerAt = nextTriggerAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)
