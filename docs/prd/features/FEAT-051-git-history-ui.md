# FEAT-051: Git History Browser UI

## Document Information
- **Feature ID**: FEAT-051
- **Status**: Draft
- **Created**: 2026-03-02
- **Last Updated**: 2026-03-02

## Background

RFC-050 introduced automatic git versioning of all text files under `filesDir`. Every write to memory files, daily logs, and AI-generated markdown is committed automatically. However, there is currently no in-app UI for users to browse this history. Users must ask the AI to run `git_log` or `git_show` tools to inspect past versions, which is indirect and not discoverable.

## Goals

1. Add a Git History screen that lists all commits in reverse chronological order
2. Allow the user to filter commits by subdirectory (e.g., only memory changes, only daily logs)
3. Tapping a commit shows its full diff so the user can see exactly what changed
4. Accessible from the Memory screen via a dedicated entry point

## Non-Goals

- No restore / rollback action from the UI (already possible via AI `git_restore` tool)
- No branch management UI
- No diff syntax highlighting (plain monospace text is sufficient)
- No pagination beyond a simple load-more mechanism

## User Stories

**US-1: Browse full history**
As a user, I want to open a "Version History" view from the Memory screen and see a chronological list of all changes made to my files.

**US-2: Filter by category**
As a user, I want to filter the history to show only memory changes, only daily logs, or only AI-generated files, so I can focus on what I care about.

**US-3: Inspect a change**
As a user, I want to tap a commit entry and see a readable diff of exactly what text was added or removed, so I understand what changed.

## Functional Requirements

### FR-1: Entry Point
Add a "Version History" icon button to the top bar of the Memory screen. Tapping it navigates to the Git History screen.

### FR-2: Commit List
- Display commits in reverse chronological order (newest first)
- Each item shows:
  - Short SHA (7 characters)
  - Relative or absolute timestamp (e.g., "2 hours ago" or "2026-03-02 14:30")
  - Commit message
- Default limit: 50 commits; a "Load more" button appends the next 50

### FR-3: Path Filter
A segmented control or chip row at the top of the list with these filter options:

| Label | Path filter passed to `git_log` |
|---|---|
| All | none |
| Memory | `memory/MEMORY.md` |
| Daily Logs | `memory/daily/` |
| Files | none, but exclude `memory/` prefix commits |

### FR-4: Commit Detail
Tapping a commit opens a detail sheet (bottom sheet or full-screen) showing:
- Commit SHA, date, message
- Full diff text rendered in a scrollable monospace `Text` composable
- A "Close" button

### FR-5: Empty State
If the repository has no commits yet, show a placeholder message: "No version history yet."

## Non-Functional Requirements

- All `AppGitRepository` calls run on the IO dispatcher via the ViewModel
- The screen must remain responsive while commits are loading (show a loading indicator)
- The diff view must handle large diffs gracefully (scroll, no truncation for reasonable sizes up to ~50KB)
