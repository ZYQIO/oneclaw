package com.oneclaw.shadow.data.repository

import com.oneclaw.shadow.core.repository.SettingsRepository
import com.oneclaw.shadow.data.local.dao.SettingsDao
import com.oneclaw.shadow.data.local.entity.SettingsEntity

class SettingsRepositoryImpl(
    private val settingsDao: SettingsDao
) : SettingsRepository {

    override suspend fun getString(key: String): String? =
        settingsDao.getString(key)

    override suspend fun setString(key: String, value: String) {
        settingsDao.set(SettingsEntity(key = key, value = value))
    }

    override suspend fun getBoolean(key: String, default: Boolean): Boolean {
        val value = settingsDao.getString(key) ?: return default
        return value.toBoolean()
    }

    override suspend fun setBoolean(key: String, value: Boolean) {
        settingsDao.set(SettingsEntity(key = key, value = value.toString()))
    }
}
