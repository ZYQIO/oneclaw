# Telegram Bridge: Image Delivery Fix

## Feature Information
- **Feature ID**: FEAT-042
- **Created**: 2026-03-01
- **Last Updated**: 2026-03-01
- **Status**: Draft
- **Priority**: P0 (Must Have)
- **Owner**: TBD
- **Related RFC**: [RFC-042 (Telegram Image Delivery Fix)](../../rfc/features/RFC-042-telegram-image-delivery.md)
- **Related Features**: [FEAT-024 (Messaging Bridge)](FEAT-024-messaging-bridge.md), [FEAT-041 (Bridge Improvements)](FEAT-041-bridge-improvements.md)

## User Story

**As** a user of the Telegram bridge,
**I want** images I send to the bot (with or without a text caption) to be received and seen by the AI agent,
**so that** I can have multimodal conversations through Telegram just as I can within the app directly.

### Typical Scenarios

1. User sends a photo with a caption "What is in this image?" via Telegram. The agent receives both the image and the caption text and responds with a description of the image.
2. User sends a photo with no caption via Telegram. The agent receives the image and responds to it (e.g., "I can see a photo. What would you like to know about it?").
3. User sends a plain text message via Telegram (no image). Behavior is unchanged -- the agent receives and responds to the text.

## Feature Description

### Overview

FEAT-042 fixes two bugs that together cause images sent via Telegram to be silently dropped before reaching the AI agent.

**Bug 1 -- Wrong field for photo caption**: When a Telegram message contains a photo, the text accompanying the photo is stored in the `caption` field of the message object, not the `text` field. The current `TelegramChannel` reads `message["text"]`, which is always null for photo messages. The caption is lost.

**Bug 2 -- imagePaths not forwarded to agent**: `BridgeAgentExecutorImpl.executeMessage()` receives the `imagePaths` list from the channel layer but does not pass it to `SendMessageUseCase.execute()`. The image paths are silently discarded. The agent is never given access to the downloaded image file.

### Root Cause Summary

| Bug | Location | Impact |
|-----|----------|--------|
| Reading `message["text"]` instead of `message["caption"]` for photo messages | `TelegramChannel.kt` | Caption text is lost when a photo is sent |
| `imagePaths` not passed from `BridgeAgentExecutorImpl` to `SendMessageUseCase` | `BridgeAgentExecutorImpl.kt` and `SendMessageUseCase.kt` | Image is downloaded locally but the agent never receives it |

### Fix Description

#### Fix 1: Read caption for photo messages

In `TelegramChannel`, when a photo is present in the message, fall back to reading `message["caption"]` if `message["text"]` is empty:

```
text = message["text"] ?: message["caption"] ?: ""
```

This preserves backward compatibility (text-only messages still read from `message["text"]`).

#### Fix 2: Thread imagePaths through the execution pipeline

Extend `SendMessageUseCase.execute()` to accept an optional `imagePaths: List<String>` parameter and pass the paths to the agent as file attachments. Update `BridgeAgentExecutorImpl` to forward `imagePaths` from the bridge layer to the use case.

### Acceptance Criteria

Must pass (all required):
- [ ] Sending a Telegram photo with caption: agent receives both the image file and the caption text
- [ ] Sending a Telegram photo without caption: agent receives the image file; text is empty or a default placeholder
- [ ] Sending a Telegram text-only message: behavior unchanged, no regression
- [ ] If image download fails, the message is still processed as text-only (existing behavior preserved)
- [ ] All existing unit tests pass; new unit tests cover caption extraction and imagePaths forwarding

## Feature Boundary

### Included
- Fix `TelegramChannel` to read `message["caption"]` for photo messages
- Extend `SendMessageUseCase.execute()` with `imagePaths: List<String> = emptyList()`
- Update `BridgeAgentExecutorImpl` to forward `imagePaths` to `SendMessageUseCase`

### Not Included
- Multi-photo album support (Telegram album = multiple messages; out of scope)
- Image support for non-Telegram channels (Discord, etc. -- separate effort)
- Outbound image responses from the agent back to Telegram

## Dependencies

### Depends On
- **FEAT-024 (Messaging Bridge)**: TelegramChannel, BridgeAgentExecutor, BridgeImageStorage
- **FEAT-001 (Chat Interaction)**: SendMessageUseCase

## Change History

| Date | Version | Changes | Owner |
|------|---------|---------|-------|
| 2026-03-01 | 0.1 | Initial version | - |
