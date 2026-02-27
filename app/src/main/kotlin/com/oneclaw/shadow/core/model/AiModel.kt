package com.oneclaw.shadow.core.model

data class AiModel(
    val id: String,
    val displayName: String?,
    val providerId: String,
    val isDefault: Boolean,
    val source: ModelSource
)

enum class ModelSource {
    DYNAMIC,
    PRESET,
    MANUAL
}
