# Task Plan

## Immediate Tasks

1. Re-run the branch on `PFEM10` with payload `0.17.0` until the top-level result reaches `process_runtime_active_session_device_proof_bootstrapped`.
   - Current state: repo-side validation is green, but the latest real-device proof still stops at April 4, 2026 `0.7.0` `plugin_lane_replayed`.
   - Exit criteria: `pnpm android:local-host:embedded-runtime-pod:doctor -- --json` converges to `classification=process_runtime_active_session_device_proof_bootstrapped` and `runtimeDescribeAfter.recommendedNextSlice=process_runtime_active_session_live_proof`.

2. Capture the next live-proof slice as `process_runtime_active_session_live_proof`.
   - The branch no longer needs a decision about whether plugin/process-model/process-runtime-activation/process-runtime-supervision/process-runtime-observation/process-runtime-recovery/process-runtime-detached-launch/process-runtime-supervisor-loop/bootstrap-validation/bootstrap-device-proof should exist; those are now settled repo-side.
   - Exit criteria: the bounded active-session-device-proof contract turns into one real detached active session on-device with observed lease renewal, recovery re-entry, and restart continuity.

3. Keep the targeted pod validation lane stable while supervisor-loop and active-session work deepen.
   - The current green path is `pnpm test -- apps/android/runtime-pod/prepare.test.ts apps/android/runtime-pod/sync-assets.test.ts` plus targeted Android Gradle reruns for `EmbeddedRuntimePodStatusTest`, `PodHandlerTest`, and `InvokeCommandRegistryTest`.
   - Exit criteria: those validations keep passing as the process-runtime supervisor-loop/active-session work lands.

## Short-Term Tasks

1. Keep the current real-device replay boringly repeatable.
   - Re-run `doctor`, `smoke`, and `browser-lane:smoke` after each meaningful desktop-runtime slice.
   - Exit criteria: the branch still converges to the expected top-level classification after reinstall and token bootstrap.

2. Keep the active-session-device-proof contract and the live-proof checklist aligned.
   - Focus on observable process state, lease ownership, restart generation, and recovery re-entry semantics rather than widening browser/tools/plugins again.
   - Exit criteria: the docs and runtime status surface both describe one coherent active-session-device-proof model, and the next gap is only live device capture.

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
