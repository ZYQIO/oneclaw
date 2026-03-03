# Memory System

OneClawShadow provides a persistent memory system that allows the AI to remember information across conversations. Memory is automatically injected into system prompts, giving the AI context about the user's preferences, projects, and past interactions.

## Memory Types

### Long-Term Memory (MEMORY.md)

A structured Markdown file stored at `filesDir/memory/MEMORY.md`. Its content is injected into the system prompt of every conversation.

Organized by category:
- **profile** -- User identity and background
- **preferences** -- Communication and workflow preferences
- **interests** -- Topics and hobbies
- **workflow** -- How the user works
- **projects** -- Active projects and goals
- **notes** -- General information

Managed via the `save_memory` and `update_memory` tools.

### Daily Logs

Automatic daily summaries stored at `filesDir/memory/daily/{YYYY-MM-DD}.md`. Written by `DailyLogWriter` at specific trigger points:

1. **Session end** -- When a conversation session ends
2. **App background** -- When the app goes to the background
3. **Session switch** -- When the user switches to a different session
4. **Day change** -- When the date changes during use
5. **Pre-compaction flush** -- Before auto-compact runs

Daily logs capture conversation summaries and are searchable via the `search_history` tool.

## Hybrid Search Engine

The search system combines two approaches for best results:

### BM25 Keyword Search (30% weight)

Full-text keyword scoring using the BM25 algorithm. Finds exact term matches across memory chunks.

### Vector Semantic Search (70% weight)

Embedding-based similarity search using cosine distance. Finds semantically related content even without exact keyword matches.

**Embedding Engine:**
- Uses ONNX Runtime with MiniLM-L6-v2 model (~22MB)
- Generates 384-dimensional vectors
- Falls back gracefully to BM25-only search when the model is unavailable
- Embeddings stored in Room via `MemoryIndexDao`

### Time Decay

A time decay multiplier weights recent memories higher than older ones, ensuring that recent context is prioritized in search results.

### Search Flow

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

## Memory Injection

`MemoryInjector` retrieves relevant memories and prepends them to the system prompt before each AI request. This provides the AI with context about the user without the user needing to repeat information.

## Memory Quality (FEAT-049)

The memory quality system scores and manages memory entries to prevent bloat:
- Quality scoring for memory entries
- Automated cleanup of low-quality or duplicate entries
- Ensures MEMORY.md stays concise and relevant

## Memory UI

The Memory screen (`feature/memory/ui/MemoryScreen.kt`) provides:
- View and browse daily logs by date
- View and edit long-term memory
- Memory statistics display

## Related Tools

| Tool | Purpose |
|------|---------|
| `save_memory` | Add new entries to MEMORY.md |
| `update_memory` | Edit or delete existing entries |
| `search_history` | Search across memory, daily logs, and past sessions |

## File Layout

```
filesDir/memory/
├── MEMORY.md           # Long-term memory (injected into system prompt)
└── daily/
    ├── 2026-02-28.md   # Daily log for Feb 28
    ├── 2026-03-01.md   # Daily log for Mar 1
    └── ...
```
