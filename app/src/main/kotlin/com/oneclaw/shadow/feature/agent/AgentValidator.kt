package com.oneclaw.shadow.feature.agent

object AgentValidator {

    fun validateName(name: String): String? = when {
        name.isBlank() -> "Agent name cannot be empty."
        name.length > 100 -> "Agent name is too long (max 100 characters)."
        else -> null
    }

    fun validateSystemPrompt(prompt: String): String? = when {
        prompt.isBlank() -> "System prompt cannot be empty."
        prompt.length > 50_000 -> "System prompt is too long (max 50,000 characters)."
        else -> null
    }

    fun validatePreferredModel(providerId: String?, modelId: String?): String? =
        if ((providerId != null) != (modelId != null)) {
            "Preferred provider and model must both be set or both be empty."
        } else null
}
