# Android Desktop Runtime Verification Queue / Android 桌面 Runtime 验证队列

Date / 日期: April 3, 2026 / 2026-04-03
Branch / 分支: `android-desktop-runtime-mainline-20260403`

## Goal / 目标

Record the exact real-device verification steps that were blocked during the earlier daytime session, plus the actual evening results once a phone became available. / 记录早先白天会话里因缺设备而挂起的真机验证步骤，以及晚间接上手机后的实际结果。

## Completion Snapshot / 完成快照

- On April 3, 2026 evening, the connected OPPO / ColorOS `PFEM10` phone completed this queue end to end. / 2026 年 4 月 3 日晚，接入的 OPPO / ColorOS `PFEM10` 真机已把这条验证队列完整跑通。
- The first doctor run correctly caught a stale device build: `classification=embedded_pod_unhealthy`, `manifestVersion=0.2.0`, and missing `pod.browser.describe` / `pod.runtime.describe` / `pod.runtime.execute`. / 第一轮 doctor 正确抓到了设备上还是旧包：`classification=embedded_pod_unhealthy`，`manifestVersion=0.2.0`，并且缺少 `pod.browser.describe` / `pod.runtime.describe` / `pod.runtime.execute`。
- After `:app:installDebug`, the pod baseline converged to `manifestVersion=0.6.0`, `verifiedFileCount=24`, and a healthy packaged pod surface. / 重新执行 `:app:installDebug` 后，pod 基线已收敛成 `manifestVersion=0.6.0`、`verifiedFileCount=24`，且 packaged pod 面整体健康。
- The browser-lane smoke then proved `browserDescribeAfter.replayReady=true` and `authCredentialPresent=true`. / 随后的 browser-lane smoke 已证明 `browserDescribeAfter.replayReady=true` 且 `authCredentialPresent=true`。
- The first direct `pod.desktop.materialize` attempt exposed a real repo bug: `/invoke/capabilities` listed the command, but `/invoke` returned `INVALID_REQUEST: unknown command` because `InvokeCommandRegistry` had not registered `OpenClawPodCommand.DesktopMaterialize`. / 第一轮直接调用 `pod.desktop.materialize` 还暴露出一个真实仓库缺口：`/invoke/capabilities` 已列出该命令，但 `/invoke` 返回 `INVALID_REQUEST: unknown command`，原因是 `InvokeCommandRegistry` 漏注册了 `OpenClawPodCommand.DesktopMaterialize`。
- After fixing that registry gap and reinstalling the debug app, `pod.desktop.materialize` succeeded on-device with `desktopHomeReady=true`, and the follow-up doctor converged to `classification=desktop_home_configured` with `runtimeDescribeAfter.mainlineStatus=desktop_home_configured`. / 修掉这个注册表缺口并重装 debug app 后，`pod.desktop.materialize` 已在真机成功返回 `desktopHomeReady=true`，随后 doctor 也收敛到 `classification=desktop_home_configured`，且 `runtimeDescribeAfter.mainlineStatus=desktop_home_configured`。
- On April 4, 2026, the same `PFEM10` device reran the updated debug app and pushed the queue one step further: `podRuntimeDescribe.desktopProfileReplayReady=false` plus `recommendedNextSlice=desktop_home_replay` before `runtime-smoke`, then `podRuntimeExecute.desktopProfileReplayReady=true`, `desktopProfileId=openclaw-desktop-host`, and replay artifacts under both `embedded-desktop-home/0.6.0/state/runtime-smoke-desktop-profile.json` and `embedded-runtime-home/0.6.0/work/runtime-smoke-desktop-profile.json`. / 2026 年 4 月 4 日，同一台 `PFEM10` 真机在更新后的 debug app 上又把这条验证队列往前推了一步：在执行 `runtime-smoke` 之前，`podRuntimeDescribe.desktopProfileReplayReady=false` 且 `recommendedNextSlice=desktop_home_replay`；执行之后，`podRuntimeExecute.desktopProfileReplayReady=true`、`desktopProfileId=openclaw-desktop-host`，并在 `embedded-desktop-home/0.6.0/state/runtime-smoke-desktop-profile.json` 与 `embedded-runtime-home/0.6.0/work/runtime-smoke-desktop-profile.json` 两边都留下 replay 产物。
- That April 4 follow-up also showed the branch's next visible fork point: the later browser-lane smoke advanced `runtimeDescribeAfter.recommendedNextSlice=plugin_lane`, so the remaining question is no longer "can desktop home be replayed?" but "whether the next narrow slice should deepen environment supervision or attach a plugin lane." / 这轮 4 月 4 日 follow-up 也把分支下一个可见分叉点暴露出来了：后续 browser-lane smoke 会把 `runtimeDescribeAfter.recommendedNextSlice` 推进到 `plugin_lane`，因此剩余问题已经不再是“desktop home 能不能 replay”，而是“下一条窄切片该优先补 environment supervision 还是接 plugin lane”。

## Tonight's Commands / 今晚要跑的命令

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

## Expected Results / 预期结果

- Step 0 should collapse the current state into one top-level classification and leave one combined `summary.json`.
- Step 3 should still report the packaged pod baseline as healthy.
- Step 4 should leave `browserDescribeAfter.replayReady=true` in `summary.json`.
- Step 4 should move `runtimeDescribeAfter.mainlineStatus` at least to `browser_lane_replayed` or `browser_lane_configured`.
- After `pod.desktop.materialize`, the branch now ideally converges one step further to `runtimeDescribeAfter.mainlineStatus=desktop_home_configured`.

## Artifacts To Keep / 建议保留的产物

- The `summary.json` from `pnpm android:local-host:embedded-runtime-pod:smoke`.
- The `summary.json` from the browser-lane smoke start pass.
- The `summary.json` from the browser-lane smoke confirm pass.
- The direct `pod.desktop.materialize` artifact set that includes `active-profile.json` and `desktop-materialize.json`.

## Do Not Reopen / 不要重开

- Do not open the plugin lane before the browser-lane smoke has real-device proof.
- Do not treat a pre-existing credential alone as proof that the packaged browser lane itself has replayed successfully.
