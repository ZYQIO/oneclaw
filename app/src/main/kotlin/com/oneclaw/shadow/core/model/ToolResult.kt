package com.oneclaw.shadow.core.model

data class ToolResult(
    val status: ToolResultStatus,
    val result: String?,
    val errorType: String?,
    val errorMessage: String?
) {
    companion object {
        fun success(result: String): ToolResult = ToolResult(
            status = ToolResultStatus.SUCCESS,
            result = result,
            errorType = null,
            errorMessage = null
        )

        fun error(errorType: String, errorMessage: String): ToolResult = ToolResult(
            status = ToolResultStatus.ERROR,
            result = null,
            errorType = errorType,
            errorMessage = errorMessage
        )
    }
}

enum class ToolResultStatus {
    SUCCESS, ERROR
}
