# Project Handover

## Snapshot

- Date: 2026-03-18
- Local HEAD: `8c8dc7cd5d` `Agents: recover openai-codex oauth from Codex CLI cache`
- Upstream main: `947dac48f2` `Tests: cap shards for explicit file lanes`
- Origin main: `f0df2d3003` `chore: add handover roadmap and phase0 baseline checks`
- Remotes:
  - `origin -> https://github.com/ZYQIO/oneclaw.git`
  - `upstream -> https://github.com/openclaw/openclaw.git`

## What Was Done

1. Rebasing work onto upstream:
   - Preserved the local auth-profile work as a commit.
   - Rebasing onto the latest `openclaw/openclaw` produced conflicts in:
     - `src/agents/auth-profiles/oauth.ts`
     - `src/agents/auth-profiles/oauth.fallback-to-main-agent.test.ts`
   - Resolved conflicts by porting the Codex CLI fallback logic into the latest upstream auth-profile architecture instead of reverting upstream refactors.

2. Remote reconfiguration:
   - Renamed the original `origin` remote to `upstream`.
   - Added your GitHub repository as the new `origin`.
   - Updated local `main` to track `origin/main`.

3. Fork comparison:
   - Compared local `main` against your `origin/main`.
   - Result: your fork is not a small delta on top of current OpenClaw.
   - It contains a separate Android/`oneclaw` product line with large structural divergence.
   - Directly merging `origin/main` into the current OpenClaw workspace would delete large parts of OpenClaw and introduce an unrelated app structure.
   - Decision: do not auto-merge the fork branch into this repository state.

## Validation Run

- `corepack pnpm install --frozen-lockfile`
  - Completed successfully.
- `corepack pnpm exec vitest run src/agents/cli-credentials.test.ts`
  - Passed: `1` file, `7` tests.
- `corepack pnpm exec vitest run --maxWorkers=1 src/agents/auth-profiles/oauth.fallback-to-main-agent.test.ts`
  - Did not fail with an assertion error during the run, but the Vitest process hung on exit in this environment.
  - This still needs follow-up before treating the test lane as clean.

## Current Risks

- `origin/main` is not safe to merge wholesale into current OpenClaw `main`.
- The auth-profile regression test file still has an unresolved "tests complete but Vitest does not exit cleanly" problem.
- `.codex/` is present locally as an untracked helper directory and was intentionally not committed.

## Recommended Next Move

- Treat the current local branch as the valid OpenClaw working line.
- Push it to a new branch on your fork instead of force-replacing your existing `origin/main`.
- Decide whether `ZYQIO/oneclaw` should remain a separate Android project or be rebuilt as a fresh fork of current OpenClaw.
