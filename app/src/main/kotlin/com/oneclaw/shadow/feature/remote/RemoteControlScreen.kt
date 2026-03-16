package com.oneclaw.shadow.feature.remote

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.oneclaw.remote.core.model.RemoteDevice
import org.koin.androidx.compose.koinViewModel

@Composable
fun RemoteControlScreen(
    onNavigateBack: () -> Unit,
    viewModel: RemoteControlViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    RemoteControlScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onBrokerUrlChange = viewModel::updateBrokerUrl,
        onPairCodeChange = viewModel::updatePairCode,
        onInputTextChange = viewModel::updateInputText,
        onConnect = viewModel::connect,
        onDisconnect = viewModel::disconnect,
        onRefreshDevices = viewModel::refreshDevices,
        onSelectDevice = viewModel::selectDevice,
        onPairSelectedDevice = viewModel::pairSelectedDevice,
        onOpenSession = viewModel::openSelectedSession,
        onCloseSession = viewModel::closeSelectedSession,
        onSnapshot = viewModel::requestSnapshot,
        onHome = viewModel::sendHome,
        onBack = viewModel::sendBack,
        onTapCenter = viewModel::tapCenter,
        onSendText = viewModel::sendInputText
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteControlScreenContent(
    uiState: RemoteControlUiState,
    onNavigateBack: () -> Unit,
    onBrokerUrlChange: (String) -> Unit,
    onPairCodeChange: (String) -> Unit,
    onInputTextChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefreshDevices: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onPairSelectedDevice: () -> Unit,
    onOpenSession: () -> Unit,
    onCloseSession: () -> Unit,
    onSnapshot: () -> Unit,
    onHome: () -> Unit,
    onBack: () -> Unit,
    onTapCenter: () -> Unit,
    onSendText: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Remote Control") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.brokerUrl,
                            onValueChange = onBrokerUrlChange,
                            label = { Text("Broker URL") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = onConnect) { Text("Connect") }
                            Button(onClick = onDisconnect) { Text("Disconnect") }
                            Button(onClick = onRefreshDevices) { Text("Refresh") }
                        }
                        OutlinedTextField(
                            value = uiState.pairCode,
                            onValueChange = onPairCodeChange,
                            label = { Text("Pair code") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(uiState.status, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            uiState.snapshotBase64?.let { snapshotBase64 ->
                item {
                    Card {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Latest snapshot", style = MaterialTheme.typography.titleMedium)
                            SnapshotImage(base64Data = snapshotBase64)
                        }
                    }
                }
            }

            items(uiState.devices, key = { it.deviceId }) { device ->
                RemoteDeviceCard(
                    device = device,
                    isSelected = uiState.selectedDeviceId == device.deviceId,
                    hasActiveSession = uiState.activeSessions.containsKey(device.deviceId),
                    inputText = uiState.inputText,
                    onSelect = { onSelectDevice(device.deviceId) },
                    onPair = onPairSelectedDevice,
                    onOpenSession = onOpenSession,
                    onCloseSession = onCloseSession,
                    onSnapshot = onSnapshot,
                    onHome = onHome,
                    onBack = onBack,
                    onTapCenter = onTapCenter,
                    onInputTextChange = onInputTextChange,
                    onSendText = onSendText
                )
            }
        }
    }
}

@Composable
private fun SnapshotImage(base64Data: String) {
    val imageBitmap = remember(base64Data) {
        val bytes = Base64.decode(base64Data, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = "Remote snapshot",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp, max = 360.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Fit
        )
    } else {
        Text("Snapshot unavailable")
    }
}

@Composable
private fun RemoteDeviceCard(
    device: RemoteDevice,
    isSelected: Boolean,
    hasActiveSession: Boolean,
    inputText: String,
    onSelect: () -> Unit,
    onPair: () -> Unit,
    onOpenSession: () -> Unit,
    onCloseSession: () -> Unit,
    onSnapshot: () -> Unit,
    onHome: () -> Unit,
    onBack: () -> Unit,
    onTapCenter: () -> Unit,
    onInputTextChange: (String) -> Unit,
    onSendText: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.PhoneAndroid, contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(device.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "mode=${device.mode.name.lowercase()} | ${device.screenWidth}x${device.screenHeight}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(if (hasActiveSession) "Session open" else "No session", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "video=${device.capabilities.video} touch=${device.capabilities.touch} file=${device.capabilities.fileTransfer} unattended=${device.capabilities.unattended}",
                style = MaterialTheme.typography.bodySmall
            )

            if (isSelected) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onPair) { Text("Pair") }
                    Button(onClick = onOpenSession) { Text("Open") }
                    Button(onClick = onCloseSession) { Text("Close") }
                    Button(onClick = onSnapshot) { Text("Snapshot") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onHome) { Text("Home") }
                    Button(onClick = onBack) { Text("Back") }
                    Button(onClick = onTapCenter) { Text("Tap Center") }
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputTextChange,
                    label = { Text("Input text") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = onSendText) {
                    Text("Send Text")
                }
            }
        }
    }
}
