# RFC-010: 并发用户输入（基于队列的消息注入）

## 文档信息
- **RFC编号**: RFC-010
- **关联PRD**: [FEAT-010 (并发用户输入)](../../prd/features/FEAT-010-concurrent-input.md)
- **关联架构**: [RFC-000 (整体架构)](../architecture/RFC-000-overall-architecture.md)
- **依赖**: [RFC-001 (聊天交互)](RFC-001-chat-interaction.md)
- **被依赖**: 无
- **创建日期**: 2026-02-28
- **最后更新**: 2026-02-28
- **状态**: 草稿
- **作者**: TBD

## 概述

### 背景

目前在 agent loop 运行期间，发送按钮被禁用（`canSend = false`），输入框也被禁用（`enabled = !isStreaming`）。用户必须等待整个循环完成才能发送新消息。在长时间的工具执行或缓慢的流式响应期间，用户可能希望补充上下文、追问、或重定向 agent，这种等待体验很差。

`SendMessageUseCase` 中的 agent loop 天然具有迭代结构：每轮开始时调用 `getMessagesSnapshot()` 从数据库读取完整消息历史。这意味着只要用户消息在下一轮迭代开始前保存到数据库，就会自动被下一次 API 调用包含。

### 目标

1. 允许用户在任何时候发送消息，包括 agent loop 运行期间。
2. 排队的消息在下一个迭代边界（当前 AI 回复结束后或工具执行完成后）被注入，AI 按正确的轮次顺序看到它们。
3. 排队的消息在提交后立即显示在聊天 UI 中，无需等待 AI 处理。
4. 发送按钮和停止按钮是独立的 UI 元素，在流式传输期间同时存在。
5. 用户在有排队消息时按停止键：保存 AI 的部分回复，排队消息保留在数据库中，并注入系统提示将其标记为已废弃。

### 非目标

- 在流式响应的 token 中途打断以注入消息。
- 编辑或取消已提交的排队消息。
- 多个同时运行的 agent loop（每个 session 仍保持一个循环的不变量）。

## 技术方案

### 架构概览

```
用户在 agent loop 运行时输入文字并点击发送
         |
         v
ChatViewModel.sendMessage()
  |-- 如果没有在 streaming: 启动新的 agent loop（现有行为）
  |-- 如果正在 streaming:
        |-- 立即保存消息到数据库（在 UI 中显示）
        |-- 将文本发送到 pendingMessages: Channel<String>
         |
         v
SendMessageUseCase（正在运行的 agent loop）
  在每个迭代边界（ResponseComplete 或 ToolRoundStarting）之后：
        |-- 排空 pendingMessages channel
        |-- 如果有消息：发出 UserMessageInjected 事件，继续循环
        |-- 如果没有消息且 AI 回复已完成：发出 ResponseComplete，跳出循环
```

### 注入点

`SendMessageUseCase` 目前在 `pendingToolCalls.isEmpty()` 时跳出循环（AI 返回了没有工具调用的文本回复）。本 RFC 中，在跳出前先检查待处理的用户消息：

```
迭代 N：
  流式接收 AI 回复 -> 没有工具调用 -> 正常情况下会发出 ResponseComplete 并跳出
  -> 检查 pendingMessages channel
  -> 如果有消息：发出 UserMessageInjected，循环到迭代 N+1
  -> 迭代 N+1 的 getMessagesSnapshot 自然读取到所有已保存的消息
  -> 如果没有消息：发出 ResponseComplete 并跳出（行为不变）

迭代 N（有工具调用）：
  流式接收 AI 回复 -> 有工具调用 -> 保存工具结果
  -> 在 ToolRoundStarting 之前检查 pendingMessages channel
  -> 如果有消息：发出 UserMessageInjected
  -> 发出 ToolRoundStarting
  -> 迭代 N+1 的 getMessagesSnapshot 自然读取到所有消息
```

在 `getMessagesSnapshot` 之前注入就足够了，因为快照始终在每次迭代开头从数据库重新读取。

### 停止 + 排队消息的行为

用户在 channel 中有排队消息时按停止键：

```
时间线：
1. 用户发送 A    -> loop 开始 -> AI 正在流式回复 A...
2. 用户发送 B    -> 排队，已保存到数据库，在 UI 中显示
3. 用户按停止键
4. -> AI 部分回复保存到数据库（现有 CancellationException 处理器）
5. -> Channel 被排空，记录排队消息文本
6. -> 在 B 之后保存一条 SYSTEM 消息到数据库：
      "[System] 用户中断了上一条回复。
       前面排队的消息是在中断前提交的，可以忽略。
       请回应用户的下一条消息。"
7. -> Loop 终止

数据库状态：
  ... -> User: A -> AI: (部分回复) -> User: B -> System: (忽略 B 的提示) -> （结束）
```

这确保了当用户之后发送消息 C 时，AI 看到完整的历史记录包括系统提示。AI 理解：A 被部分回答了，B 是排队但已废弃的，C 是实际的新意图。

系统消息使用 `MessageType.SYSTEM`，在聊天 UI 中显示为小灰色标签（现有的 `SystemMessageCard`）。

### 新的 ChatEvent

为 `ChatEvent` 添加一个新事件：

```kotlin
// 当排队的用户消息在迭代边界被注入时发出。
// ViewModel 用此更新 UI 状态（例如清除待处理指示器）。
data class UserMessageInjected(val text: String) : ChatEvent()
```

不需要其他 ChatEvent 更改。

### SendMessageUseCase 变更

签名变更：添加可选的 `pendingMessages` 参数。

```kotlin
fun execute(
    sessionId: String,
    userText: String,
    agentId: String,
    pendingMessages: Channel<String> = Channel(Channel.UNLIMITED)
): Flow<ChatEvent>
```

在循环内替换当前的跳出块：

```kotlin
// 之前（当前代码）：
if (pendingToolCalls.isEmpty()) {
    sessionRepository.updateMessageStats(...)
    send(ChatEvent.ResponseComplete(aiMessage, usage))
    break
}

// 之后：
if (pendingToolCalls.isEmpty()) {
    // 在决定停止前排空待处理的用户消息
    val injected = drainPendingMessages(pendingMessages)
    for (text in injected) {
        send(ChatEvent.UserMessageInjected(text))
    }
    if (injected.isEmpty()) {
        // 没有待处理消息 -- 循环真正结束
        sessionRepository.updateMessageStats(
            id = sessionId,
            count = messageRepository.getMessageCount(sessionId),
            preview = accumulatedText.take(100)
        )
        send(ChatEvent.ResponseComplete(aiMessage, usage))
        break
    }
    // 发现待处理消息 -- 继续下一轮迭代
    round++
    send(ChatEvent.ToolRoundStarting(round))
    continue
}

// 同样在工具调用路径的 ToolRoundStarting 之前排空：
val injected = drainPendingMessages(pendingMessages)
for (text in injected) {
    send(ChatEvent.UserMessageInjected(text))
}
round++
if (round < MAX_TOOL_ROUNDS) {
    send(ChatEvent.ToolRoundStarting(round))
}
```

私有辅助函数：

```kotlin
private fun drainPendingMessages(channel: Channel<String>): List<String> {
    val injected = mutableListOf<String>()
    while (true) {
        val text = channel.tryReceive().getOrNull() ?: break
        injected.add(text)
    }
    return injected
}
```

注意：ViewModel 在用户发送时就将消息保存到数据库。Use case 只需要排空 channel 来知道注入已发生。下一次迭代开头的 `getMessagesSnapshot()` 自然从数据库读取消息。

### ChatUiState 变更

```kotlin
data class ChatUiState(
    // ... 现有字段 ...

    // 删除：
    // val canSend: Boolean = true

    // 添加：
    val pendingCount: Int = 0  // streaming 期间排队的消息数量
)
```

### ChatViewModel 变更

**新字段**：

```kotlin
private val pendingMessages = Channel<String>(Channel.UNLIMITED)
```

**`sendMessage()` 重写**：

```kotlin
fun sendMessage() {
    val text = _uiState.value.inputText.trim()
    if (text.isBlank()) return
    _uiState.update { it.copy(inputText = "") }

    if (_uiState.value.isStreaming) {
        // 排队路径：立即保存到数据库，通知运行中的循环
        viewModelScope.launch {
            val sessionId = _uiState.value.sessionId ?: return@launch
            messageRepository.addMessage(Message(
                id = "", sessionId = sessionId, type = MessageType.USER,
                content = text, thinkingContent = null,
                toolCallId = null, toolName = null, toolInput = null, toolOutput = null,
                toolStatus = null, toolDurationMs = null, tokenCountInput = null,
                tokenCountOutput = null, modelId = null, providerId = null, createdAt = 0
            ))
            val tempId = java.util.UUID.randomUUID().toString()
            _uiState.update { state ->
                state.copy(
                    messages = state.messages + ChatMessageItem(
                        id = tempId, type = MessageType.USER,
                        content = text, timestamp = System.currentTimeMillis()
                    ),
                    pendingCount = state.pendingCount + 1
                )
            }
            pendingMessages.trySend(text)
        }
        return
    }

    // 非 streaming 路径：启动新循环（现有逻辑）
    // 删除此路径中所有的 "canSend = false" 写入。
    // ... 现有 sendMessage 主体的其余部分不变，仅移除 canSend ...
}
```

**将 `pendingMessages` 传递给 use case**：

```kotlin
streamingJob = viewModelScope.launch {
    sendMessageUseCase.execute(
        sessionId = finalSessionId,
        userText = text,
        agentId = _uiState.value.currentAgentId,
        pendingMessages = pendingMessages
    ).collect { event ->
        handleChatEvent(event, finalSessionId, accumulatedText, accumulatedThinking) { ... }
    }
}
```

**在 `handleChatEvent` 中处理 `UserMessageInjected`**：

```kotlin
is ChatEvent.UserMessageInjected -> {
    _uiState.update { it.copy(pendingCount = maxOf(0, it.pendingCount - 1)) }
}
```

**`stopGeneration()` -- 保存废弃提示**：

现有的 `stopGeneration()` 调用 `streamingJob?.cancel()`，触发 `sendMessage()` 中的 `CancellationException` 处理器，该处理器调用 `savePartialResponse()` 然后 `finishStreaming()`。

更新 `finishStreaming()`：

```kotlin
private suspend fun finishStreaming(sessionId: String) {
    // 处理不会被处理的排队消息
    val abandonedTexts = mutableListOf<String>()
    while (true) {
        val text = pendingMessages.tryReceive().getOrNull() ?: break
        abandonedTexts.add(text)
    }
    if (abandonedTexts.isNotEmpty() && sessionId != null) {
        // 插入系统消息标记排队消息为已废弃
        messageRepository.addMessage(Message(
            id = "", sessionId = sessionId, type = MessageType.SYSTEM,
            content = "The user interrupted the previous response. " +
                "The preceding queued message(s) were submitted before the interruption " +
                "and can be ignored. Please respond to the user's next message.",
            thinkingContent = null,
            toolCallId = null, toolName = null, toolInput = null, toolOutput = null,
            toolStatus = null, toolDurationMs = null, tokenCountInput = null,
            tokenCountOutput = null, modelId = null, providerId = null, createdAt = 0
        ))
    }

    _uiState.update {
        it.copy(
            isStreaming = false,
            streamingText = "",
            streamingThinkingText = "",
            activeToolCalls = emptyList(),
            pendingCount = 0
        )
    }
    // ... finishStreaming 的其余部分不变（从数据库重新加载消息、标题生成）...
}
```

**删除所有 `canSend` 写入**：涉及 `sendMessage()`、`streamWithExistingMessage()`、`finishStreaming()`、`handleError()`、`initialize()`。

### 快速连发（Session 创建前）

如果用户在 agent loop 启动前（`sessionId` 创建前）发送了两条消息，现有 `sendMessage()` 中的懒创建逻辑处理第一条消息。第二条消息在 `isStreaming = true` 时到达，进入排队路径。两条消息都作为 USER 消息存在数据库中，第一次 `getMessagesSnapshot()` 调用包含两者。

### ChatScreen / ChatInput 的 UI 变更

当前 `ChatInput` 组件使用 `if/else` 显示停止或发送按钮。改为同时独立显示两者：

```kotlin
@Composable
fun ChatInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isStreaming: Boolean,
    hasConfiguredProvider: Boolean
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().imePadding()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message") },
                shape = MaterialTheme.shapes.extraLarge,
                maxLines = 6,
                enabled = true  // 变更：始终启用
            )
            Spacer(modifier = Modifier.width(8.dp))

            // 停止按钮：仅在 streaming 时可见
            if (isStreaming) {
                IconButton(
                    onClick = onStop,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    // 旋转的停止指示器
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            // 发送按钮：始终可见，输入框有文字时启用
            IconButton(
                onClick = onSend,
                enabled = text.isNotBlank() && hasConfiguredProvider,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}
```

关键 UI 变更：
- `OutlinedTextField.enabled` 始终为 `true`（之前是 `!isStreaming`）。
- 停止按钮：streaming 期间显示，图标为 `CircularProgressIndicator`（旋转）。
- 发送按钮：始终显示，`text.isNotBlank() && hasConfiguredProvider` 时启用。
- `canSend` 参数从 `ChatInput` 移除，改为传递 `hasConfiguredProvider`。

更新 `ChatScreen` 中的调用：

```kotlin
ChatInput(
    text = uiState.inputText,
    onTextChange = { viewModel.updateInputText(it) },
    onSend = { viewModel.sendMessage() },
    onStop = { viewModel.stopGeneration() },
    isStreaming = uiState.isStreaming,
    hasConfiguredProvider = uiState.hasConfiguredProvider
)
```

## 实现步骤

### 步骤 1：为 `ChatEvent` 添加 `UserMessageInjected`
- 文件：`feature/chat/ChatEvent.kt`
- 添加 `data class UserMessageInjected(val text: String) : ChatEvent()`

### 步骤 2：更新 `SendMessageUseCase`
- 文件：`feature/chat/usecase/SendMessageUseCase.kt`
- 为 `execute()` 添加带默认值的 `pendingMessages: Channel<String>` 参数
- 添加 `drainPendingMessages()` 私有辅助函数
- 将无工具调用路径的 `break` 块替换为排空检查逻辑
- 在工具调用路径的 `ToolRoundStarting` 前添加排空调用

### 步骤 3：更新 `ChatUiState`
- 文件：`feature/chat/ChatUiState.kt`
- 删除 `canSend: Boolean`
- 添加 `pendingCount: Int = 0`

### 步骤 4：更新 `ChatViewModel`
- 文件：`feature/chat/ChatViewModel.kt`
- 添加 `private val pendingMessages = Channel<String>(Channel.UNLIMITED)`
- 重写 `sendMessage()` 包含排队路径（streaming）+ 非 streaming 路径
- 将 `pendingMessages` 传递给 `sendMessageUseCase.execute()`
- 在 `handleChatEvent()` 中处理 `UserMessageInjected`
- 更新 `finishStreaming()`：排空 channel，如需则保存废弃系统消息，重置 `pendingCount`
- 从 `sendMessage()`、`streamWithExistingMessage()`、`finishStreaming()`、`handleError()`、`initialize()` 中删除所有 `canSend` 写入

### 步骤 5：更新 `ChatScreen` / `ChatInput`
- 文件：`feature/chat/ChatScreen.kt`
- 重写 `ChatInput` 使停止和发送按钮独立显示
- 停止按钮：图标为 `CircularProgressIndicator`（旋转），仅在 streaming 时可见
- 发送按钮：始终可见，文本非空且已配置 provider 时启用
- `OutlinedTextField.enabled` 始终为 `true`
- 删除 `canSend` 参数，改为 `hasConfiguredProvider`

### 步骤 6：修复 `canSend` 移除导致的编译错误
- 搜索代码库中所有 `canSend` 引用并删除/更新

## 测试策略

### Layer 1A -- 单元测试

**`SendMessageUseCaseQueueTest`**（`app/src/test/kotlin/.../feature/chat/usecase/`）：
- 测试：发送到 channel 的消息在下一个迭代边界被排空（无工具调用路径）；循环继续，AI 回应注入的消息
- 测试：发送到 channel 的消息在下一个迭代边界被排空（工具调用路径）；注入的消息包含在下一次 `getMessagesSnapshot()` 中
- 测试：多条消息排队；全部按顺序排空，所有 `UserMessageInjected` 事件被发出
- 测试：没有待处理消息 -> `ResponseComplete` 如之前一样发出（回归测试）
- 测试：channel 初始为空 -> 现有行为无回归

**`ChatViewModelConcurrentInputTest`**：
- 测试：`isStreaming = true` 时 `sendMessage()` 将消息保存到数据库并发送到 channel；`pendingCount` 递增
- 测试：收到 `UserMessageInjected` 事件时 `pendingCount` 递减
- 测试：有排队消息时 `stopGeneration()` 保存系统废弃提示到数据库并重置 `pendingCount` 为 0
- 测试：没有排队消息时 `stopGeneration()` 不保存废弃提示（回归测试）

### Layer 1C -- 截图测试
- 添加 streaming 期间 `ChatInput` 的截图，展示停止（旋转）和发送按钮并排显示。

### Layer 2 -- adb 视觉验证
添加到 `docs/testing/strategy.md` 的流程：

**Flow 6-1：streaming 期间并发输入**
1. 开始聊天，发送消息 A
2. AI 正在 streaming 时，输入消息 B 并点击发送
3. 验证 B 立即作为用户气泡出现在聊天中
4. 等待 AI 完成对 A 的回复，然后验证 AI 回应 B
5. 验证停止和发送按钮在 streaming 期间都可见且独立

**Flow 6-2：带排队消息的停止**
1. 开始聊天，发送消息 A
2. AI 正在 streaming 时，输入消息 B 并点击发送
3. 点击停止
4. 验证 AI 部分回复已保存且可见
5. 验证消息 B 在聊天中可见
6. 发送消息 C
7. 验证 AI 回应 C（而非 B）

## 数据流

### 正常注入流程

```
用户点击发送（streaming 进行中）
  -> ChatViewModel.sendMessage()
  -> messageRepository.addMessage(userMsg)          [数据库写入]
  -> _uiState: messages += userMsg, pendingCount++  [UI 更新]
  -> pendingMessages.trySend(text)                  [channel 信号]

（agent loop，当前迭代结束）
  -> SendMessageUseCase: drainPendingMessages()
  -> channel.tryReceive() 返回文本
  -> send(ChatEvent.UserMessageInjected(text))
  -> 循环继续到下一次迭代

（下一次迭代）
  -> messageRepository.getMessagesSnapshot()  [从数据库读取注入的消息]
  -> API 调用包含新的用户消息
  -> AI 回应两条消息

ChatViewModel 收到 UserMessageInjected
  -> _uiState: pendingCount--
```

### 停止 + 排队消息流程

```
用户发送 A -> streaming 开始
用户发送 B -> 排队（保存到数据库 + channel）
用户按停止键
  -> streamingJob.cancel()
  -> CancellationException 处理器：
       savePartialResponse(sessionId, accumulatedText)  [AI 部分回复保存到数据库]
       finishStreaming(sessionId)
         -> 排空 channel：发现 B
         -> 保存 System 消息："...排队消息可以忽略..."
         -> pendingCount = 0

数据库状态：
  User: A -> AI: (部分回复) -> User: B -> System: (废弃提示)

用户之后发送 C：
  -> 新循环开始
  -> getMessagesSnapshot() 读取：A, 部分回复, B, 系统提示, C
  -> AI 理解 B 已废弃，回应 C
```

## 考虑过的替代方案

### 替代方案 A：打断当前流，用新消息重启
- 取消当前流式回复，保存部分文本，然后用部分回复和新消息一起启动新循环。
- 被拒绝：会丢失正在进行的 AI 回复的剩余部分；部分状态处理复杂；AI 在说话中途时 UX 更差。

### 替代方案 B：ViewModel 控制循环（无 Channel）
- ViewModel 调用 use case 的单次迭代版本，然后根据响应类型和待处理消息决定是否继续循环。
- 被拒绝：需要对 `SendMessageUseCase` 进行更大的重构；当前基于 `channelFlow` 的设计很简洁，本 RFC 只需要少量添加。

### 替代方案 C：停止时丢弃排队消息
- 用户按停止键时，从数据库和 UI 中删除排队消息。
- 被拒绝：具有破坏性；用户可能想看到自己输入的内容。将消息保留在数据库中并附上废弃提示更透明。

## 变更历史

| 日期 | 版本 | 变更内容 | 变更人 |
|------|------|----------|--------|
| 2026-02-28 | 0.1 | 初始草稿 | TBD |
| 2026-02-28 | 0.2 | 讨论后更新：停止时保存部分回复+废弃提示；停止和发送按钮独立；快速连发合并为多条 USER 消息 | TBD |
