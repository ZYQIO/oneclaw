# RFC-020: Agent Management Enhancement

## Document Information
- **RFC ID**: RFC-020
- **Related PRD**: [FEAT-020 (Agent Management Enhancement)](../../prd/features/FEAT-020-agent-enhancement.md)
- **Extends**: [RFC-002 (Agent Management)](RFC-002-agent-management.md)
- **Depends On**: [RFC-002 (Agent Management)](RFC-002-agent-management.md), [RFC-003 (Provider Management)](RFC-003-provider-management.md), [RFC-004 (Tool System)](RFC-004-tool-system.md)
- **Created**: 2026-02-28
- **Last Updated**: 2026-03-01
- **Status**: Draft
- **Author**: TBD

## Overview

### Background
The current Agent Management implementation (RFC-002) has three areas that need improvement:

1. The preferred model selector shows models as flat `TextButton` items which is not a standard dropdown UX and may not properly handle selection in all cases.
2. The Clone button is only shown for built-in agents; custom agents lack a clone action.
3. There is no way to create an agent from a natural language description -- users must manually fill in all fields.
4. There is no tool for creating agents during a chat conversation -- users must leave the chat and navigate to the Agent management screen.

### Goals
1. Replace the preferred model selector with a proper Material 3 `ExposedDropdownMenuBox`
2. Show Clone button on all saved agents (built-in and custom), Delete only on custom
3. Implement prompt-based agent creation using the configured AI model on the Agent screen
4. Implement a `create_agent` built-in tool so the AI can create agents during chat conversations

### Non-Goals
- Changing the agent data model or Room schema
- Adding tool auto-selection based on prompt
- Multi-turn agent creation conversation
- Prompt-based editing of existing agents
- Tools for deleting or editing agents from chat

## Technical Design

### Architecture Overview

Changes span two packages: `feature/agent/` (UI and ViewModel changes) and `tool/builtin/` (new `CreateAgentTool`). No data layer or core model changes are needed.

```
┌─────────────────────────────────────────────────┐
│                    UI Layer                       │
│                                                   │
│  AgentDetailScreen.kt                            │
│  ├── PreferredModelDropdown  (new composable)    │
│  ├── Clone/Delete buttons    (logic change)      │
│  └── PromptGenerateSection   (new composable)    │
│                                                   │
├─────────────────────────────────────────────────┤
│                 ViewModel Layer                    │
│                                                   │
│  AgentDetailViewModel.kt                         │
│  └── generateFromPrompt()    (new function)      │
│                                                   │
├─────────────────────────────────────────────────┤
│                 UseCase / Tool Layer              │
│                                                   │
│  GenerateAgentFromPromptUseCase.kt  (new)        │
│  CreateAgentTool.kt                 (new)        │
│                                                   │
├─────────────────────────────────────────────────┤
│                 Data Layer                        │
│                                                   │
│  ModelApiAdapter.sendMessage()  (existing)        │
│  ProviderRepository             (existing)        │
│  AgentRepository                (existing)        │
│  CreateAgentUseCase             (existing)        │
└─────────────────────────────────────────────────┘
```

### Core Components

#### 1. PreferredModelDropdown (replaces PreferredModelSelector)

**File**: `AgentDetailScreen.kt`

Replace the existing `PreferredModelSelector` composable with a Material 3 `ExposedDropdownMenuBox`:

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
            // Clear option
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
            // Group by provider
            availableModels
                .groupBy { it.providerName }
                .forEach { (providerName, models) ->
                    // Provider header
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
                    // Model items
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

#### 2. Clone & Delete Button Logic Change

**File**: `AgentDetailScreen.kt`

Change the button visibility logic from:

```kotlin
// Current (incorrect)
if (uiState.isBuiltIn) {
    TextButton(...) { Text("Clone Agent") }
}
if (!uiState.isBuiltIn && !uiState.isNewAgent) {
    TextButton(...) { Text("Delete Agent", color = error) }
}
```

To:

```kotlin
// Target (correct)
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

Summary of logic:
- Clone: shown for ALL saved agents (`!isNewAgent`), including built-in and custom
- Delete: shown only for saved custom agents (`!isBuiltIn && !isNewAgent`), unchanged

#### 3. GenerateAgentFromPromptUseCase (new)

**File**: `feature/agent/usecase/GenerateAgentFromPromptUseCase.kt`

```kotlin
class GenerateAgentFromPromptUseCase(
    private val providerRepository: ProviderRepository,
    private val modelApiAdapterFactory: ModelApiAdapterFactory
) {
    suspend operator fun invoke(userPrompt: String): AppResult<GeneratedAgent> {
        // 1. Resolve model: use global default
        val defaultModel = providerRepository.getGlobalDefaultModel()
            ?: return AppResult.Error("No default model configured")

        val provider = providerRepository.getProviderById(defaultModel.providerId)
            ?: return AppResult.Error("Provider not found")

        val apiKey = providerRepository.getApiKey(provider.id)
            ?: return AppResult.Error("API key not configured")

        // 2. Build generation prompt
        val systemPrompt = GENERATION_SYSTEM_PROMPT
        val userMessage = "Create an AI agent based on this description:\n\n$userPrompt"

        // 3. Call API
        val adapter = modelApiAdapterFactory.create(provider.type)
        val response = adapter.sendMessage(
            apiKey = apiKey,
            model = defaultModel.modelId,
            systemPrompt = systemPrompt,
            messages = listOf(ChatMessage(role = "user", content = userMessage))
        )

        // 4. Parse response
        return when (response) {
            is AppResult.Success -> parseGeneratedAgent(response.data)
            is AppResult.Error -> AppResult.Error(response.message)
        }
    }

    private fun parseGeneratedAgent(responseText: String): AppResult<GeneratedAgent> {
        return try {
            // Extract JSON from response (handle markdown code blocks)
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

#### 4. ViewModel Changes

**File**: `AgentDetailViewModel.kt`

Add:
- `generatePrompt` field to `AgentDetailUiState` for the prompt input
- `isGenerating` field to `AgentDetailUiState` for loading state
- `generateFromPrompt()` function
- `updateGeneratePrompt(text: String)` function

```kotlin
// New fields in AgentDetailUiState
data class AgentDetailUiState(
    // ... existing fields ...
    val generatePrompt: String = "",
    val isGenerating: Boolean = false,
)

// New function in AgentDetailViewModel
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

#### 5. PromptGenerateSection (new composable)

**File**: `AgentDetailScreen.kt`

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

This section is only shown when `uiState.isNewAgent` is true (Create Agent screen).

#### 6. CreateAgentTool (new built-in tool)

**File**: `tool/builtin/CreateAgentTool.kt`

A built-in Kotlin tool that allows the AI to create agents during chat conversations.

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
        // 1. Extract and validate parameters
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

        // 2. Create agent via existing use case
        val agent = Agent(
            id = "",  // auto-generated by repository
            name = name,
            description = description,
            systemPrompt = systemPrompt,
            preferredProviderId = null,
            preferredModelId = null,
            isBuiltIn = false,
            createdAt = 0,  // set by repository
            updatedAt = 0   // set by repository
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

### Data Model

No changes to the existing Agent data model, Room entity, or DAO. The `GeneratedAgent` and `GeneratedAgentJson` are transient data classes used only during the generation flow.

### API Design

#### New UseCase

```kotlin
class GenerateAgentFromPromptUseCase(
    private val providerRepository: ProviderRepository,
    private val modelApiAdapterFactory: ModelApiAdapterFactory
) {
    suspend operator fun invoke(userPrompt: String): AppResult<GeneratedAgent>
}
```

#### Modified ViewModel

```kotlin
class AgentDetailViewModel(
    // existing deps...
    private val generateAgentFromPromptUseCase: GenerateAgentFromPromptUseCase  // new
) {
    // existing functions...
    fun updateGeneratePrompt(text: String)  // new
    fun generateFromPrompt()                // new
}
```

### UI Layer Design

#### AgentDetailScreen Changes

The screen layout changes for Create Agent mode:

```
┌──────────────────────────────────┐
│ <- Create Agent           [Save] │  Top App Bar
├──────────────────────────────────┤
│ GENERATE FROM PROMPT             │  New section (create mode only)
│ ┌──────────────────────────────┐ │
│ │ Describe the agent...        │ │
│ │                              │ │
│ └──────────────────────────────┘ │
│                     [Generate]   │
├──────────────────────────────────┤
│ ┌──────────────────────────────┐ │  Existing form fields
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
│ PREFERRED MODEL (optional)       │  Dropdown instead of TextButtons
│ ┌──────────────────────────────┐ │
│ │ Using global default       v │ │
│ └──────────────────────────────┘ │
├──────────────────────────────────┤
│ [      Clone Agent             ] │  Shown if !isNewAgent
│ [      Delete Agent            ] │  Shown if !isBuiltIn && !isNewAgent
└──────────────────────────────────┘
```

For Edit / View mode, the "Generate from Prompt" section is hidden. Everything else remains the same.

### Dependency Injection

**File**: `di/FeatureModule.kt`

Add:
```kotlin
factory { GenerateAgentFromPromptUseCase(get(), get()) }

viewModel {
    AgentDetailViewModel(
        get(),  // agentRepository
        get(),  // providerRepository
        get(),  // createAgentUseCase
        get(),  // cloneAgentUseCase
        get(),  // deleteAgentUseCase
        get(),  // generateAgentFromPromptUseCase  <-- new
        get()   // savedStateHandle
    )
}
```

**File**: `di/ToolModule.kt`

Add:
```kotlin
single { CreateAgentTool(get()) }

single {
    ToolRegistry().apply {
        // ... existing registrations ...
        register(get<CreateAgentTool>(), ToolSourceInfo.BUILTIN)
    }
}
```

## Implementation Steps

### Phase 1: Model Selector Fix + Button Logic (small, no new code)
1. [ ] Replace `PreferredModelSelector` with `PreferredModelDropdown` in `AgentDetailScreen.kt`
2. [ ] Update Clone/Delete button visibility logic in `AgentDetailScreen.kt`
3. [ ] Verify CloneAgentUseCase works correctly for custom agents (it should -- no changes needed in use case)
4. [ ] Update unit tests for button visibility

### Phase 2: Prompt-based Agent Creation (UI)
1. [ ] Create `GenerateAgentFromPromptUseCase` in `feature/agent/usecase/`
2. [ ] Add `generatePrompt` and `isGenerating` fields to `AgentDetailUiState`
3. [ ] Add `updateGeneratePrompt()` and `generateFromPrompt()` to `AgentDetailViewModel`
4. [ ] Create `PromptGenerateSection` composable in `AgentDetailScreen.kt`
5. [ ] Integrate `PromptGenerateSection` into the Create Agent screen layout
6. [ ] Register `GenerateAgentFromPromptUseCase` in `FeatureModule.kt`
7. [ ] Update `AgentDetailViewModel` DI registration with new dependency
8. [ ] Add unit tests for `GenerateAgentFromPromptUseCase`
9. [ ] Add unit tests for ViewModel generation flow

### Phase 3: `create_agent` Tool (Chat)
1. [ ] Create `CreateAgentTool` in `tool/builtin/CreateAgentTool.kt`
2. [ ] Register `CreateAgentTool` in `ToolModule.kt` with `ToolSourceInfo.BUILTIN`
3. [ ] Add unit tests for `CreateAgentTool` (valid params, missing params, creation failure)
4. [ ] Integration test: verify tool appears in `ToolRegistry.getAllToolDefinitions()`
5. [ ] Manual test: chat with AI and ask it to create an agent, verify agent appears in list

## Testing Strategy

### Unit Tests
- `GenerateAgentFromPromptUseCase`: test successful generation, API failure, malformed JSON
- `AgentDetailViewModel`: test generate flow, button visibility state for all agent types
- `PreferredModelDropdown`: verify selection callbacks
- `CreateAgentTool`: test execute with valid parameters, missing name, missing system_prompt, name too long, system_prompt too long, creation failure from repository

### Integration Tests
- Create agent via prompt end-to-end (requires mock API adapter)
- Clone a custom agent and verify the clone
- `CreateAgentTool` registered in `ToolRegistry` and accessible via `ToolExecutionEngine`

### Manual Tests
- Verify model dropdown opens, scrolls, and selects correctly
- Verify Clone button appears on custom agents
- Verify Delete button does NOT appear on built-in agents
- Test prompt generation with real API key
- Test prompt generation with no configured providers
- Chat with AI: "Create me a Python debugging assistant agent" -- verify agent is created and appears in Agent list
- Chat with AI: ask to create an agent with specific requirements -- verify the generated system prompt matches the request

## Change History

| Date | Version | Changes | Owner |
|------|---------|---------|-------|
| 2026-02-28 | 0.1 | Initial version | - |
| 2026-03-01 | 0.2 | Added `create_agent` built-in tool (Phase 3) | - |
