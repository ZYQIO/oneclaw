package com.oneclaw.shadow.data.repository

import com.oneclaw.shadow.core.model.AiModel
import com.oneclaw.shadow.core.model.ConnectionTestResult
import com.oneclaw.shadow.core.model.ModelSource
import com.oneclaw.shadow.core.model.Provider
import com.oneclaw.shadow.core.repository.ProviderRepository
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.core.util.ErrorCode
import com.oneclaw.shadow.data.local.dao.ModelDao
import com.oneclaw.shadow.data.local.dao.ProviderDao
import com.oneclaw.shadow.data.local.mapper.toDomain
import com.oneclaw.shadow.data.local.mapper.toEntity
import com.oneclaw.shadow.data.remote.adapter.ModelApiAdapterFactory
import com.oneclaw.shadow.data.security.ApiKeyStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProviderRepositoryImpl(
    private val providerDao: ProviderDao,
    private val modelDao: ModelDao,
    private val apiKeyStorage: ApiKeyStorage,
    private val adapterFactory: ModelApiAdapterFactory
) : ProviderRepository {

    override fun getAllProviders(): Flow<List<Provider>> =
        providerDao.getAllProviders().map { entities -> entities.map { it.toDomain() } }

    override fun getActiveProviders(): Flow<List<Provider>> =
        providerDao.getActiveProviders().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getProviderById(id: String): Provider? =
        providerDao.getProviderById(id)?.toDomain()

    override suspend fun createProvider(provider: Provider) {
        providerDao.insert(provider.toEntity())
    }

    override suspend fun updateProvider(provider: Provider) {
        providerDao.update(provider.toEntity())
    }

    override suspend fun deleteProvider(id: String): AppResult<Unit> = try {
        apiKeyStorage.deleteApiKey(id)
        providerDao.delete(id)
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Error(exception = e, message = "Failed to delete provider: ${e.message}")
    }

    override suspend fun setProviderActive(id: String, isActive: Boolean) {
        providerDao.setActive(id, isActive)
    }

    override suspend fun getModelsForProvider(providerId: String): List<AiModel> =
        modelDao.getModelsForProvider(providerId).map { it.toDomain() }

    override suspend fun fetchModelsFromApi(providerId: String): AppResult<List<AiModel>> {
        val provider = providerDao.getProviderById(providerId)?.toDomain()
            ?: return AppResult.Error(message = "Provider not found", code = ErrorCode.VALIDATION_ERROR)
        val apiKey = apiKeyStorage.getApiKey(providerId)
            ?: return AppResult.Error(message = "API key not configured", code = ErrorCode.AUTH_ERROR)

        val adapter = adapterFactory.getAdapter(provider.type)
        return when (val result = adapter.listModels(provider.apiBaseUrl, apiKey)) {
            is AppResult.Success -> {
                // Clear existing dynamic models and save new ones
                modelDao.deleteByProviderAndSource(providerId, "DYNAMIC")
                val entities = result.data.map { it.toEntity() }
                modelDao.insertAll(entities)
                AppResult.Success(result.data)
            }
            is AppResult.Error -> result
        }
    }

    override suspend fun addManualModel(
        providerId: String,
        modelId: String,
        displayName: String?
    ): AppResult<Unit> = try {
        val model = AiModel(
            id = modelId,
            displayName = displayName,
            providerId = providerId,
            isDefault = false,
            source = ModelSource.MANUAL
        )
        modelDao.insert(model.toEntity())
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Error(exception = e, message = "Failed to add model: ${e.message}")
    }

    override suspend fun deleteManualModel(
        providerId: String,
        modelId: String
    ): AppResult<Unit> = try {
        modelDao.delete(modelId, providerId)
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Error(exception = e, message = "Failed to delete model: ${e.message}")
    }

    override suspend fun testConnection(providerId: String): AppResult<ConnectionTestResult> {
        val provider = providerDao.getProviderById(providerId)?.toDomain()
            ?: return AppResult.Error(message = "Provider not found", code = ErrorCode.VALIDATION_ERROR)
        val apiKey = apiKeyStorage.getApiKey(providerId)
            ?: return AppResult.Error(message = "API key not configured", code = ErrorCode.AUTH_ERROR)

        val adapter = adapterFactory.getAdapter(provider.type)
        return adapter.testConnection(provider.apiBaseUrl, apiKey)
    }

    override fun getGlobalDefaultModel(): Flow<AiModel?> =
        modelDao.getDefaultModel().map { it?.toDomain() }

    override suspend fun setGlobalDefaultModel(modelId: String, providerId: String) {
        modelDao.clearAllDefaults()
        modelDao.setDefault(modelId, providerId)
    }
}
