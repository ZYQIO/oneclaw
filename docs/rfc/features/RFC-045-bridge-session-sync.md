# RFC-045: Bridge-App Session Synchronization

## Document Information
- **RFC ID**: RFC-045
- **Related PRD**: [FEAT-045 (Bridge Session Sync)](../../prd/features/FEAT-045-bridge-session-sync.md)
- **Created**: 2026-03-01
- **Last Updated**: 2026-03-01
- **Status**: Completed
- **Author**: TBD

## Overview

### Background

After FEAT-041, the bridge routes all incoming messages to the app's most recently updated session. The bridge and the app therefore share one active session under normal use. However, when the user sends `/clear` via Telegram, `MessagingChannel.processInboundMessage()` calls `conversationMapper.createNewConversation()` and silently returns. The newly created session becomes the most recent session in the database, but the app's ChatScreen has no notification of this and continues to display the old session.

This RFC describes a minimal fix: a `SharedFlow`-based in-process event bus in `BridgeStateTracker` that emits the new session ID whenever the bridge creates one, with a `LaunchedEffect` in `ChatScreen` that subscribes and reinitializes the ViewModel.

### Goals

1. Emit a session-switch event from the bridge layer when `/clear` creates a new session.
2. Subscribe in `ChatScreen` and call `viewModel.initialize(sessionId)` upon receiving the event.
3. Keep the change minimal: no new modules, no new data classes, no DB schema changes.
4. Soft-delete the previous session before creating the new one if it contains no messages, preventing empty sessions from accumulating in the session list.
5. Track the currently displayed session in `BridgeStateTracker` so the bridge routes messages correctly when the user manually switches sessions in the app without sending a message.
6. Make "New Conversation" in the app create the session eagerly (consistent with bridge `/clear` behavior), and clean up the previous empty session.
7. Generate a session title for bridge-initiated sessions (phase 1 truncated title + phase 2 AI title), matching the behavior of app-initiated sessions.

### Non-Goals

- Any new persistence layer or inter-process communication.
- Notification when the app is not in the foreground (the existing Room Flow already handles list refresh; the session switch will apply on next foreground).
- Changes to navigation graph or back stack.
- Changes to `SessionListViewModel` (the drawer list updates automatically via Room Flow).

## Technical Design

### Changed Files Overview

```
bridge/src/main/kotlin/com/oneclaw/shadow/bridge/
└── BridgeStateTracker.kt                            # MODIFIED (add SharedFlow + activeAppSessionId)
    channel/
    └── MessagingChannel.kt                          # MODIFIED (/clear branch emits event)
app/src/main/kotlin/com/oneclaw/shadow/
├── di/
│   └── BridgeModule.kt                              # MODIFIED (inject new deps into BridgeAgentExecutorImpl)
├── feature/bridge/
│   ├── BridgeAgentExecutorImpl.kt                   # MODIFIED (title generation)
│   └── BridgeConversationManagerImpl.kt             # MODIFIED (empty session cleanup + routing fix)
└── feature/chat/
    ├── ChatScreen.kt                                # MODIFIED (LaunchedEffect subscriber)
    └── ChatViewModel.kt                             # MODIFIED (newConversation() + setActiveAppSession)
```

## Detailed Design

### Change 1: BridgeStateTracker -- Add `newSessionFromBridge` SharedFlow

`BridgeStateTracker` is an `object` (singleton) that already holds observable state shared between the bridge service and the app UI. Adding a `SharedFlow` here is consistent with its existing role.

```kotlin
// BridgeStateTracker.kt -- additions only

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object BridgeStateTracker {

    // ... existing fields and methods unchanged ...

    private val _newSessionFromBridge = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val newSessionFromBridge: SharedFlow<String> = _newSessionFromBridge.asSharedFlow()

    fun emitNewSessionFromBridge(sessionId: String) {
        _newSessionFromBridge.tryEmit(sessionId)
    }
}
```

Design decisions:
- `extraBufferCapacity = 1`: buffers one event so the emission does not drop if no collector is active at the exact moment of emission (e.g., ChatScreen is mid-composition).
- `tryEmit`: fire-and-forget; the bridge does not need a confirmation that the UI received the event.
- `SharedFlow` (not `StateFlow`): a session-switch is a one-time event, not a persistent state value. `StateFlow` would re-deliver the last session ID to every new collector, causing an unwanted navigation on each recomposition.

### Change 2: MessagingChannel -- Emit Event on `/clear`

In `processInboundMessage()`, the `/clear` branch already calls `conversationMapper.createNewConversation()`. Add one line after that call:

```kotlin
// MessagingChannel.kt -- /clear branch (step 4)

if (msg.text.trim() == "/clear") {
    val newConversationId = conversationMapper.createNewConversation()
    BridgeStateTracker.emitNewSessionFromBridge(newConversationId)   // <-- new line
    val clearMessage = BridgeMessage(
        content = "Conversation cleared. Starting a new conversation.",
        timestamp = System.currentTimeMillis()
    )
    runCatching { sendResponse(msg.externalChatId, clearMessage) }
    updateChannelState(newMessage = true)
    return
}
```

No other changes to `MessagingChannel`.

### Change 3: ChatScreen -- Subscribe and Reinitialize

Add a `LaunchedEffect` near the top of the `ChatScreen` composable, alongside the existing `LaunchedEffect` blocks, to collect from `BridgeStateTracker.newSessionFromBridge`:

```kotlin
// ChatScreen.kt -- inside ChatScreen composable

LaunchedEffect(Unit) {
    BridgeStateTracker.newSessionFromBridge.collect { sessionId ->
        viewModel.initialize(sessionId)
    }
}
```

Design decisions:
- `LaunchedEffect(Unit)`: launches once per composition lifetime, which is the correct scope for a persistent subscription.
- `viewModel.initialize(sessionId)`: this is the same method called when the user manually selects a session from the drawer. Reusing it ensures identical behavior: the ViewModel loads messages for the new session and updates `uiState.sessionId`.
- No navigation change is needed because `ChatScreen` is already the current screen.

### Change 4: BridgeConversationManagerImpl -- Clean Up Empty Previous Session

**Problem**: Every `/clear` command eagerly creates a session record in the database even though it contains no messages. If the user sends `/clear` repeatedly without having a conversation in between, empty sessions accumulate in the session list.

**Context**: Unlike the in-app "New Conversation" flow (which is lazy -- no DB record is created until the first message is sent), the bridge must create the session eagerly so that `viewModel.initialize(sessionId)` in `ChatScreen` can immediately look it up via `sessionRepository.getSessionById()`.

**Solution**: In `createNewConversation()`, check whether the current most-recent session is empty before creating the new one. If it is, soft-delete it first. This ensures that at most one empty bridge session exists at any time.

```kotlin
// BridgeConversationManagerImpl.kt -- updated createNewConversation()

override suspend fun createNewConversation(): String {
    // If the previous session has no messages, soft-delete it to avoid accumulation
    val prevId = sessionRepository.getMostRecentSessionId()
    if (prevId != null) {
        val prevSession = sessionRepository.getSessionById(prevId)
        if (prevSession != null && prevSession.messageCount == 0) {
            sessionRepository.softDeleteSession(prevId)
        }
    }

    val agentId = resolveAgentId()
    val now = System.currentTimeMillis()
    val session = Session(
        id = UUID.randomUUID().toString(),
        title = "Bridge Conversation",
        currentAgentId = agentId,
        messageCount = 0,
        lastMessagePreview = null,
        isActive = false,
        deletedAt = null,
        createdAt = now,
        updatedAt = now
    )
    val created = sessionRepository.createSession(session)
    return created.id
}
```

Design decisions:
- Uses `session.messageCount` (a denormalized field on the `Session` model) rather than adding a new `MessageRepository` query. This field is set to `0` when the bridge creates the session and is only incremented when messages are added; a bridge session created by `/clear` that has never received a message will always have `messageCount == 0`.
- Uses `sessionRepository.softDeleteSession()` (soft delete via `deleted_at` timestamp), consistent with the rest of the app's session deletion pattern. The soft-deleted session disappears from both the session list and the `getMostRecentSessionId()` query (which already filters `WHERE deleted_at IS NULL`).
- No change to the `SessionRepository` or `MessageRepository` interfaces is required -- `getSessionById`, `getMostRecentSessionId`, and `softDeleteSession` all already exist.

### Change 5: Correct Bridge Routing on App-Side Session Switch

**Problem**: `BridgeConversationManagerImpl.getActiveConversationId()` originally returned only `sessionRepository.getMostRecentSessionId()` (ordered by `updated_at DESC`). When the user switched to an older session in the app without sending a message, `updated_at` was not touched, so the bridge continued routing to the previously newer session.

**Solution**: Add an in-memory `activeAppSessionId` to `BridgeStateTracker`. `ChatViewModel.initialize()` updates it on every session switch. `getActiveConversationId()` prefers the in-memory value, falling back to the DB query when the app has not set one (e.g., cold start before the user opens ChatScreen).

```kotlin
// BridgeStateTracker.kt -- additions

private val _activeAppSessionId = MutableStateFlow<String?>(null)
val activeAppSessionId: StateFlow<String?> = _activeAppSessionId.asStateFlow()

fun setActiveAppSession(sessionId: String?) {
    _activeAppSessionId.value = sessionId
}
```

```kotlin
// ChatViewModel.kt -- in initialize()

BridgeStateTracker.setActiveAppSession(sessionId)
```

```kotlin
// BridgeConversationManagerImpl.kt -- updated getActiveConversationId()

override suspend fun getActiveConversationId(): String? {
    return BridgeStateTracker.activeAppSessionId.value
        ?: sessionRepository.getMostRecentSessionId()
}
```

### Change 6: Eager Session Creation for "New Conversation" in App

**Problem**: Clicking "New Conversation" in the app called `initialize(null)`, setting `sessionId = null` and `BridgeStateTracker.activeAppSessionId = null`. The bridge then fell back to `getMostRecentSessionId()` and routed messages to the last session that had messages, not the new blank session the user just created.

**Solution**: Add `ChatViewModel.newConversation()` which eagerly creates a DB session (same as bridge `/clear`) and calls `initialize(newSession.id)`. The session is immediately registered in `BridgeStateTracker`, so the bridge routes to it right away. The empty-session cleanup logic (soft-delete if `messages.isEmpty()`) is also applied here for consistency.

```kotlin
// ChatViewModel.kt

fun newConversation() {
    viewModelScope.launch {
        val currentSessionId = _uiState.value.sessionId
        if (currentSessionId != null && _uiState.value.messages.isEmpty()) {
            sessionRepository.softDeleteSession(currentSessionId)
        }
        val session = createSessionUseCase(agentId = _uiState.value.currentAgentId)
        isFirstMessage = true
        firstUserMessageText = null
        initialize(session.id)
    }
}
```

`ChatScreen` now calls `viewModel.newConversation()` instead of `viewModel.initialize(null)` from the drawer's "New Conversation" button.

The `sendMessage()` lazy-creation fallback (`if (sessionId == null)`) is retained as a safety path for the cold-start case (`init { initialize(null) }`). The phase-1 title generation is moved outside the `sessionId == null` block so it fires for both the lazy path and the eager path:

```kotlin
// sendMessage() -- simplified structure

var sessionId = _uiState.value.sessionId
if (sessionId == null) {
    // Fallback: lazy creation for cold-start state
    val session = createSessionUseCase(agentId = _uiState.value.currentAgentId)
    sessionId = session.id
    _uiState.update { it.copy(sessionId = sessionId) }
    isFirstMessage = true
}
// Phase 1 title applies to both lazy and eager paths
if (isFirstMessage && firstUserMessageText == null) {
    val titleSource = text.ifBlank { ... }
    val truncatedTitle = generateTitleUseCase.generateTruncatedTitle(titleSource)
    sessionRepository.updateTitle(sessionId!!, truncatedTitle)
    firstUserMessageText = text.ifBlank { titleSource }
}
```

### Change 7: Session Title Generation for Bridge-Initiated Sessions

**Problem**: Sessions created by the bridge (via `/clear` or `createNewConversation()`) always kept the title "Bridge Conversation". App-initiated sessions generate a meaningful title (phase 1: truncated user text; phase 2: AI-generated title). Bridge sessions lacked this.

**Solution**: `BridgeAgentExecutorImpl.executeMessage()` checks `session.messageCount == 0` before executing to detect the first message. If it is the first message:
- Phase 1: calls `generateTitleUseCase.generateTruncatedTitle(userMessage)` and `sessionRepository.updateTitle()` immediately.
- Phase 2: after the AI response, calls `generateTitleUseCase.generateAiTitle()` with the response content and model/provider IDs captured from `ChatEvent.ResponseComplete`.

```kotlin
// BridgeAgentExecutorImpl.kt

override suspend fun executeMessage(...): BridgeMessage? {
    val isFirstMessage = (sessionRepository.getSessionById(conversationId)?.messageCount ?: 0) == 0

    if (isFirstMessage) {
        val truncatedTitle = generateTitleUseCase.generateTruncatedTitle(userMessage)
        sessionRepository.updateTitle(conversationId, truncatedTitle)
    }

    // ... execute sendMessageUseCase, capture lastModelId and lastProviderId from ResponseComplete ...

    if (isFirstMessage && content != null && lastModelId != null && lastProviderId != null) {
        generateTitleUseCase.generateAiTitle(
            sessionId = conversationId,
            firstUserMessage = userMessage,
            firstAiResponse = content,
            currentModelId = lastModelId!!,
            currentProviderId = lastProviderId!!
        )
    }
    ...
}
```

`BridgeAgentExecutorImpl` receives two new constructor dependencies: `SessionRepository` and `GenerateTitleUseCase`, injected via `BridgeModule`.

## Testing

### Unit Tests

Modified files and their test coverage:

- `BridgeStateTracker`: no existing unit tests (it is a simple state holder); the new `SharedFlow` and `StateFlow` fields follow the same pattern as existing fields.
- `MessagingChannel` (`MessagingChannelTest`): existing tests cover the `/clear` branch. Update the test to verify that `BridgeStateTracker.newSessionFromBridge` emits the new session ID after a `/clear` message.
- `BridgeConversationManagerImpl`: add a test case for `createNewConversation()` when the previous session has `messageCount == 0` -- verify `softDeleteSession()` is called before the new session is created. Add a second case where `messageCount > 0` -- verify `softDeleteSession()` is NOT called.
- `ChatScreen`: covered by UI/Roborazzi tests; no change to visual layout, so no new screenshot baseline is needed.
- `ChatViewModel`: no existing unit tests for `newConversation()`; the function follows the same pattern as `initialize()` and is exercised by manual verification step 7 below.
- `BridgeAgentExecutorImpl` (`BridgeAgentExecutorImplTest`): existing tests mock `SessionRepository` and `GenerateTitleUseCase`. The test suite already instantiates the executor with both new dependencies (added as part of this RFC). Existing tests set `session.messageCount = 1` to exercise the non-first-message path; add a new case with `messageCount = 0` to verify phase-1 title generation is called.

### Manual Verification

1. Open the app to `ChatScreen`, confirm it shows the current active session.
2. Send `/clear` via Telegram.
3. Within 3 seconds, verify that `ChatScreen` switches to a new, empty session.
4. Send a normal message via Telegram. Verify it appears in the same new session in the app.
5. Manually switch to an older session in the app drawer. Send a message via Telegram. Verify it appears in the older session (not the newer one).
6. Send `/clear` via Telegram twice in a row without sending any message in between. Verify that only one empty session appears in the session list (the first empty session is soft-deleted when the second `/clear` is processed).
7. Tap "New Conversation" in the app drawer. Verify a new empty session is immediately created and displayed. Send a message via Telegram. Verify the message appears in that new session.
8. Send a bridge message into a new session (after `/clear`). Verify the session title changes from "Bridge Conversation" to a truncated version of the first user message, and later updates to an AI-generated title.

## Migration Notes

- No database schema changes.
- No API changes to `BridgeConversationManager`, `SessionRepository`, or any repository interface.
- `BridgeStateTracker` gains four new public members: `newSessionFromBridge` (SharedFlow), `emitNewSessionFromBridge()`, `activeAppSessionId` (StateFlow), and `setActiveAppSession()`. The `ChatScreen` and `ChatViewModel` access these through the existing shared dependency on `BridgeStateTracker`.
- `BridgeConversationManagerImpl.createNewConversation()` gains pre-creation cleanup logic via `softDeleteSession()`. The behavior change is observable only when the previous session has `messageCount == 0`; all other cases are unaffected.
- `BridgeConversationManagerImpl.getActiveConversationId()` now prefers `BridgeStateTracker.activeAppSessionId` over the DB query. This changes routing behavior only when the app has explicitly set an active session; cold-start fallback to `getMostRecentSessionId()` is unchanged.
- `ChatViewModel.initialize()` now calls `BridgeStateTracker.setActiveAppSession(sessionId)` on every session switch. This is a side-effect-only addition; ViewModel UI state behavior is unchanged.
- `ChatViewModel` gains a new `newConversation()` function. `ChatScreen` now calls this instead of `initialize(null)` from the drawer's "New Conversation" button.
- `BridgeAgentExecutorImpl` receives two new constructor dependencies (`SessionRepository`, `GenerateTitleUseCase`), injected via `BridgeModule`. Callers that construct `BridgeAgentExecutorImpl` directly (e.g., unit tests) must provide these dependencies.
