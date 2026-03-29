## OpenClaw Android App

Status: **extremely alpha**. The app is actively being rebuilt from the ground up.

### Android Local Host Tracking / Android 本机 Host 跟踪

- Progress tracker / 进度跟踪: `apps/android/local-host-progress.md`
- Self-check gate / 自检门槛: `apps/android/local-host-self-check.md`
- Session handoff / 接续手册: `apps/android/local-host-handoff.md`
- Dedicated-device plan / 专机部署方案: `apps/android/local-host-dedicated-device.md`

### Local Host Scope Today / 当前 Local Host 范围

- `Local Host` currently provides on-device Codex-backed chat, a curated Android command surface, an app-private on-device workspace for text files with search/edit/copy/move support, and a dedicated idle-phone deployment mode that can keep the host service alive across app relaunches, package upgrades, and reboots.
- When the phone is connected to a trusted desktop, the desktop can now inspect its own `openai-codex` OAuth state and push that credential into the phone's guarded local-host API so the phone can recover from missing / stale auth without another browser login.
- The app now supports a settings-driven English / Simplified Chinese toggle across the tab bar, Connect tab, Settings tab, onboarding flow, Chat / Voice primary surfaces, Voice runtime/microphone status copy, Voice reply / TTS detail status copy, common gateway auth/pairing edge states, the browser-based Codex auth success/failure page, and several runtime/auth status strings. Some deeper secondary copy still remains to be localized.
- It does **not** yet bundle the full desktop Gateway/CLI runtime, shell access, browser tools, or plugin runtime.
- If GPT replies work but many desktop-style actions do not, that is expected with the current Android MVP scope.

### Dedicated Host Deployment / 专用 Host 部署

- Use this mode when an idle Android phone should behave like a small always-on OpenClaw host.
- When enabled in `Connect -> Local Host`, the app keeps the foreground service alive, restores it after reboot or app updates, and attempts to auto-heal the Local Host connection if it drops.
- The Local Host screen now also surfaces Android battery-optimization state and links to the battery-exemption flow, because that exemption materially improves dedicated-host restart behavior on real phones.
- Remote `device.status` now includes `backgroundExecution.batteryOptimizationIgnored`, so a remote operator can tell whether the phone is in a better keepalive posture.
- Remote `/status` now also includes a `host.deployment` block with dedicated-host readiness fields such as `dedicatedEnabled`, `keepAliveEligible`, and `batteryOptimizationIgnored`.
- Real-device validation now confirms the battery-exemption CTA can flip both Android's `deviceidle` whitelist state and remote `/status.host.deployment.batteryOptimizationIgnored=true`.
- If Android removes the foreground service task while dedicated mode is enabled, OpenClaw now schedules a short recovery alarm so the local host can come back instead of waiting only for a manual reopen or reboot.
- Dedicated mode now also keeps a low-frequency watchdog alarm armed, so long-idle phones have another path to restart the foreground service even when the short recovery path was never triggered.
- On the validated OPPO / ColorOS phone, swiping the OpenClaw card away from Recents still force-stops the package and clears alarms even after the battery exemption is granted, so dedicated deployments on that device family should keep the app locked in Recents and avoid swipe-to-clear.
- The Local Host UI and readiness snapshot now surface this OEM background-policy risk so the idle-phone deployment story is explicit instead of implicit.
- This is a keepalive layer for the current Android-native host. It is not yet the full desktop shell/browser/plugin runtime embedded in the APK.
- For spare-phone deployment planning, use `pnpm android:local-host:dedicated:readiness` to decide whether the current device should go through the `Device Owner` lane first or whether root/systemize work is even worth considering yet.
- When the device is close to ready, use `pnpm android:local-host:dedicated:device-owner` as a dry-run wrapper before attempting a real `adb shell dpm set-device-owner ...` provisioning step.
- To remove the current `TestDPC not installed` blocker without hunting for APKs manually, use `pnpm android:local-host:dedicated:testdpc-install` to fetch the latest public TestDPC release and print the exact `adb install -r -d ...` command, or add `-- --apply` to install it.
- For the official factory-reset path, use `pnpm android:local-host:dedicated:testdpc-qr` to fetch the latest public `TestDPC` GitHub release, compute a checksum, and render a provisioning QR for setup wizard scanning.
- After provisioning, use `pnpm android:local-host:dedicated:post-provision` to check owner state, lock-task allowlisting, launcher resolution, and OpenClaw's own dedicated/local-host flags in one place.
- When TestDPC is already installed as `Device Owner`, use `pnpm android:local-host:dedicated:testdpc-kiosk` as the DPC-side kiosk dry-run or add `-- --apply` to enable TestDPC's `KioskModeActivity` for `ai.openclaw.app`.
- The kiosk helper is intentionally conservative: on March 28, 2026, the current OPPO `PFEM10` dry-run correctly stopped at `TestDPC not installed` plus `not the active Device Owner`, even though OpenClaw itself was already dedicated-ready on the app side.
- In apply mode, the helper makes TestDPC's kiosk activity the persistent HOME activity and keeps TestDPC itself in the kiosk backdoor package list, so treat it as a spare-phone-only action.
- Once a DPC allowlists `ai.openclaw.app` for lock-task, `MainActivity` now advertises `android:lockTaskMode=\"if_whitelisted\"` and auto-enters lock-task on launch when dedicated deployment is enabled and the app is already onboarded in local-host mode.

### Rebuild Checklist

- [x] New 4-step onboarding flow
- [x] Connect tab with `Setup Code` + `Manual` modes
- [x] Encrypted persistence for gateway setup/auth state
- [x] Chat UI restyled
- [x] Settings UI restyled and de-duplicated (gateway controls moved to Connect)
- [x] QR code scanning in onboarding
- [x] Performance improvements
- [x] Streaming support in chat UI
- [x] Request camera/location and other permissions in onboarding/settings flow
- [x] Push notifications for gateway/chat status updates
- [x] Security hardening (biometric lock, token handling, safer defaults)
- [x] Voice tab full functionality
- [x] Screen tab full functionality
- [ ] Full end-to-end QA and release hardening

## Open in Android Studio

- Open the folder `apps/android`.

## Build / Run

```bash
cd apps/android
./gradlew :app:assembleDebug
./gradlew :app:installDebug
./gradlew :app:testDebugUnitTest
cd ../..
bun run android:bundle:release
```

`bun run android:bundle:release` auto-bumps Android `versionName`/`versionCode` in `apps/android/app/build.gradle.kts`, then builds a signed release `.aab`.

## Kotlin Lint + Format

```bash
pnpm android:lint
pnpm android:format
```

Android framework/resource lint (separate pass):

```bash
pnpm android:lint:android
```

Direct Gradle tasks:

```bash
cd apps/android
./gradlew :app:ktlintCheck :benchmark:ktlintCheck
./gradlew :app:ktlintFormat :benchmark:ktlintFormat
./gradlew :app:lintDebug
```

`gradlew` auto-detects the Android SDK at `~/Library/Android/sdk` (macOS default) if `ANDROID_SDK_ROOT` / `ANDROID_HOME` are unset.

## Macrobenchmark (Startup + Frame Timing)

```bash
cd apps/android
./gradlew :benchmark:connectedDebugAndroidTest
```

Reports are written under:

- `apps/android/benchmark/build/reports/androidTests/connected/`

## Perf CLI (low-noise)

Deterministic startup measurement + hotspot extraction with compact CLI output:

```bash
cd apps/android
./scripts/perf-startup-benchmark.sh
./scripts/perf-startup-hotspots.sh
```

Benchmark script behavior:

- Runs only `StartupMacrobenchmark#coldStartup` (10 iterations).
- Prints median/min/max/COV in one line.
- Writes timestamped snapshot JSON to `apps/android/benchmark/results/`.
- Auto-compares with previous local snapshot (or pass explicit baseline: `--baseline <old-benchmarkData.json>`).

Hotspot script behavior:

- Ensures debug app installed, captures startup `simpleperf` data for `.MainActivity`.
- Prints top DSOs, top symbols, and key app-path clues (Compose/MainActivity/WebView).
- Writes raw `perf.data` path for deeper follow-up if needed.

## Run on a Real Android Phone (USB)

1) On phone, enable **Developer options** + **USB debugging**.
2) Connect by USB and accept the debugging trust prompt on phone.
3) Verify ADB can see the device:

```bash
adb devices -l
```

4) Install + launch debug build:

```bash
pnpm android:install
pnpm android:run
```

If `adb devices -l` shows `unauthorized`, re-plug and accept the trust prompt again.

## Local Host Remote Smoke

Use this after the app is already running in `Local Host` mode and remote access is enabled.

USB-friendly flow with `adb forward`:

```bash
OPENCLAW_ANDROID_LOCAL_HOST_TOKEN='<token-from-connect-tab>' \
OPENCLAW_ANDROID_LOCAL_HOST_USE_ADB_FORWARD=1 \
pnpm android:local-host:smoke
```

Direct LAN flow:

```bash
OPENCLAW_ANDROID_LOCAL_HOST_BASE_URL='http://<phone-ip>:3945' \
OPENCLAW_ANDROID_LOCAL_HOST_TOKEN='<token-from-connect-tab>' \
pnpm android:local-host:smoke
```

Optional overrides:

- `OPENCLAW_ANDROID_LOCAL_HOST_MESSAGE=...`
- `OPENCLAW_ANDROID_LOCAL_HOST_INVOKE_COMMAND=device.status`
- `OPENCLAW_ANDROID_LOCAL_HOST_INVOKE_PARAMS='{"includePermissions":true}'`
- `OPENCLAW_ANDROID_LOCAL_HOST_WAIT_MS=30000`
- `OPENCLAW_ANDROID_LOCAL_HOST_PORT=3945`

The smoke script validates `/status`, `/chat/send-wait`, `/invoke/capabilities`, and `/invoke`, then prints a compact summary.

## Local Host Codex Auth Sync

Use this when the phone is already paired to a trusted desktop and the phone's Codex auth has gone missing, expired, or is close enough to expiry that you would rather reuse the desktop login than re-open the browser flow.

USB + adb forward flow:

```bash
OPENCLAW_ANDROID_LOCAL_HOST_TOKEN='<token-from-connect-tab>' \
OPENCLAW_ANDROID_LOCAL_HOST_USE_ADB_FORWARD=1 \
pnpm android:local-host:codex-sync
```

Default desktop-guard flow:

```bash
OPENCLAW_ANDROID_LOCAL_HOST_TOKEN='<token-from-connect-tab>' \
pnpm android:local-host:codex-guard
```

Direct LAN flow:

```bash
OPENCLAW_ANDROID_LOCAL_HOST_BASE_URL='http://<phone-ip>:3945' \
OPENCLAW_ANDROID_LOCAL_HOST_TOKEN='<token-from-connect-tab>' \
pnpm android:local-host:codex-sync
```

Optional overrides:

- `OPENCLAW_ANDROID_LOCAL_HOST_PORT=3945`
- `pnpm android:local-host:codex-sync -- --force`
- `pnpm android:local-host:codex-sync -- --wait-for-device`
- `pnpm android:local-host:codex-sync -- --wait-for-device --device-poll-interval-ms 2000`
- `pnpm android:local-host:codex-sync -- --watch`
- `pnpm android:local-host:codex-sync -- --wait-for-device --watch`
- `pnpm android:local-host:codex-sync -- --watch --watch-interval-ms 45000`
- `pnpm android:local-host:codex-sync -- --watch --watch-max-runs 10`
- `pnpm android:local-host:codex-sync -- --adb-bin /path/to/adb`
- `pnpm android:local-host:codex-sync -- --artifact-dir .tmp/android-codex-guard`
- `pnpm android:local-host:codex-sync -- --agent-dir /path/to/agent`
- `pnpm android:local-host:codex-sync -- --json`

The sync command reads the preferred desktop `openai-codex` OAuth credential from the auth-profile store, checks the phone's `/auth/codex/status`, and only pushes desktop auth down when the phone is missing auth, already expired, or already in the refresh-warning window. If the desktop credential itself is also close to expiry, the command follows the import with `/auth/codex/refresh` on the phone so the phone ends on a fresh token set. Treat this as a trusted-path feature only: it reuses the existing bearer-protected local-host API and should stay on `adb forward`, localhost, or a trusted LAN/tunnel.

When you want the desktop to act more like a connection-aware guard, add `--wait-for-device` so the command blocks until adb sees a connected phone instead of failing fast. Pair that with `--watch` and the desktop will keep polling the phone, rerun the same refill logic whenever the phone auth later becomes missing, expired, or refresh-recommended again, and keep looping across short transport failures instead of exiting on the first transient disconnect. Plain-text watch output is line-oriented for terminal use; `--json` switches watch mode to one compact JSON object per iteration, including recoverable error iterations.

If you just want the opinionated default guard behavior, `pnpm android:local-host:codex-guard` is a thin wrapper around `codex-sync --use-adb-forward --wait-for-device --watch`. In JSON watch mode the stream is now typed: lifecycle events use `kind="lifecycle"`, success iterations use `kind="iteration"`, and recoverable failures use `kind="error"`, so an outer supervisor can distinguish connection state changes from actual sync passes.

If you also pass `--artifact-dir`, the guard writes durable files under that directory: `events.jsonl` for the append-only event stream, `latest.json` for the newest lifecycle or sync snapshot, and `summary.json` for one-shot sync runs. That gives launchd/tmux/agent wrappers a stable file surface without having to scrape terminal output.

On macOS, if you want the guard to survive terminal restarts and login sessions more naturally, there is now also a LaunchAgent helper:

```bash
pnpm android:local-host:codex-guard:launchd -- setup
# edit ~/.openclaw/android-local-host-codex-guard/guard.env and replace the placeholder token
pnpm android:local-host:codex-guard:launchd -- setup
pnpm android:local-host:codex-guard:launchd -- status
```

You can also seed the env file directly from the current shell or a pasted token:

```bash
OPENCLAW_ANDROID_LOCAL_HOST_TOKEN='<token-from-connect-tab>' \
pnpm android:local-host:codex-guard:launchd -- setup

pnpm android:local-host:codex-guard:launchd -- setup --token '<token-from-connect-tab>'
```

`setup` is now the preferred first-run entrypoint: when the env file is missing it writes a template, when a token is provided it seeds the env file, and once the guard is ready it installs or repairs the LaunchAgent automatically. The helper keeps artifacts under `~/.openclaw/android-local-host-codex-guard/` by default and keeps the bearer token in the external env file instead of copying it into the LaunchAgent plist. `status` also reports whether that env file exists, whether it still contains only the placeholder token, a `recommendedAction` field that tells you whether the next step is `write-env`, `configure-token`, `install`, or `check-launchagent`, and a `recommendedCommand` string you can run directly. By default it uses `adb forward`; if the desktop LaunchAgent context cannot find `adb` reliably, pass `--adb-bin /path/to/adb` at setup or install time so the generated wrapper pins an absolute binary path.

## Local Host UI Automation Smoke

Use this when you want one repeatable observe / wait / act proof for the current on-device phone-control loop.

USB-friendly flow with `adb forward`:

```bash
OPENCLAW_ANDROID_LOCAL_HOST_TOKEN='<token-from-connect-tab>' \
OPENCLAW_ANDROID_LOCAL_HOST_USE_ADB_FORWARD=1 \
pnpm android:local-host:ui
```

Direct LAN flow:

```bash
OPENCLAW_ANDROID_LOCAL_HOST_BASE_URL='http://<phone-ip>:3945' \
OPENCLAW_ANDROID_LOCAL_HOST_TOKEN='<token-from-connect-tab>' \
pnpm android:local-host:ui
```

Optional overrides:

- `OPENCLAW_ANDROID_LOCAL_HOST_UI_APP_PACKAGE=ai.openclaw.app`
- `OPENCLAW_ANDROID_LOCAL_HOST_UI_CONNECT_LABEL=Connect`
- `OPENCLAW_ANDROID_LOCAL_HOST_UI_CHAT_LABEL=Chat`
- `OPENCLAW_ANDROID_LOCAL_HOST_UI_CHAT_READY_TEXT="Select thinking level"`
- `OPENCLAW_ANDROID_LOCAL_HOST_UI_EDITOR_HINT_TEXT="Type a message"`
- `OPENCLAW_ANDROID_LOCAL_HOST_UI_DRAFT_VALUE="UI smoke draft"`
- `OPENCLAW_ANDROID_LOCAL_HOST_UI_WAIT_TIMEOUT_MS=10000`
- `OPENCLAW_ANDROID_LOCAL_HOST_UI_POLL_INTERVAL_MS=250`
- `OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PACKAGE=com.android.settings`

The UI smoke script verifies `/status` plus `/invoke/capabilities`, foregrounds OpenClaw with `ui.launchApp`, moves into the Chat tab with `ui.tap`, waits for a known Chat-ready text with `ui.waitForText`, focuses the composer, writes a temporary unsent draft through `ui.inputText`, then clears it again and verifies the editor hint returns. By default it stays inside OpenClaw so the result is repeatable and side-effect free.

- Use `OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PACKAGE=...` only when you also want an optional follow-up probe for the current cross-app freeze boundary.
- On March 28, 2026, the dedicated 5-second cross-app probe against `com.android.settings` classified as `foregrounded_host_reachable`: the target package reached the true foreground, all 10 `/status` probes still succeeded, and adb recovery brought OpenClaw cleanly back. Treat any later timeouts after a longer background stay as a separate OEM boundary, not as evidence that the in-app smoke regressed.

## Local Host Cross-App Probe

Use this when you want a ground-truth probe for the current cross-app boundary, not just the in-app smoke.

USB + adb flow:

```bash
OPENCLAW_ANDROID_LOCAL_HOST_TOKEN='<token-from-connect-tab>' \
OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PACKAGE='com.android.settings' \
pnpm android:local-host:ui:cross-app
```

Optional overrides:

- `OPENCLAW_ANDROID_LOCAL_HOST_PORT=3945`
- `OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_OBSERVE_WINDOW_MS=5000`
- `OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_POLL_INTERVAL_MS=500`
- `OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_RECOVERY_WAIT_MS=1500`
- `OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_WAIT_TEXT=...`
- `OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_TAP_TEXT=...` or `OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_TAP_RESOURCE_ID=...`
- `OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_INPUT_VALUE=...`
- `OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_INPUT_TEXT=...` or `OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_INPUT_RESOURCE_ID=...`
- `OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_FOLLOW_UP_SETTLE_MS=1000`

The cross-app probe calls `ui.launchApp` for the target package, then polls two truths in parallel:

- ADB foreground-activity state, using `topResumedActivity` and current focus
- Remote `/status`, to see whether OpenClaw stays reachable while another app is supposed to be on top

When the optional follow-up env vars are set, the same probe can also run `ui.waitForText`, `ui.tap`, `ui.inputText`, and a final `ui.state` inside the launched app before the reachability polling starts. Treat those selectors as app- and OEM-specific until the corresponding real-device proof is captured.

At the end it restores OpenClaw with adb, confirms recovery, and writes both a timeline JSONL and a compact summary JSON.

- `classification=launch_accepted_not_foregrounded` means Android accepted the launch intent but the target package never became the true foreground app during the observation window.
- `classification=foregrounded_host_reachable` means the target package reached the foreground and OpenClaw stayed reachable throughout the probe window.
- `classification=foregrounded_then_remote_unreachable` means the target package reached the foreground first, then remote `/status` later stopped answering before recovery.
- On March 28, 2026, the default `com.android.settings` probe on the current OPPO / ColorOS phone produced `classification=foregrounded_host_reachable` with `targetTopCount=9`, `statusSuccessCount=10`, and `recovery.ok=true`.

## Local Host Cross-App Sweep

Use this when you want to measure whether the current phone stays reachable across longer cross-app windows instead of checking only one observation duration.

USB + adb flow:

```bash
OPENCLAW_ANDROID_LOCAL_HOST_TOKEN='<token-from-connect-tab>' \
pnpm android:local-host:ui:cross-app:sweep
```

Optional overrides:

- `OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_SWEEP_WINDOWS_MS=5000,15000,30000`
- `OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_STOP_ON_FIRST_NON_REACHABLE=true`

The sweep reuses the cross-app probe for each observation window, stores every run in its own artifact directory, and writes a compact top-level `summary.json` plus `sweep.jsonl`.

- On March 28, 2026, the default sweep on the current OPPO / ColorOS phone finished all three windows as reachable: `5000ms -> foregrounded_host_reachable`, `15000ms -> foregrounded_host_reachable`, `30000ms -> foregrounded_host_reachable`.
- The same run therefore ended with `allWindowsReachable=true` and `firstNonReachableWindowMs=null`, so no background-freeze boundary was reproduced within 30 seconds on the current setup.

## Local Host Streaming Validation

Use this when you want direct evidence that streaming deltas arrive before the final assistant message.

USB-friendly flow with `adb forward`:

```bash
OPENCLAW_ANDROID_LOCAL_HOST_TOKEN='<token-from-connect-tab>' \
OPENCLAW_ANDROID_LOCAL_HOST_USE_ADB_FORWARD=1 \
pnpm android:local-host:streaming
```

Direct LAN flow:

```bash
OPENCLAW_ANDROID_LOCAL_HOST_BASE_URL='http://<phone-ip>:3945' \
OPENCLAW_ANDROID_LOCAL_HOST_TOKEN='<token-from-connect-tab>' \
pnpm android:local-host:streaming
```

Optional overrides:

- `OPENCLAW_ANDROID_LOCAL_HOST_MESSAGE=...`
- `OPENCLAW_ANDROID_LOCAL_HOST_WAIT_MS=30000`
- `OPENCLAW_ANDROID_LOCAL_HOST_EVENT_WAIT_MS=4000`
- `OPENCLAW_ANDROID_LOCAL_HOST_THINKING=low`
- `OPENCLAW_ANDROID_LOCAL_HOST_PORT=3945`

The streaming smoke script calls `/chat/send`, polls `/events`, and fails unless it sees at least one `chat state=delta` event before the terminal `final` event. It writes raw event payloads and a compact summary into the artifact directory it prints at the end.

- The underlying model request still goes to `https://chatgpt.com/backend-api/codex/responses`, so streaming validation needs a real network path from the phone or its trusted proxy/VPN to that endpoint.
- If direct phone egress is blocked on the current network, retry through a trusted LAN proxy or VPN path before treating the result as an Android regression.
- If the raw `/events` payload returns `errorType=usage_limit_reached`, treat that as a Codex account-quota blocker rather than evidence that the streaming pipeline itself regressed.

## Local Host Auth + Permission Validation

Codex auth metadata and refresh can be checked remotely without exposing tokens:

```bash
curl -H 'Authorization: Bearer <token-from-connect-tab>' \
  http://<phone-ip>:3945/api/local-host/v1/auth/codex/status

curl -X POST -H 'Authorization: Bearer <token-from-connect-tab>' \
  http://<phone-ip>:3945/api/local-host/v1/auth/codex/refresh
```

## Local Host Remote Access Defaults

- Remote access is off by default. Turn it on from `Connect -> Local Host` only when you need a trusted client to reach the phone.
- Prefer `LAN` or a trusted tunnel such as `adb forward`, Tailscale, or another private VPN path. Do not expose `http://<phone-ip>:3945` directly to the public internet or through a broad port-forward.
- Start with `/status` before using `/chat/*` or `/invoke`; it gives a one-shot readiness snapshot with Codex auth state, deployment readiness, and enabled remote-command tiers.
- The Connect tab shows the current bearer token. Use `Regenerate token` after sharing it, after a validation session, or any time a client or device should lose access.
- `POST /auth/codex/refresh` refreshes Codex model auth. It does not rotate the remote-access bearer token; rotate that separately from the Connect tab when access should be revoked.
- Leave the default read-control surface in place for routine checks. Enable `Advanced remote commands` only when you need camera actions, and enable `Write remote commands` only for trusted clients that truly need `sms.send`, `contacts.add`, `calendar.add`, `notifications.actions`, or bounded UI-write actions.
- For the current MVP, treat that read-control set plus the optional camera/write tiers as the intended remote surface. Broader cross-app automation belongs to the separate UI-automation phase, not to this MVP close-out.
- The first UI-automation phone-control slice now exists separately from the MVP close-out surface: `ui.state` and `ui.waitForText` stay read-only by default, while `ui.launchApp`, `ui.inputText`, `ui.tap`, `ui.back`, and `ui.home` sit behind the remote write tier.
- On the current OPPO / ColorOS device, reinstalling the APK clears the OpenClaw accessibility grant. If UI automation suddenly reports disabled after reinstall, reopen the Connect tab and re-enable the OpenClaw accessibility service before debugging further.
- For the shortest phone-control summary, read `apps/android/local-host-phone-control.md`. For the deeper research and runtime direction, read `apps/android/local-host-ui-automation-plan.md`.
- For dedicated idle-phone deployments, keep advanced and write tiers off unless the operator is actively using them, and avoid swipe-to-clear on OEMs that turn Recents removal into a package `force stop`.
- If you want a direct streaming-proof artifact for the self-check, run `pnpm android:local-host:streaming` and keep the generated event log plus summary JSON.

Permission failure validation script:

```bash
OPENCLAW_ANDROID_LOCAL_HOST_BASE_URL='http://<phone-ip>:3945' \
OPENCLAW_ANDROID_LOCAL_HOST_TOKEN='<token-from-connect-tab>' \
pnpm android:local-host:permissions
```

Optional overrides:

- `OPENCLAW_ANDROID_LOCAL_HOST_USE_ADB_FORWARD=1`
- `OPENCLAW_ANDROID_LOCAL_HOST_PERMISSION_CASES=contacts,calendar,photos,notifications`
- `OPENCLAW_ANDROID_LOCAL_HOST_PORT=3945`

The permission script verifies permission-dependent `/invoke` failures and preserves the original permission state. On devices that block shell-side runtime revocation, it still validates commands that are already denied.

### USB-only gateway testing (no LAN dependency)

Use `adb reverse` so Android `localhost:18789` tunnels to your laptop `localhost:18789`.

Terminal A (gateway):

```bash
pnpm openclaw gateway --port 18789 --verbose
```

Terminal B (USB tunnel):

```bash
adb reverse tcp:18789 tcp:18789
```

Then in app **Connect → Manual**:

- Host: `127.0.0.1`
- Port: `18789`
- TLS: off

## Hot Reload / Fast Iteration

This app is native Kotlin + Jetpack Compose.

- For Compose UI edits: use Android Studio **Live Edit** on a debug build (works on physical devices; project `minSdk=31` already meets API requirement).
- For many non-structural code/resource changes: use Android Studio **Apply Changes**.
- For structural/native/manifest/Gradle changes: do full reinstall (`pnpm android:run`).
- Canvas web content already supports live reload when loaded from Gateway `__openclaw__/canvas/` (see `docs/platforms/android.md`).

## Connect / Pair

1) Start the gateway (on your main machine):

```bash
pnpm openclaw gateway --port 18789 --verbose
```

2) In the Android app:

- Open the **Connect** tab.
- Use **Setup Code** or **Manual** mode to connect.

3) Approve pairing (on the gateway machine):

```bash
openclaw devices list
openclaw devices approve <requestId>
```

More details: `docs/platforms/android.md`.

## Permissions

- Discovery:
  - Android 13+ (`API 33+`): `NEARBY_WIFI_DEVICES`
  - Android 12 and below: `ACCESS_FINE_LOCATION` (required for NSD scanning)
- Foreground service notification (Android 13+): `POST_NOTIFICATIONS`
- Camera:
  - `CAMERA` for `camera.snap` and `camera.clip`
  - `RECORD_AUDIO` for `camera.clip` when `includeAudio=true`

## Integration Capability Test (Preconditioned)

This suite assumes setup is already done manually. It does **not** install/run/pair automatically.

Pre-req checklist:

1) Gateway is running and reachable from the Android app.
2) Android app is connected to that gateway and `openclaw nodes status` shows it as paired + connected.
3) App stays unlocked and in foreground for the whole run.
4) Open the app **Screen** tab and keep it active during the run (canvas/A2UI commands require the canvas WebView attached there).
5) Grant runtime permissions for capabilities you expect to pass (camera/mic/location/notification listener/location, etc.).
6) No interactive system dialogs should be pending before test start.
7) Canvas host is enabled and reachable from the device (do not run gateway with `OPENCLAW_SKIP_CANVAS_HOST=1`; startup logs should include `canvas host mounted at .../__openclaw__/`).
8) Local operator test client pairing is approved. If first run fails with `pairing required`, approve latest pending device pairing request, then rerun:
9) For A2UI checks, keep the app on **Screen** tab; the node now auto-refreshes canvas capability once on first A2UI reachability failure (TTL-safe retry).

```bash
openclaw devices list
openclaw devices approve --latest
```

Run:

```bash
pnpm android:test:integration
```

Optional overrides:

- `OPENCLAW_ANDROID_GATEWAY_URL=ws://...` (default: from your local OpenClaw config)
- `OPENCLAW_ANDROID_GATEWAY_TOKEN=...`
- `OPENCLAW_ANDROID_GATEWAY_PASSWORD=...`
- `OPENCLAW_ANDROID_NODE_ID=...` or `OPENCLAW_ANDROID_NODE_NAME=...`

What it does:

- Reads `node.describe` command list from the selected Android node.
- Invokes advertised non-interactive commands.
- Skips `screen.record` in this suite (Android requires interactive per-invocation screen-capture consent).
- Asserts command contracts (success or expected deterministic error for safe-invalid calls like `sms.send` and `notifications.actions`).

Common failure quick-fixes:

- `pairing required` before tests start:
  - approve pending device pairing (`openclaw devices approve --latest`) and rerun.
- `A2UI host not reachable` / `A2UI_HOST_NOT_CONFIGURED`:
  - ensure gateway canvas host is running and reachable, keep the app on the **Screen** tab. The app will auto-refresh canvas capability once; if it still fails, reconnect app and rerun.
- `NODE_BACKGROUND_UNAVAILABLE: canvas unavailable`:
  - app is not effectively ready for canvas commands; keep app foregrounded and **Screen** tab active.

## Contributions

This Android app is currently being rebuilt.
Maintainer: @obviyus. For issues/questions/contributions, please open an issue or reach out on Discord.
