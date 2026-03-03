# RFC-051: Git 历史记录浏览器 UI

## 文档信息
- **RFC ID**: RFC-051
- **关联 PRD**: [FEAT-051 (Git 历史记录浏览器 UI)](../../prd/features/FEAT-051-git-history-ui.md)
- **依赖**: [RFC-050 (基于 Git 的文件版本管理)](RFC-050-git-versioning.md)
- **被依赖**: 无
- **创建日期**: 2026-03-02
- **最后更新**: 2026-03-02
- **状态**: 草稿

## 概述

本 RFC 为记忆功能新增 Git 历史记录浏览器。它由一个 `GitHistoryViewModel`、一个 `GitHistoryScreen` 可组合项（提交列表 + 过滤芯片）以及一个 `CommitDetailSheet` 可组合项（差异视图）组成。所有 Git 数据均来源于 RFC-050 中引入的现有 `AppGitRepository`。

## 架构

### 新增文件

```
feature/
└── memory/
    └── ui/
        ├── GitHistoryScreen.kt      # 提交列表 + 过滤芯片
        ├── GitHistoryViewModel.kt   # 状态持有者
        └── CommitDetailSheet.kt     # 底部抽屉差异视图
```

### 导航

在密封类 `Route` 中新增 `Route.GitHistory`，并在导航图中注册该可组合项。入口点为 `MemoryScreen` 顶部栏中的图标按钮。

## UI 状态

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

每行显示：
- 第一行：提交信息（单行，超出截断）
- 第二行：短 SHA + 格式化时间戳（例如 `a52d630 · Mar 2, 2026 14:30`）

### FilterChipRow

一个 `LazyRow`，包含针对每个 `GitHistoryFilter` 值的 `FilterChip` 可组合项。

## CommitDetailSheet

一个 `ModalBottomSheet`，包含：
1. 提交信息作为标题
2. SHA + 日期作为副标题
3. 若 `isDiffLoading` 为真：显示 `CircularProgressIndicator`
4. 否则：在可垂直滚动的 `Column` 中显示 `SelectionContainer { Text(diff, fontFamily = FontFamily.Monospace) }`

等宽字体文本支持选中，以便用户复制特定行。

## 导航变更

### Route

```kotlin
// In navigation/Route.kt
data object GitHistory : Route()
```

### 导航图

```kotlin
composable<Route.GitHistory> {
    GitHistoryScreen(onNavigateBack = { navController.popBackStack() })
}
```

### MemoryScreen 入口点

在 `MemoryScreen` 的 `TopAppBar` actions 中添加：
```kotlin
IconButton(onClick = { navController.navigate(Route.GitHistory) }) {
    Icon(Icons.Outlined.History, contentDescription = "Version History")
}
```

## Koin DI

在 `GitModule.kt` 的 `gitModule` 中添加：
```kotlin
viewModel { GitHistoryViewModel(get()) }
```

## 测试

### 单元测试
- `GitHistoryViewModelTest`：过滤器变更触发重新加载，selectCommit 触发 show()，dismissDetail 清除选中状态，loadMore 追加提交记录

### 截图测试（第 1C 层）
- `GitHistoryScreenTest`：加载状态、空状态、带过滤芯片的列表填充状态
- `CommitDetailSheetTest`：差异加载中状态、差异已加载状态

## 待解问题

无。
