# Task Plan

## Immediate Tasks

1. Re-run the branch on `PFEM10` with payload `0.10.0` until the top-level result reaches `process_runtime_supervision_bootstrapped`.
   - Current state: repo-side validation is green, but the latest real-device proof still stops at April 4, 2026 `0.7.0` `plugin_lane_replayed`.
   - Exit criteria: `pnpm android:local-host:embedded-runtime-pod:doctor -- --json` converges to `classification=process_runtime_supervision_bootstrapped` and `runtimeDescribeAfter.recommendedNextSlice=process_runtime_observation`.

2. Implement the next bounded slice as `process_runtime_observation`.
   - The branch no longer needs a decision about whether plugin/process-model/process-runtime-activation/process-runtime-supervision bootstrap should exist; those are now settled.
   - Exit criteria: the bounded long-lived runtime session gains stronger observation and recovery semantics beyond the current descriptor/state bootstrap artifacts.

3. Keep the targeted pod validation lane stable while runtime supervision deepens.
   - The current green path is `pnpm test -- apps/android/runtime-pod/prepare.test.ts apps/android/runtime-pod/sync-assets.test.ts` plus targeted Android Gradle reruns for `EmbeddedRuntimePodStatusTest`, `PodHandlerTest`, and `InvokeCommandRegistryTest`.
   - Exit criteria: those validations keep passing as the process-runtime supervision work lands.

## Short-Term Tasks

1. Keep the current real-device replay boringly repeatable.
   - Re-run `doctor`, `smoke`, and `browser-lane:smoke` after each meaningful desktop-runtime slice.
   - Exit criteria: the branch still converges to the expected top-level classification after reinstall and token bootstrap.

2. Define the first real activation contract for the desktop process model.
   - Focus on observable process state, health, restart generation, and supervisor semantics rather than widening browser/tools/plugins again.
   - Exit criteria: the docs and runtime status surface both describe one coherent activation model.

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
