package com.oneclaw.shadow.core.model

data class Provider(
    val id: String,
    val name: String,
    val type: ProviderType,
    val apiBaseUrl: String,
    val isPreConfigured: Boolean,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

enum class ProviderType {
    OPENAI,
    ANTHROPIC,
    GEMINI
}
