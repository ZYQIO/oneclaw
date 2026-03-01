# RFC-020: Agent 管理增强

## 文档信息
- **RFC ID**: RFC-020
- **关联 PRD**: [FEAT-020 (Agent 管理增强)](../../prd/features/FEAT-020-agent-enhancement.md)
- **扩展自**: [RFC-002 (Agent 管理)](RFC-002-agent-management.md)
- **依赖**: [RFC-002 (Agent 管理)](RFC-002-agent-management.md), [RFC-003 (Provider 管理)](RFC-003-provider-management.md), [RFC-004 (Tool 系统)](RFC-004-tool-system.md)
- **创建时间**: 2026-02-28
- **最后更新**: 2026-03-01
- **状态**: Draft
- **作者**: TBD

## 概述

### 背景
当前 Agent 管理实现（RFC-002）有四个需要改进的方面：

1. 首选模型选择器以扁平的 `TextButton` 列表形式展示模型，不符合标准下拉菜单的交互规范，且在某些情况下可能无法正确处理选择操作。
2. Clone 按钮仅对内置 Agent 显示，自定义 Agent 缺少克隆功能。
3. 没有办法通过自然语言描述来创建 Agent，用户必须手动填写所有字段。
4. 在聊天对话中没有可以创建 Agent 的 Tool，用户必须离开聊天界面，导航到 Agent 管理界面才能完成创建。

### 目标
1. 将首选模型选择器替换为符合 Material 3 规范的 `ExposedDropdownMenuBox`
2. 对所有已保存的 Agent（包括内置和自定义）显示 Clone 按钮，Delete 按钮仅对自定义 Agent 显示
3. 使用已配置的 AI 模型，在 Agent 界面实现基于提示词的 Agent 创建功能
4. 实现 `create_agent` 内置 Tool，让 AI 在聊天对话中即可创建 Agent

### 非目标
- 更改 Agent 数据模型或 Room Schema
- 基于提示词自动选择工具
- 多轮 Agent 创建对话
- 基于提示词编辑现有 Agent
- 在聊天中通过 Tool 删除或编辑 Agent

## 技术设计

### 架构概览

改动涉及两个包：`feature/agent/`（UI 和 ViewModel 变更）以及 `tool/builtin/`（新增 `CreateAgentTool`）。无需修改数据层或核心模型。

```
┌─────────────────────────────────────────────────┐
│                    UI Layer                       │
│                                                   │
│  AgentDetailScreen.kt                            │
│  ├── PreferredModelDropdown  (新 Composable)     │
│  ├── Clone/Delete buttons    (逻辑变更)          │
│  └── PromptGenerateSection   (新 Composable)     │
│                                                   │
├─────────────────────────────────────────────────┤
│                 ViewModel Layer                    │
│                                                   │
│  AgentDetailViewModel.kt                         │
│  └── generateFromPrompt()    (新函数)            │
│                                                   │
├─────────────────────────────────────────────────┤
│                 UseCase / Tool Layer              │
│                                                   │
│  GenerateAgentFromPromptUseCase.kt  (新)         │
│  CreateAgentTool.kt                 (新)         │
│                                                   │
├─────────────────────────────────────────────────┤
│                 Data Layer                        │
│                                                   │
│  ModelApiAdapter.sendMessage()  (已有)           │
│  ProviderRepository             (已有)           │
│  AgentRepository                (已有)           │
│  CreateAgentUseCase             (已有)           │
└─────────────────────────────────────────────────┘
```

### 核心组件

#### 1. PreferredModelDropdown（替换 PreferredModelSelector）

**文件**: `AgentDetailScreen.kt`

将现有的 `PreferredModelSelector` Composable 替换为 Material 3 `ExposedDropdownMenuBox`：

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreferredModelDropdown(
    currentProviderId: String?,
    currentModelId: String?,
    availableModels: List<ModelOptionItem>,
    onSelect: (String, String) -> Unit,
    onClear: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val currentModel = availableModels.find {
        it.providerId == currentProviderId && it.modelId == currentModelId
    }
    val displayText = if (currentModel != null) {
        "${currentModel.providerName} / ${currentModel.modelDisplayName ?: currentModel.modelId}"
    } else {
        "Using global default"
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // 清除选项
            if (currentModel != null) {
                DropdownMenuItem(
                    text = { Text("Clear (use global default)") },
                    onClick = {
                        onClear()
                        expanded = false
                    }
                )
                HorizontalDivider()
            }
            // 按 Provider 分组
            availableModels
                .groupBy { it.providerName }
                .forEach { (providerName, models) ->
                    // Provider 标题
                    DropdownMenuItem(
                        text = {
                            Text(
                                providerName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = {},
                        enabled = false
                    )
                    // 模型列表
                    models.forEach { model ->
                        DropdownMenuItem(
                            text = {
                                Text(model.modelDisplayName ?: model.modelId)
                            },
                            onClick = {
                                onSelect(model.providerId, model.modelId)
                                expanded = false
                            },
                            trailingIcon = {
                                if (model.providerId == currentProviderId &&
                                    model.modelId == currentModelId) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected")
                                }
                            }
                        )
                    }
                }
        }
    }
}
```

#### 2. Clone 与 Delete 按钮逻辑变更

**文件**: `AgentDetailScreen.kt`

将按钮显示逻辑从：

```kotlin
// 当前（不正确）
if (uiState.isBuiltIn) {
    TextButton(...) { Text("Clone Agent") }
}
if (!uiState.isBuiltIn && !uiState.isNewAgent) {
    TextButton(...) { Text("Delete Agent", color = error) }
}
```

修改为：

```kotlin
// 目标（正确）
if (!uiState.isNewAgent) {
    TextButton(
        onClick = { viewModel.cloneAgent() },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Clone Agent")
    }
}
if (!uiState.isBuiltIn && !uiState.isNewAgent) {
    TextButton(
        onClick = { viewModel.showDeleteConfirmation() },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Delete Agent", color = MaterialTheme.colorScheme.error)
    }
}
```

逻辑说明：
- Clone：对所有已保存的 Agent 显示（`!isNewAgent`），包括内置和自定义
- Delete：仅对已保存的自定义 Agent 显示（`!isBuiltIn && !isNewAgent`），逻辑不变

#### 3. GenerateAgentFromPromptUseCase（新增）

**文件**: `feature/agent/usecase/GenerateAgentFromPromptUseCase.kt`

```kotlin
class GenerateAgentFromPromptUseCase(
    private val providerRepository: ProviderRepository,
    private val modelApiAdapterFactory: ModelApiAdapterFactory
) {
    suspend operator fun invoke(userPrompt: String): AppResult<GeneratedAgent> {
        // 1. 获取模型：使用全局默认模型
        val defaultModel = providerRepository.getGlobalDefaultModel()
            ?: return AppResult.Error("No default model configured")

        val provider = providerRepository.getProviderById(defaultModel.providerId)
            ?: return AppResult.Error("Provider not found")

        val apiKey = providerRepository.getApiKey(provider.id)
            ?: return AppResult.Error("API key not configured")

        // 2. 构建生成提示词
        val systemPrompt = GENERATION_SYSTEM_PROMPT
        val userMessage = "Create an AI agent based on this description:\n\n$userPrompt"

        // 3. 调用 API
        val adapter = modelApiAdapterFactory.create(provider.type)
        val response = adapter.sendMessage(
            apiKey = apiKey,
            model = defaultModel.modelId,
            systemPrompt = systemPrompt,
            messages = listOf(ChatMessage(role = "user", content = userMessage))
        )

        // 4. 解析响应
        return when (response) {
            is AppResult.Success -> parseGeneratedAgent(response.data)
            is AppResult.Error -> AppResult.Error(response.message)
        }
    }

    private fun parseGeneratedAgent(responseText: String): AppResult<GeneratedAgent> {
        return try {
            // 从响应中提取 JSON（处理 Markdown 代码块）
            val jsonStr = responseText
                .substringAfter("```json", responseText)
                .substringAfter("```", responseText)
                .substringBefore("```")
                .trim()
                .ifEmpty { responseText.trim() }

            val json = Json.decodeFromString<GeneratedAgentJson>(jsonStr)
            AppResult.Success(
                GeneratedAgent(
                    name = json.name,
                    description = json.description,
                    systemPrompt = json.systemPrompt
                )
            )
        } catch (e: Exception) {
            AppResult.Error("Failed to parse generated agent: ${e.message}")
        }
    }

    companion object {
        private val GENERATION_SYSTEM_PROMPT = """
            You are an AI agent configuration generator. Given a user's description of what kind
            of AI agent they want, generate a complete agent configuration.

            Respond with ONLY a JSON object (no markdown, no explanation) with these fields:
            - "name": A concise agent name (2-5 words)
            - "description": A one-sentence description of what this agent does
            - "systemPrompt": A detailed system prompt (200-500 words) that instructs the AI
              to behave as described. The system prompt should be specific, actionable, and
              include guidelines for tone, expertise areas, and behavior boundaries.

            Example response:
            {"name": "Python Debug Helper", "description": "Helps debug Python code and suggests fixes", "systemPrompt": "You are an expert Python developer..."}
        """.trimIndent()
    }
}

data class GeneratedAgent(
    val name: String,
    val description: String,
    val systemPrompt: String
)

@Serializable
private data class GeneratedAgentJson(
    val name: String,
    val description: String,
    val systemPrompt: String
)
```

#### 4. ViewModel 变更

**文件**: `AgentDetailViewModel.kt`

新增内容：
- `AgentDetailUiState` 中的 `generatePrompt` 字段，用于保存提示词输入
- `AgentDetailUiState` 中的 `isGenerating` 字段，用于表示加载状态
- `generateFromPrompt()` 函数
- `updateGeneratePrompt(text: String)` 函数

```kotlin
// AgentDetailUiState 中新增字段
data class AgentDetailUiState(
    // ... 已有字段 ...
    val generatePrompt: String = "",
    val isGenerating: Boolean = false,
)

// AgentDetailViewModel 中新增函数
fun updateGeneratePrompt(text: String) {
    _uiState.update { it.copy(generatePrompt = text) }
}

fun generateFromPrompt() {
    val prompt = _uiState.value.generatePrompt.trim()
    if (prompt.isEmpty()) return

    viewModelScope.launch {
        _uiState.update { it.copy(isGenerating = true) }
        when (val result = generateAgentFromPromptUseCase(prompt)) {
            is AppResult.Success -> {
                val generated = result.data
                _uiState.update {
                    it.copy(
                        name = generated.name,
                        description = generated.description,
                        systemPrompt = generated.systemPrompt,
                        isGenerating = false,
                        successMessage = "Agent generated! Review and save."
                    )
                }
            }
            is AppResult.Error -> {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }
}
```

#### 5. PromptGenerateSection（新 Composable）

**文件**: `AgentDetailScreen.kt`

```kotlin
@Composable
private fun PromptGenerateSection(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onGenerate: () -> Unit,
    isGenerating: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "GENERATE FROM PROMPT",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChange,
            label = { Text("Describe the agent you want to create...") },
            readOnly = isGenerating,
            minLines = 3,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onGenerate,
            enabled = prompt.isNotBlank() && !isGenerating,
            modifier = Modifier.align(Alignment.End)
        ) {
            if (isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("Generate")
        }
    }
}
```

该部分仅在 `uiState.isNewAgent` 为 true 时显示（即"创建 Agent"界面）。

#### 6. CreateAgentTool（新内置 Tool）

**文件**: `tool/builtin/CreateAgentTool.kt`

一个内置 Kotlin Tool，允许 AI 在聊天对话中创建 Agent。

```kotlin
class CreateAgentTool(
    private val createAgentUseCase: CreateAgentUseCase
) : Tool {

    override val definition = ToolDefinition(
        name = "create_agent",
        description = "Create a new custom AI agent with a name, description, and system prompt. " +
            "Use this tool when the user asks you to create or set up a new agent during a conversation.",
        parametersSchema = ToolParametersSchema(
            properties = mapOf(
                "name" to ToolParameter(
                    type = "string",
                    description = "The agent's display name (e.g., 'Python Debug Helper'). Max 100 characters."
                ),
                "description" to ToolParameter(
                    type = "string",
                    description = "A short description of what this agent does (optional)."
                ),
                "system_prompt" to ToolParameter(
                    type = "string",
                    description = "The system prompt that defines the agent's behavior, expertise, and tone. " +
                        "Should be detailed and specific (200-500 words recommended). Max 50,000 characters."
                )
            ),
            required = listOf("name", "system_prompt")
        ),
        requiredPermissions = emptyList(),
        timeoutSeconds = 10
    )

    override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
        // 1. 提取并校验参数
        val name = (parameters["name"] as? String)?.trim()
        if (name.isNullOrEmpty()) {
            return ToolResult.error(
                "validation_error",
                "Parameter 'name' is required and must be non-empty."
            )
        }
        if (name.length > 100) {
            return ToolResult.error(
                "validation_error",
                "Parameter 'name' must be 100 characters or less."
            )
        }

        val systemPrompt = (parameters["system_prompt"] as? String)?.trim()
        if (systemPrompt.isNullOrEmpty()) {
            return ToolResult.error(
                "validation_error",
                "Parameter 'system_prompt' is required and must be non-empty."
            )
        }
        if (systemPrompt.length > 50_000) {
            return ToolResult.error(
                "validation_error",
                "Parameter 'system_prompt' must be 50,000 characters or less."
            )
        }

        val description = (parameters["description"] as? String)?.trim() ?: ""

        // 2. 通过已有 UseCase 创建 Agent
        val agent = Agent(
            id = "",  // 由 Repository 自动生成
            name = name,
            description = description,
            systemPrompt = systemPrompt,
            preferredProviderId = null,
            preferredModelId = null,
            isBuiltIn = false,
            createdAt = 0,  // 由 Repository 设置
            updatedAt = 0   // 由 Repository 设置
        )

        return when (val result = createAgentUseCase(agent)) {
            is AppResult.Success -> {
                val created = result.data
                ToolResult.success(
                    "Agent '${created.name}' created successfully (ID: ${created.id}). " +
                    "The user can find it in the Agent list and switch to it from the chat screen."
                )
            }
            is AppResult.Error -> {
                ToolResult.error("creation_failed", "Failed to create agent: ${result.message}")
            }
        }
    }
}
```

### 数据模型

无需修改现有的 Agent 数据模型、Room Entity 或 DAO。`GeneratedAgent` 和 `GeneratedAgentJson` 是仅在生成流程中使用的临时数据类。

### API 设计

#### 新增 UseCase

```kotlin
class GenerateAgentFromPromptUseCase(
    private val providerRepository: ProviderRepository,
    private val modelApiAdapterFactory: ModelApiAdapterFactory
) {
    suspend operator fun invoke(userPrompt: String): AppResult<GeneratedAgent>
}
```

#### 修改 ViewModel

```kotlin
class AgentDetailViewModel(
    // 已有依赖...
    private val generateAgentFromPromptUseCase: GenerateAgentFromPromptUseCase  // 新增
) {
    // 已有函数...
    fun updateGeneratePrompt(text: String)  // 新增
    fun generateFromPrompt()                // 新增
}
```

### UI 层设计

#### AgentDetailScreen 变更

创建 Agent 模式下的界面布局变更：

```
┌──────────────────────────────────┐
│ <- Create Agent           [Save] │  顶部应用栏
├──────────────────────────────────┤
│ GENERATE FROM PROMPT             │  新增区块（仅创建模式显示）
│ ┌──────────────────────────────┐ │
│ │ Describe the agent...        │ │
│ │                              │ │
│ └──────────────────────────────┘ │
│                     [Generate]   │
├──────────────────────────────────┤
│ ┌──────────────────────────────┐ │  已有表单字段
│ │ Name                         │ │
│ └──────────────────────────────┘ │
│ ┌──────────────────────────────┐ │
│ │ Description (optional)       │ │
│ └──────────────────────────────┘ │
│ ┌──────────────────────────────┐ │
│ │ System Prompt *              │ │
│ │                              │ │
│ │                              │ │
│ └──────────────────────────────┘ │
├──────────────────────────────────┤
│ PREFERRED MODEL (optional)       │  下拉菜单替换 TextButton 列表
│ ┌──────────────────────────────┐ │
│ │ Using global default       v │ │
│ └──────────────────────────────┘ │
├──────────────────────────────────┤
│ [      Clone Agent             ] │  !isNewAgent 时显示
│ [      Delete Agent            ] │  !isBuiltIn && !isNewAgent 时显示
└──────────────────────────────────┘
```

编辑 / 查看模式下，"Generate from Prompt"区块隐藏，其余内容保持不变。

### 依赖注入

**文件**: `di/FeatureModule.kt`

新增：
```kotlin
factory { GenerateAgentFromPromptUseCase(get(), get()) }

viewModel {
    AgentDetailViewModel(
        get(),  // agentRepository
        get(),  // providerRepository
        get(),  // createAgentUseCase
        get(),  // cloneAgentUseCase
        get(),  // deleteAgentUseCase
        get(),  // generateAgentFromPromptUseCase  <-- 新增
        get()   // savedStateHandle
    )
}
```

**文件**: `di/ToolModule.kt`

新增：
```kotlin
single { CreateAgentTool(get()) }

single {
    ToolRegistry().apply {
        // ... 已有注册项 ...
        register(get<CreateAgentTool>(), ToolSourceInfo.BUILTIN)
    }
}
```

## 实现步骤

### 阶段一：模型选择器修复 + 按钮逻辑（改动较小，无需新增代码文件）
1. [ ] 在 `AgentDetailScreen.kt` 中将 `PreferredModelSelector` 替换为 `PreferredModelDropdown`
2. [ ] 更新 `AgentDetailScreen.kt` 中 Clone/Delete 按钮的显示逻辑
3. [ ] 验证 CloneAgentUseCase 对自定义 Agent 正常工作（应无需修改 UseCase）
4. [ ] 更新按钮显示逻辑的单元测试

### 阶段二：基于提示词的 Agent 创建（UI）
1. [ ] 在 `feature/agent/usecase/` 中创建 `GenerateAgentFromPromptUseCase`
2. [ ] 在 `AgentDetailUiState` 中新增 `generatePrompt` 和 `isGenerating` 字段
3. [ ] 在 `AgentDetailViewModel` 中新增 `updateGeneratePrompt()` 和 `generateFromPrompt()`
4. [ ] 在 `AgentDetailScreen.kt` 中创建 `PromptGenerateSection` Composable
5. [ ] 将 `PromptGenerateSection` 集成到创建 Agent 界面布局中
6. [ ] 在 `FeatureModule.kt` 中注册 `GenerateAgentFromPromptUseCase`
7. [ ] 更新 `AgentDetailViewModel` 的 DI 注册，添加新依赖
8. [ ] 为 `GenerateAgentFromPromptUseCase` 添加单元测试
9. [ ] 为 ViewModel 生成流程添加单元测试

### 阶段三：`create_agent` Tool（聊天）
1. [ ] 在 `tool/builtin/CreateAgentTool.kt` 中创建 `CreateAgentTool`
2. [ ] 在 `ToolModule.kt` 中以 `ToolSourceInfo.BUILTIN` 注册 `CreateAgentTool`
3. [ ] 为 `CreateAgentTool` 添加单元测试（有效参数、缺少参数、创建失败等场景）
4. [ ] 集成测试：验证 Tool 出现在 `ToolRegistry.getAllToolDefinitions()` 中
5. [ ] 手动测试：在聊天中要求 AI 创建一个 Agent，验证 Agent 出现在列表中

## 测试策略

### 单元测试
- `GenerateAgentFromPromptUseCase`：测试成功生成、API 失败、JSON 格式错误等场景
- `AgentDetailViewModel`：测试生成流程、各类型 Agent 的按钮显示状态
- `PreferredModelDropdown`：验证选择回调
- `CreateAgentTool`：测试使用有效参数执行、缺少 name、缺少 system_prompt、name 过长、system_prompt 过长、Repository 创建失败等场景

### 集成测试
- 通过提示词端到端创建 Agent（需要 Mock API Adapter）
- Clone 自定义 Agent 并验证克隆结果
- `CreateAgentTool` 已注册到 `ToolRegistry` 并可通过 `ToolExecutionEngine` 访问

### 手动测试
- 验证模型下拉菜单可正常展开、滚动和选择
- 验证 Clone 按钮在自定义 Agent 上正常显示
- 验证 Delete 按钮不在内置 Agent 上显示
- 使用真实 API Key 测试提示词生成功能
- 在未配置 Provider 的情况下测试提示词生成功能
- 在聊天中发送："Create me a Python debugging assistant agent"，验证 Agent 已创建并出现在 Agent 列表中
- 在聊天中要求 AI 按具体需求创建 Agent，验证生成的 System Prompt 与请求相符

## 变更历史

| 日期 | 版本 | 变更内容 | 负责人 |
|------|------|---------|--------|
| 2026-02-28 | 0.1 | 初始版本 | - |
| 2026-03-01 | 0.2 | 新增 `create_agent` 内置 Tool（阶段三） | - |
