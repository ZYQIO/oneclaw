# Android Local Host Dedicated Device Plan / Android 本机 Host 专机部署方案

Purpose / 用途: turn the vague question of "can we make a spare phone more system-like?" into a concrete deployment ladder and an operator checklist. / 把“能不能把闲置手机做得更像系统级部署”这个模糊问题，整理成具体的部署梯度和操作清单。

Last updated / 最后更新: March 28, 2026 / 2026 年 3 月 28 日

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
3. Re-run `pnpm android:local-host:dedicated:readiness`
4. Attempt the `Device Owner` lane before spending more time on root

Why / 原因:

- The current blocker for `Device Owner` is concrete and removable
- The current blocker for root/systemize is still uncertainty plus higher device risk

## Operator Checklist / 操作清单

### Before Device Owner / 尝试 Device Owner 前

- Confirm the phone really is a spare device
- Export anything important off the phone
- Remove existing accounts or plan for a factory reset
- Install a DPC package
- Re-run `pnpm android:local-host:dedicated:readiness`

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

Device-owner dry-run:

```bash
pnpm android:local-host:dedicated:device-owner
```

Device-owner apply:

```bash
bash apps/android/scripts/local-host-dedicated-device-owner.sh --apply
```

Optional DPC override:

```bash
OPENCLAW_ANDROID_DPC_COMPONENT='com.afwsamples.testdpc/.DeviceAdminReceiver' \
pnpm android:local-host:dedicated:readiness
```
