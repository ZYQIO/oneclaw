# Android Desktop Runtime Checkpoint / Android 桌面 Runtime 单页检查点

Purpose / 用途: give a fresh session one short page that explains the branch objective, current state, and next move without replaying the whole Android history. / 给新会话一页纸快速说明当前分支的目标、现状和下一步，不必重放整段 Android 历史。

Branch / 分支: `android-desktop-runtime-mainline-20260403`
Last updated / 最后更新: April 3, 2026 / 2026 年 4 月 3 日

## Read This First / 先看这个

- The branch objective is now the full packaged desktop environment on Android, not the earlier "selected slice" framing.
- The APK already carries a cohesive `desktop/` bundle in payload `0.6.0`.
- `pod.desktop.materialize` now materializes that bundle into `filesDir/openclaw/embedded-desktop-home/<version>/`.
- This branch still does **not** have full executable desktop parity yet.

## What Is Already Landed / 已落地内容

- The embedded pod payload is now `0.6.0` and contains `runtime/`, `toolkit/`, `browser/`, `workspace/`, and `desktop/` stages.
- The packaged `desktop/` stage now groups engine, environment, browser, tools, plugins, supervisor manifests, plus one desktop profile descriptor into the APK.
- `pod.runtime.execute(taskId=runtime-smoke)` still provides the first bounded runtime carrier.
- `pod.runtime.execute(taskId=tool-brief-inspect)` still provides the first packaged desktop-tool lane.
- `pod.browser.describe` and `pod.browser.auth.start` still provide the first bounded browser-auth lane.
- `pod.desktop.materialize` is now the first direct bridge from "desktop bundle in the APK" to "desktop home in app-private storage".
- `pod.runtime.describe` now reports desktop-bundle and desktop-home state, including fields such as `desktopEnvironmentBundled`, `desktopBundleReady`, `desktopHomeReady`, and `desktopProfileIds`.

## What Is Still Missing / 仍缺什么

- `fullDesktopRuntimeBundled` is still `false` in the runtime status payload.
- There is still no unrestricted shell parity.
- There is still no generic browser tooling/runtime parity.
- There is still no executable plugin runtime parity.
- The current desktop bundle is real packaging plus materialization, but not yet a full executable desktop environment.

## Best Reading Order / 最佳阅读顺序

1. `apps/android/local-host-desktop-runtime-checkpoint-20260403.md`
2. `apps/android/local-host-desktop-runtime-mainline.md`
3. `apps/android/README.md`
4. `apps/android/local-host-handoff.md`
5. `apps/android/app/src/main/java/ai/openclaw/app/EmbeddedRuntimePodDesktop.kt`
6. `apps/android/app/src/main/java/ai/openclaw/app/EmbeddedRuntimePodManager.kt`

## Current Next Move / 当前下一步

1. Stop widening helper/status-only surfaces.
2. Make the desktop-home side run a more real embedded engine task instead of only materializing manifests.
3. Keep the packaged browser lane, desktop-home materialization, and tool/runtime replay boringly stable on-device.
4. Decide whether the next slice should deepen engine/environment execution or attach one narrowly allowlisted plugin lane now that real-device desktop-home proof exists.

## Verification Status / 验证状态

- Offline repo checks for the desktop bundle landed in this branch are already done.
- Later on April 3, 2026, the connected OPPO / ColorOS `PFEM10` phone completed the full replay path: after reinstalling the current debug app, `pnpm android:local-host:embedded-runtime-pod:doctor` converged from the stale `0.2.0` build to `classification=desktop_home_configured`, with `manifestVersion=0.6.0`, `verifiedFileCount=24`, `browserReplayReady=true`, and `runtimeDescribeAfter.mainlineStatus=desktop_home_configured`.
- The same device session also provided direct `pod.desktop.materialize` proof: the command created `filesDir/openclaw/embedded-desktop-home/0.6.0`, returned `desktopHomeReady=true`, and wrote both `profiles/active-profile.json` and `state/desktop-materialize.json` in app-private storage.
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
