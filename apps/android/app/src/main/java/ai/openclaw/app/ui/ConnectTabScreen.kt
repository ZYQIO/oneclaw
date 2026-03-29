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
import ai.openclaw.app.AppLanguage
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
import ai.openclaw.app.accessibility.LocalHostUiAutomationStatus
import ai.openclaw.app.auth.OpenAICodexAuthUiState
import ai.openclaw.app.auth.translateOpenAICodexMessage

private enum class ConnectInputMode {
  SetupCode,
  Manual,
}

@Composable
fun ConnectTabScreen(viewModel: MainViewModel) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val language = LocalAppLanguage.current
  fun t(english: String, simplifiedChinese: String): String = language.pick(english, simplifiedChinese)
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
      language,
      localHostRemoteAccessUrl,
      localHostRemoteAccessPort,
      localHostRemoteAccessAdvancedCommandsEnabled,
      localHostRemoteAccessWriteCommandsEnabled,
    ) {
      val summaryMessage = t("Summarize my notifications", "帮我总结一下通知")
      val inputTextValue = t("OpenClaw", "OpenClaw")
      val smsBody = t("Check in when you land.", "到了报个平安。")
      val chatTabLabel = t("Chat", "聊天")
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
        append("-d '{\"message\":\"$summaryMessage\",\"waitMs\":30000}'")
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
          append("-d '{\"command\":\"ui.launchApp\",\"params\":{\"packageName\":\"com.android.settings\"}}'")
          append("\n\n")
          append("curl -X POST -H 'Authorization: Bearer <TOKEN>' ")
          append("-H 'Content-Type: application/json' ")
          append("$baseUrl/api/local-host/v1/invoke ")
          append("-d '{\"command\":\"ui.inputText\",\"params\":{\"value\":\"$inputTextValue\"}}'")
          append("\n\n")
          append("curl -X POST -H 'Authorization: Bearer <TOKEN>' ")
          append("-H 'Content-Type: application/json' ")
          append("$baseUrl/api/local-host/v1/invoke ")
          append("-d '{\"command\":\"sms.send\",\"params\":{\"to\":\"+15551234567\",\"body\":\"$smsBody\"}}'")
          append("\n\n")
          append("curl -X POST -H 'Authorization: Bearer <TOKEN>' ")
          append("-H 'Content-Type: application/json' ")
          append("$baseUrl/api/local-host/v1/invoke ")
          append("-d '{\"command\":\"ui.tap\",\"params\":{\"text\":\"$chatTabLabel\",\"matchMode\":\"exact\"}}'")
        }
      }
    }
  val localizedStatusText = remember(language, statusText) { localizeConnectionStatus(language, statusText) }
  val localizedRemoteAccessStatusText =
    remember(language, localHostRemoteAccessStatusText) { localizeRemoteAccessStatus(language, localHostRemoteAccessStatusText) }
  val localizedUiAutomationStatusText =
    remember(language, localHostUiAutomationStatus) { uiAutomationStatusText(language, localHostUiAutomationStatus) }
  val localizedUiAutomationDetailText =
    remember(language, localHostUiAutomationStatus) { uiAutomationDetailText(language, localHostUiAutomationStatus) }
  val localizedAuthStatusText =
    remember(language, openAICodexAuthUiState, hasOpenAICodexCredential) {
      codexAuthStatusText(language, openAICodexAuthUiState, hasOpenAICodexCredential)
    }

  if (pendingTrust != null) {
    val prompt = pendingTrust!!
    AlertDialog(
      onDismissRequest = { viewModel.declineGatewayTrustPrompt() },
      containerColor = mobileCardSurface,
      title = { Text(t("Trust this gateway?", "信任这个 gateway 吗？"), style = mobileHeadline, color = mobileText) },
      text = {
        Text(
          t(
            "First-time TLS connection.\n\nVerify this SHA-256 fingerprint before trusting:\n${prompt.fingerprintSha256}",
            "这是第一次 TLS 连接。\n\n请在信任前核对这个 SHA-256 指纹：\n${prompt.fingerprintSha256}",
          ),
          style = mobileCallout,
          color = mobileText,
        )
      },
      confirmButton = {
        TextButton(
          onClick = { viewModel.acceptGatewayTrustPrompt() },
          colors = ButtonDefaults.textButtonColors(contentColor = mobileAccent),
        ) {
          Text(t("Trust and continue", "信任并继续"))
        }
      },
      dismissButton = {
        TextButton(
          onClick = { viewModel.declineGatewayTrustPrompt() },
          colors = ButtonDefaults.textButtonColors(contentColor = mobileTextSecondary),
        ) {
          Text(t("Cancel", "取消"))
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
        connectionMode == GatewayConnectionMode.LocalHost -> t("This phone", "这台手机")
        isConnected && !remoteAddress.isNullOrBlank() -> remoteAddress!!
        inputMode == ConnectInputMode.SetupCode -> setupResolvedEndpoint ?: t("Not set", "未设置")
        else -> manualResolvedEndpoint ?: t("Not set", "未设置")
      }
    }

  Column(
    modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(t("Gateway Connection", "Gateway 连接"), style = mobileTitle1, color = mobileText)
      Text(
        when {
          isConnected && connectionMode == GatewayConnectionMode.LocalHost -> t("OpenClaw is running directly on this phone.", "OpenClaw 正直接运行在这台手机上。")
          isConnected -> t("Your gateway is active and ready.", "你的 gateway 已激活并就绪。")
          connectionMode == GatewayConnectionMode.LocalHost ->
            t(
              "Run OpenClaw locally on this phone with Codex-backed chat and a limited on-device command set.",
              "在这台手机上本地运行 OpenClaw，使用 Codex 驱动聊天，并启用一组受限的机上命令。",
            )
          else -> t("Connect to your gateway to get started.", "先连接你的 gateway 再开始。")
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
        Text(t("Runtime mode", "运行模式"), style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold), color = mobileTextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          MethodChip(
            label = t("Local Host", "本机 Host"),
            active = connectionMode == GatewayConnectionMode.LocalHost,
            onClick = {
              viewModel.setGatewayConnectionMode(GatewayConnectionMode.LocalHost)
              validationText = null
            },
          )
          MethodChip(
            label = t("Remote Gateway", "远程 Gateway"),
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
              t(
                "Codex auth is present. Starting local host will use your on-device OAuth credential.",
                "已检测到 Codex 授权。启动本机 Host 时会使用手机上的 OAuth 凭证。",
              )
            } else {
              t(
                "Codex auth is still missing. Local host will start, but chat requests will fail until OAuth is added.",
                "还没有 Codex 授权。本机 Host 可以启动，但在补上 OAuth 前聊天请求会失败。",
              )
            },
            style = mobileCaption1,
            color = if (hasOpenAICodexCredential) mobileTextSecondary else mobileWarning,
          )
          Text(
            t(
              "Current scope is local chat plus selected Android device commands. It does not yet match the full desktop gateway tool/runtime surface.",
              "当前范围是本地聊天加部分 Android 设备命令，还没有覆盖桌面 gateway 的完整工具与 runtime 面。",
            ),
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
              Text(t("UI automation", "UI 自动化"), style = mobileHeadline, color = mobileText)
              Text(
                t(
                  "Prepare on-device accessibility access for `ui.state`, `ui.tap`, and future richer bounded cross-app control.",
                  "为 `ui.state`、`ui.tap` 以及后续更丰富的有边界跨 app 控制准备机上的无障碍权限。",
                ),
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
                if (localHostUiAutomationStatus.enabled) t("Review access", "查看权限") else t("Enable", "启用"),
                style = mobileCaption1.copy(fontWeight = FontWeight.Bold),
              )
            }
          }

          Text(
            localizedUiAutomationStatusText,
            style = mobileCallout,
            color =
              when {
                localHostUiAutomationStatus.available -> mobileSuccess
                localHostUiAutomationStatus.enabled -> mobileWarning
                else -> mobileTextSecondary
              },
          )
          Text(
            localizedUiAutomationDetailText,
            style = mobileCaption1,
            color = mobileTextSecondary,
          )
          Text(
            t(
              "Once enabled, local-host `/status` will report `uiAutomationAvailable` and a detailed `uiAutomation` readiness object.",
              "启用后，本机 Host 的 `/status` 会返回 `uiAutomationAvailable` 和详细的 `uiAutomation` readiness 对象。",
            ),
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
          Text(t("Codex authentication", "Codex 授权"), style = mobileHeadline, color = mobileText)
          Text(
            localizedAuthStatusText,
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
                  t("Paste redirect URL or authorization code", "粘贴回跳 URL 或授权码"),
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
              if (hasOpenAICodexCredential) t("Reconnect Codex", "重新连接 Codex") else t("Sign in with Codex", "使用 Codex 登录"),
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
              Text(t("Paste code", "粘贴授权码"), style = mobileCaption1.copy(fontWeight = FontWeight.Bold))
            }
          }

          if (openAICodexAuthUiState.inProgress) {
            TextButton(onClick = { viewModel.cancelOpenAICodexLogin() }) {
              Text(t("Cancel", "取消"), style = mobileCallout.copy(fontWeight = FontWeight.SemiBold), color = mobileAccent)
            }
          } else if (hasOpenAICodexCredential) {
            TextButton(onClick = { viewModel.clearOpenAICodexCredential() }) {
              Text(t("Clear auth", "清除授权"), style = mobileCallout.copy(fontWeight = FontWeight.SemiBold), color = mobileDanger)
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
              Text(t("Dedicated host deployment", "专机部署"), style = mobileHeadline, color = mobileText)
              Text(
                t(
                  "Keep this idle phone in always-on host mode. OpenClaw will keep a foreground service up, restart after reboot or app updates, and auto-heal Local Host if it drops.",
                  "把这台闲置手机保持在常驻 host 模式。OpenClaw 会维持前台服务，在重启或应用更新后恢复，并在本机 Host 掉线时自动自愈。",
                ),
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
              t(
                "Dedicated deployment is on. Disable it before expecting Local Host to stay offline after a disconnect.",
                "专机部署已开启。如果你希望断开后本机 Host 保持离线，请先关闭它。",
              )
            } else {
              t(
                "Dedicated deployment is off. Local Host only stays up while the app/session keeps it alive.",
                "专机部署已关闭。本机 Host 只会在 App 或当前会话维持它时保持在线。",
              )
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
                  Text(t("Battery optimization", "电池优化"), style = mobileHeadline, color = mobileText)
                  Text(
                    if (batteryOptimizationIgnored) {
                      t(
                        "Unrestricted battery mode is active for OpenClaw. Android is less likely to delay dedicated-host recovery.",
                        "OpenClaw 已启用不受限制的电池模式，Android 更不容易延迟专机恢复。",
                      )
                    } else {
                      t(
                        "Android battery optimization is still active. On idle phones, that can delay dedicated-host restarts after long idle periods.",
                        "Android 电池优化仍在生效。对闲置手机来说，这可能会让专机在长时间空闲后恢复得更慢。",
                      )
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
                    if (batteryOptimizationIgnored) t("Review", "查看") else t("Allow background", "允许后台"),
                    style = mobileCaption1.copy(fontWeight = FontWeight.Bold),
                  )
                }
              }

              Text(
                if (batteryOptimizationIgnored) {
                  t(
                    "Remote `device.status` now reports `backgroundExecution.batteryOptimizationIgnored=true` for this phone.",
                    "远端 `device.status` 现在会为这台手机返回 `backgroundExecution.batteryOptimizationIgnored=true`。",
                  )
                } else {
                  t(
                    "For trusted idle-phone deployments, Android's own docs treat battery-optimization exemption as one of the ways foreground-service restarts are less restricted.",
                    "对可信的闲置手机部署，Android 官方文档把电池优化豁免视为放宽前台服务重启限制的方式之一。",
                  )
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
                    Text(t("Background policy", "后台策略"), style = mobileHeadline, color = mobileText)
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
                      t("App settings", "应用设置"),
                      style = mobileCaption1.copy(fontWeight = FontWeight.Bold),
                    )
                  }
                }

                Text(
                  if (batteryOptimizationIgnored) {
                    t(
                      "Battery optimization is already exempted here, but that still does not protect OpenClaw from OEM task cleaners after a Recents swipe.",
                      "这里虽然已经获得电池优化豁免，但这仍然不能防止 OpenClaw 在最近任务划卡后被 OEM 的任务清理机制杀掉。",
                    )
                  } else {
                    t(
                      "Battery optimization exemption helps with idle recovery, but this phone may still force-stop OpenClaw if you swipe its Recents card away.",
                      "电池优化豁免有助于空闲恢复，但如果你把最近任务卡片划掉，这台手机仍可能直接 force-stop OpenClaw。",
                    )
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
              Text(t("Remote access", "远程访问"), style = mobileHeadline, color = mobileText)
              Text(
                t(
                  "Expose local-host chat and selected Android device commands on trusted networks or Tailscale.",
                  "在可信网络或 Tailscale 上暴露本机 Host 聊天和部分 Android 设备命令。",
                ),
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
            localizedRemoteAccessStatusText,
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
                t("Remote access port", "远程访问端口"),
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
              Text(t("Advanced remote commands", "高级远程命令"), style = mobileCallout.copy(fontWeight = FontWeight.SemiBold), color = mobileText)
              Text(
                t(
                  "Allow remote camera commands like `camera.list`, `camera.snap`, and `camera.clip`.",
                  "允许远程相机命令，例如 `camera.list`、`camera.snap` 和 `camera.clip`。",
                ),
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
              Text(t("Write remote commands", "写入型远程命令"), style = mobileCallout.copy(fontWeight = FontWeight.SemiBold), color = mobileText)
              Text(
                t(
                  "Allow remote `sms.send`, `contacts.add`, `calendar.add`, `notifications.actions`, `ui.launchApp`, `ui.inputText`, and bounded `ui.tap` / `ui.back` / `ui.home`. Enable this only on networks and clients you trust.",
                  "允许远程 `sms.send`、`contacts.add`、`calendar.add`、`notifications.actions`、`ui.launchApp`、`ui.inputText` 以及有边界的 `ui.tap` / `ui.back` / `ui.home`。只应在你信任的网络和客户端上启用。",
                ),
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
            t(
              "Use header `Authorization: Bearer <token>` for `/status`, `/health`, `/events`, `/chat/*`, and `/invoke`.",
              "调用 `/status`、`/health`、`/events`、`/chat/*` 和 `/invoke` 时，请使用请求头 `Authorization: Bearer <token>`。",
            ),
            style = mobileCaption1,
            color = mobileTextSecondary,
          )
          Text(
            t(
              "Probe `/status` first if you want a one-shot readiness snapshot with Codex auth, session counts, enabled command tiers, and UI automation readiness.",
              "如果你想一次拿到包含 Codex 授权、会话数量、已启用命令层级和 UI 自动化 readiness 的快照，请先探测 `/status`。",
            ),
            style = mobileCaption1,
            color = mobileTextSecondary,
          )
          Text(
            when {
              localHostRemoteAccessAdvancedCommandsEnabled && localHostRemoteAccessWriteCommandsEnabled ->
                t(
                  "Remote `/invoke` allows the default read-control set plus camera and write-capable commands, including bounded UI actions.",
                  "远程 `/invoke` 允许默认只读控制集，再加上相机和可写命令，包括有边界的 UI 动作。",
                )
              localHostRemoteAccessAdvancedCommandsEnabled ->
                t(
                  "Remote `/invoke` allows the default read-control set plus camera commands.",
                  "远程 `/invoke` 允许默认只读控制集，再加上相机命令。",
                )
              localHostRemoteAccessWriteCommandsEnabled ->
                t(
                  "Remote `/invoke` allows the default read-control set plus write-capable commands, including bounded UI actions.",
                  "远程 `/invoke` 允许默认只读控制集，再加上可写命令，包括有边界的 UI 动作。",
                )
              else ->
                t(
                  "Remote `/invoke` currently allows read-only device, location, notifications, contacts, calendar, photos, motion, call-log commands plus `system.notify`.",
                  "远程 `/invoke` 当前允许只读的 device、location、notifications、contacts、calendar、photos、motion、call-log 命令，以及 `system.notify`。",
                )
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
                  validationText = t("Remote access port must be between 1 and 65535.", "远程访问端口必须在 1 到 65535 之间。")
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
              Text(t("Apply port", "应用端口"), style = mobileCaption1.copy(fontWeight = FontWeight.Bold))
            }

            TextButton(onClick = { viewModel.regenerateLocalHostRemoteAccessToken() }) {
              Text(
                t("Regenerate token", "重新生成 token"),
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
            Text(t("Endpoint", "端点"), style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold), color = mobileTextSecondary)
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
            Text(t("Status", "状态"), style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold), color = mobileTextSecondary)
            Text(localizedStatusText, style = mobileBody, color = if (isConnected) mobileSuccess else mobileText)
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
        Text(t("Disconnect", "断开连接"), style = mobileHeadline.copy(fontWeight = FontWeight.SemiBold))
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
                t("Paste a valid setup code to connect.", "请粘贴有效的 setup code 以建立连接。")
              } else {
                t("Enter a valid manual host and port to connect.", "请输入有效的手动 host 和 port 以建立连接。")
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
          if (connectionMode == GatewayConnectionMode.LocalHost) t("Start Local Host", "启动本机 Host") else t("Connect Gateway", "连接 Gateway"),
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
          Text(t("Advanced controls", "高级控制"), style = mobileHeadline, color = mobileText)
          Text(gatewayAdvancedControlsDescription(language), style = mobileCaption1, color = mobileTextSecondary)
        }
        Icon(
          imageVector = if (advancedOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
          contentDescription = if (advancedOpen) t("Collapse advanced controls", "收起高级控制") else t("Expand advanced controls", "展开高级控制"),
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
            Text(t("Local host runs entirely on this phone.", "本机 Host 完全运行在这台手机上。"), style = mobileCallout, color = mobileTextSecondary)
            Text(
              t(
                "This mode reuses your Android app as the OpenClaw host. Remote access and native Codex login UI are the next steps.",
                "这个模式会把你的 Android App 直接作为 OpenClaw host。下一步重点是远程访问和原生 Codex 登录 UI。",
              ),
              style = mobileCaption1,
              color = mobileTextSecondary,
            )
            HorizontalDivider(color = mobileBorder)
            TextButton(onClick = { viewModel.setOnboardingCompleted(false) }) {
              Text(t("Run onboarding again", "重新运行 onboarding"), style = mobileCallout.copy(fontWeight = FontWeight.SemiBold), color = mobileAccent)
            }
          } else {
            Text(t("Connection method", "连接方式"), style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold), color = mobileTextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              MethodChip(
                label = gatewaySetupCodeLabel(language),
                active = inputMode == ConnectInputMode.SetupCode,
                onClick = { inputMode = ConnectInputMode.SetupCode },
              )
              MethodChip(
                label = t("Manual", "手动"),
                active = inputMode == ConnectInputMode.Manual,
                onClick = { inputMode = ConnectInputMode.Manual },
              )
            }

            Text(t("Run these on the gateway host:", "在 gateway 主机上运行这些命令："), style = mobileCallout, color = mobileTextSecondary)
            CommandBlock("openclaw qr --setup-code-only")
            CommandBlock("openclaw qr --json")

            if (inputMode == ConnectInputMode.SetupCode) {
              Text(gatewaySetupCodeLabel(language), style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold), color = mobileTextSecondary)
              OutlinedTextField(
                value = setupCode,
                onValueChange = {
                  setupCode = it
                  validationText = null
                },
                placeholder = { Text(gatewaySetupCodePlaceholder(language), style = mobileBody, color = mobileTextTertiary) },
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
                  label = t("Android Emulator", "Android 模拟器"),
                  onClick = {
                    manualHostInput = "10.0.2.2"
                    manualPortInput = "18789"
                    manualTlsInput = false
                    validationText = null
                  },
                )
                QuickFillChip(
                  label = t("Localhost", "本机"),
                  onClick = {
                    manualHostInput = "127.0.0.1"
                    manualPortInput = "18789"
                    manualTlsInput = false
                    validationText = null
                  },
                )
              }

              Text(gatewayHostLabel(language), style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold), color = mobileTextSecondary)
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

              Text(t("Port", "端口"), style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold), color = mobileTextSecondary)
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
                  Text(t("Use TLS", "使用 TLS"), style = mobileHeadline, color = mobileText)
                  Text(t("Switch to secure websocket (`wss`).", "切换到安全 websocket（`wss`）。"), style = mobileCallout, color = mobileTextSecondary)
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

              Text(gatewayTokenLabel(language), style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold), color = mobileTextSecondary)
              OutlinedTextField(
                value = gatewayToken,
                onValueChange = { viewModel.setGatewayToken(it) },
                placeholder = { Text(onboardingTokenPlaceholder(language), style = mobileBody, color = mobileTextTertiary) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                textStyle = mobileBody.copy(color = mobileText),
                shape = RoundedCornerShape(14.dp),
                colors = outlinedColors(),
              )

              Text(gatewayPasswordLabel(language), style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold), color = mobileTextSecondary)
              OutlinedTextField(
                value = passwordInput,
                onValueChange = { passwordInput = it },
                placeholder = { Text(onboardingPasswordPlaceholder(language), style = mobileBody, color = mobileTextTertiary) },
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
              Text(t("Run onboarding again", "重新运行 onboarding"), style = mobileCallout.copy(fontWeight = FontWeight.SemiBold), color = mobileAccent)
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
  val language = LocalAppLanguage.current
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    HorizontalDivider(color = mobileBorder)
    Text(language.pick("Resolved endpoint", "解析后的端点"), style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold), color = mobileTextSecondary)
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

private fun uiAutomationStatusText(
  language: AppLanguage,
  status: LocalHostUiAutomationStatus,
): String {
  return when {
    status.available -> language.pick("UI automation is ready.", "UI 自动化已就绪。")
    status.enabled -> language.pick("UI automation is enabled and waiting for the service to bind.", "UI 自动化已启用，正在等待服务绑定。")
    else -> language.pick("UI automation is off.", "UI 自动化已关闭。")
  }
}

private fun uiAutomationDetailText(
  language: AppLanguage,
  status: LocalHostUiAutomationStatus,
): String {
  return when {
    status.available ->
      language.pick(
        "OpenClaw can now expose accessibility-backed UI readiness for future cross-app control.",
        "OpenClaw 现在可以暴露基于无障碍服务的 UI readiness，为后续跨 app 控制做准备。",
      )
    status.enabled ->
      language.pick(
        "Accessibility access is on, but OpenClaw has not yet observed a live service binding in this process.",
        "无障碍权限已经打开，但 OpenClaw 还没有在当前进程里观察到活跃的服务绑定。",
      )
    else ->
      language.pick(
        "Enable the OpenClaw accessibility service to prepare `ui.state`, `ui.tap`, and other bounded phone-control actions.",
        "启用 OpenClaw 无障碍服务，为 `ui.state`、`ui.tap` 等有边界的手机控制动作做准备。",
      )
  }
}

private fun codexAuthStatusText(
  language: AppLanguage,
  uiState: OpenAICodexAuthUiState,
  hasCredential: Boolean,
): String {
  if (!uiState.errorText.isNullOrBlank()) {
    return translateKnownCodexMessage(language, uiState.errorText!!)
  }
  if (!uiState.statusText.isNullOrBlank()) {
    return translateKnownCodexMessage(language, uiState.statusText!!)
  }
  if (hasCredential && !uiState.signedInEmail.isNullOrBlank()) {
    return language.pick(
      "Signed in as ${uiState.signedInEmail}",
      "已登录为 ${uiState.signedInEmail}",
    )
  }
  if (hasCredential) {
    return language.pick(
      "OpenAI Codex OAuth is stored on this phone.",
      "OpenAI Codex OAuth 已保存在这台手机上。",
    )
  }
  return language.pick(
    "Sign in once and local-host chat can call GPT through your Codex subscription.",
    "登录一次后，本机 Host 聊天就可以通过你的 Codex 订阅调用 GPT。",
  )
}

internal fun translateKnownCodexMessage(
  language: AppLanguage,
  message: String,
): String {
  return translateOpenAICodexMessage(language, message)
}
