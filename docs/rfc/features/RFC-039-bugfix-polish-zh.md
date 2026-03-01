# RFC-039: Bug 修复与 UI 优化专项

## 文档信息
- **RFC ID**: RFC-039
- **关联 PRD**: [FEAT-039 (Bug Fix & UI Polish)](../../prd/features/FEAT-039-bugfix-polish.md)
- **创建日期**: 2026-03-01
- **最后更新**: 2026-03-01
- **状态**: Completed
- **作者**: TBD

## 概述

### 背景

在 RFC-038 完成初始功能实现后，用户测试发现了 15 个问题，涵盖严重 bug（Google OAuth 失败、文件浏览器无法使用）到 UX 缺陷（无附件按钮、无工具参数展示）。本 RFC 记录了在一次协调性专项中解决全部 15 个问题所做的技术变更。

### 目标

1. 修复聊天、认证、文件浏览器、搜索、日程与工具模块中的 8 个功能性 bug
2. 在 7 个方向上改善 UX，提供更好的视觉反馈、操作控件和导航引导
3. 保持所有现有测试通过，更新受行为变更影响的测试
4. 零数据库 schema 变更——所有修复均为纯代码层面

### 非目标

- 超出已报告问题范围的新功能开发
- 数据库迁移
- 引入新的外部依赖
- 附件选择器的完整 ActivityResultLauncher 接线（仅占位实现）

## 技术设计

### 变更文件总览

```
app/
├── build.gradle.kts                              # MODIFIED (test config)
├── src/main/
│   ├── AndroidManifest.xml                        # MODIFIED (network security)
│   ├── res/xml/
│   │   └── network_security_config.xml            # NEW
│   └── kotlin/com/oneclaw/shadow/
│       ├── data/
│       │   ├── security/
│       │   │   └── GoogleAuthManager.kt           # MODIFIED (6 sub-fixes)
│       │   └── storage/
│       │       └── UserFileStorage.kt             # MODIFIED (rootDir)
│       ├── feature/
│       │   ├── agent/
│       │   │   ├── AgentDetailScreen.kt           # MODIFIED (behavior section)
│       │   │   ├── AgentDetailViewModel.kt        # MODIFIED (save logic)
│       │   │   ├── AgentUiState.kt                # MODIFIED (hasRuntimeChanges)
│       │   │   └── usecase/
│       │   │       └── CreateAgentUseCase.kt      # MODIFIED (new params)
│       │   ├── bridge/
│       │   │   └── BridgeSettingsScreen.kt        # MODIFIED (cards, text)
│       │   ├── chat/
│       │   │   ├── ChatScreen.kt                  # MODIFIED (attachment, tool card)
│       │   │   └── ChatViewModel.kt               # MODIFIED (flush condition)
│       │   ├── schedule/
│       │   │   ├── ScheduledTaskEditViewModel.kt  # MODIFIED (alarm check)
│       │   │   └── ScheduledTaskListScreen.kt     # MODIFIED (clickable, icon)
│       │   ├── search/usecase/
│       │   │   └── SearchHistoryUseCase.kt        # MODIFIED (5s buffer, logs)
│       │   ├── settings/
│       │   │   ├── GoogleAuthScreen.kt            # REWRITTEN
│       │   │   └── GoogleAuthViewModel.kt         # MODIFIED (edit mode)
│       │   └── tool/
│       │       ├── ToolManagementScreen.kt        # MODIFIED (categories)
│       │       └── ToolManagementViewModel.kt     # MODIFIED (categorize, refresh)
│       └── tool/engine/
│           └── ToolRegistry.kt                    # MODIFIED (version flow)
└── src/test/kotlin/com/oneclaw/shadow/feature/
    ├── schedule/
    │   └── ScheduledTaskEditViewModelTest.kt      # MODIFIED
    └── search/usecase/
        └── SearchHistoryUseCaseTest.kt            # MODIFIED
```

## 详细设计

### 修复 1：会话切换时日志未刷新

**文件**：`ChatViewModel.kt:78`

**根因**：会话切换条件 `if (previousSessionId != null && sessionId != null && ...)` 要求*新*的 sessionId 也不为空。当切换到"新会话"（sessionId = null）时，刷新操作被跳过。

**修复**：去除 `sessionId != null` 的判断。

```kotlin
// Before
if (previousSessionId != null && sessionId != null && previousSessionId != sessionId) {

// After
if (previousSessionId != null && previousSessionId != sessionId) {
```

**影响**：`memoryTriggerManager?.onSessionSwitch(previousSessionId)` 现在会在离开任意会话时触发，无论目标是具名会话还是新对话。

---

### 修复 2：唤醒锁说明文字

**文件**：`BridgeSettingsScreen.kt`

**变更**：在唤醒锁开关下方新增一个 `Text` 可组合项，说明其用途及对电池的影响。

```kotlin
Text(
    text = "Keeps the bridge service alive when the screen is off. " +
        "Required for reliable message delivery, but increases battery usage.",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
)
```

---

### 修复 3：Google OAuth 流程（6 项子修复）

**文件**：`GoogleAuthManager.kt`、`AndroidManifest.xml`、`network_security_config.xml`（新增）

#### 3A：网络安全配置
- 新建 `res/xml/network_security_config.xml`，允许向 `127.0.0.1` 发送明文流量（OAuth 回环重定向所需）。
- 通过 `android:networkSecurityConfig` 在 `AndroidManifest.xml` 中引用。

#### 3B：回环服务器绑定
```kotlin
// Before
val serverSocket = ServerSocket(0)

// After
val serverSocket = withContext(Dispatchers.IO) {
    ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
}
```
显式绑定 `127.0.0.1` 与 oneclaw-1 参考实现一致，防止绑定到 `0.0.0.0`。

#### 3C：令牌交换重试
在 `exchangeCodeForTokens()` + `fetchUserEmail()` 外层增加重试循环（3 次，间隔 3 秒）。首次尝试常因浏览器重定向回来时应用仍在后台、Android 可能限制网络访问而失败。

```kotlin
repeat(3) { attempt ->
    if (attempt > 0) {
        Log.d(TAG, "Token exchange retry $attempt after: ${lastError?.message}")
        delay(3000)
    }
    try {
        val tokens = exchangeCodeForTokens(...)
        val email = fetchUserEmail(tokens.accessToken)
        // save and return success
    } catch (e: UnknownHostException) { lastError = e }
      catch (e: SocketTimeoutException) { lastError = e }
      catch (e: Exception) { lastError = e }
}
```

#### 3D：NonCancellable 包裹
用 `withContext(NonCancellable)` 包裹关键的授权码接收与令牌交换流程，防止 Activity 重建时协程在流程中途被取消。

#### 3E：浏览器 Intent 修复
- 从浏览器 Intent 中移除 `FLAG_ACTIVITY_NEW_TASK`。
- 在 `Dispatchers.Main` 上启动浏览器，避免上下文问题。

#### 3F：具体错误信息
```kotlin
val errorMsg = when (lastError) {
    is UnknownHostException -> "Network unavailable. Check your internet connection and try again."
    is SocketTimeoutException -> "Connection timed out. Check your internet connection and try again."
    else -> lastError?.message ?: "Authorization failed"
}
```

#### 其他重构
- `waitForAuthCode()` 现在返回 `String?`（可空），而非在失败时抛出异常。
- 提取 `parseAuthCode()` 方法，使 URL 解析更清晰。
- 在解析令牌 JSON 前增加 `response.isSuccessful` 校验。
- `Json` 实例改为类级别 `val json = Json { ignoreUnknownKeys = true }` 复用。
- ServerSocket 在 `finally` 块中配合 `NonCancellable` 关闭。

---

### 修复 4：Google 认证界面重写

**文件**：`GoogleAuthScreen.kt`、`GoogleAuthViewModel.kt`

#### 新增 UI 组件

1. **StatusCard**——显示"Connected: email@example.com"或"Not connected"，并说明 Google Workspace 插件所提供的功能。

2. **PermissionsCard**——列出所有请求的 OAuth 权限范围（Gmail、Calendar、Tasks、Contacts、Drive、Docs、Sheets、Slides、Forms）及说明。

3. **未验证应用警告卡片**——解释"Google 尚未验证此应用"警告的含义及安全处理方式。

4. **SetupInstructions**——GCP 项目配置的 9 步编号指引：
   - 访问 console.cloud.google.com（可点击链接）
   - 启用 9 个特定 API
   - 配置 OAuth 同意界面
   - 设置品牌信息、发布应用
   - 添加 11 个 OAuth 权限范围
   - 创建桌面版 OAuth 客户端
   - 复制 Client ID 和 Secret

5. **CredentialsSection**——三态展示：
   - **已登录**：显示已连接状态、"Disconnect"按钮、"Change OAuth Credentials"按钮
   - **已有凭据但未登录**：显示带加载状态的"Authorize with Google"按钮、重试提示、"Change OAuth Credentials"按钮
   - **无凭据/编辑中**：显示带密码可见性切换的输入框，以及 Save/Cancel 按钮

6. **错误展示**——使用 `errorContainer` Card 替代普通错误色文本。

#### ViewModel 变更

- 新增 `editingCredentials: Boolean` 追踪凭据编辑模式
- 新增 `dirty: Boolean` 追踪凭据字段的未保存变更
- 新增 `startEditingCredentials()`、`cancelEditingCredentials()`、`clearError()` 方法
- `onClientIdChanged` / `onClientSecretChanged` 现在会将 `dirty` 置为 `true`
- `saveCredentials()` 会重置 `editingCredentials` 和 `dirty`

---

### 修复 5：内置 Agent 运行时设置

**文件**：`AgentDetailScreen.kt`、`AgentDetailViewModel.kt`、`AgentUiState.kt`

**问题**：内置 Agent 的网络搜索开关被禁用（`enabled = !uiState.isBuiltIn`），且保存按钮对内置 Agent 不显示。

**修复**：
- 从网络搜索开关及其父 Row 的 `clickable` 上移除 `enabled = !uiState.isBuiltIn`。
- 在 `AgentDetailUiState` 中新增 `hasRuntimeChanges` 计算属性：
  ```kotlin
  val hasRuntimeChanges: Boolean
      get() = webSearchEnabled != savedWebSearchEnabled ||
          temperature != savedTemperature ||
          maxIterations != savedMaxIterations
  ```
- 当 `!uiState.isBuiltIn || uiState.hasRuntimeChanges` 时显示保存按钮。
- 已有 Agent 的 `hasUnsavedChanges` 对运行时字段现在委托给 `hasRuntimeChanges`。

**保存逻辑变更**：保存内置 Agent 时，ViewModel 从 `originalAgent` 中保留不可变字段：
```kotlin
val updated = Agent(
    id = state.agentId!!,
    name = if (state.isBuiltIn) orig.name else state.name.trim(),
    description = if (state.isBuiltIn) orig.description else ...,
    systemPrompt = if (state.isBuiltIn) orig.systemPrompt else ...,
    // Runtime fields always from state
    temperature = state.temperature,
    maxIterations = state.maxIterations,
    webSearchEnabled = state.webSearchEnabled,
    isBuiltIn = orig.isBuiltIn,
    ...
)
```

---

### 修复 6：温度与最大迭代次数 UI

**文件**：`AgentDetailScreen.kt`、`CreateAgentUseCase.kt`

在 Agent 详情界面的网络搜索下方新增"BEHAVIOR"分区：

- **温度滑块**：范围 0.0–2.0，20 个步进，显示当前值。说明文字："Lower values produce more focused output; higher values are more creative."
- **最大迭代次数文本框**：`OutlinedTextField`，使用数字键盘，验证范围 1–100，辅助文字显示范围或错误提示。

`CreateAgentUseCase` 更新为接受 `webSearchEnabled`、`temperature`、`maxIterations` 参数，并将其传入 Agent 构造函数。

---

### 修复 7：附件按钮与选择器集成

**文件**：`ChatScreen.kt`

- 为 `ChatInput` 新增 `onAttachmentClick` 和 `hasPendingAttachments` 参数。
- 在技能按钮旁新增圆形容器内的附件按钮（AttachFile 图标）。
- 当 `uiState.pendingAttachments` 不为空时，在输入框上方显示 `AttachmentPreviewRow`。
- 当 `showAttachmentPicker` 为 true 时，显示 `AttachmentPickerSheet`。
- 发送按钮条件从 `text.isNotBlank() && hasConfiguredProvider` 改为 `(text.isNotBlank() || hasPendingAttachments) && hasConfiguredProvider`。

注意：`AttachmentPickerSheet` 中的 `ActivityResultLauncher` 回调为占位实现（`{ /* Caller wires externally */ }`）。

---

### 修复 8：工具调用参数展示

**文件**：`ChatScreen.kt`（ToolCallCard 可组合项）

将 `ToolCallCard` 从简单的 Row 重写为带展开/折叠功能的 Column：

```kotlin
var expanded by remember { mutableStateOf(false) }
val hasInput = !toolInput.isNullOrBlank()
```

- 仅当 `hasInput` 为 true 时卡片可点击。
- 折叠状态显示工具名称及展开/折叠图标。
- 展开状态以等宽 `bodySmall` 字体显示 `toolInput`，截断为 20 行并使用 `TextOverflow.Ellipsis`。

---

### 修复 9：ToolRegistry 版本 Flow

**文件**：`ToolRegistry.kt`

新增 `StateFlow<Int>` 版本计数器，在每次结构性变更时自增：

```kotlin
@PublishedApi
internal val _version = MutableStateFlow(0)
val version: StateFlow<Int> = _version.asStateFlow()
```

在 `register()`、`unregister()` 以及实际移除了键时的 `unregisterByType()` 中进行自增。

`_version` 标注为 `@PublishedApi internal`，因为 `unregisterByType()` 是需要访问它的内联 reified 函数。

---

### 修复 10：内置工具分类分组

**文件**：`ToolManagementViewModel.kt`、`ToolManagementScreen.kt`

#### 数据模型
```kotlin
data class BuiltInCategoryUiItem(
    val category: String,
    val tools: List<ToolUiItem>,
    val isExpanded: Boolean = false
)
```

#### 分类逻辑
```kotlin
private fun categorizeBuiltInTools(tools: List<ToolUiItem>): List<BuiltInCategoryUiItem> {
    val categorized = tools.groupBy { tool ->
        val name = tool.name.lowercase()
        when {
            name.startsWith("calendar") -> "Calendar"
            name.startsWith("config") -> "Config"
            name.startsWith("provider") || name.startsWith("model") -> "Provider / Model"
            name.startsWith("agent") -> "Agent"
            name.startsWith("schedule") || name.startsWith("scheduled") -> "Scheduling"
            name.startsWith("file") || name.startsWith("http") || name.startsWith("web") -> "Files & Web"
            name.startsWith("pdf") -> "PDF"
            name.startsWith("js") -> "JS Tools"
            else -> "Other"
        }
    }
    // Ordered display
    val categoryOrder = listOf(
        "Calendar", "Config", "Provider / Model", "Agent",
        "Scheduling", "Files & Web", "PDF", "JS Tools", "Other"
    )
    // ...
}
```

#### UI
- `BuiltInCategoryHeader` 可组合项，带展开/折叠箭头及工具数量。
- `toggleBuiltInCategoryExpanded()` 方法用于切换分类展开状态。
- 展开状态在重新加载后通过已有状态匹配得以保留。

#### 自动刷新
```kotlin
init {
    loadTools()
    viewModelScope.launch {
        toolRegistry.version.drop(1).collect { loadTools() }
    }
}
```

---

### 修复 11：Bridge 设置卡片包裹

**文件**：`BridgeSettingsScreen.kt`

- `ChannelSection` 可组合项现在将内容包裹在 `Surface(shape = RoundedCornerShape(16.dp), color = surfaceVariant)` 中。
- 所有 `HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))` 替换为 `Spacer(modifier = Modifier.height(12.dp))`。

---

### 修复 12：文件浏览器根目录

**文件**：`UserFileStorage.kt`

```kotlin
// Before
open val rootDir: File
    get() = File(context.filesDir, "user_files").also { it.mkdirs() }

// After
open val rootDir: File
    get() = context.filesDir
```

`user_files/` 子目录实际不存在，始终为空。改为 `context.filesDir` 后，文件浏览器可展示所有应用内部文件（memory/、daily_logs/、databases 等）。

---

### 修复 13：搜索历史排除当前消息

**文件**：`SearchHistoryUseCase.kt`

**根因**：AI 调用 `search_history` 时，用户消息已被保存到数据库。当 `dateTo = null` 时，搜索以 `Long.MAX_VALUE` 作为上界，导致当前消息被包含在结果中。

**修复**：新增 5 秒缓冲常量，并在 `dateTo` 为 null 时应用：

```kotlin
companion object {
    private const val RECENT_MESSAGE_BUFFER_MS = 5_000L
}

val createdBefore = if (dateTo != null) dateTo
    else System.currentTimeMillis() - RECENT_MESSAGE_BUFFER_MS
```

在搜索管道全链路中新增 `Log.d` 调试日志，便于生产环境排查问题。

---

### 修复 14：计划任务列表可点击性

**文件**：`ScheduledTaskListScreen.kt`

- 在 `ScheduledTaskItem` 的 Row 上添加 `clickable(onClick = onEdit)` 修饰符。
- 在 Switch 后新增 `ChevronRight` 图标（20.dp）作为视觉引导。
- 新增所需导入：`clickable`、`ChevronRight`、`size`。

---

### 修复 15：编辑任务时的精确闹钟权限检查

**文件**：`ScheduledTaskEditViewModel.kt`

```kotlin
// Before (skipped check for edits)
if (!state.isEditing && !exactAlarmHelper.canScheduleExactAlarms()) {

// After (checks for both new and edited tasks)
if (!exactAlarmHelper.canScheduleExactAlarms()) {
```

---

## 测试变更

### SearchHistoryUseCaseTest

更新之前期望 `createdBefore` 为 `Long.MAX_VALUE` 的 `coVerify` 断言，改用 `any()` 匹配器，因为实际值现在是动态的 `System.currentTimeMillis() - 5000`。

```kotlin
// Before
coVerify { messageDao.searchContent("test", 0L, Long.MAX_VALUE, 50) }

// After
coVerify { messageDao.searchContent("test", 0L, any(), 50) }
```

### ScheduledTaskEditViewModelTest

更新测试 `save does not show dialog when editing existing task` 以反映新行为：

```kotlin
// Before: asserted dialog NOT shown, save succeeded
assertFalse(state.showExactAlarmDialog)
assertTrue(state.savedSuccessfully)
coVerify(exactly = 1) { updateUseCase(any()) }

// After: asserted dialog IS shown, save blocked
assertTrue(state.showExactAlarmDialog)
assertFalse(state.savedSuccessfully)
coVerify(exactly = 0) { updateUseCase(any()) }
```

### 构建配置

在 `app/build.gradle.kts` 的 `testOptions.unitTests` 下新增 `isReturnDefaultValues = true`，防止 `SearchHistoryUseCase` 中的 `android.util.Log.d()` 调用在 JVM 单元测试中抛出 `RuntimeException`。

## 编译与测试结果

- 首次编译：发现并修复 2 个错误
  1. `ScheduledTaskListScreen.kt` 中缺少 `import androidx.compose.foundation.layout.size`
  2. `ToolRegistry` 中的 `_version` 为 `private`，但被内联 reified 函数 `unregisterByType()` 访问——改为 `@PublishedApi internal`
- 修复后：`./gradlew compileDebugUnitTestKotlin` 通过
- 测试修复后：`./gradlew test` 通过（所有测试绿色）

## 安全考虑

1. **network_security_config.xml**：仅允许向 `127.0.0.1` 发送明文流量，用于 OAuth 回环重定向。所有其他主机仍要求 HTTPS。
2. **OAuth 令牌**：继续使用 `EncryptedSharedPreferences`，令牌存储安全性无变更。
3. **文件浏览器根目录变更**：将 `context.filesDir` 而非 `user_files/` 暴露给文件浏览器，使所有应用内部文件可见。此为有意设计——文件浏览器是应用内面向用户的调试/管理工具，不可从外部访问。

## 性能考虑

- `ToolRegistry.version` StateFlow 增加的开销可忽略不计（原子整数自增）。
- `categorizeBuiltInTools()` 时间复杂度为 O(n)，其中 n 为内置工具数量（当前约 30 个）。
- `SearchHistoryUseCase` 5 秒缓冲对性能无影响。
- OAuth 重试循环仅在失败路径上最多增加 6 秒延迟。

---

## 第二轮：额外修复（问题 16-25）

### 修复 16：桥接设置 -- 可折叠频道标题

**文件**：`BridgeSettingsScreen.kt`

重构 `ChannelSection` 为单行可折叠标题：
- 左侧频道名称，右侧启用开关
- 移除冗余的"启用 X"标签（标题已标明频道名）
- 启用时（开关打开）：展开显示配置字段 + 设置指南
- 禁用时（开关关闭）：折叠为单行标题
- 使用 `AnimatedVisibility` 实现平滑展开/折叠动画

### 修复 17：桥接设置 -- 每个频道的设置指南

**文件**：`BridgeSettingsScreen.kt`

在每个展开的频道区内添加 `SetupGuide` 组合：
- 可点击的"设置指南"文本，切换编号步骤列表
- 频道特定的说明：
  - **Telegram**：@BotFather、/newbot、@userinfobot
  - **Discord**：开发者门户、Bot 令牌、Message Content Intent、OAuth2、开发者模式
  - **Slack**：api.slack.com/apps、OAuth 范围、App-Level Token、Socket Mode、事件订阅
  - **Matrix**：注册 bot 账号、Element 获取访问令牌、邀请至房间
  - **LINE**：developers.line.biz、创建 provider/channel、Channel Access Token、Channel Secret
  - **Web Chat**：端口选择、可选访问令牌、浏览器访问 URL

### 修复 18：Wake Lock 警告样式

**文件**：`BridgeSettingsScreen.kt`

将 Wake Lock 说明从普通 `onSurfaceVariant` 文本改为：
- `Row` 布局包含 `Icons.Default.Warning` 图标 + 文本
- 图标和文本均使用 `MaterialTheme.colorScheme.error` 颜色

### 修复 19：Google OAuth 屏幕崩溃

**文件**：`GoogleAuthScreen.kt`

**根因**：`pushLink(LinkAnnotation.Url(...))` 在 Compose BOM 2024.12.01 上崩溃，因为 `LinkAnnotation` API 尚未完全稳定。

**修复**：替换为稳定的 `pushStringAnnotation` + `ClickableText` 模式。同时修复了在组合期间的 `var stepNumber = 1` 副作用，改为显式传递步骤编号。

### 修复 20：删除凭据按钮

**文件**：`GoogleAuthManager.kt`、`GoogleAuthViewModel.kt`、`GoogleAuthScreen.kt`

- `GoogleAuthManager.clearAllCredentials()`：清除 OAuth 客户端凭据（client ID + secret）以及 EncryptedSharedPreferences 中的所有令牌/邮箱。
- `GoogleAuthViewModel.deleteCredentials()`：调用 `clearAllCredentials()` 并将 UiState 重置为初始空状态。
- `GoogleAuthScreen`：在已登录和有凭据状态下添加错误颜色的 `OutlinedButton`"删除凭据"。

### 修复 21：Agent 最大迭代次数 -- 滑块

**文件**：`AgentDetailScreen.kt`、`AgentUiState.kt`、`AgentDetailViewModel.kt`、`SendMessageUseCase.kt`

- 将 `OutlinedTextField` 替换为 `Slider`（范围 1-200，步数 = 198）
- 值为 200 时显示"无限制"并存储为 `null`
- `maxIterations` 为 `null` 时，滑块显示在位置 25（合理默认值）
- 从 `AgentUiState` 移除 `maxIterationsError`（滑块强制有效范围）
- `SendMessageUseCase`：将 `agent.maxIterations ?: MAX_TOOL_ROUNDS` 改为 `agent.maxIterations ?: Int.MAX_VALUE`（null = 真正无限制）

### 修复 22：附件选择器 -- 接线 ActivityResultLauncher

**文件**：`ChatScreen.kt`、`file_paths.xml`

在 `ChatScreen` 中注册 4 个 `rememberLauncherForActivityResult` 实例：
- **照片**：`PickVisualMedia()` + `ImageOnly` -> `viewModel.addAttachment(uri)`
- **视频**：`PickVisualMedia()` + `VideoOnly` -> `viewModel.addAttachment(uri)`
- **相机**：`TakePicture()` -> 在 `cache/camera_photos/` 创建临时文件，通过 `FileProvider` 获取 URI -> `viewModel.addCameraPhoto(file)`
- **文件**：`GetContent()` + `"*/*"` -> `viewModel.addAttachment(uri)`

在 `file_paths.xml` 添加 `<cache-path name="camera_photos" path="camera_photos/" />`。

### 修复 23：附件选择器 -- 深色模式状态栏

**文件**：`AttachmentPickerSheet.kt`

在 `ModalBottomSheet` 上设置显式遮罩颜色：`Color.Black.copy(alpha = 0.32f)`，防止默认遮罩干扰深色模式下的状态栏图标颜色。

### 修复 24：工具管理 -- Google 产品名称

**文件**：`ToolManagementViewModel.kt`

更新 `categorizeBuiltInTools()` 映射为完整的 Google 产品名称：
- `"calendar"` -> `"Google Calendar"`
- `"gmail"` -> `"Gmail"`
- `"drive"` -> `"Google Drive"`
- `"docs"/"document"` -> `"Google Docs"`
- `"sheets"/"spreadsheet"` -> `"Google Sheets"`
- `"slides"/"presentation"` -> `"Google Slides"`
- `"forms"` -> `"Google Forms"`
- `"contacts"/"people"` -> `"Google Contacts"`
- `"tasks"` -> `"Google Tasks"`

### 修复 25：工具管理 -- 徽章布局修复

**文件**：`ToolManagementScreen.kt`

重构 `ToolListItem` 布局：
- 工具名独占一行，`maxLines = 1` + `TextOverflow.Ellipsis`
- `SourceBadge` 移至第二行（与描述同行）
- 徽章不再与工具名争夺水平空间

## 变更历史

| Date | Version | Changes | Owner |
|------|---------|---------|-------|
| 2026-03-01 | 1.0 | Initial version (15 issues fixed) | - |
| 2026-03-01 | 2.0 | Round 2 (10 additional issues: 16-25) | - |
