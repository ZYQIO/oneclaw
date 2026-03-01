package com.oneclaw.shadow.data.local.mapper

import com.oneclaw.shadow.core.model.ExecutionStatus
import com.oneclaw.shadow.core.model.ScheduleType
import com.oneclaw.shadow.core.model.ScheduledTask
import com.oneclaw.shadow.data.local.entity.ScheduledTaskEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ScheduledTaskMapperTest {

    private fun createEntity(
        id: String = "task-1",
        name: String = "Test Task",
        agentId: String = "agent-1",
        prompt: String = "Do something",
        scheduleType: String = "DAILY",
        hour: Int = 8,
        minute: Int = 0,
        dayOfWeek: Int? = null,
        dateMillis: Long? = null,
        isEnabled: Boolean = true,
        lastExecutionAt: Long? = null,
        lastExecutionStatus: String? = null,
        lastExecutionSessionId: String? = null,
        nextTriggerAt: Long? = 5000L,
        createdAt: Long = 1000L,
        updatedAt: Long = 2000L
    ) = ScheduledTaskEntity(
        id = id,
        name = name,
        agentId = agentId,
        prompt = prompt,
        scheduleType = scheduleType,
        hour = hour,
        minute = minute,
        dayOfWeek = dayOfWeek,
        dateMillis = dateMillis,
        isEnabled = isEnabled,
        lastExecutionAt = lastExecutionAt,
        lastExecutionStatus = lastExecutionStatus,
        lastExecutionSessionId = lastExecutionSessionId,
        nextTriggerAt = nextTriggerAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun createDomain(
        id: String = "task-1",
        name: String = "Test Task",
        agentId: String = "agent-1",
        prompt: String = "Do something",
        scheduleType: ScheduleType = ScheduleType.DAILY,
        hour: Int = 8,
        minute: Int = 0,
        dayOfWeek: Int? = null,
        dateMillis: Long? = null,
        isEnabled: Boolean = true,
        lastExecutionAt: Long? = null,
        lastExecutionStatus: ExecutionStatus? = null,
        lastExecutionSessionId: String? = null,
        nextTriggerAt: Long? = 5000L,
        createdAt: Long = 1000L,
        updatedAt: Long = 2000L
    ) = ScheduledTask(
        id = id,
        name = name,
        agentId = agentId,
        prompt = prompt,
        scheduleType = scheduleType,
        hour = hour,
        minute = minute,
        dayOfWeek = dayOfWeek,
        dateMillis = dateMillis,
        isEnabled = isEnabled,
        lastExecutionAt = lastExecutionAt,
        lastExecutionStatus = lastExecutionStatus,
        lastExecutionSessionId = lastExecutionSessionId,
        nextTriggerAt = nextTriggerAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    @Test
    fun `entity to domain mapping maps all fields correctly`() {
        val entity = createEntity(
            id = "task-99",
            name = "My Task",
            agentId = "agent-7",
            prompt = "Run analysis",
            scheduleType = "WEEKLY",
            hour = 10,
            minute = 30,
            dayOfWeek = 5,
            dateMillis = null,
            isEnabled = true,
            lastExecutionAt = 3000L,
            lastExecutionStatus = "SUCCESS",
            lastExecutionSessionId = "session-abc",
            nextTriggerAt = 9999L,
            createdAt = 1000L,
            updatedAt = 2000L
        )

        val domain = entity.toDomain()

        assertEquals("task-99", domain.id)
        assertEquals("My Task", domain.name)
        assertEquals("agent-7", domain.agentId)
        assertEquals("Run analysis", domain.prompt)
        assertEquals(ScheduleType.WEEKLY, domain.scheduleType)
        assertEquals(10, domain.hour)
        assertEquals(30, domain.minute)
        assertEquals(5, domain.dayOfWeek)
        assertNull(domain.dateMillis)
        assertEquals(true, domain.isEnabled)
        assertEquals(3000L, domain.lastExecutionAt)
        assertEquals(ExecutionStatus.SUCCESS, domain.lastExecutionStatus)
        assertEquals("session-abc", domain.lastExecutionSessionId)
        assertEquals(9999L, domain.nextTriggerAt)
        assertEquals(1000L, domain.createdAt)
        assertEquals(2000L, domain.updatedAt)
    }

    @Test
    fun `domain to entity mapping maps all fields correctly`() {
        val domain = createDomain(
            id = "task-55",
            name = "Mapped Task",
            agentId = "agent-3",
            prompt = "Weekly summary",
            scheduleType = ScheduleType.WEEKLY,
            hour = 7,
            minute = 15,
            dayOfWeek = 2,
            dateMillis = null,
            isEnabled = false,
            lastExecutionAt = 4000L,
            lastExecutionStatus = ExecutionStatus.FAILED,
            lastExecutionSessionId = "session-xyz",
            nextTriggerAt = 8888L,
            createdAt = 500L,
            updatedAt = 600L
        )

        val entity = domain.toEntity()

        assertEquals("task-55", entity.id)
        assertEquals("Mapped Task", entity.name)
        assertEquals("agent-3", entity.agentId)
        assertEquals("Weekly summary", entity.prompt)
        assertEquals("WEEKLY", entity.scheduleType)
        assertEquals(7, entity.hour)
        assertEquals(15, entity.minute)
        assertEquals(2, entity.dayOfWeek)
        assertNull(entity.dateMillis)
        assertEquals(false, entity.isEnabled)
        assertEquals(4000L, entity.lastExecutionAt)
        assertEquals("FAILED", entity.lastExecutionStatus)
        assertEquals("session-xyz", entity.lastExecutionSessionId)
        assertEquals(8888L, entity.nextTriggerAt)
        assertEquals(500L, entity.createdAt)
        assertEquals(600L, entity.updatedAt)
    }

    @Test
    fun `handles null optional fields correctly`() {
        val entity = createEntity(
            dayOfWeek = null,
            dateMillis = null,
            lastExecutionAt = null,
            lastExecutionStatus = null,
            lastExecutionSessionId = null,
            nextTriggerAt = null
        )

        val domain = entity.toDomain()

        assertNull(domain.dayOfWeek)
        assertNull(domain.dateMillis)
        assertNull(domain.lastExecutionAt)
        assertNull(domain.lastExecutionStatus)
        assertNull(domain.lastExecutionSessionId)
        assertNull(domain.nextTriggerAt)
    }

    @Test
    fun `handles execution status mapping for all values`() {
        val runningEntity = createEntity(lastExecutionStatus = "RUNNING")
        val successEntity = createEntity(lastExecutionStatus = "SUCCESS")
        val failedEntity = createEntity(lastExecutionStatus = "FAILED")
        val nullEntity = createEntity(lastExecutionStatus = null)

        assertEquals(ExecutionStatus.RUNNING, runningEntity.toDomain().lastExecutionStatus)
        assertEquals(ExecutionStatus.SUCCESS, successEntity.toDomain().lastExecutionStatus)
        assertEquals(ExecutionStatus.FAILED, failedEntity.toDomain().lastExecutionStatus)
        assertNull(nullEntity.toDomain().lastExecutionStatus)
    }

    @Test
    fun `roundtrip domain to entity and back preserves data`() {
        val original = createDomain(
            id = "task-round",
            name = "Roundtrip",
            scheduleType = ScheduleType.ONE_TIME,
            hour = 12,
            minute = 0,
            dateMillis = 1700000000000L,
            lastExecutionStatus = ExecutionStatus.SUCCESS
        )

        val roundtripped = original.toEntity().toDomain()

        assertEquals(original, roundtripped)
    }
}
