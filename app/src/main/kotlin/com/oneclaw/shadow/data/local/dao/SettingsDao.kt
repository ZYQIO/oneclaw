package com.oneclaw.shadow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.oneclaw.shadow.data.local.entity.SettingsEntity

@Dao
interface SettingsDao {
    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    suspend fun getString(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(setting: SettingsEntity)

    @Query("DELETE FROM app_settings WHERE `key` = :key")
    suspend fun delete(key: String)
}
