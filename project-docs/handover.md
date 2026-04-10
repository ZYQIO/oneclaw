# Project Handover

## Snapshot

- Date: 2026-04-10
- Active branch: `android-desktop-runtime-mainline-20260403`
- Remote branch: `origin/android-desktop-runtime-mainline-20260403`
- Current payload baseline: `0.17.0`
- Latest completed real-device proof: April 10, 2026 `PFEM10` replay still reached `classification=process_runtime_active_session_live_proof_captured` on payload `0.17.0`
- Latest repo-side proof: April 6, 2026 targeted validation confirmed the repeated-replay live-proof capture path on payload `0.17.0`
- Latest hardening proof: the April 10, 2026 `PFEM10` doctor rerun again auto-ran one confirm-only browser-lane replay and left both `confirmBrowserLaneSmoke.liveProofReplayed=true` and `confirmBrowserLaneSmoke.liveProofContinuity.preserved=true`
- Latest stability proof: on April 10, 2026 `pnpm android:local-host:embedded-runtime-pod:stability -- --json --iterations 3` still passed on `PFEM10` with `passedIterationCount=3`, `failedIterationCount=0`, `stableCapturedArtifactCount=3`, and `stableExpectedArtifactCount=3`
- Latest perturbation proof: on April 10, 2026 the same `PFEM10` lane also passed `pnpm android:local-host:embedded-runtime-pod:stability -- --json --iterations 3 --restart-app-between-iterations` with `perturbationMode=app_restart_between_iterations`, `perturbationAppliedCount=2`, `perturbationFailureCount=0`, `passedIterationCount=3`, and `failedIterationCount=0`
- Latest soak proof: later on April 10, 2026 the new `pnpm android:local-host:embedded-runtime-pod:soak -- --json` wrapper also passed on the same `PFEM10` lane with `packageCommand=pnpm android:local-host:embedded-runtime-pod:soak`, `iterationsRequested=5`, `perturbationAppliedCount=4`, `perturbationFailureCount=0`, `passedIterationCount=5`, and `failedIterationCount=0`
- Latest status-surface alignment: the captured `runtime-smoke-active-session-device-proof.json` artifact still points `proofCommand` at the stability wrapper once live proof is already observed, while the pre-live-proof state still points at the single-pass doctor command; the top-level `pod.runtime.describe` payload now mirrors that split through `recommendedProofCommand`, additionally exposes `recommendedHardeningCommand=pnpm android:local-host:embedded-runtime-pod:soak -- --json`, and the captured artifact now also exposes `hardeningCommand=pnpm android:local-host:embedded-runtime-pod:soak -- --json`
- Latest hardening guard: `local-host-embedded-runtime-pod-doctor.sh` now also compares the confirm-only replay against the first live-proof capture and records `confirmBrowserLaneSmoke.liveProofContinuity` so replay hardening is not reduced to checking the top-level classification alone
- Latest perturbation guard: `local-host-embedded-runtime-pod-stability.sh` now also supports `--restart-app-between-iterations`, which force-stops and relaunches `ai.openclaw.app` between doctor iterations and fails the aggregate summary when that perturbation lane regresses
- Latest browser-lane hardening: `local-host-embedded-runtime-browser-lane-smoke.sh` now carries validation/device-proof observations such as lease renewal, recovery re-entry, restart continuity, and captured-vs-expected proof artifact counts into `runtimeExecuteAfterBrowser`, and treats regressions there as smoke failures

## What Was Done

1. The Android desktop-runtime branch advanced from desktop-home proof into plugin replay, then process-model bootstrap, activation-contract bootstrap, supervision-contract bootstrap, observation-contract bootstrap, recovery-contract bootstrap, detached-launch-contract bootstrap, supervisor-loop-contract bootstrap, active-session-contract bootstrap, active-session-validation bootstrap, active-session-device-proof bootstrap, and now one captured live active-session proof on-device.
   - Repo-side validation on April 6, 2026 confirmed the repeated-replay live-proof capture path with targeted Android tests.
   - Later that same day, real-device replay on `PFEM10` converged to `classification=process_runtime_active_session_live_proof_captured` on payload `0.17.0`.
   - The doctor wrapper now also auto-runs one confirm-only browser-lane rerun with `OPENCLAW_ANDROID_LOCAL_HOST_BROWSER_START=0`, and the latest `PFEM10` replay already preserves `process_runtime_active_session_live_proof_captured` through that confirm pass.
   - On April 10, 2026, a fresh rerun on the same `PFEM10` device still converged to `classification=process_runtime_active_session_live_proof_captured`, with `runtimeExecuteAfterBrowser.activeSessionValidationLeaseRenewalObserved=true`, `activeSessionValidationRecoveryReentryObserved=true`, `activeSessionValidationRestartContinuityObserved=true`, and matching `activeSessionDeviceProofCapturedArtifactCount=3` / `activeSessionDeviceProofExpectedArtifactCount=3` across the initial and confirm passes.
   - This round also hardens the doctor wrapper so the confirm pass must preserve browser replay, long-lived process readiness, active-session observation, lease renewal, recovery re-entry, restart continuity, validation status, device-proof status, and proof-artifact counts, all surfaced through `confirmBrowserLaneSmoke.liveProofContinuity`.

2. `runtime-smoke` now leaves structured process-model, activation-contract, supervision-contract, observation-contract, recovery-contract, detached-launch-contract, supervisor-loop-contract, active-session-contract, active-session-validation, and active-session-device-proof artifacts instead of only profile/health/restart evidence.
   - The app still writes `runtime-smoke-desktop-profile.json`, `runtime-smoke-health-report.json`, and `runtime-smoke-restart-contract.json`.
   - It now also writes `runtime-smoke-process-model.json`, `runtime-smoke-activation-contract.json`, `runtime-smoke-supervision-contract.json`, `runtime-smoke-observation-contract.json`, `runtime-smoke-recovery-contract.json`, `runtime-smoke-detached-launch-contract.json`, `runtime-smoke-supervisor-loop-contract.json`, `runtime-smoke-active-session-contract.json`, `runtime-smoke-active-session-validation.json`, and `runtime-smoke-active-session-device-proof.json`, and exposes all ten field groups through `pod.runtime.describe`.

3. The direct desktop-home bridge and bounded browser/tool/plugin lanes remain intact.
   - `pod.desktop.materialize` still materializes the packaged desktop home in app-private storage.
   - The browser-lane smoke still auto-runs `pod.desktop.materialize`, `runtime-smoke`, `tool-brief-inspect`, and `plugin-allowlist-inspect`, and it now reruns `runtime-smoke` once more after browser replay is ready so the runtime contracts see the browser dependency as present.
   - The refreshed `PFEM10` browser-lane replay now records `runtimeExecuteAfterBrowser.longLivedProcessReady=true`, `processStatus=standby`, `supervisionStatus=active`, `activeSessionStatus=ready`, `activeSessionObserved=true`, `activeSessionValidationStatus=validated`, and `activeSessionDeviceProofStatus=verified`.

4. The latest targeted repo validation is green again on the current machine.
   - `pnpm test -- apps/android/runtime-pod/prepare.test.ts apps/android/runtime-pod/sync-assets.test.ts` passes.
   - The targeted Android Gradle rerun for `EmbeddedRuntimePodStatusTest`, `PodHandlerTest`, and `InvokeCommandRegistryTest` also passes.

5. The Android and project-level handoff docs were updated to reflect the new verified state.
   - Repo-verified `0.17.0` repeated-replay live-proof capture is now documented explicitly.
   - The latest real-device state is also updated to the April 10, 2026 `0.17.0` `process_runtime_active_session_live_proof_captured` replay, plus the browser-aligned runtime summary that now exposes the captured proof directly.

6. Replay hardening now also has verified explicit perturbation and soak paths.
   - `pnpm android:local-host:embedded-runtime-pod:stability` now accepts `--restart-app-between-iterations`, so the same wrapper can force-stop/start `ai.openclaw.app` between doctor passes instead of only looping through the no-perturbation path.
   - On April 10, 2026 the same `PFEM10` real-device lane still passed that stronger restart-perturbation run with `perturbationMode=app_restart_between_iterations`, `perturbationAppliedCount=2`, `perturbationFailureCount=0`, and the same captured live-proof classification across all three doctor passes.
   - The new `pnpm android:local-host:embedded-runtime-pod:soak` wrapper now packages the next longer hardening move as one formal command: it defaults to `--iterations 5 --restart-app-between-iterations`, rewrites `packageCommand` to `pnpm android:local-host:embedded-runtime-pod:soak`, and already passed on the same `PFEM10` lane with `passedIterationCount=5`, `failedIterationCount=0`, and `perturbationAppliedCount=4`.
   - The top-level runtime status now surfaces `recommendedHardeningCommand`, while the captured active-session-device-proof artifact now also surfaces `hardeningCommand`, so both machine-readable and human-readable handoff paths can point directly at the soak wrapper instead of a longer raw stability command line.

## Validation Run

- `pnpm test -- apps/android/runtime-pod/prepare.test.ts apps/android/runtime-pod/sync-assets.test.ts`
  - Passed on April 6, 2026.
- `cd apps/android && env ANDROID_HOME="$HOME/Library/Android/sdk" ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" ./gradlew --no-daemon --console=plain :app:testDebugUnitTest --tests ai.openclaw.app.EmbeddedRuntimePodStatusTest --tests ai.openclaw.app.node.PodHandlerTest --tests ai.openclaw.app.node.InvokeCommandRegistryTest`
  - Passed on April 6, 2026.
- `pnpm android:local-host:embedded-runtime-pod:doctor -- --json`
  - Latest completed real-device proof is the April 10, 2026 run on `PFEM10`.
  - Final verified real-device outcome from that run: `classification=process_runtime_active_session_live_proof_captured`.
  - The same doctor artifact also records `browserLaneSmoke.summary.runtimeExecuteAfterBrowser.longLivedProcessReady=true`, `processStatus=standby`, `supervisionStatus=active`, `activeSessionStatus=ready`, `activeSessionObserved=true`, `activeSessionValidationStatus=validated`, `activeSessionDeviceProofStatus=verified`, and `confirmBrowserLaneSmoke.liveProofContinuity.preserved=true`.
- `pnpm android:local-host:embedded-runtime-pod:stability -- --json --iterations 3`
  - Passed on April 10, 2026 on `PFEM10`.
  - The aggregated summary reports `ok=true`, `passedIterationCount=3`, `failedIterationCount=0`, `classifications=["process_runtime_active_session_live_proof_captured"]`, and `recommendedNextSlices=["process_runtime_lane_hardening"]`.
- `pnpm android:local-host:embedded-runtime-pod:stability -- --json --iterations 3 --restart-app-between-iterations`
  - Passed on April 10, 2026 on `PFEM10`.
  - The aggregated summary reports `ok=true`, `perturbationMode="app_restart_between_iterations"`, `perturbationAppliedCount=2`, `perturbationFailureCount=0`, `passedIterationCount=3`, `failedIterationCount=0`, and `classifications=["process_runtime_active_session_live_proof_captured"]`.
- `pnpm android:local-host:embedded-runtime-pod:soak -- --json`
  - Passed on April 10, 2026 on `PFEM10`.
  - The aggregated summary reports `ok=true`, `packageCommand="pnpm android:local-host:embedded-runtime-pod:soak"`, `iterationsRequested=5`, `perturbationAppliedCount=4`, `perturbationFailureCount=0`, `passedIterationCount=5`, `failedIterationCount=0`, and `classifications=["process_runtime_active_session_live_proof_captured"]`.
- `pnpm test -- test/apps/android/local-host-embedded-runtime-pod-soak.test.ts test/apps/android/local-host-embedded-runtime-pod-stability.test.ts test/apps/android/local-host-embedded-runtime-pod-doctor.test.ts test/apps/android/local-host-embedded-runtime-browser-lane-smoke.test.ts`
  - Passed on April 10, 2026 after adding the new soak wrapper and wrapper-identity rewrite.
  - The targeted suite now covers the new formal soak entrypoint, the shorter restart-perturbation replay, and the failure path where the adb restart perturbation itself regresses, while keeping the surrounding doctor and browser-lane smoke coverage green.
- `cd apps/android && env ANDROID_HOME="$HOME/Library/Android/sdk" ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" ./gradlew --no-daemon --console=plain :app:testDebugUnitTest --tests ai.openclaw.app.node.PodHandlerTest`
  - Passed on April 10, 2026 after surfacing the soak command through the runtime status / artifact layer.

## Current Risks

- The branch still does not provide full executable desktop parity. Generic browser tooling, unrestricted shell parity, generic plugin/runtime parity, and stronger detached-process observation/recovery semantics are still missing.
- The new live proof is still a bounded repeated-replay contract, not a claim of full executable desktop parity or generic detached-process supervision.
- The captured proof currently depends on the existing browser-aligned replay flow and repeated `runtime-smoke` executions; we should keep replayability boring before widening scope.
- The new doctor continuity guard reduces the risk of silently treating a downgraded confirm replay as healthy, but the real-device `PFEM10` lane still remains the source of truth for whether lease renewal, recovery re-entry, restart continuity, and proof-artifact completeness stay stable on actual hardware.

## Recommended Next Move

- Keep the current `PFEM10` replay boringly repeatable at `classification=process_runtime_active_session_live_proof_captured`, and preserve the new `runtimeExecuteAfterBrowser` artifact in `browser-lane-smoke`.
- Prefer `pnpm android:local-host:embedded-runtime-pod:stability -- --json --iterations 3` when the question is "did replay stay boringly repeatable?", then drill into one iteration's doctor artifacts only if that aggregate summary fails.
- The longer soak replay is now green too, so the next hardening move should keep `pnpm android:local-host:embedded-runtime-pod:soak -- --json` as the default repeatability lane before widening the runtime surface again.
- Treat `process_runtime_lane_hardening` as the next slice instead of reopening the earlier plugin-lane decision.
- Keep the current `doctor` + `smoke` + `browser-lane:smoke` path boringly replayable before widening the branch surface again.
