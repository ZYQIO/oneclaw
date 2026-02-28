# Test Report: RFC-001 — Chat Interaction

## Report Information

| Field | Value |
|-------|-------|
| RFC | RFC-001 |
| Commit | `bdea03c` |
| Date | 2026-02-27 |
| Tester | AI (OpenCode) |
| Status | PASS |

## Summary

RFC-001 implements the full chat interaction loop: SSE streaming from OpenAI, Anthropic (with thinking blocks), and Gemini; a multi-turn tool call loop in `SendMessageUseCase`; and the complete Gemini-style chat UI with message bubbles, tool call cards, thinking blocks, streaming cursor, and agent selector. All feasible testing layers were executed.

| Layer | Step | Result | Notes |
|-------|------|--------|-------|
| 1A | JVM Unit Tests | PASS | 245 tests, 0 failures |
| 1B | Instrumented DAO Tests | PASS | 48 tests, 0 failures |
| 1C | Roborazzi Screenshot Tests | PASS | 8 new screenshots |
| 2 | adb Visual Flows | FAIL | Flow 1-1 PASS; Flow 1-2 FAIL — top bar pushed off-screen when keyboard opens |

## Layer 1A: JVM Unit Tests

**Command:** `./gradlew test`

**Result:** PASS

**Test count:** 245 tests, 0 failures

Notable changes:
- `OpenAiAdapterTest.sendMessageStream returns a Flow without throwing` — replaces the obsolete "throws NotImplementedError" test. The method now returns a `Flow<StreamEvent>` and this test verifies it does not throw.
- All existing adapter tests (listModels, testConnection, generateSimpleCompletion) continue to pass.

## Layer 1B: Instrumented Tests

**Command:** `ANDROID_SERIAL=emulator-5554 ./gradlew connectedAndroidTest`

**Result:** PASS

**Device:** Medium_Phone_API_36.1 (AVD) — emulator-5554

**Test count:** 48 tests, 0 failures

No new instrumented tests were added for RFC-001 (chat logic is unit-testable at the adapter level; DAO layer unchanged).

## Layer 1C: Roborazzi Screenshot Tests

**Commands:**
```bash
./gradlew recordRoborazziDebug
./gradlew verifyRoborazziDebug
```

**Result:** PASS

New screenshots recorded in `AgentScreenshotTest` (shared file for RFC-001 and RFC-002):

### ChatTopBar

<img src="screenshots/RFC-001_ChatTopBar.png" width="250">

Visual check: Hamburger menu icon on left; "General Assistant" title with dropdown arrow in center (gold/amber text); Settings gear icon on right.

### ChatInput — empty

<img src="screenshots/RFC-001_ChatInput_empty.png" width="250">

Visual check: Outlined text field with "Message" placeholder; Send icon button is disabled (greyed out) when no text.

### ChatInput — with text

<img src="screenshots/RFC-001_ChatInput_withText.png" width="250">

Visual check: Text "Explain how coroutines work" fills the field; Send icon button is enabled (colored).

### ChatEmptyState

<img src="screenshots/RFC-001_ChatEmptyState.png" width="250">

Visual check: Centered empty state placeholder shown when no messages exist.

### MessageList — conversation

<img src="screenshots/RFC-001_ChatMessageList_conversation.png" width="250">

Visual check: User messages appear as gold/amber rounded bubbles on the right; AI response appears as a surface-colored card on the left with markdown rendering (**bold** text correct); model ID "gpt-4o" shown below AI message with copy/regenerate icons.

### MessageList — with tool call

<img src="screenshots/RFC-001_ChatMessageList_toolCall.png" width="250">

Visual check: User message bubble, then a TOOL_CALL card showing tool name "get_current_time", then a TOOL_RESULT card showing the output, then final AI response.

### MessageList — streaming

<img src="screenshots/RFC-001_ChatMessageList_streaming.png" width="250">

Visual check: User message bubble, then the streaming AI response text appears in an AI bubble (streaming cursor visible).

### MessageList — active tool call

<img src="screenshots/RFC-001_ChatMessageList_activeToolCall.png" width="250">

Visual check: User message bubble, then an active TOOL_CALL card with PENDING status for "read_file" with arguments shown.

## Layer 2: adb Visual Verification

**Result:** PASS (all flows pass after bug fixes)

**Device:** Pixel 6a, Android 16

**Provider:** Anthropic (`claude-sonnet-4-6`, `claude-opus-4-5-20251101` for Flow 1-9)

**Flows executed:** Flow 1-1 through Flow 1-9 (all flows defined in RFC-001)

| Flow | Description | Result | Notes |
|------|-------------|--------|-------|
| Flow 1-1 | Send message — streaming response appears | PASS | |
| Flow 1-2 | Streaming completes — action row and model badge appear | PASS | |
| Flow 1-3 | Stop generation — button reverts, partial text preserved | PASS | |
| Flow 1-4 | Regenerate response | PASS | |
| Flow 1-5 | Keyboard appears — TopAppBar stays visible | PASS | Bug fixed: `adjustNothing` + `imePadding` on ChatInput |
| Flow 1-6 | Long-press copy on user message | PASS | Bug fixed: `DisableSelection` + explicit `interactionSource` on message bubbles |
| Flow 1-7 | Tool call loop — ToolCallCard and ToolResultCard visible | PASS | |
| Flow 1-8 | Error message — error card and Retry button visible | PASS | Minor: error card shows raw JSON; see Issues |
| Flow 1-9 | Thinking block — collapse and expand | PASS | Bug fixed: thinking config added to API request body |

---

### Flow 1-1: Send message — streaming response appears

**Result:** PASS

**Steps and observations:**

**Step 1 — Chat screen opens**

<img src="screenshots/Flow1-1_step1_launch.png" width="280">

TopAppBar visible with hamburger menu, "General Assistant" title with dropdown arrow, and settings icon. Empty state "How can I help you today?" centered. Input field at bottom with Send button. No "No provider configured" snackbar — provider already configured.

**Step 2 — Message typed in input field**

<img src="screenshots/Flow1-1_step2_typed.png" width="280">

"Hello, who are you?" typed in the pill-shaped input field. Send button activates (gold/amber fill). Keyboard visible.

**Step 4a — Immediately after Send (0.5 s): Stop button visible, streaming initiated**

<img src="screenshots/Flow1-1_step4_streaming_stop_visible.png" width="280">

User message bubble appears right-aligned (gold). Below it, the AI response begins (first completed response visible — model was fast). **Stop button (red square icon)** visible in bottom-right input area. Input field disabled ("Message" placeholder, not editable).

**Step 4b — Streaming mid-flight (1.2 s): Markdown rendering + blinking cursor**

<img src="screenshots/Flow1-1_step4_streaming_mid.png" width="280">

Streaming AI bubble shows fully rendered Markdown: H1 heading ("Neural Networks & Backpropagation: A Detailed Explanation"), H2 section heading ("1. The Big Picture"), **bold** and *italic* text rendered correctly. Blinking cursor `|` visible at the end of the in-progress text. Stop button still visible.

**Step 4c — Streaming continues (1.7 s)**

<img src="screenshots/Flow1-1_step4_streaming_initiated.png" width="280">

Earlier completed exchange visible (first "Hello, who are you?" message with its full response and action row). New user message bubble (backpropagation question) visible. Stop button present — streaming in progress for the new message.

**Step 5 — Streaming complete: Send button restored**

Streaming completed within ~4 seconds. Stop button disappeared; Send button (arrow icon, dimmed when field is empty) reappeared.

**Step 6 — Final state: action row and model badge**

<img src="screenshots/Flow1-1_step6_action_row.png" width="280">

End of the completed AI response. Below the message bubble: copy icon, regenerate icon, and model badge **`claude-sonnet-4-6`** visible. Send button (not Stop) in input area confirms streaming is complete.

---

**Additional observations:**
- Markdown rendering works correctly end-to-end: H1/H2 headings, bold, italic, bullet lists all rendered.
- Mathematical notation (e.g., `max(0,z)`) falls back to plain text — expected, as the Markdown library does not support LaTeX.
- Response latency was fast enough that a short "Hello, who are you?" completed before the 1.5 s screenshot window; a longer question was used to reliably capture mid-stream state.

---

### Flow 1-2: Streaming completes — action row and model badge appear

**Result:** PASS

<img src="screenshots/Flow1-2_step2_completed_action_row.png" width="280">

After streaming completes: AI response bubble with text. Below it: copy icon, regenerate icon (circular arrows), and model badge `claude-sonnet-4-6`. Send button (not Stop) confirms streaming finished.

---

### Flow 1-3: Stop generation — button reverts, partial text preserved

**Result:** PASS

**Mid-stream (Stop button visible):**

<img src="screenshots/Flow1-3_step2_streaming_stop_button.png" width="280">

Stop button (red square) visible in bottom-right. AI response streaming — partial essay text visible with title and opening paragraph.

**After tapping Stop:**

<img src="screenshots/Flow1-3_step4_after_stop_partial_text.png" width="280">

Stop button reverted to Send button. Partial AI response preserved in chat. Input field re-enabled.

---

### Flow 1-4: Regenerate response

**Result:** PASS

**Action row before regenerate:**

<img src="screenshots/Flow1-4_step2_action_row_before_regen.png" width="280">

Completed AI response with action row showing copy, regenerate, and model badge.

**Streaming after tapping Regenerate:**

<img src="screenshots/Flow1-4_step3_streaming_after_regen.png" width="280">

Previous response replaced by new streaming response. Stop button visible.

**New response completed:**

<img src="screenshots/Flow1-4_step4_new_response_completed.png" width="280">

New AI response with action row. Model badge `claude-sonnet-4-6` visible.

---

### Flow 1-5: Keyboard appears — TopAppBar stays visible

**Result:** PASS (bug fixed in this session)

**Before keyboard:**

<img src="screenshots/Flow1-5_step1_before_keyboard.png" width="280">

TopAppBar fully visible: hamburger (left), "General Assistant" (center), settings gear (right).

**After keyboard opens:**

<img src="screenshots/Flow1-5_step2_keyboard_open_topbar_visible.png" width="280">

TopAppBar remains fully visible at top. Input field and Send button visible above keyboard. Empty state text visible between TopAppBar and input.

**Fix applied:** `android:windowSoftInputMode="adjustNothing"` in `AndroidManifest.xml` + `Modifier.imePadding()` on `ChatInput`'s `Surface`, replacing the previous `contentWindowInsets = WindowInsets.ime.union(WindowInsets.navigationBars)` on `Scaffold`.

---

### Flow 1-6: Long-press copy on user message

**Result:** PASS (bug fixed in this session)

**Before long-press:**

<img src="screenshots/Flow1-6_step1_before_longpress.png" width="280">

User message bubble (gold, right-aligned) visible in chat.

**After long-press:**

<img src="screenshots/Flow1-6_step2_clipboard_feedback.png" width="280">

Android 13+ OS clipboard confirmation UI appeared at the bottom: copied text preview ("Hello") with Share and Copy-to-clipboard action buttons.

**Fix applied:** Added `DisableSelection {}` wrapper around `Text` in `UserMessageBubble` and `AiMessageBubble`, and supplied explicit `MutableInteractionSource` + `indication = null` to `combinedClickable`. This prevents Android 16's default text-selection long-press from intercepting the event before `onLongClick` fires.

---

### Flow 1-7: Tool call loop — ToolCallCard and ToolResultCard visible

**Result:** PASS

<img src="screenshots/Flow1-7_step3_tool_call_and_result.png" width="280">

ToolCallCard visible showing `get_current_time` with spinner animation. ToolResultCard below it: `get_current_time result (65ms)`. Final AI response: "The current time is **Friday, February 27, 2026 at 8:32 PM PST**."

<img src="screenshots/Flow1-7_step5_response_completed.png" width="280">

Full exchange completed. Action row with copy, regenerate, and `claude-sonnet-4-6` model badge.

---

### Flow 1-8: Error message — error card and Retry button visible

**Result:** PASS

<img src="screenshots/Flow1-8_step5_error_card_with_retry.png" width="280">

Red error card visible: `API error 401: {"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}...}`. **Retry** button on the right. Error icon on the left.

<img src="screenshots/Flow1-8_step6_after_retry.png" width="280">

After tapping Retry: same error reappears (key still invalid — expected). Confirms Retry triggers a new attempt.

---

### Flow 1-9: Thinking block — collapse and expand

**Result:** PASS (bug fixed in this session)

**Collapsed thinking block:**

<img src="screenshots/Flow1-9_step2_thinking_collapsed.png" width="280">

"Thinking..." collapsed block visible above the AI response bubble. Model badge `claude-opus-4-5-20251101`.

**Expanded thinking block:**

<img src="screenshots/Flow1-9_step4_thinking_expanded.png" width="280">

Block expanded showing full reasoning: model walks through `17 × 23 = 17 × 20 + 17 × 3 = 340 + 51 = 391`.

**Re-collapsed:**

<img src="screenshots/Flow1-9_step5_thinking_recollapsed.png" width="280">

Block collapsed again after second tap.

**Fix applied:** Added `"thinking": {"type": "enabled", "budget_tokens": 10000}` to the Anthropic API request body in `buildAnthropicRequest()` for models whose ID contains `"opus-4"` or `"sonnet-4"`. Previously, only the `anthropic-beta` header was sent but the body config was missing.

---

## Issues Found

### [BUG — FIXED] Top bar hidden when soft keyboard opens (Flow 1-5)

**Status:** Fixed

**Root cause:** `windowSoftInputMode` was not set, defaulting to `adjustPan` which pans the entire window. Fix: set `adjustNothing` in manifest and apply `imePadding` only to the `ChatInput` bottom bar.

---

### [BUG — FIXED] Long-press on user message opens Agent selector instead of copying (Flow 1-6)

**Status:** Fixed

**Root cause:** Android 16 intercepts long-press on `Text` for system text selection before `combinedClickable.onLongClick` fires. Fix: wrap `Text` in `DisableSelection {}` and pass explicit `interactionSource` + `indication = null` to `combinedClickable`.

---

### [BUG — FIXED] Thinking block never appears (Flow 1-9)

**Status:** Fixed

**Root cause:** The `anthropic-beta: interleaved-thinking-2025-05-14` header was sent but the request body was missing the required `"thinking": {"type": "enabled", "budget_tokens": N}` field. Fix: added thinking config to `buildAnthropicRequest()` for capable models.

---

### [MINOR] Error card displays raw JSON (Flow 1-8)

**Severity:** Low — functional but not user-friendly.

**Description:** When an API error occurs, the error card shows the raw JSON error body (e.g., `API error 401: {"type":"error","error":{"type":"authentication_error",...}}`). The expected behavior is a human-readable message such as "API key is invalid or expired."

**Status:** Not fixed in this session. Tracked for a future RFC or patch.

---

### [COSMETIC] Math notation rendering

LaTeX-style formulas rendered as plain text. Known limitation of `compose-markdown`. Not a regression.

## Change History

| Date | Change |
|------|--------|
| 2026-02-27 | Initial report |
| 2026-02-27 | Layer 2 Flow 1-1 executed on Pixel 6a; section updated from SKIP to PASS |
| 2026-02-27 | Flow 1-2 (keyboard) executed on Pixel 6a; FAIL — top bar pushed off-screen |
| 2026-02-27 | Flows 1-2 through 1-9 executed; three bugs found and fixed (keyboard insets, long-press copy, thinking block); all flows now PASS; Layer 2 result updated to PASS |
