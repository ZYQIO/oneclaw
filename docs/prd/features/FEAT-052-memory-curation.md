# Memory Curation System

## Feature Information
- **Feature ID**: FEAT-052
- **Created**: 2026-03-04
- **Last Updated**: 2026-03-04
- **Status**: Draft
- **Priority**: P1 (Should Have)
- **Owner**: TBD
- **Related RFC**: [RFC-052 (Memory Curation System)](../../rfc/features/RFC-052-memory-curation.md)
- **Extends**: [FEAT-049 (Memory Quality Improvement)](FEAT-049-memory-quality.md), [FEAT-013 (Memory System)](FEAT-013-memory.md)

## User Story

**As** a user of OneClaw,
**I want** the long-term memory (MEMORY.md) to contain only high-quality, enduring facts about me,
**so that** the AI's context remains clean, relevant, and free of noise like expired events, operational details, and redundant entries.

### Pain Points (Current State)

The current memory system has two write paths that both directly append to MEMORY.md, causing quality degradation over time:

1. **DailyLogWriter promotes noise** -- When a session ends, `DailyLogWriter` extracts "Long-term Facts" from the conversation and appends them directly to MEMORY.md via `appendMemory()`. This produces:
   - Orphan entries outside any section (e.g., `*   User's name is Ben.` at the bottom of the file, duplicating an entry already in User Profile)
   - Transient information promoted to permanent memory (e.g., "User cleaned Gmail inbox today")
   - Operational details that belong in daily logs, not long-term memory (e.g., specific email addresses to trash)

2. **No curation pass** -- There is no mechanism to periodically review MEMORY.md and remove expired, redundant, or low-value entries. The existing compaction (FEAT-049) only triggers when the file exceeds 3,000 chars and relies on a single LLM pass that may not catch all issues.

3. **Workflow section bloat** -- The "Workflow" section accumulates detailed operational configuration (email filter rules, label IDs, skill versions) that should not be in long-term memory. These details change frequently and belong in tool-specific configuration or daily logs.

4. **Expired temporal entries persist** -- Past deadlines, completed interviews, and other time-bound information remains in MEMORY.md indefinitely (e.g., "Candidate interview scheduled Tuesday, March 2, 2026 at 8:30 AM" is still present on March 4).

### Typical Scenarios

1. User has a conversation about cleaning Gmail. DailyLogWriter extracts "User cleans Gmail regularly" as a long-term fact and appends it to MEMORY.md. But this is already captured in daily logs and searchable via HybridSearchEngine -- it does not need to be in MEMORY.md.

2. User has a job interview scheduled for March 2. After March 2, this entry has zero value in long-term memory but persists indefinitely.

3. User's Gmail cleanup skill gets updated from v1.3 to v1.4. The version number gets saved to MEMORY.md. Next week it becomes v1.5 -- now MEMORY.md has stale information.

4. User interacts exclusively via Telegram bridge for several days without opening the app. No curation runs because it was gated on app open/lifecycle events.

## Feature Description

### Overview

This feature introduces a **Memory Curation System** that restructures how long-term memory is maintained:

1. **Decouple DailyLogWriter from MEMORY.md** -- DailyLogWriter writes only to daily logs. It no longer promotes facts to MEMORY.md.

2. **Introduce MemoryCurator** -- A new component that runs once daily (via WorkManager) to review recent daily logs against current MEMORY.md and make high-quality, deliberate updates.

3. **Rename Workflow -> Habits/Routines** -- Clarify that this section stores behavioral patterns, not operational configuration.

4. **Strengthen compaction prompt** -- Add specific rules for removing expired temporal entries and limiting operational detail.

5. **Enhance SaveMemoryTool prompt** -- Reinforce that daily-log-level information should not be duplicated in MEMORY.md.

6. **User-configurable curation schedule** -- Display curation time in Memory Screen and allow the user to change it.

7. **Daily log consolidation** -- During the daily curation pass, consolidate yesterday's fragmented daily log into a single coherent summary.

### Detailed Specification

#### Change 1: DailyLogWriter Decoupling

Remove the "Long-term Facts" extraction and MEMORY.md append from `DailyLogWriter.writeDailyLog()`. The summarization prompt is simplified to produce only a daily summary (no "## Long-term Facts" section). Daily logs remain the source of short-term episodic memory, searchable via HybridSearchEngine and injected into system prompts via MemoryInjector's "Relevant Memories" section.

#### Change 2: MemoryCurator (New Component)

A new `MemoryCurator` class runs once per day via Android WorkManager (`MemoryCurationWorker`). It:

1. Reads the last N days of daily logs (default: 3 days)
2. Reads the current MEMORY.md content
3. Sends both to the LLM with a carefully designed curation prompt
4. The LLM decides whether any updates are needed:
   - If new long-term-worthy facts emerged across multiple days, add them
   - If existing entries are contradicted by recent information, update them
   - If entries have expired (dates in the past), remove them
   - If no changes are needed, return a sentinel value (`NO_CHANGES`)
5. If changes are needed, overwrites MEMORY.md with the curated version (git history provides the safety net)

**Curation Prompt Design Principles:**
- Only promote facts that appeared in 2+ days of logs (pattern confirmation)
- Never add one-time events or transient observations
- Remove temporal entries whose dates have passed
- Habits/Routines section: only behavioral patterns, no configuration details
- Prefer updating existing entries over adding new contradictory ones
- If nothing needs changing, say so (avoid unnecessary rewrites)

#### Change 3: Rename Workflow -> Habits/Routines

Change the standard section name from "Workflow" to "Habits/Routines" in `MemorySections.STANDARD_SECTIONS`. The compaction and curation prompts explicitly instruct the LLM to store only habitual behaviors in this section (e.g., "Runs a daily Gmail cleanup at 11 PM"), not operational details (e.g., specific email addresses, label IDs, skill versions).

#### Change 4: Strengthen Compaction Prompt

Add these rules to the compaction prompt in `MemoryCompactor`:
- Delete all entries containing dates that have already passed (expired events, completed deadlines)
- Habits/Routines section: each entry must be a single-line behavioral pattern, no configuration parameters
- Maximum 10 entries per section to prevent unbounded growth
- If a section would be empty after compaction, remove it entirely

#### Change 5: Enhance SaveMemoryTool Prompt

Add to the "DO NOT save" list:
- Information already captured in daily logs (short-term episodic memory is handled separately)
- Operational configuration details (email addresses, label IDs, API endpoints, version numbers)
- Specific scheduled events with concrete dates (these expire and become noise)

#### Change 6: User-Configurable Curation Schedule

In the Memory Screen, add a setting that:
- Displays the current curation schedule (e.g., "Memory curation runs daily at 3:00 AM")
- Allows the user to pick a different time via a time picker dialog
- Stores the preference in SharedPreferences
- Re-registers the WorkManager periodic task when the time changes

Default curation time: **3:00 AM local time**.

#### Change 7: Daily Log Consolidation

During the daily curation pass, the MemoryCurator also consolidates yesterday's daily log. Throughout a day, `DailyLogWriter` appends a new summary block each time a session ends or the app goes to background. This produces fragmented daily logs with multiple `---`-separated sections that may overlap in topic coverage.

The consolidation step:
1. Reads yesterday's daily log file
2. If it contains multiple summary blocks (2+ sections separated by `---`), sends them to the LLM to merge into a single coherent daily summary
3. Overwrites yesterday's daily log with the consolidated version (git history preserves the original fragmented version)
4. Reindexes the consolidated content for search
5. Does NOT touch today's daily log (which may still receive new entries)
6. Does NOT touch older daily logs (those were already consolidated on their respective days)

This is safe because:
- The original per-message data lives in the Room database and is never deleted
- Git auto-commit preserves the pre-consolidation version of the daily log
- Only yesterday's log is consolidated -- today's is still accumulating, and older ones are already done

## Acceptance Criteria

### DailyLogWriter Decoupling
- [ ] `DailyLogWriter.writeDailyLog()` no longer calls `longTermMemoryManager.appendMemory()`
- [ ] Summarization prompt no longer includes "## Long-term Facts" section
- [ ] Daily log files continue to be written correctly
- [ ] Memory index continues to be updated for daily log entries
- [ ] Existing unit tests updated to reflect the simplified behavior

### MemoryCurator
- [ ] `MemoryCurator` class exists with `curate()` method
- [ ] `MemoryCurationWorker` (CoroutineWorker) runs curation on schedule
- [ ] WorkManager periodic task registered at app initialization
- [ ] Curation reads last 3 days of daily logs + current MEMORY.md
- [ ] Curation prompt enforces quality criteria (2+ day pattern, no transient info, no expired events)
- [ ] Returns `NO_CHANGES` when no updates are needed (does not rewrite MEMORY.md unnecessarily)
- [ ] When changes are needed, overwrites MEMORY.md with curated content
- [ ] Git commit created on each MEMORY.md update (existing MemoryFileStorage behavior)
- [ ] Curation runs even when app is not in foreground (WorkManager)
- [ ] Default schedule: 3:00 AM

### Section Rename
- [ ] `MemorySections.STANDARD_SECTIONS` contains "Habits/Routines" instead of "Workflow"
- [ ] Compaction prompt uses "Habits/Routines" section name
- [ ] Curation prompt uses "Habits/Routines" section name
- [ ] SaveMemoryTool category mapping updated (`"habits"` / `"routines"` -> "Habits/Routines")
- [ ] Existing "## Workflow" content migrated to "## Habits/Routines" on first compaction/curation

### Compaction Prompt Enhancement
- [ ] Compaction prompt includes rule to remove expired temporal entries
- [ ] Compaction prompt includes rule to limit entries per section (max 10)
- [ ] Compaction prompt specifies Habits/Routines should contain only behavioral patterns

### SaveMemoryTool Prompt Enhancement
- [ ] "DO NOT save" list includes daily-log-level information
- [ ] "DO NOT save" list includes operational configuration details
- [ ] "DO NOT save" list includes specific scheduled events with dates

### Daily Log Consolidation
- [ ] Curation pass checks yesterday's daily log for multiple summary blocks
- [ ] If multiple blocks exist, LLM merges them into a single coherent summary
- [ ] Consolidated summary overwrites yesterday's daily log file
- [ ] Git commit created for the consolidated daily log (existing MemoryFileStorage behavior)
- [ ] Today's daily log is never touched during consolidation
- [ ] Daily logs older than yesterday are never touched
- [ ] If yesterday's log has only one block, consolidation is skipped
- [ ] Search index is updated after consolidation

### Curation Schedule UI
- [ ] Memory Screen displays current curation schedule
- [ ] Time picker allows changing curation time
- [ ] Changed time is persisted to SharedPreferences
- [ ] Changed time triggers WorkManager re-registration
- [ ] Default time is 3:00 AM

## Feature Boundary

### Included
- DailyLogWriter decoupling from MEMORY.md
- New MemoryCurator component with LLM-driven curation
- MemoryCurationWorker with WorkManager scheduling
- Daily log consolidation (merge yesterday's fragmented log into one summary)
- Rename Workflow -> Habits/Routines
- Enhanced compaction prompt
- Enhanced SaveMemoryTool prompt
- Curation schedule UI in Memory Screen

### Not Included
- Semantic deduplication (embedding-based) -- MEMORY.md is small enough that this is unnecessary
- TTL/expiration markers on individual entries -- risk of deleting important permanent memories
- LLM quality gate on every save_memory call -- too expensive and adds latency
- Changes to MemoryInjector or HybridSearchEngine
- Changes to UpdateMemoryTool

## Business Rules

### Curation Rules
1. MemoryCurator runs once per day via WorkManager, at the user-configured time (default 3:00 AM)
2. Curation reads daily logs from the last 3 days and the current MEMORY.md
3. The LLM decides what to update; if nothing needs changing, MEMORY.md is not rewritten
4. Only facts that appear as patterns across 2+ days of logs should be promoted to MEMORY.md
5. Expired temporal entries (past dates) must be removed during curation
6. Habits/Routines section stores only behavioral patterns, not operational configuration
7. Each section should have at most 10 entries after curation

### DailyLogWriter Rules
1. DailyLogWriter writes ONLY to daily log files and the memory search index
2. DailyLogWriter does NOT write to MEMORY.md under any circumstances
3. The summarization prompt produces only a "## Daily Summary" section
4. Daily logs remain searchable via HybridSearchEngine and injectable via MemoryInjector

### Daily Log Consolidation Rules
1. Consolidation runs as part of the daily curation pass, before MEMORY.md curation
2. Only yesterday's daily log is consolidated -- today's log is never touched
3. Consolidation is skipped if yesterday's log has only a single summary block (no `---` separators)
4. The LLM merges multiple blocks into one coherent summary, removing duplication across blocks
5. Git auto-commit preserves the pre-consolidation version
6. Search index is rebuilt for the consolidated date after overwrite

### WorkManager Rules
1. Use `PeriodicWorkRequest` with a ~24-hour period
2. Calculate `initialDelay` to target the user's configured time
3. Add network constraint (curation requires LLM API call)
4. Use `ExistingPeriodicWorkPolicy.UPDATE` to handle schedule changes
5. Register with a unique work name to prevent duplicates

## Dependencies

### Depends On
- **FEAT-049 (Memory Quality Improvement)**: Extends compaction and structured memory
- **FEAT-013 (Memory System)**: Base memory infrastructure
- **AndroidX WorkManager**: For background scheduling

### Depended On By
- None currently

## Error Handling

### Error Scenarios

1. **Curation LLM call fails (network error, API error)**
   - Handling: Worker returns `Result.retry()` for transient failures
   - WorkManager will retry with exponential backoff
   - MEMORY.md is not modified

2. **Curation LLM returns empty or invalid response**
   - Handling: Discard the response, keep MEMORY.md unchanged
   - Log a warning for debugging
   - Worker returns `Result.success()` (do not retry with same input)

3. **No daily logs available for curation**
   - Handling: Skip curation, return `Result.success()`
   - This is normal for days with no app usage

4. **WorkManager task not running (Doze mode, battery optimization)**
   - Handling: WorkManager handles this automatically with flex window
   - The task will run when the device exits Doze
   - Not time-critical -- a few hours delay is acceptable

5. **User changes curation time while a curation is in progress**
   - Handling: Current curation completes normally
   - Next curation scheduled at the new time

## Test Points

### Unit Tests
- DailyLogWriter no longer writes to MEMORY.md
- DailyLogWriter summarization prompt has no "Long-term Facts" section
- MemoryCurator.curate() with daily logs containing new facts: updates MEMORY.md
- MemoryCurator.curate() with no relevant new facts: returns NO_CHANGES, MEMORY.md untouched
- MemoryCurator.curate() with no daily logs: returns early
- MemoryCurator.curate() with expired entries in MEMORY.md: removes them
- MemoryCurator consolidates yesterday's multi-block daily log into single summary
- MemoryCurator skips consolidation when yesterday's log has single block
- MemoryCurator skips consolidation when yesterday's log does not exist
- MemorySections.STANDARD_SECTIONS contains "Habits/Routines" (not "Workflow")
- SaveMemoryTool category "habits" maps to "Habits/Routines"
- Compaction prompt includes expired-entry removal rule

### Integration Tests
- MemoryCurationWorker executes curation via MemoryCurator
- WorkManager schedules curation at configured time

### Manual Tests
- Use app for 3 days, verify MEMORY.md not polluted by DailyLogWriter
- Trigger manual curation, verify only high-quality facts promoted
- Change curation time in UI, verify WorkManager rescheduled
- Verify curation runs even when app not opened (via Telegram bridge only)

## Change History

| Date | Version | Changes | Owner |
|------|---------|---------|-------|
| 2026-03-04 | 0.1 | Initial version | - |
| 2026-03-04 | 0.2 | Add daily log consolidation (Change 7) | - |
