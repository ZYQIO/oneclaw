package com.oneclaw.shadow.tool.js.bridge

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.function

/**
 * Located in: tool/js/bridge/TimeBridge.kt
 *
 * Injects _time(timezone?, format?) into the QuickJS context.
 * Delegates to Java's ZonedDateTime for accurate timezone handling.
 *
 * QuickJS does not have the Intl API, so timezone-aware formatting
 * must be bridged to the host.
 */
object TimeBridge {

    fun inject(quickJs: QuickJs) {
        quickJs.function("_time") { args: Array<Any?> ->
            val timezone = args.getOrNull(0)?.toString()?.takeIf { it.isNotEmpty() }
            val format = args.getOrNull(1)?.toString() ?: "iso8601"
            getCurrentTime(timezone, format)
        }
    }

    private fun getCurrentTime(timezone: String?, format: String): String {
        val zone = if (timezone != null) {
            try {
                java.time.ZoneId.of(timezone)
            } catch (e: Exception) {
                throw IllegalArgumentException(
                    "Invalid timezone: '$timezone'. Use IANA format (e.g., 'America/New_York')."
                )
            }
        } else {
            java.time.ZoneId.systemDefault()
        }

        val now = java.time.ZonedDateTime.now(zone)

        return when (format) {
            "human_readable" -> {
                val formatter = java.time.format.DateTimeFormatter.ofPattern(
                    "EEEE, MMMM d, yyyy 'at' h:mm:ss a z"
                )
                now.format(formatter)
            }
            else -> now.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        }
    }
}
