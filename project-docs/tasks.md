# Task Plan

## Immediate Tasks

1. Keep the refreshed `PFEM10` replay boringly repeatable on payload `0.17.0`.
   - Current state: on April 8, 2026 `pnpm android:local-host:embedded-runtime-pod:doctor -- --json` again converged to `classification=process_runtime_active_session_live_proof_captured`, the browser-lane smoke records `runtimeExecuteAfterBrowser.activeSessionObserved=true`, `activeSessionValidationStatus=validated`, `activeSessionDeviceProofStatus=verified`, and the doctor wrapper's confirm-only browser-lane pass still leaves both `confirmBrowserLaneSmoke.liveProofReplayed=true` and `confirmBrowserLaneSmoke.liveProofContinuity.preserved=true`.
   - Current guardrail: the doctor summary now also exposes `confirmBrowserLaneSmoke.liveProofContinuity`, and the browser-lane smoke now carries validation/device-proof observations such as lease renewal, recovery re-entry, restart continuity, and captured-vs-expected proof artifact counts into `runtimeExecuteAfterBrowser`.
   - Exit criteria: repeated `doctor` / `browser-lane:smoke` reruns on the current build keep converging to `classification=process_runtime_active_session_live_proof_captured`, keep leaving the browser-aligned runtime summary in place, keep preserving `confirmBrowserLaneSmoke.liveProofReplayed=true`, and keep reporting `confirmBrowserLaneSmoke.liveProofContinuity.preserved=true` without losing lease-renewal or proof-artifact completeness.

2. Preserve the captured live-proof slice as `process_runtime_lane_hardening`.
   - The branch no longer needs a decision about whether plugin/process-model/process-runtime-activation/process-runtime-supervision/process-runtime-observation/process-runtime-recovery/process-runtime-detached-launch/process-runtime-supervisor-loop/bootstrap-validation/bootstrap-device-proof/live-proof capture should exist; those are now settled.
   - Exit criteria: the bounded live active-session proof remains replayable on-device with observed lease renewal, recovery re-entry, and restart continuity across repeated reruns.

3. Keep the targeted pod validation lane stable while process-runtime hardening deepens.
   - The current green path is `pnpm test -- apps/android/runtime-pod/prepare.test.ts apps/android/runtime-pod/sync-assets.test.ts` plus targeted Android Gradle reruns for `EmbeddedRuntimePodStatusTest`, `PodHandlerTest`, and `InvokeCommandRegistryTest`.
   - Exit criteria: those validations keep passing as the process-runtime supervisor-loop/active-session work lands.

## Short-Term Tasks

1. Keep the current real-device replay boringly repeatable.
   - Re-run `doctor`, `smoke`, and `browser-lane:smoke` after each meaningful desktop-runtime slice.
   - Exit criteria: the branch still converges to the expected top-level classification after reinstall and token bootstrap.

2. Keep the active-session-device-proof contract and the live-proof checklist aligned.
   - Focus on observable process state, lease ownership, lease-renewal observation, restart generation, recovery re-entry semantics, restart continuity, proof-artifact completeness, and the browser-aligned `runtimeExecuteAfterBrowser` artifact rather than widening browser/tools/plugins again.
   - Exit criteria: the docs and runtime status surface both describe one coherent captured-live-proof model, and the next gap is only hardening.

3. Keep doc/task surfaces aligned with the Android mainline rather than old fork-management work.
   - `project-docs/` should continue to mirror the Android desktop-runtime branch state.
   - Exit criteria: the default handoff/task entrypoints no longer suggest reopening the plugin-lane decision.

## Deferred Tasks

1. If the first activation slice proves stable:
   - Fold its verification path into the broader Android README and preferred replay order.
   - Promote it alongside `doctor`, `smoke`, and `browser-lane:smoke`.

2. If the next slice materially changes user-visible Android behavior:
   - Refresh the Android handoff/checkpoint docs again.
   - Capture one new real-device evidence block before widening further.
