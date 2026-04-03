# Task Plan

## Immediate Tasks

1. Decide the next desktop-runtime slice after `desktop_home_configured`.
   - Option A: deepen the engine/environment lane so the materialized desktop home runs a more meaningful packaged workflow.
   - Option B: add one narrowly allowlisted plugin/runtime slice now that desktop-home materialization is proven on-device.
   - Exit criteria: one explicit branch-level decision, recorded in docs, for what comes after `desktop_home_configured`.

2. Turn the direct `pod.desktop.materialize` replay into a stable repo command.
   - Today the proof exists, but the clearest verification still used an ad hoc curl + adb sequence to capture `active-profile.json` and `desktop-materialize.json`.
   - Exit criteria: one repeatable repo entrypoint that invokes `pod.desktop.materialize`, verifies desktop-home state, and writes artifacts in one place.

3. Stabilize the broader pod validation lane.
   - `InvokeCommandRegistryTest` and the real-device replay are green, but the wider `PodHandlerTest` lane still fails with `KeyStoreException` / `NoSuchAlgorithmException` in this environment.
   - Exit criteria: either the broader test lane exits cleanly or the unstable portion is isolated and documented so the default validation path is reliable.

## Short-Term Tasks

1. Keep the current real-device replay boringly repeatable.
   - Re-run `pnpm android:local-host:embedded-runtime-pod:doctor -- --json` after the next desktop-runtime slice.
   - Exit criteria: the branch still converges to the expected top-level classification after reinstall and token bootstrap.

2. Review whether the current `recommendedNextSlice=plugin_lane` should be followed literally.
   - The status surface now points at `plugin_lane`, but the branch objective may still benefit more from deeper engine/environment execution first.
   - Exit criteria: one short keep/override rationale in the Android docs.

3. Keep doc/task surfaces aligned with the Android mainline rather than old fork-management work.
   - `project-docs/` is now repointed at the Android desktop-runtime branch and should stay that way until the workstream changes.
   - Exit criteria: no stale fork/auth-fallback task lists remain as the default project-management entrypoint.

## Deferred Tasks

1. If the direct desktop-home verification command proves stable:
   - Fold it into the broader Android README and preferred replay order.
   - Promote it alongside `doctor`, `smoke`, and `browser-lane:smoke`.

2. If the next slice materially changes user-visible Android behavior:
   - Refresh the Android handoff/checkpoint docs again.
   - Capture one new real-device evidence block before widening further.
