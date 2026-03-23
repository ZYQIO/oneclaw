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
- [ ] 在真实 Android 设备上验证完整登录与刷新流程。Verify the complete login and refresh flow on a real Android device.

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
- 真实 Android 手机已经完成 Codex 登录，并成功返回 GPT 回复。A real Android phone has completed Codex sign-in and returned a GPT response successfully.
- 远程访问已经暴露 `/health`、`/status`、`/examples`、`/chat/*`、`/events` 和 `/invoke`。Remote access exposes `/health`, `/status`, `/examples`, `/chat/*`, `/events`, and `/invoke`.
- 远程 `/invoke` 命令已经分层，高风险能力默认不开放。Remote invoke commands are tiered so risky actions are not enabled by default.
- 已有可执行的远程冒烟脚本，可直接验证 `/status`、聊天和 `/invoke`。A runnable remote smoke script exists for `/status`, chat, and `/invoke`.

Current gaps / 当前缺口:

- 仍未在真实 Android 手机上主动验证一次 Codex refresh 成功路径。We still have not proactively validated a successful Codex refresh path on a real Android phone.
- 当前已验证 LAN 远控可用，但 `adb forward` 路径在这台设备上没有稳定回包。LAN remote access works, but the `adb forward` path did not respond reliably on this device.
- 远程 API 目前适合工具接入，但还不是完整打磨过的远程控制产品。The remote API is usable for tooling, but it is not yet packaged as a polished remote-control product.
- 权限缺失类失败场景还没有在真机上系统记录。Permission-missing failure paths have not been systematically captured on-device yet.

## Latest Validation / 最新验证

### March 23, 2026 / 2026 年 3 月 23 日

- [x] 安装 Java 17，并补齐 Android SDK command-line tools、`platforms;android-36`、`build-tools;36.0.0`、`platform-tools`。Installed Java 17 and the Android SDK command-line tools plus `platforms;android-36`, `build-tools;36.0.0`, and `platform-tools`.
- [x] 运行 `./gradlew --no-daemon --console=plain :app:assembleDebug` 并成功生成 debug APK。Ran `./gradlew --no-daemon --console=plain :app:assembleDebug` and produced the debug APK successfully.
- [x] 通过 `adb install -r -d apps/android/app/build/outputs/apk/debug/openclaw-2026.3.14-debug.apk` 安装到真实设备 `PFEM10`（Android 15）。Installed the debug APK onto the real `PFEM10` device (Android 15) with `adb install -r -d apps/android/app/build/outputs/apk/debug/openclaw-2026.3.14-debug.apk`.
- [x] 运行 `./gradlew --no-daemon --console=plain :app:testDebugUnitTest --tests ai.openclaw.app.SecurePrefsTest --tests ai.openclaw.app.host.LocalHostRuntimeTest --tests ai.openclaw.app.host.LocalHostRemoteAccessServerTest` 并通过。Ran `./gradlew --no-daemon --console=plain :app:testDebugUnitTest --tests ai.openclaw.app.SecurePrefsTest --tests ai.openclaw.app.host.LocalHostRuntimeTest --tests ai.openclaw.app.host.LocalHostRemoteAccessServerTest` successfully.
- [x] 在真机上绕过首次 onboarding，进入 `Local Host` 模式，并看到 `Connected` 状态。Bypassed first-run onboarding on the phone, entered `Local Host`, and observed the `Connected` state.
- [x] 远控 LAN 地址 `http://192.168.21.134:3945` 返回 `/status`、`/invoke/capabilities` 和 `device.status`。The LAN remote-access URL `http://192.168.21.134:3945` returned `/status`, `/invoke/capabilities`, and `device.status`.
- [x] `/chat/send-wait` 已返回清晰错误 `OpenAI Codex login required`，证明本机聊天和错误通路都已接通。`/chat/send-wait` returned the clear error `OpenAI Codex login required`, proving the local chat path and failure path are wired correctly.
- [x] Codex 浏览器授权已完成，`/status` 返回 `codexAuthConfigured=true`。Codex browser authorization completed, and `/status` returned `codexAuthConfigured=true`.
- [x] 重新安装并重启 App 后，远控 `/chat/send-wait` 成功返回 `Android local host is working.`。After reinstalling and restarting the app, remote `/chat/send-wait` successfully returned `Android local host is working.`.
- [x] 运行 `bash apps/android/scripts/local-host-remote-smoke.sh`（经 LAN 访问）成功，返回 `Android local host smoke passed.`。Ran `bash apps/android/scripts/local-host-remote-smoke.sh` over LAN successfully and received `Android local host smoke passed.`.
- [x] 远控 `/invoke` 的安全边界已在真机验证：`camera.snap` 与 `sms.send` 在对应命令层关闭时返回 `command is not enabled for remote access`。Remote `/invoke` safety boundaries were validated on-device: `camera.snap` and `sms.send` returned `command is not enabled for remote access` while their command tiers were disabled.
- [x] 无效 bearer token 调用 `/status` 返回 `401` 和 `Missing or invalid bearer token`。Calling `/status` with an invalid bearer token returned `401` and `Missing or invalid bearer token`.

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

### Failure Evidence / 失败证据

- [x] 非法 bearer token 返回 `401`。Invalid bearer token returns `401`.
- [x] 写操作层关闭时会阻止写命令。Disabled write tier blocks write commands.
- [x] Codex 缺失或过期时会给出清晰失败提示。Expired or missing Codex auth fails clearly.
- [ ] 权限依赖命令在权限缺失时会清晰失败。Permission-dependent commands fail clearly when permission is missing.

## Recommended Next Slice / 推荐下一步

Do not treat the project as fully complete yet. / 现在还不应把项目视为已经完成。

The next best step is validation hardening, in this order: / 当前最值得推进的是做验证加固，顺序如下：

1. 主动验证一次 Codex refresh 成功路径。Proactively validate a successful Codex refresh path.
2. 记录权限缺失场景下的失败行为。Capture permission-missing failure behavior.
3. 复核远程默认值、token 轮换和网络暴露指引。Review remote defaults, token rotation, and network exposure guidance.
4. 判断是否真的需要为 MVP 开放更多远程命令。Decide whether the MVP actually needs more remote commands.

If that passes, the follow-up slice should be setup guidance and packaging polish, not more protocol churn. / 如果这些通过，下一步应优先做设置指引和交付打磨，而不是继续折腾协议细节。
