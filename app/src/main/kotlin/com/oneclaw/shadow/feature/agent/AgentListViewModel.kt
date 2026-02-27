package com.oneclaw.shadow.feature.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oneclaw.shadow.core.repository.AgentRepository
import com.oneclaw.shadow.feature.agent.usecase.CloneAgentUseCase
import com.oneclaw.shadow.feature.agent.usecase.DeleteAgentUseCase
import com.oneclaw.shadow.tool.engine.ToolRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AgentListViewModel(
    private val agentRepository: AgentRepository,
    private val toolRegistry: ToolRegistry,
    private val cloneAgentUseCase: CloneAgentUseCase,
    private val deleteAgentUseCase: DeleteAgentUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgentListUiState())
    val uiState: StateFlow<AgentListUiState> = _uiState.asStateFlow()

    init {
        loadAgents()
    }

    private fun loadAgents() {
        viewModelScope.launch {
            agentRepository.getAllAgents().collect { agents ->
                val items = agents.map { agent ->
                    AgentListItem(
                        id = agent.id,
                        name = agent.name,
                        description = agent.description,
                        isBuiltIn = agent.isBuiltIn,
                        toolCount = agent.toolIds.size
                    )
                }
                _uiState.update { it.copy(agents = items, isLoading = false) }
            }
        }
    }

    fun cloneAgent(agentId: String) {
        viewModelScope.launch {
            cloneAgentUseCase(agentId)
            // List updates automatically via Flow
        }
    }

    fun deleteAgent(agentId: String) {
        viewModelScope.launch {
            deleteAgentUseCase(agentId)
        }
    }
}
