## OpenClaw Android App

Status: **extremely alpha**. The app is actively being rebuilt from the ground up.

### Android Local Host Tracking / Android 本机 Host 跟踪

- Progress tracker / 进度跟踪: `apps/android/local-host-progress.md`
- Self-check gate / 自检门槛: `apps/android/local-host-self-check.md`
- Session handoff / 接续手册: `apps/android/local-host-handoff.md`

### Local Host Scope Today / 当前 Local Host 范围

- `Local Host` currently provides on-device Codex-backed chat, a curated Android command surface, an app-private on-device workspace for text files with search/edit/copy/move support, and a dedicated idle-phone deployment mode that can keep the host service alive across app relaunches, package upgrades, and reboots.
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
- On the current OPPO / ColorOS device, that optional cross-app probe may show the launched app on top while later remote requests still time out after `OplusHansManager` freezes OpenClaw in the background; treat that as the known OEM boundary, not as evidence that the in-app smoke regressed.

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
