# OneClaw 接手路线图

## 目的

本文档定义了如果现在由我接手 OneClaw 项目，我会采用的实施路线。

这份路线图围绕用户当前最明确的目标来制定：
- 以 Android 为第一交付目标
- 让远程控制真正运行在 Android 手机上
- 让类似 OneClaw 的 agent 功能真正运行在 Android 手机上
- GPT 系列模型接入优先围绕 Codex 授权来规划
- 先规划，再写代码

这是一份接手与执行路线图，不是 RFC。它不替代功能 RFC，而是定义阶段顺序、优先级、验收门槛和 fallback 决策。

## 指导性决策

### 1. 先把 Android 主链路做通

当前阶段的重点不是跨平台通用性，而是真正在 Android 真机上把整条链路跑通。

### 2. 把远程控制当成一个独立子系统，而不是顺手加的功能

远程控制已经不再是一个小功能，它已经横跨：
- `:app`
- `:remote-host`
- `:remote-core`
- `remote-broker`
- `remote-console-web`

这意味着它必须被当成一条一等子系统来规划、测试和加固。

### 3. root 主机远控优先于非 root 兼容模式

从当前代码和文档来看，root 路线最完整，也最接近真正可用。非 root 路线目前仍是脚手架，不应该阻塞第一版可用系统。

### 4. GPT 接入按 Codex 授权方向规划，但要先验证集成边界

当前开发环境里的本地 Codex CLI 同时暴露了：
- `codex login --device-auth`
- `codex login --with-api-key`

这说明 Codex 授权在当前工具链里是真实存在的认证模式。但这并不自动等于 Android App 可以无缝复用 CLI 登录流程，所以必须先做技术探针。

因此这里的规则是：
- Codex 授权是目标方向
- 需要先做技术探针，确认它能否直接复用，还是必须桥接
- App 内的认证层必须保持可替换

### 5. 每个阶段都要冻结范围

每个阶段都必须有清晰的退出条件，不能在执行过程中不断吞入邻近需求。

## 当前状态摘要

基于当前仓库状态：
- OneClaw 本体已经是可运行的 Android App，具备 provider、tool、memory、skill、scheduled task、bridge、session 等能力。
- 远程控制基础设施已经接入，包括：
  - `:app` 里的人工远控页面
  - 给 AI agent 调用的 `remote` 工具组
  - 独立的 Android 被控端 `:remote-host`
  - 公共模块 `:remote-core`
  - Node.js 的 `remote-broker`
  - 浏览器控制台 `remote-console-web`
- root 路线的远程控制基础能力已经具备雏形。
- 非 root 路线仍然只是脚手架，还没有端到端完成。
- 当前环境缺少 Java，因此 Android 构建与测试在这里无法完成验证。

## 分阶段路线

## Phase 0：项目基线恢复

### 目标

建立一个稳定起点，让项目能够被构建、安装、联调，并且可以基于事实来判断现状。

### 任务

- 补齐并验证 Java 17、Android SDK、Gradle、模拟器环境
- 跑通当前 Android 构建与测试命令
- 验证以下最小启动链路：
  - `:app`
  - `:remote-host`
  - `remote-broker`
- 对齐当前代码和旧阶段说明之间的文档偏差
- 产出一份“当前已知可用基线”报告

### 验收标准

- `./gradlew test` 可以成功运行，或者能产出明确的修复清单
- `./gradlew assembleDebug` 成功
- `./gradlew :remote-host:assembleDebug` 成功
- `remote-broker` 可以启动并通过健康检查
- 当前限制都以代码和可验证事实为依据，而不是猜测

### 输出物

- 构建基线报告
- 环境搭建说明
- 校正后的实现状态快照

## Phase 1：Codex 授权技术探针

### 目标

在“Codex 授权是首选方向”的前提下，确认 GPT 系列接入在 Android 架构中的可落地方式。

### 需要回答的问题

- Android 能否直接复用 Codex 的 device auth？
- 如果不能直接复用，是否可以通过本地或外部桥接方式安全接入？
- 授权完成后，App 需要本地持久化哪些状态？
- 续期、失效和登出如何处理？
- 如果 Codex 授权短期内无法直接嵌入 App，fallback 是什么？

### 任务

- 检查本地 Codex 登录流程与可观察的授权产物
- 设计面向 App 的 `AuthProvider` 抽象
- 至少比较以下方案：
  - App 内嵌 device-auth 流程
  - 通过外部 Codex 授权环境桥接
  - 退化为 provider API key 认证
- 定义 App 所需的最小 token / session 模型
- 定义本地存储与刷新机制的安全约束

### 验收标准

- 有一份明确的集成决策结论：
  - `direct`
  - `bridged`
  - `deferred with fallback`
- 在开始 GPT 集成编码之前，认证接口已经先被设计好
- model/provider 代码不依赖单一硬编码认证路径

### 输出物

- Codex 授权技术探针说明
- 认证抽象设计
- 安全与 fallback 决策记录

## Phase 2：root 远控 MVP 加固

### 目标

把当前“远程控制基础版”变成真正可用的 rooted-host Android MVP。

### 范围

本阶段包含：
- broker 连接
- 设备发现
- 配对
- session 打开 / 关闭
- 截图刷新
- 点击 / 滑动 / 文本输入 / 按键 / Home / Back
- 沙箱允许范围内的文件上传 / 下载
- AI agent 访问 `remote` 工具组

本阶段不包含：
- 非 root 无人值守模式
- 真正的视频流
- 云端多租户产品化架构

### 任务

- 在真实 Android rooted-host 设备上验证完整执行
- 加固 broker 断连、session 超时等错误处理
- 改进人工 UI 和 tool 响应里的远控状态可见性
- 补齐 remote repository 和 remote tools 的测试
- 增加 rooted-host 真实使用场景的人工验证流程

### 验收标准

- rooted Android 主机可以完成端到端配对与控制
- 截图与输入命令在重复会话中稳定工作
- AI agent 的远控工具调用能返回可执行、可理解的结果
- 各种失败路径都能给出明确的人类可读错误

### 输出物

- rooted-host 远控 MVP
- 加固后的测试与人工验证报告

## Phase 3：OneClaw 与远控子系统整合完成

### 目标

让远程控制在 OneClaw 里看起来像原生能力，而不是后来拼接进去的附属功能。

### 任务

- 让人工远控 UI 和 AI tool 调用共享统一的 session 模型
- 明确 agent 在什么条件下允许控制设备
- 为高影响远控动作增加 guardrail 和确认机制
- 改进 chat 与 remote control 之间的导航与状态恢复
- 让远控执行结果可回溯、可检查

### 验收标准

- 人工控制和 AI 控制共享一致的设备 / 会话状态
- 用户授权边界和设备控制边界是明确可见的
- 调试和复盘时可以追踪远控动作结果

### 输出物

- OneClaw 内部一体化的远控体验

## Phase 4：通过认证层完成 GPT 接入

### 目标

使用 Phase 1 确认过的认证策略，把 GPT 系列模型接入到 Android App。

### 任务

- 引入选定的认证提供器实现
- 让 OpenAI / GPT 请求统一经过认证抽象层
- 保持 model/provider 接口能适配未来认证变更
- 验证流式响应、tool calling 和错误处理
- 确认它与现有 provider 管理 UI 的交互关系

### 验收标准

- GPT 系列请求可以通过批准的认证路径成功工作
- 现有 chat、tool loop、streaming 不被破坏
- 认证失败和重新授权流程对用户是可见且可恢复的

### 输出物

- 与 Codex 授权目标对齐的 Android GPT 集成

## Phase 5：非 root 兼容路径

### 目标

让远程控制在非 root Android 主机上也具备可用性。

### 任务

- 实现 MediaProjection 授权流
- 实现基于 AccessibilityService 的手势和输入执行
- 定义兼容模式下支持的能力边界
- 在 host UI 和 controller UI 中明确显示能力差异
- 为兼容模式补充测试与人工验证

### 验收标准

- 非 root 主机可以完成支持范围内的远控动作
- 不支持的动作会明确失败，而不是静默无效
- capability 上报和真实行为一致

### 输出物

- 非 root 兼容版本

## 大规模编码前必须先完成的架构工作

在恢复主要实现工作之前，以下接口应被视为设计锚点：

- `AuthProvider`
  - 作用：抽象 Codex 授权、API key 认证和未来其他认证方式
- `RemoteSessionManager`
  - 作用：统一人工 UI 和 AI tool 调用的远控 session 状态
- `RemoteCapabilityPolicy`
  - 作用：区分 root 和 compatibility 模式下允许的动作
- `RemoteAuditLog`
  - 作用：为设备控制操作提供可追踪性

它们不一定要一次性全部实现，但必须先影响边界设计。

## 各阶段测试策略

## 基线阶段
- 构建检查
- JVM 测试
- Instrumented 测试
- broker 健康检查

## 远控 MVP 阶段
- rooted Android 真机验证
- session 打开 / 关闭重复测试
- 截图轮询稳定性
- 输入命令可靠性
- tool-call 集成验证

## GPT 集成阶段
- 认证成功路径
- 认证过期路径
- 流式返回路径
- tool calling 路径
- 重新授权路径

## 非 root 阶段
- MediaProjection 权限流
- Accessibility 权限流
- 手势分发验证
- capability 不匹配处理

## 风险清单

### 高风险

- Codex 授权可能需要桥接，而不是直接嵌入 Android App
- rooted-host 的行为在不同设备上可能差异很大
- 当前环境尚不能直接验证 Android 构建

### 中风险

- broker 当前只在内存中保存状态，重启后会丢失
- 截图轮询的交互体验可能无法满足部分场景
- 如果不尽早统一 session 逻辑，人工 UI 和 AI tool 流程可能逐渐漂移

### 低风险

- 文档偏差可以在建立基线后逐步修正

## Fallback 规则

如果 Phase 1 证明 Codex 授权短期内无法安全嵌入 App：
- 依然保留 Codex 授权作为长期首选方向
- 继续完成认证抽象层设计
- 允许临时 fallback 认证提供器用于开发与验证
- 不让 GPT 接入与单一认证方案强绑定

如果非 root 兼容阶段延期：
- 优先交付 rooted-host MVP
- 明确将 compatibility mode 标记为受限或实验性

## 立刻开始时的下一步动作

如果现在就接手执行，我会立即做这几件事：

1. 恢复构建能力并跑通当前 Android 命令
2. 产出项目基线报告
3. 进行 Codex 授权技术探针
4. 冻结认证抽象层决策
5. 开始 rooted remote-control 加固

## 成功定义

当以下条件全部满足时，说明项目进入了正确轨道：
- OneClaw 能稳定运行在 Android 手机上
- 远程控制能在 Android rooted-host 上完成端到端工作
- AI agent 能安全、可预测地调用远控工具
- GPT 接入通过可替换认证层完成，并与 Codex 授权目标保持一致
- 非 root 支持成为可排期扩展，而不是阻塞第一版可用系统的前置条件
