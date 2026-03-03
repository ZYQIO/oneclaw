package com.oneclaw.shadow.feature.memory.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oneclaw.shadow.data.git.AppGitRepository
import com.oneclaw.shadow.data.git.GitCommitEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GitHistoryUiState(
    val commits: List<GitCommitEntry> = emptyList(),
    val isLoading: Boolean = false,
    val selectedFilter: GitHistoryFilter = GitHistoryFilter.ALL,
    val canLoadMore: Boolean = false,
    val selectedCommit: GitCommitEntry? = null,
    val commitDiff: String? = null,
    val isDiffLoading: Boolean = false
)

enum class GitHistoryFilter(val label: String, val path: String?) {
    ALL("All", null),
    MEMORY("Memory", "memory/MEMORY.md"),
    DAILY("Daily Logs", "memory/daily/"),
    FILES("Files", null)
}

class GitHistoryViewModel(
    private val appGitRepository: AppGitRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GitHistoryUiState())
    val uiState: StateFlow<GitHistoryUiState> = _uiState.asStateFlow()

    private var loadedCount = 0
    private val pageSize = 50

    init {
        loadCommits(reset = true)
    }

    fun setFilter(filter: GitHistoryFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
        loadCommits(reset = true)
    }

    fun loadMore() {
        loadCommits(reset = false)
    }

    fun selectCommit(commit: GitCommitEntry) {
        _uiState.update { it.copy(selectedCommit = commit, commitDiff = null, isDiffLoading = true) }
        viewModelScope.launch {
            val diff = appGitRepository.show(commit.sha)
            _uiState.update { it.copy(commitDiff = diff, isDiffLoading = false) }
        }
    }

    fun dismissDetail() {
        _uiState.update { it.copy(selectedCommit = null, commitDiff = null) }
    }

    private fun loadCommits(reset: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val filter = _uiState.value.selectedFilter
            val fetchCount = if (reset) pageSize else loadedCount + pageSize
            val all = appGitRepository.log(path = filter.path, maxCount = fetchCount)
            val filtered = if (filter == GitHistoryFilter.FILES) {
                all.filter { it.message.startsWith("file:") }
            } else all
            if (reset) loadedCount = 0
            loadedCount = filtered.size
            _uiState.update {
                it.copy(
                    commits = filtered,
                    isLoading = false,
                    canLoadMore = all.size == fetchCount
                )
            }
        }
    }
}
