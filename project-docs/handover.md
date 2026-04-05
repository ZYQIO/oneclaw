# Project Handover

## Snapshot

- Date: 2026-04-05
- Active branch: `android-desktop-runtime-mainline-20260403`
- Remote branch: `origin/android-desktop-runtime-mainline-20260403`
- Current payload baseline: `0.12.0`
- Latest completed real-device proof: April 4, 2026 `PFEM10` replay reached `classification=plugin_lane_replayed` on payload `0.7.0`
- Latest repo-side proof: April 5, 2026 targeted validation confirmed the new process-runtime recovery bootstrap slice on payload `0.12.0`

## What Was Done

1. The Android desktop-runtime branch advanced from desktop-home proof into plugin replay, then process-model bootstrap, activation-contract bootstrap, supervision-contract bootstrap, observation-contract bootstrap, and now the first recovery-contract bootstrap slice.
   - Real-device replay on `PFEM10` already converged to `classification=plugin_lane_replayed` on payload `0.7.0`.
   - Repo-side validation on April 5, 2026 moved the branch one slice further again to the new `0.12.0` process-runtime-recovery-bootstrap state.

2. `runtime-smoke` now leaves structured process-model, activation-contract, supervision-contract, observation-contract, and recovery-contract artifacts instead of only profile/health/restart evidence.
   - The app still writes `runtime-smoke-desktop-profile.json`, `runtime-smoke-health-report.json`, and `runtime-smoke-restart-contract.json`.
   - It now also writes `runtime-smoke-process-model.json`, `runtime-smoke-activation-contract.json`, `runtime-smoke-supervision-contract.json`, `runtime-smoke-observation-contract.json`, and `runtime-smoke-recovery-contract.json`, and exposes all five field groups through `pod.runtime.describe`.

3. The direct desktop-home bridge and bounded browser/tool/plugin lanes remain intact.
   - `pod.desktop.materialize` still materializes the packaged desktop home in app-private storage.
   - The browser-lane smoke still auto-runs `pod.desktop.materialize`, `runtime-smoke`, `tool-brief-inspect`, and `plugin-allowlist-inspect`.
   - The first allowlisted plugin replay still has real-device proof from April 4, 2026.

4. The latest targeted repo validation is green again on the current machine.
   - `pnpm test -- apps/android/runtime-pod/prepare.test.ts apps/android/runtime-pod/sync-assets.test.ts` passes.
   - The targeted Android Gradle rerun for `EmbeddedRuntimePodStatusTest`, `PodHandlerTest`, and `InvokeCommandRegistryTest` also passes.

5. The Android and project-level handoff docs were updated to reflect the new split state.
   - Repo-verified `0.12.0` process-runtime recovery bootstrap is now documented explicitly.
   - The latest real-device state is still called out separately as April 4, 2026 `0.7.0` `plugin_lane_replayed`.

## Validation Run

- `pnpm test -- apps/android/runtime-pod/prepare.test.ts apps/android/runtime-pod/sync-assets.test.ts`
  - Passed on April 5, 2026.
- `cd apps/android && env ANDROID_HOME="$HOME/Library/Android/sdk" ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" ./gradlew --no-daemon --console=plain :app:testDebugUnitTest --tests ai.openclaw.app.EmbeddedRuntimePodStatusTest --tests ai.openclaw.app.node.PodHandlerTest --tests ai.openclaw.app.node.InvokeCommandRegistryTest`
  - Passed on April 5, 2026.
- `pnpm android:local-host:embedded-runtime-pod:doctor -- --json`
  - Latest completed real-device proof remains the April 4, 2026 run on `PFEM10`.
  - Final verified real-device outcome from that run: `classification=plugin_lane_replayed`.

## Current Risks

- The branch still does not provide full executable desktop parity. Generic browser tooling, unrestricted shell parity, generic plugin/runtime parity, and stronger detached-process observation/recovery semantics are still missing.
- The new process-runtime recovery bootstrap is repo-verified, but there is not yet fresh real-device proof for payload `0.12.0`.
- In the repo-side targeted lane, the new bootstrap currently reports `desktopProcessStatus=blocked`, `desktopProcessActivationStatus=blocked`, `desktopProcessSupervisionStatus=blocked`, and `desktopProcessObservationStatus=blocked` until browser replay is present; that is expected, but it means we should not overstate readiness.

## Recommended Next Move

- Reinstall the current debug app on `PFEM10`, rerun `pnpm android:local-host:embedded-runtime-pod:doctor -- --json`, and drive the device-side state to `classification=process_runtime_recovery_bootstrapped`.
- Once that device proof exists, take the next implementation slice directly into `process_runtime_detached_launch` rather than reopening the earlier plugin-lane decision.
- Keep the current `doctor` + `smoke` + `browser-lane:smoke` path boringly replayable before widening the branch surface again.
