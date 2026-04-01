# Android Embedded Runtime Pod Plan / Android 嵌入式运行时 Pod 方案

Purpose / 用途: define the first shippable slice for packaging a desktop-like runtime into OpenClaw Android without derailing the current Android Local Host roadmap. / 定义“把类桌面运行时封装进 OpenClaw Android”时第一条可交付切片，且不打断当前 Android Local Host 主线。

Last updated / 最后更新: April 1, 2026 / 2026 年 4 月 1 日

## Decision / 结论

- The first viable slice is **not** "the full desktop runtime inside the APK." / 第一条可行切片**不是**“把整套桌面 runtime 直接塞进 APK”。
- The first viable slice is a **build-time packaged, app-private, read-only helper pod** that carries only deterministic helper code and data. / 第一条可行切片应是一个**构建期打包、放在 app 私有目录、只读执行**的 helper pod，只携带确定性 helper 代码和数据。
- This pod should behave like a bounded capability bundle for the current Android host, not like a second hidden product. / 这个 pod 应该像当前 Android host 的有边界能力包，而不是 APK 里的第二个隐藏产品。
- The pod should be useful even if we never add browser parity, shell parity, or full plugin parity. / 即使我们最终不补 browser 对齐、shell 对齐或完整 plugin 对齐，这个 pod 也应该仍然有价值。

## First Slice / 第一切片

Project name / 项目名: `Embedded Runtime Pod v0`

The first slice should prove four things at once: / 第一切片需要同时证明四件事：

- The app can ship runtime assets with the APK/AAB instead of downloading executable code later. / App 可以把运行时资产随 APK/AAB 一起交付，而不是之后再下载可执行代码。
- The app can extract those assets into a versioned app-private directory on first launch. / App 可以在首次启动时把这些资产解压到带版本号的 app 私有目录。
- The app can verify integrity before running the pod. / App 可以在运行 pod 前完成完整性校验。
- The app can execute one bounded helper workflow from that pod and return a structured result. / App 可以从这个 pod 执行一条有边界的 helper 工作流，并返回结构化结果。

Recommended first helper workflow / 推荐的首条 helper 工作流:

- `pod.health`: verify manifest, version, checksum, and local execution availability. / `pod.health`：校验 manifest、版本、校验和与本地执行可用性。
- Optional second helper: `pod.workspace.scan` over the app-private workspace, with no network and no browser. / 可选第二个 helper：对 app-private workspace 做 `pod.workspace.scan`，不访问网络、不启动浏览器。

## What This Slice Is / 这条切片是什么

- A deterministic helper pod that can be packaged, unpacked, verified, and invoked locally. / 一个可被打包、解包、校验并在本地调用的确定性 helper pod。
- A bounded execution island that supports the current Android host with offline or local-only work. / 一个有边界的执行孤岛，优先服务当前 Android host 的离线或本地工作。
- A place to reuse desktop-side helper logic without importing the full desktop control plane. / 一个复用桌面侧 helper 逻辑的地方，但不导入完整桌面控制平面。

## What This Slice Is Not / 这条切片不是什么

- Not the full desktop `Gateway/CLI` runtime. / 不是完整桌面 `Gateway/CLI` runtime。
- Not a browser runtime. / 不是浏览器 runtime。
- Not a shell runtime for arbitrary commands. / 不是可以任意执行命令的 shell runtime。
- Not a plugin host with open-ended third-party execution. / 不是开放式第三方执行的 plugin host。
- Not a self-updating executable runtime fetched from OpenClaw servers after install. / 不是安装后再从 OpenClaw 服务器自更新的可执行 runtime。
- Not a Play-distributed path that relies on downloading new executable code outside Play. / 不是依赖在 Play 之外下载新可执行代码的 Play 分发路径。

## Target Capabilities / 目标能力

The first pod should support only a narrow capability set: / 第一版 pod 只应支持一小组能力：

- Read a pod manifest and expose version / checksum / feature flags. / 读取 pod manifest，并暴露版本 / 校验和 / 功能开关。
- Unpack prebuilt assets into `filesDir` or an equivalent app-private directory. / 将预构建资产解包到 `filesDir` 或等价的 app 私有目录。
- Run one or two pure helper commands with no browser dependency. / 运行一到两个不依赖浏览器的纯 helper 命令。
- Return structured JSON results so the Android app can render status and failures consistently. / 返回结构化 JSON 结果，方便 Android app 统一渲染状态和失败原因。
- Reuse the current app-private workspace as input or output, but never as an unbounded shell. / 复用当前 app-private workspace 作为输入或输出，但绝不能把它升级成无限制 shell。

Suggested command contract / 建议的命令契约:

- `pod.health`
- `pod.workspace.scan`
- `pod.manifest.describe`

If we need a second command later, it should still be offline and deterministic. / 如果后面还要补第二条命令，也应保持离线和确定性。

## Directory And Build Shape / 目录与构建形态

### Suggested source layout / 建议源码布局

- `apps/android/embedded-runtime-pod/` for source manifests, helper metadata, and packaging inputs. / `apps/android/embedded-runtime-pod/` 存放 source manifest、helper 元数据和打包输入。
- `apps/android/app/src/main/assets/embedded-runtime-pod/` for generated packaged assets. / `apps/android/app/src/main/assets/embedded-runtime-pod/` 存放生成后的打包资产。
- `apps/android/app/src/main/java/.../embeddedpod/` for the Android-side extractor, verifier, and invoker adapter. / `apps/android/app/src/main/java/.../embeddedpod/` 存放 Android 侧的解包器、校验器和调用适配器。

### Suggested runtime location / 建议运行时位置

- Extract to a versioned app-private directory such as `filesDir/embedded-runtime-pod/<version>/`. / 解压到带版本号的 app 私有目录，例如 `filesDir/embedded-runtime-pod/<version>/`。
- Keep the extracted payload read-only after verification. / 验证完成后保持解包产物只读。
- Cache only what is needed for the current installed version. / 只缓存当前安装版本所需的内容。

### Suggested build rule / 建议构建规则

- Package the pod at build time. / 在构建期完成打包。
- Verify checksum before extraction and before every first use. / 在解压前以及首次使用前都做校验和验证。
- Fail closed if the manifest or checksum does not match. / 如果 manifest 或校验和不匹配，则默认失败关闭。
- Never auto-fetch executable pod updates from a server in this first slice. / 第一切片中绝不从服务器自动拉取可执行 pod 更新。

## Minimal Validation / 最小验证

The first acceptance test should be deliberately small. / 第一条验收测试应刻意保持很小。

1. Build the APK/AAB with the pod assets included. / 构建包含 pod 资产的 APK/AAB。
2. Install on a real Android phone. / 安装到真实 Android 手机。
3. Launch the app and confirm the pod is extracted into the expected app-private path. / 启动 app，确认 pod 已解压到预期的 app 私有路径。
4. Run `pod.health` and verify manifest, version, and checksum. / 运行 `pod.health`，验证 manifest、版本和校验和。
5. Run one deterministic helper workflow and verify a structured result reaches the UI or remote status surface. / 运行一条确定性的 helper 工作流，并验证结构化结果能到达 UI 或远控状态面。
6. Reopen the app and confirm the pod is reused without changing the verified contents. / 重新打开 app，确认 pod 会复用且校验后的内容不变。

Suggested pass criteria / 建议通过标准:

- The pod can be extracted and verified on a real phone. / Pod 可以在真机上解包并校验。
- The helper workflow completes without browser or shell dependencies. / Helper 工作流可在不依赖浏览器或 shell 的情况下完成。
- The app can clearly report pod health and failure reasons. / App 可以清楚报告 pod 健康状态和失败原因。

## Risk Register / 风险清单

- Play policy risk: if we later try to download executable code from outside Play, the packaging path becomes much harder to ship publicly. / Play 策略风险：如果后续试图在 Play 之外下载可执行代码，公开分发路径会变得更难。
- Security risk: any extracted executable payload must be checksum-verified and read-only after install. / 安全风险：任何解包出的可执行载荷都必须校验和验证，且安装后只读。
- Lifecycle risk: a heavier runtime pod can inflate startup time, storage use, and recovery complexity. / 生命周期风险：更重的 runtime pod 会增加启动时延、存储占用和恢复复杂度。
- Scope risk: once browser or shell parity sneaks in, the pod can turn into a second product rather than a helper bundle. / 范围风险：一旦 browser 或 shell 对齐悄悄混进来，pod 就可能变成第二个产品，而不是 helper bundle。
- Maintenance risk: if the pod duplicates too much of the desktop stack, we will pay for two runtimes instead of one. / 维护风险：如果 pod 复制了太多桌面栈，我们会为两套 runtime 付维护成本。

## Phased Milestones / 分阶段里程碑

### Phase 0. Boundary Freeze / 边界冻结

- Freeze the first slice to `pod.health` plus one bounded helper workflow. / 把第一切片冻结为 `pod.health` 加一条有边界的 helper 工作流。
- Freeze the packaging path as build-time only. / 把打包路径冻结为仅构建期完成。
- Freeze the distribution path as internal or sideload only for the first spike. / 把分发路径冻结为第一轮只走 internal 或 sideload。

Exit criteria / 退出标准:

- We can name the first slice without mentioning browser parity, shell parity, or plugin parity. / 我们可以在不提 browser 对齐、shell 对齐或 plugin 对齐的前提下说清第一切片。

### Phase 1. Packaging Spike / 打包 Spike

- Add the pod assets to the Android build. / 把 pod 资产接进 Android 构建。
- Extract to versioned app-private storage. / 解压到带版本号的 app 私有目录。
- Verify checksum and manifest. / 校验 checksum 与 manifest。

Exit criteria / 退出标准:

- The phone can install, extract, and verify the pod with no manual file copying. / 手机可以安装、解包并校验 pod，而不需要手工拷文件。

### Phase 2. One Helper Workflow / 一条 Helper 工作流

- Land one deterministic helper command such as `pod.health`. / 落地一条确定性 helper 命令，例如 `pod.health`。
- Surface its result in the app and the local-host status snapshot. / 将结果同时展示在 app 和 local-host status 快照里。

Exit criteria / 退出标准:

- The command can be replayed on a real device and returns the same structured shape each time. / 这条命令可以在真机上复跑，并且每次都返回同样的结构化结果。

### Phase 3. Narrow Expansion / 有限扩展

- Add one more offline helper only if it clearly reduces duplicated logic. / 只有在明显减少重复逻辑时，才再加一个离线 helper。
- Keep every new command deterministic and app-private. / 保持每条新命令都确定性、app 私有。

Exit criteria / 退出标准:

- The pod is useful enough to keep, but still small enough to explain in one paragraph. / 这个 pod 已经足够有用值得保留，但仍然小到可以用一段话解释清楚。

## Recommended Main-Thread Next Step / 主线程下一步建议

- Keep the current phone-control and dedicated-device tracks moving as the primary product work. / 继续把当前 phone-control 和 dedicated-device 作为主产品工作推进。
- Treat the pod as a parallel spike, not a blocker. / 把这个 pod 视为并行 spike，而不是 blocker。
- First implement the packaging/extraction/verifier path, then decide whether `pod.health` is the right first helper or whether the app-private workspace needs a different offline command first. / 先实现打包 / 解包 / 校验路径，再决定 `pod.health` 是否真的是第一条 helper，还是 app-private workspace 需要先有别的离线命令。
- Do not move to browser or shell parity until the first slice is already boringly reliable on a real phone. / 在第一切片还没在真机上变得“无聊地可靠”之前，不要往 browser 或 shell 对齐上走。
