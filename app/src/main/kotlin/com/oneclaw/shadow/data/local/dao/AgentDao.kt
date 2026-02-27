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
    @Query("SELECT * FROM agents ORDER BY updated_at DESC")
    fun getAllAgents(): Flow<List<AgentEntity>>

    @Query("SELECT * FROM agents WHERE id = :id")
    suspend fun getAgentById(id: String): AgentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(agent: AgentEntity)

    @Update
    suspend fun update(agent: AgentEntity)

    @Query("DELETE FROM agents WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM agents WHERE is_built_in = 1")
    suspend fun getBuiltInAgents(): List<AgentEntity>
}
