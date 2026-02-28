# Token Usage Tracking

## Feature Information
- **Feature ID**: FEAT-006
- **Created**: 2026-02-28
- **Last Updated**: 2026-02-28
- **Status**: Draft
- **Priority**: P1 (Should Have)
- **Owner**: TBD
- **Related RFC**: TBD
- **Related Feature**: [FEAT-001 (Chat Interaction)](FEAT-001-chat.md)

## User Story

**As** a user who provides my own API keys,
**I want to** see how many tokens each message, session, and model is consuming,
**so that** I can understand my usage patterns and manage my API spending.

### Typical Scenarios

1. User sends a message and wants to see how many input/output tokens that exchange consumed.
2. User opens a session and checks the total token usage for that conversation.
3. User opens the Usage Statistics screen to see a breakdown by model over the past week.
4. User compares token consumption across different models to decide which one is more efficient.

## Feature Description

### Overview

Token Usage Tracking provides visibility into API token consumption at three levels: per-message, per-session, and globally. All counts come from the token usage data returned by provider APIs in streaming responses. Only token counts are tracked -- no cost estimation or pricing in V1.

### Display Levels

#### 1. Per-Message Token Count

Each AI response message displays its token usage inline:
- **Input tokens**: number of tokens in the request sent to the model (prompt + context)
- **Output tokens**: number of tokens in the model's response

Displayed as a small label below the AI message, next to the model ID label that already exists. Example: `claude-sonnet-4-20250514 | 1,234 in / 567 out`

User messages do not show token counts (they have no API call).

#### 2. Per-Session Token Summary

Each session displays cumulative token usage:
- Total input tokens for the session
- Total output tokens for the session
- Visible in the session drawer (below the session title/preview) and optionally on a session detail/info view

#### 3. Global Usage Statistics Screen

A dedicated screen accessible from Settings ("Usage Statistics" entry):

- **Time period selector**: Today / This Week / This Month / All Time
- **Breakdown by model**: Each model that has been used shows:
  - Model name/ID
  - Total input tokens
  - Total output tokens
  - Total tokens (input + output)
  - Number of messages
- **Totals row**: Sum across all models for the selected time period
- Data is read from locally stored message records (no external API needed)

### Data Source

Token counts come from `StreamEvent.Usage` events emitted during streaming, which are already captured and stored in `Message.tokenCountInput` and `Message.tokenCountOutput` fields. The global statistics screen aggregates these fields from the messages table.

Note: not all providers return token usage in every streaming response. When token data is unavailable for a message, it is stored as `null` and excluded from aggregation (not counted as zero).

## Acceptance Criteria

Must pass (all required):
- [ ] Each AI response message shows input/output token count below the message
- [ ] Token counts are only shown when data is available from the provider (no fake zeros)
- [ ] Session drawer shows cumulative input/output token count per session
- [ ] Settings screen has a "Usage Statistics" entry
- [ ] Usage Statistics screen shows token breakdown by model
- [ ] Usage Statistics screen supports time period selection: Today / This Week / This Month / All Time
- [ ] Each model row shows: model name, input tokens, output tokens, total tokens, message count
- [ ] A totals row shows the sum across all models
- [ ] Token counts use number formatting with comma separators (e.g., 1,234,567)

Optional (nice to have):
- [ ] Bar chart or visual representation of usage by model
- [ ] Trend comparison (this week vs last week)

## UI/UX Requirements

### Per-Message Display

Below each AI response, extend the existing label row:

```
[Copy] [Regenerate]  claude-sonnet-4-20250514 | 1,234 in / 567 out
```

The token label uses `labelSmall` typography, `onSurfaceVariant` color, same style as the model ID.

### Session Drawer

Below each session's preview text, add a small token summary:

```
Session Title
Last message preview text...
2 hours ago                        12.3K tokens
```

Uses `labelSmall`, right-aligned. Shows total tokens (input + output combined) with K/M abbreviation for large numbers.

### Usage Statistics Screen

```
Usage Statistics
[Today] [This Week] [This Month] [All Time]    <-- chip row

Model                  Input      Output     Total    Messages
--------------------------------------------------------------
claude-sonnet-4-...    45,230     12,450    57,680      42
gpt-4o                 23,100      8,300    31,400      28
gemini-2.0-flash        5,600      2,100     7,700      15
--------------------------------------------------------------
Total                  73,930     22,850    96,780      85
```

- Top bar: "Usage Statistics" title with back arrow
- Chip row for time period selection (single select)
- Table/list with model rows
- Each row is a `Surface` card or list item
- Totals row at the bottom with bold text

## Feature Boundary

### Included
- Per-message token display (input/output)
- Per-session token summary in drawer
- Global usage statistics screen with time period filter and model breakdown
- Number formatting with separators

### Not Included
- Cost/price estimation
- Budget alerts or spending limits
- Per-agent usage breakdown
- Data export (CSV, etc.)
- Historical trend charts
- Token prediction or estimation for unsent messages

## Business Rules

1. Token counts are sourced exclusively from provider API responses. The app does not estimate or calculate tokens independently.
2. When a provider does not return token usage data, the message's token fields are `null`. These messages are excluded from aggregation totals.
3. Token counts are stored per-message and aggregated on-the-fly for session and global views.
4. Time period filters use the message's `createdAt` timestamp.
5. "Today" means from midnight local time. "This Week" means from Monday 00:00 local time. "This Month" means from the 1st of the current month.

## Dependencies

### Depends On
- **FEAT-001 (Chat Interaction)**: Token data comes from chat message records.

### Depended On By
- None

## Test Points

### Functional Tests -- User Operating the App

#### Test 1: Per-message token display

**Setup**: Provider configured with a model that returns token usage.

| Step | User Action | Expected Result |
|------|-------------|-----------------|
| 1 | Send a message "Hello" | AI responds. |
| 2 | Observe below the AI response | Model ID label is visible. Next to it: token counts like "1,234 in / 567 out". |
| 3 | Send another message | Second AI response also shows token counts. |

#### Test 2: Token display when provider does not return usage

**Setup**: Use a provider/model that does not return token counts in streaming.

| Step | User Action | Expected Result |
|------|-------------|-----------------|
| 1 | Send a message | AI responds. |
| 2 | Observe below the AI response | Model ID is shown but no token count label (not "0 in / 0 out"). |

#### Test 3: Session token summary in drawer

| Step | User Action | Expected Result |
|------|-------------|-----------------|
| 1 | Have a conversation with several messages | Multiple AI responses received. |
| 2 | Open the session drawer (hamburger icon) | Current session shows total token count (e.g., "12.3K tokens") right-aligned below the preview. |

#### Test 4: Usage Statistics screen

| Step | User Action | Expected Result |
|------|-------------|-----------------|
| 1 | Go to Settings | "Usage Statistics" entry is visible. |
| 2 | Tap "Usage Statistics" | Statistics screen opens. Default time period is "All Time". |
| 3 | Observe the model breakdown | Each model used shows input/output/total tokens and message count. Totals row at bottom. |
| 4 | Tap "Today" chip | Table updates to show only today's usage. If no messages today, table is empty or shows zeros. |
| 5 | Tap "This Week" chip | Table updates to show this week's usage. |

#### Test 5: Number formatting

| Step | User Action | Expected Result |
|------|-------------|-----------------|
| 1 | Have enough conversation to accumulate large token counts | Token counts display with comma separators (e.g., "1,234,567" not "1234567"). |

### Edge Cases

- New install with no messages: Usage Statistics screen shows empty state
- Session with only user messages (all AI responses failed): session token count is 0 or absent
- Model used only once: still appears in the breakdown
- Very large numbers (millions of tokens): verify formatting and layout don't break

## Change History

| Date | Version | Changes | Owner |
|------|---------|---------|-------|
| 2026-02-28 | 0.1 | Initial version | TBD |
