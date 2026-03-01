package com.oneclaw.shadow.core.repository

import com.oneclaw.shadow.core.model.ExecutionStatus
import com.oneclaw.shadow.core.model.ScheduledTask
import kotlinx.coroutines.flow.Flow

interface ScheduledTaskRepository {
    fun getAllTasks(): Flow<List<ScheduledTask>>
    suspend fun getTaskById(id: String): ScheduledTask?
    suspend fun getEnabledTasks(): List<ScheduledTask>
    suspend fun createTask(task: ScheduledTask): ScheduledTask
    suspend fun updateTask(task: ScheduledTask)
    suspend fun deleteTask(id: String)
    suspend fun setEnabled(id: String, enabled: Boolean)
    suspend fun updateExecutionResult(
        id: String,
        status: ExecutionStatus,
        sessionId: String?,
        nextTriggerAt: Long?,
        isEnabled: Boolean
    )
}
