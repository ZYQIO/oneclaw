package com.oneclaw.remote.host.service

import com.oneclaw.remote.core.model.RemoteMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HostRuntimeSnapshot(
    val serviceRunning: Boolean = false,
    val connectionState: String = "idle",
    val remoteMode: RemoteMode = RemoteMode.COMPATIBILITY,
    val rootAvailable: Boolean = false,
    val activeSessionCount: Int = 0,
    val lastEvent: String = "Service not started",
    val lastHeartbeatAt: Long? = null
)

object HostRuntimeState {
    private val _state = MutableStateFlow(HostRuntimeSnapshot())
    val state: StateFlow<HostRuntimeSnapshot> = _state.asStateFlow()

    fun update(transform: (HostRuntimeSnapshot) -> HostRuntimeSnapshot) {
        _state.value = transform(_state.value)
    }
}
