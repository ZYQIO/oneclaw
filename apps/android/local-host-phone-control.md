# Android Local Host Phone Control / Android 本机 Host 手机操控说明

Purpose / 用途: give the shortest high-confidence answer to “how should OpenClaw control the phone?” and point to the deeper docs for follow-up work. / 用最短、最高置信度的方式回答“OpenClaw 应该怎样操控手机？”，并把后续深入阅读的文档指到正确位置。

Last updated / 最后更新: March 26, 2026 / 2026 年 3 月 26 日

## Short Answer / 简短结论

OpenClaw Android should control the phone through an in-app `AccessibilityService` runtime first. / OpenClaw Android 的手机操控主路线，应该先走 app 内 `AccessibilityService` 运行时。

That is the best fit for “the phone itself is the host” and for the current Android Local Host direction. / 这条路最符合“手机自己就是 host”的目标，也最符合当前 Android Local Host 的推进方向。

ADB, Appium, `Open-AutoGLM`, and similar systems are useful references, but they are not the primary runtime architecture we want. / ADB、Appium、`Open-AutoGLM` 这类系统很有参考价值，但它们不是我们想要的主运行时架构。

## Why This Path / 为什么走这条路

- `AccessibilityService` can inspect UI structure, detect visible text, and perform bounded node actions or global actions on-device. / `AccessibilityService` 可以在设备内读取 UI 结构、识别可见文本，并执行有边界的节点动作或全局动作。
- It matches the current product goal better than computer-driven harnesses such as Appium or UI Automator over ADB. / 和通过电脑驱动的 Appium 或 ADB 上的 UI Automator 相比，它更符合当前产品目标。
- It keeps the core runtime on the phone instead of requiring a nearby laptop or a permanent remote controller. / 它让核心运行时留在手机上，而不是依赖一台近旁电脑或常驻远程控制端。
- It also gives us a clean safety boundary: keep write actions behind explicit on-device enablement and the remote write tier. / 它还能提供清晰的安全边界：写动作继续放在显式设备侧开启和远端 write tier 门控之后。

## What Works Now / 现在已经能做什么

- `ui.state`: read the active window snapshot, including `packageName`, visible text, and node count. / `ui.state`：读取当前活动窗口快照，包括 `packageName`、可见文本和节点数量。
- `ui.waitForText`: wait for a simple visible-text condition. / `ui.waitForText`：等待简单的可见文本条件。
- `ui.tap`: tap a bounded selector such as a text match. / `ui.tap`：点击一个有边界 selector，例如文本匹配。
- `ui.back` and `ui.home`: execute bounded global actions. / `ui.back` 和 `ui.home`：执行有边界的全局动作。
- Remote gating now works as intended: with write disabled, remote sessions only see `ui.state` and `ui.waitForText`; once write is enabled, `ui.tap`, `ui.back`, and `ui.home` appear. / 远端门控现在已按预期工作：write 关闭时远端只有 `ui.state` 和 `ui.waitForText`；开启 write 后，`ui.tap`、`ui.back`、`ui.home` 才会出现。

## Real Device Proof / 真机证据

- On March 26, 2026, `ui.tap(text=\"Chat\")` successfully switched OpenClaw from the Connect tab into the Chat tab on the connected OPPO / ColorOS phone. / 2026 年 3 月 26 日，`ui.tap(text=\"Chat\")` 已在当前接入的 OPPO / ColorOS 手机上成功把 OpenClaw 从 Connect tab 切到 Chat tab。
- On the same phone, `ui.home` and `ui.back` both moved the active `packageName` to `com.android.launcher`. / 在同一台手机上，`ui.home` 和 `ui.back` 都已把活动 `packageName` 切到 `com.android.launcher`。
- Reinstalling the APK on this device clears the OpenClaw accessibility grant, so `ui.state` must be rechecked after reinstall and the service may need to be re-enabled. / 在这台设备上重新安装 APK 会清空 OpenClaw 的无障碍授权，因此重装后必须重新检查 `ui.state`，并且可能需要重新开启服务。

## What Is Still Missing / 现在还缺什么

- `launch_app`, so the phone can leave OpenClaw without ADB help. / `launch_app`，这样手机离开 OpenClaw 时就不再需要 ADB 帮忙。
- `input_text`, so flows can move beyond pure navigation. / `input_text`，这样流程才能超出纯导航。
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
