# Android Local Host Progress / Android 本机 Host 进度

Scope / 范围: track the Android effort to run OpenClaw locally on the phone, authenticate with Codex, and expose a safe remote-control surface. / 追踪 Android 端在手机本机运行 OpenClaw、使用 Codex 授权，并暴露安全远程控制面的推进情况。

## Objective / 目标

Ship an Android-hosted MVP that can: / 交付一个运行在 Android 手机上的 MVP，具备以下能力：

- 在手机上以 `Local Host` 模式本地运行 OpenClaw。Run OpenClaw locally on the phone in `Local Host` mode.
- 使用 `openai-codex` 完成授权。Authenticate with `openai-codex`.
- 通过 GPT 完成本地聊天的发送与返回。Send and receive local chat turns through GPT.
- 为可信远端客户端提供带保护的远程控制 API。Expose a guarded remote-control API for trusted remote clients.

## Definition Of Done / 完成定义

This effort is considered done when all of the following are true. / 当且仅当以下条件全部满足时，才视为该任务完成。

- Android can start `Local Host` without relying on an external Gateway. / Android 可以在不依赖外部 Gateway 的情况下启动 `Local Host`。
- Codex sign-in works from the app and survives token refresh. / Codex 登录可在 App 内完成，并且 token 刷新后仍然可用。
- Local-host chat succeeds end-to-end on a real phone. / 本机 Host 聊天链路已在真实手机上端到端跑通。
- A trusted remote client can call `/status`, `/chat/send-wait`, and at least one `/invoke` command successfully. / 可信远端客户端可以成功调用 `/status`、`/chat/send-wait` 以及至少一个 `/invoke` 命令。
- Remote command tiers are explicitly gated and visible in UI and API. / 远程命令分层具备明确开关，并在 UI 和 API 中可见。
- Real-device validation evidence exists for the happy path and key failure paths. / 已有真实设备上的成功路径和关键失败路径验证证据。
- Android build, unit tests, and smoke validation pass in a reproducible environment. / Android 构建、单测和冒烟验证可在可复现环境中通过。

## Non Goals / 非目标

The current MVP does not aim to deliver all desktop Gateway features. / 当前 MVP 不以覆盖桌面 Gateway 的全部能力为目标。

- 不追求与 Node Gateway 的完整插件运行时对齐。No full plugin runtime parity with the Node Gateway.
- 不追求与 Web Control UI 的完整控制台对齐。No full control UI parity with the web Control UI.
- 不提供不受限制的远程执行或 shell 访问。No unrestricted remote execution or shell access.
- 不默认做未加固的公网暴露。No public internet exposure without explicit network hardening.

## Milestones / 里程碑

### M1. Local Host Foundation / 本机 Host 基础层

- [x] 在 Android UI 中加入 `Local Host` 运行模式。Add `Local Host` runtime mode in Android UI.
- [x] 持久化本机 Host 设置和 Codex 凭证状态。Persist local-host settings and Codex credential state.
- [x] 将聊天和语音代码改为通过抽象传输层复用本地与远端后端。Route chat and voice code through transport abstractions so local and remote backends can share UI.
- [x] 搭建本机 Host 运行时，并支持 session、history、chat 方法。Stand up a local-host runtime with session, history, and chat methods.

### M2. Codex Auth And Model Calls / Codex 授权与模型调用

- [x] 加入原生 Codex OAuth 登录流程。Add native Codex OAuth login flow.
- [x] 持久化 Codex 凭证并支持刷新。Persist Codex credential and support refresh.
- [x] 在本机 Host 聊天中接入 Codex Responses。Call Codex Responses from local-host chat.
- [x] 在真实 Android 设备上验证完整登录与刷新流程。Verify the complete login and refresh flow on a real Android device.

### M3. Remote Access MVP / 远程访问 MVP

- [x] 加入带 bearer token 保护的本机 Host 远程 API。Add bearer-token protected local-host remote API.
- [x] 加入聊天路由、事件轮询和同步等待聊天结果。Add chat routes, event polling, and sync chat wait.
- [x] 加入自描述路由：`/status`、`/examples`、`/invoke/capabilities`。Add self-describing routes: `/status`, `/examples`, `/invoke/capabilities`.
- [x] 将远程命令拆分为只读、相机高级、写操作三级。Split remote commands into read, camera-advanced, and write-capable tiers.
- [x] 验证真实远端客户端可以经网络驱动手机。Verify a real remote client can drive the phone over the network.

### M4. Validation And Hardening / 验证与加固

- [x] 提供可复用的本机 Host 远控冒烟脚本。Add a reusable local-host remote smoke script.
- [x] 在装有 Java 的环境中跑通 Android Gradle 编译和单测。Run Android Gradle compile and unit tests in an environment with Java installed.
- [x] 安装到真实 Android 手机并验证主成功路径。Install on a real Android phone and validate the happy path.
- [ ] 记录 Codex 过期、权限缺失、远程命令层关闭等失败行为。Capture failure behavior for expired Codex auth, missing permissions, and disabled remote tiers.
- [ ] 复核远程访问默认值、token 轮换流程和网络暴露指引。Review remote access defaults, token rotation flow, and network guidance.
- [ ] 判断 MVP 是否还需要开放更多远程命令。Decide whether any additional remote commands should be enabled for MVP.

## Current State / 当前状态

Completed implementation highlights / 已完成实现要点:

- `Local Host` 模式已经出现在 Connect tab，并作为一等运行模式暴露。`Local Host` mode exists in the Connect tab and is exposed as a first-class runtime option.
- Codex OAuth 登录已经具备浏览器流程和手动粘贴回退。Codex OAuth login exists with browser flow and manual paste fallback.
- 本机 Host 聊天已接到使用 `gpt-5.4` 的 Codex Responses。Local-host chat is wired to Codex Responses using `gpt-5.4`.
- 当前本机 Host 聊天仍是“直接调用 Codex Responses”的形态，还没有接入桌面 Gateway 那套完整 agent/tool/plugin/runtime。The current local-host chat is still a direct Codex Responses flow and does not yet include the full desktop Gateway agent/tool/plugin/runtime stack.
- 已开始第一段桌面对齐能力：Codex Responses 现在按设计接入本机 `nodes` function-calling 闭环，可把一组 Android 原生命令作为聊天工具使用；当前进一步往“手机独立可用”方向推进，优先补相机 / 相册这类不依赖电脑的原生能力，同时本机 `workspace` 已扩展到搜索、替换、复制、移动等随身工作区能力，并在当前机器上完成了 Android-SDK-backed 单测复核。The first desktop-parity slice is now underway: Codex Responses is wired toward a local `nodes` function-calling loop so chat can use a set of Android native commands as tools; the current follow-up explicitly pushes toward phone-independent use by prioritizing native camera/photo capabilities that do not depend on a nearby computer, while the local `workspace` has also been expanded with search, replace, copy, and move capabilities and has now been re-verified with Android-SDK-backed unit tests on the current machine.
- 新增了 `Dedicated host deployment` 形态，用于闲置手机部署：它会把前台服务保活策略、开机 / 升级后恢复，以及本机 Host 掉线后的自愈重连接进 `Local Host` 模式。A new `Dedicated host deployment` mode now exists for idle-phone deployment: it wires keepalive behavior, boot/package-update restore, and self-heal reconnect back into `Local Host`.
- `Dedicated host deployment` 现在还会显式暴露 Android 电池优化状态，并把请求豁免的入口放进本机 Host 界面；同时 `device.status` 已开始回传 `backgroundExecution.batteryOptimizationIgnored`，方便远端检查部署 readiness。`Dedicated host deployment` now also exposes Android battery-optimization state directly in the local-host UI and links to the exemption flow; `device.status` also returns `backgroundExecution.batteryOptimizationIgnored` so remote tooling can inspect deployment readiness.
- 本机远控 `/status` 现在也会回传 `host.deployment`，包含 dedicated 模式、keepalive eligibility、电池优化豁免等 readiness 信号；另外前台服务被任务移除或销毁时，会在 dedicated 模式下排一个短延迟恢复闹钟。Remote `/status` now also returns `host.deployment`, including dedicated-mode, keepalive eligibility, and battery-optimization readiness signals; in addition, when the foreground service is task-removed or destroyed, dedicated mode now schedules a short recovery alarm.
- dedicated 模式现在还会维持一个低频 watchdog 闹钟，用来在长时间闲置场景下继续兜底前台服务恢复，而不仅仅依赖 task removed / destroy 的短恢复路径。Dedicated mode now also keeps a low-frequency watchdog alarm armed so long-idle phones still have a fallback path to restore the foreground service instead of depending only on the short task-removed / destroy recovery path.
- 电池优化豁免链路已经在当前真机上重新验证通过：系统弹窗可授予豁免，随后 `deviceidle whitelist` 和远端 `/status.host.deployment.batteryOptimizationIgnored` 都会同步变成 `true`。The battery-optimization exemption flow has now been revalidated on the current phone: the system confirmation dialog can grant the exemption, after which both `deviceidle whitelist` and remote `/status.host.deployment.batteryOptimizationIgnored` flip to `true`.
- 但同一台 OPPO / ColorOS V15 真机也暴露出另一条独立边界：把 `OpenClaw Node` 从 `Recents` 划掉会触发系统 `force stop` 并清空闹钟，即使已经拿到电池优化豁免也一样。But the same OPPO / ColorOS V15 phone also exposed a separate boundary: swiping `OpenClaw Node` away from Recents triggers a system `force stop` and clears alarms, even after the battery-optimization exemption is granted.
- 因此 dedicated deployment 现在会把 OEM 背景策略风险显式带到 UI 和 readiness 快照里，而不再假设“电池优化豁免”就代表闲置机部署已经稳妥。As a result, dedicated deployment now surfaces OEM background-policy risk explicitly in both the UI and the readiness snapshot instead of assuming the battery exemption alone makes idle-phone deployment safe.
- 真实 Android 手机已经完成 Codex 登录，并成功返回 GPT 回复。A real Android phone has completed Codex sign-in and returned a GPT response successfully.
- 远程访问已经暴露 `/health`、`/status`、`/examples`、`/chat/*`、`/events` 和 `/invoke`。Remote access exposes `/health`, `/status`, `/examples`, `/chat/*`, `/events`, and `/invoke`.
- 远程 `/invoke` 命令已经分层，高风险能力默认不开放。Remote invoke commands are tiered so risky actions are not enabled by default.
- 已有可执行的远程冒烟脚本，可直接验证 `/status`、聊天和 `/invoke`。A runnable remote smoke script exists for `/status`, chat, and `/invoke`.

Current gaps / 当前缺口:

- 当前已验证 LAN 远控可用，但 `adb forward` 路径在这台设备上没有稳定回包。LAN remote access works, but the `adb forward` path did not respond reliably on this device.
- 远程 API 目前适合工具接入，但还不是完整打磨过的远程控制产品。The remote API is usable for tooling, but it is not yet packaged as a polished remote-control product.
- 目前“很多桌面端能力在手机上不可用”仍然是预期范围内现象：Android `Local Host` 现在已经有 app-private workspace，也开始接入相机 / 相册等手机原生工具，但仍然没有完整 shell / browser / plugin runtime。It is still expected that many desktop capabilities are unavailable on the phone: Android `Local Host` now has an app-private workspace and is starting to expose native phone tools such as camera and photos, but it still does not bundle a full shell / browser / plugin runtime.
- 还没有在真机上强制制造一次“已登录但 token 真实过期”的失败记录；当前证据覆盖缺失授权和主动 refresh 成功。We have not yet forced a real "signed in but truly expired token" failure on-device; current evidence covers missing auth and successful proactive refresh.
- 自检里“流式文本更新进入本机 Host 聊天管线”的真机证据仍未单独记录。The self-check item for streaming text updates entering the local-host chat pipeline still lacks dedicated real-device evidence.

## Latest Validation / 最新验证

### March 23, 2026 / 2026 年 3 月 23 日

- [x] 安装 Java 17，并补齐 Android SDK command-line tools、`platforms;android-36`、`build-tools;36.0.0`、`platform-tools`。Installed Java 17 and the Android SDK command-line tools plus `platforms;android-36`, `build-tools;36.0.0`, and `platform-tools`.
- [x] 运行 `./gradlew --no-daemon --console=plain :app:assembleDebug` 并成功生成 debug APK。Ran `./gradlew --no-daemon --console=plain :app:assembleDebug` and produced the debug APK successfully.
- [x] 通过 `adb install -r -d apps/android/app/build/outputs/apk/debug/openclaw-2026.3.14-debug.apk` 安装到一台真实 Android 15 设备。Installed the debug APK onto a real Android 15 device with `adb install -r -d apps/android/app/build/outputs/apk/debug/openclaw-2026.3.14-debug.apk`.
- [x] 运行 `./gradlew --no-daemon --console=plain :app:testDebugUnitTest --tests ai.openclaw.app.SecurePrefsTest --tests ai.openclaw.app.host.LocalHostRuntimeTest --tests ai.openclaw.app.host.LocalHostRemoteAccessServerTest` 并通过。Ran `./gradlew --no-daemon --console=plain :app:testDebugUnitTest --tests ai.openclaw.app.SecurePrefsTest --tests ai.openclaw.app.host.LocalHostRuntimeTest --tests ai.openclaw.app.host.LocalHostRemoteAccessServerTest` successfully.
- [x] 在真机上绕过首次 onboarding，进入 `Local Host` 模式，并看到 `Connected` 状态。Bypassed first-run onboarding on the phone, entered `Local Host`, and observed the `Connected` state.
- [x] 远控 LAN 地址 `http://<phone-ip>:3945` 返回 `/status`、`/invoke/capabilities` 和 `device.status`。The LAN remote-access URL `http://<phone-ip>:3945` returned `/status`, `/invoke/capabilities`, and `device.status`.
- [x] `/chat/send-wait` 已返回清晰错误 `OpenAI Codex login required`，证明本机聊天和错误通路都已接通。`/chat/send-wait` returned the clear error `OpenAI Codex login required`, proving the local chat path and failure path are wired correctly.
- [x] Codex 浏览器授权已完成，`/status` 返回 `codexAuthConfigured=true`。Codex browser authorization completed, and `/status` returned `codexAuthConfigured=true`.
- [x] 重新安装并重启 App 后，远控 `/chat/send-wait` 成功返回 `Android local host is working.`。After reinstalling and restarting the app, remote `/chat/send-wait` successfully returned `Android local host is working.`.
- [x] 运行 `bash apps/android/scripts/local-host-remote-smoke.sh`（经 LAN 访问）成功，返回 `Android local host smoke passed.`。Ran `bash apps/android/scripts/local-host-remote-smoke.sh` over LAN successfully and received `Android local host smoke passed.`.
- [x] 远控 `/invoke` 的安全边界已在真机验证：`camera.snap` 与 `sms.send` 在对应命令层关闭时返回 `command is not enabled for remote access`。Remote `/invoke` safety boundaries were validated on-device: `camera.snap` and `sms.send` returned `command is not enabled for remote access` while their command tiers were disabled.
- [x] 无效 bearer token 调用 `/status` 返回 `401` 和 `Missing or invalid bearer token`。Calling `/status` with an invalid bearer token returned `401` and `Missing or invalid bearer token`.
- [x] 新增并验证 `GET /api/local-host/v1/auth/codex/status`，可返回脱敏后的授权状态与过期时间。Added and validated `GET /api/local-host/v1/auth/codex/status`, which returns sanitized auth metadata and expiry information.
- [x] 新增并验证 `POST /api/local-host/v1/auth/codex/refresh`，真机返回 `refreshed=true`，且 `expiresAt` 前移。Added and validated `POST /api/local-host/v1/auth/codex/refresh`; on-device it returned `refreshed=true`, and `expiresAt` moved forward.
- [x] 主动 refresh 之后，远控 `/chat/send-wait` 仍成功返回 `Codex refresh still works.`。After the proactive refresh, remote `/chat/send-wait` still returned `Codex refresh still works.` successfully.
- [x] 新增并运行 `bash apps/android/scripts/local-host-permission-smoke.sh`，覆盖 `contacts.search`、`calendar.events`、`photos.latest`、`system.notify` 的权限缺失错误。Added and ran `bash apps/android/scripts/local-host-permission-smoke.sh`, covering permission-missing errors for `contacts.search`, `calendar.events`, `photos.latest`, and `system.notify`.

## Session Log / 会话日志

### March 24, 2026 / 2026 年 3 月 24 日

- 开始把 Android `Local Host` 从“纯文本 Codex 聊天”推进到“Codex + 原生工具调用”形态。Started moving Android `Local Host` from plain-text Codex chat toward a `Codex + native tool-calling` shape.
- 新增本机 `nodes` 工具桥接与共享命令策略，把聊天里的工具调用接到 Android 原生 `/invoke` 命令面。Added a local `nodes` tool bridge plus shared command policy so chat-time tool calls can execute through the Android native `/invoke` surface.
- 远程会话会复用现有命令层级，不允许通过聊天工具调用绕过只读 / 高级 / 写操作开关。Remote sessions now reuse the existing command tiers so chat tool-calling cannot bypass the read-only / advanced / write gates.
- 本机 Host 运行时已开始发出 `agent.stream=tool` 事件，聊天 UI 可以沿用现有 pending-tool bubble 展示工具执行过程。The local-host runtime now emits `agent.stream=tool` events so the chat UI can reuse the existing pending-tool bubble for tool execution progress.
- 在这条基础上继续向“手机独立使用”推进：把相机列表、相机拍照、最近照片读取纳入聊天工具面，并把图片结果改造成 Responses 可直接理解的 `input_image` 回灌。Built on top of that toward more independent phone use: camera listing, camera snap, and recent-photo access are being added to the chat tool surface, and image results are rewritten into Responses-compatible `input_image` follow-up inputs.
- 新增 app-private `workspace` 工具，允许模型在手机本地沙箱里列目录、读写文本、创建目录、删除文件，把“记笔记/写草稿/落中间产物”这类能力真正搬到手机上。Added an app-private `workspace` tool so the model can list directories, read/write text, create folders, and delete files inside an on-device sandbox, bringing note-taking, drafting, and intermediate artifact storage onto the phone itself.
- 本机 `workspace` 进一步新增搜索、替换、复制、移动能力，并把远程聊天下的 workspace 写操作接到与 `/invoke` 一致的写权限开关上，避免远端经聊天工具绕过原有边界。The local `workspace` has been extended with search, replace, copy, and move capabilities, and remote-chat workspace writes now share the same write-enabled gate as `/invoke` so remote sessions cannot bypass existing boundaries through chat tools.
- 在当前机器上补齐 Android SDK 后，已成功运行 `LocalHostWorkspaceToolingTest`、`LocalHostNodesToolingTest`、`LocalHostRuntimeTest`、`LocalHostRemoteAccessServerTest`、`OpenAICodexResponsesClientTest`。After installing the Android SDK on the current machine, `LocalHostWorkspaceToolingTest`, `LocalHostNodesToolingTest`, `LocalHostRuntimeTest`, `LocalHostRemoteAccessServerTest`, and `OpenAICodexResponsesClientTest` now run successfully.

### March 23, 2026 / 2026 年 3 月 23 日

- 完成真实 Android 设备的构建、安装、重启和 LAN 远控基础验证。Completed real-device build, install, restart, and baseline LAN remote-control validation.
- 完成 Codex 浏览器授权闭环，并确认 `/status` 从 `codexAuthConfigured=false` 变为 `true`。Completed the Codex browser auth loop and confirmed `/status` flipped from `codexAuthConfigured=false` to `true`.
- 在授权完成后定位本机 Host 聊天失败的真实根因：Codex Responses 返回 `400 {"detail":"Unsupported content type"}`。Identified the real post-auth chat failure root cause: Codex Responses returned `400 {"detail":"Unsupported content type"}`.
- 修正 `OpenAICodexResponsesClient` 的请求兼容性：会话 ID 改为 UUID、请求头对齐 Codex、错误信息保留结构化细节，并把请求体收敛成单一 `application/json`。Fixed request compatibility in `OpenAICodexResponsesClient`: session IDs now use UUIDs, request headers align with Codex, error messages keep structured details, and the request body is forced to a single `application/json` content type.
- 新增并通过 `OpenAICodexResponsesClientTest`，把请求形状与错误透传锁成回归测试。Added and passed `OpenAICodexResponsesClientTest` to lock the request shape and structured error surfacing into regression coverage.
- 重新安装最新 APK 后，远控 `/chat/send-wait` 成功返回 `Android local host is working.`，标志着 Android 本机 Host + Codex + LAN 远控 happy path 成立。After reinstalling the latest APK, remote `/chat/send-wait` successfully returned `Android local host is working.`, confirming the Android local-host + Codex + LAN remote-control happy path.
- 运行仓库内建冒烟脚本成功，证明不是只靠手工 `curl` 才能复现。Ran the built-in smoke script successfully, proving the flow is not limited to ad-hoc manual `curl`.
- 新增 `LocalHostCodexAuthController` 和远控 `auth/codex/*` 路由，把 Codex 授权状态与主动 refresh 做成可重复的真机验证面。Added `LocalHostCodexAuthController` and remote `auth/codex/*` routes so Codex auth status and proactive refresh are repeatable on-device validation surfaces.
- 在真机上验证主动 refresh 成功，且 refresh 后的聊天链路仍然可用。Validated proactive refresh on the phone and confirmed chat still works afterward.
- 新增 `local-host-permission-smoke.sh`，把权限缺失失败场景变成可直接复跑的脚本。Added `local-host-permission-smoke.sh` to turn permission-missing failure cases into a directly repeatable script.
- 记录了这台 Android 15 设备的一个行为差异：shell 缺少 runtime revoke / appops 修改能力，因此脚本在权限已被拒绝时会直接验证失败路径，只在权限已授予时才尝试临时撤回。Captured a device-specific behavior on this Android 15 phone: the shell lacks runtime revoke / appops mutation privileges, so the script validates already-denied cases directly and only attempts temporary revocation when a permission starts granted.
- 当前推荐的已验证基线是提交 `9b492a1fca` 以及后续文档提交。The recommended verified baseline is commit `9b492a1fca` plus the follow-up documentation commit.

### March 24, 2026 / 2026 年 3 月 24 日

- 新增 `Dedicated host deployment` 开关，把闲置手机部署模式直接暴露在 `Connect -> Local Host`。Added a `Dedicated host deployment` switch so the idle-phone deployment mode is directly exposed in `Connect -> Local Host`.
- 新增 `apps/android/app/src/main/java/ai/openclaw/app/LocalHostDedicatedDeploymentManager.kt`，把“是否应保活”收敛成单点判断。Added `apps/android/app/src/main/java/ai/openclaw/app/LocalHostDedicatedDeploymentManager.kt` to centralize the "should this phone keep the host alive" decision.
- 新增 `apps/android/app/src/main/java/ai/openclaw/app/LocalHostBootReceiver.kt` 和 manifest 接线，使 `BOOT_COMPLETED`、`MY_PACKAGE_REPLACED`、`USER_UNLOCKED` 可以恢复本机 Host 前台服务。Added `apps/android/app/src/main/java/ai/openclaw/app/LocalHostBootReceiver.kt` plus manifest wiring so `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, and `USER_UNLOCKED` can restore the local-host foreground service.
- `apps/android/app/src/main/java/ai/openclaw/app/NodeForegroundService.kt` 现在会在 dedicated 模式下观察 `Local Host` 掉线，并延迟触发一次自愈重连。`apps/android/app/src/main/java/ai/openclaw/app/NodeForegroundService.kt` now watches for Local Host disconnects in dedicated mode and triggers a delayed self-heal reconnect.
- `apps/android/app/src/main/java/ai/openclaw/app/NodeRuntime.kt` 的前台状态默认值改为 `false`，避免服务侧拉起 runtime 时错误放开前台限定命令。The default foreground state in `apps/android/app/src/main/java/ai/openclaw/app/NodeRuntime.kt` is now `false` so service-started runtimes do not accidentally unlock foreground-only commands.
- 新增 `apps/android/app/src/test/java/ai/openclaw/app/LocalHostBootReceiverTest.kt`，并补充 `apps/android/app/src/test/java/ai/openclaw/app/SecurePrefsTest.kt`，覆盖 dedicated 开关持久化、保活条件判断和开机拉起。Added `apps/android/app/src/test/java/ai/openclaw/app/LocalHostBootReceiverTest.kt` and expanded `apps/android/app/src/test/java/ai/openclaw/app/SecurePrefsTest.kt` to cover dedicated-mode persistence, keepalive decision logic, and boot-time startup.
- 在当前机器上运行定向 Android 单测：`SecurePrefsTest`、`LocalHostBootReceiverTest`、`NodeForegroundServiceTest`、`LocalHostWorkspaceToolingTest`、`LocalHostNodesToolingTest`、`LocalHostRuntimeTest`、`LocalHostRemoteAccessServerTest`、`OpenAICodexResponsesClientTest`，结果 `BUILD SUCCESSFUL`。Ran the targeted Android unit tests on the current machine: `SecurePrefsTest`, `LocalHostBootReceiverTest`, `NodeForegroundServiceTest`, `LocalHostWorkspaceToolingTest`, `LocalHostNodesToolingTest`, `LocalHostRuntimeTest`, `LocalHostRemoteAccessServerTest`, and `OpenAICodexResponsesClientTest`, and the result was `BUILD SUCCESSFUL`.
- 新增 `apps/android/app/src/main/java/ai/openclaw/app/DedicatedHostSupport.kt`，把电池优化豁免检查与跳转逻辑集中管理，并把该状态接进 `apps/android/app/src/main/java/ai/openclaw/app/ui/ConnectTabScreen.kt` 和 `apps/android/app/src/main/java/ai/openclaw/app/node/DeviceHandler.kt`。Added `apps/android/app/src/main/java/ai/openclaw/app/DedicatedHostSupport.kt` to centralize battery-optimization exemption checks plus intents, and wired that state into `apps/android/app/src/main/java/ai/openclaw/app/ui/ConnectTabScreen.kt` and `apps/android/app/src/main/java/ai/openclaw/app/node/DeviceHandler.kt`.
- 再次运行定向 Android 单测：`DeviceHandlerTest`、`SecurePrefsTest`、`LocalHostBootReceiverTest`、`NodeForegroundServiceTest`、`LocalHostWorkspaceToolingTest`、`LocalHostNodesToolingTest`、`LocalHostRuntimeTest`、`LocalHostRemoteAccessServerTest`、`OpenAICodexResponsesClientTest`，结果 `BUILD SUCCESSFUL`。Reran the targeted Android unit tests: `DeviceHandlerTest`, `SecurePrefsTest`, `LocalHostBootReceiverTest`, `NodeForegroundServiceTest`, `LocalHostWorkspaceToolingTest`, `LocalHostNodesToolingTest`, `LocalHostRuntimeTest`, `LocalHostRemoteAccessServerTest`, and `OpenAICodexResponsesClientTest`, and the result was `BUILD SUCCESSFUL`.
- dedicated keepalive 现在补上了服务恢复闹钟与远控 readiness 快照：`apps/android/app/src/main/java/ai/openclaw/app/NodeForegroundService.kt` 在 task removed / destroy 时会走 `apps/android/app/src/main/java/ai/openclaw/app/DedicatedHostSupport.kt` 的恢复调度，`apps/android/app/src/main/java/ai/openclaw/app/host/LocalHostRuntime.kt` 则开始把 deployment 状态塞进远控 `/status`。Dedicated keepalive now includes a service-recovery alarm plus a remote readiness snapshot: `apps/android/app/src/main/java/ai/openclaw/app/NodeForegroundService.kt` now routes task-removed / destroy events through the recovery scheduler in `apps/android/app/src/main/java/ai/openclaw/app/DedicatedHostSupport.kt`, while `apps/android/app/src/main/java/ai/openclaw/app/host/LocalHostRuntime.kt` now feeds deployment status into remote `/status`.
- dedicated keepalive 进一步补上了低频 watchdog：`apps/android/app/src/main/java/ai/openclaw/app/NodeForegroundService.kt` 在启动时会重新武装 watchdog，而 `apps/android/app/src/main/java/ai/openclaw/app/DedicatedHostSupport.kt` 会把 `watchdogIntervalMs` 和 `watchdogEnabled` 暴露到 deployment readiness。Dedicated keepalive now also has a low-frequency watchdog: `apps/android/app/src/main/java/ai/openclaw/app/NodeForegroundService.kt` rearms it on service starts, and `apps/android/app/src/main/java/ai/openclaw/app/DedicatedHostSupport.kt` exposes `watchdogIntervalMs` plus `watchdogEnabled` in deployment readiness.
- 重新通过真机 UI 触发电池优化豁免，并确认 `adb shell dumpsys deviceidle whitelist` 出现 `user,ai.openclaw.app,...`，远端 `/status.host.deployment.batteryOptimizationIgnored` 也同步变成 `true`。Triggered the battery-optimization exemption again through the real-device UI and confirmed `adb shell dumpsys deviceidle whitelist` now contains `user,ai.openclaw.app,...`, while remote `/status.host.deployment.batteryOptimizationIgnored` also flips to `true`.
- 在同一台 OPPO / ColorOS V15 真机上重复 `Recents` 划卡测试，系统日志明确记录 `Force stopping ai.openclaw.app ... o-stop(40)`；随后 `stopped=true`、前台服务消失、dedicated 闹钟也被清空。Repeated the Recents swipe-away test on the same OPPO / ColorOS V15 device, and system logs explicitly recorded `Force stopping ai.openclaw.app ... o-stop(40)`; afterward the package was `stopped=true`, the foreground service was gone, and the dedicated alarms were cleared too.
- 因此把 OEM 背景策略风险正式接入 `DedicatedHostSupport`、`DeviceHandler` 和 `ConnectTabScreen`：远端 `device.status.backgroundExecution` 及 `/status.host.deployment` 会回传 `recentsSwipeForceStopRisk` / `taskLockRecommended`，本地 UI 也会提示 dedicated 部署不要划掉卡片。As a result, the OEM background-policy risk is now wired into `DedicatedHostSupport`, `DeviceHandler`, and `ConnectTabScreen`: remote `device.status.backgroundExecution` and `/status.host.deployment` now return `recentsSwipeForceStopRisk` / `taskLockRecommended`, and the local UI now warns dedicated deployments not to swipe the card away.

## Evidence Checklist / 证据清单

Use this section as an operator log when validating the feature on a phone. / 在手机上验证该功能时，可将本节作为操作记录清单。

### Build Evidence / 构建证据

- [x] `./gradlew :app:assembleDebug`
- [x] `./gradlew :app:testDebugUnitTest` (targeted local-host suite / 定向本机 Host 测试集)
- [ ] `./gradlew :app:installDebug` (`adb install` succeeded, but Gradle install hit ddmlib `InstallException: -99` on this device / `adb install` 成功，但 Gradle 安装在该设备上触发 ddmlib `InstallException: -99`)

### Device Evidence / 设备证据

- [x] App 以 `Local Host` 模式成功启动。App starts in `Local Host` mode.
- [x] Codex 登录成功。Codex sign-in succeeds.
- [x] 本地聊天返回 GPT 回复。Local chat returns a GPT response.
- [x] `/status` 返回可用性快照，且当前是 `codexAuthConfigured=true`。`/status` returns a readiness snapshot, currently with `codexAuthConfigured=true`.
- [x] 远端客户端成功完成 `/chat/send-wait` 并收到模型最终回复。A remote client completes `/chat/send-wait` and receives the model's final reply.
- [x] 至少一个只读 `/invoke` 命令成功。One read-only `/invoke` command succeeds.
- [ ] 至少一个分层命令仅在对应开关开启时成功。One gated command succeeds only when its tier is enabled.
- [x] `auth/codex/status` 与 `auth/codex/refresh` 在真机上成功工作。`auth/codex/status` and `auth/codex/refresh` work successfully on a real phone.
- [x] dedicated 部署电池优化豁免在真机上成功生效，`/status.host.deployment.batteryOptimizationIgnored=true`。The dedicated-deployment battery exemption now succeeds on-device, with `/status.host.deployment.batteryOptimizationIgnored=true`.

### Failure Evidence / 失败证据

- [x] 非法 bearer token 返回 `401`。Invalid bearer token returns `401`.
- [x] 写操作层关闭时会阻止写命令。Disabled write tier blocks write commands.
- [x] Codex 缺失或过期时会给出清晰失败提示。Expired or missing Codex auth fails clearly.
- [x] 权限依赖命令在权限缺失时会清晰失败。Permission-dependent commands fail clearly when permission is missing.
- [x] 当前 OPPO / ColorOS V15 真机上，`Recents` 划掉卡片会把 dedicated host 直接 `force stop` 并清空闹钟；UI 和 readiness 已开始显式提示该风险。On the current OPPO / ColorOS V15 phone, swiping the card away from Recents directly `force stop`s the dedicated host and clears alarms; the UI and readiness surfaces now warn about that risk explicitly.

## Recommended Next Slice / 推荐下一步

Do not treat the project as fully complete yet. / 现在还不应把项目视为已经完成。

The next best step is close-out hardening, in this order: / 当前最值得推进的是做收尾加固，顺序如下：

1. 复核远程默认值、token 轮换和网络暴露指引。Review remote defaults, token rotation, and network exposure guidance.
2. 判断自检里剩下的真机证据是否还需要补齐。Decide whether the remaining self-check evidence needs to be added on-device.
3. 判断是否真的需要为 MVP 开放更多远程命令。Decide whether the MVP actually needs more remote commands.

If that passes, the follow-up slice should be setup guidance and packaging polish, not more protocol churn. / 如果这些通过，下一步应优先做设置指引和交付打磨，而不是继续折腾协议细节。

## Tomorrow Tasks / 明天任务

Use this as the shortest checklist for the next working session. / 把这一节当作明天开工时的最短任务清单。

1. 复核远程访问默认值。Review remote-access defaults.
   Output / 产出:
   - 明确文档里只推荐 `LAN` 或可信隧道，不建议直接公网裸露。
   - 明确 `auth/codex/refresh`、token 轮换、读命令层/高级命令层/写命令层的推荐使用方式。
2. 判断 streaming-text 自检项是否仍需补证。Decide whether the streaming-text self-check item still needs more evidence.
   Output / 产出:
   - 若需要补证，写明最短验证步骤。
   - 若不需要补证，写明为什么当前证据已足够，以及剩余风险边界。
3. 判断 MVP 命令面是否收口。Decide whether the MVP remote-command surface is final enough.
   Output / 产出:
   - 若无需新增命令，明确记入文档并冻结当前范围。
   - 若需要新增命令，只列出真实需求驱动的最小补充项。

Stop point / 收工标准:

- `apps/android/local-host-progress.md`、`apps/android/local-host-self-check.md`、`apps/android/local-host-handoff.md` 三份文档对“是否完成 MVP 收尾”给出一致结论。
- 若没有新的阻塞，不再继续扩协议或命令面。

## Resume Plan / 接续推进计划

Use this section to resume work in a fresh session. / 新会话里继续推进时，优先看这一节。

### Immediate Goal / 当前直接目标

- 把 MVP 从“验证闭环基本完成”推进到“文档、默认值和自检门槛收尾完成”。Move the MVP from "validation loop mostly complete" to "docs, defaults, and self-check close-out complete."

### Priority Order / 优先顺序

1. 复核远程访问默认值与网络指引。Review remote-access defaults and network guidance.
   Exit criteria / 退出标准:
   - 明确默认只推荐 LAN / trusted tunnel。
   - 明确 token 轮换和高风险命令层的使用建议。
2. 判断自检剩余项是否需要继续补真机证据。Decide whether the remaining self-check items need more real-device evidence.
   Exit criteria / 退出标准:
   - 明确 `Go / No go` 还缺哪一条直接证据。
   - 若不再补，写明原因和风险边界。
3. 判断 MVP 命令面是否收口。Decide whether the MVP remote-command surface is final enough.
   Exit criteria / 退出标准:
   - 若无需新增命令，明确写入文档。
   - 若仍需新增命令，只为真实场景补最小集合。

### Recommended First Steps For The Next Session / 下一会话建议开局

- 先读 `apps/android/local-host-handoff.md`，确认当前状态、命令和剩余任务。Read `apps/android/local-host-handoff.md` first to confirm current state, commands, and remaining work.
- 先跑一次 `/status` 或冒烟脚本，确认设备仍在 `Local Host`。Run `/status` or the smoke script first to confirm the device is still in `Local Host`.
- 在做任何功能扩展前，先把远程默认值与自检结论收尾。Before expanding features, close out remote defaults and self-check conclusions first.
