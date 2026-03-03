# RFC-051: Git History Browser UI

## Document Information
- **RFC ID**: RFC-051
- **Related PRD**: [FEAT-051 (Git History Browser UI)](../../prd/features/FEAT-051-git-history-ui.md)
- **Depends On**: [RFC-050 (Git-based File Versioning)](RFC-050-git-versioning.md)
- **Depended On By**: None
- **Created**: 2026-03-02
- **Last Updated**: 2026-03-02
- **Status**: Draft

## Overview

This RFC adds a Git History Browser to the Memory feature. It consists of a `GitHistoryViewModel`, a `GitHistoryScreen` composable (commit list + filter chips), and a `CommitDetailSheet` composable (diff view). All git data is sourced from the existing `AppGitRepository` introduced in RFC-050.

## Architecture

### New Files

```
feature/
└── memory/
    └── ui/
        ├── GitHistoryScreen.kt      # commit list + filter chips
        ├── GitHistoryViewModel.kt   # state holder
        └── CommitDetailSheet.kt     # bottom sheet diff view
```

### Navigation

Add a new `Route.GitHistory` to the sealed `Route` class. Register the composable in the nav graph. The entry point is an icon button in `MemoryScreen`'s top bar.

## UI State

```kotlin
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
    FILES("Files", null)  // commits whose message starts with "file:"
}
```

## GitHistoryViewModel

```kotlin
class GitHistoryViewModel(
    private val appGitRepository: AppGitRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GitHistoryUiState())
    val uiState: StateFlow<GitHistoryUiState> = _uiState.asStateFlow()

    private var currentOffset = 0
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
        if (reset) currentOffset = 0
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val filter = _uiState.value.selectedFilter
            val newCommits = appGitRepository.log(
                path = filter.path,
                maxCount = pageSize + currentOffset
            )
            // For FILES filter: post-filter to commits whose message starts with "file:"
            val filtered = if (filter == GitHistoryFilter.FILES) {
                newCommits.filter { it.message.startsWith("file:") }
            } else {
                newCommits
            }
            val page = if (reset) filtered else filtered.drop(currentOffset)
            currentOffset += page.size
            _uiState.update { state ->
                state.copy(
                    commits = if (reset) filtered else state.commits + page,
                    isLoading = false,
                    canLoadMore = filtered.size == pageSize + (currentOffset - page.size)
                )
            }
        }
    }
}
```

## GitHistoryScreen

```kotlin
@Composable
fun GitHistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: GitHistoryViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Version History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Filter chips
            FilterChipRow(
                selected = uiState.selectedFilter,
                onSelect = viewModel::setFilter
            )

            when {
                uiState.isLoading && uiState.commits.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.commits.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No version history yet.")
                    }
                }
                else -> {
                    LazyColumn {
                        items(uiState.commits, key = { it.sha }) { commit ->
                            CommitListItem(
                                commit = commit,
                                onClick = { viewModel.selectCommit(commit) }
                            )
                            HorizontalDivider()
                        }
                        if (uiState.canLoadMore) {
                            item {
                                TextButton(
                                    onClick = viewModel::loadMore,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Load more")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Commit detail bottom sheet
    uiState.selectedCommit?.let { commit ->
        CommitDetailSheet(
            commit = commit,
            diff = uiState.commitDiff,
            isDiffLoading = uiState.isDiffLoading,
            onDismiss = viewModel::dismissDetail
        )
    }
}
```

### CommitListItem

Each row displays:
- First line: commit message (single line, ellipsized)
- Second line: short SHA + formatted timestamp (e.g., `a52d630 · Mar 2, 2026 14:30`)

### FilterChipRow

A `LazyRow` of `FilterChip` composables, one per `GitHistoryFilter` value.

## CommitDetailSheet

A `ModalBottomSheet` containing:
1. Commit message as a title
2. SHA + date as a subtitle
3. If `isDiffLoading`: `CircularProgressIndicator`
4. Else: `SelectionContainer { Text(diff, fontFamily = FontFamily.Monospace) }` in a vertically scrollable `Column`

The monospace text is selectable so users can copy specific lines.

## Navigation Changes

### Route

```kotlin
// In navigation/Route.kt
data object GitHistory : Route()
```

### Nav Graph

```kotlin
composable<Route.GitHistory> {
    GitHistoryScreen(onNavigateBack = { navController.popBackStack() })
}
```

### MemoryScreen Entry Point

Add to the `TopAppBar` actions of `MemoryScreen`:
```kotlin
IconButton(onClick = { navController.navigate(Route.GitHistory) }) {
    Icon(Icons.Outlined.History, contentDescription = "Version History")
}
```

## Koin DI

Add to `gitModule` in `GitModule.kt`:
```kotlin
viewModel { GitHistoryViewModel(get()) }
```

## Testing

### Unit Tests
- `GitHistoryViewModelTest`: filter changes trigger reload, selectCommit triggers show(), dismissDetail clears selection, loadMore appends commits

### Screenshot Tests (Layer 1C)
- `GitHistoryScreenTest`: loading state, empty state, populated list with filter chips
- `CommitDetailSheetTest`: loading diff state, populated diff state

## Open Questions

None.
