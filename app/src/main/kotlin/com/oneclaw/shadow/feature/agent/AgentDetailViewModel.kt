package com.oneclaw.shadow.feature.agent

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oneclaw.shadow.core.model.Agent
import com.oneclaw.shadow.core.model.AgentConstants
import com.oneclaw.shadow.core.repository.AgentRepository
import com.oneclaw.shadow.core.repository.ProviderRepository
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.feature.agent.usecase.CloneAgentUseCase
import com.oneclaw.shadow.feature.agent.usecase.CreateAgentUseCase
import com.oneclaw.shadow.feature.agent.usecase.DeleteAgentUseCase
import com.oneclaw.shadow.feature.agent.usecase.GenerateAgentFromPromptUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AgentDetailViewModel(
    private val agentRepository: AgentRepository,
    private val providerRepository: ProviderRepository,
    private val createAgentUseCase: CreateAgentUseCase,
    private val cloneAgentUseCase: CloneAgentUseCase,
    private val deleteAgentUseCase: DeleteAgentUseCase,
    private val generateAgentFromPromptUseCase: GenerateAgentFromPromptUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val agentId: String? = savedStateHandle["agentId"]
    private val isCreateMode = agentId == null

    private val _uiState = MutableStateFlow(AgentDetailUiState(isNewAgent = isCreateMode))
    val uiState: StateFlow<AgentDetailUiState> = _uiState.asStateFlow()

    private var originalAgent: Agent? = null

    init {
        loadAvailableModels()
        if (!isCreateMode) {
            loadAgent(agentId!!)
        } else {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun loadAgent(id: String) {
        viewModelScope.launch {
            val agent = agentRepository.getAgentById(id)
            if (agent == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Agent not found.") }
                return@launch
            }
            originalAgent = agent
            _uiState.update {
                it.copy(
                    agentId = agent.id,
                    isBuiltIn = agent.isBuiltIn,
                    name = agent.name,
                    description = agent.description ?: "",
                    systemPrompt = agent.systemPrompt,
                    preferredProviderId = agent.preferredProviderId,
                    preferredModelId = agent.preferredModelId,
                    webSearchEnabled = agent.webSearchEnabled,
                    temperature = agent.temperature,
                    maxIterations = agent.maxIterations,
                    savedName = agent.name,
                    savedDescription = agent.description ?: "",
                    savedSystemPrompt = agent.systemPrompt,
                    savedPreferredProviderId = agent.preferredProviderId,
                    savedPreferredModelId = agent.preferredModelId,
                    savedWebSearchEnabled = agent.webSearchEnabled,
                    savedTemperature = agent.temperature,
                    savedMaxIterations = agent.maxIterations,
                    isLoading = false
                )
            }
        }
    }

    private fun loadAvailableModels() {
        viewModelScope.launch {
            providerRepository.getActiveProviders().collect { providers ->
                val modelOptions = mutableListOf<ModelOptionItem>()
                for (provider in providers) {
                    val models = providerRepository.getModelsForProvider(provider.id)
                    for (model in models) {
                        modelOptions.add(
                            ModelOptionItem(
                                modelId = model.id,
                                modelDisplayName = model.displayName,
                                providerId = provider.id,
                                providerName = provider.name
                            )
                        )
                    }
                }
                _uiState.update { it.copy(availableModels = modelOptions) }
            }
        }
    }

    fun updateName(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun updateDescription(description: String) {
        _uiState.update { it.copy(description = description) }
    }

    fun updateSystemPrompt(prompt: String) {
        _uiState.update { it.copy(systemPrompt = prompt) }
    }

    fun setPreferredModel(providerId: String?, modelId: String?) {
        _uiState.update {
            it.copy(preferredProviderId = providerId, preferredModelId = modelId)
        }
    }

    fun clearPreferredModel() = setPreferredModel(null, null)

    fun updateWebSearchEnabled(enabled: Boolean) {
        _uiState.update { it.copy(webSearchEnabled = enabled) }
    }

    fun updateTemperature(value: Float?) {
        val error = if (value != null && (value < 0f || value > 2f)) {
            "Temperature must be between 0.0 and 2.0"
        } else null
        _uiState.update { it.copy(temperature = value, temperatureError = error) }
    }

    fun updateMaxIterations(value: Int?) {
        _uiState.update { it.copy(maxIterations = value) }
    }

    fun saveAgent() {
        val state = _uiState.value
        if (state.temperatureError != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            if (isCreateMode) {
                val result = createAgentUseCase(
                    name = state.name,
                    description = state.description.ifBlank { null },
                    systemPrompt = state.systemPrompt,
                    preferredProviderId = state.preferredProviderId,
                    preferredModelId = state.preferredModelId,
                    webSearchEnabled = state.webSearchEnabled,
                    temperature = state.temperature,
                    maxIterations = state.maxIterations
                )
                when (result) {
                    is AppResult.Success -> _uiState.update {
                        it.copy(isSaving = false, successMessage = "Agent created.", navigateBack = true)
                    }
                    is AppResult.Error -> _uiState.update {
                        it.copy(isSaving = false, errorMessage = result.message)
                    }
                }
            } else {
                val orig = originalAgent ?: return@launch
                val updated = Agent(
                    id = state.agentId!!,
                    name = state.name.trim(),
                    description = state.description.trim().ifBlank { null },
                    systemPrompt = state.systemPrompt.trim(),
                    preferredProviderId = state.preferredProviderId,
                    preferredModelId = state.preferredModelId,
                    temperature = state.temperature,
                    maxIterations = state.maxIterations,
                    isBuiltIn = orig.isBuiltIn,
                    createdAt = orig.createdAt,
                    updatedAt = 0,
                    webSearchEnabled = state.webSearchEnabled
                )
                when (val result = agentRepository.updateAgent(updated)) {
                    is AppResult.Success -> {
                        originalAgent = updated
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                savedName = updated.name,
                                savedDescription = updated.description ?: "",
                                savedSystemPrompt = updated.systemPrompt,
                                savedPreferredProviderId = updated.preferredProviderId,
                                savedPreferredModelId = updated.preferredModelId,
                                savedWebSearchEnabled = updated.webSearchEnabled,
                                savedTemperature = updated.temperature,
                                savedMaxIterations = updated.maxIterations,
                                successMessage = "Agent saved."
                            )
                        }
                    }
                    is AppResult.Error -> _uiState.update {
                        it.copy(isSaving = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun cloneAgent() {
        val id = _uiState.value.agentId ?: return
        viewModelScope.launch {
            when (val result = cloneAgentUseCase(id)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(successMessage = "Agent cloned.", navigateBack = true)
                }
                is AppResult.Error -> _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    fun showDeleteConfirmation() { _uiState.update { it.copy(showDeleteDialog = true) } }
    fun dismissDeleteConfirmation() { _uiState.update { it.copy(showDeleteDialog = false) } }

    fun deleteAgent() {
        val id = _uiState.value.agentId ?: return
        viewModelScope.launch {
            when (val result = deleteAgentUseCase(id)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(showDeleteDialog = false, successMessage = "Agent deleted.", navigateBack = true)
                }
                is AppResult.Error -> _uiState.update {
                    it.copy(showDeleteDialog = false, errorMessage = result.message)
                }
            }
        }
    }

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

    fun showResetConfirmation() { _uiState.update { it.copy(showResetDialog = true) } }
    fun dismissResetConfirmation() { _uiState.update { it.copy(showResetDialog = false) } }

    fun resetAgent() {
        val orig = originalAgent ?: return
        val reset = orig.copy(
            name = AgentConstants.GENERAL_ASSISTANT_NAME,
            description = AgentConstants.GENERAL_ASSISTANT_DESCRIPTION,
            systemPrompt = AgentConstants.GENERAL_ASSISTANT_SYSTEM_PROMPT,
            preferredProviderId = null,
            preferredModelId = null,
            temperature = AgentConstants.GENERAL_ASSISTANT_DEFAULT_TEMPERATURE,
            maxIterations = AgentConstants.GENERAL_ASSISTANT_DEFAULT_MAX_ITERATIONS,
            webSearchEnabled = AgentConstants.GENERAL_ASSISTANT_DEFAULT_WEB_SEARCH
        )
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            when (val result = agentRepository.updateAgent(reset)) {
                is AppResult.Success -> {
                    originalAgent = reset
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            showResetDialog = false,
                            name = reset.name,
                            description = reset.description ?: "",
                            systemPrompt = reset.systemPrompt,
                            preferredProviderId = reset.preferredProviderId,
                            preferredModelId = reset.preferredModelId,
                            webSearchEnabled = reset.webSearchEnabled,
                            temperature = reset.temperature,
                            maxIterations = reset.maxIterations,
                            savedName = reset.name,
                            savedDescription = reset.description ?: "",
                            savedSystemPrompt = reset.systemPrompt,
                            savedPreferredProviderId = reset.preferredProviderId,
                            savedPreferredModelId = reset.preferredModelId,
                            savedWebSearchEnabled = reset.webSearchEnabled,
                            savedTemperature = reset.temperature,
                            savedMaxIterations = reset.maxIterations,
                            successMessage = "Agent reset to defaults."
                        )
                    }
                }
                is AppResult.Error -> _uiState.update {
                    it.copy(isSaving = false, showResetDialog = false, errorMessage = result.message)
                }
            }
        }
    }

    fun clearError() { _uiState.update { it.copy(errorMessage = null) } }
    fun clearSuccess() { _uiState.update { it.copy(successMessage = null) } }
    fun onNavigatedBack() { _uiState.update { it.copy(navigateBack = false) } }
}
