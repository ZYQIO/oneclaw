# Android Local Host Dedicated Device Plan / Android 本机 Host 专机部署方案

Purpose / 用途: turn the vague question of "can we make a spare phone more system-like?" into a concrete deployment ladder and an operator checklist. / 把“能不能把闲置手机做得更像系统级部署”这个模糊问题，整理成具体的部署梯度和操作清单。

Last updated / 最后更新: April 1, 2026 / 2026 年 4 月 1 日

## Short Answer / 简短结论

For a spare phone, the best order is: / 对闲置手机来说，最值得按这个顺序推进：

1. `Device Owner` plus `lock-task` / `dedicated-device` mode
2. Root plus Magisk-style systemization
3. Custom-ROM `priv-app` preload
4. True Android platform service / `system_server` integration

The project should treat these as four separate deployment tiers, not one vague "make it a system app" idea. / 项目应该把它们当成四档完全不同的部署层级，而不是笼统地说一句“做成系统 app”。

## Current Device Readiness / 当前设备 readiness

The new readiness probe is `apps/android/scripts/local-host-dedicated-readiness.sh`, exposed as `pnpm android:local-host:dedicated:readiness`. / 新的 readiness 探针是 `apps/android/scripts/local-host-dedicated-readiness.sh`，并通过 `pnpm android:local-host:dedicated:readiness` 暴露出来。

There is now also a `Device Owner` helper at `apps/android/scripts/local-host-dedicated-device-owner.sh`, exposed as `pnpm android:local-host:dedicated:device-owner`. It defaults to dry-run mode and only attempts `adb shell dpm set-device-owner ...` when `--apply` is passed explicitly. / 现在还多了一条 `Device Owner` helper：`apps/android/scripts/local-host-dedicated-device-owner.sh`，并通过 `pnpm android:local-host:dedicated:device-owner` 暴露出来。它默认只做 dry-run，只有显式传入 `--apply` 时才会尝试 `adb shell dpm set-device-owner ...`。

There is now also a TestDPC install helper at `apps/android/scripts/local-host-dedicated-testdpc-install.sh`, exposed as `pnpm android:local-host:dedicated:testdpc-install`. It fetches the latest public TestDPC release, downloads the APK locally, prints the exact `adb install -r -d ...` command in dry-run mode, and only installs onto the phone when `--apply` is passed explicitly. / 现在还新增了一条 TestDPC install helper：`apps/android/scripts/local-host-dedicated-testdpc-install.sh`，命令入口为 `pnpm android:local-host:dedicated:testdpc-install`。它会抓取最新公开 TestDPC release、把 APK 下载到本地，在 dry-run 模式下输出精确的 `adb install -r -d ...` 命令，只有显式传入 `--apply` 时才会真的安装到手机上。

For the factory-reset path that Android officially prefers on dedicated devices, there is now also a provisioning-QR toolchain: `apps/android/scripts/local-host-dedicated-provisioning-qr.ts` plus the `TestDPC` wrapper `apps/android/scripts/local-host-dedicated-testdpc-qr.sh`, exposed as `pnpm android:local-host:dedicated:testdpc-qr`. / 对于 Android 官方更偏好的“恢复出厂后走 QR 入管”路径，现在还新增了一套 provisioning QR 工具链：底层生成器 `apps/android/scripts/local-host-dedicated-provisioning-qr.ts`，以及 `TestDPC` wrapper `apps/android/scripts/local-host-dedicated-testdpc-qr.sh`，命令入口为 `pnpm android:local-host:dedicated:testdpc-qr`。

There is now also a post-provision checker at `apps/android/scripts/local-host-dedicated-post-provision-check.sh`, exposed as `pnpm android:local-host:dedicated:post-provision`. It inspects adb owner state, lock-task state, launcher resolution, and OpenClaw's plain `shared_prefs` via `run-as` when available, so the project can tell whether the remaining gap is still DPC provisioning or already inside the app. / 现在还新增了一条 post-provision checker：`apps/android/scripts/local-host-dedicated-post-provision-check.sh`，命令入口为 `pnpm android:local-host:dedicated:post-provision`。它会同时检查 adb 的 owner / lock-task 状态、launcher resolution，以及在可用时通过 `run-as` 读取 OpenClaw 的明文 `shared_prefs`，从而把“剩余差距还在 DPC provisioning”还是“已经进入 app 内部问题”区分开。

There is now also a TestDPC kiosk helper at `apps/android/scripts/local-host-dedicated-testdpc-kiosk.sh`, exposed as `pnpm android:local-host:dedicated:testdpc-kiosk`. It defaults to dry-run mode, reuses the post-provision checker, prints the exact adb commands for TestDPC's kiosk flow, and only mutates the device when `--apply` is passed explicitly. / 现在还新增了一条 TestDPC kiosk helper：`apps/android/scripts/local-host-dedicated-testdpc-kiosk.sh`，命令入口为 `pnpm android:local-host:dedicated:testdpc-kiosk`。它默认只做 dry-run，会复用 post-provision checker，输出 TestDPC kiosk 流程所需的精确 adb 命令，只有显式传入 `--apply` 时才会真正修改设备状态。

The two wrapper entrypoints `pnpm android:local-host:dedicated:next` and `pnpm android:local-host:dedicated:post-provision:next` now also support `-- --describe` for an offline preview of their wrapper layout, artifact paths, and downstream action map. Add `-- --assume-action <action>` when you want to inspect one specific dry-run lane such as `testdpc-install` or `launch-openclaw` without running adb-dependent checks first. / 两个 wrapper 入口 `pnpm android:local-host:dedicated:next` 和 `pnpm android:local-host:dedicated:post-provision:next` 现在也都支持 `-- --describe`，可离线预览 wrapper 结构、artifact 路径和下游 action map；如果想在不跑 adb 依赖检查的前提下预览某一条具体 dry-run 路径，还可以加 `-- --assume-action <action>`，例如 `testdpc-install` 或 `launch-openclaw`。

Its March 28, 2026 run on the currently connected spare OPPO phone produced: / 2026 年 3 月 28 日它在当前接入的闲置 OPPO 手机上跑出的结果是：

- `manufacturer=OPPO`, `model=PFEM10`, `android=15`, `sdk=35`
- `bootloaderLocked=true`
- `verifiedBootState=green`
- `hasDeviceOwner=false`
- `accountsCount=5`
- `dpcInstalled=false`
- `preferredPath=device_owner_after_reset_or_account_cleanup`
- `rootLaneFriction=high_unknown_oem_unlock_support`

Meaning / 含义:

- The phone is not ready for adb-based device-owner provisioning yet, because it still has configured accounts and no DPC installed. / 这台手机当前还不能直接走 adb 的 device-owner 配置，因为它还有账号，且没装 DPC。
- The root / custom-ROM lane is currently higher friction than the device-owner lane, because the bootloader is still locked and OEM unlock support is not yet confirmed. / root / 自定义 ROM 路线当前比 device-owner 路线更难，因为 bootloader 还锁着，而且 OEM unlock 能不能走通还没确认。
- The first March 28, 2026 dry-run of `pnpm android:local-host:dedicated:testdpc-install` on the same phone successfully fetched the latest public release `v9.0.12`, downloaded `TestDPC_9.0.12.apk`, and confirmed the connected phone still reports `installed=false`, `device_owner=false`. / 同一台手机上 2026 年 3 月 28 日第一次执行 `pnpm android:local-host:dedicated:testdpc-install` dry-run 已成功抓到最新公开 release `v9.0.12`，下载 `TestDPC_9.0.12.apk`，并确认当前接入手机仍然是 `installed=false`、`device_owner=false`。
- The new post-provision check on the same phone already shows that OpenClaw itself is mostly ready: `dedicatedEnabled=true`, `onboardingCompleted=true`, `gatewayConnectionMode=localHost`, the launcher still resolves to `ai.openclaw.app/.MainActivity`, and the local APK manifest already reports `if_whitelisted`. The missing pieces are still `Device Owner` and the DPC lock-task allowlist. / 同一台手机上新的 post-provision 检查也表明 OpenClaw 自身其实已经基本 ready：`dedicatedEnabled=true`、`onboardingCompleted=true`、`gatewayConnectionMode=localHost`、launcher 仍然正确指向 `ai.openclaw.app/.MainActivity`，而且本地 APK manifest 已经是 `if_whitelisted`。真正缺的仍然是 `Device Owner` 和 DPC 的 lock-task allowlist。
- The first March 28, 2026 dry-run of `pnpm android:local-host:dedicated:testdpc-kiosk` on the same phone also confirms the gap is still on the DPC side: `OpenClaw installed=true`, `readyForApply=false`, and the only blockers are `TestDPC is not installed` plus `TestDPC is not the active Device Owner`. / 同一台手机上 2026 年 3 月 28 日第一次执行 `pnpm android:local-host:dedicated:testdpc-kiosk` dry-run 也进一步确认，剩余差距仍在 DPC 侧：`OpenClaw installed=true`、`readyForApply=false`，而且唯一 blockers 就是 `TestDPC is not installed` 和 `TestDPC is not the active Device Owner`。

## Deployment Ladder / 部署梯度

### Tier 1. Device Owner / 专用设备模式

Best fit when / 最适合的情况:

- The phone can be wiped or reset
- You want an official Android path first
- You want stronger kiosk-style behavior without starting with root

What it gives / 能带来的收益:

- `lock-task` / dedicated-device mode
- A managed launcher or managed entrypoint
- Better alignment with "this phone exists mainly for OpenClaw"
- OpenClaw can now auto-enter lock-task on launch once the DPC allowlists `ai.openclaw.app` and dedicated deployment is enabled

What it does not give / 不能直接带来的东西:

- It does not make OpenClaw a privileged platform service
- It does not bypass every OEM battery policy automatically

### Tier 2. Root plus systemize / Root 后系统化

Best fit when / 最适合的情况:

- The device-owner path is not enough
- You accept root on this spare phone
- You want to reduce the fragility of remaining an ordinary third-party app

What it gives / 能带来的收益:

- OpenClaw can be moved closer to a system-app posture
- Some OEMs treat systemized apps less harshly than ordinary apps

What it still does not solve / 仍不能直接解决:

- It is still not the same as a true `system_server` service
- Privileged permissions may still need allowlists or custom signing

### Tier 3. Custom ROM `priv-app` / 自定义 ROM 预装

Best fit when / 最适合的情况:

- This phone is becoming a project-owned device image
- You are willing to maintain system partitions, permission XML, and OTA risk

What it gives / 能带来的收益:

- A much more real "in the system" posture
- A structurally cleaner path toward privileged capabilities

What it costs / 代价:

- ROM engineering, signing, SELinux, rescue risk, and maintenance burden

### Tier 4. Platform service / 平台服务

Best fit when / 最适合的情况:

- The goal is a custom Android image or OEM-style integration
- You want a true platform-level runtime rather than an app

What it means / 这真正意味着什么:

- OpenClaw is no longer "just an app"
- The work becomes platform engineering, not app delivery

## Current Recommendation / 当前建议

For the currently connected spare phone, the recommended next move is: / 对当前接入的这台闲置手机，最建议的下一步是：

1. Clear accounts or factory-reset the phone
2. Install a DPC such as TestDPC
3. Prefer the official QR provisioning lane after reset by generating a `TestDPC` QR payload
4. Re-run `pnpm android:local-host:dedicated:readiness`
5. Attempt the `Device Owner` lane before spending more time on root

Why / 原因:

- The current blocker for `Device Owner` is concrete and removable
- The current blocker for root/systemize is still uncertainty plus higher device risk

## Operator Checklist / 操作清单

### Before Device Owner / 尝试 Device Owner 前

- Confirm the phone really is a spare device
- Export anything important off the phone
- Remove existing accounts or plan for a factory reset
- Install a DPC package, preferably through `pnpm android:local-host:dedicated:testdpc-install` if you want a repo-tracked TestDPC fetch/install step
- Re-run `pnpm android:local-host:dedicated:readiness`

### Factory-reset QR path / 恢复出厂后的 QR 路径

- Generate the latest public `TestDPC` QR with `pnpm android:local-host:dedicated:testdpc-qr`
- If setup wizard needs Wi-Fi, pass `-- --wifi-ssid <ssid> --wifi-security WPA --wifi-password <password>`
- On the welcome screen, tap 6 times to open the QR scanner
- Scan the generated ASCII QR or use the emitted `payload.json` with another QR renderer
- Follow setup wizard and finish the fully managed / dedicated-device flow
- After provisioning, either allowlist `ai.openclaw.app` manually inside TestDPC's lock-task policy or use `pnpm android:local-host:dedicated:testdpc-kiosk` as the dry-run helper before any actual apply

### After Device Owner / Device Owner 完成后

- Keep `ai.openclaw.app` installed and launchable as `ai.openclaw.app/.MainActivity`
- Add `ai.openclaw.app` to the DPC lock-task allowlist
- Keep OpenClaw dedicated deployment enabled inside the app
- Relaunch OpenClaw; `MainActivity` now declares `android:lockTaskMode="if_whitelisted"` and calls `startLockTask()` only when dedicated mode, local-host mode, onboarding completion, and the DPC allowlist are all in place
- Check `host.deployment.lockTaskPermitted`, `host.deployment.lockTaskAutoEnterReady`, and `host.deployment.lockTaskModeState` through the app or remote `/status`
- Run `pnpm android:local-host:dedicated:post-provision` as the first verification pass, and use `pnpm android:local-host:dedicated:post-provision -- --launch` when you want the script to relaunch OpenClaw and re-check lock-task after launch
- If TestDPC is the DPC, use `pnpm android:local-host:dedicated:testdpc-kiosk` first to confirm the helper sees `installed=true` and `isDeviceOwner=true`; only use `-- --apply` on a spare phone after acknowledging that TestDPC's kiosk activity becomes the persistent HOME activity and that TestDPC keeps itself as the kiosk backdoor package

### Before Root / 尝试 Root 前

- Confirm whether OEM unlocking is actually available on this model
- Decide whether losing verified-boot green state is acceptable
- Decide whether the device is worth custom maintenance

### Before Custom ROM / 尝试自定义 ROM 前

- Decide whether OpenClaw really needs `priv-app` or platform integration
- Decide who will maintain signing, OTA behavior, and rescue procedures

## Commands / 命令

Dedicated readiness:

```bash
pnpm android:local-host:dedicated:readiness
```

Preview the readiness wrapper offline:

```bash
pnpm android:local-host:dedicated:next -- --describe
pnpm android:local-host:dedicated:next -- --describe --assume-action testdpc-install
```

Device-owner dry-run:

```bash
pnpm android:local-host:dedicated:device-owner
```

Download the latest public TestDPC release and print the install command:

```bash
pnpm android:local-host:dedicated:testdpc-install
```

Install the latest public TestDPC release onto the connected phone:

```bash
pnpm android:local-host:dedicated:testdpc-install -- --apply
```

Device-owner apply:

```bash
bash apps/android/scripts/local-host-dedicated-device-owner.sh --apply
```

Generate the latest `TestDPC` provisioning QR:

```bash
pnpm android:local-host:dedicated:testdpc-qr
```

Generate a `TestDPC` provisioning QR with Wi-Fi:

```bash
pnpm android:local-host:dedicated:testdpc-qr -- \
  --wifi-ssid SparePhoneWiFi \
  --wifi-security WPA \
  --wifi-password example-password
```

Check post-provision dedicated readiness:

```bash
pnpm android:local-host:dedicated:post-provision
```

Preview the post-provision wrapper offline:

```bash
pnpm android:local-host:dedicated:post-provision:next -- --describe
pnpm android:local-host:dedicated:post-provision:next -- --describe --assume-action launch-openclaw
```

Check post-provision dedicated readiness and relaunch OpenClaw:

```bash
pnpm android:local-host:dedicated:post-provision -- --launch
```

Dry-run the TestDPC kiosk flow:

```bash
pnpm android:local-host:dedicated:testdpc-kiosk
```

Apply the TestDPC kiosk flow after Device Owner is in place:

```bash
pnpm android:local-host:dedicated:testdpc-kiosk -- --apply
```

Optional DPC override:

```bash
OPENCLAW_ANDROID_DPC_COMPONENT='com.afwsamples.testdpc/.DeviceAdminReceiver' \
pnpm android:local-host:dedicated:readiness
```
