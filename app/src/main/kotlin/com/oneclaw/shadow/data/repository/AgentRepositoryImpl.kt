package com.oneclaw.shadow.data.repository

import com.oneclaw.shadow.core.model.Agent
import com.oneclaw.shadow.core.repository.AgentRepository
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.data.local.dao.AgentDao
import com.oneclaw.shadow.data.local.mapper.toDomain
import com.oneclaw.shadow.data.local.mapper.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AgentRepositoryImpl(
    private val agentDao: AgentDao
) : AgentRepository {

    override fun getAllAgents(): Flow<List<Agent>> =
        agentDao.getAllAgents().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getAgentById(id: String): Agent? =
        agentDao.getAgentById(id)?.toDomain()

    override suspend fun createAgent(agent: Agent): Agent {
        agentDao.insert(agent.toEntity())
        return agent
    }

    override suspend fun updateAgent(agent: Agent): AppResult<Unit> = try {
        agentDao.update(agent.toEntity())
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Error(exception = e, message = "Failed to update agent: ${e.message}")
    }

    override suspend fun deleteAgent(id: String): AppResult<Unit> = try {
        agentDao.delete(id)
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Error(exception = e, message = "Failed to delete agent: ${e.message}")
    }

    override suspend fun getBuiltInAgents(): List<Agent> =
        agentDao.getBuiltInAgents().map { it.toDomain() }
}
