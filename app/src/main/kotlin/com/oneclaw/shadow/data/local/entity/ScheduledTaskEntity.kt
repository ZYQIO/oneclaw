package com.oneclaw.shadow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scheduled_tasks",
    indices = [
        Index(value = ["is_enabled"]),
        Index(value = ["next_trigger_at"])
    ]
)
data class ScheduledTaskEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @ColumnInfo(name = "agent_id")
    val agentId: String,
    val prompt: String,
    @ColumnInfo(name = "schedule_type")
    val scheduleType: String,
    val hour: Int,
    val minute: Int,
    @ColumnInfo(name = "day_of_week")
    val dayOfWeek: Int?,
    @ColumnInfo(name = "date_millis")
    val dateMillis: Long?,
    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean,
    @ColumnInfo(name = "last_execution_at")
    val lastExecutionAt: Long?,
    @ColumnInfo(name = "last_execution_status")
    val lastExecutionStatus: String?,
    @ColumnInfo(name = "last_execution_session_id")
    val lastExecutionSessionId: String?,
    @ColumnInfo(name = "next_trigger_at")
    val nextTriggerAt: Long?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
