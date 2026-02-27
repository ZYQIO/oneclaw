package com.oneclaw.shadow.core.repository

import com.oneclaw.shadow.core.model.Agent
import com.oneclaw.shadow.core.util.AppResult
import kotlinx.coroutines.flow.Flow

interface AgentRepository {
    fun getAllAgents(): Flow<List<Agent>>
    suspend fun getAgentById(id: String): Agent?
    suspend fun createAgent(agent: Agent): Agent
    suspend fun updateAgent(agent: Agent): AppResult<Unit>
    suspend fun deleteAgent(id: String): AppResult<Unit>
    suspend fun getBuiltInAgents(): List<Agent>
}
