# Android Local Host Phone Control / Android 本机 Host 手机操控说明

Purpose / 用途: give the shortest high-confidence answer to “how should OpenClaw control the phone?” and point to the deeper docs for follow-up work. / 用最短、最高置信度的方式回答“OpenClaw 应该怎样操控手机？”，并把后续深入阅读的文档指到正确位置。

Last updated / 最后更新: March 28, 2026 / 2026 年 3 月 28 日

## Short Answer / 简短结论

OpenClaw Android should control the phone through an in-app `AccessibilityService` runtime first. / OpenClaw Android 的手机操控主路线，应该先走 app 内 `AccessibilityService` 运行时。

That is the best fit for “the phone itself is the host” and for the current Android Local Host direction. / 这条路最符合“手机自己就是 host”的目标，也最符合当前 Android Local Host 的推进方向。

ADB, Appium, `Open-AutoGLM`, and similar systems are useful references, but they are not the primary runtime architecture we want. / ADB、Appium、`Open-AutoGLM` 这类系统很有参考价值，但它们不是我们想要的主运行时架构。

## Why This Path / 为什么走这条路

- `AccessibilityService` can inspect UI structure, detect visible text, and perform bounded node actions or global actions on-device. / `AccessibilityService` 可以在设备内读取 UI 结构、识别可见文本，并执行有边界的节点动作或全局动作。
- It matches the current product goal better than computer-driven harnesses such as Appium or UI Automator over ADB. / 和通过电脑驱动的 Appium 或 ADB 上的 UI Automator 相比，它更符合当前产品目标。
- It keeps the core runtime on the phone instead of requiring a nearby laptop or a permanent remote controller. / 它让核心运行时留在手机上，而不是依赖一台近旁电脑或常驻远程控制端。
- It also gives us a clean safety boundary: keep write actions behind explicit on-device enablement and the remote write tier. / 它还能提供清晰的安全边界：写动作继续放在显式设备侧开启和远端 write tier 门控之后。

## Why Background Control Breaks / 为什么切到后台后会失效

This is not just "AccessibilityService cannot control other apps." / 这不是简单的“AccessibilityService 不能控制别的 app”。

The more precise answer is that Android background limits plus OEM power managers can starve or freeze the host process after it leaves the foreground. / 更准确地说，是 Android 的后台限制再叠加 OEM 电源管理，会在 Host 退到后台后让进程得不到足够的 CPU、网络或保活机会，甚至直接被冻结。

- Android's official Doze and App Standby docs already say idle apps can lose background network access, and that restricted apps may stop running in the background as expected. / Android 官方的 Doze 与 App Standby 文档本身就说明：空闲 app 的后台网络会被延后，而被标成 restricted 的 app 可能无法像预期那样继续在后台运行。
- Android's foreground-service docs also make background recovery harder on modern versions: starting or re-promoting a foreground service from the background is restricted on Android 12+. / Android 的前台服务文档还让后台恢复更难了：从 Android 12 开始，后台启动或重新拉起前台服务本身就受到限制。
- On OPPO / ColorOS, GitHub reports are even more aggressive than AOSP defaults: some devices kill background services, including accessibility-related flows, unless users also lock the task in Recents, allow auto-start, disable battery optimization, and keep a persistent notification. / 在 OPPO / ColorOS 上，GitHub 上的实机报告比 AOSP 默认策略更激进：有些机型会直接杀掉后台服务，甚至影响无障碍相关流程，除非用户同时把任务锁在最近任务里、允许自启动、关闭电池优化，并保留常驻通知。
- In our own project evidence, this is not an "instant background failure" on the current device: on March 28, 2026, `pnpm android:local-host:ui:cross-app:sweep` kept `foregrounded_host_reachable` through `5000ms`, `15000ms`, and `30000ms`. That means the current blocker is not "the app immediately dies in the background," but "the longer-lived control plane is still exposed to OEM background policy." / 结合我们自己的项目证据，这也不是“当前设备一切后台立刻失效”：2026 年 3 月 28 日，`pnpm android:local-host:ui:cross-app:sweep` 在 `5000ms`、`15000ms`、`30000ms` 三档都保持了 `foregrounded_host_reachable`。这说明当前真正的风险不是“只要退后台就立刻死掉”，而是“更长时长的控制面仍然暴露在 OEM 后台策略下”。

## What Recent Research Suggests / 最近资料给出的解决方向

The research and GitHub material point to four distinct solution classes. / 这轮资料和 GitHub 项目基本收敛到四类解决路线。

1. Keepalive and recovery on the device / 设备内保活与恢复
   - Foreground service, battery-optimization exemption, exact alarms, and user-visible recovery paths are still the official Android tools. / 前台服务、电池优化豁免、精确闹钟和面向用户的恢复入口，仍然是 Android 官方给出的主要工具。
   - FCM high-priority wakeups are the official answer when the app must be nudged while idle; Android explicitly recommends FCM over maintaining your own persistent background connection where possible. / 当 app 在 idle 状态下仍需要被唤醒时，官方答案是 FCM 高优先级消息；Android 也明确更推荐这种方式，而不是每个 app 自己硬维持后台长连接。

2. Reverse or outbound control plane / 反向或外连控制面
   - `droidrun-portal` is the cleanest reference here: besides local HTTP/WebSocket servers, it also supports a reverse outbound WebSocket connection initiated by the device. / `droidrun-portal` 是这里最值得参考的项目：它除了本地 HTTP / WebSocket 服务，还支持由设备主动发起的 reverse outbound WebSocket 连接。
   - Inference: for OpenClaw, a device-initiated reverse channel is likely more robust than assuming the backgrounded app can keep serving inbound LAN requests forever. / 推断：对 OpenClaw 来说，由设备主动发起的反向通道，很可能比假设后台 app 会永远稳定监听 LAN 入站请求更稳。

3. External controller architectures / 外部主控架构
   - `Open-AutoGLM`, `uiautomator2`, and Appium's UiAutomator2 driver all rely on an external controller over ADB or a device-side automation service, instead of requiring the target app itself to remain the sole long-lived automation runtime. / `Open-AutoGLM`、`uiautomator2`、Appium 的 UiAutomator2 driver 都依赖外部主控配合 ADB 或设备侧自动化服务，而不是要求目标 app 自己承担唯一的长时运行控制器。
   - This is why those stacks are often more reliable for hostile OEMs: the automation executor is no longer only "the backgrounded app we are trying to keep alive." / 这也是它们在激进 OEM 上往往更稳的原因：自动化执行器不再只有“那个已经退到后台、还要设法活着的 app”。

4. Managed-device mode / 受管设备模式
   - Android's lock-task mode is the strongest official answer if the phone is a dedicated device and you can accept device-owner style management. / 如果手机就是 dedicated 设备，而且可以接受 device-owner 级管理，Android 的 lock-task mode 是最强的官方方案。
   - This is not a generic consumer-phone fix, but it is a serious option for an idle-phone deployment SKU. / 这不是普通消费级手机都适用的修复，但对闲置手机部署形态来说，它是非常认真的一条路。

## What Papers Actually Help With / 论文真正能解决什么

Recent mobile-agent papers help much more with action quality than with Android process survival. / 最近的 mobile agent 论文，对“动作质量”的帮助远大于对“Android 进程存活”的帮助。

- `AndroidWorld` is mainly about realistic and reproducible task evaluation. / `AndroidWorld` 主要解决的是现实任务的可重复评测。
- `V-Droid`, `Mobile-Agent-v3`, and similar work improve planning, verification, and action reliability. / `V-Droid`、`Mobile-Agent-v3` 这类工作主要提升规划、校验和动作可靠性。
- They do not provide a general way to bypass OEM background killing. / 它们并没有给出一条通用的“绕过 OEM 杀后台”的方案。

So the paper-side takeaway is: use them to improve follow-up action quality and benchmark rigor, not to expect a model-layer fix for ColorOS background policy. / 所以论文侧真正有价值的结论是：用它们增强 follow-up action 质量和 benchmark 严谨性，而不是期待模型层自己解决 ColorOS 的后台策略。

## Recommended Path For OpenClaw / 对 OpenClaw 最合理的处理路线

For this project, the most realistic path is hybrid rather than ideological. / 对这个项目来说，最现实的处理方式不是单一路线信仰，而是分层组合。

1. Keep the current in-app `AccessibilityService` runtime as the primary phone-control surface. / 继续把当前 app 内 `AccessibilityService` runtime 作为主手机操控面。
2. Add a reverse outbound transport, so the device can initiate the control channel instead of only waiting for inbound LAN calls. / 增加 reverse outbound transport，让设备主动发起控制通道，而不是只等 LAN 入站请求。
3. Keep the OPPO / ColorOS operator playbook explicit: battery exemption, keep the app locked in Recents, enable startup-related permissions where available, and never swipe the host away. / 把 OPPO / ColorOS 的操作手册继续写死：电池豁免、最近任务锁定、开启可能存在的自启动相关权限、绝对不要把 host 划掉。
4. Treat exact alarms and watchdogs as recovery tools, not as the primary live control plane. / 把精确闹钟和 watchdog 视为恢复工具，而不是主要的实时控制面。
5. If reliability on hostile OEMs becomes a hard requirement, add an external-controller lane based on ADB / UiAutomator2 rather than forcing the on-device host to solve every background policy alone. / 如果 hostile OEM 上的可靠性变成硬要求，就增加一条基于 ADB / UiAutomator2 的外部主控路线，而不是强迫设备内 host 单独扛下所有后台策略问题。
6. If the deployment target is a dedicated phone rather than a normal personal phone, evaluate lock-task / dedicated-device mode instead of treating it as overkill. / 如果部署目标是 dedicated phone 而不是普通私人手机，就认真评估 lock-task / dedicated-device mode，不要把它一开始就当成过度设计。

## Can We Put It Into The System / 能不能“写进系统”

Yes, but this phrase covers several very different levels. / 能，但“写进系统”其实分成好几档，成本和收益差别很大。

### Level 1. Device owner / dedicated-device mode / 第 1 档：Device Owner / 专用设备模式

This is the best official path if the phone is truly idle and can be dedicated to OpenClaw. / 如果这台手机真的是闲置设备，而且可以专门给 OpenClaw 用，这是最值得优先尝试的官方路线。

- With device owner plus lock-task mode, OpenClaw or its launcher can stay pinned as the managed front door of the device. / 有了 device owner 加 lock-task mode，OpenClaw 或它的 launcher 可以变成设备受管入口。
- This does not make OpenClaw a platform service, but it does reduce the need to survive as an ordinary background consumer app. / 它不会让 OpenClaw 变成平台服务，但它会显著降低“像普通消费级后台 app 一样求生”的压力。
- Reference: Android's dedicated-device docs and TestDPC sample are the right starting point. / 参考上，Android dedicated-device 文档和 TestDPC sample 是最合适的起点。

### Level 2. Root plus systemized app / 第 2 档：Root 后 systemize 成系统 app

If you are willing to root the spare phone, converting the app into a system app or `priv-app` is feasible. / 如果你愿意 root 这台闲置手机，把 app systemize 成 system app 或 `priv-app` 是可行的。

- GitHub tooling such as `AppSystemizer` and other Magisk-based installers shows this is a practical path on rooted phones. / GitHub 上像 `AppSystemizer`、其他 Magisk system-app 安装器都说明，这在 rooted 设备上是现实可走的路。
- But this still does not magically make the app a real `system_server` service. / 但这仍然不会神奇地把 app 变成真正的 `system_server` 服务。
- Also, AOSP's privileged-app docs make an important distinction: being placed in `priv-app` is not enough by itself; privileged permissions must be allowlisted, and some capabilities still require platform signing or a custom build. / 同时 AOSP 的 privileged-app 文档也强调了一个关键区别：放进 `priv-app` 本身并不够，特权权限还要有 allowlist，某些能力还需要 platform signing 或自定义构建。

### Level 3. Privileged app in a custom ROM / 第 3 档：自定义 ROM 里的 privileged app

If you build or modify the ROM, you can preload OpenClaw into `/system/priv-app`, `/system_ext/priv-app`, or `/product/priv-app` and ship matching `privapp-permissions` XML. / 如果你愿意自己做 ROM 或改系统镜像，可以把 OpenClaw 预装进 `/system/priv-app`、`/system_ext/priv-app` 或 `/product/priv-app`，并配套 `privapp-permissions` XML。

- This is much closer to "really in the system" than Magisk systemization. / 这比 Magisk 式 systemize 更接近“真的进系统”。
- It is also the first level where asking for some signature|privileged capabilities becomes structurally reasonable. / 也是第一档可以结构性争取某些 signature|privileged 能力的路线。
- But it now becomes ROM engineering: partitions, SELinux, permission allowlists, signing, OTA compatibility, and rescue risk all become your responsibility. / 但这时就已经进入 ROM 工程：分区、SELinux、权限 allowlist、签名、OTA 兼容性、救砖风险都要自己扛。

### Level 4. True Android system service / 第 4 档：真正的 Android system service

This is possible only in the "custom platform build" sense, not as an ordinary app packaging trick. / 这一档只在“自定义 Android 平台构建”的意义上成立，不是普通 app 打包技巧能做到的。

- AOSP system-service material shows that true system services live in platform processes, often under `system_server` or native system partitions, with SELinux, service contexts, and framework integration. / AOSP 的 system-service 资料说明，真正的系统服务运行在平台进程里，通常挂在 `system_server` 或 native system partition，并伴随 SELinux、service context 和 framework 集成。
- If OpenClaw ever goes this far, we are no longer talking about "an Android app with extra privileges"; we are talking about a custom Android build or OEM-style integration. / 如果 OpenClaw 真走到这一步，那讨论的就不再是“一个权限更高的 Android app”，而是“自定义 Android 构建 / OEM 式集成”了。
- For this project stage, that is likely too expensive unless the goal becomes a dedicated in-house device image. / 以当前项目阶段看，除非目标已经变成自用专机镜像，否则这条路大概率过重。

## My Recommendation / 我的建议

If this is really a spare phone, the order I would recommend is: / 如果这真的是一台闲置手机，我建议的优先级是：

1. Try device owner plus lock-task / dedicated-device mode first. / 先试 device owner 加 lock-task / dedicated-device mode。
2. If root is acceptable, test a Magisk-based system-app lane to reduce ordinary-app fragility. / 如果能接受 root，再测试一条 Magisk-based system-app 路线，降低普通 app 形态的脆弱性。
3. Only if those still are not enough, move to a custom-ROM `priv-app` preload. / 只有前两条都不够，再考虑自定义 ROM 的 `priv-app` 预装。
4. Treat a true `system_server` service as the final form, not the first experiment. / 把真正的 `system_server` 服务当作最终形态，而不是第一步实验。

## What Recent GitHub Work Changes / 最近 GitHub 开源给出的新信号

- [`droidrun/droidrun-portal`](https://github.com/droidrun/droidrun-portal) is now the closest runtime blueprint for OpenClaw Android: it uses an Android accessibility service, exports JSON UI trees, exposes launchable-app discovery, supports keyboard text input, and can run with local or reverse connections. / [`droidrun/droidrun-portal`](https://github.com/droidrun/droidrun-portal) 现在是最接近 OpenClaw Android 的 runtime 参考：它基于 Android 无障碍服务，导出 JSON UI 树，暴露可启动 app 列表，支持键盘文本输入，并且既能本地运行，也能反向连接。
- [`zai-org/Open-AutoGLM`](https://github.com/zai-org/Open-AutoGLM) is still the clearest external-controller reference: its README centers on Python + VLM + ADB/HDC orchestration, remote debugging, and ADB-keyboard-style text input. That is useful for control-loop ideas and fallback mechanisms, but it is not the runtime shape we want for “the phone itself is the host.” / [`zai-org/Open-AutoGLM`](https://github.com/zai-org/Open-AutoGLM) 仍然是最清晰的外部主控参考：它的 README 主轴是 Python + VLM + ADB/HDC 编排、远程调试，以及类似 ADB Keyboard 的文本输入。这对动作闭环和兜底机制有参考价值，但不是“手机自己就是 host”要采用的运行时形态。
- [`bytedance/UI-TARS`](https://github.com/bytedance/UI-TARS), [`X-PLUG/MobileAgent`](https://github.com/X-PLUG/MobileAgent), [`OpenBMB/AgentCPM-GUI`](https://github.com/OpenBMB/AgentCPM-GUI), and [`showlab/ShowUI`](https://github.com/showlab/ShowUI) mostly change the model layer, not the Android embedding layer: they provide stronger action schemas, mobile prompts, grounding, planning, reflection, memory, and compact on-device JSON action spaces. / [`bytedance/UI-TARS`](https://github.com/bytedance/UI-TARS)、[`X-PLUG/MobileAgent`](https://github.com/X-PLUG/MobileAgent)、[`OpenBMB/AgentCPM-GUI`](https://github.com/OpenBMB/AgentCPM-GUI)、[`showlab/ShowUI`](https://github.com/showlab/ShowUI) 主要改变的是模型层，而不是 Android 内嵌 runtime 层：它们提供了更强的动作 schema、移动端 prompt、grounding、planning、reflection、memory，以及更紧凑的端侧 JSON 动作空间。
- [`google-research/android_world`](https://github.com/google-research/android_world) plus [`GUI-CEval`](https://arxiv.org/abs/2603.15039) should be treated as validation references, not runtime templates. They are the right inspiration for repeatable smoke, regression, and task evaluation once OpenClaw has a stable phone-control surface. / [`google-research/android_world`](https://github.com/google-research/android_world) 和 [`GUI-CEval`](https://arxiv.org/abs/2603.15039) 更应该被当成验证参考，而不是 runtime 模板。等 OpenClaw 有了稳定的手机操控面之后，它们才是做可重复 smoke、回归和任务评测的正确灵感来源。

## How To Land It / 具体怎么落地

- Keep the runtime local and Android-native first: expand the in-app `AccessibilityService` surface rather than adding a separate ADB-first controller. / 第一阶段继续保持 runtime 在手机内、Android 原生：优先扩 app 内 `AccessibilityService` 能力面，而不是再加一层 ADB-first 主控器。
- Keep `ui.launchApp` package-first in v1 and validate it on-device before expanding package discovery. If package discovery becomes a blocker later, add a read-only launchable-app listing surface after that. / 让 `ui.launchApp` 的第一版继续保持 package-first，并先在真机上验证它，再考虑扩 package 发现。如果后面 package 发现成为阻塞，再补一个只读的可启动 app 列表能力。
- Keep `ui.inputText` focused-editable / selector-editable in v1 and validate it across more real apps before adding IME-style fallbacks. / 让 `ui.inputText` 在第一版继续保持 focused-editable / selector-editable 边界，并先在更多真实 app 上验证，再考虑补 IME 风格兜底。
- Only after `ui.launchApp` and `input_text` are stable should we spend more time on screenshot-grounding augmentation from `ShowUI`, `UI-TARS`, or `AgentCPM-GUI`. / 只有在 `ui.launchApp` 和 `input_text` 稳定之后，才值得继续投入 `ShowUI`、`UI-TARS`、`AgentCPM-GUI` 这类截图 grounding 增强。
- Use `AndroidWorld`-style and `GUI-CEval`-style tasks to judge progress, rather than treating “demo works once” as enough. / 进度判断应逐步靠近 `AndroidWorld` 风格和 `GUI-CEval` 风格的任务验证，而不是把“demo 成功一次”当成足够。

## What Works Now / 现在已经能做什么

- `ui.state`: read the active window snapshot, including `packageName`, visible text, and node count. / `ui.state`：读取当前活动窗口快照，包括 `packageName`、可见文本和节点数量。
- `ui.waitForText`: wait for a simple visible-text condition. / `ui.waitForText`：等待简单的可见文本条件。
- `ui.launchApp`: launch an installed app with a package-first contract such as `com.android.settings`. / `ui.launchApp`：以 package-first 的方式拉起已安装 app，例如 `com.android.settings`。
- `ui.inputText`: set text on a focused or selector-matched editable node through accessibility text-setting. / `ui.inputText`：通过无障碍文本设置能力，向当前焦点或 selector 命中的 editable 节点写入文本。
- `ui.tap`: tap a bounded selector such as a text match. / `ui.tap`：点击一个有边界 selector，例如文本匹配。
- `ui.back` and `ui.home`: execute bounded global actions. / `ui.back` 和 `ui.home`：执行有边界的全局动作。
- Remote gating now works as intended: with write disabled, remote sessions only see `ui.state` and `ui.waitForText`; once write is enabled, `ui.launchApp`, `ui.inputText`, `ui.tap`, `ui.back`, and `ui.home` appear. / 远端门控现在已按预期工作：write 关闭时远端只有 `ui.state` 和 `ui.waitForText`；开启 write 后，`ui.launchApp`、`ui.inputText`、`ui.tap`、`ui.back`、`ui.home` 才会出现。

## Real Device Proof / 真机证据

- On March 26, 2026, `ui.tap(text=\"Chat\")` successfully switched OpenClaw from the Connect tab into the Chat tab on the connected OPPO / ColorOS phone. / 2026 年 3 月 26 日，`ui.tap(text=\"Chat\")` 已在当前接入的 OPPO / ColorOS 手机上成功把 OpenClaw 从 Connect tab 切到 Chat tab。
- On the same phone, `ui.home` and `ui.back` both moved the active `packageName` to `com.android.launcher`. / 在同一台手机上，`ui.home` 和 `ui.back` 都已把活动 `packageName` 切到 `com.android.launcher`。
- On March 27, 2026, `ui.launchApp(packageName=\"com.android.settings\")` was validated on-device: the remote invoke returned `launched=true`, and `adb shell dumpsys activity activities` showed `topResumedActivity=com.android.settings/.Settings`. / 2026 年 3 月 27 日，`ui.launchApp(packageName=\"com.android.settings\")` 已在真机验证：远端调用返回 `launched=true`，而 `adb shell dumpsys activity activities` 显示 `topResumedActivity=com.android.settings/.Settings`。
- Later that same day, `ui.inputText(value=\"3945\", text=\"3945\", matchMode=\"exact\", packageName=\"ai.openclaw.app\")` also succeeded on-device against the Connect-screen port field, returning `performed=true` with `strategy=selector_editable`. / 同一天稍后，`ui.inputText(value=\"3945\", text=\"3945\", matchMode=\"exact\", packageName=\"ai.openclaw.app\")` 也已在真机针对 Connect 页端口输入框成功返回 `performed=true`，并给出 `strategy=selector_editable`。
- A first repeatable validation harness now exists as `apps/android/scripts/local-host-ui-smoke.sh` plus `pnpm android:local-host:ui`: by default it re-foregrounds OpenClaw, moves into the Chat tab, focuses the editor, writes a temporary unsent draft, clears it again, and captures the before/after UI state into artifacts. / 第一条可重复验证面现在也已经落成 `apps/android/scripts/local-host-ui-smoke.sh` 和 `pnpm android:local-host:ui`：默认它会先把 OpenClaw 带回前台，再切到 Chat 页、聚焦输入框、写入一条临时未发送草稿、再清空它，并把前后 UI 状态写进 artifact。
- That same UI smoke now also has real-device proof: on March 27, 2026 it succeeded over `adb forward`, including the write-and-clear draft path on the Chat composer. / 这条 UI smoke 现在也已经拿到真机证据：2026 年 3 月 27 日它已经通过 `adb forward` 在真机跑通，包括 Chat 输入框里的“写入并清空草稿”路径。
- There is now also a separate cross-app probe at `apps/android/scripts/local-host-ui-cross-app-probe.sh`: it checks whether a launched app truly becomes the foreground app, whether OpenClaw stays remotely reachable during that window, and whether adb can recover the app cleanly afterward. / 现在也有了一条独立的跨 app probe：`apps/android/scripts/local-host-ui-cross-app-probe.sh`。它会检查被拉起的 app 是否真的成为前台 app、这段时间 OpenClaw 是否继续远端可达、以及 adb 是否能在事后把 app 干净地恢复回来。
- On March 28, 2026, that probe ran end-to-end on the same OPPO / ColorOS phone against `com.android.settings` and classified as `foregrounded_host_reachable`: the target app reached the true foreground for 9 of 10 rounds, all 10 `/status` probes still succeeded, and adb recovery returned OpenClaw cleanly. / 2026 年 3 月 28 日，这条 probe 已在同一台 OPPO / ColorOS 手机上针对 `com.android.settings` 完整跑通，并得到 `foregrounded_host_reachable`：目标 app 在 10 轮里有 9 轮成为真前台，10 次 `/status` 探针全部成功，而且 adb 恢复也把 OpenClaw 干净地带了回来。
- There is now also a multi-window sweep at `apps/android/scripts/local-host-ui-cross-app-sweep.sh`: it reuses the same probe for multiple observation windows and writes one combined summary. / 现在还多了一条多窗口 sweep：`apps/android/scripts/local-host-ui-cross-app-sweep.sh`。它会针对多档观察窗口复用同一条 probe，并输出一份汇总 summary。
- On the same phone, the first default sweep on March 28, 2026 kept `foregrounded_host_reachable` across `5000ms`, `15000ms`, and `30000ms`, so the current setup did not reproduce a background-freeze boundary within 30 seconds. / 在同一台手机上，2026 年 3 月 28 日第一轮默认 sweep 在 `5000ms`、`15000ms`、`30000ms` 三档都保持了 `foregrounded_host_reachable`，因此当前设置下还没有在 30 秒内复现后台冻结边界。
- Reinstalling the APK on this device clears the OpenClaw accessibility grant, so `ui.state` must be rechecked after reinstall and the service may need to be re-enabled. / 在这台设备上重新安装 APK 会清空 OpenClaw 的无障碍授权，因此重装后必须重新检查 `ui.state`，并且可能需要重新开启服务。

## What Is Still Missing / 现在还缺什么

- A repeatable cross-app path that combines `ui.launchApp` and `ui.inputText` now that reachability is already proven through 30 seconds on the current setup. / 既然当前设置下 30 秒内的 reachability 已经成立，下一步还需要一条可重复的跨 app 路径，把 `ui.launchApp` 和 `ui.inputText` 串起来。
- If the longer background freeze reappears later, an operator playbook is still needed, but it is no longer the first unresolved question. / 如果后面再次出现更长后台冻结，仍然需要一条操作手册，但它已经不再是当前第一优先的未决问题。
- A read-only launchable-app listing surface, but only if package discovery becomes the real blocker after `ui.launchApp` validation. / 一个只读的可启动 app 列表能力，但前提是 `ui.launchApp` 验证后，package 发现真的成为实际阻塞。
- Richer selectors such as stronger `resourceId` support and more stable node ranking. / 更丰富的 selector，例如更强的 `resourceId` 支持和更稳定的节点排序。
- The current missing piece is no longer “any smoke script exists,” but “keep the smoke healthy as a baseline and then extend it across app boundaries.” / 当前缺的已经不再是“有没有 smoke 脚本”，而是“先把 smoke 稳定成健康基线，再把它扩到跨 app 边界”。

## Policy Boundary / 策略边界

The technical runtime direction and the distribution direction are not the same question. / 技术运行时方向和发布分发方向，不是同一个问题。

For now, the right engineering path is still `AccessibilityService` first. / 现阶段正确的工程路线仍然是 `AccessibilityService` 优先。

For Google Play distribution, accessibility-based autonomous control remains a separate product and policy question. / 对 Google Play 分发来说，基于 accessibility 的自治操控仍然是一个单独的产品和策略问题。

## How To Resume / 接手时怎么看

1. Read `apps/android/local-host-handoff.md` for the current operational state. / 先看 `apps/android/local-host-handoff.md`，确认当前操作态。
2. Read `apps/android/local-host-ui-automation-plan.md` for the deeper phone-control architecture and research notes. / 再看 `apps/android/local-host-ui-automation-plan.md`，获取更完整的手机操控架构与调研结论。
3. Use `apps/android/local-host-progress.md` for the validation log and current next-slice priorities. / 用 `apps/android/local-host-progress.md` 跟验证记录和当前下一段优先级。
