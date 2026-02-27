package com.oneclaw.shadow.data.repository

import com.oneclaw.shadow.core.model.Agent
import com.oneclaw.shadow.core.repository.AgentRepository
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.core.util.ErrorCode
import com.oneclaw.shadow.data.local.dao.AgentDao
import com.oneclaw.shadow.data.local.mapper.toDomain
import com.oneclaw.shadow.data.local.mapper.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class AgentRepositoryImpl(
    private val agentDao: AgentDao
) : AgentRepository {

    override fun getAllAgents(): Flow<List<Agent>> =
        agentDao.getAllAgents().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getAgentById(id: String): Agent? =
        agentDao.getAgentById(id)?.toDomain()

    override suspend fun createAgent(agent: Agent): Agent {
        val now = System.currentTimeMillis()
        val newAgent = agent.copy(
            id = if (agent.id.isBlank()) UUID.randomUUID().toString() else agent.id,
            isBuiltIn = false,
            createdAt = now,
            updatedAt = now
        )
        agentDao.insert(newAgent.toEntity())
        return newAgent
    }

    override suspend fun updateAgent(agent: Agent): AppResult<Unit> {
        val existing = agentDao.getAgentById(agent.id)
            ?: return AppResult.Error(
                message = "Agent not found.",
                code = ErrorCode.VALIDATION_ERROR
            )
        if (existing.isBuiltIn) {
            return AppResult.Error(
                message = "Built-in agents cannot be edited.",
                code = ErrorCode.VALIDATION_ERROR
            )
        }
        val updated = agent.copy(updatedAt = System.currentTimeMillis())
        agentDao.update(updated.toEntity())
        return AppResult.Success(Unit)
    }

    override suspend fun deleteAgent(id: String): AppResult<Unit> {
        val deleted = agentDao.deleteCustomAgent(id)
        return if (deleted > 0) {
            AppResult.Success(Unit)
        } else {
            val exists = agentDao.getAgentById(id)
            if (exists != null && exists.isBuiltIn) {
                AppResult.Error(
                    message = "Built-in agents cannot be deleted.",
                    code = ErrorCode.VALIDATION_ERROR
                )
            } else {
                AppResult.Error(
                    message = "Agent not found.",
                    code = ErrorCode.VALIDATION_ERROR
                )
            }
        }
    }

    override suspend fun getBuiltInAgents(): List<Agent> =
        agentDao.getBuiltInAgents().map { it.toDomain() }
}
