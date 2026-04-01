# Android Desktop Runtime Packaging Feasibility / Android 桌面运行时封装可行性

Purpose / 用途: evaluate whether OpenClaw can package selected desktop-runtime capabilities into the Android app, identify which deployment lanes are actually allowed, and define a realistic project shape that does not derail the current Android Local Host roadmap. / 评估 OpenClaw 是否能把精选桌面运行时能力封装进 Android app，明确哪些部署线路真的可行，并定义一个不会冲掉当前 Android Local Host 路线的现实立项形态。

Last updated / 最后更新: April 1, 2026 / 2026 年 4 月 1 日

## Question / 问题定义

- Here, "desktop runtime" means the desktop `Gateway/CLI` agent, tool, plugin, browser, and shell surface, not the current Android-native `Local Host` MVP. / 这里的“桌面运行时”指桌面 `Gateway/CLI` 的 agent、tool、plugin、browser 和 shell 能力面，而不是当前 Android 原生的 `Local Host` MVP。
- The question is not only "can Android execute more code?" but also "under which distribution and device-management assumptions can we ship it safely and legally?" / 这个问题不只是“Android 能不能多跑一些代码”，还包括“在什么分发和设备管理前提下，这件事才能安全、合规地交付”。

## Short Answer / 简短结论

- Yes, but not as one universal path. / 能做，但不是一条通用单路线。
- For internal, sideloaded, or dedicated-device deployments, OpenClaw can realistically package a curated desktop-runtime slice inside the app or an app-private sidecar directory. / 对 internal、sideload 或 dedicated-device 部署，OpenClaw 现实上可以把一段经过裁剪的桌面运行时封装进 app 或 app-private sidecar 目录。
- For public Google Play distribution, the lane is much narrower: executable code should ship in the APK/AAB or in Google-Play-delivered feature modules, not as OpenClaw-managed self-updating binaries fetched from our own server. / 对公开 Google Play 分发，这条路会窄很多：可执行代码应随 APK/AAB 或由 Google Play 下发的 feature module 一起交付，而不是由 OpenClaw 自己从自有服务器拉取并自更新。
- Shipping the entire desktop `shell/browser/plugin` runtime as the next Android milestone is not recommended. / 把“整套桌面 `shell/browser/plugin` runtime”当成 Android 下一里程碑并不推荐。

## Why This Is Not The Current Primary Milestone / 为什么这不是当前主里程碑

- The current repo direction is still Android-native `Local Host` first, and `apps/android/local-host-ui-automation-plan.md` already says not to copy the entire desktop runtime into Android first. / 当前仓库主路线仍然是 Android 原生 `Local Host` 优先，而 `apps/android/local-host-ui-automation-plan.md` 已经明确写了不要先把整套桌面 runtime 硬搬进 Android。
- The Android MVP has already proven Codex auth, chat, remote `/invoke`, UI automation, and dedicated-device scaffolding. The highest-value work today is keeping those loops replayable. / Android MVP 已经证明了 Codex 授权、聊天、远端 `/invoke`、UI 自动化和 dedicated-device 脚手架；当前最高价值的工作是把这些闭环维持成可复跑基线。
- A full desktop-runtime import would add packaging, sandbox, security, update, and policy work all at once, while the phone-control and dedicated-device tracks are already giving us validated product value. / 一次性导入完整桌面运行时，会同时引入打包、沙箱、安全、更新和策略问题，而 phone-control 与 dedicated-device 这两条线已经在持续产出有验证的产品价值。

## Official Platform And Policy Constraints / 官方平台与策略约束

- Android security guidance explicitly discourages dynamic code loading when it can be avoided, and warns that many remote-source DCL patterns can violate Google Play policy. / Android 安全指南明确不鼓励在非必要情况下做动态代码加载，并警告很多来自远端来源的 DCL 模式会直接触碰 Google Play 策略。Source / 来源: <https://developer.android.com/privacy-and-security/risks/dynamic-code-loading>, <https://developer.android.com/guide/practices/security>
- Android 14 tightened this further: dynamically loaded files must be marked read-only, which makes ad-hoc self-managed runtime updates even less attractive. / Android 14 还进一步收紧：动态加载文件必须标记为只读，这让 app 自己随意管理运行时代码更新变得更不划算。Source / 来源: <https://developer.android.com/about/versions/14/behavior-changes-14>
- Google Play policy says a Play-distributed app may not self-update outside Play and may not download executable code such as `dex`、`JAR`、`.so` from outside Google Play. / Google Play 策略明确写了：通过 Play 分发的 app 不能绕过 Play 自更新，也不能从 Google Play 之外下载 `dex`、`JAR`、`.so` 这类可执行代码。Source / 来源: <https://support.google.com/googleplay/android-developer/answer/16273414?hl=en>
- Google Play does provide an official modular delivery lane through Play Feature Delivery, including on-demand dynamic feature modules. / Google Play 也提供了官方的模块化下发路径，也就是 Play Feature Delivery 与按需 dynamic feature modules。Source / 来源: <https://developer.android.com/guide/playcore/feature-delivery>, <https://developer.android.com/guide/playcore/feature-delivery/on-demand>
- Dedicated-device lock-task flows are official, but they live behind `DevicePolicyManager` and device-owner / profile-owner style management instead of normal-app privilege escalation. / dedicated-device 的 lock-task 流程是官方支持的，但它走的是 `DevicePolicyManager` 和 device-owner / profile-owner 这类受管设备能力，而不是普通 app 的权限升级。Source / 来源: <https://developer.android.com/work/dpc/dedicated-devices/>, <https://developer.android.com/reference/android/app/admin/DevicePolicyManager>
- Privileged permission lanes require system-image control plus allowlists under `/etc/permissions`; a normal sideloaded app cannot simply "become" a `priv-app`. / privileged permission 这条线需要系统镜像控制和 `/etc/permissions` 下的 allowlist；一个普通 sideloaded app 不能靠自身“变成” `priv-app`。Source / 来源: <https://source.android.com/docs/core/permissions>, <https://source.android.com/docs/core/permissions/perms-allowlist?hl=en>

## What Existing Projects Prove / 现有项目证明了什么

- `Termux` proves that Android can host a meaningful app-private command environment with packaged bootstrap assets and a large user-space package surface. / `Termux` 证明了 Android 可以承载一个有意义的 app-private 命令环境，并通过打包 bootstrap 资产提供较大的 user-space package 能力面。Source / 来源: <https://github.com/termux/termux-app>, <https://github.com/termux/termux-packages/wiki/Termux-execution-environment>
- `UserLAnd` proves that a no-root Android app can present full Linux distributions or apps through app-managed assets. / `UserLAnd` 证明了一个无 root 的 Android app 也能通过自管资产把完整 Linux 发行版或应用形态带进来。Source / 来源: <https://github.com/CypherpunkArmory/UserLAnd>
- `Andronix` proves that `PRoot`-style rootless Linux userspace on Android is a practical path for advanced users. / `Andronix` 证明了基于 `PRoot` 的 Android rootless Linux userspace 对高级用户来说是现实可用的路径。Source / 来源: <https://github.com/AndronixApp/AndronixOrigin>
- Inference: packaging or attaching a desktop-like environment to an Android app is technically possible, but these projects do not automatically solve Play-policy scope, app review, or OpenClaw's own safety boundaries. / 推断：把类桌面环境打包进 Android app 在技术上可行，但这些项目并不会自动替我们解决 Play 策略范围、应用审核或 OpenClaw 自己的安全边界。

## What Papers Help With And What They Do Not / 论文能帮什么，不能帮什么

- `ANDROIDWORLD` is valuable because it gives a realistic, reproducible Android-agent benchmark. / `ANDROIDWORLD` 的价值在于它提供了现实而可复现的 Android agent benchmark。Source / 来源: <https://arxiv.org/pdf/2405.14573>
- `AppAgent` and related mobile-agent work improve planning, action selection, and long-horizon task execution on mobile UIs. / `AppAgent` 及相关 mobile-agent 工作，提升的是移动端 UI 上的规划、动作选择和长链任务执行质量。Source / 来源: <https://arxiv.org/abs/2312.13771>
- These papers do not answer Android packaging, background execution, or Google Play compliance by themselves. / 这些论文本身并不解决 Android 打包、后台执行或 Google Play 合规问题。
- So this initiative is mostly a systems, packaging, and product-scope problem, not a model-paper problem. / 所以这条立项本质上主要是系统、打包和产品范围问题，而不是模型论文问题。

## Feasible Lanes / 可行线路

### Lane 1. Public Google Play / 公开 Google Play

- Conditional yes, but only if executable code ships in the signed app bundle or in Play-delivered feature modules. / 条件性可行，但前提是可执行代码随签名 app bundle 或 Play 下发的 feature module 一起交付。
- No OpenClaw-managed out-of-store `dex`、`JAR`、`.so` updates. / 不能由 OpenClaw 自己在 Play 之外更新 `dex`、`JAR`、`.so`。
- This lane is therefore suitable only for a tightly curated runtime slice, not a self-mutating desktop environment. / 因此，这条线只适合一个高度裁剪的 runtime slice，不适合会自演化、自更新的“桌面环境”。

### Lane 2. Internal Testing, Sideload, Or Enterprise Distribution / 内测、Sideload 或企业分发

- Yes, and this is the most practical first research lane. / 可行，而且这是最现实的第一条研究线。
- We can package native binaries or runtime assets at build time, extract them into app-private storage, and update them by shipping a new app build. / 我们可以在构建期打包 native binaries 或运行时资产，把它们解到 app-private storage，再通过发新版 app 来整体更新。
- This lane avoids the hardest Play-distribution restrictions while still keeping the deployment story close to real phones. / 这条线避开了最硬的 Play 分发限制，同时又让部署形态仍然贴近真实手机。

### Lane 3. Dedicated Device With Device Owner / 带 Device Owner 的专机线

- Yes, and it pairs well with the current dedicated-device roadmap. / 可行，而且和当前 dedicated-device 路线天然兼容。
- `Device Owner` and lock-task do not give us a desktop runtime for free, but they do give us a much stronger operational envelope for a long-lived phone host. / `Device Owner` 和 lock-task 不会免费送来桌面运行时，但它们确实能给“长期在线的手机 host”提供更强的运行边界。
- If runtime packaging continues, this lane is operationally stronger than trying to treat a normal consumer phone as a general-purpose always-on desktop host. / 如果继续推进 runtime packaging，这条线的运行时条件会明显强于把普通消费级手机硬当成通用常驻桌面 host。

### Lane 4. Rootless Linux Sidecar / Rootless Linux Sidecar

- Technically yes, as shown by `UserLAnd` and `Andronix`. / 技术上可行，`UserLAnd` 和 `Andronix` 已经证明了这一点。
- This is the closest path to a "desktop-like" environment, but it adds startup time, storage size, lifecycle complexity, and a much larger maintenance surface. / 这条线最接近“类桌面环境”，但它也会带来启动时延、存储体积、生命周期复杂度和更大的维护面。
- Treat this as an optional research branch, not the default next milestone. / 这更适合作为可选研究支线，而不是默认下一里程碑。

### Lane 5. `priv-app` Or Custom ROM / `priv-app` 或定制 ROM

- Yes, but only when we control the system image or OEM integration. / 可行，但前提是我们控制系统镜像或 OEM 集成。
- This is not an app-level packaging task anymore; it becomes a device-platform project. / 到这里它已经不再是 app 级打包任务，而是设备平台项目。
- Keep it as a far-future lane only if device-owner mode later proves insufficient. / 只有在 device-owner 模式后面真的不够时，才把它保留成远期线路。

## Recommended Project Shape / 推荐立项形态

Project name / 项目名: `Android Embedded Runtime Pod` / `Android 嵌入式运行时 Pod`

- Goal: bring a selected desktop-runtime slice onto the phone without importing the entire desktop `shell/browser/plugin` surface. / 目标：把一小段精选桌面运行时能力带到手机上，而不是整套导入桌面 `shell/browser/plugin` 能力面。
- Recommended first lane: internal or sideloaded `Embedded Runtime Pod`, updated only through app releases. / 推荐第一条线：internal 或 sideload 形态的 `Embedded Runtime Pod`，只通过 app 发版更新。
- Keep the Android app as the primary host and control plane. The embedded pod should remain a bounded capability provider, not a second product hidden inside the APK. / 继续让 Android app 本身作为主 host 和控制平面；嵌入式 pod 只做有边界的能力提供者，而不是藏在 APK 里的第二套产品。

## Non Goals For The First Spike / 第一轮 spike 的非目标

- No full desktop parity. / 不追求完整桌面对齐。
- No unrestricted remote shell. / 不提供无限制远程 shell。
- No Play-first self-updating executable runtime. / 不做 Play-first 的自更新可执行运行时。
- No assumption that a normal app can get privileged or system-server behavior. / 不假设普通 app 能拿到 privileged 或 system-server 行为。

## Immediate Proposal / 立项后的立即动作

1. Inventory which missing desktop capabilities are actually worth bringing to Android first. / 先盘点哪些“桌面侧缺失能力”真的值得先带到 Android。
2. Pick one narrow first slice and keep it smaller than `full shell/browser/plugin runtime`. / 选一条足够窄的第一能力切片，范围要小于 `full shell/browser/plugin runtime`。
3. Build a spike that packages the runtime assets at build time, extracts them into app-private storage, and proves one bounded command or workflow. / 做一个 spike：构建期打包运行时资产，解到 app-private storage，并验证一条有边界的命令或工作流。
4. Keep the packaging lane and the distribution lane separate in docs and code, so Play constraints never get mixed up with sideload-only capability. / 在文档和代码里把“打包线路”和“分发线路”分开，避免把 Play 约束和 sideload-only 能力混在一起。

## Do Not Reopen / 暂时不要重开

- Do not turn this into "copy the entire desktop runtime first." / 不要把这件事重新转成“先把整套桌面 runtime 搬过来”。
- Do not let this block the current `pnpm android:local-host:ui` and dedicated-device baseline work. / 不要让它阻塞当前 `pnpm android:local-host:ui` 和 dedicated-device 基线工作。
- Do not assume Google Play distribution and sideload distribution have the same policy envelope. / 不要假设 Google Play 分发和 sideload 分发处在同一策略边界里。
- Do not assume mobile-agent papers solve packaging or background-lifecycle problems. / 不要假设 mobile-agent 论文能替代打包和后台生命周期问题。

## Current Recommendation / 当前建议

- Start this as a parallel research-and-spike track, not as a replacement for the phone-control and dedicated-device roadmap. / 把它作为并行的 research-and-spike 支线启动，而不是替代 phone-control 和 dedicated-device 主路线。
- The first concrete deliverable should be a scoped `Embedded Runtime Pod` design note plus one build-time-packaged spike, not a promise of full desktop parity. / 第一份具体产出应该是一个范围明确的 `Embedded Runtime Pod` 设计说明和一个构建期打包的 spike，而不是承诺完整桌面对齐。
