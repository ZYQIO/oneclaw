package ai.openclaw.app.ui

import android.content.Context
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ai.openclaw.app.GatewayConnectionMode
import ai.openclaw.app.MainViewModel
import ai.openclaw.app.accessibility.openLocalHostUiAutomationSettings
import ai.openclaw.app.dedicatedHostBackgroundPolicyNote
import ai.openclaw.app.isDedicatedHostBatteryOptimizationIgnored
import ai.openclaw.app.openDedicatedHostAppSettings
import ai.openclaw.app.requestDedicatedHostBatteryOptimizationExemption
import ai.openclaw.app.ui.mobileCardSurface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private enum class ConnectInputMode {
  SetupCode,
  Manual,
}

@Composable
fun ConnectTabScreen(viewModel: MainViewModel) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val statusText by viewModel.statusText.collectAsState()
  val isConnected by viewModel.isConnected.collectAsState()
  val remoteAddress by viewModel.remoteAddress.collectAsState()
  val connectionMode by viewModel.gatewayConnectionMode.collectAsState()
  val hasOpenAICodexCredential by viewModel.hasOpenAICodexCredential.collectAsState()
  val openAICodexAuthUiState by viewModel.openAICodexAuthUiState.collectAsState()
  val localHostRemoteAccessEnabled by viewModel.localHostRemoteAccessEnabled.collectAsState()
  val localHostRemoteAccessAdvancedCommandsEnabled by viewModel.localHostRemoteAccessAdvancedCommandsEnabled.collectAsState()
  val localHostRemoteAccessWriteCommandsEnabled by viewModel.localHostRemoteAccessWriteCommandsEnabled.collectAsState()
  val localHostRemoteAccessPort by viewModel.localHostRemoteAccessPort.collectAsState()
  val localHostRemoteAccessToken by viewModel.localHostRemoteAccessToken.collectAsState()
  val localHostRemoteAccessStatusText by viewModel.localHostRemoteAccessStatusText.collectAsState()
  val localHostRemoteAccessUrl by viewModel.localHostRemoteAccessUrl.collectAsState()
  val localHostUiAutomationStatus by viewModel.localHostUiAutomationStatus.collectAsState()
  val localHostDedicatedDeploymentEnabled by viewModel.localHostDedicatedDeploymentEnabled.collectAsState()
  val manualHost by viewModel.manualHost.collectAsState()
  val manualPort by viewModel.manualPort.collectAsState()
  val manualTls by viewModel.manualTls.collectAsState()
  val manualEnabled by viewModel.manualEnabled.collectAsState()
  val gatewayToken by viewModel.gatewayToken.collectAsState()
  val pendingTrust by viewModel.pendingGatewayTrust.collectAsState()

  var advancedOpen by rememberSaveable { mutableStateOf(false) }
  var inputMode by
    remember(manualEnabled, manualHost, gatewayToken) {
      mutableStateOf(
        if (manualEnabled || manualHost.isNotBlank() || gatewayToken.trim().isNotEmpty()) {
          ConnectInputMode.Manual
        } else {
          ConnectInputMode.SetupCode
        },
      )
    }
  var setupCode by rememberSaveable { mutableStateOf("") }
  var manualHostInput by rememberSaveable { mutableStateOf(manualHost.ifBlank { "10.0.2.2" }) }
  var manualPortInput by rememberSaveable { mutableStateOf(manualPort.toString()) }
  var manualTlsInput by rememberSaveable { mutableStateOf(manualTls) }
  var remoteAccessPortInput by rememberSaveable(localHostRemoteAccessPort) { mutableStateOf(localHostRemoteAccessPort.toString()) }
  var passwordInput by rememberSaveable { mutableStateOf("") }
  var manualAuthorizationInput by rememberSaveable { mutableStateOf("") }
  var validationText by rememberSaveable { mutableStateOf<String?>(null) }
  var batteryOptimizationIgnored by remember(context) { mutableStateOf(isDedicatedHostBatteryOptimizationIgnored(context)) }
  val backgroundPolicyNote = remember { dedicatedHostBackgroundPolicyNote() }
  DisposableEffect(context, lifecycleOwner) {
    viewModel.refreshLocalHostUiAutomationStatus()
    val observer =
      LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
          batteryOptimizationIgnored = isDedicatedHostBatteryOptimizationIgnored(context)
          viewModel.refreshLocalHostUiAutomationStatus()
        }
      }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }
  val remoteAccessExamples =
    remember(
      localHostRemoteAccessUrl,
      localHostRemoteAccessPort,
      localHostRemoteAccessAdvancedCommandsEnabled,
      localHostRemoteAccessWriteCommandsEnabled,
    ) {
      val baseUrl = localHostRemoteAccessUrl?.trim().orEmpty().ifEmpty { "http://<phone-ip>:${localHostRemoteAccessPort}" }
      buildString {
        append("curl -H 'Authorization: Bearer <TOKEN>' ")
        append("$baseUrl/api/local-host/v1/status")
        append("\n\n")
        append("curl -H 'Authorization: Bearer <TOKEN>' ")
        append("$baseUrl/api/local-host/v1/health")
        append("\n\n")
        append("curl -X POST -H 'Authorization: Bearer <TOKEN>' ")
        append("-H 'Content-Type: application/json' ")
        append("$baseUrl/api/local-host/v1/chat/send-wait ")
        append("-d '{\"message\":\"Summarize my notifications\",\"waitMs\":30000}'")
        append("\n\n")
        append("curl -H 'Authorization: Bearer <TOKEN>' ")
        append("'$baseUrl/api/local-host/v1/events?cursor=0&waitMs=20000'")
        append("\n\n")
        append("curl -X POST -H 'Authorization: Bearer <TOKEN>' ")
        append("-H 'Content-Type: application/json' ")
        append("$baseUrl/api/local-host/v1/invoke ")
        append("-d '{\"command\":\"device.status\"}'")
        if (localHostRemoteAccessAdvancedCommandsEnabled) {
          append("\n\n")
          append("curl -X POST -H 'Authorization: Bearer <TOKEN>' ")
          append("-H 'Content-Type: application/json' ")
          append("$baseUrl/api/local-host/v1/invoke ")
          append("-d '{\"command\":\"camera.snap\"}'")
        }
        if (localHostRemoteAccessWriteCommandsEnabled) {
          append("\n\n")
          append("curl -X POST -H 'Authorization: Bearer <TOKEN>' ")
          append("-H 'Content-Type: application/json' ")
          append("$baseUrl/api/local-host/v1/invoke ")
          append("-d '{\"command\":\"sms.send\",\"params\":{\"to\":\"+15551234567\",\"body\":\"Check in when you land.\"}}'")
          append("\n\n")
          append("curl -X POST -H 'Authorization: Bearer <TOKEN>' ")
          append("-H 'Content-Type: application/json' ")
          append("$baseUrl/api/local-host/v1/invoke ")
          append("-d '{\"command\":\"ui.tap\",\"params\":{\"text\":\"Chat\",\"matchMode\":\"exact\"}}'")
        }
      }
    }

  if (pendingTrust != null) {
    val prompt = pendingTrust!!
    AlertDialog(
      onDismissRequest = { viewModel.declineGatewayTrustPrompt() },
      containerColor = mobileCardSurface,
      title = { Text("Trust this gateway?", style = mobileHeadline, color = mobileText) },
      text = {
        Text(
          "First-time TLS connection.\n\nVerify this SHA-256 fingerprint before trusting:\n${prompt.fingerprintSha256}",
          style = mobileCallout,
          color = mobileText,
        )
      },
      confirmButton = {
        TextButton(
          onClick = { viewModel.acceptGatewayTrustPrompt() },
          colors = ButtonDefaults.textButtonColors(contentColor = mobileAccent),
        ) {
          Text("Trust and continue")
        }
      },
      dismissButton = {
        TextButton(
          onClick = { viewModel.declineGatewayTrustPrompt() },
          colors = ButtonDefaults.textButtonColors(contentColor = mobileTextSecondary),
        ) {
          Text("Cancel")
        }
      },
    )
  }

  val setupResolvedEndpoint = remember(setupCode) { decodeGatewaySetupCode(setupCode)?.url?.let { parseGatewayEndpoint(it)?.displayUrl } }
  val manualResolvedEndpoint = remember(manualHostInput, manualPortInput, manualTlsInput) {
    composeGatewayManualUrl(manualHostInput, manualPortInput, manualTlsInput)?.let { parseGatewayEndpoint(it)?.displayUrl }
  }

  val activeEndpoint =
    remember(isConnected, remoteAddress, setupResolvedEndpoint, manualResolvedEndpoint, inputMode, connectionMode) {
      when {
        connectionMode == GatewayConnectionMode.LocalHost && isConnected && !remoteAddress.isNullOrBlank() -> remoteAddress!!
        connectionMode == GatewayConnectionMode.LocalHost -> "This phone"
        isConnected && !remoteAddress.isNullOrBlank() -> remoteAddress!!
        inputMode == ConnectInputMode.SetupCode -> setupResolvedEndpoint ?: "Not set"
        else -> manualResolvedEndpoint ?: "Not set"
      }
    }

  Column(
    modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text("Gateway Connection", style = mobileTitle1, color = mobileText)
      Text(
        when {
          isConnected && connectionMode == GatewayConnectionMode.LocalHost -> "OpenClaw is running directly on this phone."
          isConnected -> "Your gateway is active and ready."
          connectionMode == GatewayConnectionMode.LocalHost ->
            "Run OpenClaw locally on this phone with Codex-backed chat and a limited on-device command set."
          else -> "Connect to your gateway to get started."
        },
        style = mobileCallout,
        color = mobileTextSecondary,
      )
    }

    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(14.dp),
      color = mobileSurface,
      border = BorderStroke(1.dp, mobileBorder),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text("Runtime mode", style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold), color = mobileTextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          MethodChip(
            label = "Local Host",
            active = connectionMode == GatewayConnectionMode.LocalHost,
            onClick = {
              viewModel.setGatewayConnectionMode(GatewayConnectionMode.LocalHost)
              validationText = null
            },
          )
          MethodChip(
            label = "Remote Gateway",
            active = connectionMode == GatewayConnectionMode.RemoteGateway,
            onClick = {
              viewModel.setGatewayConnectionMode(GatewayConnectionMode.RemoteGateway)
              validationText = null
            },
          )
        }
        if (connectionMode == GatewayConnectionMode.LocalHost) {
          Text(
            if (hasOpenAICodexCredential) {
              "Codex auth is present. Starting local host will use your on-device OAuth credential."
            } else {
              "Codex auth is still missing. Local host will start, but chat requests will fail until OAuth is added."
            },
            style = mobileCaption1,
            color = if (hasOpenAICodexCredential) mobileTextSecondary else mobileWarning,
          )
          Text(
            "Current scope is local chat plus selected Android device commands. It does not yet match the full desktop gateway tool/runtime surface.",
            style = mobileCaption1,
            color = mobileTextSecondary,
          )
        }
      }
    }

    if (connectionMode == GatewayConnectionMode.LocalHost) {
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = mobileCardSurface,
        border = BorderStroke(1.dp, mobileBorder),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text("UI automation", style = mobileHeadline, color = mobileText)
              Text(
                "Prepare on-device accessibility access for future `ui.state`, `ui.tap`, and bounded cross-app control.",
                style = mobileCallout,
                color = mobileTextSecondary,
              )
            }
            Button(
              onClick = { openLocalHostUiAutomationSettings(context) },
              shape = RoundedCornerShape(12.dp),
              colors = settingsPrimaryButtonColors(),
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            ) {
              Text(
                if (localHostUiAutomationStatus.enabled) "Review access" else "Enable",
                style = mobileCaption1.copy(fontWeight = FontWeight.Bold),
              )
            }
          }

          Text(
            localHostUiAutomationStatus.statusText,
            style = mobileCallout,
            color =
              when {
                localHostUiAutomationStatus.available -> mobileSuccess
                localHostUiAutomationStatus.enabled -> mobileWarning
                else -> mobileTextSecondary
              },
          )
          Text(
            localHostUiAutomationStatus.detailText,
            style = mobileCaption1,
            color = mobileTextSecondary,
          )
          Text(
            "Once enabled, local-host `/status` will report `uiAutomationAvailable` and a detailed `uiAutomation` readiness object.",
            style = mobileCaption1,
            color = mobileTextSecondary,
          )
        }
      }

      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = mobileCardSurface,
        border = BorderStroke(1.dp, mobileBorder),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text("Codex authentication", style = mobileHeadline, color = mobileText)
          val authStatusText =
            when {
              !openAICodexAuthUiState.errorText.isNullOrBlank() -> openAICodexAuthUiState.errorText!!
              !openAICodexAuthUiState.statusText.isNullOrBlank() -> openAICodexAuthUiState.statusText!!
              hasOpenAICodexCredential && !openAICodexAuthUiState.signedInEmail.isNullOrBlank() ->
                "Signed in as ${openAICodexAuthUiState.signedInEmail}"
              hasOpenAICodexCredential -> "OpenAI Codex OAuth is stored on this phone."
              else -> "Sign in once and local-host chat can call GPT through your Codex subscription."
            }
          Text(
            authStatusText,
            style = mobileCallout,
            color =
              when {
                !openAICodexAuthUiState.errorText.isNullOrBlank() -> mobileWarning
                hasOpenAICodexCredential -> mobileSuccess
                else -> mobileTextSecondary
              },
          )

          if (openAICodexAuthUiState.manualInputEnabled) {
            OutlinedTextField(
              value = manualAuthorizationInput,
              onValueChange = { manualAuthorizationInput = it },
              placeholder = {
                Text(
                  "Paste redirect URL or authorization code",
                  style = mobileBody,
                  color = mobileTextTertiary,
                )
              },
              modifier = Modifier.fillMaxWidth(),
              minLines = 2,
              maxLines = 4,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
              textStyle = mobileBody.copy(fontFamily = FontFamily.Monospace, color = mobileText),
              shape = RoundedCornerShape(14.dp),
              colors = outlinedColors(),
            )
          }

          Button(
            onClick = {
              validationText = null
              manualAuthorizationInput = ""
              viewModel.startOpenAICodexLogin()
            },
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(12.dp),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = mobileAccent,
                contentColor = Color.White,
              ),
          ) {
            Text(
              if (hasOpenAICodexCredential) "Reconnect Codex" else "Sign in with Codex",
              style = mobileCaption1.copy(fontWeight = FontWeight.Bold),
            )
          }

          if (openAICodexAuthUiState.manualInputEnabled) {
            Button(
              onClick = { viewModel.submitOpenAICodexManualInput(manualAuthorizationInput) },
              modifier = Modifier.fillMaxWidth().height(44.dp),
              shape = RoundedCornerShape(12.dp),
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = mobileSurface,
                  contentColor = mobileText,
                ),
              border = BorderStroke(1.dp, mobileBorderStrong),
            ) {
              Text("Paste code", style = mobileCaption1.copy(fontWeight = FontWeight.Bold))
            }
          }

          if (openAICodexAuthUiState.inProgress) {
            TextButton(onClick = { viewModel.cancelOpenAICodexLogin() }) {
              Text("Cancel", style = mobileCallout.copy(fontWeight = FontWeight.SemiBold), color = mobileAccent)
            }
          } else if (hasOpenAICodexCredential) {
            TextButton(onClick = { viewModel.clearOpenAICodexCredential() }) {
              Text("Clear auth", style = mobileCallout.copy(fontWeight = FontWeight.SemiBold), color = mobileDanger)
            }
          }
        }
      }
    }

    if (connectionMode == GatewayConnectionMode.LocalHost) {
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = mobileCardSurface,
        border = BorderStroke(1.dp, mobileBorder),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text("Dedicated host deployment", style = mobileHeadline, color = mobileText)
              Text(
                "Keep this idle phone in always-on host mode. OpenClaw will keep a foreground service up, restart after reboot or app updates, and auto-heal Local Host if it drops.",
                style = mobileCallout,
                color = mobileTextSecondary,
              )
            }
            Switch(
              checked = localHostDedicatedDeploymentEnabled,
              onCheckedChange = { viewModel.setLocalHostDedicatedDeploymentEnabled(it) },
              colors =
                SwitchDefaults.colors(
                  checkedThumbColor = mobileAccent,
                  checkedTrackColor = mobileAccentSoft,
                ),
            )
          }

          Text(
            if (localHostDedicatedDeploymentEnabled) {
              "Dedicated deployment is on. Disable it before expecting Local Host to stay offline after a disconnect."
            } else {
              "Dedicated deployment is off. Local Host only stays up while the app/session keeps it alive."
            },
            style = mobileCaption1,
            color = if (localHostDedicatedDeploymentEnabled) mobileSuccess else mobileTextSecondary,
          )

          AnimatedVisibility(visible = localHostDedicatedDeploymentEnabled) {
            Column(
              modifier = Modifier.fillMaxWidth(),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              HorizontalDivider(color = mobileBorder)
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Column(
                  modifier = Modifier.weight(1f),
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Text("Battery optimization", style = mobileHeadline, color = mobileText)
                  Text(
                    if (batteryOptimizationIgnored) {
                      "Unrestricted battery mode is active for OpenClaw. Android is less likely to delay dedicated-host recovery."
                    } else {
                      "Android battery optimization is still active. On idle phones, that can delay dedicated-host restarts after long idle periods."
                    },
                    style = mobileCallout,
                    color = mobileTextSecondary,
                  )
                }
                Button(
                  onClick = {
                    requestDedicatedHostBatteryOptimizationExemption(context)
                    batteryOptimizationIgnored = isDedicatedHostBatteryOptimizationIgnored(context)
                  },
                  shape = RoundedCornerShape(12.dp),
                  colors = settingsPrimaryButtonColors(),
                  contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                ) {
                  Text(
                    if (batteryOptimizationIgnored) "Review" else "Allow background",
                    style = mobileCaption1.copy(fontWeight = FontWeight.Bold),
                  )
                }
              }

              Text(
                if (batteryOptimizationIgnored) {
                  "Remote `device.status` now reports `backgroundExecution.batteryOptimizationIgnored=true` for this phone."
                } else {
                  "For trusted idle-phone deployments, Android's own docs treat battery-optimization exemption as one of the ways foreground-service restarts are less restricted."
                },
                style = mobileCaption1,
                color = if (batteryOptimizationIgnored) mobileSuccess else mobileWarning,
              )

              backgroundPolicyNote?.let { note ->
                HorizontalDivider(color = mobileBorder)
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Text("Background policy", style = mobileHeadline, color = mobileText)
                    Text(
                      note,
                      style = mobileCallout,
                      color = mobileTextSecondary,
                    )
                  }
                  Button(
                    onClick = { openDedicatedHostAppSettings(context) },
                    shape = RoundedCornerShape(12.dp),
                    colors = settingsPrimaryButtonColors(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                  ) {
                    Text(
                      "App settings",
                      style = mobileCaption1.copy(fontWeight = FontWeight.Bold),
                    )
                  }
                }

                Text(
                  if (batteryOptimizationIgnored) {
                    "Battery optimization is already exempted here, but that still does not protect OpenClaw from OEM task cleaners after a Recents swipe."
                  } else {
                    "Battery optimization exemption helps with idle recovery, but this phone may still force-stop OpenClaw if you swipe its Recents card away."
                  },
                  style = mobileCaption1,
                  color = mobileWarning,
                )
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text("Remote access", style = mobileHeadline, color = mobileText)
              Text(
                "Expose local-host chat and selected Android device commands on trusted networks or Tailscale.",
                style = mobileCallout,
                color = mobileTextSecondary,
              )
            }
            Switch(
              checked = localHostRemoteAccessEnabled,
              onCheckedChange = { viewModel.setLocalHostRemoteAccessEnabled(it) },
              colors =
                SwitchDefaults.colors(
                  checkedThumbColor = mobileAccent,
                  checkedTrackColor = mobileAccentSoft,
                ),
            )
          }

          Text(
            localHostRemoteAccessStatusText,
            style = mobileCallout,
            color =
              when {
                localHostRemoteAccessEnabled && !localHostRemoteAccessUrl.isNullOrBlank() -> mobileSuccess
                localHostRemoteAccessEnabled -> mobileWarning
                else -> mobileTextSecondary
              },
          )

          localHostRemoteAccessUrl?.takeIf { it.isNotBlank() }?.let { url ->
            Text(
              url,
              style = mobileBody.copy(fontFamily = FontFamily.Monospace),
              color = mobileText,
            )
          }

          OutlinedTextField(
            value = remoteAccessPortInput,
            onValueChange = { remoteAccessPortInput = it.filter(Char::isDigit) },
            placeholder = {
              Text(
                "Remote access port",
                style = mobileBody,
                color = mobileTextTertiary,
              )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = mobileBody.copy(fontFamily = FontFamily.Monospace, color = mobileText),
            shape = RoundedCornerShape(14.dp),
            colors = outlinedColors(),
          )

          OutlinedTextField(
            value = localHostRemoteAccessToken,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 3,
            textStyle = mobileBody.copy(fontFamily = FontFamily.Monospace, color = mobileText),
            shape = RoundedCornerShape(14.dp),
            colors = outlinedColors(),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text("Advanced remote commands", style = mobileCallout.copy(fontWeight = FontWeight.SemiBold), color = mobileText)
              Text(
                "Allow remote camera commands like `camera.list`, `camera.snap`, and `camera.clip`.",
                style = mobileCaption1,
                color = mobileTextSecondary,
              )
            }
            Switch(
              checked = localHostRemoteAccessAdvancedCommandsEnabled,
              onCheckedChange = { viewModel.setLocalHostRemoteAccessAdvancedCommandsEnabled(it) },
              colors =
                SwitchDefaults.colors(
                  checkedThumbColor = mobileAccent,
                  checkedTrackColor = mobileAccentSoft,
                ),
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text("Write remote commands", style = mobileCallout.copy(fontWeight = FontWeight.SemiBold), color = mobileText)
              Text(
                "Allow remote `sms.send`, `contacts.add`, `calendar.add`, `notifications.actions`, and bounded `ui.tap` / `ui.back` / `ui.home`. Enable this only on networks and clients you trust.",
                style = mobileCaption1,
                color = mobileTextSecondary,
              )
            }
            Switch(
              checked = localHostRemoteAccessWriteCommandsEnabled,
              onCheckedChange = { viewModel.setLocalHostRemoteAccessWriteCommandsEnabled(it) },
              colors =
                SwitchDefaults.colors(
                  checkedThumbColor = mobileAccent,
                  checkedTrackColor = mobileAccentSoft,
                ),
            )
          }

          Text(
            "Use header `Authorization: Bearer <token>` for `/status`, `/health`, `/events`, `/chat/*`, and `/invoke`.",
            style = mobileCaption1,
            color = mobileTextSecondary,
          )
          Text(
            "Probe `/status` first if you want a one-shot readiness snapshot with Codex auth, session counts, enabled command tiers, and UI automation readiness.",
            style = mobileCaption1,
            color = mobileTextSecondary,
          )
          Text(
            when {
              localHostRemoteAccessAdvancedCommandsEnabled && localHostRemoteAccessWriteCommandsEnabled ->
                "Remote `/invoke` allows the default read-control set plus camera and write-capable commands, including bounded UI actions."
              localHostRemoteAccessAdvancedCommandsEnabled ->
                "Remote `/invoke` allows the default read-control set plus camera commands."
              localHostRemoteAccessWriteCommandsEnabled ->
                "Remote `/invoke` allows the default read-control set plus write-capable commands, including bounded UI actions."
              else ->
                "Remote `/invoke` currently allows read-only device, location, notifications, contacts, calendar, photos, motion, call-log commands plus `system.notify`."
            },
            style = mobileCaption1,
            color = mobileTextSecondary,
          )

          OutlinedTextField(
            value = remoteAccessExamples,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            minLines = 8,
            maxLines = 12,
            textStyle = mobileCaption1.copy(fontFamily = FontFamily.Monospace, color = mobileText),
            shape = RoundedCornerShape(14.dp),
            colors = outlinedColors(),
          )

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = {
                val parsedPort = remoteAccessPortInput.toIntOrNull()
                if (parsedPort == null || parsedPort !in 1..65535) {
                  validationText = "Remote access port must be between 1 and 65535."
                } else {
                  validationText = null
                  viewModel.setLocalHostRemoteAccessPort(parsedPort)
                }
              },
              modifier = Modifier.height(44.dp),
              shape = RoundedCornerShape(12.dp),
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = mobileSurface,
                  contentColor = mobileText,
                ),
              border = BorderStroke(1.dp, mobileBorderStrong),
            ) {
              Text("Apply port", style = mobileCaption1.copy(fontWeight = FontWeight.Bold))
            }

            TextButton(onClick = { viewModel.regenerateLocalHostRemoteAccessToken() }) {
              Text(
                "Regenerate token",
                style = mobileCallout.copy(fontWeight = FontWeight.SemiBold),
                color = mobileAccent,
              )
            }
          }
        }
      }
    }

    // Status cards in a unified card group
    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(14.dp),
      color = mobileCardSurface,
      border = BorderStroke(1.dp, mobileBorder),
    ) {
      Column {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = mobileAccentSoft,
          ) {
            Icon(
              imageVector = Icons.Default.Link,
              contentDescription = null,
              modifier = Modifier.padding(8.dp).size(18.dp),
              tint = mobileAccent,
            )
          }
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Endpoint", style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold), color = mobileTextSecondary)
            Text(activeEndpoint, style = mobileBody.copy(fontFamily = FontFamily.Monospace), color = mobileText)
          }
        }
        HorizontalDivider(color = mobileBorder)
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (isConnected) mobileSuccessSoft else mobileSurface,
          ) {
            Icon(
              imageVector = Icons.Default.Cloud,
              contentDescription = null,
              modifier = Modifier.padding(8.dp).size(18.dp),
              tint = if (isConnected) mobileSuccess else mobileTextTertiary,
            )
          }
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Status", style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold), color = mobileTextSecondary)
            Text(statusText, style = mobileBody, color = if (isConnected) mobileSuccess else mobileText)
          }
        }
      }
    }

    if (isConnected) {
      // Outlined secondary button when connected — don't scream "danger"
      Button(
        onClick = {
          viewModel.disconnect()
          validationText = null
        },
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = mobileCardSurface,
            contentColor = mobileDanger,
          ),
        border = BorderStroke(1.dp, mobileDanger.copy(alpha = 0.4f)),
      ) {
        Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Disconnect", style = mobileHeadline.copy(fontWeight = FontWeight.SemiBold))
      }
    } else {
      Button(
        onClick = {
          if (connectionMode == GatewayConnectionMode.LocalHost) {
            validationText = null
            viewModel.setGatewayConnectionMode(GatewayConnectionMode.LocalHost)
            viewModel.connectManual()
            return@Button
          }

          if (statusText.contains("operator offline", ignoreCase = true)) {
            validationText = null
            viewModel.refreshGatewayConnection()
            return@Button
          }

          val config =
            resolveGatewayConnectConfig(
              useSetupCode = inputMode == ConnectInputMode.SetupCode,
              setupCode = setupCode,
              manualHost = manualHostInput,
              manualPort = manualPortInput,
              manualTls = manualTlsInput,
              fallbackToken = gatewayToken,
              fallbackPassword = passwordInput,
            )

          if (config == null) {
            validationText =
              if (inputMode == ConnectInputMode.SetupCode) {
                "Paste a valid setup code to connect."
              } else {
                "Enter a valid manual host and port to connect."
              }
            return@Button
          }

          validationText = null
          viewModel.setManualEnabled(true)
          viewModel.setManualHost(config.host)
          viewModel.setManualPort(config.port)
          viewModel.setManualTls(config.tls)
          viewModel.setGatewayBootstrapToken(config.bootstrapToken)
          if (config.token.isNotBlank()) {
            viewModel.setGatewayToken(config.token)
          } else if (config.bootstrapToken.isNotBlank()) {
            viewModel.setGatewayToken("")
          }
          viewModel.setGatewayPassword(config.password)
          viewModel.connectManual()
        },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = mobileAccent,
            contentColor = Color.White,
          ),
      ) {
        Text(
          if (connectionMode == GatewayConnectionMode.LocalHost) "Start Local Host" else "Connect Gateway",
          style = mobileHeadline.copy(fontWeight = FontWeight.Bold),
        )
      }
    }

    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(14.dp),
      color = mobileSurface,
      border = BorderStroke(1.dp, mobileBorder),
      onClick = { advancedOpen = !advancedOpen },
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text("Advanced controls", style = mobileHeadline, color = mobileText)
          Text("Setup code, endpoint, TLS, token, password, onboarding.", style = mobileCaption1, color = mobileTextSecondary)
        }
        Icon(
          imageVector = if (advancedOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
          contentDescription = if (advancedOpen) "Collapse advanced controls" else "Expand advanced controls",
          tint = mobileTextSecondary,
        )
      }
    }

    AnimatedVisibility(visible = advancedOpen) {
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = mobileCardSurface,
        border = BorderStroke(1.dp, mobileBorder),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          if (connectionMode == GatewayConnectionMode.LocalHost) {
            Text("Local host runs entirely on this phone.", style = mobileCallout, color = mobileTextSecondary)
            Text(
              "This mode reuses your Android app as the OpenClaw host. Remote access and native Codex login UI are the next steps.",
              style = mobileCaption1,
              color = mobileTextSecondary,
            )
            HorizontalDivider(color = mobileBorder)
            TextButton(onClick = { viewModel.setOnboardingCompleted(false) }) {
              Text("Run onboarding again", style = mobileCallout.copy(fontWeight = FontWeight.SemiBold), color = mobileAccent)
            }
          } else {
            Text("Connection method", style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold), color = mobileTextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              MethodChip(
                label = "Setup Code",
                active = inputMode == ConnectInputMode.SetupCode,
                onClick = { inputMode = ConnectInputMode.SetupCode },
              )
              MethodChip(
                label = "Manual",
                active = inputMode == ConnectInputMode.Manual,
                onClick = { inputMode = ConnectInputMode.Manual },
              )
            }

            Text("Run these on the gateway host:", style = mobileCallout, color = mobileTextSecondary)
            CommandBlock("openclaw qr --setup-code-only")
            CommandBlock("openclaw qr --json")

            if (inputMode == ConnectInputMode.SetupCode) {
              Text("Setup Code", style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold), color = mobileTextSecondary)
              OutlinedTextField(
                value = setupCode,
                onValueChange = {
                  setupCode = it
                  validationText = null
                },
                placeholder = { Text("Paste setup code", style = mobileBody, color = mobileTextTertiary) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                textStyle = mobileBody.copy(fontFamily = FontFamily.Monospace, color = mobileText),
                shape = RoundedCornerShape(14.dp),
                colors = outlinedColors(),
              )
              if (!setupResolvedEndpoint.isNullOrBlank()) {
                EndpointPreview(endpoint = setupResolvedEndpoint)
              }
            } else {
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickFillChip(
                  label = "Android Emulator",
                  onClick = {
                    manualHostInput = "10.0.2.2"
                    manualPortInput = "18789"
                    manualTlsInput = false
                    validationText = null
                  },
                )
                QuickFillChip(
                  label = "Localhost",
                  onClick = {
                    manualHostInput = "127.0.0.1"
                    manualPortInput = "18789"
                    manualTlsInput = false
                    validationText = null
                  },
                )
              }

              Text("Host", style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold), color = mobileTextSecondary)
              OutlinedTextField(
                value = manualHostInput,
                onValueChange = {
                  manualHostInput = it
                  validationText = null
                },
                placeholder = { Text("10.0.2.2", style = mobileBody, color = mobileTextTertiary) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                textStyle = mobileBody.copy(color = mobileText),
                shape = RoundedCornerShape(14.dp),
                colors = outlinedColors(),
              )

              Text("Port", style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold), color = mobileTextSecondary)
              OutlinedTextField(
                value = manualPortInput,
                onValueChange = {
                  manualPortInput = it
                  validationText = null
                },
                placeholder = { Text("18789", style = mobileBody, color = mobileTextTertiary) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = mobileBody.copy(fontFamily = FontFamily.Monospace, color = mobileText),
                shape = RoundedCornerShape(14.dp),
                colors = outlinedColors(),
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                  Text("Use TLS", style = mobileHeadline, color = mobileText)
                  Text("Switch to secure websocket (`wss`).", style = mobileCallout, color = mobileTextSecondary)
                }
                Switch(
                  checked = manualTlsInput,
                  onCheckedChange = {
                    manualTlsInput = it
                    validationText = null
                  },
                  colors =
                    SwitchDefaults.colors(
                      checkedTrackColor = mobileAccent,
                      uncheckedTrackColor = mobileBorderStrong,
                      checkedThumbColor = Color.White,
                      uncheckedThumbColor = Color.White,
                    ),
                )
              }

              Text("Token (optional)", style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold), color = mobileTextSecondary)
              OutlinedTextField(
                value = gatewayToken,
                onValueChange = { viewModel.setGatewayToken(it) },
                placeholder = { Text("token", style = mobileBody, color = mobileTextTertiary) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                textStyle = mobileBody.copy(color = mobileText),
                shape = RoundedCornerShape(14.dp),
                colors = outlinedColors(),
              )

              Text("Password (optional)", style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold), color = mobileTextSecondary)
              OutlinedTextField(
                value = passwordInput,
                onValueChange = { passwordInput = it },
                placeholder = { Text("password", style = mobileBody, color = mobileTextTertiary) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                textStyle = mobileBody.copy(color = mobileText),
                shape = RoundedCornerShape(14.dp),
                colors = outlinedColors(),
              )

              if (!manualResolvedEndpoint.isNullOrBlank()) {
                EndpointPreview(endpoint = manualResolvedEndpoint)
              }
            }

            HorizontalDivider(color = mobileBorder)

            TextButton(onClick = { viewModel.setOnboardingCompleted(false) }) {
              Text("Run onboarding again", style = mobileCallout.copy(fontWeight = FontWeight.SemiBold), color = mobileAccent)
            }
          }
        }
      }
    }

    if (!validationText.isNullOrBlank()) {
      Text(validationText!!, style = mobileCaption1, color = mobileWarning)
    }
  }
}

@Composable
private fun settingsPrimaryButtonColors() =
  ButtonDefaults.buttonColors(
    containerColor = mobileAccent,
    contentColor = Color.White,
    disabledContainerColor = mobileAccent.copy(alpha = 0.45f),
    disabledContentColor = Color.White.copy(alpha = 0.9f),
  )

@Composable
private fun MethodChip(label: String, active: Boolean, onClick: () -> Unit) {
  Button(
    onClick = onClick,
    modifier = Modifier.height(40.dp),
    shape = RoundedCornerShape(12.dp),
    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    colors =
      ButtonDefaults.buttonColors(
        containerColor = if (active) mobileAccent else mobileSurface,
        contentColor = if (active) Color.White else mobileText,
      ),
    border = BorderStroke(1.dp, if (active) mobileAccentBorderStrong else mobileBorderStrong),
  ) {
    Text(label, style = mobileCaption1.copy(fontWeight = FontWeight.Bold))
  }
}

@Composable
private fun QuickFillChip(label: String, onClick: () -> Unit) {
  Button(
    onClick = onClick,
    shape = RoundedCornerShape(999.dp),
    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    colors =
      ButtonDefaults.buttonColors(
        containerColor = mobileAccentSoft,
        contentColor = mobileAccent,
      ),
    elevation = null,
  ) {
    Text(label, style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold))
  }
}

@Composable
private fun CommandBlock(command: String) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    color = mobileCodeBg,
    border = BorderStroke(1.dp, mobileCodeBorder),
  ) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Box(modifier = Modifier.width(3.dp).height(42.dp).background(mobileCodeAccent))
      Text(
        text = command,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        style = mobileCallout.copy(fontFamily = FontFamily.Monospace),
        color = mobileCodeText,
      )
    }
  }
}

@Composable
private fun EndpointPreview(endpoint: String) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    HorizontalDivider(color = mobileBorder)
    Text("Resolved endpoint", style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold), color = mobileTextSecondary)
    Text(endpoint, style = mobileCallout.copy(fontFamily = FontFamily.Monospace), color = mobileText)
    HorizontalDivider(color = mobileBorder)
  }
}

@Composable
private fun outlinedColors() =
  OutlinedTextFieldDefaults.colors(
    focusedContainerColor = mobileSurface,
    unfocusedContainerColor = mobileSurface,
    focusedBorderColor = mobileAccent,
    unfocusedBorderColor = mobileBorder,
    focusedTextColor = mobileText,
    unfocusedTextColor = mobileText,
    cursorColor = mobileAccent,
  )
