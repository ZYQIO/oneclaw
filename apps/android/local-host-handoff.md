# Android Local Host Handoff / Android 本机 Host 接续手册

Purpose / 用途: make it easy to resume the Android local-host effort in a fresh chat without re-discovering the current state. / 让新会话可以快速接手 Android 本机 Host 项目，不必重新摸索当前状态。

## Current Objective / 当前目标

The active goal is still the same: / 当前目标仍然不变：

- 在 Android 手机上以 `Local Host` 模式本机运行 OpenClaw。Run OpenClaw on the phone itself in `Local Host` mode.
- 使用 Codex 授权访问 GPT。Use Codex auth for GPT access.
- 通过受保护的远程 API 从另一台设备控制手机上的本机 Host。Control the on-device local host from another trusted device through the guarded remote API.

## Current Status Snapshot / 当前状态快照

As of March 28, 2026, the MVP happy path is working, the streaming gate has direct on-device proof, the first repeatable UI smoke passes, the cross-app probe has real-device evidence, and the first multi-window sweep now shows no failure within 30 seconds. The active engineering focus has therefore moved from "is short-window cross-app reachability real?" to "what is the next repeatable follow-up action while another app stays on top?" / 截至 2026 年 3 月 28 日，MVP 的成功路径已经跑通，streaming 门槛已有真机直证，第一条可重复 UI smoke 已通过，cross-app probe 已拿到真机证据，而第一条多窗口 sweep 也已经证明 30 秒内没有掉线；当前工程重心因此已经从“短窗口跨 app 可达性是不是真的”转到“当另一个 app 仍在前台时，下一条可重复 follow-up action 是什么”。

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
- app 内中英文切换现在已经扩到更完整的主流程：`Settings -> Language` 会持久化 English / 简体中文 偏好，并立即切换 tab bar、Connect 页、Settings 页、onboarding、Chat / Voice 主界面、Voice 页的 mic/runtime 细状态，以及一批已知 runtime / auth 状态文案；其中 `Codex auth` 默认 OAuth 错误、gateway auth/pairing 常见错误、浏览器里的授权成功/失败页、Chat 页常见失败提示和连接边缘态也已经开始走同一条翻译路径。剩余缺口主要收敛到更深的次级文案、少量连接边缘态和零散错误文本。The in-app bilingual toggle now covers a broader primary flow: `Settings -> Language` persists the English / Simplified Chinese preference and immediately switches the tab bar, Connect tab, Settings tab, onboarding, the main Chat / Voice surfaces, the fine-grained mic/runtime status on the Voice screen, and a batch of known runtime/auth status strings; default `Codex auth` OAuth errors, common gateway auth/pairing failures, the browser-side auth success/failure page, common Chat-surface failures, and connection edge states are also starting to flow through the same translation path. The remaining gaps are now mostly deeper secondary copy, a smaller set of connection edge states, and scattered error text.
- 当前 MVP 远程命令面现在视为已收口：默认只读远控集 + 可选相机高级层 + 可选写操作层 + 共享 write gate 的 workspace 写能力；如果需要更广的手机操作能力，应转到单独的 UI 自动化阶段，而不是继续扩这条 MVP。The current MVP remote-command surface is now considered frozen: the default read-only remote set plus the optional camera advanced tier, the optional write tier, and workspace writes behind the shared write gate; broader phone-control capability should move into the separate UI-automation phase instead of expanding this MVP further.
- UI 自动化阶段已经不再只是规划：Android app 里已有 `AccessibilityService` 骨架、Connect 页 readiness、`/status.host.uiAutomation*` 状态，以及第一条只读 `ui.state` 命令；未开启服务时它会返回结构化原因，开启后会返回当前窗口的 `packageName`、`visibleText`、`nodeCount` 和扁平节点摘要。The UI-automation phase is no longer just a plan: the Android app now has an `AccessibilityService` skeleton, Connect-tab readiness, `/status.host.uiAutomation*` state, and a first read-only `ui.state` command; before the service is enabled it returns a structured reason, and once enabled it returns the active window's `packageName`, `visibleText`, `nodeCount`, and a flattened node summary.
- 2026 年 3 月 26 日已在真机补齐第一条 bounded control loop：write tier 关闭时远端只允许 `ui.state` / `ui.waitForText`，开启 write 后才放出 `ui.tap` / `ui.back` / `ui.home`；`ui.tap(text=\"Chat\")` 已成功切到 Chat tab，而 `ui.home` 与 `ui.back` 都能把活动 `packageName` 切到 `com.android.launcher`。On March 26, 2026, the first bounded control loop was completed on-device: with the write tier off, remote access only allows `ui.state` / `ui.waitForText`, and only after enabling write do `ui.tap` / `ui.back` / `ui.home` appear; `ui.tap(text=\"Chat\")` now successfully switches to the Chat tab, and both `ui.home` and `ui.back` move the active `packageName` to `com.android.launcher`.
- “怎么操控手机”的调研结论继续写在 `apps/android/local-host-ui-automation-plan.md`：主路线仍是 app 内 `AccessibilityService`，把 ADB / Appium / `Open-AutoGLM` 当参考而不是主运行时。The answer to “how should we control the phone” continues to live in `apps/android/local-host-ui-automation-plan.md`: the primary path is still an in-app `AccessibilityService`, while ADB / Appium / `Open-AutoGLM` remain references rather than the main runtime.
- 2026 年 3 月 26 日又补做了一轮以 GitHub 仓库和论文为主的新调研，并把外部参考清楚分成四层：`droidrun-portal` 负责 runtime 参考，`Open-AutoGLM` 负责外部主控闭环参考，`UI-TARS` / `MobileAgent` / `AgentCPM-GUI` / `ShowUI` 负责模型与 grounding 参考，`AndroidWorld` / `GUI-CEval` 负责验证参考；这条调研后来也兑现成了顺序落地：先 `ui.launchApp`，后 `ui.inputText`。On March 26, 2026, we also completed a fresh GitHub-plus-paper scan and separated external references into four layers: `droidrun-portal` for runtime inspiration, `Open-AutoGLM` for external-controller loops, `UI-TARS` / `MobileAgent` / `AgentCPM-GUI` / `ShowUI` for model and grounding ideas, and `AndroidWorld` / `GUI-CEval` for validation; that research later turned into the actual landing order too: `ui.launchApp` first, then `ui.inputText`.
- 2026 年 3 月 27 日已把 `launch_app` 落成实际命令 `ui.launchApp`：它采用 `packageName`-first 接口，走标准 Android launch intent，并把 “未安装” / “已安装但不可启动” 区分成清晰错误；这条命令也已经接进远端 write gate、`nodes` 工具桥接、Connect tab 示例和定向单测，但还需要一轮新的真机验证。On March 27, 2026, `launch_app` was turned into a real command as `ui.launchApp`: it uses a `packageName`-first contract, launches through standard Android launch intents, and distinguishes "not installed" from "installed but not launchable" with clear errors; the command is also wired into the remote write gate, the `nodes` tool bridge, Connect-tab examples, and targeted unit tests, but it still needs a fresh on-device validation pass.
- 同一天后来也补到了真机证据：远端 `ui.launchApp(packageName=\"com.android.settings\")` 返回 `launched=true`，并且 `adb shell dumpsys activity activities` 显示 `topResumedActivity=com.android.settings/.Settings`。Later the same day we also got the device proof: remote `ui.launchApp(packageName=\"com.android.settings\")` returned `launched=true`, and `adb shell dumpsys activity activities` showed `topResumedActivity=com.android.settings/.Settings`.
- 同一天还把 `input_text` 落成了实际命令 `ui.inputText`：它采用 focused-editable / selector-editable 的第一版边界，走 accessibility `ACTION_SET_TEXT`，并接进远端 write gate、`nodes` 工具桥接、Connect tab 示例和定向单测；随后在真机 Connect 页端口输入框上也已成功返回 `performed=true`。The same day also turned `input_text` into the real command `ui.inputText`: its first bounded form uses focused-editable / selector-editable targeting with accessibility `ACTION_SET_TEXT`, and it is wired into the remote write gate, the `nodes` tool bridge, Connect-tab examples, and targeted unit tests; afterward it also returned `performed=true` against the real-device Connect-screen port field.
- 同一天晚些时候，第一版 `pnpm android:local-host:ui` 也已经在真机通过：它经 `adb forward` 成功完成 `ui.launchApp(OpenClaw)`、`ui.tap(Chat)`、`ui.waitForText(Select thinking level)`、`ui.tap(Type a message…)`、`ui.inputText(写入/清空临时草稿)` 和最终 `ui.state`。Later that same day, the first `pnpm android:local-host:ui` pass also succeeded on-device: over `adb forward` it completed `ui.launchApp(OpenClaw)`, `ui.tap(Chat)`, `ui.waitForText(Select thinking level)`, `ui.tap(Type a message…)`, `ui.inputText` (write/clear a temporary draft), and the final `ui.state`.
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
- 第一版可重复 UI smoke 已经落成 `apps/android/scripts/local-host-ui-smoke.sh`，并通过 `pnpm android:local-host:ui` 暴露出来；默认它会先把 OpenClaw 自己拉回前台，再在 Chat 页上完成 `ui.tap`、`ui.waitForText`、`ui.inputText(写入/清空临时草稿)` 和 `ui.state` 这一条无副作用闭环。The first repeatable UI smoke now exists as `apps/android/scripts/local-host-ui-smoke.sh`, exposed as `pnpm android:local-host:ui`; by default it brings OpenClaw itself back to the foreground first, then completes a side-effect-free `ui.tap` / `ui.waitForText` / `ui.inputText` (write/clear a temporary draft) / `ui.state` loop on the Chat tab.
- 现在又多了一条独立的 cross-app probe：`pnpm android:local-host:ui:cross-app`。它不会只信 `ui.launchApp` 返回值，而是同时记录 ADB 的 `topResumedActivity`、远端 `/status` 是否继续可达，以及 adb 把 OpenClaw 拉回前台后的恢复结果。2026 年 3 月 28 日在当前 OPPO / ColorOS 真机上的第一次完整运行，针对 `com.android.settings` 得到了 `classification=foregrounded_host_reachable`，其中 `targetTopCount=9`、`statusSuccessCount=10`、`recovery.ok=true`。There is now also a separate cross-app probe as `pnpm android:local-host:ui:cross-app`. It does not trust the `ui.launchApp` return value alone; it records adb `topResumedActivity`, whether remote `/status` stays reachable, and the recovery result after adb brings OpenClaw back to the foreground. Its first full run on March 28, 2026 against `com.android.settings` on the current OPPO / ColorOS phone produced `classification=foregrounded_host_reachable`, with `targetTopCount=9`, `statusSuccessCount=10`, and `recovery.ok=true`.
- 同一条 cross-app probe 现在还支持可选的 repo-side follow-up harness：通过环境变量就能在 reachability polling 之前加上 `ui.waitForText`、`ui.tap`、`ui.inputText` 和最终 `ui.state`。这个能力已经进入仓库，但还没有新增的真机正证据，因此接手时要把它理解成“下一条验证入口已准备好”，而不是“已通过”。The same cross-app probe now also supports an optional repo-side follow-up harness: through env vars it can add `ui.waitForText`, `ui.tap`, `ui.inputText`, and a final `ui.state` before reachability polling begins. This capability is now in the repo, but it does not yet have new positive device proof, so when resuming you should treat it as "the next validation entrypoint is ready" rather than "already passed."
- 现在还多了一条多窗口 sweep：`pnpm android:local-host:ui:cross-app:sweep`。它会顺序复用 cross-app probe，并把多档观察窗口汇总成一份总 summary。2026 年 3 月 28 日默认 `5000,15000,30000` 三档 sweep 全部保持 `foregrounded_host_reachable`，顶层结果是 `allWindowsReachable=true`、`firstNonReachableWindowMs=null`。There is now also a multi-window sweep as `pnpm android:local-host:ui:cross-app:sweep`. It reuses the cross-app probe sequentially and summarizes multiple observation windows into one top-level summary. On March 28, 2026, the default `5000,15000,30000` sweep stayed `foregrounded_host_reachable` in every window, with top-level `allWindowsReachable=true` and `firstNonReachableWindowMs=null`.
- 现在还多了一条 dedicated readiness 探针：`pnpm android:local-host:dedicated:readiness`。2026 年 3 月 28 日当前接入的 OPPO `PFEM10` 真机返回：`accountsCount=5`、`hasDeviceOwner=false`、`dpcInstalled=false`、`bootloaderLocked=true`，因此当前最优路径被固定为 `device_owner_after_reset_or_account_cleanup`，而不是直接转去 root/systemize。There is now also a dedicated readiness probe as `pnpm android:local-host:dedicated:readiness`. On March 28, 2026, the connected OPPO `PFEM10` phone returned `accountsCount=5`, `hasDeviceOwner=false`, `dpcInstalled=false`, and `bootloaderLocked=true`, which pinned the current best path as `device_owner_after_reset_or_account_cleanup` instead of jumping straight to root/systemize.
- 现在还补上了 dedicated 的官方 QR provisioning 工具：`pnpm android:local-host:dedicated:testdpc-qr` 会抓取最新公开 `TestDPC` GitHub release、计算包校验和，并输出 setup wizard 可扫的 provisioning QR；这让“恢复出厂后走官方 dedicated-device 配置”从文档建议变成了可直接执行的仓库命令。There is now also an official dedicated QR provisioning tool: `pnpm android:local-host:dedicated:testdpc-qr` fetches the latest public `TestDPC` GitHub release, computes the package checksum, and outputs a setup-wizard-scannable provisioning QR, which turns the post-reset official dedicated-device path from documentation advice into a runnable repo command.
- app 端现在也正式接住了 lock-task：`MainActivity` 已声明 `android:lockTaskMode="if_whitelisted"`，并会在 dedicated 模式、本机 Host 模式、onboarding 完成且 DPC allowlist 到位时自动调用 `startLockTask()`；远端 `/status.host.deployment` 也会回传 `lockTaskPermitted`、`lockTaskAutoEnterReady`、`lockTaskModeState` 等字段。The app side now also handles lock-task directly: `MainActivity` declares `android:lockTaskMode="if_whitelisted"` and automatically calls `startLockTask()` when dedicated mode, local-host mode, onboarding completion, and the DPC allowlist are all in place; remote `/status.host.deployment` now also returns fields such as `lockTaskPermitted`, `lockTaskAutoEnterReady`, and `lockTaskModeState`.
- 现在还多了一条 post-provision checker：`pnpm android:local-host:dedicated:post-provision`，必要时可带 `-- --launch`。2026 年 3 月 28 日它在当前 OPPO `PFEM10` 真机上的首次 dry-run 表明：OpenClaw app 自身已经 ready，但 `Device Owner` 仍缺失、lock-task allowlist 为空；这使得 dedicated 剩余差距已经被压缩成 DPC provisioning 问题。There is now also a post-provision checker as `pnpm android:local-host:dedicated:post-provision`, optionally with `-- --launch`. Its first dry-run on March 28, 2026 on the current OPPO `PFEM10` phone showed that the OpenClaw app itself is already ready, while `Device Owner` is still missing and the lock-task allowlist is empty; that compresses the remaining dedicated-device gap down to DPC provisioning work.
- 现在还多了一条 TestDPC kiosk helper：`pnpm android:local-host:dedicated:testdpc-kiosk`。它默认只做 dry-run，会复用 post-provision checker，给出精确的 TestDPC `pm enable` / `am start ... KioskModeActivity` 命令，只有显式传入 `-- --apply` 时才会真正修改设备。There is now also a TestDPC kiosk helper as `pnpm android:local-host:dedicated:testdpc-kiosk`. It defaults to dry-run mode, reuses the post-provision checker, prints the exact TestDPC `pm enable` / `am start ... KioskModeActivity` commands, and only mutates the device when `-- --apply` is passed explicitly.
- 2026 年 3 月 28 日它在当前 OPPO `PFEM10` 真机上的第一次 dry-run 与 `--apply` 安全验证都表明：OpenClaw app 一侧已经 ready，但 helper 会因 `TestDPC is not installed` 和 `TestDPC is not the active Device Owner` 提前停下，`applySucceeded=false`。这意味着当前 dedicated 主线已经不是“还缺脚手架”，而是“差真正的 DPC provisioning 落地”。Its first dry-run and `--apply` safety validation on March 28, 2026 on the current OPPO `PFEM10` phone both showed that the OpenClaw app side is already ready, but the helper stops early on `TestDPC is not installed` and `TestDPC is not the active Device Owner`, leaving `applySucceeded=false`. That means the current dedicated-device lane is no longer missing project-side scaffolding; it is mainly blocked on real DPC provisioning.
- 现在还多了一条 TestDPC install helper：`pnpm android:local-host:dedicated:testdpc-install`。它会抓取最新公开 TestDPC release、下载 APK，并在 dry-run 模式下打印精确的 `adb install -r -d ...` 命令，必要时再通过 `-- --apply` 真正安装。There is now also a TestDPC install helper as `pnpm android:local-host:dedicated:testdpc-install`. It fetches the latest public TestDPC release, downloads the APK, prints the exact `adb install -r -d ...` command in dry-run mode, and only installs for real with `-- --apply` when needed.
- 2026 年 3 月 28 日它在当前 OPPO `PFEM10` 真机上的第一次 dry-run 已经成功拿到 `v9.0.12` / `TestDPC_9.0.12.apk`，并再次确认当前设备仍是 `installed=false`、`device_owner=false`。因此接下来如果要继续 dedicated 主线，优先动作已经从“去网上找安装包”收敛成了“决定何时执行 repo 里的 TestDPC install / provisioning 步骤”。Its first dry-run on March 28, 2026 on the current OPPO `PFEM10` phone already fetched `v9.0.12` / `TestDPC_9.0.12.apk` successfully and reconfirmed that the current device is still `installed=false` and `device_owner=false`. So if the dedicated-device lane continues from here, the next concrete move is no longer "go hunt for an APK online" but "decide when to execute the repo's TestDPC install / provisioning steps."

## Session Checkpoint / 会话暂停检查点

Read this section first when resuming later today. / 如果今天晚些时候继续，先读这一节。

### What The Project Is Trying To Finish / 当前收敛目标

- 主线 1：把现有 `ui.launchApp + ui.waitForText + ui.inputText` 从 app 内 smoke，推进到 launched app 仍在前台时也能做 follow-up action 的真机闭环。
- 主线 2：把闲置手机专机化路径收敛到官方 `Device Owner` / `TestDPC` 梯子，不再停留在“要不要写进系统”的讨论层。

### What Is Already True / 已确认事实

- `pnpm android:local-host:ui` 已经是稳定的 in-app 健康基线。
- `pnpm android:local-host:ui:cross-app:sweep` 已证明当前设置下 30 秒内仍属于 `foregrounded_host_reachable`。
- `pnpm android:local-host:ui:cross-app` 现在已经能承接可选 follow-up harness，但这条新路径还没有新增真机正证据。
- app 内中英文切换已经覆盖 post-onboarding 主页面、onboarding、Chat / Voice 主界面、Voice 页 mic/runtime 细状态，以及一批已知 runtime / auth 状态文本；`Codex auth` 的默认 OAuth 错误文案、gateway auth/pairing 常见连接态、Chat 页常见失败提示和浏览器里的授权成功/失败页也开始纳入同一路径，并通过本地编译 + `AppStringsTest` + `ConnectTabScreenStringsTest` + `OpenAICodexAuthManagerTest` + `SecurePrefsTest` 复核。
- OpenClaw app 一侧的 dedicated readiness 已基本到位，当前主要缺口在 DPC provisioning。
- dedicated 相关仓库命令现在已经形成链路：`readiness -> testdpc-install -> testdpc-qr -> post-provision -> testdpc-kiosk`。

### Do Not Reopen / 不要重新打开的旧问题

- 不要把 Codex 浏览器授权回跳问题重新当作当前 blocker；继续把它当成有兜底路径的非阻塞项。
- 不要把 `usage_limit_reached` 或旧 streaming 超时记录重新解释成 Android 本机 Host 回归。
- 不要把“做成系统服务 / root / 自定义 ROM”重新提到主优先级前面；当前最优先仍是 `Device Owner` / `TestDPC` 路线。
- 不要假设拿到电池优化豁免就等于 OPPO / ColorOS 上的 dedicated 稳定；`Recents` 划卡仍会 `force stop`。

### First Commands To Run Tonight / 今晚恢复时先跑这些命令

1. `pnpm android:local-host:ui`
2. `pnpm android:local-host:dedicated:readiness`
3. `pnpm android:local-host:dedicated:testdpc-install`
4. 如果要推进跨 app follow-up，给 `pnpm android:local-host:ui:cross-app` 补上目标 app 的 wait/tap/inputText 环境变量后再跑一轮
5. 如果确定要继续 dedicated 官方入管，再看 `pnpm android:local-host:dedicated:testdpc-qr`
6. 如果手机已经完成 DPC provisioning，再看 `pnpm android:local-host:dedicated:post-provision` 和 `pnpm android:local-host:dedicated:testdpc-kiosk`
7. 如果要继续双语工作，下一步直接扫剩余的深层 runtime / auth 错误文本、较少见的 gateway/control-ui 边缘态和次级页面，不要回头重做已经接好的 `Settings -> Language` 主流程。

## Next Tasks / 接下来要做的事

### P0 / 最高优先级

1. 把 `pnpm android:local-host:ui` 当作每次开工的健康基线，先确认新的默认 in-app UI smoke 仍能稳定复跑，再继续跨 app 工作。Use `pnpm android:local-host:ui` as the start-of-session health baseline so the default in-app UI smoke is reconfirmed before continuing any cross-app work.
2. 继续按两条线并行推进：一条是优先用现在这条可选 follow-up harness，把 `ui.launchApp + ui.inputText` 或其他 follow-up action 推进到 launched app 仍在前台的窗口里；另一条是按 `pnpm android:local-host:dedicated:readiness`、`pnpm android:local-host:dedicated:testdpc-install`、`pnpm android:local-host:dedicated:testdpc-qr` 这条顺序，把专机部署优先收敛到 `Device Owner` / `TestDPC` 路线，而不是直接跳去 root/systemize。Continue in two parallel lanes: first, use the new optional follow-up harness to move `ui.launchApp + ui.inputText` or another follow-up action into the window where the launched app is still on top; second, follow the `pnpm android:local-host:dedicated:readiness` -> `pnpm android:local-host:dedicated:testdpc-install` -> `pnpm android:local-host:dedicated:testdpc-qr` sequence so the dedicated-device track converges on the `Device Owner` / `TestDPC` lane instead of jumping straight to root/systemize.

### P1 / 次优先级

1. 继续强化 selector 模型，优先资源 ID、包名作用域和更稳定的节点匹配顺序。Keep strengthening the selector model, prioritizing resource IDs, package-name scoping, and more stable node matching order.
2. 把中英文切换继续扩到剩余的深层 runtime / auth 错误文本、连接边缘态和次级页面，但不要回头重做已经覆盖的 onboarding / Chat / Voice 主流程。Extend the English / Simplified Chinese toggle into the remaining deeper runtime/auth error text, connection edge states, and secondary screens, without reworking the already-covered onboarding and main Chat / Voice flow.
3. 如需补强，再做一次更强的 expired-auth 验证，但继续把它视为 hardening，不是 blocker。If more hardening is wanted, run a stronger expired-auth validation, but keep treating it as hardening rather than a blocker.

## Tomorrow Checklist / 明日清单

If resuming tomorrow, do these in order. / 如果明天继续，按这个顺序推进。

1. 先跑一次 `/status` 与 `ui.state`，确认手机还在 `Local Host`，并检查 accessibility grant 是否还在。Run `/status` plus `ui.state` first, confirm the phone is still in `Local Host`, and check whether the accessibility grant is still present.
2. 若刚重装过 APK，优先恢复 OpenClaw 的无障碍服务，再继续任何 UI 自动化验证。If the APK was just reinstalled, restore the OpenClaw accessibility service before continuing any UI-automation validation.
3. 把 `pnpm android:local-host:ui` 当作基线回归，确认它仍能稳定完成 `ui.launchApp(OpenClaw)`、`ui.tap(Chat)`、`ui.waitForText(chat-ready)`、`ui.tap(editor)`、`ui.inputText(写入/清空草稿)` 和最终 `ui.state`。Use `pnpm android:local-host:ui` as the baseline regression check and confirm it still completes `ui.launchApp(OpenClaw)`, `ui.tap(Chat)`, `ui.waitForText(chat-ready)`, `ui.tap(editor)`, `ui.inputText` (write/clear draft), and the final `ui.state`.
4. dedicated 线先跑 `pnpm android:local-host:dedicated:readiness` 和 `pnpm android:local-host:dedicated:testdpc-install`，确认当前手机是否还停在 `TestDPC not installed`，不要直接跳到 `--apply`。On the dedicated-device lane, run `pnpm android:local-host:dedicated:readiness` plus `pnpm android:local-host:dedicated:testdpc-install` first, confirm whether the phone is still stuck at `TestDPC not installed`, and do not jump straight to `--apply`.
5. 保持授权回跳问题处于挂起状态，不要让它打断当前 UI 自动化推进。Keep the auth-return issue parked so it does not interrupt the current UI-automation push.

## Suggested Next-Session Plan / 下一会话建议推进方式

1. 先读 `apps/android/local-host-progress.md` 的 `Resume Plan`。Start with the `Resume Plan` in `apps/android/local-host-progress.md`.
2. 先跑一次 `/status` 和 `ui.state`，确认手机仍在 `Local Host` 且 UI automation readiness 可见；若刚重装过 APK，先恢复 accessibility grant。Run `/status` and `ui.state` to confirm the phone is still in `Local Host` and UI-automation readiness is visible; if the APK was just reinstalled, restore the accessibility grant first.
3. 先跑 `pnpm android:local-host:ui`，再跑 `pnpm android:local-host:dedicated:readiness` 和 `pnpm android:local-host:dedicated:testdpc-install`，确认 UI 自动化和 dedicated 两条主线都还在同一个状态面上。Run `pnpm android:local-host:ui` first, then run `pnpm android:local-host:dedicated:readiness` plus `pnpm android:local-host:dedicated:testdpc-install` so the UI-automation and dedicated-device lanes are reconfirmed on the same state snapshot.
4. 再读 `apps/android/local-host-ui-automation-plan.md` 里的当前 next-session plan，把 `ui.launchApp + ui.inputText` 串成一条跨 app 可重复路径，并继续记录 OPPO 后台冻结。Then read the current next-session plan in `apps/android/local-host-ui-automation-plan.md`, combine `ui.launchApp + ui.inputText` into one repeatable cross-app path, and keep documenting the OPPO background freeze.
5. 如果要继续补强，直接看 `apps/android/local-host-self-check.md` 里关于 optional hardening 的说明。If more hardening is wanted, jump straight to the optional-hardening note in `apps/android/local-host-self-check.md`.

## Related Docs / 相关文档

- `apps/android/local-host-progress.md`
- `apps/android/local-host-phone-control.md`
- `apps/android/local-host-self-check.md`
- `apps/android/local-host-ui-automation-plan.md`
- `apps/android/README.md`
