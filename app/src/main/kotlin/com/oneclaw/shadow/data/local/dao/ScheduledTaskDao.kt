package com.oneclaw.shadow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.oneclaw.shadow.data.local.entity.ScheduledTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledTaskDao {

    @Query("SELECT * FROM scheduled_tasks ORDER BY created_at DESC")
    fun getAllTasks(): Flow<List<ScheduledTaskEntity>>

    @Query("SELECT * FROM scheduled_tasks WHERE id = :id")
    suspend fun getTaskById(id: String): ScheduledTaskEntity?

    @Query("SELECT * FROM scheduled_tasks WHERE is_enabled = 1")
    suspend fun getEnabledTasks(): List<ScheduledTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: ScheduledTaskEntity)

    @Update
    suspend fun update(task: ScheduledTaskEntity)

    @Query("DELETE FROM scheduled_tasks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE scheduled_tasks SET is_enabled = :enabled, updated_at = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long)

    @Query(
        """UPDATE scheduled_tasks SET
            last_execution_at = :executionAt,
            last_execution_status = :status,
            last_execution_session_id = :sessionId,
            next_trigger_at = :nextTriggerAt,
            is_enabled = :isEnabled,
            updated_at = :updatedAt
           WHERE id = :id"""
    )
    suspend fun updateExecutionResult(
        id: String,
        executionAt: Long,
        status: String,
        sessionId: String?,
        nextTriggerAt: Long?,
        isEnabled: Boolean,
        updatedAt: Long
    )
}
