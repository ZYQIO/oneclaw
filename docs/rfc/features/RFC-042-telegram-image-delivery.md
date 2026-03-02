# RFC-042: Telegram Bridge Image Delivery Fix

## Document Information
- **RFC ID**: RFC-042
- **Related PRD**: [FEAT-042 (Telegram Image Delivery Fix)](../../prd/features/FEAT-042-telegram-image-delivery.md)
- **Created**: 2026-03-01
- **Last Updated**: 2026-03-01
- **Status**: Draft
- **Author**: TBD

## Overview

### Background

When a user sends an image (with or without a text caption) via Telegram to the bridge bot, the agent receives neither the image nor the caption. Two independent bugs cause this:

1. **Wrong field for photo caption** (`TelegramChannel.kt`): Telegram places the text accompanying a photo in `message["caption"]`, not `message["text"]`. The current code only reads `message["text"]`, which is `null` for photo messages, so the caption is silently discarded.

2. **imagePaths dropped at executor boundary** (`BridgeAgentExecutorImpl.kt`): `BridgeAgentExecutorImpl.executeMessage()` receives `imagePaths` from the channel layer but passes nothing to `SendMessageUseCase.execute()`. The downloaded image files are never forwarded to the agent.

### Goals

1. Fix caption extraction in `TelegramChannel` to read `message["caption"]` for photo messages
2. Fix `BridgeAgentExecutorImpl` to convert `imagePaths` into `PendingAttachment` objects and pass them to `SendMessageUseCase`

### Non-Goals

- Multi-photo album support (Telegram sends each album photo as a separate update; out of scope)
- Image support for other channels (Discord, etc.)
- Outbound image responses from the agent to Telegram

## Technical Design

### Changed Files Overview

```
bridge/src/main/kotlin/com/oneclaw/shadow/bridge/channel/telegram/
└── TelegramChannel.kt                         # MODIFIED (caption extraction)

app/src/main/kotlin/com/oneclaw/shadow/
└── feature/bridge/
    └── BridgeAgentExecutorImpl.kt             # MODIFIED (imagePaths -> PendingAttachment)

bridge/src/test/kotlin/com/oneclaw/shadow/bridge/channel/telegram/
└── TelegramChannelTest.kt                     # MODIFIED (caption tests)

app/src/test/kotlin/com/oneclaw/shadow/feature/bridge/
└── BridgeAgentExecutorImplTest.kt             # MODIFIED (imagePaths forwarding tests)
```

## Detailed Design

### Fix 1: Caption Extraction in TelegramChannel

**Location**: `TelegramChannel.kt`, in the polling loop where the update message is parsed.

**Current code** (reads only `message["text"]`):
```kotlin
val text = message["text"]?.jsonPrimitive?.content ?: ""
```

**Fixed code** (falls back to `message["caption"]` for photo messages):
```kotlin
val text = message["text"]?.jsonPrimitive?.content
    ?.takeIf { it.isNotBlank() }
    ?: message["caption"]?.jsonPrimitive?.content
    ?: ""
```

**Rationale**: Telegram's Bot API places message text in `message.text` for text-only messages, and the image caption in `message.caption` for photo/document/video messages. These fields are mutually exclusive -- a photo message never has a `text` field. Reading `caption` as the fallback covers all photo message variants without affecting text-only messages.

**Behavior after fix**:

| Telegram message type | `message["text"]` | `message["caption"]` | Resulting `text` |
|-----------------------|-------------------|----------------------|-----------------|
| Text only | "Hello" | null | "Hello" |
| Photo with caption | null | "What is this?" | "What is this?" |
| Photo without caption | null | null | "" |
| (unchanged) text only | "Hi" | null | "Hi" |

---

### Fix 2: imagePaths Forwarded in BridgeAgentExecutorImpl

**Location**: `BridgeAgentExecutorImpl.kt`

**Current code** (imagePaths received but not forwarded):
```kotlin
override suspend fun executeMessage(
    conversationId: String,
    userMessage: String,
    imagePaths: List<String>
) {
    val agentId = resolveAgentId()
    sendMessageUseCase.execute(
        sessionId = conversationId,
        userText = userMessage,
        agentId = agentId
        // imagePaths is silently dropped here
    ).collect()
}
```

**Fixed code** (imagePaths converted to PendingAttachment and forwarded):
```kotlin
override suspend fun executeMessage(
    conversationId: String,
    userMessage: String,
    imagePaths: List<String>
) {
    val agentId = resolveAgentId()
    val pendingAttachments = imagePaths.mapNotNull { path ->
        val file = File(path)
        if (!file.exists()) return@mapNotNull null
        AttachmentFileManager.PendingAttachment(
            id = UUID.randomUUID().toString(),
            type = AttachmentType.IMAGE,
            fileName = file.name,
            mimeType = mimeTypeFromExtension(file.extension),
            fileSize = file.length(),
            filePath = path,
            thumbnailPath = null,
            width = null,
            height = null,
            durationMs = null
        )
    }
    sendMessageUseCase.execute(
        sessionId = conversationId,
        userText = userMessage,
        agentId = agentId,
        pendingAttachments = pendingAttachments
    ).collect()
}

private fun mimeTypeFromExtension(ext: String): String = when (ext.lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png"         -> "image/png"
    "gif"         -> "image/gif"
    "webp"        -> "image/webp"
    "bmp"         -> "image/bmp"
    else          -> "image/jpeg"
}
```

**Why PendingAttachment directly (no AttachmentFileManager.copyFromUri)?**

`BridgeImageStorage.downloadAndStore()` already copies the image to `context.filesDir/bridge_images/` and returns the absolute path. The file is already in app-private storage. `PendingAttachment` only requires a valid `filePath` -- the `AttachmentFileManager.copyFromUri()` path (for user-picked URIs) is not needed here. We construct `PendingAttachment` directly, skipping the URI copy step.

**How SendMessageUseCase processes it:**

`SendMessageUseCase.execute()` at lines 169--184 reads `pendingAttachments`, filters by `ProviderCapability.supportsAttachmentType(provider.type, it.type)`, then calls `attachmentFileManager.readAsBase64(pending.filePath)` to convert each file to base64 for the API call. `attachmentFileManager` is already injected in the Koin `featureModule` -- no DI changes required.

The image is also persisted to `AttachmentRepository` (lines 106--125), so it appears in the chat UI alongside the user message.

**Required imports added to BridgeAgentExecutorImpl**:
```kotlin
import com.oneclaw.shadow.core.model.AttachmentType
import com.oneclaw.shadow.data.local.AttachmentFileManager
import java.io.File
import java.util.UUID
```

---

## Testing

### Unit Tests

**TelegramChannelTest** -- add:
- `photoMessage_withCaption_extractsCaption`: mock update with `photo` array + `caption` field; assert `ChannelMessage.text == "caption text"`
- `photoMessage_withoutCaption_textIsEmpty`: mock update with `photo` array, no `caption`; assert `ChannelMessage.text == ""`
- `textMessage_noChange_readsTextField`: mock update with `text` field only; assert `ChannelMessage.text == "text content"`

**BridgeAgentExecutorImplTest** -- add:
- `executeMessage_withImagePaths_createsPendingAttachments`: mock `sendMessageUseCase.execute()`; call `executeMessage()` with one imagePath pointing to a temp file; verify `execute()` called with `pendingAttachments` list of size 1
- `executeMessage_withMissingImageFile_skipsIt`: call `executeMessage()` with a path to a non-existent file; verify `execute()` called with empty `pendingAttachments`
- `executeMessage_noImagePaths_forwardsEmpty`: call with empty `imagePaths`; verify `execute()` called with empty `pendingAttachments`

### Manual Verification

1. Send a Telegram photo with caption "describe this image". Verify caption and image both appear in the active session in the app, and the agent responds with image description.
2. Send a Telegram photo with no caption. Verify image appears in the session; agent receives and responds to the image.
3. Send a plain text message via Telegram. Verify unchanged behavior (no regression).
4. If image download fails (e.g., malformed file_id), verify message is still processed as text-only.

## Migration Notes

- No database schema changes.
- `BridgeAgentExecutorImpl` adds three new imports (`AttachmentType`, `AttachmentFileManager`, `File`, `UUID`). No constructor changes required.
- `TelegramChannel` polling loop: one-line change to `text` extraction. No interface or signature changes.

## Open Questions

- [ ] Should the fallback `mimeType` default to `"image/jpeg"` (current proposal) or be left as `"application/octet-stream"` when extension is unknown?
- [ ] Should the bridge-downloaded image files in `bridge_images/` be cleaned up after they are forwarded to the agent, or retained for potential reuse?
