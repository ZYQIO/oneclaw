# 人工测试手册 — OneClawShadow

本文档是 OneClawShadow 应用的累积性、始终保持最新的人工测试指南。它描述了当前实现中所有用户可见的流程，以及如何在真实设备或模拟器上进行验证。

**使用方式：**
- 每次发布前，从全新安装状态开始，完整执行本手册中的所有流程。
- 每当某个 RFC 新增、修改或移除用户可见的行为时，更新本手册。
- 本手册反映 App 当前实际状态，不包含计划中的未来功能。

## 文档信息

| 字段 | 内容 |
|------|------|
| 最后更新 | 2026-02-27 |
| App 版本 | 0.1.0 |
| 最后实现的 RFC | RFC-005（Session 管理） |
| 当前状态 | Provider 管理 + Tool 系统 + Session 管理后端已实现；Chat 功能尚未实现 |

---

## 准备工作

### 前置条件

- Android 设备或模拟器（API 26+）
- 已安装 App：`./gradlew installDebug` 或 `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- 如需测试 Provider 连接：至少一个 Provider 的有效 API key（Anthropic、OpenAI 或 Gemini）

### 全新安装

```bash
adb shell pm clear com.oneclaw.shadow
adb shell am start -n com.oneclaw.shadow/.MainActivity
```

---

## 当前 App 状态

截至最后实现的 RFC（RFC-005），App 支持以下功能：
- 首次启动引导流程（选择 Provider、输入 API key、选择默认模型）
- Provider 管理（列表、详情、API key、连接测试、模型列表）
- 设置界面
- Tool 系统（仅后端实现，暂无 Tool 配置 UI）
- Session 管理（后端 + 抽屉 UI 已就绪，但尚未接入 MainActivity 导航图）
- Chat 界面（占位符状态，发送消息功能尚未实现）

**尚未实现：** Chat 消息发送和流式响应（RFC-001）、Agent 管理 UI（RFC-002）、Session 抽屉接入导航图（RFC-001 集成点）。

---

## 流程 1：首次启动 — 设置引导

**触发时机：** 全新安装，`has_completed_setup` 标志为 false。

**预期行为：** App 启动后直接进入设置引导界面（3 步向导）。

### 步骤 1.1：全新启动 App

```bash
adb shell pm clear com.oneclaw.shadow
adb shell am start -n com.oneclaw.shadow/.MainActivity
```

**验证：**
- 显示设置引导界面（不是 Chat 界面）
- 显示第 1 步"选择 Provider"
- 可见三个 Provider 选项：OpenAI、Anthropic、Google Gemini
- 可见"跳过"按钮

### 步骤 1.2：跳过设置

在设置界面点击"Skip for now"（暂时跳过）。

**验证：**
- App 导航至 Chat 界面
- Chat 界面显示占位符/欢迎语
- `has_completed_setup` 现在为 true（重新启动 App 应直接进入 Chat，不再显示 Setup）

### 步骤 1.3：完成设置 — 选择 Provider

全新安装后，点击"Anthropic"（或其他 Provider）。

**验证：**
- 显示第 2 步"输入 API Key"
- 可见 API key 输入框
- 可见"Test Connection"（测试连接）按钮
- "Skip for now"按钮仍然可见

### 步骤 1.4：输入 API key 并测试连接

在输入框中输入有效的 API key，点击"Test Connection"。

**验证：**
- 测试过程中显示加载指示器
- 成功时：显示绿色成功提示或"Connected"文字
- 失败时：显示错误信息（如"Invalid API key"或"Network error"）

### 步骤 1.5：进入模型选择

连接测试成功后，进入第 3 步。

**验证：**
- 显示第 3 步"Select Default Model"（选择默认模型）
- 显示该 Provider 的模型列表
- 每个模型可选择
- 选中模型后"Get Started"按钮变为可点击状态

### 步骤 1.6：完成设置

选择模型后点击"Get Started"。

**验证：**
- App 导航至 Chat 界面
- `has_completed_setup` 为 true（下次启动直接进入 Chat）

---

## 流程 2：设置界面

**入口：** 从 Chat 界面点击右上角设置齿轮图标。

### 步骤 2.1：打开设置

**验证：**
- 打开设置界面
- 可见"Manage Providers"列表项，副标题"Add API keys, configure models"
- 返回导航有效（左上角返回箭头）

### 步骤 2.2：进入 Provider 列表

点击"Manage Providers"。

**验证：**
- 打开 Provider 列表界面
- 显示三个内置 Provider：OpenAI、Anthropic、Google Gemini
- 每个 Provider 行显示：名称、模型数量、状态标签
- 状态标签为以下之一："Connected"（绿色/紫色）、"Not configured"（灰色）、"Disconnected"（红色/粉色）
- 右上角可见"+"按钮（为未来自定义 Provider 预留）

---

## 流程 3：Provider 详情 — API Key 管理

**入口：** 设置 → Manage Providers → 点击某个 Provider（如 Anthropic）。

### 步骤 3.1：打开 Provider 详情

**验证：**
- 打开 Provider 详情界面
- 顶部显示 Provider 名称
- API key 区域：遮蔽输入框（已有 key 时显示"••••"，无 key 时为空）
- 眼睛图标（切换显示/隐藏）
- 保存按钮
- "Test Connection"按钮
- 模型列表区域，显示可用模型
- 每个模型显示：名称、来源标签（PRESET 或 MANUAL）、默认模型星标图标

### 步骤 3.2：输入并保存 API key

点击 API key 输入框，输入 key，点击保存。

**验证：**
- Key 已保存（遮蔽显示更新）
- 保存后 UI 不显示明文 key
- 出现成功提示

### 步骤 3.3：切换 API key 显示

点击 API key 旁边的眼睛图标。

**验证：**
- Key 以明文形式显示
- 眼睛图标切换为"隐藏"样式
- 再次点击：key 重新遮蔽

### 步骤 3.4：测试连接

输入有效 API key 后，点击"Test Connection"。

**验证：**
- 显示加载指示器
- 成功时：显示"Connected"结果（绿色指示器或成功卡片）
- 失败时：显示错误类型（认证错误、网络错误、超时）

### 步骤 3.5：查看模型列表

**验证：**
- "Available Models"区域列出模型
- 每个模型显示：ID、来源（PRESET 或 MANUAL）、若为全局默认则显示星标

### 步骤 3.6：设置默认模型

点击非默认模型旁边的星标图标。

**验证：**
- 该模型的星标变为实心
- 之前默认模型的星标变为空心
- 出现成功提示

### 步骤 3.7：刷新模型列表

点击"Refresh Models"按钮。

**验证：**
- 显示加载指示器
- 模型列表更新（从 Provider API 重新获取）
- 显示成功或错误提示

---

## 流程 4：Provider 详情 — 手动添加模型

**入口：** 设置 → Manage Providers → [任意 Provider] → Add Manual Model。

### 步骤 4.1：添加手动模型

点击"Add Manual Model"按钮，输入模型 ID（如 `gpt-4-turbo`），确认。

**验证：**
- 模型出现在列表中，来源标签为"MANUAL"
- 可以将其设置为默认模型（星标可用）

### 步骤 4.2：删除手动模型

点击 MANUAL 模型旁边的删除图标，确认删除。

**验证：**
- 模型从列表中移除
- PRESET 模型不能删除（不显示删除图标）

---

## 流程 5：重新启动 — 首次启动检测

**验证 App 是否记住设置状态。**

### 步骤 5.1：完成设置后关闭并重新启动

```bash
adb shell am force-stop com.oneclaw.shadow
adb shell am start -n com.oneclaw.shadow/.MainActivity
```

**验证：**
- App 直接启动至 Chat 界面（不显示 Setup）
- 之前输入的 API key 仍然生效（检查：设置 → Providers）

---

## 流程 6：Chat 界面（占位符状态）

**注意：** Chat 功能（RFC-001）尚未实现。本流程仅验证 UI 外壳。

### 步骤 6.1：进入 Chat 界面

从任意界面导航回 Chat 界面。

**验证：**
- Chat 界面可见
- 显示占位符或空状态（如"How can I help you today?"）
- 底部可见文字输入框
- 顶部栏显示 Agent 名称
- 右上角有设置齿轮图标
- 左上角有抽屉/汉堡菜单图标

### 步骤 6.2：Session 抽屉（占位符）

点击左上角汉堡菜单图标。

**验证：**
- 抽屉打开
- 顶部显示"New Conversation"按钮
- 列出已有 Session（全新安装时为空）
- 已有 Session 项（创建后）显示：标题、消息预览、相对时间戳、Agent 名称标签
- 长按某个 Session 项可进入选择模式（显示复选框和批量删除工具栏）
- 向左滑动 Session 项可触发删除操作

---

## 当前已知限制

| 功能 | 状态 | 预计 RFC |
|------|------|---------|
| 发送 Chat 消息 | 未实现 | RFC-001 |
| 流式 AI 响应 | 未实现 | RFC-001 |
| Agent 创建/管理 UI | 未实现 | RFC-002 |
| Session 抽屉接入导航图 | 未实现 | RFC-001 集成 |
| Chat 中的 Tool 调用可视化 | 未实现 | RFC-001 + RFC-004 |

---

## 变更历史

| 日期 | RFC | 变更内容 |
|------|-----|---------|
| 2026-02-27 | RFC-003、RFC-004 | 初始版本，涵盖 Setup、Settings、Provider 管理流程 |
| 2026-02-27 | RFC-005 | 更新当前状态，补充步骤 6.2 Session 抽屉细节，更新已知限制表 |
