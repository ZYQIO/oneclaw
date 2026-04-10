# Android Desktop Runtime Verification Queue / Android 桌面 Runtime 验证队列

Date / 日期: April 10, 2026 / 2026-04-10
Branch / 分支: `android-desktop-runtime-mainline-20260403`

## Goal / 目标

Record the exact real-device verification steps for this branch, the already-completed `0.6.0` and `0.7.0` results, plus the current `0.17.0` live-proof baseline that now has a captured active session and needs replayability hardening next. / 记录这条分支的真机验证步骤、已经完成的 `0.6.0` 与 `0.7.0` 结果，以及当前已经捕获 live active session、下一步转向重放加固的 `0.17.0` 基线。

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
- On April 6, 2026, the branch advanced one repo-side slice further again: payload `0.17.0` now passes the pod asset tests plus the targeted Android Gradle suite, `runtime-smoke` writes `runtime-smoke-process-model.json`, `runtime-smoke-activation-contract.json`, `runtime-smoke-supervision-contract.json`, `runtime-smoke-observation-contract.json`, `runtime-smoke-recovery-contract.json`, `runtime-smoke-detached-launch-contract.json`, `runtime-smoke-supervisor-loop-contract.json`, `runtime-smoke-active-session-contract.json`, `runtime-smoke-active-session-validation.json`, and `runtime-smoke-active-session-device-proof.json`, and `pod.runtime.describe` can now distinguish `plugin_lane_replayed`, `process_model_bootstrapped`, `process_runtime_activation_bootstrapped`, `process_runtime_supervision_bootstrapped`, `process_runtime_observation_bootstrapped`, `process_runtime_recovery_bootstrapped`, `process_runtime_detached_launch_bootstrapped`, `process_runtime_supervisor_loop_bootstrapped`, `process_runtime_active_session_bootstrapped`, `process_runtime_active_session_validation_bootstrapped`, and the newer `process_runtime_active_session_device_proof_bootstrapped` state.
- Later on April 6, 2026, the same `PFEM10` device replayed that `0.17.0` build again and the combined doctor result converged to `classification=process_runtime_active_session_live_proof_captured` with `manifestVersion=0.17.0`, `verifiedFileCount=26`, and `runtimeDescribeAfter.recommendedNextSlice=process_runtime_lane_hardening`. The updated browser-lane smoke also reran `runtime-smoke` after browser replay was ready and captured `runtimeExecuteAfterBrowser.longLivedProcessReady=true`, `processStatus=standby`, `supervisionStatus=active`, `activeSessionStatus=ready`, `activeSessionObserved=true`, `activeSessionValidationStatus=validated`, and `activeSessionDeviceProofStatus=verified`. The remaining gap in this queue is therefore replay hardening rather than live active-session proof capture itself. / 2026 年 4 月 6 日稍后，同一台 `PFEM10` 真机又把这版 `0.17.0` build 重新跑通，组合 doctor 结果已经收敛到 `classification=process_runtime_active_session_live_proof_captured`，并得到 `manifestVersion=0.17.0`、`verifiedFileCount=26` 与 `runtimeDescribeAfter.recommendedNextSlice=process_runtime_lane_hardening`。更新后的 browser-lane smoke 还会在 browser replay 就绪后补跑一次 `runtime-smoke`，并留下 `runtimeExecuteAfterBrowser.longLivedProcessReady=true`、`processStatus=standby`、`supervisionStatus=active`、`activeSessionStatus=ready`、`activeSessionObserved=true`、`activeSessionValidationStatus=validated` 与 `activeSessionDeviceProofStatus=verified`。因此这条队列当前剩下的缺口已经不再是 live active-session proof 本身，而是重放加固。
- On April 10, 2026, the same `PFEM10` lane still passed `pnpm android:local-host:embedded-runtime-pod:stability -- --json --iterations 3`, so three consecutive doctor runs all kept `classification=process_runtime_active_session_live_proof_captured`, `recommendedNextSlice=process_runtime_lane_hardening`, `liveProofReplayed=true`, and `liveProofContinuity.preserved=true`. / 2026 年 4 月 10 日，同一条 `PFEM10` 真机 lane 仍然通过了 `pnpm android:local-host:embedded-runtime-pod:stability -- --json --iterations 3`，这意味着连续三轮 doctor 都保持了 `classification=process_runtime_active_session_live_proof_captured`、`recommendedNextSlice=process_runtime_lane_hardening`、`liveProofReplayed=true` 与 `liveProofContinuity.preserved=true`。
- 同一天稍晚，同一条 `PFEM10` 真机 lane 还通过了 `pnpm android:local-host:embedded-runtime-pod:stability -- --json --iterations 3 --restart-app-between-iterations`，这意味着即便 wrapper 在各轮 doctor 之间强制停止并重新拉起 `ai.openclaw.app`，整条 replay 也依然保持绿色；聚合 summary 回报 `perturbationMode=app_restart_between_iterations`、`perturbationAppliedCount=2`、`perturbationFailureCount=0`、`passedIterationCount=3` 与 `failedIterationCount=0`。 / Later that same day, the same `PFEM10` lane also passed `pnpm android:local-host:embedded-runtime-pod:stability -- --json --iterations 3 --restart-app-between-iterations`, so the replay remained green even when the wrapper force-stopped and relaunched `ai.openclaw.app` between doctor iterations; the aggregate summary reported `perturbationMode=app_restart_between_iterations`, `perturbationAppliedCount=2`, `perturbationFailureCount=0`, `passedIterationCount=3`, and `failedIterationCount=0`.
- On April 10, 2026, the same `PFEM10` lane also passed `pnpm android:local-host:embedded-runtime-pod:soak -- --json`, so the replay remained green through a five-iteration restart-perturbation soak too; the aggregate summary reported `packageCommand=pnpm android:local-host:embedded-runtime-pod:soak`, `iterationsRequested=5`, `perturbationMode=app_restart_between_iterations`, `perturbationAppliedCount=4`, `perturbationFailureCount=0`, `passedIterationCount=5`, and `failedIterationCount=0`. / 2026 年 4 月 10 日，同一条 `PFEM10` 真机 lane 还通过了 `pnpm android:local-host:embedded-runtime-pod:soak -- --json`，这意味着整条 replay 在五轮 restart-perturbation soak 中也依然保持绿色；聚合 summary 回报 `packageCommand=pnpm android:local-host:embedded-runtime-pod:soak`、`iterationsRequested=5`、`perturbationMode=app_restart_between_iterations`、`perturbationAppliedCount=4`、`perturbationFailureCount=0`、`passedIterationCount=5` 与 `failedIterationCount=0`。

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

6. Keep the combined doctor artifact and confirm whether the top-level result reaches the captured live-proof state and preserves it through the confirm-only rerun.

```bash
OPENCLAW_ANDROID_LOCAL_HOST_BROWSER_START=0 \
pnpm android:local-host:embedded-runtime-pod:doctor -- --json
```

7. When you want the replay-hardening verdict rather than one single doctor pass, run the stability wrapper.

```bash
pnpm android:local-host:embedded-runtime-pod:stability -- --json --iterations 3
```

8. When you want the stronger replay-hardening verdict through explicit app restarts, run the restart-perturbation stability lane.

```bash
pnpm android:local-host:embedded-runtime-pod:stability -- --json --iterations 3 --restart-app-between-iterations
```

9. When you want the default longer hardening lane, run the soak wrapper.

```bash
pnpm android:local-host:embedded-runtime-pod:soak -- --json
```

## Expected Results / 预期结果

- Step 0 should collapse the current state into one top-level classification and leave one combined `summary.json`.
- Step 3 should still report the packaged pod baseline as healthy.
- Step 4 should leave `browserDescribeAfter.replayReady=true` in `summary.json`.
- Step 4 should auto-run `pod.desktop.materialize` and leave `desktopMaterialize.ok=true` in `summary.json`.
- Step 4 should now also replay `plugin-allowlist-inspect` and leave `pluginExecute.ok=true` with `pluginExecute.pluginId=openclaw-plugin-host-placeholder`.
- Step 4 should now also leave `runtimeExecuteAfterBrowser.longLivedProcessReady=true`, `runtimeExecuteAfterBrowser.processStatus=standby`, `runtimeExecuteAfterBrowser.activeSessionStatus=ready`, `runtimeExecuteAfterBrowser.activeSessionObserved=true`, `runtimeExecuteAfterBrowser.activeSessionValidationStatus=validated`, and `runtimeExecuteAfterBrowser.activeSessionDeviceProofStatus=verified`.
- On the newest `0.17.0` build, the branch should now converge to `runtimeDescribeAfter.mainlineStatus=process_runtime_active_session_live_proof_captured` with `runtimeDescribeAfter.recommendedNextSlice=process_runtime_lane_hardening`.
- The combined doctor summary should now also leave `confirmBrowserLaneSmoke.required=true`, `executed=true`, `ok=true`, `mainlineStatus=process_runtime_active_session_live_proof_captured`, and `liveProofReplayed=true`.
- Step 7 should now also leave `ok=true`, `passedIterationCount=3`, `failedIterationCount=0`, `classifications=["process_runtime_active_session_live_proof_captured"]`, and `recommendedNextSlices=["process_runtime_lane_hardening"]`.
- Step 8 should now also leave `ok=true`, `perturbationMode=app_restart_between_iterations`, `perturbationAppliedCount=2`, `perturbationFailureCount=0`, `passedIterationCount=3`, `failedIterationCount=0`, and `classifications=["process_runtime_active_session_live_proof_captured"]`.
- Step 9 should now also leave `ok=true`, `packageCommand=pnpm android:local-host:embedded-runtime-pod:soak`, `iterationsRequested=5`, `perturbationAppliedCount=4`, `perturbationFailureCount=0`, `passedIterationCount=5`, `failedIterationCount=0`, and `classifications=["process_runtime_active_session_live_proof_captured"]`.

## Artifacts To Keep / 建议保留的产物

- The `summary.json` from `pnpm android:local-host:embedded-runtime-pod:smoke`.
- The `summary.json` from the browser-lane smoke start pass.
- The `summary.json` from the browser-lane smoke confirm pass.
- The `summary.json` from `pnpm android:local-host:embedded-runtime-pod:stability`, plus the three per-iteration doctor summaries under `iterations/`.
- The `summary.json` from the restart-perturbation `pnpm android:local-host:embedded-runtime-pod:stability -- --json --iterations 3 --restart-app-between-iterations` run, plus its per-iteration restart logs under `iterations/*/between-iterations-restart.stdout.txt`.
- The `summary.json` from `pnpm android:local-host:embedded-runtime-pod:soak -- --json`, plus its five per-iteration doctor summaries and restart logs under `iterations/*/`.
- The `pod-runtime-execute.json` artifact that captures the initial `runtime-smoke` replay.
- The `pod-runtime-execute-after-browser.json` artifact that captures the browser-aligned runtime state after replay becomes ready, including `longLivedProcessReady`, `processStatus`, `supervisionStatus`, and active-session readiness.
- The direct `pod.desktop.materialize` artifact set that includes `active-profile.json` and `desktop-materialize.json`.
- The `pod-plugin-execute.json` artifact that captures the allowlisted plugin replay result.

## Do Not Reopen / 不要重开

- Do not reopen whether the plugin lane should exist; the first allowlisted plugin replay already has real-device proof.
- Do not treat a pre-existing credential alone as proof that the packaged browser lane itself has replayed successfully.
- Do not overstate the new `process_runtime_active_session_live_proof_captured` proof as full executable desktop parity; it is a bounded repeated-replay proof, not generic detached-process parity.
