# Task Plan

## Immediate Tasks

1. Decide repository strategy.
   - Option A: keep `ZYQIO/oneclaw` as a separate Android product repo.
   - Option B: use the repo as an OpenClaw fork.
   - Exit criteria: one clear decision for what `origin/main` is supposed to represent.

2. Preserve the current OpenClaw work on your fork without damaging the old branch.
   - Create a new branch from local `main`.
   - Push that branch to `origin`.
   - Exit criteria: the rebased OpenClaw branch exists remotely on your fork.

3. Finish verification for the Codex CLI auth fallback change.
   - Investigate why `oauth.fallback-to-main-agent.test.ts` hangs on Vitest exit.
   - Exit criteria: the test exits cleanly or is split/refactored so the regression is reliably covered.

## Short-Term Tasks

1. Decide what to do with the old fork history.
   - If it is still needed, keep it on a dedicated branch such as `android-main` or archive it into a separate repository.
   - If it is not needed, stop treating it as the default `main` branch for OpenClaw work.

2. Run broader project checks after the hanging test issue is resolved.
   - Suggested order:
     - `corepack pnpm exec vitest run src/agents/auth-profiles/oauth.fallback-to-main-agent.test.ts`
     - `corepack pnpm check`
     - `corepack pnpm build`
   - Exit criteria: the branch is ready for push/PR with known-good local validation.

3. Review whether any content from your fork is worth porting manually.
   - Current evidence suggests the fork contains a different product line, not a clean feature branch.
   - Exit criteria: a short keep/drop list of fork-only artifacts worth preserving.

## Deferred Tasks

1. If you want to contribute this auth-profile fix upstream:
   - Push the branch to your fork.
   - Open a PR against `openclaw/openclaw`.
   - Include the test-hang note if it is not fully resolved by then.

2. If you want unified project management artifacts inside this repo:
   - Keep `project-docs/` updated at the end of each session.
   - Promote only stable, user-facing docs into `docs/`.
