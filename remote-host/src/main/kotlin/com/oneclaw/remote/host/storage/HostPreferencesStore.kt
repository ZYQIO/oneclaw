package com.oneclaw.remote.host.storage

import android.content.Context
import android.os.Build
import java.util.UUID

class HostPreferencesStore(context: Context) {
    private val prefs = context.getSharedPreferences("remote_host_prefs", Context.MODE_PRIVATE)

    fun deviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
        return generated
    }

    fun deviceName(): String =
        prefs.getString(KEY_DEVICE_NAME, "${Build.MANUFACTURER} ${Build.MODEL}") ?: "${Build.MANUFACTURER} ${Build.MODEL}"

    fun setDeviceName(name: String) {
        prefs.edit().putString(KEY_DEVICE_NAME, name).apply()
    }

    fun brokerUrl(): String = prefs.getString(KEY_BROKER_URL, DEFAULT_BROKER_URL) ?: DEFAULT_BROKER_URL

    fun setBrokerUrl(url: String) {
        prefs.edit().putString(KEY_BROKER_URL, url).apply()
    }

    fun pairCode(): String {
        val existing = prefs.getString(KEY_PAIR_CODE, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }
        return refreshPairCode()
    }

    fun refreshPairCode(): String {
        val next = UUID.randomUUID().toString().take(8).uppercase()
        prefs.edit().putString(KEY_PAIR_CODE, next).apply()
        return next
    }

    fun allowAgentControl(): Boolean = prefs.getBoolean(KEY_ALLOW_AGENT_CONTROL, false)

    fun setAllowAgentControl(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ALLOW_AGENT_CONTROL, enabled).apply()
    }

    fun autoStartEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_START, true)

    fun setAutoStartEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_START, enabled).apply()
    }

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_BROKER_URL = "broker_url"
        private const val KEY_PAIR_CODE = "pair_code"
        private const val KEY_ALLOW_AGENT_CONTROL = "allow_agent_control"
        private const val KEY_AUTO_START = "auto_start"

        const val DEFAULT_BROKER_URL = "ws://10.0.2.2:8080/ws"
    }
}
