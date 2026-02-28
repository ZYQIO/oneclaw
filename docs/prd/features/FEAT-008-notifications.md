# Notifications

## Feature Information
- **Feature ID**: FEAT-008
- **Created**: 2026-02-28
- **Last Updated**: 2026-02-28
- **Status**: Draft
- **Priority**: P1 (Should Have)
- **Owner**: TBD
- **Related RFC**: TBD
- **Related Feature**: [FEAT-001 (Chat Interaction)](FEAT-001-chat.md)

## User Story

**As** a user who has started a long-running AI task,
**I want to** receive a notification when the task completes or fails,
**so that** I can switch to another app while waiting and come back when the AI is done.

### Typical Scenarios

1. User asks the AI a complex question that involves multiple tool calls. The user switches to another app. A few minutes later, a notification appears: "Task completed -- Here's a summary of the files I found...". The user taps it and returns to the session.

2. User asks the AI to do something, then switches apps. The API request fails due to a rate limit. A notification appears: "Task failed -- Rate limit exceeded. Please try again later." The user taps it to see the error.

3. User is actively in the app watching the AI stream. No notification is shown -- the UI is already visible.

## Feature Description

### Overview

The app sends Android push notifications when an AI agent task completes or fails while the app is in the background. Notifications include a preview of the result, and tapping them navigates back to the relevant session. No notifications are sent while the user is actively viewing the app.

### When Notifications Are Sent

A notification is sent when ALL of the following are true:
1. The app is in the **background** (user has switched away or screen is off).
2. An agent loop has **finished** -- either successfully (ResponseComplete) or with an error (ChatEvent.Error).

A notification is NOT sent when:
- The app is in the foreground (user is looking at the chat).
- The agent loop is still running (waiting for API response, executing tools, streaming).

### Notification Content

#### Task Completed

```
OneClaw Shadow
Task completed
[Preview of AI response, first ~100 characters]
```

- Title: "Task completed"
- Body: First ~100 characters of the AI's final response text, truncated with "..."
- Tap action: Opens the app and navigates to the relevant session

#### Task Failed

```
OneClaw Shadow
Task failed
[Error message, first ~100 characters]
```

- Title: "Task failed"
- Body: The error message from ChatEvent.Error, truncated with "..."
- Tap action: Opens the app and navigates to the relevant session

### Notification Channel

A single Android notification channel:
- Channel ID: `agent_tasks`
- Channel name: "Agent Tasks"
- Importance: Default (sound + notification shade)
- Respects system Do Not Disturb settings automatically

### Foreground Detection

The app tracks whether it is in the foreground using `ProcessLifecycleOwner` (from `androidx.lifecycle`). When `ON_STOP` is received, the app is in the background. When `ON_START` is received, the app is back in the foreground.

## Acceptance Criteria

Must pass (all required):
- [ ] Notification is shown when AI task completes while app is in the background
- [ ] Notification is shown when AI task fails while app is in the background
- [ ] Notification is NOT shown when the app is in the foreground
- [ ] Notification includes a preview of the AI response or error message
- [ ] Tapping the notification opens the app and navigates to the correct session
- [ ] Notification uses a proper Android notification channel
- [ ] Notifications respect system Do Not Disturb settings
- [ ] No notification is shown while the task is still in progress (waiting/streaming)

Optional (nice to have):
- [ ] Notification grouping when multiple tasks complete
- [ ] Notification actions (e.g., "Reply" directly from notification)

## UI/UX Requirements

### Notification Appearance

Standard Android notification appearance:
- Small icon: app icon (monochrome)
- Large icon: none (use default)
- Title: "Task completed" or "Task failed"
- Body: Preview text (~100 chars)
- Tap: Opens the app to the relevant session

### No In-App Notification UI

When the app is in the foreground, no toast, snackbar, or overlay notification is shown. The chat UI itself is the live feedback.

## Feature Boundary

### Included
- Push notification on task completion (background)
- Push notification on task failure (background)
- Notification with result/error preview
- Tap-to-navigate to the relevant session
- Single notification channel

### Not Included
- In-app notification overlays or toasts
- Notification preferences/toggle in Settings (V1: always on if the app is in background)
- Multiple notification channels (e.g., separate for errors)
- Custom notification sounds or vibration patterns
- Notification for sync status (FEAT-007)
- Notification for non-chat events

## Business Rules

1. Notifications are only sent when the app is in the background.
2. One notification per completed/failed task. If multiple tasks finish, each gets its own notification.
3. Notification preview text is truncated to ~100 characters.
4. Tapping a notification always navigates to the session where the task ran.
5. If the user dismisses the notification, no further action is taken (no re-notification).

## Dependencies

### Depends On
- **FEAT-001 (Chat Interaction)**: Notifications are triggered by ChatEvent.ResponseComplete and ChatEvent.Error.

### Depended On By
- None

## Test Points

### Functional Tests -- User Operating the App

#### Test 1: Notification on task completion

| Step | User Action | Expected Result |
|------|-------------|-----------------|
| 1 | Send a message to the AI | AI starts streaming. |
| 2 | Press the Home button (switch to another app) | App goes to background. AI continues processing. |
| 3 | Wait for AI to finish | A notification appears in the notification shade: "Task completed" with a preview of the response. |
| 4 | Tap the notification | App opens and navigates to the session with the completed response visible. |

#### Test 2: Notification on task failure

| Step | User Action | Expected Result |
|------|-------------|-----------------|
| 1 | Configure a provider with an invalid API key | Provider set up with wrong key. |
| 2 | Send a message | AI starts processing (will fail). |
| 3 | Press the Home button | App goes to background. |
| 4 | Wait for the API call to fail | A notification appears: "Task failed" with the error message. |
| 5 | Tap the notification | App opens, navigates to the session, error message visible in chat. |

#### Test 3: No notification when app is in foreground

| Step | User Action | Expected Result |
|------|-------------|-----------------|
| 1 | Send a message while staying in the app | AI streams response. |
| 2 | Wait for completion | Response appears in chat. No notification in the notification shade. |

#### Test 4: Notification navigates to correct session

| Step | User Action | Expected Result |
|------|-------------|-----------------|
| 1 | Open Session A, send a message | AI starts processing in Session A. |
| 2 | Switch to Session B via the drawer | Session B is now visible. |
| 3 | Press the Home button | App goes to background. Session A's task continues. |
| 4 | Wait for Session A's task to complete | Notification appears for Session A. |
| 5 | Tap the notification | App opens to Session A (not Session B). |

### Edge Cases

- Multiple tasks complete in background: each gets a separate notification
- User clears all notifications: no side effects on the app
- App is killed while task is running: task is lost (no notification, same as current behavior)
- Notification permission denied (Android 13+): notifications silently fail, app still works

## Change History

| Date | Version | Changes | Owner |
|------|---------|---------|-------|
| 2026-02-28 | 0.1 | Initial version | TBD |
