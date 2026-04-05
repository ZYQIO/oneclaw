# Progress Tracker

## Status Legend

- `done`: completed and verified enough for handoff
- `in_progress`: active work with usable partial results
- `blocked`: cannot safely continue without a product or repo decision
- `pending`: not started yet

## Current Workstreams

| ID  | Workstream                                     | Status      | Evidence                                                                                                                                                                                                                                                                      | Next Action                                                                                                     |
| --- | ---------------------------------------------- | ----------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| W1  | Device replay for desktop home and plugin lane | done        | Real OPPO / ColorOS `PFEM10` replay reached `classification=plugin_lane_replayed` on payload `0.7.0`, with `desktopMaterialize.ok=true` and `pluginExecute.ok=true`                                                                                                           | Re-run on the same device after the current `0.9.0` slice to refresh the device baseline                        |
| W2  | `pod.desktop.materialize` wiring               | done        | Commit `b74e3d1b87` fixed the missing `InvokeCommandRegistry` entry and direct on-device materialize now succeeds                                                                                                                                                             | Keep the registry/test path stable whenever pod commands expand again                                           |
| W3  | Android doc alignment                          | done        | Android checkpoint/handoff/progress/verification docs now distinguish repo-verified `0.9.0` activation bootstrap from the latest April 4 real-device `0.7.0` proof                                                                                                            | Refresh again only after the next verified branch state change                                                  |
| W4  | Process-runtime activation bootstrap slice     | in_progress | Repo-side `0.9.0` validation is green: `runtime-smoke` now writes `runtime-smoke-process-model.json` plus `runtime-smoke-activation-contract.json`, `pod.runtime.describe` exposes activation fields, and the next device target is `process_runtime_activation_bootstrapped` | Reinstall the current debug app on `PFEM10`, rerun `doctor`, and then deepen into `process_runtime_supervision` |
| W5  | Targeted pod regression lane                   | done        | `pnpm test -- apps/android/runtime-pod/prepare.test.ts apps/android/runtime-pod/sync-assets.test.ts` plus targeted Android Gradle reruns for `EmbeddedRuntimePodStatusTest`, `PodHandlerTest`, and `InvokeCommandRegistryTest` pass on April 5, 2026                          | Keep this targeted validation lane green whenever runtime/browser/process-model/activation pod surfaces expand  |

## Operating Rules

1. Update this file at the end of each Android desktop-runtime session.
2. Record exact commit IDs when a workstream changes state.
3. Prefer real-device evidence over doc-only status claims for new desktop-runtime slices.
4. Do not reopen the old assumption that helper/status surfaces alone answer the desktop-runtime objective.
