# Project Handover

## Snapshot

- Date: 2026-04-06
- Active branch: `android-desktop-runtime-mainline-20260403`
- Remote branch: `origin/android-desktop-runtime-mainline-20260403`
- Current payload baseline: `0.17.0`
- Latest completed real-device proof: April 6, 2026 `PFEM10` replay reached `classification=process_runtime_active_session_device_proof_bootstrapped` on payload `0.17.0`
- Latest repo-side proof: April 6, 2026 targeted validation confirmed the new process-runtime active-session-device-proof bootstrap slice on payload `0.17.0`

## What Was Done

1. The Android desktop-runtime branch advanced from desktop-home proof into plugin replay, then process-model bootstrap, activation-contract bootstrap, supervision-contract bootstrap, observation-contract bootstrap, recovery-contract bootstrap, detached-launch-contract bootstrap, supervisor-loop-contract bootstrap, active-session-contract bootstrap, active-session-validation bootstrap, and now the first active-session-device-proof bootstrap slice.
   - Repo-side validation on April 6, 2026 moved the branch one slice further again to the new `0.17.0` process-runtime-active-session-device-proof-bootstrap state.
   - Later that same day, real-device replay on `PFEM10` also converged to `classification=process_runtime_active_session_device_proof_bootstrapped` on payload `0.17.0`.

2. `runtime-smoke` now leaves structured process-model, activation-contract, supervision-contract, observation-contract, recovery-contract, detached-launch-contract, supervisor-loop-contract, active-session-contract, active-session-validation, and active-session-device-proof artifacts instead of only profile/health/restart evidence.
   - The app still writes `runtime-smoke-desktop-profile.json`, `runtime-smoke-health-report.json`, and `runtime-smoke-restart-contract.json`.
   - It now also writes `runtime-smoke-process-model.json`, `runtime-smoke-activation-contract.json`, `runtime-smoke-supervision-contract.json`, `runtime-smoke-observation-contract.json`, `runtime-smoke-recovery-contract.json`, `runtime-smoke-detached-launch-contract.json`, `runtime-smoke-supervisor-loop-contract.json`, `runtime-smoke-active-session-contract.json`, `runtime-smoke-active-session-validation.json`, and `runtime-smoke-active-session-device-proof.json`, and exposes all ten field groups through `pod.runtime.describe`.

3. The direct desktop-home bridge and bounded browser/tool/plugin lanes remain intact.
   - `pod.desktop.materialize` still materializes the packaged desktop home in app-private storage.
   - The browser-lane smoke still auto-runs `pod.desktop.materialize`, `runtime-smoke`, `tool-brief-inspect`, and `plugin-allowlist-inspect`, and it now reruns `runtime-smoke` once more after browser replay is ready so the runtime contracts see the browser dependency as present.
   - The refreshed `PFEM10` browser-lane replay now records `runtimeExecuteAfterBrowser.longLivedProcessReady=true`, `processStatus=standby`, `supervisionStatus=active`, and `activeSessionStatus=ready` while the active-session proof layer remains honest about the missing live proof.

4. The latest targeted repo validation is green again on the current machine.
   - `pnpm test -- apps/android/runtime-pod/prepare.test.ts apps/android/runtime-pod/sync-assets.test.ts` passes.
   - The targeted Android Gradle rerun for `EmbeddedRuntimePodStatusTest`, `PodHandlerTest`, and `InvokeCommandRegistryTest` also passes.

5. The Android and project-level handoff docs were updated to reflect the new verified state.
   - Repo-verified `0.17.0` process-runtime active-session-device-proof bootstrap is now documented explicitly.
   - The latest real-device state is also updated to the April 6, 2026 `0.17.0` `process_runtime_active_session_device_proof_bootstrapped` replay, plus the browser-aligned runtime summary that now exposes the live-proof gap directly.

## Validation Run

- `pnpm test -- apps/android/runtime-pod/prepare.test.ts apps/android/runtime-pod/sync-assets.test.ts`
  - Passed on April 6, 2026.
- `cd apps/android && env ANDROID_HOME="$HOME/Library/Android/sdk" ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" ./gradlew --no-daemon --console=plain :app:testDebugUnitTest --tests ai.openclaw.app.EmbeddedRuntimePodStatusTest --tests ai.openclaw.app.node.PodHandlerTest --tests ai.openclaw.app.node.InvokeCommandRegistryTest`
  - Passed on April 6, 2026.
- `pnpm android:local-host:embedded-runtime-pod:doctor -- --json`
  - Latest completed real-device proof is the April 6, 2026 run on `PFEM10`.
  - Final verified real-device outcome from that run: `classification=process_runtime_active_session_device_proof_bootstrapped`.
  - The same doctor artifact also records `browserLaneSmoke.summary.runtimeExecuteAfterBrowser.longLivedProcessReady=true`, `processStatus=standby`, `supervisionStatus=active`, `activeSessionStatus=ready`, `activeSessionValidationStatus=pending_device_proof`, and `activeSessionDeviceProofStatus=pending_live_proof`.

## Current Risks

- The branch still does not provide full executable desktop parity. Generic browser tooling, unrestricted shell parity, generic plugin/runtime parity, and stronger detached-process observation/recovery semantics are still missing.
- The new process-runtime active-session-device-proof bootstrap is now repo-verified and freshly replayed on-device, but there is still no verified live detached active session yet.
- Even in the refreshed `PFEM10` replay, the browser-aligned runtime summary still reports `activeSessionObserved=false`, `activeSessionValidationStatus=pending_device_proof`, and `activeSessionDeviceProofStatus=pending_live_proof`; that is expected, but it means we should not overstate readiness.

## Recommended Next Move

- Keep the current `PFEM10` replay boringly repeatable at `classification=process_runtime_active_session_device_proof_bootstrapped`, and preserve the new `runtimeExecuteAfterBrowser` artifact in `browser-lane-smoke`.
- Capture one real live detached active-session proof instead of reopening the earlier plugin-lane decision.
- Keep the current `doctor` + `smoke` + `browser-lane:smoke` path boringly replayable before widening the branch surface again.
