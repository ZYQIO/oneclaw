# Android Local Host Phone Control / Android 本机 Host 手机操控说明

Purpose / 用途: give the shortest high-confidence answer to “how should OpenClaw control the phone?” and point to the deeper docs for follow-up work. / 用最短、最高置信度的方式回答“OpenClaw 应该怎样操控手机？”，并把后续深入阅读的文档指到正确位置。

Last updated / 最后更新: March 27, 2026 / 2026 年 3 月 27 日

## Short Answer / 简短结论

OpenClaw Android should control the phone through an in-app `AccessibilityService` runtime first. / OpenClaw Android 的手机操控主路线，应该先走 app 内 `AccessibilityService` 运行时。

That is the best fit for “the phone itself is the host” and for the current Android Local Host direction. / 这条路最符合“手机自己就是 host”的目标，也最符合当前 Android Local Host 的推进方向。

ADB, Appium, `Open-AutoGLM`, and similar systems are useful references, but they are not the primary runtime architecture we want. / ADB、Appium、`Open-AutoGLM` 这类系统很有参考价值，但它们不是我们想要的主运行时架构。

## Why This Path / 为什么走这条路

- `AccessibilityService` can inspect UI structure, detect visible text, and perform bounded node actions or global actions on-device. / `AccessibilityService` 可以在设备内读取 UI 结构、识别可见文本，并执行有边界的节点动作或全局动作。
- It matches the current product goal better than computer-driven harnesses such as Appium or UI Automator over ADB. / 和通过电脑驱动的 Appium 或 ADB 上的 UI Automator 相比，它更符合当前产品目标。
- It keeps the core runtime on the phone instead of requiring a nearby laptop or a permanent remote controller. / 它让核心运行时留在手机上，而不是依赖一台近旁电脑或常驻远程控制端。
- It also gives us a clean safety boundary: keep write actions behind explicit on-device enablement and the remote write tier. / 它还能提供清晰的安全边界：写动作继续放在显式设备侧开启和远端 write tier 门控之后。

## What Recent GitHub Work Changes / 最近 GitHub 开源给出的新信号

- [`droidrun/droidrun-portal`](https://github.com/droidrun/droidrun-portal) is now the closest runtime blueprint for OpenClaw Android: it uses an Android accessibility service, exports JSON UI trees, exposes launchable-app discovery, supports keyboard text input, and can run with local or reverse connections. / [`droidrun/droidrun-portal`](https://github.com/droidrun/droidrun-portal) 现在是最接近 OpenClaw Android 的 runtime 参考：它基于 Android 无障碍服务，导出 JSON UI 树，暴露可启动 app 列表，支持键盘文本输入，并且既能本地运行，也能反向连接。
- [`zai-org/Open-AutoGLM`](https://github.com/zai-org/Open-AutoGLM) is still the clearest external-controller reference: its README centers on Python + VLM + ADB/HDC orchestration, remote debugging, and ADB-keyboard-style text input. That is useful for control-loop ideas and fallback mechanisms, but it is not the runtime shape we want for “the phone itself is the host.” / [`zai-org/Open-AutoGLM`](https://github.com/zai-org/Open-AutoGLM) 仍然是最清晰的外部主控参考：它的 README 主轴是 Python + VLM + ADB/HDC 编排、远程调试，以及类似 ADB Keyboard 的文本输入。这对动作闭环和兜底机制有参考价值，但不是“手机自己就是 host”要采用的运行时形态。
- [`bytedance/UI-TARS`](https://github.com/bytedance/UI-TARS), [`X-PLUG/MobileAgent`](https://github.com/X-PLUG/MobileAgent), [`OpenBMB/AgentCPM-GUI`](https://github.com/OpenBMB/AgentCPM-GUI), and [`showlab/ShowUI`](https://github.com/showlab/ShowUI) mostly change the model layer, not the Android embedding layer: they provide stronger action schemas, mobile prompts, grounding, planning, reflection, memory, and compact on-device JSON action spaces. / [`bytedance/UI-TARS`](https://github.com/bytedance/UI-TARS)、[`X-PLUG/MobileAgent`](https://github.com/X-PLUG/MobileAgent)、[`OpenBMB/AgentCPM-GUI`](https://github.com/OpenBMB/AgentCPM-GUI)、[`showlab/ShowUI`](https://github.com/showlab/ShowUI) 主要改变的是模型层，而不是 Android 内嵌 runtime 层：它们提供了更强的动作 schema、移动端 prompt、grounding、planning、reflection、memory，以及更紧凑的端侧 JSON 动作空间。
- [`google-research/android_world`](https://github.com/google-research/android_world) plus [`GUI-CEval`](https://arxiv.org/abs/2603.15039) should be treated as validation references, not runtime templates. They are the right inspiration for repeatable smoke, regression, and task evaluation once OpenClaw has a stable phone-control surface. / [`google-research/android_world`](https://github.com/google-research/android_world) 和 [`GUI-CEval`](https://arxiv.org/abs/2603.15039) 更应该被当成验证参考，而不是 runtime 模板。等 OpenClaw 有了稳定的手机操控面之后，它们才是做可重复 smoke、回归和任务评测的正确灵感来源。

## How To Land It / 具体怎么落地

- Keep the runtime local and Android-native first: expand the in-app `AccessibilityService` surface rather than adding a separate ADB-first controller. / 第一阶段继续保持 runtime 在手机内、Android 原生：优先扩 app 内 `AccessibilityService` 能力面，而不是再加一层 ADB-first 主控器。
- Keep `ui.launchApp` package-first in v1 and validate it on-device before expanding package discovery. If package discovery becomes a blocker later, add a read-only launchable-app listing surface after that. / 让 `ui.launchApp` 的第一版继续保持 package-first，并先在真机上验证它，再考虑扩 package 发现。如果后面 package 发现成为阻塞，再补一个只读的可启动 app 列表能力。
- Add `input_text` next by targeting the focused editable node and using accessibility text-setting first. Keep keyboard-IME-style fallbacks as a later compatibility layer, not the first dependency. / 下一步补 `input_text`，优先对准当前焦点 editable 节点，并先走无障碍文本设置能力。类似键盘 IME 的兜底应留作后续兼容层，而不是第一依赖。
- Only after `ui.launchApp` and `input_text` are stable should we spend more time on screenshot-grounding augmentation from `ShowUI`, `UI-TARS`, or `AgentCPM-GUI`. / 只有在 `ui.launchApp` 和 `input_text` 稳定之后，才值得继续投入 `ShowUI`、`UI-TARS`、`AgentCPM-GUI` 这类截图 grounding 增强。
- Use `AndroidWorld`-style and `GUI-CEval`-style tasks to judge progress, rather than treating “demo works once” as enough. / 进度判断应逐步靠近 `AndroidWorld` 风格和 `GUI-CEval` 风格的任务验证，而不是把“demo 成功一次”当成足够。

## What Works Now / 现在已经能做什么

- `ui.state`: read the active window snapshot, including `packageName`, visible text, and node count. / `ui.state`：读取当前活动窗口快照，包括 `packageName`、可见文本和节点数量。
- `ui.waitForText`: wait for a simple visible-text condition. / `ui.waitForText`：等待简单的可见文本条件。
- `ui.launchApp`: launch an installed app with a package-first contract such as `com.android.settings`. / `ui.launchApp`：以 package-first 的方式拉起已安装 app，例如 `com.android.settings`。
- `ui.tap`: tap a bounded selector such as a text match. / `ui.tap`：点击一个有边界 selector，例如文本匹配。
- `ui.back` and `ui.home`: execute bounded global actions. / `ui.back` 和 `ui.home`：执行有边界的全局动作。
- Remote gating now works as intended: with write disabled, remote sessions only see `ui.state` and `ui.waitForText`; once write is enabled, `ui.launchApp`, `ui.tap`, `ui.back`, and `ui.home` appear. / 远端门控现在已按预期工作：write 关闭时远端只有 `ui.state` 和 `ui.waitForText`；开启 write 后，`ui.launchApp`、`ui.tap`、`ui.back`、`ui.home` 才会出现。

## Real Device Proof / 真机证据

- On March 26, 2026, `ui.tap(text=\"Chat\")` successfully switched OpenClaw from the Connect tab into the Chat tab on the connected OPPO / ColorOS phone. / 2026 年 3 月 26 日，`ui.tap(text=\"Chat\")` 已在当前接入的 OPPO / ColorOS 手机上成功把 OpenClaw 从 Connect tab 切到 Chat tab。
- On the same phone, `ui.home` and `ui.back` both moved the active `packageName` to `com.android.launcher`. / 在同一台手机上，`ui.home` 和 `ui.back` 都已把活动 `packageName` 切到 `com.android.launcher`。
- On March 27, 2026, `ui.launchApp(packageName=\"com.android.settings\")` was validated on-device: the remote invoke returned `launched=true`, and `adb shell dumpsys activity activities` showed `topResumedActivity=com.android.settings/.Settings`. / 2026 年 3 月 27 日，`ui.launchApp(packageName=\"com.android.settings\")` 已在真机验证：远端调用返回 `launched=true`，而 `adb shell dumpsys activity activities` 显示 `topResumedActivity=com.android.settings/.Settings`。
- On the same OPPO / ColorOS phone, remote `/status` still responded immediately after that app launch, but the system later froze `ai.openclaw.app` in the background and further remote requests timed out until OpenClaw was brought back to the foreground. / 在同一台 OPPO / ColorOS 手机上，app 启动后的第一时间远端 `/status` 仍然能回包，但随后系统会把后台的 `ai.openclaw.app` 冻住，后续远端请求超时，直到 OpenClaw 被重新带回前台。
- Reinstalling the APK on this device clears the OpenClaw accessibility grant, so `ui.state` must be rechecked after reinstall and the service may need to be re-enabled. / 在这台设备上重新安装 APK 会清空 OpenClaw 的无障碍授权，因此重装后必须重新检查 `ui.state`，并且可能需要重新开启服务。

## What Is Still Missing / 现在还缺什么

- A mitigation or operator playbook for the current OPPO / ColorOS background freeze after OpenClaw leaves the foreground, so follow-up remote commands can survive across app boundaries. / 当前 OPPO / ColorOS 在 OpenClaw 退到后台后会触发后台冻结，需要一条缓解方案或操作手册，这样后续远端命令才能跨 app 存活。
- `input_text`, ideally focused-field-first in v1, so flows can move beyond pure navigation. / `input_text`，第一版最好先从焦点输入框出发，这样流程才能超出纯导航。
- A read-only launchable-app listing surface, but only if package discovery becomes the real blocker after `ui.launchApp` validation. / 一个只读的可启动 app 列表能力，但前提是 `ui.launchApp` 验证后，package 发现真的成为实际阻塞。
- Richer selectors such as stronger `resourceId` support and more stable node ranking. / 更丰富的 selector，例如更强的 `resourceId` 支持和更稳定的节点排序。
- A repeatable real-device smoke script for the current observe / wait / act loop. / 一条可重复的真机 smoke 脚本，用来验证当前 observe / wait / act 闭环。

## Policy Boundary / 策略边界

The technical runtime direction and the distribution direction are not the same question. / 技术运行时方向和发布分发方向，不是同一个问题。

For now, the right engineering path is still `AccessibilityService` first. / 现阶段正确的工程路线仍然是 `AccessibilityService` 优先。

For Google Play distribution, accessibility-based autonomous control remains a separate product and policy question. / 对 Google Play 分发来说，基于 accessibility 的自治操控仍然是一个单独的产品和策略问题。

## How To Resume / 接手时怎么看

1. Read `apps/android/local-host-handoff.md` for the current operational state. / 先看 `apps/android/local-host-handoff.md`，确认当前操作态。
2. Read `apps/android/local-host-ui-automation-plan.md` for the deeper phone-control architecture and research notes. / 再看 `apps/android/local-host-ui-automation-plan.md`，获取更完整的手机操控架构与调研结论。
3. Use `apps/android/local-host-progress.md` for the validation log and current next-slice priorities. / 用 `apps/android/local-host-progress.md` 跟验证记录和当前下一段优先级。
