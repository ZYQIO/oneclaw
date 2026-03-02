# RFC-042: Telegram Bridge 图片投递修复

## 文档信息
- **RFC ID**: RFC-042
- **关联 PRD**: [FEAT-042 (Telegram 图片投递修复)](../../prd/features/FEAT-042-telegram-image-delivery.md)
- **创建时间**: 2026-03-01
- **最后更新**: 2026-03-01
- **状态**: 草稿
- **作者**: 待定

## 概述

### 背景

当用户通过 Telegram 向 bridge bot 发送图片（无论是否附带文字说明）时，agent 既收不到图片，也收不到说明文字。这是由两个相互独立的 bug 导致的：

1. **照片说明字段读取错误** (`TelegramChannel.kt`)：Telegram 将图片附带的文字放在 `message["caption"]` 字段，而非 `message["text"]`。当前代码只读取 `message["text"]`，而该字段在图片消息中为 `null`，因此说明文字被静默丢弃。

2. **imagePaths 在执行器边界处被丢弃** (`BridgeAgentExecutorImpl.kt`)：`BridgeAgentExecutorImpl.executeMessage()` 从 channel 层接收到 `imagePaths`，但未将其传递给 `SendMessageUseCase.execute()`。已下载的图片文件从未被转发给 agent。

### 目标

1. 修复 `TelegramChannel` 中的说明文字提取逻辑，对图片消息读取 `message["caption"]`
2. 修复 `BridgeAgentExecutorImpl`，将 `imagePaths` 转换为 `PendingAttachment` 对象并传递给 `SendMessageUseCase`

### 非目标

- 多图相册支持（Telegram 将相册中每张图片作为独立 update 发送，超出本次范围）
- 其他 channel 的图片支持（Discord 等）
- agent 向 Telegram 的出站图片回复

## 技术方案

### 改动文件概览

```
bridge/src/main/kotlin/com/oneclaw/shadow/bridge/channel/telegram/
└── TelegramChannel.kt                         # 修改 (说明文字提取)

app/src/main/kotlin/com/oneclaw/shadow/
└── feature/bridge/
    └── BridgeAgentExecutorImpl.kt             # 修改 (imagePaths -> PendingAttachment)

bridge/src/test/kotlin/com/oneclaw/shadow/bridge/channel/telegram/
└── TelegramChannelTest.kt                     # 修改 (说明文字测试)

app/src/test/kotlin/com/oneclaw/shadow/feature/bridge/
└── BridgeAgentExecutorImplTest.kt             # 修改 (imagePaths 转发测试)
```

## 详细设计

### 修复一：TelegramChannel 中的说明文字提取

**位置**：`TelegramChannel.kt`，轮询循环中解析 update 消息的部分。

**当前代码**（仅读取 `message["text"]`）：
```kotlin
val text = message["text"]?.jsonPrimitive?.content ?: ""
```

**修复后代码**（对图片消息回退读取 `message["caption"]`）：
```kotlin
val text = message["text"]?.jsonPrimitive?.content
    ?.takeIf { it.isNotBlank() }
    ?: message["caption"]?.jsonPrimitive?.content
    ?: ""
```

**设计说明**：Telegram Bot API 在纯文字消息中将文本置于 `message.text`，在图片/文档/视频消息中将说明文字置于 `message.caption`。这两个字段互斥——图片消息不会同时存在 `text` 字段。将 `caption` 作为回退值，可覆盖所有图片消息变体，同时不影响纯文字消息。

**修复后行为**：

| Telegram 消息类型 | `message["text"]` | `message["caption"]` | 最终 `text` |
|-----------------------|-------------------|----------------------|-----------------|
| 纯文字 | "Hello" | null | "Hello" |
| 带说明文字的图片 | null | "What is this?" | "What is this?" |
| 不带说明文字的图片 | null | null | "" |
| （不变）纯文字 | "Hi" | null | "Hi" |

---

### 修复二：BridgeAgentExecutorImpl 中转发 imagePaths

**位置**：`BridgeAgentExecutorImpl.kt`

**当前代码**（接收到 imagePaths 但未转发）：
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
        // imagePaths 在此被静默丢弃
    ).collect()
}
```

**修复后代码**（将 imagePaths 转换为 PendingAttachment 并转发）：
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

**为什么直接使用 PendingAttachment（而不经过 AttachmentFileManager.copyFromUri）？**

`BridgeImageStorage.downloadAndStore()` 已经将图片复制到 `context.filesDir/bridge_images/` 并返回绝对路径。文件已位于应用私有存储中。`PendingAttachment` 只需要一个有效的 `filePath`——面向用户选取 URI 场景的 `AttachmentFileManager.copyFromUri()` 路径在此不适用。我们直接构造 `PendingAttachment`，跳过 URI 复制步骤。

**SendMessageUseCase 的处理方式：**

`SendMessageUseCase.execute()` 在第 169--184 行读取 `pendingAttachments`，按 `ProviderCapability.supportsAttachmentType(provider.type, it.type)` 过滤，然后调用 `attachmentFileManager.readAsBase64(pending.filePath)` 将每个文件转换为 base64 用于 API 调用。`attachmentFileManager` 已在 Koin 的 `featureModule` 中注入，无需更改 DI 配置。

图片也会被持久化到 `AttachmentRepository`（第 106--125 行），因此会在聊天 UI 中与用户消息一同显示。

**需要在 BridgeAgentExecutorImpl 中新增的 import**：
```kotlin
import com.oneclaw.shadow.core.model.AttachmentType
import com.oneclaw.shadow.data.local.AttachmentFileManager
import java.io.File
import java.util.UUID
```

---

## 测试

### 单元测试

**TelegramChannelTest** — 新增：
- `photoMessage_withCaption_extractsCaption`：mock 包含 `photo` 数组和 `caption` 字段的 update；断言 `ChannelMessage.text == "caption text"`
- `photoMessage_withoutCaption_textIsEmpty`：mock 包含 `photo` 数组但无 `caption` 的 update；断言 `ChannelMessage.text == ""`
- `textMessage_noChange_readsTextField`：mock 仅包含 `text` 字段的 update；断言 `ChannelMessage.text == "text content"`

**BridgeAgentExecutorImplTest** — 新增：
- `executeMessage_withImagePaths_createsPendingAttachments`：mock `sendMessageUseCase.execute()`；以指向临时文件的 imagePath 调用 `executeMessage()`；验证 `execute()` 被调用时 `pendingAttachments` 列表长度为 1
- `executeMessage_withMissingImageFile_skipsIt`：以指向不存在文件的路径调用 `executeMessage()`；验证 `execute()` 被调用时 `pendingAttachments` 为空
- `executeMessage_noImagePaths_forwardsEmpty`：以空 `imagePaths` 调用；验证 `execute()` 被调用时 `pendingAttachments` 为空

### 手动验证

1. 发送一张带说明文字"describe this image"的 Telegram 图片。验证说明文字和图片均出现在应用当前会话中，且 agent 返回图片描述。
2. 发送一张不带说明文字的 Telegram 图片。验证图片出现在会话中；agent 接收并响应该图片。
3. 通过 Telegram 发送纯文字消息。验证行为不变（无回归）。
4. 若图片下载失败（如 file_id 格式错误），验证消息仍以纯文字方式处理。

## 迁移说明

- 无数据库 schema 变更。
- `BridgeAgentExecutorImpl` 新增三个 import（`AttachmentType`、`AttachmentFileManager`、`File`、`UUID`），无需更改构造函数。
- `TelegramChannel` 轮询循环：仅修改 `text` 提取的一行代码，无接口或签名变更。

## 开放问题

- [ ] 当扩展名未知时，回退 `mimeType` 应默认为 `"image/jpeg"`（当前方案）还是 `"application/octet-stream"`？
- [ ] bridge 下载到 `bridge_images/` 的图片文件，在转发给 agent 后应立即清理，还是保留以备复用？
