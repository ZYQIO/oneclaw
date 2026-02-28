# 测试报告：RFC-001 — 对话交互

## 报告信息

| 字段 | 值 |
|------|-----|
| RFC | RFC-001 |
| 提交 | `bdea03c` |
| 日期 | 2026-02-27 |
| 测试人 | AI（OpenCode） |
| 状态 | PASS |

## 概述

RFC-001 实现了完整的对话交互循环：来自 OpenAI、Anthropic（含 thinking blocks）和 Gemini 的 SSE 流式传输；`SendMessageUseCase` 中的多轮工具调用循环；以及完整的 Gemini 风格聊天 UI，包含消息气泡、工具调用卡片、思考块、流式光标和 Agent 选择器。所有可行的测试层均已成功执行。

| 层 | 步骤 | 结果 | 说明 |
|----|------|------|------|
| 1A | JVM 单元测试 | PASS | 245 个测试，0 个失败 |
| 1B | 设备 DAO 测试 | PASS | 48 个测试，0 个失败 |
| 1C | Roborazzi 截图测试 | PASS | 8 张新截图 |
| 2 | adb 视觉验证 | PASS | Flow 1-1 在 Pixel 6a（Android 16）上执行 |

## Layer 1A：JVM 单元测试

**命令：** `./gradlew test`

**结果：** PASS

**测试数量：** 245 个测试，0 个失败

主要变更：
- `OpenAiAdapterTest.sendMessageStream returns a Flow without throwing` — 替换了过时的"throws NotImplementedError"测试。该方法现在返回 `Flow<StreamEvent>`，此测试验证它不会抛出异常。
- 所有现有适配器测试（listModels、testConnection、generateSimpleCompletion）继续通过。

## Layer 1B：设备测试

**命令：** `ANDROID_SERIAL=emulator-5554 ./gradlew connectedAndroidTest`

**结果：** PASS

**设备：** Medium_Phone_API_36.1（AVD）— emulator-5554

**测试数量：** 48 个测试，0 个失败

RFC-001 未新增设备测试（Chat 逻辑在适配器层可进行单元测试；DAO 层无变更）。

## Layer 1C：Roborazzi 截图测试

**命令：**
```bash
./gradlew recordRoborazziDebug
./gradlew verifyRoborazziDebug
```

**结果：** PASS

在 `AgentScreenshotTest`（RFC-001 和 RFC-002 共享文件）中录制的新截图：

### ChatTopBar

<img src="screenshots/RFC-001_ChatTopBar.png" width="250">

视觉检查：左侧汉堡菜单图标；中央"General Assistant"标题带下拉箭头（金/琥珀色文字）；右侧设置齿轮图标。

### ChatInput — 空状态

<img src="screenshots/RFC-001_ChatInput_empty.png" width="250">

视觉检查：带"Message"占位符的边框输入框；无文字时发送按钮禁用（灰色）。

### ChatInput — 含文字

<img src="screenshots/RFC-001_ChatInput_withText.png" width="250">

视觉检查：输入框中显示文字；发送按钮启用（有颜色）。

### ChatEmptyState

<img src="screenshots/RFC-001_ChatEmptyState.png" width="250">

视觉检查：无消息时显示居中的空状态占位符。

### MessageList — 对话

<img src="screenshots/RFC-001_ChatMessageList_conversation.png" width="250">

视觉检查：用户消息以金/琥珀色圆角气泡显示在右侧；AI 回复以 Surface 色卡片显示在左侧，Markdown 渲染正确（**粗体**显示正常）；AI 消息下方显示模型 ID "gpt-4o" 及复制/重新生成图标。

### MessageList — 工具调用

<img src="screenshots/RFC-001_ChatMessageList_toolCall.png" width="250">

视觉检查：用户消息气泡，然后是显示工具名称"get_current_time"的 TOOL_CALL 卡片，再是显示输出的 TOOL_RESULT 卡片，最后是 AI 最终回复。

### MessageList — 流式传输中

<img src="screenshots/RFC-001_ChatMessageList_streaming.png" width="250">

视觉检查：用户消息气泡，然后是 AI 气泡中显示的流式文字（流式光标可见）。

### MessageList — 活跃工具调用

<img src="screenshots/RFC-001_ChatMessageList_activeToolCall.png" width="250">

视觉检查：用户消息气泡，然后是显示 PENDING 状态的活跃 TOOL_CALL 卡片，工具名"read_file"及参数可见。

## Layer 2：adb 视觉验证

**结果：** PASS（Flow 1-1）

**设备：** Pixel 6a，Android 16

**Provider：** Anthropic（`claude-sonnet-4-6`）

**执行流程：** Flow 1-1 — 发送消息，流式响应出现

---

### Flow 1-1：发送消息 — 流式响应出现

**结果：** PASS

**步骤与观察：**

**Step 1 — Chat 屏幕打开**

<img src="screenshots/Flow1-1_step1_launch.png" width="280">

TopAppBar 可见（汉堡菜单、"General Assistant" 标题带下拉箭头、设置图标）。屏幕中央显示空状态"How can I help you today?"。底部输入框和发送按钮正常显示。无"未配置 Provider"Snackbar — Provider 已配置。

**Step 2 — 消息已输入**

<img src="screenshots/Flow1-1_step2_typed.png" width="280">

"Hello, who are you?" 已输入至胶囊形输入框。发送按钮激活（金/琥珀色填充）。键盘可见。

**Step 4a — 发送后立即（0.5 秒）：停止按钮可见，流式传输已启动**

<img src="screenshots/Flow1-1_step4_streaming_stop_visible.png" width="280">

用户消息气泡右对齐出现（金色）。下方 AI 响应开始（第一条消息的回复已完成显示 — 模型响应较快）。输入区底部右侧可见**停止按钮（红色方块图标）**。输入框禁用（"Message"占位符，不可编辑）。

**Step 4b — 流式传输进行中（1.2 秒）：Markdown 渲染 + 闪烁光标**

<img src="screenshots/Flow1-1_step4_streaming_mid.png" width="280">

流式 AI 气泡显示完整渲染的 Markdown：H1 标题（"Neural Networks & Backpropagation: A Detailed Explanation"）、H2 章节标题（"1. The Big Picture"）、**加粗**和*斜体*文字渲染正确。文字末尾可见闪烁光标 `|`（流式传输进行中）。停止按钮持续显示。

**Step 4c — 流式传输继续（1.7 秒）**

<img src="screenshots/Flow1-1_step4_streaming_initiated.png" width="280">

可见之前已完成的对话（第一条"Hello, who are you?"消息及其完整回复和操作行）。新的用户消息气泡（反向传播问题）可见。停止按钮存在 — 新消息正在流式传输中。

**Step 5 — 流式传输完成：发送按钮恢复**

流式传输在约 4 秒内完成。停止按钮消失；发送按钮（箭头图标，输入框为空时变暗）重新出现。

**Step 6 — 最终状态：操作行和模型标签**

<img src="screenshots/Flow1-1_step6_action_row.png" width="280">

已完成 AI 响应的末尾。消息气泡下方：复制图标、重新生成图标、模型标签 **`claude-sonnet-4-6`** 均可见。输入区显示发送按钮（非停止按钮），确认流式传输已完成。

---

**额外观察：**
- Markdown 渲染端到端正常：H1/H2 标题、加粗、斜体、无序列表均正确渲染。
- 数学符号（如 `max(0,z)`）回退为纯文本 — 符合预期，Markdown 库不支持 LaTeX。
- "Hello, who are you?" 响应过快（1.5 秒内已完成），因此使用了较长的问题以稳定捕获流式中间状态。

**尚未执行的流程：** Flow 1-2 至 Flow 1-9（键盘布局、停止生成、重新生成、工具调用、错误卡片、Thinking block），待后续 session 执行。

## 发现的问题

无阻塞性问题。一处外观观察：

- **数学符号渲染**：LaTeX 风格公式（如 `max(0,z)`、`∂L/∂w`）以纯文本片段分行显示，而非排版后的数学公式。这是 `compose-markdown` 库的已知限制，已在 RFC-001 开放问题中记录，不属于回归问题。

## 变更记录

| 日期 | 变更 |
|------|------|
| 2026-02-27 | 初始报告 |
| 2026-02-27 | 在 Pixel 6a 上执行 Layer 2 Flow 1-1；章节从 SKIP 更新为 PASS |
