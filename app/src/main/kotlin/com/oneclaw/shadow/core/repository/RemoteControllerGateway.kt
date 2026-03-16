package com.oneclaw.shadow.core.repository

import com.oneclaw.remote.core.model.RemoteControlCommand
import com.oneclaw.remote.core.model.RemoteDevice
import com.oneclaw.remote.core.model.RemoteFileEntry
import com.oneclaw.remote.core.model.RemoteSession
import com.oneclaw.remote.core.model.RemoteSnapshot
import com.oneclaw.shadow.core.util.AppResult
import kotlinx.coroutines.flow.StateFlow

interface RemoteControllerGateway {
    val devices: StateFlow<List<RemoteDevice>>
    val lastSnapshot: StateFlow<RemoteSnapshot?>
    val status: StateFlow<String>
    val activeSessions: StateFlow<Map<String, RemoteSession>>

    suspend fun getBrokerUrl(): String
    suspend fun setBrokerUrl(url: String)
    suspend fun connect(): AppResult<Unit>
    fun disconnect()

    suspend fun pairDevice(deviceId: String, pairCode: String): AppResult<Unit>
    suspend fun refreshDevices(): AppResult<List<RemoteDevice>>
    suspend fun openSession(deviceId: String, source: String = "manual"): AppResult<RemoteSession>
    suspend fun closeSession(deviceId: String): AppResult<Unit>
    suspend fun requestSnapshot(deviceId: String): AppResult<RemoteSnapshot>
    suspend fun sendControl(
        deviceId: String,
        command: RemoteControlCommand,
        source: String = "manual"
    ): AppResult<String>
    suspend fun listFiles(deviceId: String, path: String): AppResult<List<RemoteFileEntry>>
    suspend fun pushFile(deviceId: String, localPath: String, remotePath: String): AppResult<String>
    suspend fun pullFile(deviceId: String, remotePath: String, localPath: String): AppResult<String>
}
