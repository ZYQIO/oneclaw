package com.oneclaw.remote.host

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.oneclaw.remote.host.service.HostRuntimeState
import com.oneclaw.remote.host.service.RemoteHostForegroundService
import com.oneclaw.remote.host.storage.HostPreferencesStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = HostPreferencesStore(this)
        setContent {
            MaterialTheme {
                RemoteHostScreen(store = store)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteHostScreen(store: HostPreferencesStore) {
    val context = LocalContext.current
    val runtimeState by HostRuntimeState.state.collectAsState()
    var deviceName by remember { mutableStateOf(store.deviceName()) }
    var brokerUrl by remember { mutableStateOf(store.brokerUrl()) }
    var pairCode by remember { mutableStateOf(store.pairCode()) }
    var allowAgentControl by remember { mutableStateOf(store.allowAgentControl()) }
    var autoStart by remember { mutableStateOf(store.autoStartEnabled()) }
    val powerManager = context.getSystemService(PowerManager::class.java)
    val ignoringBatteryOptimizations = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Remote Host") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Deploy this app on the Android phone you want to control. Root-enabled devices can use screencap + input for unattended control.",
                style = MaterialTheme.typography.bodyMedium
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Runtime status", style = MaterialTheme.typography.titleMedium)
                    Text("Service: ${if (runtimeState.serviceRunning) "running" else "stopped"}")
                    Text("Broker: ${runtimeState.connectionState}")
                    Text("Mode: ${runtimeState.remoteMode.name.lowercase()} | root=${runtimeState.rootAvailable}")
                    Text("Active sessions: ${runtimeState.activeSessionCount}")
                    Text("Last event: ${runtimeState.lastEvent}")
                    val heartbeatLabel = runtimeState.lastHeartbeatAt?.let { "Last heartbeat: $it" } ?: "Last heartbeat: -"
                    Text(heartbeatLabel, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(text = "Device ID: ${store.deviceId()}", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = deviceName,
                onValueChange = {
                    deviceName = it
                    store.setDeviceName(it)
                },
                label = { Text("Device name") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = brokerUrl,
                onValueChange = {
                    brokerUrl = it
                    store.setBrokerUrl(it)
                },
                label = { Text("Broker URL") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pair code: $pairCode", modifier = Modifier.weight(1f))
                Button(onClick = {
                    pairCode = store.refreshPairCode()
                }) {
                    Text("Regenerate")
                }
            }
            ToggleRow(
                title = "Allow AI / agent control",
                checked = allowAgentControl,
                onCheckedChange = {
                    allowAgentControl = it
                    store.setAllowAgentControl(it)
                }
            )
            ToggleRow(
                title = "Auto start on boot",
                checked = autoStart,
                onCheckedChange = {
                    autoStart = it
                    store.setAutoStartEnabled(it)
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        ContextCompat.startForegroundService(
                            context,
                            Intent(context, RemoteHostForegroundService::class.java)
                                .setAction(RemoteHostForegroundService.ACTION_START)
                        )
                    }
                ) {
                    Text("Start Service")
                }
                Button(
                    onClick = {
                        context.startService(
                            Intent(context, RemoteHostForegroundService::class.java)
                                .setAction(RemoteHostForegroundService.ACTION_STOP)
                        )
                    }
                ) {
                    Text("Stop Service")
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Deployment checklist", style = MaterialTheme.typography.titleMedium)
                    Text("1. Keep this device dedicated if you want unattended control.")
                    Text("2. Prefer Root devices. Non-root compatibility is not complete yet.")
                    Text("3. Disable battery optimizations and enable accessibility when needed.")
                    Text(
                        "Battery optimization: ${if (ignoringBatteryOptimizations) "ignored" else "active"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }) {
                            Text("Accessibility")
                        }
                        Button(onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }) {
                            Text("Battery")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }) {
                            Text("App Info")
                        }
                        Button(onClick = {
                            pairCode = store.refreshPairCode()
                        }) {
                            Text("Rotate Pair Code")
                        }
                    }
                }
            }
            Text(
                text = "Compatibility mode placeholders are present for MediaProjection and Accessibility, but this build is optimized for the Root path first.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
