# RFC-039: Bug Fix & UI Polish Pass

## Document Information
- **RFC ID**: RFC-039
- **Related PRD**: [FEAT-039 (Bug Fix & UI Polish)](../../prd/features/FEAT-039-bugfix-polish.md)
- **Created**: 2026-03-01
- **Last Updated**: 2026-03-01
- **Status**: Completed
- **Author**: TBD

## Overview

### Background

After the initial implementation of features through RFC-038, user testing revealed 15 issues ranging from critical bugs (Google OAuth failure, file browser not working) to UX gaps (no attachment button, no tool parameter display). This RFC documents the technical changes made to address all 15 issues in a single coordinated pass.

### Goals

1. Fix 8 functional bugs across chat, auth, file browser, search, scheduling, and tools
2. Improve UX in 7 areas with better visual feedback, controls, and navigation affordances
3. Maintain all existing tests passing, update tests affected by behavior changes
4. Zero database schema changes -- all fixes are code-level only

### Non-Goals

- New feature development beyond fixing reported issues
- Database migrations
- New external dependencies
- Full ActivityResultLauncher wiring for attachment pickers (placeholder only)

## Technical Design

### Changed Files Overview

```
app/
├── build.gradle.kts                              # MODIFIED (test config)
├── src/main/
│   ├── AndroidManifest.xml                        # MODIFIED (network security)
│   ├── res/xml/
│   │   └── network_security_config.xml            # NEW
│   └── kotlin/com/oneclaw/shadow/
│       ├── data/
│       │   ├── security/
│       │   │   └── GoogleAuthManager.kt           # MODIFIED (6 sub-fixes)
│       │   └── storage/
│       │       └── UserFileStorage.kt             # MODIFIED (rootDir)
│       ├── feature/
│       │   ├── agent/
│       │   │   ├── AgentDetailScreen.kt           # MODIFIED (behavior section)
│       │   │   ├── AgentDetailViewModel.kt        # MODIFIED (save logic)
│       │   │   ├── AgentUiState.kt                # MODIFIED (hasRuntimeChanges)
│       │   │   └── usecase/
│       │   │       └── CreateAgentUseCase.kt      # MODIFIED (new params)
│       │   ├── bridge/
│       │   │   └── BridgeSettingsScreen.kt        # MODIFIED (cards, text)
│       │   ├── chat/
│       │   │   ├── ChatScreen.kt                  # MODIFIED (attachment, tool card)
│       │   │   └── ChatViewModel.kt               # MODIFIED (flush condition)
│       │   ├── schedule/
│       │   │   ├── ScheduledTaskEditViewModel.kt  # MODIFIED (alarm check)
│       │   │   └── ScheduledTaskListScreen.kt     # MODIFIED (clickable, icon)
│       │   ├── search/usecase/
│       │   │   └── SearchHistoryUseCase.kt        # MODIFIED (5s buffer, logs)
│       │   ├── settings/
│       │   │   ├── GoogleAuthScreen.kt            # REWRITTEN
│       │   │   └── GoogleAuthViewModel.kt         # MODIFIED (edit mode)
│       │   └── tool/
│       │       ├── ToolManagementScreen.kt        # MODIFIED (categories)
│       │       └── ToolManagementViewModel.kt     # MODIFIED (categorize, refresh)
│       └── tool/engine/
│           └── ToolRegistry.kt                    # MODIFIED (version flow)
└── src/test/kotlin/com/oneclaw/shadow/feature/
    ├── schedule/
    │   └── ScheduledTaskEditViewModelTest.kt      # MODIFIED
    └── search/usecase/
        └── SearchHistoryUseCaseTest.kt            # MODIFIED
```

## Detailed Design

### Fix 1: Daily Log Not Flushed on Session Switch

**File**: `ChatViewModel.kt:78`

**Root Cause**: The session switch condition `if (previousSessionId != null && sessionId != null && ...)` required the *new* sessionId to be non-null. When switching to "New Conversation" (sessionId = null), the flush was skipped.

**Fix**: Remove the `sessionId != null` check.

```kotlin
// Before
if (previousSessionId != null && sessionId != null && previousSessionId != sessionId) {

// After
if (previousSessionId != null && previousSessionId != sessionId) {
```

**Impact**: `memoryTriggerManager?.onSessionSwitch(previousSessionId)` is now called whenever leaving a session, regardless of whether the destination is a named session or a new conversation.

---

### Fix 2: Wake Lock Explanation Text

**File**: `BridgeSettingsScreen.kt`

**Change**: Added a `Text` composable below the Wake Lock switch explaining its purpose and battery impact.

```kotlin
Text(
    text = "Keeps the bridge service alive when the screen is off. " +
        "Required for reliable message delivery, but increases battery usage.",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
)
```

---

### Fix 3: Google OAuth Flow (6 Sub-Fixes)

**Files**: `GoogleAuthManager.kt`, `AndroidManifest.xml`, `network_security_config.xml` (new)

#### 3A: Network Security Config
- Created `res/xml/network_security_config.xml` allowing cleartext traffic to `127.0.0.1` (needed for the loopback OAuth redirect).
- Referenced in `AndroidManifest.xml` via `android:networkSecurityConfig`.

#### 3B: Loopback Server Binding
```kotlin
// Before
val serverSocket = ServerSocket(0)

// After
val serverSocket = withContext(Dispatchers.IO) {
    ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
}
```
Explicit `127.0.0.1` binding matches the oneclaw-1 reference implementation and prevents binding to `0.0.0.0`.

#### 3C: Token Exchange Retry
Added retry loop (3 attempts, 3-second delay) around `exchangeCodeForTokens()` + `fetchUserEmail()`. The first attempt often fails because the app is still backgrounded when the browser redirects back, and Android may restrict network access.

```kotlin
repeat(3) { attempt ->
    if (attempt > 0) {
        Log.d(TAG, "Token exchange retry $attempt after: ${lastError?.message}")
        delay(3000)
    }
    try {
        val tokens = exchangeCodeForTokens(...)
        val email = fetchUserEmail(tokens.accessToken)
        // save and return success
    } catch (e: UnknownHostException) { lastError = e }
      catch (e: SocketTimeoutException) { lastError = e }
      catch (e: Exception) { lastError = e }
}
```

#### 3D: NonCancellable Wrapping
Wrapped the critical auth code receive + token exchange flow with `withContext(NonCancellable)` to prevent Activity recreation from cancelling the coroutine mid-flow.

#### 3E: Browser Intent Fix
- Removed `FLAG_ACTIVITY_NEW_TASK` from the browser intent.
- Launches browser on `Dispatchers.Main` to avoid context issues.

#### 3F: Specific Error Messages
```kotlin
val errorMsg = when (lastError) {
    is UnknownHostException -> "Network unavailable. Check your internet connection and try again."
    is SocketTimeoutException -> "Connection timed out. Check your internet connection and try again."
    else -> lastError?.message ?: "Authorization failed"
}
```

#### Additional Refactoring
- `waitForAuthCode()` now returns `String?` (nullable) instead of throwing on failure.
- Extracted `parseAuthCode()` method for cleaner URL parsing.
- Response check `response.isSuccessful` added before parsing token JSON.
- `Json` instance reused as a class-level `val json = Json { ignoreUnknownKeys = true }`.
- ServerSocket is closed in a `finally` block with `NonCancellable`.

---

### Fix 4: Google Auth Screen Rewrite

**Files**: `GoogleAuthScreen.kt`, `GoogleAuthViewModel.kt`

#### New UI Components

1. **StatusCard** -- Shows "Connected: email@example.com" or "Not connected" with description of what Google Workspace plugins provide.

2. **PermissionsCard** -- Lists all requested OAuth scopes (Gmail, Calendar, Tasks, Contacts, Drive, Docs, Sheets, Slides, Forms) with descriptions.

3. **Unverified App Warning Card** -- Explains the "Google hasn't verified this app" warning and how to proceed safely.

4. **SetupInstructions** -- 9-step numbered guide for GCP project setup:
   - Go to console.cloud.google.com (clickable link)
   - Enable 9 specific APIs
   - Configure OAuth consent screen
   - Set branding, publish app
   - Add 11 OAuth scopes
   - Create Desktop OAuth client
   - Copy Client ID and Secret

5. **CredentialsSection** -- Three-state display:
   - **Signed in**: Shows connected status, "Disconnect" button, "Change OAuth Credentials" button
   - **Has credentials, not signed in**: Shows "Authorize with Google" button with loading state, retry hint, "Change OAuth Credentials" button
   - **No credentials / editing**: Shows input fields with password visibility toggle, Save/Cancel buttons

6. **Error display** -- Uses `errorContainer` Card instead of plain error-colored text.

#### ViewModel Changes

- Added `editingCredentials: Boolean` to track credential editing mode
- Added `dirty: Boolean` to track unsaved changes to credential fields
- Added `startEditingCredentials()`, `cancelEditingCredentials()`, `clearError()` methods
- `onClientIdChanged` / `onClientSecretChanged` now set `dirty = true`
- `saveCredentials()` resets `editingCredentials` and `dirty`

---

### Fix 5: Built-in Agent Runtime Settings

**Files**: `AgentDetailScreen.kt`, `AgentDetailViewModel.kt`, `AgentUiState.kt`

**Problem**: Built-in agents had Web Search toggle disabled (`enabled = !uiState.isBuiltIn`), and the Save button was hidden for built-in agents.

**Fix**:
- Removed `enabled = !uiState.isBuiltIn` from Web Search switch and its parent Row's `clickable`.
- Added `hasRuntimeChanges` computed property to `AgentDetailUiState`:
  ```kotlin
  val hasRuntimeChanges: Boolean
      get() = webSearchEnabled != savedWebSearchEnabled ||
          temperature != savedTemperature ||
          maxIterations != savedMaxIterations
  ```
- Save button shown when `!uiState.isBuiltIn || uiState.hasRuntimeChanges`.
- `hasUnsavedChanges` for existing agents now delegates to `hasRuntimeChanges` for the runtime fields.

**Save logic change**: When saving a built-in agent, the ViewModel preserves immutable fields from `originalAgent`:
```kotlin
val updated = Agent(
    id = state.agentId!!,
    name = if (state.isBuiltIn) orig.name else state.name.trim(),
    description = if (state.isBuiltIn) orig.description else ...,
    systemPrompt = if (state.isBuiltIn) orig.systemPrompt else ...,
    // Runtime fields always from state
    temperature = state.temperature,
    maxIterations = state.maxIterations,
    webSearchEnabled = state.webSearchEnabled,
    isBuiltIn = orig.isBuiltIn,
    ...
)
```

---

### Fix 6: Temperature & Max Iterations UI

**Files**: `AgentDetailScreen.kt`, `CreateAgentUseCase.kt`

Added a "BEHAVIOR" section below Web Search in the agent detail screen:

- **Temperature Slider**: Range 0.0-2.0, 20 steps, displays current value. Description: "Lower values produce more focused output; higher values are more creative."
- **Max Iterations TextField**: `OutlinedTextField` with number keyboard, validation 1-100, supporting text showing range or error.

`CreateAgentUseCase` updated to accept `webSearchEnabled`, `temperature`, `maxIterations` parameters and pass them to the Agent constructor.

---

### Fix 7: Attachment Button & Picker Integration

**File**: `ChatScreen.kt`

- Added `onAttachmentClick` and `hasPendingAttachments` parameters to `ChatInput`.
- Added attachment button (AttachFile icon) in a circular container next to the skill button.
- When `uiState.pendingAttachments` is not empty, `AttachmentPreviewRow` is displayed above the input.
- When `showAttachmentPicker` is true, `AttachmentPickerSheet` is displayed.
- Send button condition changed from `text.isNotBlank() && hasConfiguredProvider` to `(text.isNotBlank() || hasPendingAttachments) && hasConfiguredProvider`.

Note: `ActivityResultLauncher` callbacks in `AttachmentPickerSheet` are placeholder (`{ /* Caller wires externally */ }`).

---

### Fix 8: Tool Call Parameter Display

**File**: `ChatScreen.kt` (ToolCallCard composable)

Rewrote `ToolCallCard` from a simple Row to a Column with expand/collapse:

```kotlin
var expanded by remember { mutableStateOf(false) }
val hasInput = !toolInput.isNullOrBlank()
```

- Card is clickable only when `hasInput` is true.
- Collapsed state shows tool name with expand/collapse icon.
- Expanded state shows `toolInput` in monospace `bodySmall` font, truncated to 20 lines with `TextOverflow.Ellipsis`.

---

### Fix 9: ToolRegistry Version Flow

**File**: `ToolRegistry.kt`

Added a `StateFlow<Int>` version counter that increments on every structural change:

```kotlin
@PublishedApi
internal val _version = MutableStateFlow(0)
val version: StateFlow<Int> = _version.asStateFlow()
```

Incremented in `register()`, `unregister()`, and `unregisterByType()` (only when keys were actually removed).

`_version` is annotated `@PublishedApi internal` because `unregisterByType()` is an inline reified function that needs access.

---

### Fix 10: Built-in Tool Category Grouping

**Files**: `ToolManagementViewModel.kt`, `ToolManagementScreen.kt`

#### Data Model
```kotlin
data class BuiltInCategoryUiItem(
    val category: String,
    val tools: List<ToolUiItem>,
    val isExpanded: Boolean = false
)
```

#### Categorization Logic
```kotlin
private fun categorizeBuiltInTools(tools: List<ToolUiItem>): List<BuiltInCategoryUiItem> {
    val categorized = tools.groupBy { tool ->
        val name = tool.name.lowercase()
        when {
            name.startsWith("calendar") -> "Calendar"
            name.startsWith("config") -> "Config"
            name.startsWith("provider") || name.startsWith("model") -> "Provider / Model"
            name.startsWith("agent") -> "Agent"
            name.startsWith("schedule") || name.startsWith("scheduled") -> "Scheduling"
            name.startsWith("file") || name.startsWith("http") || name.startsWith("web") -> "Files & Web"
            name.startsWith("pdf") -> "PDF"
            name.startsWith("js") -> "JS Tools"
            else -> "Other"
        }
    }
    // Ordered display
    val categoryOrder = listOf(
        "Calendar", "Config", "Provider / Model", "Agent",
        "Scheduling", "Files & Web", "PDF", "JS Tools", "Other"
    )
    // ...
}
```

#### UI
- `BuiltInCategoryHeader` composable with expand/collapse chevron and tool count.
- `toggleBuiltInCategoryExpanded()` method to toggle categories.
- Expanded state preserved across reloads via existing state matching.

#### Auto-Refresh
```kotlin
init {
    loadTools()
    viewModelScope.launch {
        toolRegistry.version.drop(1).collect { loadTools() }
    }
}
```

---

### Fix 11: Bridge Settings Card Wrapping

**File**: `BridgeSettingsScreen.kt`

- `ChannelSection` composable now wraps content in `Surface(shape = RoundedCornerShape(16.dp), color = surfaceVariant)`.
- All `HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))` replaced with `Spacer(modifier = Modifier.height(12.dp))`.

---

### Fix 12: File Browser Root Directory

**File**: `UserFileStorage.kt`

```kotlin
// Before
open val rootDir: File
    get() = File(context.filesDir, "user_files").also { it.mkdirs() }

// After
open val rootDir: File
    get() = context.filesDir
```

The `user_files/` subdirectory did not exist and was always empty. Changing to `context.filesDir` exposes all internal files (memory/, daily_logs/, databases, etc.) to the file browser.

---

### Fix 13: Search History Current Message Exclusion

**File**: `SearchHistoryUseCase.kt`

**Root Cause**: When the AI calls `search_history`, the user's message is already saved to the database. With `dateTo = null`, the search used `Long.MAX_VALUE` as the upper bound, including the current message in results.

**Fix**: Added a 5-second buffer constant and apply it when `dateTo` is null:

```kotlin
companion object {
    private const val RECENT_MESSAGE_BUFFER_MS = 5_000L
}

val createdBefore = if (dateTo != null) dateTo
    else System.currentTimeMillis() - RECENT_MESSAGE_BUFFER_MS
```

Added `Log.d` debug logging throughout the search pipeline for production debugging.

---

### Fix 14: Scheduled Task List Clickability

**File**: `ScheduledTaskListScreen.kt`

- Added `clickable(onClick = onEdit)` modifier to `ScheduledTaskItem`'s Row.
- Added `ChevronRight` icon (20.dp) after the Switch as a visual affordance.
- Added required imports: `clickable`, `ChevronRight`, `size`.

---

### Fix 15: Exact Alarm Permission for Edited Tasks

**File**: `ScheduledTaskEditViewModel.kt`

```kotlin
// Before (skipped check for edits)
if (!state.isEditing && !exactAlarmHelper.canScheduleExactAlarms()) {

// After (checks for both new and edited tasks)
if (!exactAlarmHelper.canScheduleExactAlarms()) {
```

---

## Test Changes

### SearchHistoryUseCaseTest

Updated `coVerify` assertions that previously expected `Long.MAX_VALUE` as `createdBefore` to use `any()` matcher, since the actual value is now `System.currentTimeMillis() - 5000` (a dynamic value).

```kotlin
// Before
coVerify { messageDao.searchContent("test", 0L, Long.MAX_VALUE, 50) }

// After
coVerify { messageDao.searchContent("test", 0L, any(), 50) }
```

### ScheduledTaskEditViewModelTest

Updated the test `save does not show dialog when editing existing task` to reflect new behavior:

```kotlin
// Before: asserted dialog NOT shown, save succeeded
assertFalse(state.showExactAlarmDialog)
assertTrue(state.savedSuccessfully)
coVerify(exactly = 1) { updateUseCase(any()) }

// After: asserted dialog IS shown, save blocked
assertTrue(state.showExactAlarmDialog)
assertFalse(state.savedSuccessfully)
coVerify(exactly = 0) { updateUseCase(any()) }
```

### Build Config

Added `isReturnDefaultValues = true` to `app/build.gradle.kts` under `testOptions.unitTests` to prevent `android.util.Log.d()` calls in `SearchHistoryUseCase` from throwing `RuntimeException` in JVM unit tests.

## Compilation & Test Results

- First compilation: 2 errors found and fixed
  1. Missing `import androidx.compose.foundation.layout.size` in `ScheduledTaskListScreen.kt`
  2. `_version` in `ToolRegistry` was `private` but accessed from inline reified function `unregisterByType()` -- changed to `@PublishedApi internal`
- After fixes: `./gradlew compileDebugUnitTestKotlin` passed
- After test fixes: `./gradlew test` passed (all tests green)

## Security Considerations

1. **network_security_config.xml**: Only allows cleartext traffic to `127.0.0.1` for the OAuth loopback redirect. All other hosts still require HTTPS.
2. **OAuth tokens**: Continue to use `EncryptedSharedPreferences`. No changes to token storage security.
3. **File browser root change**: Exposing `context.filesDir` instead of `user_files/` gives visibility to all app-internal files. This is intentional -- the file browser is a user-facing debug/management tool within the app itself, not accessible externally.

## Performance Considerations

- `ToolRegistry.version` StateFlow adds negligible overhead (atomic int increment).
- `categorizeBuiltInTools()` runs in O(n) where n is the number of built-in tools (currently ~30).
- `SearchHistoryUseCase` 5-second buffer has no performance impact.
- OAuth retry loop adds up to 6 seconds of delay only on failure paths.

---

## Round 2: Additional Fixes (Issues 16-25)

### Fix 16: Bridge Settings -- Collapsible Channel Headers

**File**: `BridgeSettingsScreen.kt`

Refactored `ChannelSection` to be a single-line collapsible header:
- Channel name on the left, enable Switch on the right
- Removed redundant "Enable X" label (the title already says the channel name)
- When enabled (switch ON): expand to show configuration fields + setup guide
- When disabled (switch OFF): collapse to single header line
- Uses `AnimatedVisibility` for smooth expand/collapse animation

### Fix 17: Bridge Settings -- Setup Instructions per Channel

**File**: `BridgeSettingsScreen.kt`

Added `SetupGuide` composable inside each expanded channel section:
- Clickable "Setup guide" text that toggles numbered step list
- Channel-specific instructions:
  - **Telegram**: @BotFather, /newbot, @userinfobot
  - **Discord**: Developer Portal, Bot token, Message Content Intent, OAuth2, Developer Mode
  - **Slack**: api.slack.com/apps, OAuth scopes, App-Level Token, Socket Mode, Event Subscriptions
  - **Matrix**: Register bot account, Element access token, invite to rooms
  - **LINE**: developers.line.biz, provider/channel, Channel Access Token, Channel Secret
  - **Web Chat**: Port selection, optional access token, browser access URL

### Fix 18: Wake Lock Warning Styling

**File**: `BridgeSettingsScreen.kt`

Changed wake lock explanation from plain `onSurfaceVariant` text to:
- `Row` layout with `Icons.Default.Warning` icon + text
- Both icon and text use `MaterialTheme.colorScheme.error` color

### Fix 19: Google OAuth Screen Crash

**File**: `GoogleAuthScreen.kt`

**Root Cause**: `pushLink(LinkAnnotation.Url("https://console.cloud.google.com"))` crashes on Compose BOM 2024.12.01 because the `LinkAnnotation` API is not fully stable.

**Fix**: Replaced with stable `pushStringAnnotation` + `ClickableText` pattern:
```kotlin
pushStringAnnotation(tag = "URL", annotation = consoleUrl)
// ...
ClickableText(
    text = step1Text,
    onClick = { offset ->
        step1Text.getStringAnnotations(tag = "URL", start = offset, end = offset)
            .firstOrNull()?.let { ... context.startActivity(Intent(ACTION_VIEW, Uri.parse(it.item))) }
    }
)
```

Also fixed `var stepNumber = 1` side-effect during composition by passing step numbers explicitly as parameters.

### Fix 20: Delete Credentials Button

**Files**: `GoogleAuthManager.kt`, `GoogleAuthViewModel.kt`, `GoogleAuthScreen.kt`

- `GoogleAuthManager.clearAllCredentials()`: Clears both OAuth client credentials (client ID + secret) and all tokens/email from EncryptedSharedPreferences.
- `GoogleAuthViewModel.deleteCredentials()`: Calls `clearAllCredentials()` and resets UiState to initial empty state.
- `GoogleAuthScreen`: Added `OutlinedButton` with error color for "Delete Credentials" in both signed-in and has-credentials states.

### Fix 21: Agent Max Iterations -- Slider

**Files**: `AgentDetailScreen.kt`, `AgentUiState.kt`, `AgentDetailViewModel.kt`, `SendMessageUseCase.kt`

- Replaced `OutlinedTextField` with `Slider` (range 1-200, steps = 198)
- At 200, displays "Unlimited" and stores as `null`
- When `maxIterations` is `null`, slider shows at position 25 (sensible default)
- Removed `maxIterationsError` from `AgentUiState` (slider enforces valid range)
- Removed validation logic from `AgentDetailViewModel.updateMaxIterations()`
- Removed `maxIterationsError` check from `saveAgent()`
- `SendMessageUseCase`: Changed `agent.maxIterations ?: MAX_TOOL_ROUNDS` to `agent.maxIterations ?: Int.MAX_VALUE` (null = truly unlimited)

### Fix 22: Attachment Picker -- Wire ActivityResultLauncher

**File**: `ChatScreen.kt`, `file_paths.xml`

Registered 4 `rememberLauncherForActivityResult` instances in `ChatScreen`:
- **Photo**: `ActivityResultContracts.PickVisualMedia()` with `ImageOnly` -> `viewModel.addAttachment(uri)`
- **Video**: `ActivityResultContracts.PickVisualMedia()` with `VideoOnly` -> `viewModel.addAttachment(uri)`
- **Camera**: `ActivityResultContracts.TakePicture()` -> creates temp file in `cache/camera_photos/`, gets URI via `FileProvider` -> `viewModel.addCameraPhoto(file)`
- **File**: `ActivityResultContracts.GetContent()` with `"*/*"` -> `viewModel.addAttachment(uri)`

Added `<cache-path name="camera_photos" path="camera_photos/" />` to `file_paths.xml` for camera photo temp files.

### Fix 23: Attachment Picker -- Dark Mode Status Bar

**File**: `AttachmentPickerSheet.kt`

Set explicit scrim color on `ModalBottomSheet`:
```kotlin
ModalBottomSheet(
    onDismissRequest = onDismiss,
    scrimColor = Color.Black.copy(alpha = 0.32f)
)
```
This prevents the default scrim from interfering with status bar icon colors in dark mode.

### Fix 24: Tool Management -- Google Product Names

**File**: `ToolManagementViewModel.kt`

Updated `categorizeBuiltInTools()` mapping to use full Google product names:
- `"calendar"` -> `"Google Calendar"`
- `"gmail"` -> `"Gmail"`
- `"drive"` -> `"Google Drive"`
- `"docs"/"document"` -> `"Google Docs"`
- `"sheets"/"spreadsheet"` -> `"Google Sheets"`
- `"slides"/"presentation"` -> `"Google Slides"`
- `"forms"` -> `"Google Forms"`
- `"contacts"/"people"` -> `"Google Contacts"`
- `"tasks"` -> `"Google Tasks"`

Updated `categoryOrder` to include all Google product categories in logical order.

### Fix 25: Tool Management -- Badge Layout Fix

**File**: `ToolManagementScreen.kt`

Restructured `ToolListItem` layout:
- Tool name on its own line with `maxLines = 1` and `TextOverflow.Ellipsis`
- `SourceBadge` moved to second line (same row as description)
- Badge no longer competes with tool name for horizontal space

## Change History

| Date | Version | Changes | Owner |
|------|---------|---------|-------|
| 2026-03-01 | 1.0 | Initial version (15 issues fixed) | - |
| 2026-03-01 | 2.0 | Round 2 (10 additional issues: 16-25) | - |
