# RFC-010: Concurrent User Input (Queue-Based Message Injection)

## Document Information
- **RFC ID**: RFC-010
- **Related PRD**: [FEAT-010 (Concurrent User Input)](../../prd/features/FEAT-010-concurrent-input.md)
- **Related Architecture**: [RFC-000 (Overall Architecture)](../architecture/RFC-000-overall-architecture.md)
- **Depends On**: [RFC-001 (Chat Interaction)](RFC-001-chat-interaction.md)
- **Depended On By**: None
- **Created**: 2026-02-28
- **Last Updated**: 2026-02-28
- **Status**: Draft
- **Author**: TBD

## Overview

### Background

Currently, the send button is disabled (`canSend = false`) and the text field is disabled (`enabled = !isStreaming`) while the agent loop is running. Users must wait for the full loop to complete before they can send another message. This creates a poor experience during long-running tool executions or slow streaming responses, where the user may wish to add context, ask a follow-up, or redirect the agent mid-loop.

The agent loop in `SendMessageUseCase` already has a natural iteration structure: it calls `getMessagesSnapshot()` at the start of every round, which reads the full message history from the database. This means any user message saved to the database before the next iteration begins will be included in the next API call automatically.

### Goals

1. Allow users to send messages at any time, including while the agent loop is running.
2. Queued messages are injected at the next iteration boundary (after the current AI response finishes or after tool results are returned), so the AI sees them in the correct turn order.
3. Queued messages appear in the chat UI immediately after being submitted, before the AI processes them.
4. The send button and stop button are independent UI elements that coexist during streaming.
5. When the user presses Stop with queued messages, the partial AI response is saved, queued messages remain in the DB, and a system prompt is injected marking those queued messages as abandoned.

### Non-Goals

- Interrupting a streaming response mid-token to inject a message.
- Editing or cancelling a queued message once it has been submitted.
- Multiple simultaneous agent loops (one loop per session remains the invariant).

## Technical Design

### Architecture Overview

```
User types and taps Send while agent loop is running
         |
         v
ChatViewModel.sendMessage()
  |-- if NOT streaming: start a new agent loop (current behavior)
  |-- if streaming:
        |-- save message to DB immediately (shows in UI)
        |-- send text to pendingMessages: Channel<String>
         |
         v
SendMessageUseCase (running agent loop)
  After each iteration boundary (ResponseComplete or ToolRoundStarting):
        |-- drain pendingMessages channel
        |-- if messages found: emit UserMessageInjected, continue loop
        |-- if no messages and AI response is final: emit ResponseComplete, break
```

### Injection Point

`SendMessageUseCase` currently breaks on `pendingToolCalls.isEmpty()` (the AI returned a text response with no tool calls). With this RFC, before breaking it checks for pending user messages:

```
iteration N:
  stream AI response -> no tool calls -> would normally emit ResponseComplete and break
  -> check pendingMessages channel
  -> if message found: emit UserMessageInjected, loop to iteration N+1
  -> getMessagesSnapshot in iteration N+1 picks up all saved messages naturally
  -> if no message: emit ResponseComplete and break (unchanged behavior)

iteration N (tool calls):
  stream AI response -> tool calls found -> save tool results
  -> check pendingMessages channel before ToolRoundStarting
  -> if message found: emit UserMessageInjected
  -> emit ToolRoundStarting
  -> getMessagesSnapshot in iteration N+1 picks up all messages naturally
```

Injecting before `getMessagesSnapshot` is sufficient because the snapshot is always read fresh from the database at the top of each iteration.

### Stop + Queued Messages Behavior

When the user presses Stop while there are queued messages in the channel:

```
Timeline:
1. User sends A    -> loop starts -> AI streaming response to A...
2. User sends B    -> queued, saved to DB, shown in UI
3. User presses Stop
4. -> AI partial response is saved to DB (existing CancellationException handler)
5. -> Channel is drained, queued message texts are recorded
6. -> A SYSTEM message is saved to DB after B:
      "[System] The user interrupted the previous response.
       The preceding queued message(s) were submitted before the interruption
       and can be ignored. Please respond to the user's next message."
7. -> Loop terminates

DB state:
  ... -> User: A -> AI: (partial response) -> User: B -> System: (ignore B note) -> (end)
```

This ensures that when the user later sends message C, the AI sees the full history including the system note. The AI understands: A was partially answered, B was queued but abandoned, C is the actual new intent.

The system message is not rendered as a visible message in the chat UI. It uses `MessageType.SYSTEM` and appears as a small grey label (existing `SystemMessageCard`), or alternatively we can filter it from display. The wording is an internal instruction to the AI, not user-facing text.

### New ChatEvent

Add one new event to `ChatEvent`:

```kotlin
// Emitted when a pending user message is injected at an iteration boundary.
// The ViewModel uses this to update the UI state (e.g., clear pending indicator).
data class UserMessageInjected(val text: String) : ChatEvent()
```

No other ChatEvent changes are needed.

### SendMessageUseCase Changes

Signature change: add an optional `pendingMessages` parameter.

```kotlin
fun execute(
    sessionId: String,
    userText: String,
    agentId: String,
    pendingMessages: Channel<String> = Channel(Channel.UNLIMITED)
): Flow<ChatEvent>
```

Inside the loop, replace the current break block:

```kotlin
// BEFORE (current code):
if (pendingToolCalls.isEmpty()) {
    sessionRepository.updateMessageStats(...)
    send(ChatEvent.ResponseComplete(aiMessage, usage))
    break
}

// AFTER:
if (pendingToolCalls.isEmpty()) {
    // Drain any pending user messages before deciding to stop
    val injected = drainPendingMessages(pendingMessages)
    for (text in injected) {
        send(ChatEvent.UserMessageInjected(text))
    }
    if (injected.isEmpty()) {
        // No pending messages -- loop is truly done
        sessionRepository.updateMessageStats(
            id = sessionId,
            count = messageRepository.getMessageCount(sessionId),
            preview = accumulatedText.take(100)
        )
        send(ChatEvent.ResponseComplete(aiMessage, usage))
        break
    }
    // Pending messages found -- continue to next iteration
    round++
    send(ChatEvent.ToolRoundStarting(round))
    continue
}

// Also drain after tool results, before ToolRoundStarting (tool call path):
val injected = drainPendingMessages(pendingMessages)
for (text in injected) {
    send(ChatEvent.UserMessageInjected(text))
}
round++
if (round < MAX_TOOL_ROUNDS) {
    send(ChatEvent.ToolRoundStarting(round))
}
```

Private helper function:

```kotlin
private fun drainPendingMessages(channel: Channel<String>): List<String> {
    val injected = mutableListOf<String>()
    while (true) {
        val text = channel.tryReceive().getOrNull() ?: break
        injected.add(text)
    }
    return injected
}
```

Note: the ViewModel saves the message to DB at the moment the user sends it. The use case only needs to drain the channel to know injection happened. `getMessagesSnapshot()` at the top of the next iteration reads the messages from DB naturally.

### ChatUiState Changes

```kotlin
data class ChatUiState(
    // ... existing fields ...

    // REMOVE:
    // val canSend: Boolean = true

    // ADD:
    val pendingCount: Int = 0  // number of messages queued during streaming
)
```

### ChatViewModel Changes

**New field**:

```kotlin
private val pendingMessages = Channel<String>(Channel.UNLIMITED)
```

**`sendMessage()` rewrite**:

```kotlin
fun sendMessage() {
    val text = _uiState.value.inputText.trim()
    if (text.isBlank()) return
    _uiState.update { it.copy(inputText = "") }

    if (_uiState.value.isStreaming) {
        // Queue path: save to DB immediately, signal the running loop
        viewModelScope.launch {
            val sessionId = _uiState.value.sessionId ?: return@launch
            messageRepository.addMessage(Message(
                id = "", sessionId = sessionId, type = MessageType.USER,
                content = text, thinkingContent = null,
                toolCallId = null, toolName = null, toolInput = null, toolOutput = null,
                toolStatus = null, toolDurationMs = null, tokenCountInput = null,
                tokenCountOutput = null, modelId = null, providerId = null, createdAt = 0
            ))
            val tempId = java.util.UUID.randomUUID().toString()
            _uiState.update { state ->
                state.copy(
                    messages = state.messages + ChatMessageItem(
                        id = tempId, type = MessageType.USER,
                        content = text, timestamp = System.currentTimeMillis()
                    ),
                    pendingCount = state.pendingCount + 1
                )
            }
            pendingMessages.trySend(text)
        }
        return
    }

    // Non-streaming path: start a new loop (existing logic)
    // Remove all "canSend = false" writes from this path.
    // ... rest of existing sendMessage body unchanged except canSend removal ...
}
```

**Pass `pendingMessages` to use case**:

```kotlin
streamingJob = viewModelScope.launch {
    sendMessageUseCase.execute(
        sessionId = finalSessionId,
        userText = text,
        agentId = _uiState.value.currentAgentId,
        pendingMessages = pendingMessages
    ).collect { event ->
        handleChatEvent(event, finalSessionId, accumulatedText, accumulatedThinking) { ... }
    }
}
```

**Handle `UserMessageInjected` in `handleChatEvent`**:

```kotlin
is ChatEvent.UserMessageInjected -> {
    _uiState.update { it.copy(pendingCount = maxOf(0, it.pendingCount - 1)) }
}
```

**`stopGeneration()` -- save abandon note**:

The existing `stopGeneration()` calls `streamingJob?.cancel()`, which triggers the `CancellationException` handler in `sendMessage()`. That handler calls `savePartialResponse()` then `finishStreaming()`.

Update `finishStreaming()`:

```kotlin
private suspend fun finishStreaming(sessionId: String) {
    // Handle queued messages that won't be processed
    val abandonedTexts = mutableListOf<String>()
    while (true) {
        val text = pendingMessages.tryReceive().getOrNull() ?: break
        abandonedTexts.add(text)
    }
    if (abandonedTexts.isNotEmpty() && sessionId != null) {
        // Insert system message marking queued messages as abandoned
        messageRepository.addMessage(Message(
            id = "", sessionId = sessionId, type = MessageType.SYSTEM,
            content = "The user interrupted the previous response. " +
                "The preceding queued message(s) were submitted before the interruption " +
                "and can be ignored. Please respond to the user's next message.",
            thinkingContent = null,
            toolCallId = null, toolName = null, toolInput = null, toolOutput = null,
            toolStatus = null, toolDurationMs = null, tokenCountInput = null,
            tokenCountOutput = null, modelId = null, providerId = null, createdAt = 0
        ))
    }

    _uiState.update {
        it.copy(
            isStreaming = false,
            streamingText = "",
            streamingThinkingText = "",
            activeToolCalls = emptyList(),
            pendingCount = 0
        )
    }
    // ... rest of finishStreaming unchanged (reload messages from DB, title generation) ...
}
```

**Remove all `canSend` writes** from: `sendMessage()`, `streamWithExistingMessage()`, `finishStreaming()`, `handleError()`, `initialize()`.

### Rapid Double-Send (Pre-Session)

If the user sends two messages before the agent loop starts (before `sessionId` is created), the existing lazy session creation in `sendMessage()` handles the first message. The second message arrives while `isStreaming = true`, entering the queue path. Both messages end up as USER messages in the DB, and the first `getMessagesSnapshot()` call includes both.

### UI Changes in ChatScreen / ChatInput

The `ChatInput` composable currently uses an `if/else` to show either Stop or Send. Change to show both independently:

```kotlin
@Composable
fun ChatInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isStreaming: Boolean,
    hasConfiguredProvider: Boolean
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().imePadding()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message") },
                shape = MaterialTheme.shapes.extraLarge,
                maxLines = 6,
                enabled = true  // CHANGED: always enabled
            )
            Spacer(modifier = Modifier.width(8.dp))

            // Stop button: only visible during streaming
            if (isStreaming) {
                IconButton(
                    onClick = onStop,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    // Spinning stop indicator
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            // Send button: always visible, enabled when text is not blank
            IconButton(
                onClick = onSend,
                enabled = text.isNotBlank() && hasConfiguredProvider,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}
```

Key UI changes:
- `OutlinedTextField.enabled` is always `true` (was `!isStreaming`).
- Stop button: shown during streaming with a `CircularProgressIndicator` as the icon (spinning).
- Send button: always shown, enabled when `text.isNotBlank() && hasConfiguredProvider`.
- The `canSend` parameter is removed from `ChatInput`. The caller passes `hasConfiguredProvider` instead.

Update the call site in `ChatScreen`:

```kotlin
ChatInput(
    text = uiState.inputText,
    onTextChange = { viewModel.updateInputText(it) },
    onSend = { viewModel.sendMessage() },
    onStop = { viewModel.stopGeneration() },
    isStreaming = uiState.isStreaming,
    hasConfiguredProvider = uiState.hasConfiguredProvider
)
```

## Implementation Steps

### Step 1: Add `UserMessageInjected` to `ChatEvent`
- File: `feature/chat/ChatEvent.kt`
- Add `data class UserMessageInjected(val text: String) : ChatEvent()`

### Step 2: Update `SendMessageUseCase`
- File: `feature/chat/usecase/SendMessageUseCase.kt`
- Add `pendingMessages: Channel<String>` parameter with default to `execute()`
- Add `drainPendingMessages()` private helper
- Replace the `break` block on the no-tool-calls path with drain-check logic
- Add drain call on the tool-calls path before `ToolRoundStarting`

### Step 3: Update `ChatUiState`
- File: `feature/chat/ChatUiState.kt`
- Remove `canSend: Boolean`
- Add `pendingCount: Int = 0`

### Step 4: Update `ChatViewModel`
- File: `feature/chat/ChatViewModel.kt`
- Add `private val pendingMessages = Channel<String>(Channel.UNLIMITED)`
- Rewrite `sendMessage()` with queue path (streaming) + non-streaming path
- Pass `pendingMessages` to `sendMessageUseCase.execute()`
- Handle `UserMessageInjected` in `handleChatEvent()`
- Update `finishStreaming()`: drain channel, save abandon system message if needed, reset `pendingCount`
- Remove all `canSend` writes from `sendMessage()`, `streamWithExistingMessage()`, `finishStreaming()`, `handleError()`, `initialize()`

### Step 5: Update `ChatScreen` / `ChatInput`
- File: `feature/chat/ChatScreen.kt`
- Rewrite `ChatInput` to show Stop and Send as independent buttons
- Stop button: `CircularProgressIndicator` as icon (spinning), visible only during streaming
- Send button: always visible, enabled when text not blank and provider configured
- `OutlinedTextField.enabled` = `true` always
- Remove `canSend` parameter, replace with `hasConfiguredProvider`

### Step 6: Fix compilation errors from `canSend` removal
- Search for all references to `canSend` across the codebase and remove/update them

## Test Strategy

### Layer 1A -- Unit Tests

**`SendMessageUseCaseQueueTest`** (`app/src/test/kotlin/.../feature/chat/usecase/`):
- Test: message sent to channel is drained at next iteration boundary (no tool calls path); loop continues and AI responds to injected message
- Test: message sent to channel is drained at next iteration boundary (tool calls path); injected message included in next `getMessagesSnapshot()`
- Test: multiple messages queued; all are drained in order, all `UserMessageInjected` events emitted
- Test: no pending messages -> `ResponseComplete` emitted as before (regression)
- Test: empty channel at start -> no regression to existing behavior

**`ChatViewModelConcurrentInputTest`**:
- Test: `sendMessage()` while `isStreaming = true` saves message to DB and sends to channel; `pendingCount` increments
- Test: receiving `UserMessageInjected` event decrements `pendingCount`
- Test: `stopGeneration()` with queued messages saves system abandon note to DB and resets `pendingCount` to 0
- Test: `stopGeneration()` with no queued messages does not save abandon note (regression)

### Layer 1C -- Screenshot Tests
- Add a screenshot of `ChatInput` during streaming showing both Stop (spinning) and Send buttons side by side.

### Layer 2 -- adb Visual Verification
Flow to add to `docs/testing/strategy.md`:

**Flow 6-1: Concurrent input during streaming**
1. Start a chat, send message A
2. While AI is streaming, type message B and tap Send
3. Verify B appears in the chat immediately as a user bubble
4. Wait for AI to finish responding to A, then verify AI responds to B
5. Verify Stop and Send buttons are both visible and independent during streaming

**Flow 6-2: Stop with queued message**
1. Start a chat, send message A
2. While AI is streaming, type message B and tap Send
3. Tap Stop
4. Verify AI partial response is saved and visible
5. Verify message B is visible in chat
6. Send message C
7. Verify AI responds to C (not to B)

## Data Flow

### Normal injection flow

```
User taps Send (streaming active)
  -> ChatViewModel.sendMessage()
  -> messageRepository.addMessage(userMsg)          [DB write]
  -> _uiState: messages += userMsg, pendingCount++  [UI update]
  -> pendingMessages.trySend(text)                  [channel signal]

(agent loop, current iteration ends)
  -> SendMessageUseCase: drainPendingMessages()
  -> channel.tryReceive() returns text
  -> send(ChatEvent.UserMessageInjected(text))
  -> loop continues to next iteration

(next iteration)
  -> messageRepository.getMessagesSnapshot()  [reads injected message from DB]
  -> API call includes the new user message
  -> AI responds to both messages

ChatViewModel receives UserMessageInjected
  -> _uiState: pendingCount--
```

### Stop with queued messages flow

```
User sends A -> streaming starts
User sends B -> queued (saved to DB + channel)
User presses Stop
  -> streamingJob.cancel()
  -> CancellationException handler:
       savePartialResponse(sessionId, accumulatedText)  [AI partial saved to DB]
       finishStreaming(sessionId)
         -> drain channel: finds B
         -> save System message: "...queued messages can be ignored..."
         -> pendingCount = 0

DB state:
  User: A -> AI: (partial) -> User: B -> System: (abandon note)

User later sends C:
  -> new loop starts
  -> getMessagesSnapshot() reads: A, partial, B, system note, C
  -> AI understands B is abandoned, responds to C
```

## Alternatives Considered

### Alternative A: Interrupt current stream, restart with new message
- Cancels the current streaming response, saves partial text, then starts a new loop with both the partial response and new message.
- Rejected: loses the rest of the in-progress AI response; complex partial-state handling; worse UX when the AI was mid-sentence.

### Alternative B: ViewModel-controlled loop (no Channel)
- ViewModel calls a single-iteration version of the use case, then decides whether to loop based on response type and pending messages.
- Rejected: would require a larger refactor of `SendMessageUseCase`; the current `channelFlow`-based design is clean and this RFC only needs a small addition.

### Alternative C: Discard queued messages on Stop
- When the user presses Stop, delete queued messages from DB and UI.
- Rejected: destructive; the user may want to see what they typed. Keeping messages in DB with an abandon note is more transparent.

## Change History

| Date | Version | Change | Author |
|------|---------|--------|--------|
| 2026-02-28 | 0.1 | Initial draft | TBD |
| 2026-02-28 | 0.2 | Updated after discussion: Stop saves partial + abandon note; Stop and Send are independent buttons; rapid double-send merges as multiple USER messages | TBD |
