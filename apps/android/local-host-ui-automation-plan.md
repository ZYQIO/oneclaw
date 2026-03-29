# Android Local Host UI Automation Plan / Android 本机 Host UI 自动化规划

Purpose / 用途: capture today's status, findings, and next-session execution plan for making Android `Local Host` capable of operating the phone itself without a nearby computer. / 记录今天的状态、结论和下一会话执行计划，目标是让 Android `Local Host` 在没有电脑陪同的情况下也能自己操作手机。

Last updated / 最后更新: March 29, 2026 / 2026 年 3 月 29 日

## Session Goal / 本次会话目标

Move Android `Local Host` from "GPT can chat plus use curated device tools" toward "the phone can observe and operate its own UI like a lightweight autonomous host." / 把 Android `Local Host` 从“GPT 能聊天并调用精选设备工具”推进到“手机可以像轻量自治 Host 一样观察并操作自己的 UI”。

## Status Update / 状态更新

- `ui.state`、`ui.waitForText`、`ui.launchApp`、`ui.inputText`、`ui.tap`、`ui.back`、`ui.home`、`ui.swipe` 都已经落地；其中 `ui.launchApp`、`ui.inputText`、`ui.tap`、`ui.back`、`ui.home`、`ui.swipe` 继续挂在远端 write tier 后面，默认关闭。`ui.state`, `ui.waitForText`, `ui.launchApp`, `ui.inputText`, `ui.tap`, `ui.back`, `ui.home`, and `ui.swipe` are all now landed; `ui.launchApp`, `ui.inputText`, `ui.tap`, `ui.back`, `ui.home`, and `ui.swipe` remain behind the remote write tier and stay off by default.
- 2026 年 3 月 26 日已在真实 OPPO / ColorOS 手机上验证第一条 bounded control loop：`ui.tap(text=\"Chat\")` 成功切到 Chat tab，而 `ui.home` 与 `ui.back` 都能把活动窗口切回 `com.android.launcher`。On March 26, 2026, the first bounded control loop was validated on a real OPPO / ColorOS phone: `ui.tap(text=\"Chat\")` successfully switches to the Chat tab, and `ui.home` plus `ui.back` both return the active window to `com.android.launcher`.
- 2026 年 3 月 27 日又补上了 `ui.launchApp`：它采用 `packageName`-first 接口，通过标准 Android launch intent 拉起 app，并为 “未安装” / “已安装但不可启动” 返回清晰错误；相关 Kotlin 编译和定向单测已通过，而且 `ui.launchApp(packageName=\"com.android.settings\")` 已在真机成功把前台 activity 切到 `com.android.settings/.Settings`。On March 27, 2026, `ui.launchApp` was added as well: it uses a `packageName`-first contract, launches apps through standard Android launch intents, and returns clear errors for "not installed" versus "installed but not launchable"; the related Kotlin compile plus targeted unit tests now pass, and `ui.launchApp(packageName=\"com.android.settings\")` has already switched the foreground activity to `com.android.settings/.Settings` on-device.
- 同一天也补上了 `ui.inputText`：它采用 focused-editable / selector-editable 的第一版边界，并通过 accessibility `ACTION_SET_TEXT` 写入文本；相关 Kotlin 编译和定向单测也已通过，而且在 Connect 页端口输入框上，`ui.inputText(value=\"3945\", text=\"3945\", matchMode=\"exact\", packageName=\"ai.openclaw.app\")` 已在真机成功返回 `performed=true`。The same day also added `ui.inputText`: its first bounded form uses focused-editable / selector-editable targeting and writes text through accessibility `ACTION_SET_TEXT`; the related Kotlin compile plus targeted unit tests also pass, and on the Connect-screen port field `ui.inputText(value=\"3945\", text=\"3945\", matchMode=\"exact\", packageName=\"ai.openclaw.app\")` has already returned `performed=true` on-device.
- 同一轮验证和代码接线也确认了门控策略：write tier 关闭时远端只有 `ui.state` / `ui.waitForText`，开启 write 后才会放出 `ui.launchApp` / `ui.inputText` / `ui.tap` / `ui.back` / `ui.home`。The same validation and code wiring also confirm the gating model: with the write tier off, remote access only exposes `ui.state` / `ui.waitForText`, and only after enabling write do `ui.launchApp` / `ui.inputText` / `ui.tap` / `ui.back` / `ui.home` appear.
- 2026 年 3 月 29 日又补上了 `ui.swipe`：第一版采用坐标驱动接口 `startX/startY/endX/endY`，可选 `durationMs` 和 `packageName` 守卫，经无障碍手势注入执行；对应 Kotlin 编译和定向单测都已通过，但还没有新的真机 swipe 正证据。On March 29, 2026, `ui.swipe` was added as well: the first version uses a coordinate-driven `startX/startY/endX/endY` contract with optional `durationMs` and `packageName` guarding, executing through accessibility gesture injection; the related Kotlin compile plus targeted unit tests pass, but it does not yet have new on-device swipe proof.
- 2026 年 3 月 28 日，新加的 `pnpm android:local-host:ui:cross-app` 已在同一台 OPPO / ColorOS 手机上给出更扎实的短窗口证据：针对 `com.android.settings` 的 5 秒观察中，目标 app 确实在第 2 轮开始成为真前台，`targetTopCount=9`，同时 10 次远端 `/status` 探针全部成功，最终分类为 `foregrounded_host_reachable`。On March 28, 2026, the new `pnpm android:local-host:ui:cross-app` probe produced a stronger short-window result on the same OPPO / ColorOS phone: during a 5-second observation against `com.android.settings`, the target app truly became the foreground app starting in round 2, `targetTopCount=9`, and all 10 remote `/status` probes still succeeded, yielding `classification=foregrounded_host_reachable`.
- 同一天又补上了 `pnpm android:local-host:ui:cross-app:sweep`，默认 `5000,15000,30000` 三档都保持 `foregrounded_host_reachable`，顶层结果是 `allWindowsReachable=true`、`firstNonReachableWindowMs=null`。这说明当前设置下 30 秒内还没有复现后台冻结边界。Later the same day we also added `pnpm android:local-host:ui:cross-app:sweep`, and its default `5000,15000,30000` windows all stayed `foregrounded_host_reachable`, with top-level `allWindowsReachable=true` and `firstNonReachableWindowMs=null`. That means the background-freeze boundary was not reproduced within 30 seconds on the current setup.
- 这也意味着当前真正未定的边界已经从“短到中等窗口里 Host 会不会掉线”收敛成“在 launched app 仍在前台时，我们能稳定叠加什么 follow-up action”。That also means the remaining uncertainty has shifted from "will the host drop in short-to-medium windows" to "what follow-up action can we reliably layer on while the launched app is still on top."
- 当前 OPPO / ColorOS 真机在重新安装 APK 后会把 OpenClaw accessibility grant 清空；如果 `ui.state` 突然回到 disabled，需要先重新开启无障碍服务。On the current OPPO / ColorOS phone, reinstalling the APK clears the OpenClaw accessibility grant; if `ui.state` suddenly drops back to disabled, the accessibility service needs to be re-enabled first.

## What Happened Today / 今天做了什么

### Delivery / 交付

- The previously pushed Android local-host batch on this line is commit `75631a07c5` with message `Android: expand and harden local host deployment`. / 这一条开发线此前已推送的 Android local-host 批次是提交 `75631a07c5`，提交信息为 `Android: expand and harden local host deployment`。
- That pushed batch already includes local-host tool-calling, app-private workspace tooling, dedicated idle-phone deployment, recovery/watchdog behavior, and OEM background-policy readiness surfacing. / 那一批已推送内容已经包含：本机 Host 工具调用、app-private workspace、闲置手机 dedicated 部署、恢复 / watchdog 机制，以及 OEM 后台策略 readiness 提示。
- The first UI-automation scaffolding slice is now in the Android app: accessibility-service manifest wiring, service XML, Connect-tab readiness, and `/status` reporting for `uiAutomationAvailable`. / Android app 里已经落下第一段 UI 自动化骨架：无障碍服务 manifest 接线、service XML、Connect 页 readiness，以及 `/status` 的 `uiAutomationAvailable` 上报。
- A first read-only `ui.state` command now exists too: before accessibility is enabled it returns structured readiness reasons, and once the service is live it returns `packageName`, `visibleText`, `nodeCount`, and a flattened node list for the active window. / 第一条只读 `ui.state` 命令也已经接上：无障碍未开启时会返回结构化 readiness 原因，服务连上后则会返回当前窗口的 `packageName`、`visibleText`、`nodeCount` 和扁平节点列表。
- A first bounded gesture path now exists too as `ui.swipe`: it is currently coordinate-first, wired through the same write-tier gate, exposed in `/examples` and nodes tooling, and backed by fresh Kotlin compile plus targeted unit-test coverage. / 第一条有边界的手势路径 `ui.swipe` 也已经接上：当前是 coordinate-first 版本，走同一套 write-tier 门控，并已同步接进 `/examples` 与 nodes tooling，且有最新 Kotlin 编译和定向单测覆盖。
- The Codex browser-auth return-to-app path was also retried with a custom deep link plus browser CTA, but on the current OPPO / ColorOS device it still does not return reliably; that issue is now parked so it does not block UI automation. / 这轮还重试了 Codex 浏览器授权回 App 路径，加入了自定义 deep link 和浏览器 CTA，但在当前 OPPO / ColorOS 设备上仍不能稳定回跳；这个问题现在已挂起，不再阻塞 UI 自动化推进。

### Investigation / 调研

- Verified in code why the app cannot yet "control the phone like a human." / 已在代码中核实为什么当前 app 还不能“像人一样控制手机”。
- Researched Android-native UI automation routes plus existing GitHub projects that already solve similar problems. / 已调研 Android 原生 UI 自动化路径，以及 GitHub 上已解决类似问题的项目。
- Reviewed Z.ai's recent GitHub open-source work to separate model-side references from Android-runtime references. / 已补看 Z.ai 最近在 GitHub 开源的项目，区分哪些更适合当模型参考、哪些更适合当 Android runtime 参考。
- Reduced the architecture choice to a concrete direction instead of continuing vague scope expansion. / 已把架构选择收敛到明确方向，而不是继续模糊扩范围。
- The latest background-control research now points to a hybrid runtime answer rather than a model-only answer: use official Android keepalive/recovery tools where possible, prefer a reverse outbound transport over a background LAN listener, and keep an external-controller fallback in mind for hostile OEMs. / 最新一轮后台操控调研已经把答案收敛成“混合 runtime”而不是“模型层自己解决”：能用 Android 官方保活 / 恢复工具的地方先用，控制面优先考虑 reverse outbound transport 而不是长期依赖后台 LAN 监听，同时为 hostile OEM 预留外部主控 fallback。
- The new dedicated-phone research also clarifies the system-integration ladder: for a spare phone, device-owner / lock-task mode should be evaluated before Magisk systemization, custom-ROM `priv-app` preload, or a true platform `system_server` service. / 新一轮闲置手机调研也把系统集成梯度说明白了：对闲置手机来说，应该先评估 device-owner / lock-task mode，再考虑 Magisk systemize、自定义 ROM 的 `priv-app` 预装，最后才是真正的平台 `system_server` 服务。

## Current Reality / 当前现实

Today the Android app can already do these things well. / 当前 Android app 已经比较稳定地具备以下能力：

- Run `Local Host` directly on the phone. / 在手机上直接运行 `Local Host`。
- Authenticate with Codex and use GPT for local chat. / 使用 Codex 授权并通过 GPT 完成本地聊天。
- Execute a curated set of Android-native actions through the local `nodes` tool. / 通过本机 `nodes` tool 执行一组精选 Android 原生命令。
- Maintain a dedicated idle-phone deployment with keepalive, restore, and readiness signals. / 以 dedicated idle-phone 形态运行，并具备保活、恢复和 readiness 信号。
- Use an app-private local `workspace` for file-like operations. / 使用 app-private 本地 `workspace` 做文件式操作。
- Observe the current active window with `ui.state`, wait on simple text conditions with `ui.waitForText`, launch another app with the new package-first `ui.launchApp`, write text into editable fields with `ui.inputText`, perform bounded `ui.tap` / `ui.back` / `ui.home` actions, and issue a first coordinate-based `ui.swipe` once accessibility plus the write tier are enabled. / 在开启无障碍服务和 write tier 后，用 `ui.state` 观察当前窗口、用 `ui.waitForText` 等待简单文本条件、用新的 package-first `ui.launchApp` 拉起其他 app、用 `ui.inputText` 向 editable 字段写入文本、执行有边界的 `ui.tap` / `ui.back` / `ui.home` 动作，并发出第一版坐标驱动的 `ui.swipe`。

What it still cannot do yet. / 但它现在仍然做不到：

- Read a deeper selector-friendly UI tree of other apps. / 读取更适合 selector 的、层次更完整的其他 app UI 树。
- Turn text entry, scrolling, and other richer multi-step UI flows after app launch into repeatable real-device proofs without the current OEM background freeze interrupting the host. / 把拉起 app 之后的文本输入、滚动和其他更丰富的多步 UI 流程，收敛成可重复的真机证据，而且不会被当前 OEM 后台冻结打断。
- Turn the current manual proof into a durable regression harness. / 把当前仍偏手工的验证过程变成稳定的回归验证面。
- Behave like the desktop OpenClaw host when a task requires real cross-app navigation. / 在需要真实跨 app 导航时，像桌面 OpenClaw host 那样行动。

## Why It Cannot Control The Phone Yet / 为什么现在还不能控制手机

This is not mainly an auth problem. / 这主要不是授权问题。

It is a missing runtime surface. / 而是缺了一层运行时能力面。

- `apps/android/app/src/main/AndroidManifest.xml` now declares an accessibility service, but the runtime surface is still only partially built. / `apps/android/app/src/main/AndroidManifest.xml` 现在已经声明了无障碍服务，但这层运行时能力面仍只完成了一部分。
- `apps/android/app/src/main/java/ai/openclaw/app/node/InvokeCommandRegistry.kt` and `apps/android/app/src/main/java/ai/openclaw/app/node/InvokeDispatcher.kt` now expose `ui.state`, `ui.waitForText`, `ui.launchApp`, `ui.inputText`, `ui.tap`, `ui.back`, `ui.home`, and `ui.swipe`, but the selector model is still lightweight and there is still no launchable-app listing path. / `apps/android/app/src/main/java/ai/openclaw/app/node/InvokeCommandRegistry.kt` 和 `apps/android/app/src/main/java/ai/openclaw/app/node/InvokeDispatcher.kt` 现在已经接入 `ui.state`、`ui.waitForText`、`ui.launchApp`、`ui.inputText`、`ui.tap`、`ui.back`、`ui.home`、`ui.swipe`，但 selector 模型仍偏轻量，而且还没有可启动 app 列表这类路径。
- The current `ui.state` output is intentionally lightweight: it is good enough for readiness, simple waits, and the first bounded actions, but it is not yet a selector-friendly control surface. / 当前 `ui.state` 输出是有意保持轻量的：它已经足够做 readiness、简单等待和第一批 bounded actions，但还不足以成为 selector-friendly 的操控面。
- The model can only act within the runtime surface that exists, so until we add app launch, text entry, richer selectors, and a repeatable validation harness, the phone still cannot complete broader cross-app tasks reliably. / 模型只能在 runtime 已暴露的能力面内行动，所以在补上 app launch、文本输入、更丰富的 selector 和可重复验证面之前，手机仍然不能稳定完成更广的跨 app 任务。

## Research Findings / 调研结论

### What Android Allows / Android 允许什么

- The most realistic in-app path is `AccessibilityService`. It can inspect UI structure, trigger node actions, and dispatch gestures when the user explicitly enables the service. / 最现实的 app 内路径是 `AccessibilityService`。在用户显式开启服务后，它可以读取 UI 结构、触发节点动作，并分发手势。
- Visual-only control is possible with screenshots or `MediaProjection`, but Android treats it as a foreground, user-consented flow and it is harder to keep alive as an always-on host. / 纯视觉控制也可以做，比如截图或 `MediaProjection`，但 Android 会把它视为需要前台且需用户同意的流程，不适合直接做成长驻 host 的第一实现。
- Test frameworks such as UI Automator and Appium are powerful, but they are usually driven by a computer or ADB and are a weaker fit for an idle-phone autonomous deployment. / UI Automator、Appium 这类测试框架虽然强，但通常由电脑或 ADB 驱动，对“闲置手机自治部署”并不是最优匹配。

### What Distribution Allows / 发布边界允许什么

- As of March 25, 2026, Google Play's current Accessibility policy is a real product boundary, not a minor paperwork detail. Non-accessibility-tool uses require declaration, clear in-app disclosure, and affirmative consent, and the policy explicitly says the API cannot be used for an app that autonomously initiates, plans, and executes actions or decisions. / 截至 2026 年 3 月 25 日，Google Play 当前的 Accessibility 策略是真实的产品边界，不是小型申报手续。非无障碍工具用途需要声明、清晰的 app 内披露和明确同意，而且策略明确写了不能把这套 API 用在“自主发起、规划并执行动作或决策”的 app 上。
- Therefore we should treat sideload / internal / debug deployment and Play-store distribution as separate lanes from day one. / 所以从第一天起就应把 sideload / 内部 / debug 部署与 Play 上架视为两条不同产品线。

### What GitHub Already Shows / GitHub 上已经验证过什么

- `droidrun-portal` is structurally the closest reference: Android-side portal, accessibility service, UI tree, action APIs, and local control surface. / `droidrun-portal` 在结构上最值得参考：Android 端 portal、无障碍服务、UI 树、动作 API、本地控制面基本齐了。
- `Auto.js`-style projects prove that accessibility plus scripting can drive real phones without a nearby computer. / `Auto.js` 风格项目已经证明，无障碍加脚本这条路能在没有电脑陪同的情况下驱动真实手机。
- `Maestro`, `Appium UiAutomator2`, and `DroidBot` are good references for action models and testing patterns, but not the exact deployment architecture we want. / `Maestro`、`Appium UiAutomator2`、`DroidBot` 适合借鉴动作模型和测试方法，但不是我们想要的最终部署架构。
- `scrcpy` is useful as a remote-control reference, but it is host-computer centric rather than phone-autonomous. / `scrcpy` 适合参考远控能力，但它是电脑主控，不是手机自治。
- Z.ai's `Open-AutoGLM` is the most relevant recent GitHub repo for our direction. It is explicitly positioned as "An Open Phone Agent Model & Framework," but its README is centered on Python plus model endpoints plus ADB / remote ADB control, so it is a strong reference for action loops and evaluation, not a direct drop-in architecture for "the phone itself is the host." / Z.ai 的 `Open-AutoGLM` 是最近最贴近我们方向的 GitHub 项目。它明确把自己定位为 “An Open Phone Agent Model & Framework”，但 README 的主轴仍是 Python + 模型端点 + ADB / remote ADB 控制，所以它更适合作为动作闭环和评测方式的参考，而不是直接照搬成“手机自己就是 host”的架构。
- `CogAgent` is a model-side GUI-agent reference rather than an Android runtime. Its README explicitly supports `Mobile` on Android 13, 14, and 15, which makes it useful for screenshot-to-action grounding research and future selector ranking. / `CogAgent` 更像模型侧 GUI agent 参考，而不是 Android runtime。它的 README 明确支持 Android 13、14、15 的 `Mobile` 平台，因此适合作为截图到动作的 grounding 研究，以及后续 selector 排序的参考。
- `UI2Code_N` is about turning a UI screenshot plus prompt into front-end code. It is interesting for screenshot structuring and evaluator ideas, but it is not a direct phone-control stack. / `UI2Code_N` 的方向是把 UI 截图和指令变成前端代码。它对截图结构化和评估器思路有参考价值，但不是直接的手机操控栈。
- Looking at Z.ai's public org page on March 25, 2026, the most recently updated repositories are `GLM-5`, `GLM-V`, `GLM-OCR`, `GLM-Image`, and `Open-AutoGLM`; for OpenClaw's Android phone-control path, `Open-AutoGLM` is the one worth mining first. / 按 2026 年 3 月 25 日的 Z.ai 公开组织页来看，最近更新最活跃的是 `GLM-5`、`GLM-V`、`GLM-OCR`、`GLM-Image` 和 `Open-AutoGLM`；对 OpenClaw 的 Android 手机操控路线来说，最值得先深挖的还是 `Open-AutoGLM`。

### Fresh GitHub And Paper Scan On March 26, 2026 / 2026 年 3 月 26 日的新一轮 GitHub 与论文扫描

- [`droidrun/droidrun-portal`](https://github.com/droidrun/droidrun-portal) remains the best runtime-side reference because it combines an Android accessibility service, JSON state export, launchable-app discovery, text input, local sockets, and reverse connection. For OpenClaw this points to an “Accessibility Portal” shape inside the app, not to a desktop-first controller. / [`droidrun/droidrun-portal`](https://github.com/droidrun/droidrun-portal) 仍然是最强的 runtime 侧参考，因为它把 Android 无障碍服务、JSON 状态导出、可启动 app 发现、文本输入、本地 socket 和反向连接整合在了一起。对 OpenClaw 来说，这更像是在 app 内做一层 “Accessibility Portal”，而不是做桌面主控器。
- [`zai-org/Open-AutoGLM`](https://github.com/zai-org/Open-AutoGLM) is still the clearest example of the external-controller lane: Python orchestration, model endpoints, ADB/HDC device control, remote debugging, and ADB-keyboard-style text entry. It is valuable for control loops and fallback ideas, but not as our primary runtime architecture. / [`zai-org/Open-AutoGLM`](https://github.com/zai-org/Open-AutoGLM) 仍然是外部主控路线最清晰的例子：Python 编排、模型端点、ADB/HDC 设备控制、远程调试，以及类似 ADB Keyboard 的文本输入。它对动作闭环和兜底思路有价值，但不该成为我们的主运行时架构。
- [`bytedance/UI-TARS`](https://github.com/bytedance/UI-TARS) is highly relevant for the action surface itself. Its mobile prompt template already includes `open_app`, `press_home`, and `press_back`, which is a useful signal that OpenClaw's next bounded primitives are on the right path. / [`bytedance/UI-TARS`](https://github.com/bytedance/UI-TARS) 对动作面本身很有参考价值。它的移动端 prompt 模板已经显式包含 `open_app`、`press_home`、`press_back`，这说明 OpenClaw 当前把下一批 bounded primitive 放在这些方向上是合理的。
- [`X-PLUG/MobileAgent`](https://github.com/X-PLUG/MobileAgent) has now evolved into a broader family where `Mobile-Agent-v3` adds planning, progress management, reflection, and memory on top of a GUI model. That is a planner-layer reference for later, not a reason to delay the first Android runtime primitives. / [`X-PLUG/MobileAgent`](https://github.com/X-PLUG/MobileAgent) 现在已经演进成更完整的家族，`Mobile-Agent-v3` 在 GUI 模型之上进一步加入了 planning、progress management、reflection 和 memory。这更适合作为后续 planner 层参考，而不是拖慢第一批 Android runtime primitive 的理由。
- [`OpenBMB/AgentCPM-GUI`](https://github.com/OpenBMB/AgentCPM-GUI) is the strongest on-device Android model-side reference in this scan: it is explicitly positioned as an on-device Android GUI agent, highlights compact JSON actions, and focuses on Chinese plus English app operation. That makes it especially relevant for future action-schema compression and later Chinese-app grounding quality. / [`OpenBMB/AgentCPM-GUI`](https://github.com/OpenBMB/AgentCPM-GUI) 是这轮扫描里最强的端侧 Android 模型参考：它明确定位为 on-device Android GUI agent，强调紧凑 JSON 动作，并且聚焦中英文 app 操作。这对后续动作 schema 压缩和中文 app grounding 质量尤其有参考价值。
- [`showlab/ShowUI`](https://github.com/showlab/ShowUI) is best treated as a lightweight screenshot-grounding or visual fallback reference. It is not a replacement for accessibility metadata, but it becomes interesting if selector quality stays weak in some apps. / [`showlab/ShowUI`](https://github.com/showlab/ShowUI) 最适合被当成轻量截图 grounding 或视觉兜底参考。它不是无障碍元数据的替代品，但如果某些 app 的 selector 质量持续偏弱，它会变得很有价值。
- [`google-research/android_world`](https://github.com/google-research/android_world), [`AppAgent`](https://arxiv.org/abs/2312.13771), [`Mobile-Agent-E`](https://arxiv.org/abs/2501.11733), and [`GUI-CEval`](https://arxiv.org/abs/2603.15039) together suggest how to validate the next phase: benchmark tasks, replayable action traces, reflection loops, and Chinese-ecosystem evaluation matter once the base runtime exists. / [`google-research/android_world`](https://github.com/google-research/android_world)、[`AppAgent`](https://arxiv.org/abs/2312.13771)、[`Mobile-Agent-E`](https://arxiv.org/abs/2501.11733)、[`GUI-CEval`](https://arxiv.org/abs/2603.15039) 一起提示了下一阶段该怎么验证：等基础 runtime 出来之后，benchmark 任务、可重放 action trace、reflection 闭环，以及中文生态评测都会重要。

### Related Papers And Benchmarks / 相关论文与基准

- `AppAgent` (2023) is the clean proof that a screenshot-driven, simplified action space can already complete meaningful smartphone tasks without privileged backend integration. It is a good reference for action abstraction, but not the best fit if we want deterministic on-device control. / `AppAgent`（2023）证明了：只用截图观察和简化动作空间，不接系统后端，也能完成一批真实手机任务。它适合参考动作抽象，但如果我们要做确定性更强的本机控制，它不是最优主线。
- `Mobile-Agent` (2024) is the stronger vision-centric baseline. It is useful for learning how to combine screenshot perception, OCR-like grounding, and multi-step planning when accessibility metadata is weak or missing. / `Mobile-Agent`（2024）是更强的视觉主线基线。它适合帮助我们理解：当无障碍元数据不完整时，怎样把截图感知、类似 OCR 的 grounding 和多步规划接起来。
- `Mobile-Agent-E` (2025) is especially relevant for long-horizon tasks because it separates manager, operator, reflector, and memory roles. That architecture is a good reference for future OpenClaw planning loops after the base runtime exists. / `Mobile-Agent-E`（2025）对长任务特别有参考价值，因为它把 manager、operator、reflector、memory 拆开了。等 OpenClaw 先有基础 runtime 后，这种分层很适合借鉴到后续规划闭环里。
- `Android in the Wild` (2023) is a large human-demonstration dataset for Android control. It is useful for understanding realistic action distributions and failure modes, and it is a strong source if we later want selector heuristics or offline evaluation data. / `Android in the Wild`（2023）是一个大规模 Android 控制人类演示数据集。它适合帮助我们理解真实动作分布和失败模式；如果后面要做 selector 启发式或离线评测，它是很强的数据来源。
- `AndroidWorld` (2024) is the most directly useful benchmark environment for reproducible regression testing. For OpenClaw it is less a runtime template and more a verification template: task setup, success checks, and durable rewards. / `AndroidWorld`（2024）是最适合借鉴的可复现实验基准环境。对 OpenClaw 来说，它更像验证模板，而不是运行时模板：任务初始化、成功判定和稳定 reward 设计都值得学。
- `GUI-CEval` (2026) is worth tracking if we care about the Chinese mobile ecosystem specifically. Its value is not just benchmark size, but that it evaluates perception, planning, reflection, execution, and evaluation on physical devices. / `GUI-CEval`（2026）如果我们关心中文移动生态，就值得持续跟踪。它的价值不只在规模，还在于它把 perception、planning、reflection、execution、evaluation 放到了真机环境里统一评测。

### How To Use These References / 这些参考该怎么用

- Use `droidrun-portal` and Android Accessibility docs as the immediate implementation reference for the first `ui.state` and bounded action APIs. / 第一版 `ui.state` 和有边界动作 API，优先参考 `droidrun-portal` 和 Android Accessibility 官方文档。
- Use `Open-AutoGLM`, `AppAgent`, and `Mobile-Agent` mostly for planner / operator loop design, prompt shape, and failure-recovery patterns, not for the Android embedding model. / `Open-AutoGLM`、`AppAgent`、`Mobile-Agent` 主要拿来参考 planner / operator 闭环、prompt 形态和失败恢复方式，不要直接照搬 Android 内嵌形态。
- Use `AndroidWorld` and `Arbigent`-style external harnesses later for regression and task evaluation, once the in-app runtime exists. / 等 app 内 runtime 先出来后，再把 `AndroidWorld` 和 `Arbigent` 这类外部 harness 拿来做回归和任务评测。

### What This Means For OpenClaw Next / 这对 OpenClaw 下一步意味着什么

- The runtime decision is now more stable, not less: keep building an in-app `AccessibilityService` portal, and do not switch the main architecture to ADB-driven control. / 运行时路线现在更加稳定，而不是更摇摆：继续构建 app 内 `AccessibilityService` portal，不要把主架构切到 ADB 驱动控制。
- `launch_app` has now landed in code as `ui.launchApp`, with a `packageName`-first v1 contract and standard Android launch intents. The next decision is not whether to add it, but whether it needs a follow-up read-only launchable-app listing after on-device validation. / `launch_app` 现在已经以 `ui.launchApp` 的形式落到代码里，第一版采用 `packageName`-first 接口和标准 Android launch intent。下一步要判断的已经不是“要不要做”，而是它在真机验证后是否需要继续补一个只读的可启动 app 列表。
- `ui.inputText` has now landed right after that, with focused-editable-node plus accessibility text-setting as the first path. Keyboard-style fallbacks should remain optional until a real app proves we need them. / `ui.inputText` 现在已经紧随其后落地，第一版就是焦点 editable 节点加无障碍文本设置。只有等真实 app 证明我们确实需要时，再补键盘式兜底。
- Screenshot-grounding augmentation from `ShowUI`, `UI-TARS`, or `AgentCPM-GUI` should be treated as phase two, after the base `launch_app` / `input_text` / `tap` / `back` / `home` loop is stable. / `ShowUI`、`UI-TARS`、`AgentCPM-GUI` 这类截图 grounding 增强应被视为第二阶段，放在基础 `launch_app` / `input_text` / `tap` / `back` / `home` 闭环稳定之后。
- Validation should keep moving toward task-based replay instead of one-off demos. `AndroidWorld` and `GUI-CEval` are the right mental model here. / 验证应该继续朝任务化重放收敛，而不是停留在一次性 demo。`AndroidWorld` 和 `GUI-CEval` 就是这里正确的心智模型。

## Decision / 当前判断

Do not try to copy the entire desktop runtime into Android first. / 不要先把整套桌面 runtime 硬搬进 Android。

Build an `Accessibility Portal` first. / 先做一层 `Accessibility Portal`。

This should become the missing bridge between the current Android-native tool surface and true cross-app phone operation. / 它应该成为“当前 Android 原生工具面”和“真实跨 app 操作手机”之间缺失的那一层桥。

Borrow ideas from `Open-AutoGLM` and `CogAgent`, but keep the primary runtime on-device and in-app. / 可以借鉴 `Open-AutoGLM` 和 `CogAgent` 的思路，但主运行时仍应留在设备内、App 内。

Also treat policy-sensitive autonomous control as a separate product question from the technical runtime milestone. / 同时要把“策略敏感的自治操控”当成一个独立于技术 runtime 里程碑之外的产品问题。

## Proposed Architecture / 建议架构

### Core Components / 核心组件

1. `AccessibilityService` runtime / 无障碍服务运行时
   - Observe active windows, node tree, package name, focus, and visible text. / 观察当前窗口、节点树、包名、焦点和可见文本。
   - Execute bounded actions: click, long-click, scroll, set text, global back/home/recents, gesture tap/swipe. / 执行有边界的动作：点击、长按、滚动、填文本、全局返回 / Home / Recents、手势点击 / 滑动。

2. UI snapshot model / UI 快照模型
   - Normalize accessibility nodes into a tool-friendly JSON tree. / 把无障碍节点标准化成 tool-friendly 的 JSON 树。
   - Include stable fields such as `text`, `contentDescription`, `className`, `bounds`, `clickable`, `enabled`, `checked`, `packageName`, and `resourceId` when available. / 在可用时包含 `text`、`contentDescription`、`className`、`bounds`、`clickable`、`enabled`、`checked`、`packageName`、`resourceId` 等稳定字段。

3. New local-host tool surface / 新的本机 Host 工具面
   - Add a new tool namespace such as `ui` or `android_ui`. / 新增一个 `ui` 或 `android_ui` 工具命名空间。
   - First actions should be `state`, `launch_app`, `tap`, `swipe`, `input_text`, `back`, `home`, `recents`, `open_notifications`, `wait_for_text`. / 第一批动作建议就是 `state`、`launch_app`、`tap`、`swipe`、`input_text`、`back`、`home`、`recents`、`open_notifications`、`wait_for_text`。

4. Safety and policy layer / 安全与策略层
   - Keep it off by default. / 默认关闭。
   - Require explicit on-device enablement plus UI disclosure. / 要求用户在手机上显式开启，并在 UI 中清楚告知。
   - Keep remote role gating separate from local interactive use. / 把远端角色权限和本地交互权限继续分层。

5. Validation harness / 验证工具
   - Add a deterministic smoke script for launching an app, reading a UI state, tapping a known element, and returning a final state. / 补一个确定性的冒烟脚本：拉起 app、读取 UI 状态、点一个已知元素、返回最终状态。

## Next Objective / 当前下一目标

Keep the first bounded control loop stable on a real phone, turn it into a repeatable smoke path, then expand the runtime from in-app navigation toward broader cross-app tasks. / 先让第一条 bounded control loop 在真机上保持稳定，并把它做成可重复的 smoke 路径，再把运行时从 app 内导航扩到更广的跨 app 任务。

The target is no longer "land the first wait plus action path." / 目标已经不再是“落第一条等待加动作链路”。

The target is to turn the current manual proof into a reusable validation surface, then validate the next cross-app follow-up steps with the primitives that already exist, especially `ui.launchApp`, `ui.inputText`, and the new coordinate-first `ui.swipe`. / 现在的目标是把当前偏手工的证明路径变成可复用验证面，然后用已经落地的 primitive 去补下一批跨 app follow-up 验证，重点是 `ui.launchApp`、`ui.inputText` 和新的 coordinate-first `ui.swipe`。

## Next Execution Plan / 当前执行计划

### P0. Keep the real-device baseline healthy / 先保持真机基线健康

- Re-run `/status` plus `ui.state` at the start of each session. / 每次新会话开局都先重跑 `/status` 和 `ui.state`。
- If the APK was just reinstalled, check whether the accessibility grant was cleared and restore it before continuing. / 如果刚重新安装过 APK，先检查 accessibility grant 是否被系统清空，并在继续之前恢复它。
- Keep recording the active-window snapshot or disabled reason so the next iteration starts from evidence instead of source inspection only. / 继续记录当前活动窗口快照或 disabled 原因，确保下一轮从证据开工，而不是只靠源码分析。

Exit criteria / 退出标准:

- App still reports `uiAutomationAvailable=true/false` clearly on the real phone. / App 仍能在真机上清楚报告 `uiAutomationAvailable=true/false`。
- `ui.state` still gives either a structured disabled reason or a real active-window snapshot. / `ui.state` 仍能给出结构化 disabled 原因或真实活动窗口快照。

### P1. Turn the first loop into a smoke path / 把第一条闭环做成 smoke 路径

- Reuse the current `ui.state` / `ui.waitForText` / `ui.tap` / `ui.home` / `ui.back` set instead of expanding command names first. / 第一阶段先复用现有 `ui.state` / `ui.waitForText` / `ui.tap` / `ui.home` / `ui.back`，而不是先扩更多命令名。
- The first concrete script shape is now `apps/android/scripts/local-host-ui-smoke.sh`: `ui.launchApp(OpenClaw) -> ui.tap(Chat) -> ui.waitForText(chat-ready) -> ui.tap(editor) -> ui.inputText(write draft) -> ui.inputText(clear draft) -> ui.state`. / 第一条落地成脚本的路径现在是 `apps/android/scripts/local-host-ui-smoke.sh`：`ui.launchApp(OpenClaw) -> ui.tap(Chat) -> ui.waitForText(聊天就绪) -> ui.tap(输入框) -> ui.inputText(写入草稿) -> ui.inputText(清空草稿) -> ui.state`。
- Keep the default smoke inside OpenClaw first so it stays repeatable and side-effect free; treat cross-app follow-up as the next probe, not as the baseline path yet. / 默认 smoke 先留在 OpenClaw app 内，这样才能保持可重复和无副作用；跨 app follow-up 继续作为下一步探针，而不是当前基线路径。
- Keep the write-tier gate and explicit disclosure intact while doing this. / 在这个过程中继续保持 write tier 门控和明确提示不被放松。

Exit criteria / 退出标准:

- A local or remote operator can replay a stable observe / wait / act smoke path and get deterministic results. / 本地或远端操作者都能复跑一条稳定的 observe / wait / act smoke 路径，并拿到确定性结果。

### P2. Add the next missing primitives / 补下一批缺口 primitive

- Keep turning `ui.launchApp + ui.inputText` into a repeatable real-device path, including immediate follow-up checks while another app is on top. The repo-side entrypoint for that proof now exists as optional follow-up envs on `apps/android/scripts/local-host-ui-cross-app-probe.sh`, and its `--describe` output now groups them behind a small preset label so reruns are easier to copy; the next job is to use that preset preview for fresh device evidence rather than starting from ad-hoc `curl` again. / 继续把 `ui.launchApp + ui.inputText` 收敛成可重复的真机路径，包括在其他 app 置前时立即做后续检查。对应的 repo-side 入口现在已经是 `apps/android/scripts/local-host-ui-cross-app-probe.sh` 上那组可选 follow-up 环境变量，而且它的 `--describe` 输出现在会把它们收进一个小的 preset 标签里，复跑时更容易直接照抄；下一步要做的是用这个 preset 预览补新的设备证据，而不是再从临时 `curl` 起步。
- 当前第一条内置 preset 已经是 `OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PRESET=settings-search-input`：它默认把目标 app 固定到 `com.android.settings`，并改为优先使用稳定的 `resourceId` 做 `tap(searchView) -> input(search_src_text, openclaw)` 这条 repo-side follow-up 链，而不是再依赖英文 `Settings / Search` 文案；如果 OEM 连 resource id 都不同，再继续用显式 env 覆盖。The first built-in preset is now `OPENCLAW_ANDROID_LOCAL_HOST_UI_CROSS_APP_PRESET=settings-search-input`: it defaults the target app to `com.android.settings` and now prefers a stable `resourceId` chain of `tap(searchView) -> input(search_src_text, openclaw)` instead of depending on English `Settings / Search` copy; if an OEM even changes those resource IDs, explicit env overrides still win.
- Investigate or document the current OPPO / ColorOS background freeze only after we have tried a real follow-up action while another app stays on top, because the 30-second sweep no longer suggests an immediate short-window reachability drop. / 只有在 launched app 仍在前台时先尝试过真实 follow-up action 之后，再继续调查或记录 OPPO / ColorOS 的后台冻结，因为 30 秒 sweep 已经不再显示短窗口内会立即掉线。
- Keep validating `ui.inputText` so tasks can move beyond navigation into form-like workflows, then decide whether IME-style fallbacks are actually needed. / 继续验证 `ui.inputText`，让任务从纯导航迈向表单类流程，然后再决定是否真的需要 IME 风格兜底。
- Keep preferring bounded selectors, package scoping, and stable resource IDs over "freeform do anything." / 继续优先做有边界 selector、包名作用域和稳定资源 ID，而不是“无限制自由操作”。
- Treat `ui.swipe` as a coordinate-first v1 rather than a license to add freeform gestures: derive bounds from `ui.state`, keep `packageName` scoping when possible, and only decide on selector-driven swipe or long-press after real-device evidence says we need them. / 把 `ui.swipe` 当成 coordinate-first 的 v1，而不是无限扩手势的理由：尽量从 `ui.state` 推导 bounds、能带 `packageName` 就带上，只有真机证据证明需要时，再决定是否继续补 selector-driven swipe 或 long-press。
- Dedicated reachability probes now exist both as `apps/android/scripts/local-host-ui-cross-app-probe.sh` and `apps/android/scripts/local-host-ui-cross-app-sweep.sh`; because the sweep stayed healthy through 30 seconds, the next step is to layer `ui.inputText` or another follow-up action on top instead of prioritizing more short-to-medium reachability windows first. / 现在已经同时有 `apps/android/scripts/local-host-ui-cross-app-probe.sh` 和 `apps/android/scripts/local-host-ui-cross-app-sweep.sh` 两条 reachability probe；既然 sweep 已经在 30 秒内保持健康，下一步应优先叠加 `ui.inputText` 或其他 follow-up action，而不是继续优先扩短到中等长度的 reachability 窗口。
- In parallel, start evaluating a reverse outbound control channel for the on-device host, because recent GitHub references suggest this is a more realistic long-lived transport than assuming a backgrounded app will keep serving inbound LAN traffic indefinitely. / 并行地开始评估设备内 host 的 reverse outbound 控制通道，因为最新 GitHub 参考更支持这是一条现实的长时控制面，而不是假设退到后台的 app 会无限期稳定提供 LAN 入站服务。

Exit criteria / 退出标准:

- At least one task can launch a target app, wait for a target element, and continue beyond a pure tap/home/back loop. / 至少有一个任务能启动目标 app、等待目标元素，并继续超出纯 tap/home/back 循环的流程。

### P3. Keep scope from drifting / 继续控范围

- Keep the auth-return issue parked unless it blocks a new validation step directly. / 除非直接卡住新的验证步骤，否则继续把授权回跳问题挂起。
- Keep using `apps/android/local-host-ui-automation-plan.md` as the primary phone-control research note so ADB / Appium / `Open-AutoGLM` stay references rather than accidental runtime scope. / 继续把 `apps/android/local-host-ui-automation-plan.md` 当作手机操控主调研文档，确保 ADB / Appium / `Open-AutoGLM` 只是参考，而不会意外变成运行时范围。
- Only after accessibility-based observe / wait / act is repeatable should we evaluate screenshot or `MediaProjection` augmentation. / 只有在基于无障碍的 observe / wait / act 已经可重复之后，再评估截图或 `MediaProjection` 增强。

## Concrete Deliverables For The Next Session / 下一会话的具体产出

- One repeatable real-device observe / wait / act smoke path. / 一条可重复的真机 observe / wait / act smoke 路径。
- One repeatable real-device proof for `ui.launchApp`, including at least one follow-up check while the launched app is still on top. / 一条可重复的 `ui.launchApp` 真机验证路径，而且至少要包含 launched app 仍在前台时的一次后续检查。
- One repeatable cross-app proof that combines `ui.launchApp` and `ui.inputText`, plus a first real-device proof for `ui.swipe` if scrolling is required by the target flow. / 一条把 `ui.launchApp` 和 `ui.inputText` 串起来的可重复跨 app 证据；如果目标流程需要滚动，再补第一条 `ui.swipe` 真机证据。

## Success Criteria / 成功标准

The next session is a success if all of the following become true. / 下一会话只要做到下面这些，就算成功：

- We still reconfirm on-device that UI automation is enabled, or we get a structured reason why not. / 我们仍能在真机上重新确认 UI 自动化已启用，或拿到结构化的未启用原因。
- We can replay a stable observe / wait / act smoke path without relying on ad-hoc manual recovery. / 我们可以复跑一条稳定的 observe / wait / act smoke 路径，而不再依赖临时性的手工补救。
- At least one task grows beyond the current tap/home/back loop via app launch, text entry, or a bounded swipe when the UI actually needs scrolling. / 至少有一个任务能借助 app launch、文本输入，或在界面确实需要滚动时借助有边界的 swipe，超出当前 tap/home/back 的闭环。
- The capability remains explicitly disclosed and gated. / 新能力继续保持明确提示和门控。

## Risks / 风险点

- OEM ROMs may aggressively interrupt service behavior or hide accessibility settings flows. / OEM ROM 可能会激进打断服务行为，或隐藏无障碍设置入口。
- Accessibility-based automation is more sensitive from a policy perspective than the current curated device commands. / 基于无障碍的自动化在平台策略上比当前精选设备命令更敏感。
- Some apps expose poor accessibility metadata, so selector quality may vary. / 某些 app 的无障碍元数据很差，selector 质量会不稳定。
- `Recents` swipe-away behavior on OPPO / ColorOS remains a deployment risk even after UI automation exists. / 即使做完 UI 自动化，OPPO / ColorOS 的 `Recents` 划卡问题仍然是 dedicated 部署风险。

## Open Questions / 待决问题

- Should UI automation be local-only at first, or should remote trusted sessions be allowed to use it immediately? / UI 自动化第一版是否只开放给本地使用，还是立刻开放给可信远端会话？
- Should the first selector model be coordinate-driven, text-driven, or hybrid? / 第一版 selector 模型应以坐标为主、文本为主，还是混合？
- Do we want a separate `ui` tool namespace or fold this into the existing `nodes` tool? / 是单独做 `ui` 工具命名空间，还是继续塞进现有 `nodes` 工具？

Recommended default / 默认建议:

- Start with a separate `ui` namespace and keep it disabled by default for remote roles. / 先做独立 `ui` namespace，并且默认不对远端角色开放。

## Resume Checklist / 接续检查清单

When resuming on another computer tomorrow, do this first. / 明天在另一台电脑继续时，先按这个顺序做：

1. Read this file first. / 先读本文档。
2. Read `apps/android/local-host-handoff.md` for the current Android local-host baseline. / 再读 `apps/android/local-host-handoff.md`，确认当前 Android local-host 基线。
3. Confirm the local branch is still `main` and its upstream remains `origin/custom-dev-base-20260319`. / 确认本地分支仍是 `main`，且 upstream 仍是 `origin/custom-dev-base-20260319`。
4. Run a quick Android status check before editing. / 开始改动前先跑一次 Android 状态检查。
5. Start from real-device `/status` plus `ui.state`, confirm the accessibility grant if the APK was reinstalled, then run the current smoke path before adding new primitives. / 从真机 `/status` 和 `ui.state` 开始；如果 APK 刚重装过，先确认 accessibility grant；然后先跑当前 smoke 路径，再补新的 primitive。

## Suggested Verification Commands / 建议验证命令

```bash
cd apps/android
ANDROID_HOME="$HOME/Library/Android/sdk" \
ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
./gradlew --no-daemon --console=plain :app:compileDebugKotlin
```

```bash
adb devices -l
adb shell am start -n ai.openclaw.app/.MainActivity
```

```bash
curl -sS \
  -H 'Authorization: Bearer <token-from-connect-tab>' \
  http://<phone-ip>:3945/api/local-host/v1/status
```

## Useful References / 参考方向

- Android Accessibility service docs
- Android `AccessibilityService` API docs
- Android UI Automator docs
- Android MediaProjection docs
- Google Play Accessibility API policy
- `Open-AutoGLM`
- `CogAgent`
- `UI2Code_N`
- `droidrun-portal`
- `droidrun`
- `Auto.js` style projects
- `Maestro`
- `Appium UiAutomator2`
- `DroidBot`
- `scrcpy`
