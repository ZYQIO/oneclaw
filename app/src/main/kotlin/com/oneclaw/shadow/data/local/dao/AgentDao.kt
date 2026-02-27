package com.oneclaw.shadow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.oneclaw.shadow.data.local.entity.AgentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentDao {
    /** Built-in first, then custom sorted by updated_at descending. */
    @Query("SELECT * FROM agents ORDER BY is_built_in DESC, updated_at DESC")
    fun getAllAgents(): Flow<List<AgentEntity>>

    @Query("SELECT * FROM agents WHERE id = :id")
    suspend fun getAgentById(id: String): AgentEntity?

    @Query("SELECT * FROM agents WHERE is_built_in = 1")
    suspend fun getBuiltInAgents(): List<AgentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(agent: AgentEntity)

    @Update
    suspend fun update(agent: AgentEntity)

    /** Only deletes custom (non-built-in) agents. Returns rows affected. */
    @Query("DELETE FROM agents WHERE id = :id AND is_built_in = 0")
    suspend fun deleteCustomAgent(id: String): Int

    @Query("SELECT COUNT(*) FROM agents")
    suspend fun getAgentCount(): Int
}
