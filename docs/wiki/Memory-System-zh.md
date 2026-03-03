# 记忆系统

OneClawShadow 提供持久化记忆系统，使 AI 能够在多次对话之间记住信息。记忆内容会自动注入到系统提示中，让 AI 了解用户的偏好、项目和过往交互。

## 记忆类型

### 长期记忆（MEMORY.md）

一个结构化的 Markdown 文件，存储在 `filesDir/memory/MEMORY.md`。其内容会注入到每次对话的系统提示中。

按类别组织：
- **profile** -- 用户身份与背景
- **preferences** -- 沟通与工作流偏好
- **interests** -- 感兴趣的话题与爱好
- **workflow** -- 用户的工作方式
- **projects** -- 进行中的项目与目标
- **notes** -- 一般性信息

通过 `save_memory` 和 `update_memory` 工具进行管理。

### 每日日志

自动生成的每日摘要，存储在 `filesDir/memory/daily/{YYYY-MM-DD}.md`。由 `DailyLogWriter` 在以下特定触发点写入：

1. **会话结束** -- 对话会话结束时
2. **应用切入后台** -- 应用进入后台时
3. **切换会话** -- 用户切换到不同会话时
4. **日期变更** -- 使用过程中日期发生变化时
5. **自动压缩前刷新** -- 自动压缩（auto-compact）运行前

每日日志记录对话摘要，可通过 `search_history` 工具进行检索。

## 混合搜索引擎

搜索系统结合两种方式以获得最优结果：

### BM25 关键词搜索（权重 30%）

使用 BM25 算法进行全文关键词评分，对记忆块中的精确词语进行匹配。

### 向量语义搜索（权重 70%）

基于嵌入向量的相似度搜索，使用 cosine distance 度量。即使没有精确关键词匹配，也能找到语义相关的内容。

**嵌入引擎：**
- 使用 ONNX Runtime 与 MiniLM-L6-v2 模型（约 22MB）
- 生成 384 维向量
- 当模型不可用时，优雅降级为仅 BM25 搜索
- 嵌入向量通过 `MemoryIndexDao` 存储在 Room 中

### 时间衰减

时间衰减乘数对近期记忆赋予更高权重，确保搜索结果中优先呈现近期上下文。

### 搜索流程

```
Query
  |
  +---> BM25Scorer -> keyword matches with scores
  |
  +---> VectorSearcher -> semantic matches with cosine similarity
  |
  v
HybridSearchEngine
  |
  +---> Merge results (0.3 * BM25 + 0.7 * vector)
  +---> Apply time decay multiplier
  +---> Return top-K ranked results
```

## 记忆注入

`MemoryInjector` 在每次 AI 请求前检索相关记忆，并将其前置到系统提示中。这使 AI 能够了解用户的上下文，而无需用户重复说明。

## 记忆质量（FEAT-049）

记忆质量系统对记忆条目进行评分与管理，防止内容膨胀：
- 对记忆条目进行质量评分
- 自动清理低质量或重复的条目
- 确保 MEMORY.md 保持简洁且相关

## 记忆界面

记忆界面（`feature/memory/ui/MemoryScreen.kt`）提供以下功能：
- 按日期浏览每日日志
- 查看和编辑长期记忆
- 显示记忆统计信息

## 相关工具

| 工具 | 用途 |
|------|------|
| `save_memory` | 向 MEMORY.md 添加新条目 |
| `update_memory` | 编辑或删除已有条目 |
| `search_history` | 在记忆、每日日志和历史会话中搜索 |

## 文件结构

```
filesDir/memory/
├── MEMORY.md           # Long-term memory (injected into system prompt)
└── daily/
    ├── 2026-02-28.md   # Daily log for Feb 28
    ├── 2026-03-01.md   # Daily log for Mar 1
    └── ...
```
