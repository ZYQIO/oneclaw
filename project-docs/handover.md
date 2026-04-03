# Project Handover

## Snapshot

- Date: 2026-04-03
- Active branch: `android-desktop-runtime-mainline-20260403`
- Remote branch: `origin/android-desktop-runtime-mainline-20260403`
- Recent verified commits:
  - `b74e3d1b87` `Android: fix desktop materialize invoke wiring`
  - `ed3b0e8f27` `docs(android): record desktop home device replay`

## What Was Done

1. Real-device desktop-runtime replay was completed on the connected OPPO / ColorOS `PFEM10` phone.
   - The first `pnpm android:local-host:embedded-runtime-pod:doctor -- --json` run correctly detected a stale `0.2.0` device build.
   - After reinstalling the current debug app, the same flow converged to `manifestVersion=0.6.0`, `verifiedFileCount=24`, and `classification=desktop_home_configured`.

2. The packaged browser lane and runtime/tool carrier were reconfirmed on-device.
   - `pod.browser.describe` now shows replayable state on disk plus a stored credential.
   - `pod.runtime.execute(taskId=runtime-smoke)` and `pod.runtime.execute(taskId=tool-brief-inspect)` both replay cleanly against the packaged `0.6.0` payload.

3. A real repo bug was exposed and fixed during direct `pod.desktop.materialize` replay.
   - Remote `/invoke/capabilities` advertised `pod.desktop.materialize`.
   - Actual `/invoke` returned `INVALID_REQUEST: unknown command`.
   - Root cause: `InvokeCommandRegistry` was missing `OpenClawPodCommand.DesktopMaterialize`.
   - Fix landed in `b74e3d1b87`.

4. The direct desktop-home bridge now has real-device proof.
   - `pod.desktop.materialize` returns `desktopHomeReady=true`.
   - The app writes `filesDir/openclaw/embedded-desktop-home/0.6.0/profiles/active-profile.json`.
   - The app writes `filesDir/openclaw/embedded-desktop-home/0.6.0/state/desktop-materialize.json`.

5. The Android handoff/progress/checkpoint docs were updated to reflect the new verified state.
   - The verification queue now records both the stale-build failure and the successful replay.
   - The checkpoint now treats `pod.desktop.materialize` as proven on-device, not pending.

## Validation Run

- `cd apps/android && env ANDROID_HOME="$HOME/Library/Android/sdk" ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" ./gradlew --no-daemon --console=plain :app:installDebug`
  - Passed and installed on the connected `PFEM10` device.
- `pnpm android:local-host:token -- --json`
  - Passed and exported the current local-host bearer token from the debug app.
- `pnpm android:local-host:embedded-runtime-pod:doctor -- --json`
  - Passed.
  - Final verified outcome: `classification=desktop_home_configured`.
- Direct remote `pod.desktop.materialize` invoke over the phone's guarded local-host API
  - Passed after the registry fix.
  - Returned `desktopHomeReady=true`.
- `cd apps/android && env ANDROID_HOME="$HOME/Library/Android/sdk" ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" ./gradlew --no-daemon --console=plain :app:testDebugUnitTest --tests ai.openclaw.app.node.InvokeCommandRegistryTest`
  - Passed.
- `cd apps/android && env ANDROID_HOME="$HOME/Library/Android/sdk" ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" ./gradlew --no-daemon --console=plain :app:compileDebugKotlin`
  - Passed.

## Current Risks

- The branch still does not provide full executable desktop parity. Generic browser tooling, unrestricted shell parity, and executable plugin/runtime parity are still missing.
- The broad `PodHandlerTest` lane is not yet a clean validation gate in this environment; several tests currently fail with `KeyStoreException` / `NoSuchAlgorithmException`, which looks environmental rather than caused by the desktop-home fix.
- `pnpm android:local-host:embedded-runtime-pod:doctor` now reports the correct top-level `desktop_home_configured` classification, but the next slice itself is still a product/engineering decision rather than an automatic continuation.

## Recommended Next Move

- Decide the next Android desktop-runtime slice explicitly:
  - Option A: deepen the engine/environment path so the materialized desktop home runs a more substantial packaged task.
  - Option B: add one narrowly allowlisted plugin/runtime slice now that `desktop_home_configured` is proven.
- Add one dedicated repo entrypoint that verifies `pod.desktop.materialize` directly and captures the desktop-home state artifacts in one place, instead of relying on an ad hoc curl sequence.
- Keep the current `doctor` + pod smoke + direct materialize path boringly replayable on the same device before widening the branch surface again.
