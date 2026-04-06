# Android Desktop Runtime Checkpoint / Android 桌面 Runtime 单页检查点

Purpose / 用途: give a fresh session one short page that explains the branch objective, current state, and next move without replaying the whole Android history. / 给新会话一页纸快速说明当前分支的目标、现状和下一步，不必重放整段 Android 历史。

Branch / 分支: `android-desktop-runtime-mainline-20260403`
Last updated / 最后更新: April 6, 2026 / 2026 年 4 月 6 日

## Read This First / 先看这个

- The branch objective is now the full packaged desktop environment on Android, not the earlier "selected slice" framing.
- The APK now carries a cohesive `desktop/` bundle plus the first allowlisted packaged plugin descriptor in payload `0.15.0`.
- `pod.desktop.materialize` now materializes that bundle into `filesDir/openclaw/embedded-desktop-home/<version>/`.
- `pod.runtime.execute(taskId=runtime-smoke)` now replays the materialized desktop profile and environment/supervisor manifests into app-private runtime state, and also leaves health-report, restart-contract, process-model, activation-contract, supervision-contract, observation-contract, recovery-contract, detached-launch-contract, supervisor-loop-contract, and active-session-contract bootstrap artifacts when desktop home is present.
- The new process-runtime active-session bootstrap is repo-verified on this branch, but the latest completed real-device replay is still the April 4, 2026 `0.7.0` `plugin_lane_replayed` proof.
- This branch still does **not** have full executable desktop parity yet.

## What Is Already Landed / 已落地内容

- The embedded pod payload is now `0.15.0` and contains `runtime/`, `toolkit/`, `browser/`, `workspace/`, and `desktop/` stages.
- The packaged `desktop/` stage now groups engine, environment, browser, tools, plugins, supervisor manifests, plus one desktop profile descriptor into the APK.
- `pod.runtime.execute(taskId=runtime-smoke)` still provides the first bounded runtime carrier.
- `pod.runtime.execute(taskId=runtime-smoke)` now also replays the active desktop profile plus packaged environment/supervisor manifests into `runtime-smoke-desktop-profile.json` artifacts under both runtime-home and desktop-home state.
- That same runtime-smoke path now also writes `runtime-smoke-health-report.json` and `runtime-smoke-restart-contract.json` under desktop-home state so restart/health semantics are no longer only implied by manifest fields.
- That same runtime-smoke path now also writes `runtime-smoke-process-model.json`, `runtime-smoke-activation-contract.json`, `runtime-smoke-supervision-contract.json`, `runtime-smoke-observation-contract.json`, `runtime-smoke-recovery-contract.json`, `runtime-smoke-detached-launch-contract.json`, `runtime-smoke-supervisor-loop-contract.json`, and `runtime-smoke-active-session-contract.json` under desktop-home state, which together form the branch's current process-runtime bootstrap stack.
- `pod.runtime.execute(taskId=tool-brief-inspect)` still provides the first packaged desktop-tool lane.
- `pod.runtime.execute(taskId=plugin-allowlist-inspect)` now provides the first narrow allowlisted packaged plugin lane and writes a replayable plugin result under runtime-home `work/`.
- `pod.browser.describe` and `pod.browser.auth.start` still provide the first bounded browser-auth lane.
- `pod.desktop.materialize` is now the first direct bridge from "desktop bundle in the APK" to "desktop home in app-private storage".
- `pod.runtime.describe` now reports desktop-bundle and desktop-home state, including fields such as `desktopEnvironmentBundled`, `desktopBundleReady`, `desktopHomeReady`, and `desktopProfileIds`.

## What Is Still Missing / 仍缺什么

- `fullDesktopRuntimeBundled` is still `false` in the runtime status payload.
- There is still no unrestricted shell parity.
- There is still no generic browser tooling/runtime parity.
- There is still no generic or open-ended plugin runtime parity.
- The current desktop bundle now reaches packaging, materialization, and first profile replay, but it is still not a full executable desktop environment.
- There is still no executed detached subprocess or verified live active session beyond the current health/restart/process-model/activation/supervision/observation/recovery/detached-launch/supervisor-loop/active-session bootstrap artifacts.

## Best Reading Order / 最佳阅读顺序

1. `apps/android/local-host-desktop-runtime-checkpoint-20260403.md`
2. `apps/android/local-host-desktop-runtime-mainline.md`
3. `apps/android/README.md`
4. `apps/android/local-host-handoff.md`
5. `apps/android/app/src/main/java/ai/openclaw/app/EmbeddedRuntimePodDesktop.kt`
6. `apps/android/app/src/main/java/ai/openclaw/app/EmbeddedRuntimePodManager.kt`

## Current Next Move / 当前下一步

1. Stop widening helper/status-only surfaces.
2. Treat `desktop_home_replay`, `environment_supervision`, and the first allowlisted plugin lane as landed bootstrap rather than open hypotheses.
3. Keep the packaged browser lane, desktop-home supervision artifacts, tool replay, and plugin replay boringly stable on-device.
4. Reinstall the current debug app on `PFEM10`, rerun the doctor path until it reaches `process_runtime_active_session_bootstrapped`, and then treat the next gap as device-side active-session proof rather than another repo-only bootstrap.

## Verification Status / 验证状态

- Offline repo checks for the desktop bundle landed in this branch are already done.
- Later on April 3, 2026, the connected OPPO / ColorOS `PFEM10` phone completed the full replay path: after reinstalling the current debug app, `pnpm android:local-host:embedded-runtime-pod:doctor` converged from the stale `0.2.0` build to `classification=desktop_home_configured`, with `manifestVersion=0.6.0`, `verifiedFileCount=24`, `browserReplayReady=true`, and `runtimeDescribeAfter.mainlineStatus=desktop_home_configured`.
- The same device session also provided direct `pod.desktop.materialize` proof: the command created `filesDir/openclaw/embedded-desktop-home/0.6.0`, returned `desktopHomeReady=true`, and wrote both `profiles/active-profile.json` and `state/desktop-materialize.json` in app-private storage.
- On April 4, 2026, the same `PFEM10` device reran the updated debug build and exposed the next environment gap explicitly: before `runtime-smoke`, `podRuntimeDescribe.desktopProfileReplayReady=true` but `desktopEnvironmentSupervisionReady=false`, `desktopHealthStatus=null`, `desktopRestartGeneration=0`, and `recommendedNextSlice=environment_supervision`.
- The follow-up `pod.runtime.execute(taskId=runtime-smoke)` on that same device then wrote `runtime-smoke-health-report.json` and `runtime-smoke-restart-contract.json`, returned `desktopEnvironmentSupervisionReady=true`, `desktopHealthStatus=healthy`, `desktopRestartGeneration=1`, `desktopProfileId=openclaw-desktop-host`, `desktopHealthReportPath=filesDir/openclaw/embedded-desktop-home/0.6.0/state/runtime-smoke-health-report.json`, and `desktopRestartContractPath=filesDir/openclaw/embedded-desktop-home/0.6.0/state/runtime-smoke-restart-contract.json`.
- Later on April 4, 2026, after reinstalling the newest debug app, the same `PFEM10` device also replayed the new packaged plugin slice: `pnpm android:local-host:embedded-runtime-pod:doctor -- --json` now converges to `classification=plugin_lane_replayed`, with `manifestVersion=0.7.0`, `verifiedFileCount=26`, `browserLaneSmoke.summary.desktopMaterialize.ok=true`, `browserLaneSmoke.summary.pluginExecute.ok=true`, `browserLaneSmoke.summary.pluginExecute.pluginId=openclaw-plugin-host-placeholder`, and `browserLaneSmoke.summary.runtimeDescribeAfter.recommendedNextSlice=process_model`.
- On April 6, 2026, the branch advanced one repo-side slice further again: payload `0.15.0` now passes `pnpm test -- apps/android/runtime-pod/prepare.test.ts apps/android/runtime-pod/sync-assets.test.ts` plus the targeted Android Gradle suite, `runtime-smoke` writes `runtime-smoke-process-model.json`, `runtime-smoke-activation-contract.json`, `runtime-smoke-supervision-contract.json`, `runtime-smoke-observation-contract.json`, `runtime-smoke-recovery-contract.json`, `runtime-smoke-detached-launch-contract.json`, `runtime-smoke-supervisor-loop-contract.json`, and `runtime-smoke-active-session-contract.json`, `pod.runtime.describe` now exposes process-model plus activation-contract plus supervision-contract plus observation-contract plus recovery-contract plus detached-launch plus supervisor-loop plus active-session fields, and the expected next on-device convergence after reinstall is `classification=process_runtime_active_session_bootstrapped` with `runtimeDescribeAfter.recommendedNextSlice=process_runtime_active_session_validation`.
- The clean targeted Android verification lane is green again after making browser-auth state reads safe under Robolectric when `EncryptedSharedPreferences` cannot reach `AndroidKeyStore`; `EmbeddedRuntimePodStatusTest`, `PodHandlerTest`, `InvokeCommandRegistryTest`, `LocalHostNodesToolingTest`, `LocalHostRemoteAccessServerTest`, and `OpenClawProtocolConstantsTest` all pass in the clean rerun.
- That first materialize rerun also surfaced a real repo bug: remote `/invoke/capabilities` advertised `pod.desktop.materialize`, but `/invoke` returned `INVALID_REQUEST: unknown command` because `InvokeCommandRegistry` was missing `OpenClawPodCommand.DesktopMaterialize`. The branch now fixes that mismatch.
- The existing device-facing verification entrypoints remain:
  - `pnpm android:local-host:embedded-runtime-pod:doctor`
  - `pnpm android:local-host:embedded-runtime-pod:smoke`
  - `pnpm android:local-host:embedded-runtime-pod:browser-lane:smoke`
- The device-side queue is tracked in `apps/android/local-host-desktop-runtime-verification-queue-20260403.md`.

## Do Not Misread / 不要误判

- Do not describe this branch as "the full desktop Gateway/CLI already runs inside Android".
- Do not collapse "desktop bundle exists in the APK" into "desktop execution parity is complete".
- Do not reopen the old assumption that helper metadata alone answers the desktop-runtime question.
- Do not treat the older Android MVP narrative as the active finish line for this branch.
- Do not treat the new process-model, activation-contract, supervision-contract, observation-contract, recovery-contract, detached-launch-contract, supervisor-loop-contract, or active-session-contract bootstrap artifacts as proof that a detached desktop process with a live active session is already running inside Android.
