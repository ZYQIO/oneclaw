package com.oneclaw.shadow.data.remote.sse

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.ResponseBody

data class SseEvent(
    val type: String?,
    val data: String
)

fun ResponseBody.asSseFlow(): Flow<SseEvent> = callbackFlow {
    val source = source().buffer()
    try {
        var eventType: String? = null
        val dataBuilder = StringBuilder()

        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break

            when {
                line.startsWith("event:") -> {
                    eventType = line.removePrefix("event:").trim()
                }
                line.startsWith("data:") -> {
                    dataBuilder.append(line.removePrefix("data:").trim())
                }
                line.isEmpty() -> {
                    if (dataBuilder.isNotEmpty()) {
                        trySend(SseEvent(type = eventType, data = dataBuilder.toString()))
                        eventType = null
                        dataBuilder.clear()
                    }
                }
            }
        }
        // Flush remaining data if stream ends without trailing newline
        if (dataBuilder.isNotEmpty()) {
            trySend(SseEvent(type = eventType, data = dataBuilder.toString()))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        close(e)
    } finally {
        source.close()
        close()
    }
    awaitClose()
}
