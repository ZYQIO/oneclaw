package com.oneclaw.shadow.feature.schedule

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oneclaw.shadow.core.model.AgentConstants
import com.oneclaw.shadow.core.model.ScheduleType
import com.oneclaw.shadow.core.model.ScheduledTask
import com.oneclaw.shadow.core.repository.AgentRepository
import com.oneclaw.shadow.core.repository.ScheduledTaskRepository
import com.oneclaw.shadow.core.util.AppResult
import com.oneclaw.shadow.feature.schedule.usecase.CreateScheduledTaskUseCase
import com.oneclaw.shadow.feature.schedule.usecase.UpdateScheduledTaskUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ScheduledTaskEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val agentRepository: AgentRepository,
    private val scheduledTaskRepository: ScheduledTaskRepository,
    private val createUseCase: CreateScheduledTaskUseCase,
    private val updateUseCase: UpdateScheduledTaskUseCase
) : ViewModel() {

    private val taskId: String? = savedStateHandle.get<String>("taskId")

    private val _uiState = MutableStateFlow(ScheduledTaskEditUiState())
    val uiState: StateFlow<ScheduledTaskEditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val agents = agentRepository.getAllAgents().first()
            _uiState.value = _uiState.value.copy(agents = agents)

            if (taskId != null) {
                val task = scheduledTaskRepository.getTaskById(taskId)
                if (task != null) {
                    _uiState.value = _uiState.value.copy(
                        name = task.name,
                        agentId = task.agentId,
                        prompt = task.prompt,
                        scheduleType = task.scheduleType,
                        hour = task.hour,
                        minute = task.minute,
                        dayOfWeek = task.dayOfWeek ?: 1,
                        dateMillis = task.dateMillis,
                        isEditing = true
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    agentId = agents.firstOrNull()?.id ?: AgentConstants.GENERAL_ASSISTANT_ID
                )
            }
        }
    }

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(name = name, errorMessage = null)
    }

    fun updateAgentId(agentId: String) {
        _uiState.value = _uiState.value.copy(agentId = agentId)
    }

    fun updatePrompt(prompt: String) {
        _uiState.value = _uiState.value.copy(prompt = prompt, errorMessage = null)
    }

    fun updateScheduleType(type: ScheduleType) {
        _uiState.value = _uiState.value.copy(scheduleType = type)
    }

    fun updateTime(hour: Int, minute: Int) {
        _uiState.value = _uiState.value.copy(hour = hour, minute = minute)
    }

    fun updateDayOfWeek(day: Int) {
        _uiState.value = _uiState.value.copy(dayOfWeek = day)
    }

    fun updateDateMillis(millis: Long?) {
        _uiState.value = _uiState.value.copy(dateMillis = millis)
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return

        _uiState.value = state.copy(isSaving = true, errorMessage = null)

        viewModelScope.launch {
            val task = ScheduledTask(
                id = taskId ?: "",
                name = state.name,
                agentId = state.agentId,
                prompt = state.prompt,
                scheduleType = state.scheduleType,
                hour = state.hour,
                minute = state.minute,
                dayOfWeek = if (state.scheduleType == ScheduleType.WEEKLY) state.dayOfWeek else null,
                dateMillis = if (state.scheduleType == ScheduleType.ONE_TIME) state.dateMillis else null,
                isEnabled = true,
                lastExecutionAt = null,
                lastExecutionStatus = null,
                lastExecutionSessionId = null,
                nextTriggerAt = null,
                createdAt = 0,
                updatedAt = 0
            )

            val result = if (state.isEditing) {
                updateUseCase(task)
            } else {
                createUseCase(task).map { }
            }

            when (result) {
                is AppResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        savedSuccessfully = true
                    )
                }
                is AppResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }
}
