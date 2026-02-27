package com.oneclaw.shadow.core.model

data class ConnectionTestResult(
    val success: Boolean,
    val latencyMs: Long,
    val modelCount: Int?,
    val errorMessage: String?
)
