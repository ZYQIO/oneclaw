# Android Local Host UI Automation Plan / Android 本机 Host UI 自动化规划

Purpose / 用途: capture today's status, findings, and next-session execution plan for making Android `Local Host` capable of operating the phone itself without a nearby computer. / 记录今天的状态、结论和下一会话执行计划，目标是让 Android `Local Host` 在没有电脑陪同的情况下也能自己操作手机。

Last updated / 最后更新: March 25, 2026 / 2026 年 3 月 25 日

## Session Goal / 本次会话目标

Move Android `Local Host` from "GPT can chat plus use curated device tools" toward "the phone can observe and operate its own UI like a lightweight autonomous host." / 把 Android `Local Host` 从“GPT 能聊天并调用精选设备工具”推进到“手机可以像轻量自治 Host 一样观察并操作自己的 UI”。

## What Happened Today / 今天做了什么

### Delivery / 交付

- Committed and pushed the current Android local-host batch to `origin/custom-dev-base-20260319`. / 已把当前 Android local-host 这一批改动提交并推送到 `origin/custom-dev-base-20260319`。
- Commit pushed today: `75631a07c5` with message `Android: expand and harden local host deployment`. / 今日已推送提交：`75631a07c5`，提交信息为 `Android: expand and harden local host deployment`。
- The pushed batch includes local-host tool-calling, app-private workspace tooling, dedicated idle-phone deployment, recovery/watchdog behavior, and OEM background-policy readiness surfacing. / 这批已推送内容包含：本机 Host 工具调用、app-private workspace、闲置手机 dedicated 部署、恢复 / watchdog 机制，以及 OEM 后台策略 readiness 提示。

### Investigation / 调研

- Verified in code why the app cannot yet "control the phone like a human." / 已在代码中核实为什么当前 app 还不能“像人一样控制手机”。
- Researched Android-native UI automation routes plus existing GitHub projects that already solve similar problems. / 已调研 Android 原生 UI 自动化路径，以及 GitHub 上已解决类似问题的项目。
- Reduced the architecture choice to a concrete direction instead of continuing vague scope expansion. / 已把架构选择收敛到明确方向，而不是继续模糊扩范围。

## Current Reality / 当前现实

Today the Android app can already do these things well. / 当前 Android app 已经比较稳定地具备以下能力：

- Run `Local Host` directly on the phone. / 在手机上直接运行 `Local Host`。
- Authenticate with Codex and use GPT for local chat. / 使用 Codex 授权并通过 GPT 完成本地聊天。
- Execute a curated set of Android-native actions through the local `nodes` tool. / 通过本机 `nodes` tool 执行一组精选 Android 原生命令。
- Maintain a dedicated idle-phone deployment with keepalive, restore, and readiness signals. / 以 dedicated idle-phone 形态运行，并具备保活、恢复和 readiness 信号。
- Use an app-private local `workspace` for file-like operations. / 使用 app-private 本地 `workspace` 做文件式操作。

What it still cannot do yet. / 但它现在仍然做不到：

- Read the current UI tree of other apps. / 读取其他 app 的当前 UI 树。
- Tap, swipe, long-press, type, or press system buttons on behalf of the user. / 代用户点击、滑动、长按、输入文本或按系统键。
- Launch another app, wait for a target element, then continue an interaction loop. / 拉起另一个 app，等待目标元素出现，然后继续执行交互循环。
- Behave like the desktop OpenClaw host when a task requires real cross-app navigation. / 在需要真实跨 app 导航时，像桌面 OpenClaw host 那样行动。

## Why It Cannot Control The Phone Yet / 为什么现在还不能控制手机

This is not mainly an auth problem. / 这主要不是授权问题。

It is a missing runtime surface. / 而是缺了一层运行时能力面。

- `apps/android/app/src/main/AndroidManifest.xml` currently declares a foreground service, boot receiver, and notification listener, but no accessibility service. / `apps/android/app/src/main/AndroidManifest.xml` 目前声明了前台服务、开机接收器和通知监听器，但没有无障碍服务。
- `apps/android/app/src/main/java/ai/openclaw/app/node/InvokeCommandRegistry.kt` only advertises curated commands such as camera, location, notifications, contacts, calendar, motion, SMS, and call log. / `apps/android/app/src/main/java/ai/openclaw/app/node/InvokeCommandRegistry.kt` 当前只暴露了相机、位置、通知、联系人、日历、运动、短信、通话记录等精选命令。
- `apps/android/app/src/main/java/ai/openclaw/app/node/InvokeDispatcher.kt` only dispatches that curated command set. There is no `tap`, `swipe`, `input_text`, `launch_app`, `back`, `home`, or `read_ui_tree` path. / `apps/android/app/src/main/java/ai/openclaw/app/node/InvokeDispatcher.kt` 也只分发这一组精选命令，没有 `tap`、`swipe`、`input_text`、`launch_app`、`back`、`home`、`read_ui_tree` 这类路径。
- `apps/android/app/src/main/java/ai/openclaw/app/host/LocalHostNodesTooling.kt` maps the local-host chat tool surface to those same curated actions, so the model cannot do more than the runtime exposes. / `apps/android/app/src/main/java/ai/openclaw/app/host/LocalHostNodesTooling.kt` 也只是把聊天工具面映射到同样这批精选动作，所以模型不可能做出 runtime 没暴露的能力。

## Research Findings / 调研结论

### What Android Allows / Android 允许什么

- The most realistic in-app path is `AccessibilityService`. It can inspect UI structure, trigger node actions, and dispatch gestures when the user explicitly enables the service. / 最现实的 app 内路径是 `AccessibilityService`。在用户显式开启服务后，它可以读取 UI 结构、触发节点动作，并分发手势。
- Visual-only control is possible with screenshots or `MediaProjection`, but Android treats it as a foreground, user-consented flow and it is harder to keep alive as an always-on host. / 纯视觉控制也可以做，比如截图或 `MediaProjection`，但 Android 会把它视为需要前台且需用户同意的流程，不适合直接做成长驻 host 的第一实现。
- Test frameworks such as UI Automator and Appium are powerful, but they are usually driven by a computer or ADB and are a weaker fit for an idle-phone autonomous deployment. / UI Automator、Appium 这类测试框架虽然强，但通常由电脑或 ADB 驱动，对“闲置手机自治部署”并不是最优匹配。

### What GitHub Already Shows / GitHub 上已经验证过什么

- `droidrun-portal` is structurally the closest reference: Android-side portal, accessibility service, UI tree, action APIs, and local control surface. / `droidrun-portal` 在结构上最值得参考：Android 端 portal、无障碍服务、UI 树、动作 API、本地控制面基本齐了。
- `Auto.js`-style projects prove that accessibility plus scripting can drive real phones without a nearby computer. / `Auto.js` 风格项目已经证明，无障碍加脚本这条路能在没有电脑陪同的情况下驱动真实手机。
- `Maestro`, `Appium UiAutomator2`, and `DroidBot` are good references for action models and testing patterns, but not the exact deployment architecture we want. / `Maestro`、`Appium UiAutomator2`、`DroidBot` 适合借鉴动作模型和测试方法，但不是我们想要的最终部署架构。
- `scrcpy` is useful as a remote-control reference, but it is host-computer centric rather than phone-autonomous. / `scrcpy` 适合参考远控能力，但它是电脑主控，不是手机自治。

## Decision / 当前判断

Do not try to copy the entire desktop runtime into Android first. / 不要先把整套桌面 runtime 硬搬进 Android。

Build an `Accessibility Portal` first. / 先做一层 `Accessibility Portal`。

This should become the missing bridge between the current Android-native tool surface and true cross-app phone operation. / 它应该成为“当前 Android 原生工具面”和“真实跨 app 操作手机”之间缺失的那一层桥。

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

Start the first implementation slice for Android UI automation. / 开始 Android UI 自动化的第一段实现。

The target is not "full autonomy in one day." / 目标不是“一天做完整自治”。

The target is to ship the first real control loop. / 而是先做出第一条真实可用的控制闭环。

## Tomorrow Execution Plan / 明天执行计划

### P0. Land the scaffolding / 先落基础骨架

- Add an Android accessibility service declaration and XML config. / 增加 Android 无障碍服务声明和 XML 配置。
- Add a small runtime manager that tracks whether the service is enabled and whether UI automation is available. / 增加一个小型 runtime manager，用来跟踪服务是否启用、UI 自动化是否可用。
- Expose readiness in local UI and remote `/status`. / 把 readiness 暴露到本地 UI 和远端 `/status`。

Exit criteria / 退出标准:

- App can report `uiAutomationAvailable=true/false`. / App 能报告 `uiAutomationAvailable=true/false`。
- There is a user-visible path that tells the operator how to enable the accessibility service. / 有明确的用户可见路径告诉操作者如何开启无障碍服务。

### P1. Add read-only UI observation / 增加只读 UI 观察

- Implement `ui.state` to return a normalized snapshot of the active window. / 实现 `ui.state`，返回当前活动窗口的标准化快照。
- Add package name, visible text summary, and node list/tree summary. / 返回包名、可见文本摘要、节点列表 / 树摘要。
- Add tests for empty tree, disabled service, and a normalized sample node tree. / 为无树、服务未启用、标准化节点树样例补测试。

Exit criteria / 退出标准:

- Local chat or remote invoke can ask for current UI state and get structured output. / 本地聊天或远端 invoke 能请求当前 UI 状态并拿到结构化输出。

### P2. Add the first write actions / 增加第一批写动作

- Implement `ui.tap`, `ui.back`, and `ui.home` first. / 先实现 `ui.tap`、`ui.back`、`ui.home`。
- Prefer bounded selectors and coordinates over "freeform do anything" at first. / 第一版优先做有边界的 selector / 坐标动作，不做“无限制自由操作”。
- Log each action as a tool event so chat can explain what happened. / 每个动作都作为 tool event 回流，让聊天界面能解释执行过程。

Exit criteria / 退出标准:

- The app can open another app, return the current state, and perform at least one successful tap or global action. / app 能拉起另一个 app、返回当前状态，并成功完成至少一个点击或全局动作。

### P3. Decide whether to add vision / 再决定是否加视觉

- Only after accessibility-based observation works should we evaluate screenshot or `MediaProjection` augmentation. / 只有在基于无障碍的观察已经跑通之后，再评估截图或 `MediaProjection` 增强。
- Do not make vision the first dependency. / 不要把视觉作为第一依赖。

## Concrete Deliverables For The Next Session / 下一会话的具体产出

- New Android accessibility service files and manifest wiring. / 新增 Android 无障碍服务文件及 manifest 接线。
- A readiness section in `ConnectTabScreen` and `/status` for UI automation. / 在 `ConnectTabScreen` 和 `/status` 中增加 UI 自动化 readiness。
- A first `ui.state` command path. / 第一版 `ui.state` 命令路径。
- If time allows, one writable action such as `ui.back` or `ui.tap`. / 如果时间允许，再做一个可写动作，如 `ui.back` 或 `ui.tap`。

## Success Criteria / 成功标准

The next session is a success if all of the following become true. / 下一会话只要做到下面这些，就算成功：

- The phone can report whether UI automation is enabled. / 手机能报告 UI 自动化是否已启用。
- We can fetch a structured UI snapshot from the phone itself. / 我们可以从手机自身拿到结构化 UI 快照。
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
3. Confirm the target branch is still `origin/custom-dev-base-20260319`. / 确认目标分支仍然是 `origin/custom-dev-base-20260319`。
4. Run a quick Android status check before editing. / 开始改动前先跑一次 Android 状态检查。
5. Start from accessibility scaffolding, not from vision or more generic command expansion. / 从无障碍骨架开始，不要先上视觉，也不要先继续泛化扩命令。

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
- `droidrun-portal`
- `droidrun`
- `Auto.js` style projects
- `Maestro`
- `Appium UiAutomator2`
- `DroidBot`
- `scrcpy`
