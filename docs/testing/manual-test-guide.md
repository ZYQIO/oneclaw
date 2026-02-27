# Manual Test Guide — OneClawShadow

This document is the cumulative, always-up-to-date guide for manually testing the OneClawShadow app. It describes every user-facing flow that exists in the current implementation, and how to verify it on a real device or emulator.

**How to use this guide:**
- Before any release, execute all flows in this guide from a fresh install.
- Update this guide whenever an RFC adds, changes, or removes user-facing behavior.
- This guide reflects the current state of the app, not planned future features.

## Document Information

| Field | Value |
|-------|-------|
| Last updated | 2026-02-27 |
| App version | 0.1.0 |
| Last RFC implemented | RFC-005 (Session Management) |
| Status | Provider Management + Tool System + Session Management backend implemented; Chat not yet implemented |

---

## Setup

### Prerequisites

- Android device or emulator (API 26+)
- App installed: `./gradlew installDebug` or `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- For provider connection tests: API key for at least one provider (Anthropic, OpenAI, or Gemini)

### Fresh Install

```bash
adb shell pm clear com.oneclaw.shadow
adb shell am start -n com.oneclaw.shadow/.MainActivity
```

---

## Current App State

As of the last implemented RFC (RFC-005), the app supports:
- First-launch setup flow (choose provider, enter API key, select default model)
- Provider management (list, detail, API key, connection test, model list)
- Settings screen
- Tool system (backend only — no UI for tool configuration yet)
- Session management (backend + drawer UI ready, but not yet wired to MainActivity nav graph)
- Chat screen (placeholder — sending messages not yet functional)

**Not yet implemented:** Chat message sending and streaming (RFC-001), Agent management UI (RFC-002), Session drawer wired to chat (RFC-001 integration point).

---

## Flow 1: First Launch — Setup Screen

**When:** Fresh install, `has_completed_setup` flag is false.

**Expected behavior:** App launches directly to the Setup screen (3-step wizard).

### Step 1.1: Launch app fresh

```bash
adb shell pm clear com.oneclaw.shadow
adb shell am start -n com.oneclaw.shadow/.MainActivity
```

**Verify:**
- Setup screen is displayed (not chat screen)
- Step 1 "Choose Provider" is shown
- Three provider options are visible: OpenAI, Anthropic, Google Gemini
- "Skip for now" button is visible

### Step 1.2: Skip setup

On the setup screen, tap "Skip for now".

**Verify:**
- App navigates to the Chat screen
- Chat screen shows a placeholder/greeting
- `has_completed_setup` is now true (relaunching the app should go directly to Chat, not Setup)

### Step 1.3: Complete setup — choose provider

From a fresh install, tap "Anthropic" (or another provider).

**Verify:**
- Step 2 "Enter API Key" is shown
- An API key input field is visible
- "Test Connection" button is visible
- "Skip for now" button is still visible

### Step 1.4: Enter API key and test connection

Enter a valid API key in the field. Tap "Test Connection".

**Verify:**
- Loading indicator appears while testing
- On success: a green success indicator or "Connected" message appears
- On failure: an error message appears (e.g., "Invalid API key" or "Network error")

### Step 1.5: Continue to model selection

After a successful connection test, proceed to Step 3.

**Verify:**
- Step 3 "Select Default Model" is shown
- A list of models from the provider is displayed
- Each model has a name and can be selected
- "Get Started" button is enabled after selecting a model

### Step 1.6: Complete setup

Select a model and tap "Get Started".

**Verify:**
- App navigates to the Chat screen
- `has_completed_setup` is true (app goes to Chat on next launch)

---

## Flow 2: Settings Screen

**Access:** From the Chat screen, tap the Settings gear icon (top-right).

### Step 2.1: Open Settings

**Verify:**
- Settings screen opens
- "Manage Providers" list item is visible with subtitle "Add API keys, configure models"
- Back navigation works (arrow in top-left)

### Step 2.2: Navigate to Provider List

Tap "Manage Providers".

**Verify:**
- Provider list screen opens
- Three built-in providers are shown: OpenAI, Anthropic, Google Gemini
- Each provider row shows: name, model count, and a status chip
- Status chip is one of: "Connected" (green/purple), "Not configured" (grey), "Disconnected" (red/pink)
- "+" button in top-right is visible (for future custom provider support)

---

## Flow 3: Provider Detail — API Key Management

**Access:** Settings → Manage Providers → tap a provider (e.g., Anthropic).

### Step 3.1: Open provider detail

**Verify:**
- Provider detail screen opens
- Provider name in top bar
- API key section: masked input field (shows "••••" if key exists, empty if not)
- Eye icon to toggle key visibility
- Save button
- "Test Connection" button
- Model list section showing available models
- Each model has: name, source label (PRESET or MANUAL), star icon for default model

### Step 3.2: Enter and save API key

Tap the API key field. Enter a key. Tap Save.

**Verify:**
- Key is saved (masked display updates)
- No plaintext key visible in UI after save
- Success toast or message appears

### Step 3.3: Toggle API key visibility

Tap the eye icon next to the API key field.

**Verify:**
- Key is revealed in plaintext
- Eye icon changes to "hide" variant
- Tap again: key is masked again

### Step 3.4: Test connection

With a valid API key entered, tap "Test Connection".

**Verify:**
- Loading indicator appears
- On success: "Connected" result shown (green indicator or success card)
- On failure: error message with type (auth error, network error, timeout)

### Step 3.5: View model list

**Verify:**
- Models are listed under the "Available Models" section
- Each model shows: ID, source (PRESET or MANUAL), star icon if it is the global default

### Step 3.6: Set a model as default

Tap the star icon next to a non-default model.

**Verify:**
- That model's star becomes filled
- Previous default model's star becomes unfilled
- Success message appears

### Step 3.7: Refresh models from API

Tap "Refresh Models" button.

**Verify:**
- Loading indicator appears
- Model list updates (new models fetched from the provider's API)
- Success or error message shown

---

## Flow 4: Provider Detail — Manual Model

**Access:** Settings → Manage Providers → [any provider] → Add Manual Model.

### Step 4.1: Add a manual model

Tap "Add Manual Model" button. Enter a model ID (e.g., `gpt-4-turbo`). Confirm.

**Verify:**
- Model appears in the list with source label "MANUAL"
- Star icon is available to set it as default

### Step 4.2: Delete a manual model

Tap the delete icon next to a MANUAL model. Confirm deletion.

**Verify:**
- Model is removed from the list
- PRESET models cannot be deleted (no delete icon shown)

---

## Flow 5: Relaunch — First Launch Detection

**Verify the app remembers setup state.**

### Step 5.1: After completing setup, close and relaunch

```bash
adb shell am force-stop com.oneclaw.shadow
adb shell am start -n com.oneclaw.shadow/.MainActivity
```

**Verify:**
- App launches directly to Chat screen (not Setup)
- Previously entered API keys are still configured (check Settings → Providers)

---

## Flow 6: Chat Screen (Placeholder State)

**Note:** Chat functionality (RFC-001) is not yet implemented. This flow only verifies the UI shell.

### Step 6.1: Navigate to Chat

From any screen, navigate back to the Chat screen.

**Verify:**
- Chat screen is visible
- A placeholder or empty state is shown (e.g., "How can I help you today?")
- Input text field is visible at the bottom
- Agent name is shown in the top bar
- Settings gear icon is in the top-right
- Hamburger/drawer icon is in the top-left

### Step 6.2: Session drawer (placeholder)

Tap the hamburger icon (top-left).

**Verify:**
- Drawer opens
- "New Conversation" button is visible at the top
- Any existing sessions are listed (initially empty on fresh install)
- Session items (once created) show: title, message preview, relative timestamp, agent name chip
- Long-pressing a session item enters selection mode (checkboxes appear, bulk-delete toolbar shown)
- Swiping a session item to the left reveals delete action

---

## Known Limitations (current state)

| Feature | Status | Expected RFC |
|---------|--------|--------------|
| Sending chat messages | Not implemented | RFC-001 |
| Streaming AI response | Not implemented | RFC-001 |
| Agent creation/management UI | Not implemented | RFC-002 |
| Session drawer wired to nav graph | Not implemented | RFC-001 integration |
| Tool call visualization in chat | Not implemented | RFC-001 + RFC-004 |

---

## Change History

| Date | RFC | Changes |
|------|-----|---------|
| 2026-02-27 | RFC-003, RFC-004 | Initial guide — covers Setup, Settings, Provider management flows |
| 2026-02-27 | RFC-005 | Updated current state, Flow 6.2 session drawer detail, known limitations |
