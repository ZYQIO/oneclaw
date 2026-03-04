# 记忆策展系统

## 功能信息
- **功能 ID**: FEAT-052
- **创建日期**: 2026-03-04
- **最后更新**: 2026-03-04
- **状态**: 草稿
- **优先级**: P1 (应该有)
- **负责人**: TBD
- **相关 RFC**: [RFC-052 (记忆策展系统)](../../rfc/features/RFC-052-memory-curation-zh.md)
- **扩展**: [FEAT-049 (记忆质量改进)](FEAT-049-memory-quality-zh.md), [FEAT-013 (记忆系统)](FEAT-013-memory-zh.md)

## 用户故事

**作为** OneClaw 的用户，
**我希望** 长期记忆 (MEMORY.md) 只包含关于我的高质量、持久的事实，
**以便** AI 的上下文保持干净、相关，不被过期事件、操作细节和冗余条目污染。

### 痛点（当前状态）

当前记忆系统有两条写入路径都直接追加到 MEMORY.md，导致质量随时间退化：

1. **DailyLogWriter 推送噪音** -- 当会话结束时，`DailyLogWriter` 从对话中提取"长期事实"并通过 `appendMemory()` 直接追加到 MEMORY.md。这会产生：
   - 脱离任何 section 结构的孤儿条目（例如文件底部的 `*   User's name is.`，与 User Profile 中已有的条目重复）
   - 临时信息被提升为永久记忆（例如"用户今天清理了 Gmail 收件箱"）
   - 属于每日日志而非长期记忆的操作细节（例如要删除的具体邮箱地址）

2. **没有策展流程** -- 没有机制定期审查 MEMORY.md 并移除过期、冗余或低价值的条目。现有的压缩 (FEAT-049) 仅在文件超过 3,000 字符时触发，依赖单次 LLM 调用，可能无法发现所有问题。

3. **Workflow section 膨胀** -- "Workflow" section 积累了详细的操作配置（邮件过滤规则、标签 ID、技能版本号）等不应该出现在长期记忆中的内容。这些细节变化频繁，应该存在工具专属配置或每日日志中。

4. **过期的时间性条目持续存在** -- 已过期的截止日期、已完成的面试和其他时间限定信息无限期地留在 MEMORY.md 中（例如 3 月 4 日仍然保留着"3 月 2 日星期二 8:30 AM 的候选人面试"）。

### 典型场景

1. 用户进行了关于清理 Gmail 的对话。DailyLogWriter 提取"用户定期清理 Gmail"作为长期事实并追加到 MEMORY.md。但这已经在每日日志中记录，并可通过 HybridSearchEngine 搜索 -- 不需要在 MEMORY.md 中重复。

2. 用户有一个 3 月 2 日的工作面试。3 月 2 日之后，这条记录在长期记忆中毫无价值，但会无限期存在。

3. 用户的 Gmail 清理技能从 v1.3 更新到 v1.4，版本号被保存到 MEMORY.md。下周变成 v1.5 -- 现在 MEMORY.md 中有过时信息。

4. 用户连续几天完全通过 Telegram bridge 交互，没有打开 app。由于策展依赖 app 打开/生命周期事件，因此不会运行。

## 功能描述

### 概述

此功能引入 **记忆策展系统**，重构长期记忆的维护方式：

1. **将 DailyLogWriter 与 MEMORY.md 解耦** -- DailyLogWriter 只写入每日日志，不再向 MEMORY.md 推送事实。

2. **引入 MemoryCurator** -- 新组件，每天通过 WorkManager 运行一次，审查最近的每日日志与当前 MEMORY.md，进行高质量、审慎的更新。

3. **重命名 Workflow -> Habits/Routines** -- 明确该 section 存储行为模式，而非操作配置。

4. **加强压缩提示词** -- 添加删除过期时间条目和限制操作细节的具体规则。

5. **增强 SaveMemoryTool 提示词** -- 强调每日日志级别的信息不应重复到 MEMORY.md。

6. **用户可配置的策展时间** -- 在 Memory 界面显示策展时间并允许用户更改。

7. **每日日志合并整理** -- 在每日策展时，将昨天碎片化的每日日志合并为一份连贯的摘要。

### 详细规格

#### 变更 1: DailyLogWriter 解耦

从 `DailyLogWriter.writeDailyLog()` 中移除"长期事实"提取和 MEMORY.md 追加。简化摘要提示词，只产生每日摘要（没有"## Long-term Facts" section）。每日日志仍然是短期情景记忆的来源，可通过 HybridSearchEngine 搜索，并通过 MemoryInjector 的"相关记忆"部分注入系统提示。

#### 变更 2: MemoryCurator（新组件）

新的 `MemoryCurator` 类通过 Android WorkManager（`MemoryCurationWorker`）每天运行一次。它：

1. 读取最近 N 天的每日日志（默认：3 天）
2. 读取当前 MEMORY.md 内容
3. 将两者发送给 LLM，附带精心设计的策展提示词
4. LLM 决定是否需要更新：
   - 如果多天日志中出现了新的长期值得记录的事实，添加它们
   - 如果现有条目被最新信息否定，更新它们
   - 如果条目已过期（日期已过去），删除它们
   - 如果不需要变更，返回哨兵值（`NO_CHANGES`）
5. 如果需要变更，用策展后的内容覆盖 MEMORY.md（git 历史提供安全网）

**策展提示词设计原则：**
- 只提升在 2+ 天日志中反复出现的事实（模式确认）
- 永远不添加一次性事件或临时观察
- 删除日期已过的时间性条目
- Habits/Routines section：只存行为模式，不存配置细节
- 优先更新现有条目而非添加新的矛盾条目
- 如果不需要变更就直接说明（避免不必要的重写）

#### 变更 3: 重命名 Workflow -> Habits/Routines

将 `MemorySections.STANDARD_SECTIONS` 中的标准 section 名称从 "Workflow" 改为 "Habits/Routines"。压缩和策展提示词明确指示 LLM 在此 section 中只存储习惯性行为（例如"每天晚上 11 点运行 Gmail 清理"），不存操作细节（例如具体邮箱地址、标签 ID、技能版本号）。

#### 变更 4: 加强压缩提示词

在 `MemoryCompactor` 的压缩提示词中添加以下规则：
- 删除所有包含已过去日期的条目（过期事件、已完成的截止日期）
- Habits/Routines section：每条必须是单行行为模式，不含配置参数
- 每个 section 最多 10 条以防止无限增长
- 如果某 section 压缩后为空，则整个移除

#### 变更 5: 增强 SaveMemoryTool 提示词

在"不要保存"列表中添加：
- 已在每日日志中记录的信息（短期情景记忆由单独机制处理）
- 操作配置细节（邮箱地址、标签 ID、API 端点、版本号）
- 包含具体日期的特定计划事件（这些会过期并成为噪音）

#### 变更 6: 用户可配置的策展时间

在 Memory 界面中添加设置项：
- 显示当前策展时间（例如"记忆策展每天凌晨 3:00 运行"）
- 允许用户通过时间选择器对话框选择不同时间
- 将偏好存储在 SharedPreferences 中
- 时间更改时重新注册 WorkManager 定期任务

默认策展时间：**本地时间凌晨 3:00**。

#### 变更 7: 每日日志合并整理

在每日策展过程中，MemoryCurator 还会合并整理昨天的每日日志。一天之内，`DailyLogWriter` 在每次会话结束或 app 进入后台时都会追加一段新的摘要块。这导致每日日志碎片化，包含多个 `---` 分隔的段落，话题可能重叠。

合并整理步骤：
1. 读取昨天的每日日志文件
2. 如果包含多个摘要块（2+ 个 `---` 分隔的段落），将它们发送给 LLM 合并为一份连贯的每日摘要
3. 用合并后的精简版覆盖昨天的每日日志（git 历史保留原始碎片版本）
4. 为合并后的内容重建搜索索引
5. 不碰今天的每日日志（今天可能还会有新条目）
6. 不碰更早的每日日志（那些在各自的日子已经整理过了）

这样做是安全的，因为：
- 原始的逐条消息数据存在 Room 数据库中，永远不会被删除
- Git 自动提交保留了合并前的每日日志版本
- 只合并昨天的日志 -- 今天的还在积累中，更早的已经处理过了

## 验收标准

### DailyLogWriter 解耦
- [ ] `DailyLogWriter.writeDailyLog()` 不再调用 `longTermMemoryManager.appendMemory()`
- [ ] 摘要提示词不再包含 "## Long-term Facts" section
- [ ] 每日日志文件继续正确写入
- [ ] 记忆索引继续为每日日志条目更新
- [ ] 现有单元测试已更新以反映简化的行为

### MemoryCurator
- [ ] `MemoryCurator` 类存在且有 `curate()` 方法
- [ ] `MemoryCurationWorker` (CoroutineWorker) 按计划运行策展
- [ ] WorkManager 定期任务在 app 初始化时注册
- [ ] 策展读取最近 3 天的每日日志 + 当前 MEMORY.md
- [ ] 策展提示词强制执行质量标准（2+ 天模式、无临时信息、无过期事件）
- [ ] 不需要更新时返回 `NO_CHANGES`（不必要地重写 MEMORY.md）
- [ ] 需要变更时用策展内容覆盖 MEMORY.md
- [ ] 每次 MEMORY.md 更新时创建 git commit（现有 MemoryFileStorage 行为）
- [ ] 即使 app 不在前台也能运行策展（WorkManager）
- [ ] 默认时间：凌晨 3:00

### Section 重命名
- [ ] `MemorySections.STANDARD_SECTIONS` 包含 "Habits/Routines" 而非 "Workflow"
- [ ] 压缩提示词使用 "Habits/Routines" section 名
- [ ] 策展提示词使用 "Habits/Routines" section 名
- [ ] SaveMemoryTool category 映射更新（`"habits"` / `"routines"` -> "Habits/Routines"）
- [ ] 首次压缩/策展时将现有 "## Workflow" 内容迁移到 "## Habits/Routines"

### 压缩提示词增强
- [ ] 压缩提示词包含删除过期时间条目的规则
- [ ] 压缩提示词包含限制每 section 条目数的规则（最多 10 条）
- [ ] 压缩提示词指定 Habits/Routines 只应包含行为模式

### SaveMemoryTool 提示词增强
- [ ] "不要保存"列表包含每日日志级别信息
- [ ] "不要保存"列表包含操作配置细节
- [ ] "不要保存"列表包含带具体日期的特定计划事件

### 每日日志合并整理
- [ ] 策展过程检查昨天的每日日志是否有多个摘要块
- [ ] 如果有多个块，LLM 将它们合并为一份连贯的摘要
- [ ] 合并后的摘要覆盖昨天的每日日志文件
- [ ] 为合并后的每日日志创建 git commit（现有 MemoryFileStorage 行为）
- [ ] 合并过程中不碰今天的每日日志
- [ ] 不碰比昨天更早的每日日志
- [ ] 如果昨天的日志只有一个块，跳过合并
- [ ] 合并后更新搜索索引

### 策展时间 UI
- [ ] Memory 界面显示当前策展时间
- [ ] 时间选择器允许更改策展时间
- [ ] 更改的时间持久化到 SharedPreferences
- [ ] 更改时间触发 WorkManager 重新注册
- [ ] 默认时间为凌晨 3:00

## 功能边界

### 包含
- DailyLogWriter 与 MEMORY.md 解耦
- 新的 MemoryCurator 组件（LLM 驱动的策展）
- MemoryCurationWorker（WorkManager 调度）
- 每日日志合并整理（将昨天碎片化的日志合并为一份摘要）
- 重命名 Workflow -> Habits/Routines
- 增强压缩提示词
- 增强 SaveMemoryTool 提示词
- Memory 界面中的策展时间设置

### 不包含
- 语义去重（基于 embedding）-- MEMORY.md 足够小，不需要
- 个别条目的 TTL/过期标记 -- 有误删重要永久记忆的风险
- 每次 save_memory 调用的 LLM 质量门控 -- 太贵且增加延迟
- MemoryInjector 或 HybridSearchEngine 的变更
- UpdateMemoryTool 的变更

## 业务规则

### 策展规则
1. MemoryCurator 通过 WorkManager 每天运行一次，在用户配置的时间（默认凌晨 3:00）
2. 策展读取最近 3 天的每日日志和当前 MEMORY.md
3. LLM 决定更新什么；如果不需要变更，MEMORY.md 不会被重写
4. 只有在 2+ 天日志中出现的模式才应被提升到 MEMORY.md
5. 过期的时间性条目（过去的日期）必须在策展中删除
6. Habits/Routines section 只存储行为模式，不存操作配置
7. 策展后每个 section 最多 10 条

### DailyLogWriter 规则
1. DailyLogWriter 只写入每日日志文件和记忆搜索索引
2. DailyLogWriter 在任何情况下都不写入 MEMORY.md
3. 摘要提示词只产生 "## Daily Summary" section
4. 每日日志保持可通过 HybridSearchEngine 搜索和通过 MemoryInjector 注入

### 每日日志合并规则
1. 合并在每日策展过程中运行，在 MEMORY.md 策展之前执行
2. 只合并昨天的每日日志 -- 不碰今天的日志
3. 如果昨天的日志只有一个摘要块（无 `---` 分隔符），跳过合并
4. LLM 将多个块合并为一份连贯的摘要，消除块之间的重复
5. Git 自动提交保留合并前的版本
6. 覆盖后为该日期重建搜索索引

### WorkManager 规则
1. 使用 `PeriodicWorkRequest`，周期约 24 小时
2. 计算 `initialDelay` 以对准用户配置的时间
3. 添加网络约束（策展需要 LLM API 调用）
4. 使用 `ExistingPeriodicWorkPolicy.UPDATE` 处理时间变更
5. 使用唯一工作名称防止重复

## 依赖

### 依赖于
- **FEAT-049 (记忆质量改进)**: 扩展压缩和结构化记忆
- **FEAT-013 (记忆系统)**: 基础记忆基础设施
- **AndroidX WorkManager**: 后台调度

### 被依赖于
- 暂无

## 错误处理

### 错误场景

1. **策展 LLM 调用失败（网络错误、API 错误）**
   - 处理：Worker 对临时故障返回 `Result.retry()`
   - WorkManager 会以指数退避重试
   - MEMORY.md 不被修改

2. **策展 LLM 返回空或无效响应**
   - 处理：丢弃响应，保持 MEMORY.md 不变
   - 记录警告用于调试
   - Worker 返回 `Result.success()`（不用相同输入重试）

3. **没有可用的每日日志**
   - 处理：跳过策展，返回 `Result.success()`
   - 这对于没有使用 app 的日子来说是正常的

4. **WorkManager 任务未运行（Doze 模式、电池优化）**
   - 处理：WorkManager 通过弹性窗口自动处理
   - 设备退出 Doze 时任务会运行
   - 非时间敏感 -- 延迟几小时可接受

5. **用户在策展进行中更改策展时间**
   - 处理：当前策展正常完成
   - 下次策展按新时间调度

## 测试要点

### 单元测试
- DailyLogWriter 不再写入 MEMORY.md
- DailyLogWriter 摘要提示词没有 "Long-term Facts" section
- MemoryCurator.curate() 每日日志含新事实时：更新 MEMORY.md
- MemoryCurator.curate() 无相关新事实时：返回 NO_CHANGES，MEMORY.md 不变
- MemoryCurator.curate() 无每日日志时：提前返回
- MemoryCurator.curate() MEMORY.md 含过期条目时：删除它们
- MemoryCurator 将昨天多块的每日日志合并为单份摘要
- MemoryCurator 昨天日志只有单块时跳过合并
- MemoryCurator 昨天日志不存在时跳过合并
- MemorySections.STANDARD_SECTIONS 包含 "Habits/Routines"（非 "Workflow"）
- SaveMemoryTool category "habits" 映射到 "Habits/Routines"
- 压缩提示词包含过期条目删除规则

### 集成测试
- MemoryCurationWorker 通过 MemoryCurator 执行策展
- WorkManager 在配置时间调度策展

### 手动测试
- 使用 app 3 天，验证 MEMORY.md 不被 DailyLogWriter 污染
- 手动触发策展，验证只有高质量事实被提升
- 在 UI 中更改策展时间，验证 WorkManager 重新调度
- 验证即使不打开 app（只通过 Telegram bridge）策展也能运行

## 变更历史

| 日期 | 版本 | 变更 | 负责人 |
|------|------|------|--------|
| 2026-03-04 | 0.1 | 初始版本 | - |
| 2026-03-04 | 0.2 | 添加每日日志合并整理（变更 7） | - |
