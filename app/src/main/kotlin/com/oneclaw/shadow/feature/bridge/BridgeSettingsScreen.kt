package com.oneclaw.shadow.feature.bridge

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.Button
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.oneclaw.shadow.bridge.service.MessagingBridgeService
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BridgeSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: BridgeSettingsViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Restart bridge when leaving screen so credential/token changes take effect
    DisposableEffect(Unit) {
        onDispose {
            if (viewModel.uiState.value.bridgeEnabled) {
                MessagingBridgeService.restart(context)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Messaging Bridge") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Helper: restart bridge service to pick up channel changes
            fun restartServiceIfRunning() {
                if (state.bridgeEnabled) {
                    MessagingBridgeService.restart(context)
                }
            }

            // Master bridge toggle
            BridgeSwitchRow(
                label = "Enable Messaging Bridge",
                checked = state.bridgeEnabled,
                onCheckedChange = { enabled ->
                    viewModel.toggleBridge(enabled)
                    if (enabled) {
                        MessagingBridgeService.start(context)
                    } else {
                        MessagingBridgeService.stop(context)
                    }
                }
            )

            if (state.serviceRunning) {
                Text(
                    text = "Service is running",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
                var broadcastText by remember { mutableStateOf("") }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = broadcastText,
                        onValueChange = { broadcastText = it },
                        label = { Text("Message to broadcast") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (broadcastText.isNotBlank()) {
                                MessagingBridgeService.broadcast(context, broadcastText)
                                broadcastText = ""
                            }
                        }
                    ) {
                        Text("Send")
                    }
                }
            }

            BridgeSwitchRow(
                label = "Keep CPU awake (Wake Lock)",
                checked = state.wakeLockEnabled,
                onCheckedChange = { viewModel.toggleWakeLock(it) }
            )
            Row(
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Keeps the bridge service alive when the screen is off. Required for reliable message delivery, but increases battery usage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Telegram
            ChannelSection(
                title = "Telegram",
                enabled = state.telegramEnabled,
                onEnabledChange = { viewModel.toggleTelegram(it); restartServiceIfRunning() },
                setupSteps = listOf(
                    "Open Telegram and search for @BotFather",
                    "Send /newbot and follow the prompts to create a bot",
                    "Copy the bot token provided by BotFather",
                    "To find your user ID, search for @userinfobot and send it any message"
                )
            ) {
                BridgeTextField(
                    label = "Bot Token",
                    value = state.telegramBotToken,
                    onValueChange = { viewModel.updateTelegramBotToken(it) }
                )
                BridgeTextField(
                    label = "Allowed User IDs (comma-separated)",
                    value = state.telegramAllowedUserIds,
                    onValueChange = { viewModel.updateTelegramAllowedUserIds(it) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Discord
            ChannelSection(
                title = "Discord",
                enabled = state.discordEnabled,
                onEnabledChange = { viewModel.toggleDiscord(it); restartServiceIfRunning() },
                setupSteps = listOf(
                    "Go to discord.com/developers and create a New Application",
                    "Go to the Bot tab and click Reset Token to get your bot token",
                    "Under Privileged Gateway Intents, enable Message Content Intent",
                    "Go to OAuth2 > URL Generator, select 'bot' scope with Send Messages permission",
                    "Use the generated URL to invite the bot to your server",
                    "Enable Developer Mode in Discord settings, then right-click your username to copy your user ID"
                )
            ) {
                BridgeTextField(
                    label = "Bot Token",
                    value = state.discordBotToken,
                    onValueChange = { viewModel.updateDiscordBotToken(it) }
                )
                BridgeTextField(
                    label = "Allowed User IDs (comma-separated)",
                    value = state.discordAllowedUserIds,
                    onValueChange = { viewModel.updateDiscordAllowedUserIds(it) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Slack
            ChannelSection(
                title = "Slack",
                enabled = state.slackEnabled,
                onEnabledChange = { viewModel.toggleSlack(it); restartServiceIfRunning() },
                setupSteps = listOf(
                    "Go to api.slack.com/apps and click Create New App",
                    "Under OAuth & Permissions, add scopes: chat:write, channels:history, im:history",
                    "Install the app to your workspace and copy the Bot User OAuth Token (xoxb-...)",
                    "Under Basic Information > App-Level Tokens, generate a token with connections:write scope (xapp-...)",
                    "Enable Socket Mode under Socket Mode settings",
                    "Enable Event Subscriptions and subscribe to message.im event"
                )
            ) {
                BridgeTextField(
                    label = "Bot Token (xoxb-...)",
                    value = state.slackBotToken,
                    onValueChange = { viewModel.updateSlackBotToken(it) }
                )
                BridgeTextField(
                    label = "App Token (xapp-...)",
                    value = state.slackAppToken,
                    onValueChange = { viewModel.updateSlackAppToken(it) }
                )
                BridgeTextField(
                    label = "Allowed User IDs (comma-separated)",
                    value = state.slackAllowedUserIds,
                    onValueChange = { viewModel.updateSlackAllowedUserIds(it) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Matrix
            ChannelSection(
                title = "Matrix",
                enabled = state.matrixEnabled,
                onEnabledChange = { viewModel.toggleMatrix(it); restartServiceIfRunning() },
                setupSteps = listOf(
                    "Register a new bot account on your Matrix homeserver",
                    "Log in to Element with the bot account and go to Settings > Help & About to get the access token",
                    "Invite the bot to the rooms you want it to monitor",
                    "Add allowed user IDs in the format @user:homeserver.org"
                )
            ) {
                BridgeTextField(
                    label = "Homeserver URL",
                    value = state.matrixHomeserver,
                    onValueChange = { viewModel.updateMatrixHomeserver(it) }
                )
                BridgeTextField(
                    label = "Access Token",
                    value = state.matrixAccessToken,
                    onValueChange = { viewModel.updateMatrixAccessToken(it) }
                )
                BridgeTextField(
                    label = "Allowed User IDs (comma-separated)",
                    value = state.matrixAllowedUserIds,
                    onValueChange = { viewModel.updateMatrixAllowedUserIds(it) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // LINE
            ChannelSection(
                title = "LINE",
                enabled = state.lineEnabled,
                onEnabledChange = { viewModel.toggleLine(it); restartServiceIfRunning() },
                setupSteps = listOf(
                    "Go to developers.line.biz and create a new provider",
                    "Create a Messaging API channel under the provider",
                    "In the channel settings, issue a Channel Access Token (long-lived)",
                    "Copy the Channel Secret from the Basic Settings tab",
                    "Set the webhook URL to your device's address with the configured port"
                )
            ) {
                BridgeTextField(
                    label = "Channel Access Token",
                    value = state.lineChannelAccessToken,
                    onValueChange = { viewModel.updateLineChannelAccessToken(it) }
                )
                BridgeTextField(
                    label = "Channel Secret",
                    value = state.lineChannelSecret,
                    onValueChange = { viewModel.updateLineChannelSecret(it) }
                )
                BridgeTextField(
                    label = "Webhook Port",
                    value = state.lineWebhookPort.toString(),
                    onValueChange = { viewModel.updateLineWebhookPort(it.toIntOrNull() ?: state.lineWebhookPort) },
                    keyboardType = KeyboardType.Number
                )
                BridgeTextField(
                    label = "Allowed User IDs (comma-separated)",
                    value = state.lineAllowedUserIds,
                    onValueChange = { viewModel.updateLineAllowedUserIds(it) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // WebChat
            ChannelSection(
                title = "Web Chat",
                enabled = state.webChatEnabled,
                onEnabledChange = { viewModel.toggleWebChat(it); restartServiceIfRunning() },
                setupSteps = listOf(
                    "Choose a port number (default 8080)",
                    "Optionally set an access token to restrict who can connect",
                    "Access the chat at http://<device-ip>:<port> from any browser on the same network"
                )
            ) {
                BridgeTextField(
                    label = "Access Token (optional)",
                    value = state.webChatAccessToken,
                    onValueChange = { viewModel.updateWebChatAccessToken(it) }
                )
                BridgeTextField(
                    label = "Port",
                    value = state.webChatPort.toString(),
                    onValueChange = { viewModel.updateWebChatPort(it.toIntOrNull() ?: state.webChatPort) },
                    keyboardType = KeyboardType.Number
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ChannelSection(
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    setupSteps: List<String> = emptyList(),
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            // Single-line header: channel name + switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }

            // Expanded content when enabled
            AnimatedVisibility(visible = enabled) {
                Column {
                    content()
                    if (setupSteps.isNotEmpty()) {
                        SetupGuide(steps = setupSteps)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun SetupGuide(steps: List<String>) {
    var expanded by remember { mutableStateOf(false) }
    Text(
        text = if (expanded) "Hide setup guide" else "Setup guide",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clickable { expanded = !expanded }
            .padding(vertical = 8.dp)
    )
    AnimatedVisibility(visible = expanded) {
        Column(modifier = Modifier.padding(bottom = 4.dp)) {
            steps.forEachIndexed { index, step ->
                Text(
                    text = "${index + 1}. $step",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun BridgeSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun BridgeTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}
