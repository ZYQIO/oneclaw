package ai.openclaw.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ai.openclaw.app.MainViewModel
import ai.openclaw.app.ui.localizeConnectionStatus
import ai.openclaw.app.ui.localizeMicCaptureStatus
import ai.openclaw.app.ui.mobileAccent
import ai.openclaw.app.ui.mobileAccentSoft
import ai.openclaw.app.ui.mobileBackgroundGradient
import ai.openclaw.app.ui.mobileBorder
import ai.openclaw.app.ui.mobileBorderStrong
import ai.openclaw.app.ui.mobileCallout
import ai.openclaw.app.ui.mobileCaption1
import ai.openclaw.app.ui.mobileCaption2
import ai.openclaw.app.ui.mobileCardSurface
import ai.openclaw.app.ui.mobileDanger
import ai.openclaw.app.ui.mobileDangerSoft
import ai.openclaw.app.ui.mobileHeadline
import ai.openclaw.app.ui.mobileSuccess
import ai.openclaw.app.ui.mobileSuccessSoft
import ai.openclaw.app.ui.mobileSurface
import ai.openclaw.app.ui.mobileSurfaceStrong
import ai.openclaw.app.ui.mobileText
import ai.openclaw.app.ui.mobileTextSecondary
import ai.openclaw.app.ui.mobileTextTertiary
import ai.openclaw.app.ui.mobileWarning
import ai.openclaw.app.ui.pick
import ai.openclaw.app.voice.VoiceConversationEntry
import ai.openclaw.app.voice.VoiceConversationRole
import kotlin.math.max

@Composable
fun VoiceTabScreen(viewModel: MainViewModel) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val activity = remember(context) { context.findActivity() }
  val listState = rememberLazyListState()
  val language = LocalAppLanguage.current
  fun t(english: String, simplifiedChinese: String): String = language.pick(english, simplifiedChinese)

  val gatewayStatus by viewModel.statusText.collectAsState()
  val micEnabled by viewModel.micEnabled.collectAsState()
  val micCooldown by viewModel.micCooldown.collectAsState()
  val speakerEnabled by viewModel.speakerEnabled.collectAsState()
  val micStatusText by viewModel.micStatusText.collectAsState()
  val talkStatusText by viewModel.talkStatusText.collectAsState()
  val micLiveTranscript by viewModel.micLiveTranscript.collectAsState()
  val micQueuedMessages by viewModel.micQueuedMessages.collectAsState()
  val micConversation by viewModel.micConversation.collectAsState()
  val micInputLevel by viewModel.micInputLevel.collectAsState()
  val micIsSending by viewModel.micIsSending.collectAsState()
  val localizedGatewayStatus = remember(language, gatewayStatus) { localizeConnectionStatus(language, gatewayStatus) }
  val localizedMicStatus = remember(language, micStatusText) { localizeMicCaptureStatus(language, micStatusText) }
  val localizedTalkStatus = remember(language, talkStatusText) { localizeTalkModeStatus(language, talkStatusText) }

  val hasStreamingAssistant = micConversation.any { it.role == VoiceConversationRole.Assistant && it.isStreaming }
  val showThinkingBubble = micIsSending && !hasStreamingAssistant

  var hasMicPermission by remember { mutableStateOf(context.hasRecordAudioPermission()) }
  var pendingMicEnable by remember { mutableStateOf(false) }

  DisposableEffect(lifecycleOwner, context) {
    val observer =
      LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
          hasMicPermission = context.hasRecordAudioPermission()
        }
      }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      // Stop TTS when leaving the voice screen
      viewModel.setVoiceScreenActive(false)
    }
  }

  val requestMicPermission =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasMicPermission = granted
      if (granted && pendingMicEnable) {
        viewModel.setMicEnabled(true)
      }
      pendingMicEnable = false
    }

  LaunchedEffect(micConversation.size, showThinkingBubble) {
    val total = micConversation.size + if (showThinkingBubble) 1 else 0
    if (total > 0) {
      listState.animateScrollToItem(total - 1)
    }
  }

  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .background(mobileBackgroundGradient)
        .imePadding()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
        .padding(horizontal = 20.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxWidth().weight(1f),
      contentPadding = PaddingValues(vertical = 4.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (micConversation.isEmpty() && !showThinkingBubble) {
        item {
          Box(
            modifier = Modifier.fillParentMaxHeight().fillMaxWidth(),
            contentAlignment = Alignment.Center,
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = mobileTextTertiary,
              )
              Text(
                t("Tap the mic to start", "点击麦克风开始"),
                style = mobileHeadline,
                color = mobileTextSecondary,
              )
              Text(
                t("Each pause sends a turn automatically.", "每次停顿都会自动发送一轮。"),
                style = mobileCallout,
                color = mobileTextTertiary,
              )
            }
          }
        }
      }

      items(items = micConversation, key = { it.id }) { entry ->
        VoiceTurnBubble(entry = entry)
      }

      if (showThinkingBubble) {
        item {
          VoiceThinkingBubble()
        }
      }
    }

    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      if (!micLiveTranscript.isNullOrBlank()) {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          color = mobileAccentSoft,
          border = BorderStroke(1.dp, mobileAccent.copy(alpha = 0.2f)),
        ) {
          Text(
            micLiveTranscript!!.trim(),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = mobileCallout,
            color = mobileText,
          )
        }
      }

      // Mic button with input-reactive ring + speaker toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Speaker toggle
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
          IconButton(
            onClick = { viewModel.setSpeakerEnabled(!speakerEnabled) },
            modifier = Modifier.size(48.dp),
            colors =
              IconButtonDefaults.iconButtonColors(
                containerColor = if (speakerEnabled) mobileSurface else mobileDangerSoft,
              ),
          ) {
            Icon(
              imageVector = if (speakerEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
              contentDescription = if (speakerEnabled) t("Mute speaker", "静音扬声器") else t("Unmute speaker", "取消静音"),
              modifier = Modifier.size(22.dp),
              tint = if (speakerEnabled) mobileTextSecondary else mobileDanger,
            )
          }
          Text(
            if (speakerEnabled) t("Speaker", "扬声器") else t("Muted", "静音"),
            style = mobileCaption2,
            color = if (speakerEnabled) mobileTextTertiary else mobileDanger,
          )
        }

        // Ring size = 68dp base + up to 22dp driven by mic input level.
        // The outer Box is fixed at 90dp (max ring size) so the ring never shifts the button.
        Box(
          modifier = Modifier.padding(horizontal = 16.dp).size(90.dp),
          contentAlignment = Alignment.Center,
        ) {
          if (micEnabled) {
            val ringLevel = micInputLevel.coerceIn(0f, 1f)
            val ringSize = 68.dp + (22.dp * max(ringLevel, 0.05f))
            Box(
              modifier =
                Modifier
                  .size(ringSize)
                  .background(mobileAccent.copy(alpha = 0.12f + 0.14f * ringLevel), CircleShape),
            )
          }
          Button(
            onClick = {
              if (micCooldown) return@Button
              if (micEnabled) {
                viewModel.setMicEnabled(false)
                return@Button
              }
              if (hasMicPermission) {
                viewModel.setMicEnabled(true)
              } else {
                pendingMicEnable = true
                requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
              }
            },
            enabled = !micCooldown,
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(60.dp),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = if (micCooldown) mobileTextSecondary else if (micEnabled) mobileDanger else mobileAccent,
                contentColor = Color.White,
                disabledContainerColor = mobileTextSecondary,
                disabledContentColor = Color.White.copy(alpha = 0.5f),
              ),
          ) {
            Icon(
              imageVector = if (micEnabled) Icons.Default.MicOff else Icons.Default.Mic,
              contentDescription = if (micEnabled) t("Turn microphone off", "关闭麦克风") else t("Turn microphone on", "开启麦克风"),
              modifier = Modifier.size(24.dp),
            )
          }
        }

        // Invisible spacer to balance the row (matches speaker column width)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Box(modifier = Modifier.size(48.dp))
          Spacer(modifier = Modifier.height(4.dp))
          Text("", style = mobileCaption2)
        }
      }

      // Status + labels
      val queueCount = micQueuedMessages.size
      val stateText =
        when {
          queueCount > 0 -> t("$queueCount queued", "$queueCount 条排队中")
          micIsSending -> t("Sending", "发送中")
          micCooldown -> t("Cooldown", "冷却中")
          micEnabled -> t("Listening", "监听中")
          else -> t("Mic off", "麦克风关闭")
        }
      val stateColor =
        when {
          micEnabled -> mobileSuccess
          micIsSending -> mobileAccent
          else -> mobileTextSecondary
        }
      val showDetailedMicStatus =
        remember(micStatusText, queueCount, micIsSending, micEnabled) {
          when (micStatusText.trim()) {
            "",
            "Mic off",
            "Listening",
            "Sending queued voice" -> queueCount > 0 || !micIsSending
            else -> true
          }
        }
      val detailedMicStatusColor = micStatusDetailColor(micStatusText)
      Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (micEnabled) mobileSuccessSoft else mobileSurface,
        border = BorderStroke(1.dp, if (micEnabled) mobileSuccess.copy(alpha = 0.3f) else mobileBorder),
      ) {
        Text(
          "$localizedGatewayStatus · $stateText",
          style = mobileCallout.copy(fontWeight = FontWeight.SemiBold),
          color = stateColor,
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
      }
      if (showDetailedMicStatus) {
        Text(
          localizedMicStatus,
          style = mobileCaption1,
          color = detailedMicStatusColor,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(),
        )
      }
      val showTalkStatus =
        remember(talkStatusText) {
          val trimmed = talkStatusText.trim()
          trimmed.isNotEmpty() &&
            !trimmed.equals("Off", ignoreCase = true) &&
            !trimmed.equals("Ready", ignoreCase = true) &&
            !trimmed.equals("Listening", ignoreCase = true)
        }
      if (showTalkStatus) {
        Text(
          t("Reply status", "回复状态") + " · " + localizedTalkStatus,
          style = mobileCaption1,
          color = talkStatusDetailColor(talkStatusText),
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      if (!hasMicPermission) {
        val showRationale =
          if (activity == null) {
            false
          } else {
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
          }
        Text(
          if (showRationale) {
            t("Microphone permission is required for voice mode.", "语音模式需要麦克风权限。")
          } else {
            t("Microphone blocked. Open app settings to enable it.", "麦克风权限被阻止了。请打开应用设置进行启用。")
          },
          style = mobileCaption1,
          color = mobileWarning,
          textAlign = TextAlign.Center,
        )
        Button(
          onClick = { openAppSettings(context) },
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = mobileSurfaceStrong, contentColor = mobileText),
        ) {
          Text(t("Open settings", "打开设置"), style = mobileCallout.copy(fontWeight = FontWeight.SemiBold))
        }
      }
    }
  }
}

@Composable
private fun VoiceTurnBubble(entry: VoiceConversationEntry) {
  val language = LocalAppLanguage.current
  val isUser = entry.role == VoiceConversationRole.User
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth(0.90f),
      shape = RoundedCornerShape(12.dp),
      color = if (isUser) mobileAccentSoft else mobileCardSurface,
      border = BorderStroke(1.dp, if (isUser) mobileAccent else mobileBorderStrong),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        Text(
          if (isUser) language.pick("You", "你") else "OpenClaw",
          style = mobileCaption2.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
          color = if (isUser) mobileAccent else mobileTextSecondary,
        )
        Text(
          if (entry.isStreaming && entry.text.isBlank()) {
            language.pick("Listening response…", "正在接收回应…")
          } else if (isUser) {
            entry.text
          } else {
            localizeVoiceConversationText(language, entry.text)
          },
          style = mobileCallout,
          color = mobileText,
        )
      }
    }
  }
}

@Composable
private fun VoiceThinkingBubble() {
  val language = LocalAppLanguage.current
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
    Surface(
      modifier = Modifier.fillMaxWidth(0.68f),
      shape = RoundedCornerShape(12.dp),
      color = mobileCardSurface,
      border = BorderStroke(1.dp, mobileBorderStrong),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        ThinkingDots(color = mobileTextSecondary)
        Text(language.pick("OpenClaw is thinking…", "OpenClaw 正在思考…"), style = mobileCallout, color = mobileTextSecondary)
      }
    }
  }
}

@Composable
private fun ThinkingDots(color: Color) {
  Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
    ThinkingDot(alpha = 0.38f, color = color)
    ThinkingDot(alpha = 0.62f, color = color)
    ThinkingDot(alpha = 0.90f, color = color)
  }
}

@Composable
private fun ThinkingDot(alpha: Float, color: Color) {
  Surface(
    modifier = Modifier.size(6.dp).alpha(alpha),
    shape = CircleShape,
    color = color,
  ) {}
}

private fun Context.hasRecordAudioPermission(): Boolean {
  return (
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
      PackageManager.PERMISSION_GRANTED
    )
}

private fun Context.findActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }

private fun openAppSettings(context: Context) {
  val intent =
    Intent(
      Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
      Uri.fromParts("package", context.packageName, null),
    )
  context.startActivity(intent)
}

@Composable
private fun micStatusDetailColor(statusText: String): Color {
  val trimmed = statusText.trim()
  return when {
    trimmed.isEmpty() -> mobileTextTertiary
    trimmed.startsWith("Start failed:", ignoreCase = true) -> mobileWarning
    trimmed.startsWith("Send failed:", ignoreCase = true) -> mobileWarning
    trimmed.contains("error", ignoreCase = true) -> mobileWarning
    trimmed.contains("permission required", ignoreCase = true) -> mobileWarning
    trimmed.contains("unavailable", ignoreCase = true) -> mobileWarning
    trimmed.contains("disconnected", ignoreCase = true) -> mobileWarning
    trimmed.contains("limited", ignoreCase = true) -> mobileWarning
    trimmed.contains("timed out", ignoreCase = true) -> mobileWarning
    trimmed.contains("waiting for gateway", ignoreCase = true) -> mobileAccent
    trimmed.contains("queued", ignoreCase = true) -> mobileAccent
    trimmed.contains("sending", ignoreCase = true) -> mobileAccent
    else -> mobileTextTertiary
  }
}

@Composable
private fun talkStatusDetailColor(statusText: String): Color {
  val trimmed = statusText.trim()
  return when {
    trimmed.isEmpty() -> mobileTextTertiary
    trimmed.startsWith("Talk failed:", ignoreCase = true) -> mobileWarning
    trimmed.startsWith("Speak failed:", ignoreCase = true) -> mobileWarning
    trimmed.startsWith("Start failed:", ignoreCase = true) -> mobileWarning
    trimmed.contains("no reply", ignoreCase = true) -> mobileWarning
    trimmed.contains("not connected", ignoreCase = true) -> mobileWarning
    trimmed.contains("permission required", ignoreCase = true) -> mobileWarning
    trimmed.contains("unavailable", ignoreCase = true) -> mobileWarning
    trimmed.contains("thinking", ignoreCase = true) -> mobileAccent
    trimmed.contains("listening", ignoreCase = true) -> mobileAccent
    trimmed.contains("speaking", ignoreCase = true) -> mobileSuccess
    else -> mobileTextTertiary
  }
}
