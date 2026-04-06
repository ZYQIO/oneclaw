# Android Desktop Runtime Mainline / Android 桌面 Runtime 主线

Purpose / 用途: redefine the Android mainline around integrating the full packaged desktop environment into the app, instead of stopping at the current helper-pod boundary. / 把 Android 主线重新定义为将完整打包的桌面环境整合进 app，而不是停在当前 helper pod 边界。
Branch / 分支: `android-desktop-runtime-mainline-20260403`
Last updated / 最后更新: April 6, 2026 / 2026 年 4 月 6 日

## Pivot / 主线切换

- The embedded-runtime pod is now the bootstrap carrier, not the destination. / embedded-runtime pod 现在是引导载体，不再是终点。
- The Android-native `Local Host` MVP, UI automation, and dedicated-device work now serve as the control plane and operating envelope for desktop-runtime integration. / Android 原生 `Local Host` MVP、UI 自动化和 dedicated-device 现在转为桌面 runtime 集成的控制平面和运行边界。
- Do not treat `pod.health`, `pod.manifest.describe`, `pod.workspace.scan`, and `pod.workspace.read` as the final product shape. / 不要再把 `pod.health`、`pod.manifest.describe`、`pod.workspace.scan` 与 `pod.workspace.read` 当成最终产品形态。

## Current Baseline / 当前基线

- The packaged pod payload is now `0.17.0` and includes a packaged `desktop/` stage plus the first allowlisted packaged plugin descriptor on top of the earlier `runtime/`, `toolkit/`, and `browser/` carrier stages; repo-side validation for this newer slice is complete, while the next real-device replay is still pending. / 当前打包 pod payload 已更新到 `0.17.0`，并在既有 `runtime/`、`toolkit/` 与 `browser/` carrier stages 之上保留打包 `desktop/` stage 与第一条 allowlisted packaged plugin descriptor；这条新切片的 repo 侧验证已经完成，但下一轮真机复跑仍待执行。
- `pod.runtime.execute` still carries the bounded execution lane, but it no longer stops at runtime-home hydration: once desktop home has been materialized it now replays `profiles/active-profile.json` plus packaged environment/supervisor manifests into replayable state artifacts, leaves explicit health-report plus restart-contract files under desktop-home state, exposes the first narrow allowlisted plugin replay through `pod.runtime.execute(taskId=plugin-allowlist-inspect)`, and now also writes structured `runtime-smoke-process-model.json`, `runtime-smoke-activation-contract.json`, `runtime-smoke-supervision-contract.json`, `runtime-smoke-observation-contract.json`, `runtime-smoke-recovery-contract.json`, `runtime-smoke-detached-launch-contract.json`, `runtime-smoke-supervisor-loop-contract.json`, `runtime-smoke-active-session-contract.json`, `runtime-smoke-active-session-validation.json`, and `runtime-smoke-active-session-device-proof.json` bootstrap artifacts that tie together desired state, observed state, session ID, health-report, restart-contract paths, the next supervisor action, lease semantics, heartbeat timing, explicit recovery actions, the bounded detached-launch handoff, the first supervisor-loop renewal contract, the first active-session continuity contract, the explicit device-proof checklist, and the exact bounded proof bundle expected from doctor plus smoke. `pod.browser.describe` still reports the first bounded browser-auth lane, and `pod.browser.auth.start` still reuses the app's existing OpenAI Codex OAuth browser flow instead of pretending Android already has a generic browser runtime. / `pod.runtime.execute` 仍然承载这条有边界执行通道，但它已不再止步于 runtime-home 水合：当 desktop home 已 materialize 后，它现在会把 `profiles/active-profile.json` 与打包 environment/supervisor manifests replay 成可复跑状态产物，在 desktop-home state 下留下显式 health-report 与 restart-contract 文件，通过 `pod.runtime.execute(taskId=plugin-allowlist-inspect)` 暴露第一条窄白名单 plugin replay，并进一步写出结构化的 `runtime-smoke-process-model.json`、`runtime-smoke-activation-contract.json`、`runtime-smoke-supervision-contract.json`、`runtime-smoke-observation-contract.json`、`runtime-smoke-recovery-contract.json`、`runtime-smoke-detached-launch-contract.json`、`runtime-smoke-supervisor-loop-contract.json`、`runtime-smoke-active-session-contract.json`、`runtime-smoke-active-session-validation.json` 与 `runtime-smoke-active-session-device-proof.json` bootstrap 工件，把 desired state、observed state、session ID、health-report、restart-contract 路径、下一步 supervisor action、lease 语义、heartbeat 时间、显式恢复动作、有边界 detached-launch handoff、第一份 supervisor-loop renewal contract、第一份 active-session continuity contract、明确的 device-proof checklist 与 doctor/smoke 所需的有边界证据包一并收拢起来。

- Build-time packaging, app-private extraction, checksum verification, and read-only pod invocation are already landed. / 构建期打包、app 私有目录解包、checksum 校验和只读 pod 调用已经落地。
- `pod.runtime.describe` is now the machine-readable status surface for this mainline and reports which desktop-runtime domains are landed, bootstrap-only, or still missing. / `pod.runtime.describe` 现在是这条主线的机器可读状态面，会直接报告哪些桌面 runtime 域已落地、仅是 bootstrap，或仍然缺失。
- The current pod payload still does not include a generic browser runtime, a full embedded execution engine, a long-lived app-private process supervisor, or open-ended plugin execution parity. / 当前 pod payload 仍然没有通用浏览器 runtime、完整嵌入执行引擎、长生命周期的 app 私有 process supervisor，或开放式插件执行对齐。

## Mainline Objective / 主线目标

Bring the full packaged desktop environment onto Android so the phone can host not only local chat and Android-native commands, but also a cohesive desktop-environment bundle that can be materialized inside app-private storage. / 把完整打包的桌面环境带到 Android，让手机不只承载本地聊天和 Android 原生命令，还能承载一套可 materialize 到 app 私有目录里的完整桌面环境 bundle。

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
- `engine`: partial bootstrap. `runtime-smoke` can now replay one packaged desktop profile and dependency/readiness contract on-device, but there is still no general embedded desktop execution engine. / `engine`：部分 bootstrap。`runtime-smoke` 现在已经能在设备上 replay 一条打包 desktop profile 与 dependency/readiness 契约，但仍然没有通用嵌入桌面执行引擎。
- `environment`: partial bootstrap. The bounded replay now leaves app-private state, health-report, restart-contract, process-model, activation-contract, supervision-contract, observation-contract, recovery-contract, detached-launch-contract, supervisor-loop-contract, active-session-contract, active-session-validation, and active-session-device-proof bootstrap evidence under desktop-home, but there is still no executed detached subprocess or verified live active session beyond those artifacts. / `environment`：部分 bootstrap。有边界 replay 现在已经会在 desktop-home 下留下 app 私有 state、health-report、restart-contract、process-model、activation-contract、supervision-contract、observation-contract、recovery-contract、detached-launch-contract、supervisor-loop-contract、active-session-contract、active-session-validation 与 active-session-device-proof bootstrap 证据，但在这些工件之外仍然没有真正执行中的 detached 子进程或经过验证的 live active session。
- `browser`: partial bootstrap. A single allowlisted external-browser auth lane is now packaged and has replayable on-device proof, but it is still far from generic browser runtime parity. / `browser`：部分 bootstrap。现在已经打包了一条单一白名单 external-browser auth lane，且已有可复跑的真机证据，但离通用浏览器 runtime 对齐仍然很远。
- `tools`: partial bootstrap. One packaged desktop tool lane now exists behind toolkit descriptors and command policy, and it already has repetitive on-device replay proof, but it is still a curated lane rather than general tool parity. / `tools`：部分 bootstrap。现在已经有一条通过 toolkit descriptor 和 command policy 封装的打包桌面工具通道，且已有持续真机复跑证据，但它仍然只是精选能力，不是通用工具对齐。
- `plugins`: partial bootstrap. One allowlisted packaged plugin lane now exists and replays on-device, but there is still no generic or open-ended plugin runtime. / `plugins`：部分 bootstrap。现在已经有一条 allowlisted packaged plugin lane，并且能在真机上复跑，但仍然没有通用或开放式插件 runtime。

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

- Status on this branch: the packaged `runtime/` stage plus `pod.runtime.execute(taskId=runtime-smoke)` still provide the first bounded execution carrier, and that same task now also replays the active desktop profile plus environment/supervisor manifests into `runtime-smoke-desktop-profile.json` artifacts. `pod.runtime.execute(taskId=tool-brief-inspect)` still proves the first packaged desktop tool contract on top of it. / 这条分支的现状：打包好的 `runtime/` stage 和 `pod.runtime.execute(taskId=runtime-smoke)` 仍然提供第一条有边界执行载体，而且同一任务现在也会把 active desktop profile 与 environment/supervisor manifests replay 成 `runtime-smoke-desktop-profile.json` 产物；`pod.runtime.execute(taskId=tool-brief-inspect)` 则继续在其上证明第一条打包桌面工具契约。

- Choose and package the smallest viable embedded execution engine. / 选出并打包最小可行的嵌入执行引擎。
- Prove it can run one bounded desktop-side task from app-private storage. / 证明它能从 app 私有目录执行一条有边界的桌面侧任务。
- Keep the payload build-time packaged and versioned. / 保持 payload 构建期打包且带版本。

### Phase 2. Runtime Environment / 运行环境

- Status on this branch: `pod.runtime.execute` now hydrates `filesDir/openclaw/embedded-runtime-home/<version>/` with config, logs, state, and work directories, the bounded desktop-home replay writes profile artifacts plus `runtime-smoke-health-report.json`, `runtime-smoke-restart-contract.json`, `runtime-smoke-process-model.json`, `runtime-smoke-activation-contract.json`, `runtime-smoke-supervision-contract.json`, `runtime-smoke-observation-contract.json`, `runtime-smoke-recovery-contract.json`, `runtime-smoke-detached-launch-contract.json`, `runtime-smoke-supervisor-loop-contract.json`, `runtime-smoke-active-session-contract.json`, `runtime-smoke-active-session-validation.json`, and `runtime-smoke-active-session-device-proof.json` into desktop-home state, and the first packaged tool lane already writes replayable results under `work/`. The next gap is no longer "should we bootstrap supervision?" or even "should we bootstrap a supervisor loop?" but "how to capture that bounded active-session-device-proof contract as one live detached session on-device." / 这条分支的现状：`pod.runtime.execute` 已经会把 `filesDir/openclaw/embedded-runtime-home/<version>/` 水合成带有 config、logs、state 和 work 目录的 runtime home，有边界的 desktop-home replay 现在也会把 profile 产物以及 `runtime-smoke-health-report.json`、`runtime-smoke-restart-contract.json`、`runtime-smoke-process-model.json`、`runtime-smoke-activation-contract.json`、`runtime-smoke-supervision-contract.json`、`runtime-smoke-observation-contract.json`、`runtime-smoke-recovery-contract.json`、`runtime-smoke-detached-launch-contract.json`、`runtime-smoke-supervisor-loop-contract.json`、`runtime-smoke-active-session-contract.json`、`runtime-smoke-active-session-validation.json` 与 `runtime-smoke-active-session-device-proof.json` 写进 desktop-home state，而第一条打包工具通道已经会把可复跑结果写入 `work/`。因此下一步缺口已经不再是“要不要先 bootstrap supervision”，甚至也不再是“要不要先 bootstrap supervisor loop”，而是“如何把这条有边界 active-session-device-proof contract 真正捕获成设备内的 live detached session”。

- Add an app-private runtime environment layout, config bundle, lifecycle supervision, and health/log surfaces. / 加入 app 私有 runtime 环境目录、配置 bundle、生命周期监督和 health/log 面。
- Make restart and stale-build diagnostics explicit. / 让重启和 stale-build 诊断显式化。

### Phase 3. Browser Lane / 浏览器通道

- Status on this branch: the browser-lane smoke now auto-runs `pod.desktop.materialize`, replays the bounded browser-auth lane, and leaves replayable device evidence while the runtime/tool/plugin lanes have already been replayed from the same packaged bundle. / 这条分支的现状：browser-lane smoke 现在会自动执行 `pod.desktop.materialize`、复跑这条有边界 browser-auth lane，并在 runtime / tool / plugin lanes 已经从同一份打包 bundle 复跑之后，留下可复查的真机证据。
- Add only the browser capability that the curated desktop slice truly needs. / 只加入精选桌面切片真正需要的浏览器能力。
- Keep it bounded and replayable. / 保持有边界且可复跑。

### Phase 4. Tools And Plugins / 工具与插件

- Status on this branch: one packaged desktop tool lane plus one narrow allowlisted plugin lane are both landed, the refreshed real-device replay now already reaches `process_runtime_active_session_device_proof_bootstrapped` on `0.17.0`, and the current browser-lane smoke also reruns `runtime-smoke` after browser replay is ready so the branch now has explicit on-device proof for `longLivedProcessReady=true`, `processStatus=standby`, `supervisionStatus=active`, and `activeSessionStatus=ready` before live proof. The next gap is therefore no longer "whether to add a plugin lane", "whether to bootstrap runtime activation", "whether to bootstrap supervision", "whether to bootstrap observation", "whether to bootstrap recovery", "whether to bootstrap detached launch", or even "whether to bootstrap a supervisor loop" but how to capture that bounded handoff as a live active session on-device. / 这条分支的现状：一条打包桌面工具通道和一条窄白名单 plugin lane 都已落地，更新后的真机复跑也已经在 `0.17.0` 上达到 `process_runtime_active_session_device_proof_bootstrapped`，而当前 browser-lane smoke 还会在 browser replay 就绪后补跑一次 `runtime-smoke`，因此在 live proof 之前，这条分支已经拿到了 `longLivedProcessReady=true`、`processStatus=standby`、`supervisionStatus=active` 与 `activeSessionStatus=ready` 的显式真机证据。因此下一步缺口已经不再是“要不要加 plugin lane”“要不要先 bootstrap runtime activation”“要不要先 bootstrap supervision”“要不要先 bootstrap observation”“要不要先 bootstrap recovery”“要不要先 bootstrap detached launch”，甚至也不再是“要不要先 bootstrap supervisor loop”，而是如何把这条有边界 handoff 真正捕获成设备内 live active session。
- Package one curated desktop tool lane first. / 先打包一条精选桌面工具通道。
- Add plugin/runtime surfaces only when the curated tool lane proves the need. / 只有在精选工具通道证明必要性之后，再补插件或 runtime 面。

## Guardrails / 护栏

- No unrestricted remote shell. / 不做无限制远程 shell。
- No Play-first promise for a self-mutating desktop runtime. / 不承诺面向 Play 的自演化桌面 runtime。
- No reopening of the old assumption that the helper quartet is already enough. / 不再回到“helper quartet 已经足够”的旧假设。
- Do not widen browser, tools, or plugins beyond the current allowlisted lanes before the engine carrier and runtime environment become a real long-lived process model. / 在引擎载体和 runtime 环境深化成真正的长生命周期 process model 之前，不要把 browser、tools 或 plugins 扩到当前 allowlisted lanes 之外。

## Working Rule / 工作规则

- `apps/android/local-host-desktop-runtime-feasibility.md` stays as the feasibility input. / `apps/android/local-host-desktop-runtime-feasibility.md` 继续作为可行性输入文档。
- `apps/android/local-host-desktop-runtime-checkpoint-20260403.md` is the fastest fresh-session entrypoint and should be read before replaying the longer Android history. / `apps/android/local-host-desktop-runtime-checkpoint-20260403.md` 是给新会话看的最快入口，应该先读它，再回放更长的 Android 历史。
- `apps/android/local-host-embedded-runtime-pod-plan.md` now describes the bootstrap carrier, not the end-state mainline. / `apps/android/local-host-embedded-runtime-pod-plan.md` 现在描述的是 bootstrap 载体，不再是终态主线。
- `apps/android/local-host-progress.md` and `apps/android/local-host-handoff.md` should treat the old Android-native MVP as validated baseline context, not the new finish line. / `apps/android/local-host-progress.md` 和 `apps/android/local-host-handoff.md` 应把旧的 Android-native MVP 当作已验证基线，而不是新的终点。

## Correction Update / 纠偏更新

- The branch objective is now explicitly corrected back to the full packaged desktop environment, not a "selected slice" framing.
- Payload `0.17.0` now carries a packaged `desktop/` stage that groups engine, environment, browser, tools, plugins, supervisor manifests, one desktop profile descriptor, and the first allowlisted plugin descriptor into one cohesive APK bundle.
- `pod.desktop.materialize` now materializes that packaged bundle into `filesDir/openclaw/embedded-desktop-home/<version>/`, which turns "desktop environment inside the APK" into a real app-private home layout rather than only a status-map claim.
- `runtime-smoke` now replays the materialized desktop profile and packaged environment/supervisor manifests into device-local artifacts, and also leaves explicit health-report, restart-contract, process-model, activation-contract, supervision-contract, observation-contract, recovery-contract, detached-launch-contract, supervisor-loop-contract, and active-session-contract bootstrap state.
- `plugin-allowlist-inspect` now replays the first packaged allowlisted plugin descriptor on-device, so the next gap is no longer "should we attach a plugin lane at all?" but "how much of that bundled environment should become a stronger active runtime session beyond the current replay artifacts?"
