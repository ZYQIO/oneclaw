# 自定义技能

技能（Skill）是可复用的提示词模板，用于为 AI 定义结构化的工作流程。可以通过聊天输入框中的 slash command、UI 技能选择器，或由 AI 自身通过 `load_skill` 工具来调用。

## 什么是技能？

技能是一个目录，其中包含一个 `SKILL.md` 文件，该文件由 YAML frontmatter（元数据）和 Markdown 正文（指令）组成。当技能被加载时，其指令会以系统级提示词的形式注入对话，引导 AI 遵循特定的工作流程。

## SKILL.md 格式

```yaml
---
name: skill-name
display_name: "Display Name"
description: "One-line description of what this skill does"
version: "1.0"
tools_required:
  - tool_name_1
  - tool_name_2
parameters:
  - name: param_name
    type: string
    required: true
    description: "What this parameter is for"
  - name: optional_param
    type: string
    required: false
    description: "An optional parameter"
---

# Skill Instructions

Detailed prompt instructions for the AI to follow when this skill is active.

You can reference parameters using {{param_name}} syntax.
The value of {{optional_param}} will be substituted at load time.
```

## 存储位置

- **内置技能：** `app/src/main/assets/skills/<skill-name>/SKILL.md`
- **用户创建的技能：** `filesDir/skills/<skill-name>/SKILL.md`

## 内置技能

| 技能 | 描述 |
|------|------|
| `create-skill` | 引导用户创建新的自定义技能 |
| `create-tool` | 引导用户创建新的自定义 JavaScript 工具 |

## 调用方式

### 1. Slash Command

在聊天输入框中输入 `/` 可查看可用技能。选择一个技能后填写所需参数。`SlashCommandPopup` 提供自动补全功能。

### 2. UI 技能选择器

使用技能选择底部弹窗（`SkillSelectionBottomSheet`）浏览并选择技能。对于需要输入的技能，会显示参数填写表单。

### 3. AI 自主调用

AI 可以调用 `load_skill` 工具，将技能指令加载到当前对话中。这使 AI 能够在识别到某个任务与可用技能匹配时，自动触发相应工作流程。

## 创建技能

### 通过 UI

1. 进入设置 > 技能
2. 点击"创建技能"
3. 填写技能元数据（名称、显示名称、描述）
4. 在编辑器中编写 SKILL.md 内容
5. 保存

### 通过 `create-skill` 技能

1. 在聊天中输入 `/create-skill`
2. 描述该技能应实现的功能
3. AI 将自动生成并保存 SKILL.md 文件

### 手动创建

在 `filesDir/skills/` 下创建一个目录，并按照上述格式在其中放置 `SKILL.md` 文件。

## 技能管理

技能管理页面（`SkillManagementScreen`）提供以下功能：

- 列出所有技能（内置技能和用户创建的技能）
- 编辑用户创建的技能
- 删除用户创建的技能
- 导出技能（以文件形式分享）
- 从文件导入技能

### Use Cases

| Use Case | 描述 |
|----------|------|
| `CreateSkillUseCase` | 创建并持久化一个新技能 |
| `UpdateSkillUseCase` | 更新现有技能的内容 |
| `DeleteSkillUseCase` | 删除用户创建的技能 |
| `LoadSkillContentUseCase` | 加载并解析 SKILL.md，并进行参数替换 |
| `ExportSkillUseCase` | 导出技能以供分享 |
| `ImportSkillUseCase` | 从文件导入技能 |
| `GetAllSkillsUseCase` | 列出所有可用技能 |

## 参数替换

当技能被加载时，`SkillFileParser` 会将 Markdown 正文中的 `{{param_name}}` 占位符替换为用户实际提供的参数值。

## 技能注册表

`SkillRegistry` 负责管理技能的发现与缓存：

1. 扫描 `assets/skills/` 获取内置技能
2. 扫描 `filesDir/skills/` 获取用户创建的技能
3. 缓存已解析的技能定义
4. 提供按名称查找的功能

每当技能被创建、更新或删除时，注册表会自动刷新。
