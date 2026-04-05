# Android Desktop Runtime Verification Queue / Android 桌面 Runtime 验证队列

Date / 日期: April 5, 2026 / 2026-04-05
Branch / 分支: `android-desktop-runtime-mainline-20260403`

## Goal / 目标

Record the exact real-device verification steps for this branch, the already-completed `0.6.0` and `0.7.0` results, plus the current `0.10.0` process-runtime-supervision bootstrap replay that still needs fresh device proof. / 记录这条分支的真机验证步骤、已经完成的 `0.6.0` 与 `0.7.0` 结果，以及当前仍待补真机证据的 `0.10.0` process-runtime-supervision bootstrap 复跑。

## Completion Snapshot / 完成快照

- On April 3, 2026 evening, the connected OPPO / ColorOS `PFEM10` phone completed this queue end to end. / 2026 年 4 月 3 日晚，接入的 OPPO / ColorOS `PFEM10` 真机已把这条验证队列完整跑通。
- The first doctor run correctly caught a stale device build: `classification=embedded_pod_unhealthy`, `manifestVersion=0.2.0`, and missing `pod.browser.describe` / `pod.runtime.describe` / `pod.runtime.execute`. / 第一轮 doctor 正确抓到了设备上还是旧包：`classification=embedded_pod_unhealthy`，`manifestVersion=0.2.0`，并且缺少 `pod.browser.describe` / `pod.runtime.describe` / `pod.runtime.execute`。
- After `:app:installDebug`, the pod baseline converged to `manifestVersion=0.6.0`, `verifiedFileCount=24`, and a healthy packaged pod surface. / 重新执行 `:app:installDebug` 后，pod 基线已收敛成 `manifestVersion=0.6.0`、`verifiedFileCount=24`，且 packaged pod 面整体健康。
- The browser-lane smoke then proved `browserDescribeAfter.replayReady=true` and `authCredentialPresent=true`. / 随后的 browser-lane smoke 已证明 `browserDescribeAfter.replayReady=true` 且 `authCredentialPresent=true`。
- The first direct `pod.desktop.materialize` attempt exposed a real repo bug: `/invoke/capabilities` listed the command, but `/invoke` returned `INVALID_REQUEST: unknown command` because `InvokeCommandRegistry` had not registered `OpenClawPodCommand.DesktopMaterialize`. / 第一轮直接调用 `pod.desktop.materialize` 还暴露出一个真实仓库缺口：`/invoke/capabilities` 已列出该命令，但 `/invoke` 返回 `INVALID_REQUEST: unknown command`，原因是 `InvokeCommandRegistry` 漏注册了 `OpenClawPodCommand.DesktopMaterialize`。
- After fixing that registry gap and reinstalling the debug app, `pod.desktop.materialize` succeeded on-device with `desktopHomeReady=true`, and the follow-up doctor converged to `classification=desktop_home_configured` with `runtimeDescribeAfter.mainlineStatus=desktop_home_configured`. / 修掉这个注册表缺口并重装 debug app 后，`pod.desktop.materialize` 已在真机成功返回 `desktopHomeReady=true`，随后 doctor 也收敛到 `classification=desktop_home_configured`，且 `runtimeDescribeAfter.mainlineStatus=desktop_home_configured`。
- On April 4, 2026, the same `PFEM10` device reran the newer environment-supervision build and exposed the next distinction explicitly: before rerunning `runtime-smoke`, `podRuntimeDescribe.desktopProfileReplayReady=true` but `desktopEnvironmentSupervisionReady=false`, `desktopHealthStatus=null`, `desktopRestartGeneration=0`, and `recommendedNextSlice=environment_supervision`. / 2026 年 4 月 4 日，同一台 `PFEM10` 真机在更新后的 environment-supervision build 上又把状态差异显式化了：在重新执行 `runtime-smoke` 之前，`podRuntimeDescribe.desktopProfileReplayReady=true`，但 `desktopEnvironmentSupervisionReady=false`、`desktopHealthStatus=null`、`desktopRestartGeneration=0`，因此 `recommendedNextSlice=environment_supervision`。
- The follow-up `pod.runtime.execute(taskId=runtime-smoke)` on that same device then wrote `runtime-smoke-health-report.json` plus `runtime-smoke-restart-contract.json`, returned `desktopEnvironmentSupervisionReady=true`, `desktopHealthStatus=healthy`, and `desktopRestartGeneration=1`. / 同一台设备上后续执行的 `pod.runtime.execute(taskId=runtime-smoke)` 则真正写出了 `runtime-smoke-health-report.json` 和 `runtime-smoke-restart-contract.json`，返回 `desktopEnvironmentSupervisionReady=true`、`desktopHealthStatus=healthy` 与 `desktopRestartGeneration=1`。
- Later on April 4, 2026, after reinstalling the newest debug app with payload `0.7.0`, the same queue advanced one slice further again: the pod baseline converged to `manifestVersion=0.7.0`, `verifiedFileCount=26`, the browser-lane smoke auto-ran `pod.desktop.materialize`, replayed `plugin-allowlist-inspect`, and the combined doctor result converged to `classification=plugin_lane_replayed` with `runtimeDescribeAfter.recommendedNextSlice=process_model`. / 2026 年 4 月 4 日稍晚，在重装携带 `0.7.0` payload 的最新 debug app 后，同一条队列又往前推进了一刀：pod 基线已收敛成 `manifestVersion=0.7.0`、`verifiedFileCount=26`，browser-lane smoke 会自动执行 `pod.desktop.materialize`、复跑 `plugin-allowlist-inspect`，而组合 doctor 结果则收敛到 `classification=plugin_lane_replayed`，并把 `runtimeDescribeAfter.recommendedNextSlice` 推进到 `process_model`。
- On April 5, 2026, the branch advanced one repo-side slice further again: payload `0.10.0` now passes the pod asset tests plus the targeted Android Gradle suite, `runtime-smoke` writes `runtime-smoke-process-model.json`, `runtime-smoke-activation-contract.json`, and `runtime-smoke-supervision-contract.json`, and `pod.runtime.describe` can now distinguish `plugin_lane_replayed`, `process_model_bootstrapped`, `process_runtime_activation_bootstrapped`, and the newer `process_runtime_supervision_bootstrapped` state. The remaining gap in this queue is therefore the next device replay on `PFEM10`. / 2026 年 4 月 5 日，这条分支在 repo 侧又往前推进了一刀：`0.10.0` payload 现在已经通过 pod asset 测试和定向 Android Gradle 套件，`runtime-smoke` 会同时写出 `runtime-smoke-process-model.json`、`runtime-smoke-activation-contract.json` 与 `runtime-smoke-supervision-contract.json`，而 `pod.runtime.describe` 也已经能把 `plugin_lane_replayed`、`process_model_bootstrapped`、`process_runtime_activation_bootstrapped` 与更新后的 `process_runtime_supervision_bootstrapped` 状态区分开来。因此这条队列当前剩下的缺口就是在 `PFEM10` 上补跑下一轮真机验证。

## Current Commands / 当前要跑的命令

0. Fastest one-command path when you just want the combined desktop-runtime verdict first.

```bash
pnpm android:local-host:embedded-runtime-pod:doctor
```

1. Reinstall the current debug app if the device might still be on an older build.

```bash
cd apps/android
./gradlew --no-daemon --console=plain :app:installDebug
```

2. Bootstrap the current local-host bearer token over trusted adb.

```bash
pnpm android:local-host:token -- --json
```

3. Reconfirm the packaged pod baseline first.

```bash
OPENCLAW_ANDROID_LOCAL_HOST_USE_ADB_FORWARD=1 \
OPENCLAW_ANDROID_LOCAL_HOST_TOKEN='<token>' \
pnpm android:local-host:embedded-runtime-pod:smoke
```

4. Prove the bounded browser lane leaves replayable state on disk.

```bash
OPENCLAW_ANDROID_LOCAL_HOST_USE_ADB_FORWARD=1 \
OPENCLAW_ANDROID_LOCAL_HOST_TOKEN='<token>' \
pnpm android:local-host:embedded-runtime-pod:browser-lane:smoke
```

5. After the external browser flow completes on the phone, rerun the same browser-lane smoke in read-only confirm mode.

```bash
OPENCLAW_ANDROID_LOCAL_HOST_USE_ADB_FORWARD=1 \
OPENCLAW_ANDROID_LOCAL_HOST_TOKEN='<token>' \
OPENCLAW_ANDROID_LOCAL_HOST_BROWSER_START=0 \
pnpm android:local-host:embedded-runtime-pod:browser-lane:smoke
```

6. Keep the combined doctor artifact and confirm whether the top-level result reaches the new process-model state.

```bash
OPENCLAW_ANDROID_LOCAL_HOST_BROWSER_START=0 \
pnpm android:local-host:embedded-runtime-pod:doctor -- --json
```

## Expected Results / 预期结果

- Step 0 should collapse the current state into one top-level classification and leave one combined `summary.json`.
- Step 3 should still report the packaged pod baseline as healthy.
- Step 4 should leave `browserDescribeAfter.replayReady=true` in `summary.json`.
- Step 4 should auto-run `pod.desktop.materialize` and leave `desktopMaterialize.ok=true` in `summary.json`.
- Step 4 should now also replay `plugin-allowlist-inspect` and leave `pluginExecute.ok=true` with `pluginExecute.pluginId=openclaw-plugin-host-placeholder`.
- On the newest `0.10.0` build, the branch should now ideally converge to `runtimeDescribeAfter.mainlineStatus=process_runtime_supervision_bootstrapped` with `runtimeDescribeAfter.recommendedNextSlice=process_runtime_observation`.

## Artifacts To Keep / 建议保留的产物

- The `summary.json` from `pnpm android:local-host:embedded-runtime-pod:smoke`.
- The `summary.json` from the browser-lane smoke start pass.
- The `summary.json` from the browser-lane smoke confirm pass.
- The `pod-runtime-execute.json` artifact that captures the new `runtime-smoke-process-model.json`, `runtime-smoke-activation-contract.json`, and `runtime-smoke-supervision-contract.json` bootstrap state.
- The direct `pod.desktop.materialize` artifact set that includes `active-profile.json` and `desktop-materialize.json`.
- The `pod-plugin-execute.json` artifact that captures the allowlisted plugin replay result.

## Do Not Reopen / 不要重开

- Do not reopen whether the plugin lane should exist; the first allowlisted plugin replay already has real-device proof.
- Do not treat a pre-existing credential alone as proof that the packaged browser lane itself has replayed successfully.
- Do not treat the repo-side `0.10.0` process-runtime supervision bootstrap proof as if the device has already replayed it; the latest real-device proof still stops at `0.7.0` `plugin_lane_replayed`.
