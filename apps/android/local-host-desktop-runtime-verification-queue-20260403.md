# Android Desktop Runtime Verification Queue / Android 桌面 Runtime 验证队列

Date / 日期: April 3, 2026 / 2026-04-03
Branch / 分支: `android-desktop-runtime-mainline-20260403`

## Goal / 目标

Record the exact real-device verification steps that are still blocked during the current daytime session. / 记录当前白天会话里因为没法连设备而暂时挂起的真机验证步骤。

## Tonight's Commands / 今晚要跑的命令

1. Reinstall the current debug app if the device might still be on an older build.

```bash
cd apps/android
./gradlew --no-daemon --console=plain :app:installDebug
```

2. Bootstrap the current local-host bearer token over trusted adb.

```bash
pnpm android:local-host:token -- --json
```

3. Reconfirm the packaged pod baseline first.

```bash
OPENCLAW_ANDROID_LOCAL_HOST_USE_ADB_FORWARD=1 \
OPENCLAW_ANDROID_LOCAL_HOST_TOKEN='<token>' \
pnpm android:local-host:embedded-runtime-pod:smoke
```

4. Prove the bounded browser lane leaves replayable state on disk.

```bash
OPENCLAW_ANDROID_LOCAL_HOST_USE_ADB_FORWARD=1 \
OPENCLAW_ANDROID_LOCAL_HOST_TOKEN='<token>' \
pnpm android:local-host:embedded-runtime-pod:browser-lane:smoke
```

5. After the external browser flow completes on the phone, rerun the same browser-lane smoke in read-only confirm mode.

```bash
OPENCLAW_ANDROID_LOCAL_HOST_USE_ADB_FORWARD=1 \
OPENCLAW_ANDROID_LOCAL_HOST_TOKEN='<token>' \
OPENCLAW_ANDROID_LOCAL_HOST_BROWSER_START=0 \
pnpm android:local-host:embedded-runtime-pod:browser-lane:smoke
```

## Expected Results / 预期结果

- Step 3 should still report the packaged pod baseline as healthy.
- Step 4 should leave `browserDescribeAfter.replayReady=true` in `summary.json`.
- Step 4 should move `runtimeDescribeAfter.mainlineStatus` to `browser_lane_replayed` or `browser_lane_configured`.
- Step 5 should keep `browserDescribeAfter.replayReady=true` and ideally converge to `runtimeDescribeAfter.mainlineStatus=browser_lane_configured`.

## Artifacts To Keep / 建议保留的产物

- The `summary.json` from `pnpm android:local-host:embedded-runtime-pod:smoke`.
- The `summary.json` from the browser-lane smoke start pass.
- The `summary.json` from the browser-lane smoke confirm pass.

## Do Not Reopen / 不要重开

- Do not open the plugin lane before the browser-lane smoke has real-device proof.
- Do not treat a pre-existing credential alone as proof that the packaged browser lane itself has replayed successfully.
