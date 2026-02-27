package com.oneclaw.shadow.data.local.mapper

import com.oneclaw.shadow.core.model.Agent
import com.oneclaw.shadow.data.local.entity.AgentEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

fun AgentEntity.toDomain(): Agent = Agent(
    id = id,
    name = name,
    description = description,
    systemPrompt = systemPrompt,
    toolIds = try { json.decodeFromString<List<String>>(toolIds) } catch (e: Exception) { emptyList() },
    preferredProviderId = preferredProviderId,
    preferredModelId = preferredModelId,
    isBuiltIn = isBuiltIn,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Agent.toEntity(): AgentEntity = AgentEntity(
    id = id,
    name = name,
    description = description,
    systemPrompt = systemPrompt,
    toolIds = json.encodeToString(toolIds),
    preferredProviderId = preferredProviderId,
    preferredModelId = preferredModelId,
    isBuiltIn = isBuiltIn,
    createdAt = createdAt,
    updatedAt = updatedAt
)
