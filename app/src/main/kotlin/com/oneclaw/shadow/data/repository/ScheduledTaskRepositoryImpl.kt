package com.oneclaw.shadow.data.repository

import com.oneclaw.shadow.core.model.ExecutionStatus
import com.oneclaw.shadow.core.model.ScheduledTask
import com.oneclaw.shadow.core.repository.ScheduledTaskRepository
import com.oneclaw.shadow.data.local.dao.ScheduledTaskDao
import com.oneclaw.shadow.data.local.mapper.toDomain
import com.oneclaw.shadow.data.local.mapper.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class ScheduledTaskRepositoryImpl(
    private val scheduledTaskDao: ScheduledTaskDao
) : ScheduledTaskRepository {

    override fun getAllTasks(): Flow<List<ScheduledTask>> =
        scheduledTaskDao.getAllTasks().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getTaskById(id: String): ScheduledTask? =
        scheduledTaskDao.getTaskById(id)?.toDomain()

    override suspend fun getEnabledTasks(): List<ScheduledTask> =
        scheduledTaskDao.getEnabledTasks().map { it.toDomain() }

    override suspend fun createTask(task: ScheduledTask): ScheduledTask {
        val now = System.currentTimeMillis()
        val newTask = task.copy(
            id = if (task.id.isBlank()) UUID.randomUUID().toString() else task.id,
            createdAt = now,
            updatedAt = now
        )
        scheduledTaskDao.insert(newTask.toEntity())
        return newTask
    }

    override suspend fun updateTask(task: ScheduledTask) {
        val updated = task.copy(updatedAt = System.currentTimeMillis())
        scheduledTaskDao.update(updated.toEntity())
    }

    override suspend fun deleteTask(id: String) {
        scheduledTaskDao.delete(id)
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) {
        scheduledTaskDao.setEnabled(id, enabled, System.currentTimeMillis())
    }

    override suspend fun updateExecutionResult(
        id: String,
        status: ExecutionStatus,
        sessionId: String?,
        nextTriggerAt: Long?,
        isEnabled: Boolean
    ) {
        val now = System.currentTimeMillis()
        scheduledTaskDao.updateExecutionResult(
            id = id,
            executionAt = now,
            status = status.name,
            sessionId = sessionId,
            nextTriggerAt = nextTriggerAt,
            isEnabled = isEnabled,
            updatedAt = now
        )
    }
}
