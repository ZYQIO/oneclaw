# RFC-044: 工具组会话持久化

## 文档信息
- **RFC ID**: RFC-044
- **关联 PRD**: [FEAT-044（工具组会话持久化）](../../prd/features/FEAT-044-tool-group-persistence.md)
- **关联 RFC**: [RFC-040（工具组路由）](RFC-040-tool-group-routing.md)
- **创建日期**: 2026-03-01
- **最后更新**: 2026-03-01
- **状态**: 草稿
- **作者**: 待定

## 概述

### 背景

RFC-040 通过 `load_tool_group` 引入了动态工具加载机制，工具 schema 按需加载以减少 token 用量。然而，已加载的工具组仅在单次 `SendMessageUseCase.execute()` 调用期间有效。每条新用户消息都会触发一次新的 `execute()` 调用，并从头重新初始化活动工具列表：

```kotlin
// SendMessageUseCase.kt:136-137 -- 每次 execute() 都会重置
val loadedGroupNames = mutableSetOf<String>()
val activeToolDefs = toolRegistry.getCoreToolDefinitions().toMutableList()
```

这导致在前几轮对话中加载的工具在后续轮次变得不可用，并产生"工具 'X' 对该 agent 不可用"的错误。AI 在对话历史中看到了成功的 `load_tool_group` 结果，因此认为这些工具仍然可用，但实际上它们已被卸载。

实际观测到的影响：在单轮对话中连续发生 61 次 `gmail_trash` ERROR 调用，原因是 AI 逐一尝试删除邮件，而每次调用都因 Gmail 工具在轮次之间被卸载而失败。

### 目标

1. 在每次 `SendMessageUseCase.execute()` 调用开始时恢复之前已加载的工具组
2. 使用现有消息历史作为持久化机制（无需新增数据库字段）
3. 零额外 API 调用轮次——在调用 LLM 之前预先加载工具组

### 非目标

- 跨会话的工具组持久化
- 工具组自动卸载
- `unload_tool_group` 命令
- 已加载工具组状态的 UI 展示

## 技术设计

### 方案：基于历史记录的恢复

扫描会话的现有消息历史，查找 `tool_name = "load_tool_group"` 且 `tool_status = "SUCCESS"` 的 `TOOL_CALL` 消息。从 `tool_input` JSON 中提取 `group_name`，并将这些工具组预加载到 `activeToolDefs` 中。

选择该方案的原因：
- **无 schema 变更** -- 使用 `TOOL_CALL` 消息上已有的 `tool_input` 字段
- **无新增持久化层** -- 消息历史已是事实来源
- **自动清理** -- `/clear` 创建无历史记录的新会话，工具组自然重置
- **幂等性** -- 扫描和加载操作可以安全地重复执行

### 变更文件

```
app/src/main/kotlin/com/oneclaw/shadow/feature/chat/usecase/
  └── SendMessageUseCase.kt          # 修改（添加历史扫描 + 恢复逻辑）
```

一个文件，一个新增方法，一处调用点变更。

## 详细设计

### 第一步：添加历史扫描方法

在 `SendMessageUseCase` 中添加一个私有方法，从会话消息历史中提取之前已加载的工具组名称：

```kotlin
/**
 * RFC-044: 扫描消息历史，查找之前成功执行的 load_tool_group 调用。
 * 返回在前几轮对话中已加载的工具组名称集合。
 */
private fun restoreLoadedGroups(messages: List<Message>): Set<String> {
    val groups = mutableSetOf<String>()
    for (msg in messages) {
        if (msg.type == MessageType.TOOL_CALL &&
            msg.toolName == "load_tool_group" &&
            msg.toolStatus == ToolCallStatus.SUCCESS &&
            msg.toolInput != null
        ) {
            try {
                val params = Json.parseToJsonElement(msg.toolInput)
                    .jsonObject
                val groupName = params["group_name"]?.jsonPrimitive?.content
                if (groupName != null) {
                    groups.add(groupName)
                }
            } catch (_: Exception) {
                // tool_input 格式错误 -- 跳过
            }
        }
    }
    return groups
}
```

### 第二步：在 execute() 开始时预加载工具组

在 `SendMessageUseCase.execute()` 中，使用核心工具初始化 `activeToolDefs` 之后，扫描历史记录并恢复之前已加载的工具组：

**当前代码（第 135-137 行）：**
```kotlin
// 5. 构建动态工具列表：仅从核心工具开始
val loadedGroupNames = mutableSetOf<String>()
val activeToolDefs = toolRegistry.getCoreToolDefinitions().toMutableList()
```

**新代码：**
```kotlin
// 5. 构建动态工具列表：从核心工具开始，并恢复之前已加载的工具组
val existingMessages = messageRepository.getMessagesSnapshot(sessionId)
val previouslyLoadedGroups = restoreLoadedGroups(existingMessages)
val loadedGroupNames = previouslyLoadedGroups.toMutableSet()
val activeToolDefs = toolRegistry.getCoreToolDefinitions().toMutableList()
for (groupName in previouslyLoadedGroups) {
    val groupDefs = toolRegistry.getGroupToolDefinitions(groupName)
    activeToolDefs.addAll(groupDefs)
}
```

### 数据流

```
用户发送消息 1：
  execute() 被调用
    activeToolDefs = 核心工具
    AI 调用 load_tool_group("google_gmail") --> 成功
    activeToolDefs += Gmail 工具
    AI 调用 gmail_search --> 成功
    消息保存：TOOL_CALL(load_tool_group, SUCCESS, {"group_name":"google_gmail"})

用户发送消息 2：
  execute() 被调用
    activeToolDefs = 核心工具
    restoreLoadedGroups(历史记录) --> {"google_gmail"}     <-- 新增
    activeToolDefs += Gmail 工具                           <-- 新增
    AI 调用 gmail_search --> 成功（无需重新加载）
```

### 边界情况

| 场景 | 行为 |
|------|------|
| 新会话（无历史记录） | 不恢复任何工具组，仅从核心工具开始 |
| `/clear` 命令 | 创建新会话，历史记录清空，不恢复任何工具组 |
| 历史记录中 `load_tool_group` 失败 | `tool_status = ERROR`，被扫描逻辑跳过 |
| 同一工具组多次加载 | `Set<String>` 自动去重，仅加载一次 |
| 工具组在注册表中不再存在 | `getGroupToolDefinitions()` 返回空列表，不产生副作用 |
| `tool_input` JSON 格式错误 | 由 try/catch 捕获，跳过 |

### 性能

历史扫描是轻量级操作：
- `getMessagesSnapshot()` 在 execute 循环后续步骤中（约第 191 行）已被调用，因此数据很可能已被 Room 缓存
- 扫描复杂度为 O(n)，仅过滤 `tool_name` 为 `load_tool_group` 的 `TOOL_CALL` 类型消息
- 典型会话消息数量不超过 100 条，扫描耗时不足 1ms
- 此方案替代了否则需要额外 API 调用轮次的方式（每次重新加载费用约为 $0.01-0.05）

## 测试

### 单元测试

添加测试以验证 `restoreLoadedGroups` 能够正确从消息历史中提取工具组名称：

```kotlin
@Test
fun `restoreLoadedGroups extracts group names from successful load_tool_group calls`() {
    val messages = listOf(
        Message(type = MessageType.USER, content = "check email", ...),
        Message(type = MessageType.TOOL_CALL, toolName = "load_tool_group",
                toolStatus = ToolCallStatus.SUCCESS,
                toolInput = """{"group_name": "google_gmail"}""", ...),
        Message(type = MessageType.TOOL_RESULT, toolName = "load_tool_group", ...),
        Message(type = MessageType.TOOL_CALL, toolName = "gmail_search",
                toolStatus = ToolCallStatus.SUCCESS, ...),
        Message(type = MessageType.TOOL_CALL, toolName = "load_tool_group",
                toolStatus = ToolCallStatus.ERROR,
                toolInput = """{"group_name": "google_drive"}""", ...),
    )

    val groups = restoreLoadedGroups(messages)

    assertEquals(setOf("google_gmail"), groups)
    // google_drive 因状态为 ERROR 而被排除
}
```

### 手动验证

1. 在第 1 轮加载 Gmail 工具，在第 2 轮使用 `gmail_search` 而不重新加载——应成功
2. 跨轮次加载多个工具组，验证所有工具组持续可用
3. 发送 `/clear`，验证下一轮从无预加载工具组的状态开始
4. 通过 Telegram bridge：加载 Gmail，发送后续消息——验证不出现"不可用"错误

## 迁移说明

- 无数据库 schema 变更
- 无新增依赖
- 完全向后兼容——包含 `load_tool_group` 历史记录的现有对话将自动受益
- `getMessagesSnapshot()` 调用时机略微前移至 `execute()` 中，位于对话循环开始之前
