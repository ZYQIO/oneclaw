package com.oneclaw.shadow.feature.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oneclaw.shadow.core.repository.ScheduledTaskRepository
import com.oneclaw.shadow.feature.schedule.usecase.DeleteScheduledTaskUseCase
import com.oneclaw.shadow.feature.schedule.usecase.ToggleScheduledTaskUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScheduledTaskListViewModel(
    private val repository: ScheduledTaskRepository,
    private val toggleUseCase: ToggleScheduledTaskUseCase,
    private val deleteUseCase: DeleteScheduledTaskUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduledTaskListUiState())
    val uiState: StateFlow<ScheduledTaskListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllTasks().collect { tasks ->
                _uiState.value = ScheduledTaskListUiState(
                    tasks = tasks,
                    isLoading = false
                )
            }
        }
    }

    fun toggleTask(taskId: String, enabled: Boolean) {
        viewModelScope.launch {
            toggleUseCase(taskId, enabled)
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            deleteUseCase(taskId)
        }
    }
}
