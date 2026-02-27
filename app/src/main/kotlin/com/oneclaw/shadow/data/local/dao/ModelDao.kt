package com.oneclaw.shadow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.oneclaw.shadow.data.local.entity.ModelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {
    @Query("SELECT * FROM models WHERE provider_id = :providerId")
    suspend fun getModelsForProvider(providerId: String): List<ModelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(model: ModelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(models: List<ModelEntity>)

    @Query("DELETE FROM models WHERE id = :id AND provider_id = :providerId")
    suspend fun delete(id: String, providerId: String)

    @Query("DELETE FROM models WHERE provider_id = :providerId AND source = :source")
    suspend fun deleteByProviderAndSource(providerId: String, source: String)

    @Query("UPDATE models SET is_default = 0 WHERE is_default = 1")
    suspend fun clearAllDefaults()

    @Query("UPDATE models SET is_default = 1 WHERE id = :modelId AND provider_id = :providerId")
    suspend fun setDefault(modelId: String, providerId: String)

    @Query("SELECT * FROM models WHERE is_default = 1 LIMIT 1")
    fun getDefaultModel(): Flow<ModelEntity?>

    @Query("SELECT * FROM models WHERE is_default = 1 LIMIT 1")
    suspend fun getDefaultModelSnapshot(): ModelEntity?

    @Query("SELECT * FROM models WHERE id = :modelId AND provider_id = :providerId LIMIT 1")
    suspend fun getModel(modelId: String, providerId: String): ModelEntity?
}
