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
| 2 | adb Visual Flows | PASS | Flow 1-1 executed on Pixel 6a (Android 16) |

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

**Result:** PASS (Flow 1-1)

**Device:** Pixel 6a, Android 16

**Provider:** Anthropic (`claude-sonnet-4-6`)

**Flow executed:** Flow 1-1 — Send message, streaming response appears

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

**Flows not yet executed:** Flow 1-2 through Flow 1-9 (keyboard layout, stop generation, regenerate, tool calls, error card, thinking block). These remain to be run in future sessions.

## Issues Found

No blocking issues. One cosmetic observation:

- **Math notation rendering**: LaTeX-style formulas (e.g., `max(0,z)`, `∂L/∂w`) are rendered as plain text fragments on separate lines rather than typeset math. This is a known limitation of the `compose-markdown` library and was already documented in RFC-001 Open Questions. Not a regression.

## Change History

| Date | Change |
|------|--------|
| 2026-02-27 | Initial report |
| 2026-02-27 | Layer 2 Flow 1-1 executed on Pixel 6a; section updated from SKIP to PASS |
