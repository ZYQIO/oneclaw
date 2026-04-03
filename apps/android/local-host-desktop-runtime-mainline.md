# Android Desktop Runtime Mainline / Android 桌面 Runtime 主线

Purpose / 用途: redefine the Android mainline around integrating selected desktop-runtime capabilities into the app, instead of stopping at the current helper-pod boundary. / 把 Android 主线重新定义为将精选桌面 runtime 能力整合进 app，而不是停在当前 helper pod 边界。
Branch / 分支: `android-desktop-runtime-mainline-20260403`
Last updated / 最后更新: April 3, 2026 / 2026 年 4 月 3 日

## Pivot / 主线切换

- The embedded-runtime pod is now the bootstrap carrier, not the destination. / embedded-runtime pod 现在是引导载体，不再是终点。
- The Android-native `Local Host` MVP, UI automation, and dedicated-device work now serve as the control plane and operating envelope for desktop-runtime integration. / Android 原生 `Local Host` MVP、UI 自动化和 dedicated-device 现在转为桌面 runtime 集成的控制平面和运行边界。
- Do not treat `pod.health`, `pod.manifest.describe`, `pod.workspace.scan`, and `pod.workspace.read` as the final product shape. / 不要再把 `pod.health`、`pod.manifest.describe`、`pod.workspace.scan` 与 `pod.workspace.read` 当成最终产品形态。

## Current Baseline / 当前基线

- The packaged pod payload is now `0.6.0` and includes a packaged `desktop/` stage on top of the earlier `runtime/`, `toolkit/`, and `browser/` carrier stages. / 当前打包 pod payload 已更新到 `0.6.0`，并在既有 `runtime/`、`toolkit/` 与 `browser/` carrier stages 之上新增了打包 `desktop/` stage。
- `pod.runtime.execute` still carries the first bounded execution lane, `pod.browser.describe` now reports the first bounded browser-auth lane, and `pod.browser.auth.start` reuses the app's existing OpenAI Codex OAuth browser flow instead of pretending Android already has a generic browser runtime. / `pod.runtime.execute` 仍然承载第一条有边界执行通道，`pod.browser.describe` 现在开始报告第一条有边界 browser-auth lane，而 `pod.browser.auth.start` 只是复用 app 现有的 OpenAI Codex OAuth 浏览器流程，并不假装 Android 已经拥有通用浏览器 runtime。

- Build-time packaging, app-private extraction, checksum verification, and read-only pod invocation are already landed. / 构建期打包、app 私有目录解包、checksum 校验和只读 pod 调用已经落地。
- `pod.runtime.describe` is now the machine-readable status surface for this mainline and reports which desktop-runtime domains are landed, bootstrap-only, or still missing. / `pod.runtime.describe` 现在是这条主线的机器可读状态面，会直接报告哪些桌面 runtime 域已落地、仅是 bootstrap，或仍然缺失。
- The current pod payload still does not include a generic browser runtime, a full embedded execution engine, an app-private runtime supervisor, or packaged plugin execution. / 当前 pod payload 仍然没有通用浏览器 runtime、完整嵌入执行引擎、app 私有 runtime supervisor，或打包插件执行面。

## Mainline Objective / 主线目标

Bring a selected desktop-runtime slice onto Android so the phone can host not only local chat and Android-native commands, but also a curated subset of the desktop execution environment. / 把一段精选桌面 runtime 能力带到 Android，让手机不只承载本地聊天和 Android 原生命令，还能承载一段经过裁剪的桌面执行环境。

Target capability domains / 目标能力域:

- `packaging`: ship runtime assets in the APK, extract them into app-private storage, verify them, and keep the payload replayable. / `packaging`：把 runtime 资产随 APK 交付，解到 app 私有目录，完成校验，并维持 payload 可复跑。
- `engine`: package a real embedded execution engine that can run a bounded desktop-side task on-device. / `engine`：打包一个真正的嵌入执行引擎，能在设备内执行一条有边界的桌面侧任务。
- `environment`: provide an app-private runtime environment with config injection, health reporting, lifecycle supervision, logs, and restart semantics. / `environment`：提供 app 私有 runtime 环境，带配置注入、健康状态、生命周期监督、日志和重启语义。
- `browser`: provide a bounded browser lane for auth and selected automation flows without turning Android into an unrestricted desktop browser host. / `browser`：提供一个有边界的浏览器通道，用于授权和精选自动化流程，但不把 Android 变成无限制桌面浏览器宿主。
- `tools`: package a curated subset of desktop tools behind explicit contracts. / `tools`：把精选桌面工具以显式契约方式打包进来。
- `plugins`: package only allowlisted plugin/runtime surfaces that are needed for the curated slice. / `plugins`：只打包白名单插件或 runtime 面，服务于这条精选能力切片。
- `workspace-bridge`: keep packaged workspace, app-private workspace, and runtime state readable and replayable across relaunches. / `workspace-bridge`：让打包 workspace、app 私有 workspace 和 runtime 状态在重启后仍可读取和复跑。

## Capability Map / 能力图

- `packaging`: landed bootstrap. The app can already prepare, ship, extract, and verify the embedded payload. / `packaging`：已落地 bootstrap。App 已经能准备、交付、解包和校验嵌入 payload。
- `helper-surface`: landed bootstrap. Read-only pod helpers already exist and remain useful as diagnostics and bootstrap plumbing. / `helper-surface`：已落地 bootstrap。只读 pod helper 已存在，并继续作为诊断和引导管线有价值。
- `workspace-bridge`: landed bootstrap. Packaged workspace metadata and document reads already work. / `workspace-bridge`：已落地 bootstrap。打包 workspace 元数据和文档读取已经可用。
- `engine`: missing. No real embedded desktop execution engine is wired into Android yet. / `engine`：缺失。Android 里还没有接入真正的嵌入桌面执行引擎。
- `environment`: missing. No runtime env supervisor, process model, config bundle, or restart contract exists yet. / `environment`：缺失。还没有 runtime env supervisor、进程模型、配置 bundle 或重启契约。
- `browser`: partial bootstrap. A single allowlisted external-browser auth lane is now packaged, but it still needs replayable on-device proof. / `browser`：部分 bootstrap。现在已经打包了一条单一白名单 external-browser auth lane，但仍需可复跑的真机证据。
- `tools`: partial bootstrap. One packaged desktop tool lane now exists behind toolkit descriptors and command policy, but it still needs repetitive on-device replay proof. / `tools`：部分 bootstrap。现在已经有一条通过 toolkit descriptor 和 command policy 封装的打包桌面工具通道，但仍需持续补足真机复跑证据。
- `plugins`: missing. No packaged plugin/runtime lane exists yet. / `plugins`：缺失。还没有打包插件或 runtime lane。

## Definition Of Done / 完成定义

This mainline is only done when all of the following are true. / 只有以下条件全部满足，这条主线才算完成。

- A packaged embedded engine can execute at least one bounded desktop-side workflow on a real phone. / 打包进 app 的嵌入引擎能在真机上执行至少一条有边界的桌面侧工作流。
- That workflow runs inside an app-private runtime environment with visible health, logs, and restart semantics. / 这条工作流运行在 app 私有 runtime 环境里，并具有可见的健康状态、日志和重启语义。
- A bounded browser lane exists for the desktop-runtime slice where auth or browser-driven tasks are actually required. / 当授权或浏览器驱动任务确有需要时，存在一条有边界的浏览器通道。
- At least one curated desktop tool lane is packaged and callable through a replayable contract. / 至少有一条精选桌面工具通道被打包并可通过可复跑契约调用。
- Plugin runtime surfaces, if any, stay allowlisted and explicit rather than open-ended. / 如需插件 runtime，其能力面必须保持白名单和显式边界，而不是开放式扩散。
- A real-device smoke can verify the packaged runtime slice end to end after install or reinstall. / 真机 smoke 能在安装或重装后端到端验证这段打包 runtime slice。

## Phases / 阶段切片

### Phase 0. Visibility And Contract / 可见性与契约

- Land a machine-readable runtime status surface. / 先落一层机器可读的 runtime 状态面。
- Record the mainline branch, targeted domains, bootstrap state, and missing domains in code and docs. / 在代码和文档里记录主线分支、目标能力域、bootstrap 状态和缺失域。
- Status on this branch: `pod.runtime.describe` is the first landed piece of this phase. / 这条分支上的状态：`pod.runtime.describe` 是本阶段第一块已落地内容。

### Phase 1. Engine Carrier / 引擎载体

- Status on this branch: the packaged `runtime/` stage plus `pod.runtime.execute(taskId=runtime-smoke)` still provide the first bounded execution carrier, and `pod.runtime.execute(taskId=tool-brief-inspect)` now proves the first packaged desktop tool contract on top of it. / 这条分支的现状：打包好的 `runtime/` stage 和 `pod.runtime.execute(taskId=runtime-smoke)` 仍然提供第一条有边界执行载体，而 `pod.runtime.execute(taskId=tool-brief-inspect)` 现在已经在其上证明了第一条打包桌面工具契约。

- Choose and package the smallest viable embedded execution engine. / 选出并打包最小可行的嵌入执行引擎。
- Prove it can run one bounded desktop-side task from app-private storage. / 证明它能从 app 私有目录执行一条有边界的桌面侧任务。
- Keep the payload build-time packaged and versioned. / 保持 payload 构建期打包且带版本。

### Phase 2. Runtime Environment / 运行环境

- Status on this branch: `pod.runtime.execute` now hydrates `filesDir/openclaw/embedded-runtime-home/<version>/` with config, logs, state, and work directories, the first packaged tool lane already writes replayable results under `work/`, and the next gap is sustained on-device replay for the new bounded browser-auth lane. / 这条分支的现状：`pod.runtime.execute` 已经会把 `filesDir/openclaw/embedded-runtime-home/<version>/` 水合成带有 config、logs、state 和 work 目录的 runtime home，第一条打包工具通道也已经会把可复跑结果写入 `work/`，而下一步缺口则变成了给新的 bounded browser-auth lane 补上持续真机复跑。

- Add an app-private runtime environment layout, config bundle, lifecycle supervision, and health/log surfaces. / 加入 app 私有 runtime 环境目录、配置 bundle、生命周期监督和 health/log 面。
- Make restart and stale-build diagnostics explicit. / 让重启和 stale-build 诊断显式化。

### Phase 3. Browser Lane / 浏览器通道

- Add only the browser capability that the curated desktop slice truly needs. / 只加入精选桌面切片真正需要的浏览器能力。
- Keep it bounded and replayable. / 保持有边界且可复跑。

### Phase 4. Tools And Plugins / 工具与插件

- Package one curated desktop tool lane first. / 先打包一条精选桌面工具通道。
- Add plugin/runtime surfaces only when the curated tool lane proves the need. / 只有在精选工具通道证明必要性之后，再补插件或 runtime 面。

## Guardrails / 护栏

- No unrestricted remote shell. / 不做无限制远程 shell。
- No Play-first promise for a self-mutating desktop runtime. / 不承诺面向 Play 的自演化桌面 runtime。
- No reopening of the old assumption that the helper quartet is already enough. / 不再回到“helper quartet 已经足够”的旧假设。
- Do not widen browser, tools, or plugins before the engine carrier and runtime environment exist. / 在引擎载体和 runtime 环境落地前，不要先扩 browser、tools 或 plugins。

## Working Rule / 工作规则

- `apps/android/local-host-desktop-runtime-feasibility.md` stays as the feasibility input. / `apps/android/local-host-desktop-runtime-feasibility.md` 继续作为可行性输入文档。
- `apps/android/local-host-embedded-runtime-pod-plan.md` now describes the bootstrap carrier, not the end-state mainline. / `apps/android/local-host-embedded-runtime-pod-plan.md` 现在描述的是 bootstrap 载体，不再是终态主线。
- `apps/android/local-host-progress.md` and `apps/android/local-host-handoff.md` should treat the old Android-native MVP as validated baseline context, not the new finish line. / `apps/android/local-host-progress.md` 和 `apps/android/local-host-handoff.md` 应把旧的 Android-native MVP 当作已验证基线，而不是新的终点。

## Correction Update / 纠偏更新

- The branch objective is now explicitly corrected back to the full packaged desktop environment, not a "selected slice" framing.
- Payload `0.6.0` adds a packaged `desktop/` stage that groups engine, environment, browser, tools, plugins, supervisor manifests, and one desktop profile descriptor into one cohesive APK bundle.
- `pod.desktop.materialize` now materializes that packaged bundle into `filesDir/openclaw/embedded-desktop-home/<version>/`, which turns "desktop environment inside the APK" into a real app-private home layout rather than only a status-map claim.
- The next gap is no longer "can the APK carry the bundle?" but "how much of that bundled environment is executable with boring replay proof on-device?"
