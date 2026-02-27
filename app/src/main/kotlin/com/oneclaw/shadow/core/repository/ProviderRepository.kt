package com.oneclaw.shadow.core.repository

import com.oneclaw.shadow.core.model.AiModel
import com.oneclaw.shadow.core.model.ConnectionTestResult
import com.oneclaw.shadow.core.model.Provider
import com.oneclaw.shadow.core.util.AppResult
import kotlinx.coroutines.flow.Flow

interface ProviderRepository {
    fun getAllProviders(): Flow<List<Provider>>
    fun getActiveProviders(): Flow<List<Provider>>
    suspend fun getProviderById(id: String): Provider?
    suspend fun createProvider(provider: Provider)
    suspend fun updateProvider(provider: Provider)
    suspend fun deleteProvider(id: String): AppResult<Unit>
    suspend fun setProviderActive(id: String, isActive: Boolean)
    suspend fun getModelsForProvider(providerId: String): List<AiModel>
    suspend fun fetchModelsFromApi(providerId: String): AppResult<List<AiModel>>
    suspend fun addManualModel(providerId: String, modelId: String, displayName: String?): AppResult<Unit>
    suspend fun deleteManualModel(providerId: String, modelId: String): AppResult<Unit>
    suspend fun testConnection(providerId: String): AppResult<ConnectionTestResult>
    fun getGlobalDefaultModel(): Flow<AiModel?>
    suspend fun setGlobalDefaultModel(modelId: String, providerId: String)
}
