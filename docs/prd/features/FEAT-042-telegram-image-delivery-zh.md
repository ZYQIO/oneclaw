# Telegram Bridge: 图片投递修复

## 功能标识
- **功能 ID**: FEAT-042
- **创建日期**: 2026-03-01
- **最后更新**: 2026-03-01
- **状态: 草稿**
- **优先级: P0（必须有）**
- **负责人: 待定**
- **关联 RFC**: [RFC-042 (Telegram Image Delivery Fix)](../../rfc/features/RFC-042-telegram-image-delivery.md)
- **关联功能**: [FEAT-024 (Messaging Bridge)](FEAT-024-messaging-bridge.md), [FEAT-041 (Bridge Improvements)](FEAT-041-bridge-improvements.md)

## 用户故事

**作为** Telegram bridge 的用户，
**我希望** 我发送给机器人的图片（无论是否附带文字说明）能够被 AI agent 接收并识别，
**以便** 我可以通过 Telegram 进行多模态对话，就像直接在 App 内操作一样。

### 典型场景

1. 用户通过 Telegram 发送一张照片，并附带说明文字"这张图片里有什么？"。Agent 同时接收到图片和说明文字，并返回对图片内容的描述。
2. 用户通过 Telegram 发送一张没有说明文字的照片。Agent 接收到图片并作出回应（例如："我看到了一张照片，你想了解什么？"）。
3. 用户通过 Telegram 发送纯文字消息（无图片）。行为保持不变——agent 正常接收并回复文字内容。

## 功能描述

### 功能概述

FEAT-042 修复两个共同导致通过 Telegram 发送的图片在到达 AI agent 之前被静默丢弃的缺陷。

**缺陷 1 -- 读取照片说明文字的字段错误**: 当 Telegram 消息包含照片时，随照片附带的文字存储在消息对象的 `caption` 字段中，而非 `text` 字段。当前的 `TelegramChannel` 读取的是 `message["text"]`，对于含图片的消息该字段始终为 null，导致说明文字丢失。

**缺陷 2 -- imagePaths 未转发给 agent**: `BridgeAgentExecutorImpl.executeMessage()` 从 channel 层接收到 `imagePaths` 列表，但并未将其传递给 `SendMessageUseCase.execute()`，图片路径被静默丢弃，agent 始终无法获取已下载的图片文件。

### 根因分析

| 缺陷 | 位置 | 影响 |
|------|------|------|
| 对照片消息读取 `message["text"]` 而非 `message["caption"]` | `TelegramChannel.kt` | 发送照片时说明文字丢失 |
| `imagePaths` 未从 `BridgeAgentExecutorImpl` 传递到 `SendMessageUseCase` | `BridgeAgentExecutorImpl.kt` 和 `SendMessageUseCase.kt` | 图片已在本地下载，但 agent 始终无法收到 |

### 修复说明

#### 修复 1: 读取照片消息的 caption 字段

在 `TelegramChannel` 中，当消息包含照片时，若 `message["text"]` 为空则回退读取 `message["caption"]`：

```
text = message["text"] ?: message["caption"] ?: ""
```

此改动保持向后兼容性（纯文字消息仍从 `message["text"]` 读取）。

#### 修复 2: 在执行流水线中传递 imagePaths

扩展 `SendMessageUseCase.execute()`，使其接受可选参数 `imagePaths: List<String>`，并将路径作为文件附件传递给 agent。更新 `BridgeAgentExecutorImpl`，将 bridge 层的 `imagePaths` 转发给 use case。

### 验收标准

必须满足（全部必需）:
- [ ] 通过 Telegram 发送带说明文字的照片：agent 同时接收到图片文件和说明文字
- [ ] 通过 Telegram 发送不带说明文字的照片：agent 接收到图片文件；文字为空或显示默认占位内容
- [ ] 通过 Telegram 发送纯文字消息：行为不变，无回归
- [ ] 若图片下载失败，消息仍以纯文字方式处理（保留现有行为）
- [ ] 所有现有单元测试通过；新增单元测试覆盖 caption 提取和 imagePaths 转发逻辑

## 功能边界

### 包含的功能
- 修复 `TelegramChannel`，使其对照片消息读取 `message["caption"]`
- 扩展 `SendMessageUseCase.execute()`，添加 `imagePaths: List<String> = emptyList()` 参数
- 更新 `BridgeAgentExecutorImpl`，将 `imagePaths` 转发给 `SendMessageUseCase`

### 不包含的功能
- 多图相册支持（Telegram 相册 = 多条消息；不在本期范围内）
- 非 Telegram 渠道的图片支持（Discord 等——单独处理）
- agent 向 Telegram 回传图片响应

## 依赖关系

### 依赖于
- **FEAT-024 (Messaging Bridge)**: TelegramChannel, BridgeAgentExecutor, BridgeImageStorage
- **FEAT-001 (Chat Interaction)**: SendMessageUseCase

## 变更历史

| 日期 | 版本 | 变更内容 | 负责人 |
|------|------|----------|--------|
| 2026-03-01 | 0.1 | 初始版本 | - |
