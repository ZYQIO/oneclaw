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
- 真实 Android 手机已经完成 Codex 登录，并成功返回 GPT 回复。A real Android phone has completed Codex sign-in and returned a GPT response successfully.
- 远程访问已经暴露 `/health`、`/status`、`/examples`、`/chat/*`、`/events` 和 `/invoke`。Remote access exposes `/health`, `/status`, `/examples`, `/chat/*`, `/events`, and `/invoke`.
- 远程 `/invoke` 命令已经分层，高风险能力默认不开放。Remote invoke commands are tiered so risky actions are not enabled by default.
- 已有可执行的远程冒烟脚本，可直接验证 `/status`、聊天和 `/invoke`。A runnable remote smoke script exists for `/status`, chat, and `/invoke`.

Current gaps / 当前缺口:

- 当前已验证 LAN 远控可用，但 `adb forward` 路径在这台设备上没有稳定回包。LAN remote access works, but the `adb forward` path did not respond reliably on this device.
- 远程 API 目前适合工具接入，但还不是完整打磨过的远程控制产品。The remote API is usable for tooling, but it is not yet packaged as a polished remote-control product.
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

### Failure Evidence / 失败证据

- [x] 非法 bearer token 返回 `401`。Invalid bearer token returns `401`.
- [x] 写操作层关闭时会阻止写命令。Disabled write tier blocks write commands.
- [x] Codex 缺失或过期时会给出清晰失败提示。Expired or missing Codex auth fails clearly.
- [x] 权限依赖命令在权限缺失时会清晰失败。Permission-dependent commands fail clearly when permission is missing.

## Recommended Next Slice / 推荐下一步

Do not treat the project as fully complete yet. / 现在还不应把项目视为已经完成。

The next best step is close-out hardening, in this order: / 当前最值得推进的是做收尾加固，顺序如下：

1. 复核远程默认值、token 轮换和网络暴露指引。Review remote defaults, token rotation, and network exposure guidance.
2. 判断自检里剩下的真机证据是否还需要补齐。Decide whether the remaining self-check evidence needs to be added on-device.
3. 判断是否真的需要为 MVP 开放更多远程命令。Decide whether the MVP actually needs more remote commands.

If that passes, the follow-up slice should be setup guidance and packaging polish, not more protocol churn. / 如果这些通过，下一步应优先做设置指引和交付打磨，而不是继续折腾协议细节。

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
