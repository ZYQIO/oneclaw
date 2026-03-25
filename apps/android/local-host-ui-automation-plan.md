# Android Local Host UI Automation Plan / Android 本机 Host UI 自动化规划

Purpose / 用途: capture today's status, findings, and next-session execution plan for making Android `Local Host` capable of operating the phone itself without a nearby computer. / 记录今天的状态、结论和下一会话执行计划，目标是让 Android `Local Host` 在没有电脑陪同的情况下也能自己操作手机。

Last updated / 最后更新: March 26, 2026 / 2026 年 3 月 26 日

## Session Goal / 本次会话目标

Move Android `Local Host` from "GPT can chat plus use curated device tools" toward "the phone can observe and operate its own UI like a lightweight autonomous host." / 把 Android `Local Host` 从“GPT 能聊天并调用精选设备工具”推进到“手机可以像轻量自治 Host 一样观察并操作自己的 UI”。

## What Happened Today / 今天做了什么

### Delivery / 交付

- The previously pushed Android local-host batch on this line is commit `75631a07c5` with message `Android: expand and harden local host deployment`. / 这一条开发线此前已推送的 Android local-host 批次是提交 `75631a07c5`，提交信息为 `Android: expand and harden local host deployment`。
- That pushed batch already includes local-host tool-calling, app-private workspace tooling, dedicated idle-phone deployment, recovery/watchdog behavior, and OEM background-policy readiness surfacing. / 那一批已推送内容已经包含：本机 Host 工具调用、app-private workspace、闲置手机 dedicated 部署、恢复 / watchdog 机制，以及 OEM 后台策略 readiness 提示。
- The first UI-automation scaffolding slice is now in the Android app: accessibility-service manifest wiring, service XML, Connect-tab readiness, and `/status` reporting for `uiAutomationAvailable`. / Android app 里已经落下第一段 UI 自动化骨架：无障碍服务 manifest 接线、service XML、Connect 页 readiness，以及 `/status` 的 `uiAutomationAvailable` 上报。
- A first read-only `ui.state` command now exists too: before accessibility is enabled it returns structured readiness reasons, and once the service is live it returns `packageName`, `visibleText`, `nodeCount`, and a flattened node list for the active window. / 第一条只读 `ui.state` 命令也已经接上：无障碍未开启时会返回结构化 readiness 原因，服务连上后则会返回当前窗口的 `packageName`、`visibleText`、`nodeCount` 和扁平节点列表。
- The Codex browser-auth return-to-app path was also retried with a custom deep link plus browser CTA, but on the current OPPO / ColorOS device it still does not return reliably; that issue is now parked so it does not block UI automation. / 这轮还重试了 Codex 浏览器授权回 App 路径，加入了自定义 deep link 和浏览器 CTA，但在当前 OPPO / ColorOS 设备上仍不能稳定回跳；这个问题现在已挂起，不再阻塞 UI 自动化推进。

### Investigation / 调研

- Verified in code why the app cannot yet "control the phone like a human." / 已在代码中核实为什么当前 app 还不能“像人一样控制手机”。
- Researched Android-native UI automation routes plus existing GitHub projects that already solve similar problems. / 已调研 Android 原生 UI 自动化路径，以及 GitHub 上已解决类似问题的项目。
- Reviewed Z.ai's recent GitHub open-source work to separate model-side references from Android-runtime references. / 已补看 Z.ai 最近在 GitHub 开源的项目，区分哪些更适合当模型参考、哪些更适合当 Android runtime 参考。
- Reduced the architecture choice to a concrete direction instead of continuing vague scope expansion. / 已把架构选择收敛到明确方向，而不是继续模糊扩范围。

## Current Reality / 当前现实

Today the Android app can already do these things well. / 当前 Android app 已经比较稳定地具备以下能力：

- Run `Local Host` directly on the phone. / 在手机上直接运行 `Local Host`。
- Authenticate with Codex and use GPT for local chat. / 使用 Codex 授权并通过 GPT 完成本地聊天。
- Execute a curated set of Android-native actions through the local `nodes` tool. / 通过本机 `nodes` tool 执行一组精选 Android 原生命令。
- Maintain a dedicated idle-phone deployment with keepalive, restore, and readiness signals. / 以 dedicated idle-phone 形态运行，并具备保活、恢复和 readiness 信号。
- Use an app-private local `workspace` for file-like operations. / 使用 app-private 本地 `workspace` 做文件式操作。

What it still cannot do yet. / 但它现在仍然做不到：

- Read a deeper selector-friendly UI tree of other apps. / 读取更适合 selector 的、层次更完整的其他 app UI 树。
- Tap, swipe, long-press, type, or press system buttons on behalf of the user. / 代用户点击、滑动、长按、输入文本或按系统键。
- Launch another app, wait for a target element, then continue an interaction loop. / 拉起另一个 app，等待目标元素出现，然后继续执行交互循环。
- Behave like the desktop OpenClaw host when a task requires real cross-app navigation. / 在需要真实跨 app 导航时，像桌面 OpenClaw host 那样行动。

## Why It Cannot Control The Phone Yet / 为什么现在还不能控制手机

This is not mainly an auth problem. / 这主要不是授权问题。

It is a missing runtime surface. / 而是缺了一层运行时能力面。

- `apps/android/app/src/main/AndroidManifest.xml` now declares an accessibility service, but the runtime surface is still only partially built. / `apps/android/app/src/main/AndroidManifest.xml` 现在已经声明了无障碍服务，但这层运行时能力面仍只完成了一部分。
- `apps/android/app/src/main/java/ai/openclaw/app/node/InvokeCommandRegistry.kt` and `apps/android/app/src/main/java/ai/openclaw/app/node/InvokeDispatcher.kt` now expose a first read-only `ui.state`, but there is still no `tap`, `swipe`, `input_text`, `launch_app`, `back`, or `home` action path. / `apps/android/app/src/main/java/ai/openclaw/app/node/InvokeCommandRegistry.kt` 和 `apps/android/app/src/main/java/ai/openclaw/app/node/InvokeDispatcher.kt` 现在已经接入第一条只读 `ui.state`，但仍然没有 `tap`、`swipe`、`input_text`、`launch_app`、`back`、`home` 这类动作路径。
- The current `ui.state` output is intentionally lightweight: it is good enough for readiness and simple observation, but not yet a selector-friendly control surface. / 当前 `ui.state` 输出是有意保持轻量的：它足够做 readiness 和简单观察，但还不足以成为 selector-friendly 的操控面。
- The model can only act within the runtime surface that exists, so until we add waits plus bounded actions, the phone still cannot complete a real cross-app interaction loop. / 模型只能在 runtime 已暴露的能力面内行动，所以在 waits 和 bounded actions 落地之前，手机仍然不能完成真正的跨 app 交互闭环。

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

## Tomorrow Objective / 明天目标

Validate the landed scaffolding on a real phone, then ship the first bounded control loop. / 先在真机上验证已落地骨架，再做出第一条有边界的控制闭环。

The target is not "start UI automation from zero." / 目标已经不是“从零开始做 UI 自动化”。

The target is to turn `ui.state` into a usable observe / wait / act path. / 现在的目标是把 `ui.state` 推进成可用的 observe / wait / act 路径。

## Tomorrow Execution Plan / 明天执行计划

### P0. Reconfirm the real-device baseline / 先重确认真机基线

- Re-enable the Android accessibility service on the connected phone if needed. / 如有需要，在当前连接手机上重新开启无障碍服务。
- Run `ui.state` and capture the readiness result or active-window snapshot. / 运行 `ui.state`，记录 readiness 结果或当前活动窗口快照。
- Keep this proof in the docs so the next iteration starts from a verified baseline instead of source inspection only. / 把这条真机记录沉淀进文档，确保下一轮不是只靠源码分析开工。

Exit criteria / 退出标准:

- App can still report `uiAutomationAvailable=true/false` on the real phone. / App 仍能在真机上报告 `uiAutomationAvailable=true/false`。
- `ui.state` gives either a structured disabled reason or a real active-window snapshot. / `ui.state` 能给出结构化 disabled 原因或真实活动窗口快照。

### P1. Add the first wait primitive / 增加第一条等待能力

- Implement `wait_for_text` on top of the current observation path. / 基于当前观察链路实现 `wait_for_text`。
- Reuse the current snapshot model first instead of designing a much larger selector language up front. / 第一版先复用当前快照模型，不急着设计更大的 selector 语言。
- Add tests for timeout, disabled service, and a simple success case. / 为超时、服务未启用、简单成功场景补测试。

Exit criteria / 退出标准:

- Local chat or remote invoke can wait for a simple visible-text condition and get a deterministic result. / 本地聊天或远端 invoke 能等待一个简单可见文本条件，并拿到确定性结果。

### P2. Add the first write actions / 增加第一批写动作

- Implement `ui.back` and `ui.home` first, then add `ui.tap`. / 先实现 `ui.back` 和 `ui.home`，再补 `ui.tap`。
- Prefer bounded selectors and coordinates over "freeform do anything" at first. / 第一版优先做有边界的 selector / 坐标动作，不做“无限制自由操作”。
- Log each action as a tool event so chat can explain what happened. / 每个动作都作为 tool event 回流，让聊天界面能解释执行过程。

Exit criteria / 退出标准:

- The app can observe a state, wait for a simple condition, and perform at least one successful global action or tap. / app 能观察一个状态、等待一个简单条件，并成功完成至少一个全局动作或点击。

### P3. Keep scope from drifting / 继续控范围

- Keep the auth-return issue parked unless it blocks a new validation step directly. / 除非直接卡住新的验证步骤，否则继续把授权回跳问题挂起。
- Only after accessibility-based observe / wait / act works should we evaluate screenshot or `MediaProjection` augmentation. / 只有在基于无障碍的 observe / wait / act 已经跑通之后，再评估截图或 `MediaProjection` 增强。

## Concrete Deliverables For The Next Session / 下一会话的具体产出

- One real-device `ui.state` validation record. / 一条真机 `ui.state` 验证记录。
- A first `wait_for_text` command path. / 第一版 `wait_for_text` 命令路径。
- One or more writable actions such as `ui.back`, `ui.home`, or `ui.tap`. / 一个或多个可写动作，如 `ui.back`、`ui.home`、`ui.tap`。

## Success Criteria / 成功标准

The next session is a success if all of the following become true. / 下一会话只要做到下面这些，就算成功：

- We reconfirm on-device that UI automation is enabled or get a structured reason why not. / 我们在真机上重新确认 UI 自动化已启用，或拿到结构化的未启用原因。
- We can fetch a structured UI snapshot from the phone itself and use it to support a simple wait loop. / 我们可以从手机自身拿到结构化 UI 快照，并基于它支撑一个简单等待闭环。
- At least one bounded cross-app action succeeds on a real phone. / 至少一个有边界的跨 app 动作能在真机成功执行。
- The new capability is explicitly disclosed and gated. / 新能力有明确提示和门控。

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
5. Start from real-device `ui.state`, then `wait_for_text`, then the first bounded actions. / 从真机 `ui.state` 开始，再做 `wait_for_text`，然后补第一批 bounded actions。

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
