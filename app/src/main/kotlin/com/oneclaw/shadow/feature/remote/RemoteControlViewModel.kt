package com.oneclaw.shadow.feature.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oneclaw.remote.core.model.RemoteControlAction
import com.oneclaw.remote.core.model.RemoteControlCommand
import com.oneclaw.remote.core.model.RemoteDevice
import com.oneclaw.remote.core.model.RemoteSession
import com.oneclaw.shadow.core.repository.RemoteControllerGateway
import com.oneclaw.shadow.core.util.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class RemoteControlAggregate(
    val devices: List<RemoteDevice>,
    val snapshotBase64: String?,
    val status: String,
    val sessions: Map<String, RemoteSession>
)

data class RemoteControlUiState(
    val brokerUrl: String = "",
    val pairCode: String = "",
    val inputText: String = "",
    val devices: List<RemoteDevice> = emptyList(),
    val activeSessions: Map<String, RemoteSession> = emptyMap(),
    val status: String = "Idle",
    val snapshotBase64: String? = null,
    val selectedDeviceId: String? = null
)

class RemoteControlViewModel(
    private val remoteControllerGateway: RemoteControllerGateway
) : ViewModel() {
    private val _uiState = MutableStateFlow(RemoteControlUiState())
    val uiState: StateFlow<RemoteControlUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(brokerUrl = remoteControllerGateway.getBrokerUrl()) }
        }
        viewModelScope.launch {
            combine(
                remoteControllerGateway.devices,
                remoteControllerGateway.lastSnapshot,
                remoteControllerGateway.status,
                remoteControllerGateway.activeSessions
            ) { devices, snapshot, status, sessions ->
                RemoteControlAggregate(
                    devices = devices,
                    snapshotBase64 = snapshot?.base64Data,
                    status = status,
                    sessions = sessions
                )
            }.collect { aggregate ->
                _uiState.update { current ->
                    current.copy(
                        devices = aggregate.devices,
                        snapshotBase64 = aggregate.snapshotBase64,
                        status = aggregate.status,
                        activeSessions = aggregate.sessions,
                        selectedDeviceId = current.selectedDeviceId ?: aggregate.devices.firstOrNull()?.deviceId
                    )
                }
            }
        }
    }

    fun updateBrokerUrl(value: String) {
        _uiState.update { it.copy(brokerUrl = value) }
    }

    fun updatePairCode(value: String) {
        _uiState.update { it.copy(pairCode = value) }
    }

    fun updateInputText(value: String) {
        _uiState.update { it.copy(inputText = value) }
    }

    fun selectDevice(deviceId: String) {
        _uiState.update { it.copy(selectedDeviceId = deviceId) }
    }

    fun connect() {
        viewModelScope.launch {
            remoteControllerGateway.setBrokerUrl(_uiState.value.brokerUrl)
            applyResult(remoteControllerGateway.connect(), successMessage = "Connected to broker")
        }
    }

    fun disconnect() {
        remoteControllerGateway.disconnect()
    }

    fun refreshDevices() {
        viewModelScope.launch {
            applyResult(remoteControllerGateway.refreshDevices(), successMessage = "Device list refreshed")
        }
    }

    fun pairSelectedDevice() {
        val deviceId = _uiState.value.selectedDeviceId ?: return
        val pairCode = _uiState.value.pairCode
        if (pairCode.isBlank()) {
            _uiState.update { it.copy(status = "Enter the device pair code first") }
            return
        }
        viewModelScope.launch {
            applyResult(remoteControllerGateway.pairDevice(deviceId, pairCode), successMessage = "Device paired")
        }
    }

    fun openSelectedSession() {
        val deviceId = _uiState.value.selectedDeviceId ?: return
        viewModelScope.launch {
            applyResult(remoteControllerGateway.openSession(deviceId), successMessage = "Session opened")
        }
    }

    fun closeSelectedSession() {
        val deviceId = _uiState.value.selectedDeviceId ?: return
        viewModelScope.launch {
            applyResult(remoteControllerGateway.closeSession(deviceId), successMessage = "Session closed")
        }
    }

    fun requestSnapshot() {
        val deviceId = _uiState.value.selectedDeviceId ?: return
        viewModelScope.launch {
            applyResult(remoteControllerGateway.requestSnapshot(deviceId), successMessage = "Snapshot updated")
        }
    }

    fun sendBack() = sendControl(RemoteControlCommand(action = RemoteControlAction.BACK))

    fun sendHome() = sendControl(RemoteControlCommand(action = RemoteControlAction.HOME))

    fun tapCenter() {
        val device = selectedDevice() ?: return
        sendControl(
            RemoteControlCommand(
                action = RemoteControlAction.TAP,
                x = (device.screenWidth / 2f).toInt(),
                y = (device.screenHeight / 2f).toInt()
            )
        )
    }

    fun sendInputText() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank()) {
            _uiState.update { it.copy(status = "Input text cannot be empty") }
            return
        }
        sendControl(RemoteControlCommand(action = RemoteControlAction.TEXT, text = text))
    }

    private fun sendControl(command: RemoteControlCommand) {
        val deviceId = _uiState.value.selectedDeviceId ?: return
        viewModelScope.launch {
            applyResult(remoteControllerGateway.sendControl(deviceId, command), successMessage = "Command sent")
        }
    }

    private fun selectedDevice(): RemoteDevice? =
        _uiState.value.devices.firstOrNull { it.deviceId == _uiState.value.selectedDeviceId }

    private fun <T> applyResult(result: AppResult<T>, successMessage: String) {
        when (result) {
            is AppResult.Success -> _uiState.update { it.copy(status = successMessage) }
            is AppResult.Error -> _uiState.update { it.copy(status = result.message) }
        }
    }
}
