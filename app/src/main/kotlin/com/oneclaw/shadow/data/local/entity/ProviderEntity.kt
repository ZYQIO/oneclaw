package com.oneclaw.shadow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val type: String, // "OPENAI", "ANTHROPIC", "GEMINI"
    @ColumnInfo(name = "api_base_url")
    val apiBaseUrl: String,
    @ColumnInfo(name = "is_pre_configured")
    val isPreConfigured: Boolean,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
