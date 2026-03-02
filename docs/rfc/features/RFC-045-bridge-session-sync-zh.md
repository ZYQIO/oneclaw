# RFC-045：Bridge-App 会话同步

## 文档信息
- **RFC ID**: RFC-045
- **Related PRD**: [FEAT-045（Bridge 会话同步）](../../prd/features/FEAT-045-bridge-session-sync.md)
- **Created**: 2026-03-01
- **Last Updated**: 2026-03-01
- **Status**: 已完成
- **Author**: TBD

## 概述

### 背景

在 FEAT-041 之后，bridge 将所有传入消息路由到应用中最近更新的会话。因此，在正常使用情况下，bridge 与应用共享同一个活跃会话。然而，当用户通过 Telegram 发送 `/clear` 时，`MessagingChannel.processInboundMessage()` 会调用 `conversationMapper.createNewConversation()` 并静默返回。新创建的会话成为数据库中最新的会话，但应用的 ChatScreen 并不会收到通知，仍然继续显示旧会话。

本 RFC 描述了一个最小化的修复方案：在 `BridgeStateTracker` 中添加基于 `SharedFlow` 的进程内事件总线，在 bridge 创建新会话时发出新会话 ID；同时在 ChatScreen 中添加一个 `LaunchedEffect` 进行订阅并重新初始化 ViewModel。

### 目标

1. 当 `/clear` 创建新会话时，从 bridge 层发出会话切换事件。
2. 在 `ChatScreen` 中订阅该事件，并在收到事件时调用 `viewModel.initialize(sessionId)`。
3. 保持变更最小化：不新增模块、不新增数据类、不更改数据库 schema。
4. 在创建新会话前，若上一个会话不含任何消息，则对其进行软删除，防止空会话在会话列表中不断积累。
5. 在 `BridgeStateTracker` 中追踪当前显示的会话，以便当用户在应用内手动切换会话（而未发送消息）时，bridge 能正确路由消息。
6. 让应用内的"新建对话"操作急切创建会话（与 bridge 的 `/clear` 行为保持一致），并清理上一个空会话。
7. 为 bridge 发起的会话生成会话标题（第一阶段：截断标题；第二阶段：AI 标题），与应用发起的会话行为保持一致。

### 非目标

- 任何新的持久化层或进程间通信。
- 应用不在前台时的通知处理（现有的 Room Flow 已负责列表刷新；会话切换将在下次进入前台时生效）。
- 对导航图或返回栈的更改。
- 对 `SessionListViewModel` 的更改（抽屉列表已通过 Room Flow 自动更新）。

## 技术设计

### 变更文件概览

```
bridge/src/main/kotlin/com/oneclaw/shadow/bridge/
└── BridgeStateTracker.kt                            # 修改（添加 SharedFlow + activeAppSessionId）
    channel/
    └── MessagingChannel.kt                          # 修改（/clear 分支发出事件）
app/src/main/kotlin/com/oneclaw/shadow/
├── di/
│   └── BridgeModule.kt                              # 修改（向 BridgeAgentExecutorImpl 注入新依赖）
├── feature/bridge/
│   ├── BridgeAgentExecutorImpl.kt                   # 修改（标题生成）
│   └── BridgeConversationManagerImpl.kt             # 修改（清理空会话 + 路由修正）
└── feature/chat/
    ├── ChatScreen.kt                                # 修改（添加 LaunchedEffect 订阅者）
    └── ChatViewModel.kt                             # 修改（newConversation() + setActiveAppSession）
```

## 详细设计

### 变更 1：BridgeStateTracker -- 添加 `newSessionFromBridge` SharedFlow

`BridgeStateTracker` 是一个 `object`（单例），已持有在 bridge 服务与应用 UI 之间共享的可观察状态。在此处添加 `SharedFlow` 与其现有职责保持一致。

```kotlin
// BridgeStateTracker.kt -- 仅展示新增内容

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object BridgeStateTracker {

    // ... 现有字段和方法保持不变 ...

    private val _newSessionFromBridge = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val newSessionFromBridge: SharedFlow<String> = _newSessionFromBridge.asSharedFlow()

    fun emitNewSessionFromBridge(sessionId: String) {
        _newSessionFromBridge.tryEmit(sessionId)
    }
}
```

设计决策：
- `extraBufferCapacity = 1`：缓冲一个事件，避免在发出时没有收集者处于活跃状态（例如 ChatScreen 正在组合过程中）导致事件丢失。
- `tryEmit`：即发即弃；bridge 无需确认 UI 已收到事件。
- 使用 `SharedFlow`（而非 `StateFlow`）：会话切换是一次性事件，而非持久状态值。`StateFlow` 会将最后一个会话 ID 重新推送给每个新收集者，导致每次重组时触发不必要的导航。

### 变更 2：MessagingChannel -- 在 `/clear` 时发出事件

在 `processInboundMessage()` 中，`/clear` 分支已调用 `conversationMapper.createNewConversation()`。在该调用之后添加一行：

```kotlin
// MessagingChannel.kt -- /clear 分支（第 4 步）

if (msg.text.trim() == "/clear") {
    val newConversationId = conversationMapper.createNewConversation()
    BridgeStateTracker.emitNewSessionFromBridge(newConversationId)   // <-- 新增行
    val clearMessage = BridgeMessage(
        content = "Conversation cleared. Starting a new conversation.",
        timestamp = System.currentTimeMillis()
    )
    runCatching { sendResponse(msg.externalChatId, clearMessage) }
    updateChannelState(newMessage = true)
    return
}
```

`MessagingChannel` 无其他变更。

### 变更 3：ChatScreen -- 订阅并重新初始化

在 `ChatScreen` 可组合函数顶部附近，与现有 `LaunchedEffect` 块并列，添加一个新的 `LaunchedEffect` 来收集 `BridgeStateTracker.newSessionFromBridge`：

```kotlin
// ChatScreen.kt -- ChatScreen 可组合函数内部

LaunchedEffect(Unit) {
    BridgeStateTracker.newSessionFromBridge.collect { sessionId ->
        viewModel.initialize(sessionId)
    }
}
```

设计决策：
- `LaunchedEffect(Unit)`：在每次组合生命周期内只启动一次，这是持久订阅的正确作用域。
- `viewModel.initialize(sessionId)`：这与用户从抽屉手动选择会话时调用的方法相同。复用该方法可确保行为一致：ViewModel 加载新会话的消息并更新 `uiState.sessionId`。
- 无需更改导航，因为 `ChatScreen` 已是当前屏幕。

### 变更 4：BridgeConversationManagerImpl -- 清理空的上一个会话

**问题**：每次 `/clear` 命令都会急切地在数据库中创建一条会话记录，即使该会话不包含任何消息。如果用户连续发送 `/clear` 而中间没有任何对话，空会话将在会话列表中不断积累。

**背景**：与应用内"新建对话"流程（惰性创建——直到发送第一条消息才在数据库中创建记录）不同，bridge 必须急切地创建会话，以便 `ChatScreen` 中的 `viewModel.initialize(sessionId)` 能立即通过 `sessionRepository.getSessionById()` 查找到该会话。

**解决方案**：在 `createNewConversation()` 中，在创建新会话前先检查当前最新会话是否为空。若为空，则先对其进行软删除。这样可确保任意时刻最多只存在一个空的 bridge 会话。

```kotlin
// BridgeConversationManagerImpl.kt -- 更新后的 createNewConversation()

override suspend fun createNewConversation(): String {
    // 若上一个会话没有消息，则对其软删除，避免积累
    val prevId = sessionRepository.getMostRecentSessionId()
    if (prevId != null) {
        val prevSession = sessionRepository.getSessionById(prevId)
        if (prevSession != null && prevSession.messageCount == 0) {
            sessionRepository.softDeleteSession(prevId)
        }
    }

    val agentId = resolveAgentId()
    val now = System.currentTimeMillis()
    val session = Session(
        id = UUID.randomUUID().toString(),
        title = "Bridge Conversation",
        currentAgentId = agentId,
        messageCount = 0,
        lastMessagePreview = null,
        isActive = false,
        deletedAt = null,
        createdAt = now,
        updatedAt = now
    )
    val created = sessionRepository.createSession(session)
    return created.id
}
```

设计决策：
- 使用 `session.messageCount`（`Session` 模型上的反规范化字段），而非新增 `MessageRepository` 查询。该字段在 bridge 创建会话时设置为 `0`，仅在添加消息时递增；由 `/clear` 创建的、从未收到过消息的 bridge 会话，其 `messageCount` 始终为 `0`。
- 使用 `sessionRepository.softDeleteSession()`（通过 `deleted_at` 时间戳进行软删除），与应用其他部分的会话删除模式保持一致。软删除后的会话将从会话列表和 `getMostRecentSessionId()` 查询中消失（该查询已过滤 `WHERE deleted_at IS NULL`）。
- 无需更改 `SessionRepository` 或 `MessageRepository` 接口——`getSessionById`、`getMostRecentSessionId` 和 `softDeleteSession` 均已存在。

### 变更 5：修正应用侧会话切换时的 bridge 路由

**问题**：`BridgeConversationManagerImpl.getActiveConversationId()` 原本仅返回 `sessionRepository.getMostRecentSessionId()`（按 `updated_at DESC` 排序）。当用户在应用内切换到旧会话但未发送消息时，`updated_at` 不会更新，导致 bridge 继续将消息路由到之前那个较新的会话。

**解决方案**：在 `BridgeStateTracker` 中添加一个内存变量 `activeAppSessionId`。`ChatViewModel.initialize()` 在每次会话切换时更新该值。`getActiveConversationId()` 优先使用内存中的值，当应用未设置该值时（例如冷启动、用户尚未打开 ChatScreen）则回退到数据库查询。

```kotlin
// BridgeStateTracker.kt -- 新增内容

private val _activeAppSessionId = MutableStateFlow<String?>(null)
val activeAppSessionId: StateFlow<String?> = _activeAppSessionId.asStateFlow()

fun setActiveAppSession(sessionId: String?) {
    _activeAppSessionId.value = sessionId
}
```

```kotlin
// ChatViewModel.kt -- 在 initialize() 中

BridgeStateTracker.setActiveAppSession(sessionId)
```

```kotlin
// BridgeConversationManagerImpl.kt -- 更新后的 getActiveConversationId()

override suspend fun getActiveConversationId(): String? {
    return BridgeStateTracker.activeAppSessionId.value
        ?: sessionRepository.getMostRecentSessionId()
}
```

### 变更 6：应用内"新建对话"改为急切创建

**问题**：点击应用抽屉中的"新建对话"会调用 `initialize(null)`，将 `sessionId` 设置为 `null`，同时将 `BridgeStateTracker.activeAppSessionId` 置为 `null`。随后 bridge 回退到 `getMostRecentSessionId()`，将消息路由到上一个有消息的会话，而非用户刚刚创建的新空白会话。

**解决方案**：在 `ChatViewModel` 中新增 `newConversation()` 方法，急切地在数据库中创建会话（与 bridge 的 `/clear` 行为相同），然后调用 `initialize(newSession.id)`。新会话立即注册到 `BridgeStateTracker`，bridge 可以即刻路由到该会话。同样应用空会话清理逻辑（若 `messages.isEmpty()` 则软删除），以保持一致性。

```kotlin
// ChatViewModel.kt

fun newConversation() {
    viewModelScope.launch {
        val currentSessionId = _uiState.value.sessionId
        if (currentSessionId != null && _uiState.value.messages.isEmpty()) {
            sessionRepository.softDeleteSession(currentSessionId)
        }
        val session = createSessionUseCase(agentId = _uiState.value.currentAgentId)
        isFirstMessage = true
        firstUserMessageText = null
        initialize(session.id)
    }
}
```

`ChatScreen` 现在从抽屉的"新建对话"按钮调用 `viewModel.newConversation()`，而非 `viewModel.initialize(null)`。

`sendMessage()` 中的惰性创建回退逻辑（`if (sessionId == null)`）作为冷启动场景（`init { initialize(null) }`）的安全路径予以保留。第一阶段标题生成移至 `sessionId == null` 块之外，使其对惰性路径和急切路径都生效：

```kotlin
// sendMessage() -- 简化结构

var sessionId = _uiState.value.sessionId
if (sessionId == null) {
    // 回退：冷启动状态下的惰性创建
    val session = createSessionUseCase(agentId = _uiState.value.currentAgentId)
    sessionId = session.id
    _uiState.update { it.copy(sessionId = sessionId) }
    isFirstMessage = true
}
// 第一阶段标题同时适用于惰性路径和急切路径
if (isFirstMessage && firstUserMessageText == null) {
    val titleSource = text.ifBlank { ... }
    val truncatedTitle = generateTitleUseCase.generateTruncatedTitle(titleSource)
    sessionRepository.updateTitle(sessionId!!, truncatedTitle)
    firstUserMessageText = text.ifBlank { titleSource }
}
```

### 变更 7：为 bridge 发起的会话生成标题

**问题**：由 bridge 创建的会话（通过 `/clear` 或 `createNewConversation()`）始终保留标题"Bridge Conversation"。应用发起的会话会生成有意义的标题（第一阶段：截断用户文本；第二阶段：AI 生成标题），而 bridge 会话缺少这一功能。

**解决方案**：`BridgeAgentExecutorImpl.executeMessage()` 在执行前检查 `session.messageCount == 0` 来判断是否为第一条消息。若是第一条消息：
- 第一阶段：立即调用 `generateTitleUseCase.generateTruncatedTitle(userMessage)` 和 `sessionRepository.updateTitle()`。
- 第二阶段：在 AI 响应结束后，使用从 `ChatEvent.ResponseComplete` 中捕获的响应内容及模型/提供商 ID，调用 `generateTitleUseCase.generateAiTitle()`。

```kotlin
// BridgeAgentExecutorImpl.kt

override suspend fun executeMessage(...): BridgeMessage? {
    val isFirstMessage = (sessionRepository.getSessionById(conversationId)?.messageCount ?: 0) == 0

    if (isFirstMessage) {
        val truncatedTitle = generateTitleUseCase.generateTruncatedTitle(userMessage)
        sessionRepository.updateTitle(conversationId, truncatedTitle)
    }

    // ... 执行 sendMessageUseCase，从 ResponseComplete 中捕获 lastModelId 和 lastProviderId ...

    if (isFirstMessage && content != null && lastModelId != null && lastProviderId != null) {
        generateTitleUseCase.generateAiTitle(
            sessionId = conversationId,
            firstUserMessage = userMessage,
            firstAiResponse = content,
            currentModelId = lastModelId!!,
            currentProviderId = lastProviderId!!
        )
    }
    ...
}
```

`BridgeAgentExecutorImpl` 新增两个构造函数依赖：`SessionRepository` 和 `GenerateTitleUseCase`，通过 `BridgeModule` 注入。

## 测试

### 单元测试

各修改文件及其测试覆盖情况：

- `BridgeStateTracker`：无现有单元测试（它是一个简单的状态持有者）；新增的 `SharedFlow` 和 `StateFlow` 字段遵循与现有字段相同的模式。
- `MessagingChannel`（`MessagingChannelTest`）：现有测试覆盖了 `/clear` 分支。更新测试以验证在收到 `/clear` 消息后，`BridgeStateTracker.newSessionFromBridge` 会发出新会话 ID。
- `BridgeConversationManagerImpl`：针对 `createNewConversation()` 新增一个测试用例，场景为上一个会话的 `messageCount == 0`——验证在创建新会话前 `softDeleteSession()` 被调用。再新增第二个用例，场景为 `messageCount > 0`——验证 `softDeleteSession()` 不被调用。
- `ChatScreen`：由 UI/Roborazzi 测试覆盖；视觉布局无变更，因此无需新的截图基线。
- `ChatViewModel`：`newConversation()` 目前无现有单元测试；该函数遵循与 `initialize()` 相同的模式，通过下方手动验证步骤 7 进行验证。
- `BridgeAgentExecutorImpl`（`BridgeAgentExecutorImplTest`）：现有测试已模拟 `SessionRepository` 和 `GenerateTitleUseCase`。测试套件在实例化 executor 时已包含两个新依赖（作为本 RFC 的一部分添加）。现有测试将 `session.messageCount` 设置为 `1` 以覆盖非首条消息的路径；新增一个 `messageCount = 0` 的用例，验证第一阶段标题生成被调用。

### 手动验证

1. 打开应用至 `ChatScreen`，确认其显示当前活跃会话。
2. 通过 Telegram 发送 `/clear`。
3. 在 3 秒内，验证 `ChatScreen` 切换到一个新的空会话。
4. 通过 Telegram 发送一条普通消息，验证其出现在应用的同一新会话中。
5. 在应用抽屉中手动切换到旧会话，通过 Telegram 发送一条消息，验证其出现在旧会话中（而非较新的会话）。
6. 通过 Telegram 连续发送两次 `/clear`，中间不发送任何消息。验证会话列表中只出现一个空会话（第一个空会话在处理第二次 `/clear` 时被软删除）。
7. 在应用抽屉中点击"新建对话"，验证立即创建并显示了一个新的空会话。通过 Telegram 发送一条消息，验证该消息出现在这个新会话中。
8. 在新会话（`/clear` 之后）中发送一条 bridge 消息。验证会话标题从"Bridge Conversation"变更为第一条用户消息的截断版本，并在之后更新为 AI 生成的标题。

## 迁移说明

- 无数据库 schema 变更。
- `BridgeConversationManager`、`SessionRepository` 或任何仓库接口均无 API 变更。
- `BridgeStateTracker` 新增四个公开成员：`newSessionFromBridge`（SharedFlow）、`emitNewSessionFromBridge()`、`activeAppSessionId`（StateFlow）和 `setActiveAppSession()`。`ChatScreen` 和 `ChatViewModel` 通过现有的对 `BridgeStateTracker` 的共享依赖访问这些成员。
- `BridgeConversationManagerImpl.createNewConversation()` 新增通过 `softDeleteSession()` 实现的创建前清理逻辑。行为变更仅在上一个会话的 `messageCount == 0` 时可见；其他所有情况不受影响。
- `BridgeConversationManagerImpl.getActiveConversationId()` 现在优先使用 `BridgeStateTracker.activeAppSessionId`，而非数据库查询。仅当应用已明确设置活跃会话时才会改变路由行为；冷启动时回退到 `getMostRecentSessionId()` 的逻辑不变。
- `ChatViewModel.initialize()` 现在在每次会话切换时调用 `BridgeStateTracker.setActiveAppSession(sessionId)`。这是纯副作用新增；ViewModel UI 状态行为不变。
- `ChatViewModel` 新增 `newConversation()` 方法。`ChatScreen` 现在从抽屉的"新建对话"按钮调用此方法，而非 `initialize(null)`。
- `BridgeAgentExecutorImpl` 新增两个构造函数依赖（`SessionRepository`、`GenerateTitleUseCase`），通过 `BridgeModule` 注入。直接构造 `BridgeAgentExecutorImpl` 的调用方（如单元测试）须提供这些依赖。
