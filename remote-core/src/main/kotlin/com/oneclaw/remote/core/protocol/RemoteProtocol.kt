package com.oneclaw.remote.core.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class BrokerEnvelope(
    val type: String,
    val requestId: String? = null,
    val senderId: String? = null,
    val targetId: String? = null,
    val deviceId: String? = null,
    val sessionId: String? = null,
    val payload: JsonObject = buildJsonObject { }
)

object BrokerMessageType {
    const val CONTROLLER_REGISTER = "controller.register"
    const val DEVICE_REGISTER = "device.register"
    const val DEVICE_REGISTERED = "device.registered"
    const val DEVICE_HEARTBEAT = "device.heartbeat"
    const val DEVICE_CAPABILITIES = "device.capabilities"
    const val DEVICE_LIST_REQUEST = "device.list.request"
    const val DEVICE_LIST_RESPONSE = "device.list.response"
    const val PAIR_REQUEST = "pair.request"
    const val PAIR_CONFIRM = "pair.confirm"
    const val SESSION_OPEN = "session.open"
    const val SESSION_OPENED = "session.opened"
    const val SESSION_CLOSE = "session.close"
    const val SESSION_CLOSED = "session.closed"
    const val SESSION_CONTROL = "session.control"
    const val SESSION_CONTROL_ACK = "session.control.ack"
    const val SESSION_FILE_META = "session.file.meta"
    const val SESSION_FILE_RESPONSE = "session.file.response"
    const val SESSION_FILE_CHUNK = "session.file.chunk"
    const val SESSION_SNAPSHOT_REQUEST = "session.snapshot.request"
    const val SESSION_SNAPSHOT_RESPONSE = "session.snapshot.response"
    const val ERROR = "error"
}

object RemoteProtocolJson {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}

fun jsonObjectOf(vararg pairs: Pair<String, JsonElement?>): JsonObject = buildJsonObject {
    pairs.forEach { (key, value) ->
        if (value != null) {
            put(key, value)
        }
    }
}

fun JsonObject.stringOrNull(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

fun JsonObject.booleanOrNull(key: String): Boolean? = this[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()

fun JsonObject.longOrNull(key: String): Long? = this[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull()

fun JsonObject.intOrNull(key: String): Int? = this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

fun jsonString(value: String): JsonPrimitive = JsonPrimitive(value)
