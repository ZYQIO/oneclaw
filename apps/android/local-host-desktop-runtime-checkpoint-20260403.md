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
3. Move browser, tools, and plugins from manifest-only bundle state toward executable bundle state.
4. When a device is available again, capture on-device replay evidence for the desktop-home path and update the verification queue.

## Verification Status / 验证状态

- Offline repo checks for the desktop bundle landed in this branch are already done.
- The device-side replay for `pod.desktop.materialize` is still pending because no device was available at the time of this checkpoint.
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
