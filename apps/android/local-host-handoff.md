# Android Local Host Handoff / Android 本机 Host 接续手册

Purpose / 用途: make it easy to resume the Android local-host effort in a fresh chat without re-discovering the current state. / 让新会话可以快速接手 Android 本机 Host 项目，不必重新摸索当前状态。

## Current Objective / 当前目标

The active goal is still the same: / 当前目标仍然不变：

- 在 Android 手机上以 `Local Host` 模式本机运行 OpenClaw。Run OpenClaw on the phone itself in `Local Host` mode.
- 使用 Codex 授权访问 GPT。Use Codex auth for GPT access.
- 通过受保护的远程 API 从另一台设备控制手机上的本机 Host。Control the on-device local host from another trusted device through the guarded remote API.

## Current Status Snapshot / 当前状态快照

As of March 27, 2026, the MVP happy path is working, the streaming gate has direct on-device proof, and the close-out docs are aligned on a `Go` verdict. The active engineering focus has now moved to the first Android UI-automation control loop plus the next cross-app primitive. / 截至 2026 年 3 月 27 日，MVP 的成功路径已经跑通，streaming 门槛也已有真机直证，收尾文档对 `Go` 结论已经对齐；当前工程重心已经转到第一条 Android UI 自动化控制闭环以及下一条跨 app primitive。

- `Local Host` 可以在真实 Android 手机上启动。`Local Host` can start on a real Android phone.
- Codex 浏览器授权已经完成，并且 `/status` 会返回 `codexAuthConfigured=true`。Codex browser auth completes, and `/status` returns `codexAuthConfigured=true`.
- 远控 `/chat/send-wait` 已成功返回模型回复。Remote `/chat/send-wait` has successfully returned a model reply.
- 远控 `/invoke` 的只读命令已成功执行。Read-only `/invoke` commands have succeeded remotely.
- 高风险命令层在关闭时会被明确拒绝。Higher-risk command tiers are clearly rejected when disabled.
- LAN 冒烟脚本已经成功。The LAN smoke script has succeeded.
- `auth/codex/status` 和 `auth/codex/refresh` 已在真机验证成功。`auth/codex/status` and `auth/codex/refresh` have both been validated successfully on-device.
- 权限缺失脚本已经在真机上成功覆盖四类失败。The permission-failure script has already covered four failure cases successfully on-device.
- 2026 年 3 月 25 日已经补齐 streaming 真机直证：先确认当前网络下手机直连 `chatgpt.com` 会超时，再通过可信主机代理恢复外网出口；在补充 Codex 额度后，原始 `/events` 已看到同一条 run 持续产出 `chat state=delta` 并最终到达 `state=final`，随后 `pnpm android:local-host:streaming` 也以 `deltaCount=68`、`terminalState=final` 直接通过。On March 25, 2026, streaming direct evidence was completed on-device: after first confirming the phone timed out on direct `chatgpt.com` access on the current network, we restored external egress through a trusted host proxy; after Codex usage was topped up, raw `/events` showed repeated `chat state=delta` events for the same run and eventually reached `state=final`, and `pnpm android:local-host:streaming` then passed directly with `deltaCount=68` and `terminalState=final`.
- 同一天更早的一次重试曾返回 `The usage limit has been reached | errorType=usage_limit_reached`；这个记录现在应被视为 account-quota 边界，而不是 Android streaming 回归。An earlier retry on the same day returned `The usage limit has been reached | errorType=usage_limit_reached`; that record should now be treated as an account-quota boundary rather than an Android streaming regression.
- 远程访问默认值、token 轮换和网络暴露说明现在已写入 `apps/android/README.md`，并明确只推荐 `LAN` / trusted tunnel、先探测 `/status`、以及在需要撤销访问时单独再生成 bearer token。Remote-access defaults, token rotation, and network-exposure guidance are now written down in `apps/android/README.md`, explicitly recommending only `LAN` / trusted tunnels, probing `/status` first, and regenerating the bearer token separately when access should be revoked.
- 当前 MVP 远程命令面现在视为已收口：默认只读远控集 + 可选相机高级层 + 可选写操作层 + 共享 write gate 的 workspace 写能力；如果需要更广的手机操作能力，应转到单独的 UI 自动化阶段，而不是继续扩这条 MVP。The current MVP remote-command surface is now considered frozen: the default read-only remote set plus the optional camera advanced tier, the optional write tier, and workspace writes behind the shared write gate; broader phone-control capability should move into the separate UI-automation phase instead of expanding this MVP further.
- UI 自动化阶段已经不再只是规划：Android app 里已有 `AccessibilityService` 骨架、Connect 页 readiness、`/status.host.uiAutomation*` 状态，以及第一条只读 `ui.state` 命令；未开启服务时它会返回结构化原因，开启后会返回当前窗口的 `packageName`、`visibleText`、`nodeCount` 和扁平节点摘要。The UI-automation phase is no longer just a plan: the Android app now has an `AccessibilityService` skeleton, Connect-tab readiness, `/status.host.uiAutomation*` state, and a first read-only `ui.state` command; before the service is enabled it returns a structured reason, and once enabled it returns the active window's `packageName`, `visibleText`, `nodeCount`, and a flattened node summary.
- 2026 年 3 月 26 日已在真机补齐第一条 bounded control loop：write tier 关闭时远端只允许 `ui.state` / `ui.waitForText`，开启 write 后才放出 `ui.tap` / `ui.back` / `ui.home`；`ui.tap(text=\"Chat\")` 已成功切到 Chat tab，而 `ui.home` 与 `ui.back` 都能把活动 `packageName` 切到 `com.android.launcher`。On March 26, 2026, the first bounded control loop was completed on-device: with the write tier off, remote access only allows `ui.state` / `ui.waitForText`, and only after enabling write do `ui.tap` / `ui.back` / `ui.home` appear; `ui.tap(text=\"Chat\")` now successfully switches to the Chat tab, and both `ui.home` and `ui.back` move the active `packageName` to `com.android.launcher`.
- “怎么操控手机”的调研结论继续写在 `apps/android/local-host-ui-automation-plan.md`：主路线仍是 app 内 `AccessibilityService`，把 ADB / Appium / `Open-AutoGLM` 当参考而不是主运行时。The answer to “how should we control the phone” continues to live in `apps/android/local-host-ui-automation-plan.md`: the primary path is still an in-app `AccessibilityService`, while ADB / Appium / `Open-AutoGLM` remain references rather than the main runtime.
- 2026 年 3 月 26 日又补做了一轮以 GitHub 仓库和论文为主的新调研，并把外部参考清楚分成四层：`droidrun-portal` 负责 runtime 参考，`Open-AutoGLM` 负责外部主控闭环参考，`UI-TARS` / `MobileAgent` / `AgentCPM-GUI` / `ShowUI` 负责模型与 grounding 参考，`AndroidWorld` / `GUI-CEval` 负责验证参考；这条调研后来也兑现成了顺序落地：先 `ui.launchApp`，后 `ui.inputText`。On March 26, 2026, we also completed a fresh GitHub-plus-paper scan and separated external references into four layers: `droidrun-portal` for runtime inspiration, `Open-AutoGLM` for external-controller loops, `UI-TARS` / `MobileAgent` / `AgentCPM-GUI` / `ShowUI` for model and grounding ideas, and `AndroidWorld` / `GUI-CEval` for validation; that research later turned into the actual landing order too: `ui.launchApp` first, then `ui.inputText`.
- 2026 年 3 月 27 日已把 `launch_app` 落成实际命令 `ui.launchApp`：它采用 `packageName`-first 接口，走标准 Android launch intent，并把 “未安装” / “已安装但不可启动” 区分成清晰错误；这条命令也已经接进远端 write gate、`nodes` 工具桥接、Connect tab 示例和定向单测，但还需要一轮新的真机验证。On March 27, 2026, `launch_app` was turned into a real command as `ui.launchApp`: it uses a `packageName`-first contract, launches through standard Android launch intents, and distinguishes "not installed" from "installed but not launchable" with clear errors; the command is also wired into the remote write gate, the `nodes` tool bridge, Connect-tab examples, and targeted unit tests, but it still needs a fresh on-device validation pass.
- 同一天后来也补到了真机证据：远端 `ui.launchApp(packageName=\"com.android.settings\")` 返回 `launched=true`，并且 `adb shell dumpsys activity activities` 显示 `topResumedActivity=com.android.settings/.Settings`。Later the same day we also got the device proof: remote `ui.launchApp(packageName=\"com.android.settings\")` returned `launched=true`, and `adb shell dumpsys activity activities` showed `topResumedActivity=com.android.settings/.Settings`.
- 同一天还把 `input_text` 落成了实际命令 `ui.inputText`：它采用 focused-editable / selector-editable 的第一版边界，走 accessibility `ACTION_SET_TEXT`，并接进远端 write gate、`nodes` 工具桥接、Connect tab 示例和定向单测；随后在真机 Connect 页端口输入框上也已成功返回 `performed=true`。The same day also turned `input_text` into the real command `ui.inputText`: its first bounded form uses focused-editable / selector-editable targeting with accessibility `ACTION_SET_TEXT`, and it is wired into the remote write gate, the `nodes` tool bridge, Connect-tab examples, and targeted unit tests; afterward it also returned `performed=true` against the real-device Connect-screen port field.
- 但新的 OPPO / ColorOS 风险也在这轮暴露出来：Settings 前台时远端 `/status` 还能短时间回包，可随后 `OplusHansManager` 会把后台的 `ai.openclaw.app` 冻住，导致后续远端请求超时，直到 OpenClaw 被重新带回前台。But a new OPPO / ColorOS risk also surfaced in this run: while Settings is on top, remote `/status` can still answer briefly, but `OplusHansManager` later freezes background `ai.openclaw.app`, causing subsequent remote requests to time out until OpenClaw is brought back to the foreground.
- 与这条 UI 自动化切片直接相关的 Android 编译和单测已经重新通过，包括 `:app:compileDebugKotlin` 以及定向的 `LocalHostRuntimeTest`、`LocalHostRemoteAccessServerTest`、`InvokeCommandRegistryTest`、`UiAutomationHandlerTest`。The Android compile and targeted tests directly tied to this UI-automation slice have been rerun successfully, including `:app:compileDebugKotlin` plus the targeted `LocalHostRuntimeTest`, `LocalHostRemoteAccessServerTest`, `InvokeCommandRegistryTest`, and `UiAutomationHandlerTest`.
- `Dedicated host deployment` 形态已经接进 app：支持 idle-phone 场景下的前台服务保活、开机恢复、升级后恢复，以及本机 Host 掉线后的自愈重连。A `Dedicated host deployment` mode is now wired into the app: it supports idle-phone keepalive with foreground service persistence, restore after reboot, restore after app updates, and self-heal reconnect when Local Host drops.
- dedicated 模式现在还会把 Android 电池优化状态直接展示出来，并提供跳转到电池豁免请求的入口；远端 `device.status` 也能看到 `backgroundExecution.batteryOptimizationIgnored`。Dedicated mode now also shows Android battery-optimization state directly and links to the battery-exemption flow; remote `device.status` can also see `backgroundExecution.batteryOptimizationIgnored`.
- 电池优化豁免链路已经在真机上重新验证通过：系统弹窗可成功授予豁免，随后 `deviceidle whitelist` 和远端 `/status.host.deployment.batteryOptimizationIgnored` 都会翻成 `true`。The battery-optimization exemption path has now been revalidated on-device: the system confirmation dialog can grant the exemption successfully, and `deviceidle whitelist` plus remote `/status.host.deployment.batteryOptimizationIgnored` both flip to `true` afterward.
- 远端 `/status` 现在会带 `host.deployment` readiness 信息，而且 dedicated 模式下如果前台服务被任务移除或销毁，会排短延迟恢复闹钟。Remote `/status` now carries `host.deployment` readiness info, and in dedicated mode a short recovery alarm is scheduled if the foreground service is task-removed or destroyed.
- dedicated 模式现在还会维持一个低频 watchdog 闹钟，作为长时间闲置时的额外恢复路径。Dedicated mode now also maintains a low-frequency watchdog alarm as an extra recovery path during long idle periods.
- 但在当前接入的 OPPO / ColorOS V15 真机上，`Recents` 里把 `OpenClaw Node` 卡片划掉会触发系统 `Force stopping ai.openclaw.app ... o-stop(40)`，并把闹钟一起清空；这说明 dedicated 部署在该机型上必须避免 swipe-to-clear，并建议把卡片锁在最近任务里。But on the connected OPPO / ColorOS V15 device, swiping the `OpenClaw Node` card away from Recents triggers a system `Force stopping ai.openclaw.app ... o-stop(40)` and clears alarms as well; this means dedicated deployment on that device must avoid swipe-to-clear and should keep the card locked in Recents.

What remains optional / 仍可选的补强项:

- 如仍想进一步加固，可再做一次“已登录但真过期”的 debug-only expired-auth 真机验证；它现在不是 MVP 收尾 blocker。If more hardening is still wanted, we can still add one debug-only real-device validation for the "signed in but truly expired" case; it is no longer an MVP close-out blocker.
- 当前最现实的下一步已经不是补 streaming 证据，而是在 setup/packaging polish 与 UI 自动化下一阶段之间做取舍。The most practical next step is no longer collecting streaming evidence, but choosing between setup/packaging polish and the next UI-automation phase.

Important scope note / 重要范围说明:

- Android `Local Host` 现在不是“把桌面 Gateway/CLI 全量搬进手机”。Android `Local Host` is not yet "the full desktop Gateway/CLI moved into the phone."
- 当前聊天路径虽然仍不是“桌面 CLI 全量搬运”，但已经开始注册手机本地工具面。The current chat path is still not "the full desktop CLI moved over," but it has started to register an on-phone local tool surface.
- 手机上的可操作能力现在主要来自两块：精选 Android 原生命令，以及 app-private 本地 workspace；后者现在已经支持搜索、替换、复制、移动等文本工作区操作，但它仍然不是完整 shell / browser / plugin runtime。Most actionable capability on the phone now comes from two areas: curated Android native commands and an app-private local workspace; the workspace now supports search, replace, copy, and move style text-workspace operations, but it is still not a full shell / browser / plugin runtime.
- 对于“闲置手机常驻部署”，当前已经有一层 Android 原生 keepalive 架构，但它仍然是移动端受限模型，不等于桌面 OpenClaw 可以无限后台运行的完整运行时。For "idle phone always-on deployment", there is now an Android-native keepalive layer, but it is still a mobile-constrained model rather than the full desktop OpenClaw runtime running without limits in the background.
- 远程 `chat/send-wait` 会以 `remote-operator` 角色运行，所以 workspace 写能力也已经接到现有 write gate；不开写权限时，远端只能读 / 列 / 搜 / 查 stat。Remote `chat/send-wait` runs as `remote-operator`, so workspace writes now share the existing write gate; without write mode enabled, remote sessions are limited to read/list/search/stat actions.
- 所以“接上 GPT 但很多桌面功能不能用”目前属于产品范围限制，不是单纯授权异常。So "GPT is connected but many desktop features do not work" is currently a product-scope limitation, not just an auth problem.

## Today Work Log / 今日工作日志

### March 23, 2026 / 2026 年 3 月 23 日

- 完成真实设备构建、安装、重启和 `Local Host` 联调。Finished real-device build, install, relaunch, and `Local Host` bring-up.
- 完成 Codex 登录闭环。Finished the Codex sign-in loop.
- 授权成功后定位到 Codex Responses 的 `400 {"detail":"Unsupported content type"}`。After auth succeeded, identified Codex Responses returning `400 {"detail":"Unsupported content type"}`.
- 修正 `apps/android/app/src/main/java/ai/openclaw/app/host/OpenAICodexResponsesClient.kt` 的请求兼容性，并为结构化错误和请求形状补了测试。Fixed request compatibility in `apps/android/app/src/main/java/ai/openclaw/app/host/OpenAICodexResponsesClient.kt` and added tests for request shape plus structured errors.
- 把 `apps/android/app/src/main/java/ai/openclaw/app/host/LocalHostRuntime.kt` 的本机会话 ID 改成 UUID，避免继续沿用 `main` 这种非会话标识。Changed local-host session IDs in `apps/android/app/src/main/java/ai/openclaw/app/host/LocalHostRuntime.kt` to UUIDs instead of reusing `main`.
- 重新安装 APK 后，远控 `/chat/send-wait` 成功返回 `Android local host is working.`。After reinstalling the APK, remote `/chat/send-wait` returned `Android local host is working.` successfully.
- 验证了无效 token、关闭的相机层、关闭的写命令层这三类边界。Validated three boundary cases: invalid token, disabled camera tier, and disabled write tier.
- 运行 `bash apps/android/scripts/local-host-remote-smoke.sh` 成功。Ran `bash apps/android/scripts/local-host-remote-smoke.sh` successfully.
- 新增 `apps/android/app/src/main/java/ai/openclaw/app/host/LocalHostCodexAuthController.kt`，并把 `auth/codex/status`、`auth/codex/refresh` 接入远控面。Added `apps/android/app/src/main/java/ai/openclaw/app/host/LocalHostCodexAuthController.kt` and wired `auth/codex/status` plus `auth/codex/refresh` into remote access.
- 真机调用 `POST /api/local-host/v1/auth/codex/refresh` 成功，`expiresAt` 向前推进，之后 `/chat/send-wait` 仍返回 `Codex refresh still works.`。On-device `POST /api/local-host/v1/auth/codex/refresh` succeeded, `expiresAt` moved forward, and `/chat/send-wait` still returned `Codex refresh still works.` afterward.
- 新增并运行 `bash apps/android/scripts/local-host-permission-smoke.sh`，覆盖 `contacts.search`、`calendar.events`、`photos.latest`、`system.notify` 的权限缺失错误。Added and ran `bash apps/android/scripts/local-host-permission-smoke.sh`, covering permission-missing errors for `contacts.search`, `calendar.events`, `photos.latest`, and `system.notify`.
- 继续把手机本地能力往“离开电脑也能独立工作”推进：`workspace` 新增搜索、替换、复制、移动能力，并复用 write gate 收紧远程聊天下的写操作。Continued pushing the phone-side capability toward "independent away from a computer": `workspace` now supports search, replace, copy, and move actions, and reuses the write gate to keep remote chat writes constrained.
- 在当前机器上补齐 Android SDK 并成功运行定向 `Local Host` 单测集。Installed the Android SDK on the current machine and successfully ran the targeted `Local Host` unit-test suite.

### March 24, 2026 / 2026 年 3 月 24 日

- 把 `Dedicated host deployment` 开关和说明接进 `apps/android/app/src/main/java/ai/openclaw/app/ui/ConnectTabScreen.kt`。Wired the `Dedicated host deployment` switch and explanatory copy into `apps/android/app/src/main/java/ai/openclaw/app/ui/ConnectTabScreen.kt`.
- 新增 `apps/android/app/src/main/java/ai/openclaw/app/LocalHostDedicatedDeploymentManager.kt` 和 `apps/android/app/src/main/java/ai/openclaw/app/LocalHostBootReceiver.kt`，接上开机 / 解锁 / 升级后恢复。Added `apps/android/app/src/main/java/ai/openclaw/app/LocalHostDedicatedDeploymentManager.kt` and `apps/android/app/src/main/java/ai/openclaw/app/LocalHostBootReceiver.kt` to restore the host after boot, unlock, or app upgrades.
- `apps/android/app/src/main/java/ai/openclaw/app/NodeForegroundService.kt` 现在会在 dedicated 模式下观察本机 Host 掉线并自愈重连。`apps/android/app/src/main/java/ai/openclaw/app/NodeForegroundService.kt` now watches for local-host disconnects in dedicated mode and self-heals by reconnecting.
- 新增 dedicated-mode 回归测试，并再次运行定向 Android 单测集，结果 `BUILD SUCCESSFUL`。Added dedicated-mode regression tests and reran the targeted Android unit-test suite with a `BUILD SUCCESSFUL` result.
- 新增 `apps/android/app/src/main/java/ai/openclaw/app/DedicatedHostSupport.kt`，并把电池优化豁免状态接进 `apps/android/app/src/main/java/ai/openclaw/app/ui/ConnectTabScreen.kt` 与 `apps/android/app/src/main/java/ai/openclaw/app/node/DeviceHandler.kt`。Added `apps/android/app/src/main/java/ai/openclaw/app/DedicatedHostSupport.kt` and wired battery-optimization exemption state into `apps/android/app/src/main/java/ai/openclaw/app/ui/ConnectTabScreen.kt` plus `apps/android/app/src/main/java/ai/openclaw/app/node/DeviceHandler.kt`.
- `apps/android/app/src/main/java/ai/openclaw/app/NodeForegroundService.kt` 现在还会在 task removed / destroy 时为 dedicated 模式排恢复闹钟，`apps/android/app/src/main/java/ai/openclaw/app/host/LocalHostRuntime.kt` 会把 deployment readiness 带进远控 `/status`。`apps/android/app/src/main/java/ai/openclaw/app/NodeForegroundService.kt` now also schedules a recovery alarm for dedicated mode on task-removed / destroy, and `apps/android/app/src/main/java/ai/openclaw/app/host/LocalHostRuntime.kt` now includes deployment readiness in remote `/status`.
- `apps/android/app/src/main/java/ai/openclaw/app/NodeForegroundService.kt` 现在还会在服务启动时重新武装 watchdog，`apps/android/app/src/main/java/ai/openclaw/app/DedicatedHostSupport.kt` 则把 watchdog 状态带进 deployment readiness。`apps/android/app/src/main/java/ai/openclaw/app/NodeForegroundService.kt` now also rearms the watchdog on service starts, while `apps/android/app/src/main/java/ai/openclaw/app/DedicatedHostSupport.kt` feeds watchdog state into deployment readiness.
- 再次在真机上打通电池优化豁免弹窗，确认 `deviceidle whitelist` 和远端 `/status.host.deployment.batteryOptimizationIgnored` 同步翻为 `true`。Revalidated the battery-optimization confirmation dialog on-device and confirmed both `deviceidle whitelist` and remote `/status.host.deployment.batteryOptimizationIgnored` flip to `true`.
- 在同一台 OPPO / ColorOS 设备上重复做了 `Recents` 划卡测试，确认即便已经电池豁免，划掉 `OpenClaw Node` 仍会把包置为 `stopped=true`、清空前台服务和 dedicated 闹钟。Repeated the Recents swipe-away test on the same OPPO / ColorOS device and confirmed that even with the battery exemption granted, swiping away `OpenClaw Node` still sets the package to `stopped=true` and clears both the foreground service and the dedicated alarms.
- 因此把 OEM 背景策略风险正式接入 `apps/android/app/src/main/java/ai/openclaw/app/DedicatedHostSupport.kt`、`apps/android/app/src/main/java/ai/openclaw/app/node/DeviceHandler.kt`、`apps/android/app/src/main/java/ai/openclaw/app/ui/ConnectTabScreen.kt`，让 UI 和远端 readiness 都明确提示“不要划掉卡片”。As a result, the OEM background-policy risk is now wired into `apps/android/app/src/main/java/ai/openclaw/app/DedicatedHostSupport.kt`, `apps/android/app/src/main/java/ai/openclaw/app/node/DeviceHandler.kt`, and `apps/android/app/src/main/java/ai/openclaw/app/ui/ConnectTabScreen.kt` so both the UI and remote readiness now explicitly warn "do not swipe the card away."

## Verified Commands / 已验证命令

Use placeholders instead of real live values. / 这里统一使用占位符，不写真实值。

Build and test / 构建与测试:

```bash
cd apps/android
./gradlew --no-daemon --console=plain :app:assembleDebug
./gradlew --no-daemon --console=plain :app:testDebugUnitTest \
  --tests ai.openclaw.app.SecurePrefsTest \
  --tests ai.openclaw.app.host.LocalHostCodexAuthControllerTest \
  --tests ai.openclaw.app.host.LocalHostRuntimeTest \
  --tests ai.openclaw.app.host.LocalHostRemoteAccessServerTest \
  --tests ai.openclaw.app.host.OpenAICodexResponsesClientTest
```

Install and relaunch / 安装与重启:

```bash
adb install -r -d apps/android/app/build/outputs/apk/debug/openclaw-2026.3.14-debug.apk
adb shell am force-stop ai.openclaw.app
adb shell am start -n ai.openclaw.app/.MainActivity
```

Remote status / 远控状态:

```bash
curl -sS \
  -H 'Authorization: Bearer <token-from-connect-tab>' \
  http://<phone-ip>:3945/api/local-host/v1/status
```

Remote Codex auth / 远控 Codex 授权:

```bash
curl -sS \
  -H 'Authorization: Bearer <token-from-connect-tab>' \
  http://<phone-ip>:3945/api/local-host/v1/auth/codex/status

curl -sS -X POST \
  -H 'Authorization: Bearer <token-from-connect-tab>' \
  http://<phone-ip>:3945/api/local-host/v1/auth/codex/refresh
```

Remote chat / 远控聊天:

```bash
curl -sS -X POST \
  -H 'Authorization: Bearer <token-from-connect-tab>' \
  -H 'Content-Type: application/json' \
  http://<phone-ip>:3945/api/local-host/v1/chat/send-wait \
  -d '{"message":"Reply with exactly: Android local host is working.","waitMs":60000}'
```

Remote invoke / 远控调用:

```bash
curl -sS -X POST \
  -H 'Authorization: Bearer <token-from-connect-tab>' \
  -H 'Content-Type: application/json' \
  http://<phone-ip>:3945/api/local-host/v1/invoke \
  -d '{"command":"device.status","args":{}}'
```

Remote UI state / 远控 UI 状态:

```bash
curl -sS -X POST \
  -H 'Authorization: Bearer <token-from-connect-tab>' \
  -H 'Content-Type: application/json' \
  http://<phone-ip>:3945/api/local-host/v1/invoke \
  -d '{"command":"ui.state","args":{}}'
```

Streaming smoke / 流式验证:

```bash
OPENCLAW_ANDROID_LOCAL_HOST_BASE_URL='http://<phone-ip>:3945' \
OPENCLAW_ANDROID_LOCAL_HOST_TOKEN='<token-from-connect-tab>' \
pnpm android:local-host:streaming
```

If direct phone egress cannot reach `chatgpt.com`, temporarily validate through a trusted LAN proxy or VPN path first. If raw `/events` returns `errorType=usage_limit_reached`, treat that as an account-quota blocker rather than a local-host transport regression. / 如果手机当前网络无法直连 `chatgpt.com`，先临时通过可信 LAN 代理或 VPN 路径验证。若原始 `/events` 返回 `errorType=usage_limit_reached`，应把它视为账号额度 blocker，而不是本机 Host 传输回归。

Smoke script / 冒烟脚本:

```bash
OPENCLAW_ANDROID_LOCAL_HOST_BASE_URL='http://<phone-ip>:3945' \
OPENCLAW_ANDROID_LOCAL_HOST_TOKEN='<token-from-connect-tab>' \
bash apps/android/scripts/local-host-remote-smoke.sh
```

Permission smoke / 权限冒烟:

```bash
OPENCLAW_ANDROID_LOCAL_HOST_BASE_URL='http://<phone-ip>:3945' \
OPENCLAW_ANDROID_LOCAL_HOST_TOKEN='<token-from-connect-tab>' \
bash apps/android/scripts/local-host-permission-smoke.sh
```

## Known Good Code Areas / 当前可信代码区域

- `apps/android/app/src/main/java/ai/openclaw/app/host/OpenAICodexResponsesClient.kt`
- `apps/android/app/src/main/java/ai/openclaw/app/host/LocalHostCodexAuthController.kt`
- `apps/android/app/src/main/java/ai/openclaw/app/host/LocalHostRuntime.kt`
- `apps/android/app/src/main/java/ai/openclaw/app/accessibility/OpenClawAccessibilityService.kt`
- `apps/android/app/src/main/java/ai/openclaw/app/accessibility/LocalHostUiAutomation.kt`
- `apps/android/app/src/main/java/ai/openclaw/app/node/UiAutomationHandler.kt`
- `apps/android/app/src/test/java/ai/openclaw/app/host/LocalHostCodexAuthControllerTest.kt`
- `apps/android/app/src/test/java/ai/openclaw/app/host/OpenAICodexResponsesClientTest.kt`
- `apps/android/app/src/test/java/ai/openclaw/app/host/LocalHostRuntimeTest.kt`
- `apps/android/app/src/test/java/ai/openclaw/app/node/UiAutomationHandlerTest.kt`
- `apps/android/local-host-progress.md`
- `apps/android/local-host-self-check.md`

## Known Quirks / 已知注意事项

- `./gradlew :app:installDebug` 在这台设备上可能触发 ddmlib `InstallException: -99`，但直接 `adb install -r -d ...apk` 是可行的。`./gradlew :app:installDebug` may hit ddmlib `InstallException: -99` on this device, but direct `adb install -r -d ...apk` works.
- 在当前 OPPO / ColorOS 真机上，重新安装 APK 后 accessibility grant 会被系统清空；如果 `ui.state` 突然回到 disabled，要先重新开启 OpenClaw 的无障碍服务。On the current OPPO / ColorOS phone, reinstalling the APK clears the accessibility grant; if `ui.state` suddenly returns to disabled again, re-enable the OpenClaw accessibility service first.
- `pnpm android:local-host:smoke` 依赖当前 shell 能找到 `pnpm`；如果环境里 `pnpm` shim 不可用，直接调用脚本本体即可。`pnpm android:local-host:smoke` depends on a working `pnpm` shim; if `pnpm` is unavailable in the shell, run the script directly instead.
- 这台 Android 15 设备拒绝 shell 侧 `pm revoke` 和 `appops set`；权限脚本会在权限已被拒绝时直接验证失败路径，只在权限已授予时才尝试临时撤回。This Android 15 device rejects shell-side `pm revoke` and `appops set`; the permission script validates already-denied cases directly and only attempts temporary revocation when a permission starts granted.
- 这台 OPPO / ColorOS V15 设备会把 `Recents` 划卡当成系统级 `force stop`，并清空 app 闹钟；对 dedicated 部署来说，这比普通后台回收更激进。This OPPO / ColorOS V15 device treats a Recents swipe-away as a system-level `force stop` and clears the app's alarms; for dedicated deployment this is more aggressive than ordinary background eviction.
- 已尝试用 `openclaw://auth/callback` deep link、浏览器成功页自动回跳和 `Return to OpenClaw` CTA 修正 Codex 浏览器授权回 App；单测和系统级 deep-link resolve 已通过，但在当前 OPPO / ColorOS 真机的真实浏览器授权流程里仍不能稳定自动回到 App。暂时继续依赖“手动切回 App / 粘贴 redirect URL 或 code”的兜底路径。We already tried to fix the Codex browser-auth return-to-app path with the `openclaw://auth/callback` deep link, browser-page auto-return, and a `Return to OpenClaw` CTA; unit tests and system-level deep-link resolution pass, but on the current OPPO / ColorOS device the real browser auth flow still does not reliably jump back into the app. For now we keep relying on the fallback path of manually switching back to the app or pasting the redirect URL / code.
- 如果将来再次看到 `errorType=usage_limit_reached`，优先把它当成账号额度边界，而不是 Android streaming 实现报错。If `errorType=usage_limit_reached` appears again in the future, treat it as an account-usage boundary first rather than an Android streaming implementation error.
- 不要把真实 token、真实手机 IP、或个人设备标识写进提交。Do not commit real tokens, the real phone IP, or personal device identifiers.

## Next Tasks / 接下来要做的事

### P0 / 最高优先级

1. 先把当前真机 observe / wait / act 证据做成可重复的 smoke 路径。Turn the current real-device observe / wait / act proof into a repeatable smoke path first.
2. 先把 `ui.launchApp + ui.inputText` 做成可重复的真机路径，并调查当前 OPPO / ColorOS 的后台冻结风险，再决定下一条跨 app primitive 是 `swipe` 还是更强 selector。First turn `ui.launchApp + ui.inputText` into a repeatable real-device path and investigate the current OPPO / ColorOS background-freeze risk, then decide whether `swipe` or stronger selectors should be the next cross-app primitive.

### P1 / 次优先级

1. 继续强化 selector 模型，优先资源 ID、包名作用域和更稳定的节点匹配顺序。Keep strengthening the selector model, prioritizing resource IDs, package-name scoping, and more stable node matching order.
2. 如需补强，再做一次更强的 expired-auth 验证，但继续把它视为 hardening，不是 blocker。If more hardening is wanted, run a stronger expired-auth validation, but keep treating it as hardening rather than a blocker.

## Tomorrow Checklist / 明日清单

If resuming tomorrow, do these in order. / 如果明天继续，按这个顺序推进。

1. 先跑一次 `/status` 与 `ui.state`，确认手机还在 `Local Host`，并检查 accessibility grant 是否还在。Run `/status` plus `ui.state` first, confirm the phone is still in `Local Host`, and check whether the accessibility grant is still present.
2. 若刚重装过 APK，优先恢复 OpenClaw 的无障碍服务，再继续任何 UI 自动化验证。If the APK was just reinstalled, restore the OpenClaw accessibility service before continuing any UI-automation validation.
3. 先把现有 `ui.state` / `ui.waitForText` / `ui.tap` / `ui.home` / `ui.back` 组合成可重复的 smoke 路径，并顺手补 `ui.launchApp -> follow-up check` 这条真机证据。Turn the existing `ui.state` / `ui.waitForText` / `ui.tap` / `ui.home` / `ui.back` set into a repeatable smoke path first, and add a `ui.launchApp -> follow-up check` device proof along the way.
4. 保持授权回跳问题处于挂起状态，不要让它打断当前 UI 自动化推进。Keep the auth-return issue parked so it does not interrupt the current UI-automation push.

## Suggested Next-Session Plan / 下一会话建议推进方式

1. 先读 `apps/android/local-host-progress.md` 的 `Resume Plan`。Start with the `Resume Plan` in `apps/android/local-host-progress.md`.
2. 先跑一次 `/status` 和 `ui.state`，确认手机仍在 `Local Host` 且 UI automation readiness 可见；若刚重装过 APK，先恢复 accessibility grant。Run `/status` and `ui.state` to confirm the phone is still in `Local Host` and UI-automation readiness is visible; if the APK was just reinstalled, restore the accessibility grant first.
3. 再读 `apps/android/local-host-ui-automation-plan.md` 里的当前 next-session plan，优先把现有闭环做成 smoke、把 `ui.launchApp + ui.inputText` 串成一条可重复路径，并继续记录 OPPO 后台冻结。Then read the current next-session plan in `apps/android/local-host-ui-automation-plan.md`, first turn the existing loop into a smoke path, combine `ui.launchApp + ui.inputText` into one repeatable path, and keep documenting the OPPO background freeze.
4. 如果要继续补强，直接看 `apps/android/local-host-self-check.md` 里关于 optional hardening 的说明。If more hardening is wanted, jump straight to the optional-hardening note in `apps/android/local-host-self-check.md`.

## Related Docs / 相关文档

- `apps/android/local-host-progress.md`
- `apps/android/local-host-phone-control.md`
- `apps/android/local-host-self-check.md`
- `apps/android/local-host-ui-automation-plan.md`
- `apps/android/README.md`
