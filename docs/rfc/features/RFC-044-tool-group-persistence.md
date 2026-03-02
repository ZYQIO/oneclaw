# RFC-044: Tool Group Session Persistence

## Document Information
- **RFC ID**: RFC-044
- **Related PRD**: [FEAT-044 (Tool Group Session Persistence)](../../prd/features/FEAT-044-tool-group-persistence.md)
- **Related RFC**: [RFC-040 (Tool Group Routing)](RFC-040-tool-group-routing.md)
- **Created**: 2026-03-01
- **Last Updated**: 2026-03-01
- **Status**: Draft
- **Author**: TBD

## Overview

### Background

RFC-040 introduced dynamic tool loading via `load_tool_group`. Tool schemas are loaded on-demand to reduce token usage. However, loaded tool groups only persist within a single `SendMessageUseCase.execute()` invocation. Each new user message triggers a new `execute()` call, which reinitializes the active tool list from scratch:

```kotlin
// SendMessageUseCase.kt:136-137 -- reset on every execute()
val loadedGroupNames = mutableSetOf<String>()
val activeToolDefs = toolRegistry.getCoreToolDefinitions().toMutableList()
```

This causes tools loaded in previous turns to become unavailable, producing "Tool 'X' is not available for this agent" errors. The AI sees successful `load_tool_group` results in the conversation history and assumes the tools are still available, but they are not.

Real-world impact observed: 61 consecutive `gmail_trash` ERROR calls in a single turn because the AI tried to delete emails one by one, and every call failed because Gmail tools had been unloaded between turns.

### Goals

1. Restore previously loaded tool groups at the start of each `SendMessageUseCase.execute()` call
2. Use existing message history as the persistence mechanism (no new DB columns)
3. Zero additional API round-trips -- groups are pre-loaded before the LLM is called

### Non-Goals

- Cross-session group persistence
- Automatic group unloading
- `unload_tool_group` command
- UI for loaded group status

## Technical Design

### Approach: History-Based Restoration

Scan the session's existing message history for `TOOL_CALL` messages where `tool_name = "load_tool_group"` and `tool_status = "SUCCESS"`. Extract the `group_name` from `tool_input` JSON and pre-load those groups into `activeToolDefs`.

This approach is chosen because:
- **No schema changes** -- uses existing `tool_input` field on `TOOL_CALL` messages
- **No new persistence layer** -- message history is already the source of truth
- **Self-cleaning** -- `/clear` creates a new session with no history, so groups reset naturally
- **Idempotent** -- scanning and loading is safe to repeat

### Changed Files

```
app/src/main/kotlin/com/oneclaw/shadow/feature/chat/usecase/
  └── SendMessageUseCase.kt          # MODIFIED (add history scan + restore)
```

One file, one method addition, one call-site change.

## Detailed Design

### Step 1: Add History Scan Method

Add a private method to `SendMessageUseCase` that extracts previously loaded group names from the session's message history:

```kotlin
/**
 * RFC-044: Scan message history for previously successful load_tool_group calls.
 * Returns the set of group names that were loaded in prior turns.
 */
private fun restoreLoadedGroups(messages: List<Message>): Set<String> {
    val groups = mutableSetOf<String>()
    for (msg in messages) {
        if (msg.type == MessageType.TOOL_CALL &&
            msg.toolName == "load_tool_group" &&
            msg.toolStatus == ToolCallStatus.SUCCESS &&
            msg.toolInput != null
        ) {
            try {
                val params = Json.parseToJsonElement(msg.toolInput)
                    .jsonObject
                val groupName = params["group_name"]?.jsonPrimitive?.content
                if (groupName != null) {
                    groups.add(groupName)
                }
            } catch (_: Exception) {
                // Malformed tool_input -- skip
            }
        }
    }
    return groups
}
```

### Step 2: Pre-Load Groups at execute() Start

In `SendMessageUseCase.execute()`, after initializing `activeToolDefs` with core tools, scan history and restore previously loaded groups:

**Current code (lines 135-137):**
```kotlin
// 5. Build dynamic tool list: start with core tools only
val loadedGroupNames = mutableSetOf<String>()
val activeToolDefs = toolRegistry.getCoreToolDefinitions().toMutableList()
```

**New code:**
```kotlin
// 5. Build dynamic tool list: start with core tools, restore previously loaded groups
val existingMessages = messageRepository.getMessagesSnapshot(sessionId)
val previouslyLoadedGroups = restoreLoadedGroups(existingMessages)
val loadedGroupNames = previouslyLoadedGroups.toMutableSet()
val activeToolDefs = toolRegistry.getCoreToolDefinitions().toMutableList()
for (groupName in previouslyLoadedGroups) {
    val groupDefs = toolRegistry.getGroupToolDefinitions(groupName)
    activeToolDefs.addAll(groupDefs)
}
```

### Data Flow

```
User sends message 1:
  execute() called
    activeToolDefs = core tools
    AI calls load_tool_group("google_gmail") --> SUCCESS
    activeToolDefs += Gmail tools
    AI calls gmail_search --> SUCCESS
    Messages saved: TOOL_CALL(load_tool_group, SUCCESS, {"group_name":"google_gmail"})

User sends message 2:
  execute() called
    activeToolDefs = core tools
    restoreLoadedGroups(history) --> {"google_gmail"}     <-- NEW
    activeToolDefs += Gmail tools                          <-- NEW
    AI calls gmail_search --> SUCCESS (no re-load needed)
```

### Edge Cases

| Scenario | Behavior |
|----------|----------|
| New session (no history) | No groups restored, starts with core tools only |
| `/clear` command | Creates new session, fresh history, no groups restored |
| `load_tool_group` failed in history | `tool_status = ERROR`, skipped by the scan |
| Same group loaded multiple times | `Set<String>` deduplicates, loaded once |
| Group no longer exists in registry | `getGroupToolDefinitions()` returns empty list, no harm |
| Malformed `tool_input` JSON | Caught by try/catch, skipped |

### Performance

The history scan is lightweight:
- `getMessagesSnapshot()` is already called later in the execute loop (line ~191), so the data is likely cached by Room
- The scan is O(n) over messages, filtering only `TOOL_CALL` type with `load_tool_group` name
- Typical session has < 100 messages; scanning takes < 1ms
- This replaces what would otherwise be an extra API round-trip ($0.01-0.05 per re-load)

## Testing

### Unit Test

Add a test to verify `restoreLoadedGroups` correctly extracts group names from message history:

```kotlin
@Test
fun `restoreLoadedGroups extracts group names from successful load_tool_group calls`() {
    val messages = listOf(
        Message(type = MessageType.USER, content = "check email", ...),
        Message(type = MessageType.TOOL_CALL, toolName = "load_tool_group",
                toolStatus = ToolCallStatus.SUCCESS,
                toolInput = """{"group_name": "google_gmail"}""", ...),
        Message(type = MessageType.TOOL_RESULT, toolName = "load_tool_group", ...),
        Message(type = MessageType.TOOL_CALL, toolName = "gmail_search",
                toolStatus = ToolCallStatus.SUCCESS, ...),
        Message(type = MessageType.TOOL_CALL, toolName = "load_tool_group",
                toolStatus = ToolCallStatus.ERROR,
                toolInput = """{"group_name": "google_drive"}""", ...),
    )

    val groups = restoreLoadedGroups(messages)

    assertEquals(setOf("google_gmail"), groups)
    // google_drive excluded because status was ERROR
}
```

### Manual Verification

1. Load Gmail tools in turn 1, use `gmail_search` in turn 2 without re-loading -- should succeed
2. Load multiple groups across turns, verify all remain available
3. Send `/clear`, verify next turn starts fresh with no pre-loaded groups
4. Via Telegram bridge: load Gmail, send follow-up -- verify no "not available" errors

## Migration Notes

- No database schema changes
- No new dependencies
- Fully backward compatible -- existing conversations with `load_tool_group` history will automatically benefit
- The `getMessagesSnapshot()` call is moved slightly earlier in `execute()`, before the conversation loop starts
