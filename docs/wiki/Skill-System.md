# Custom Skills

Skills are reusable prompt templates that define structured workflows for the AI. They can be invoked via slash commands in the chat input, through the UI skill selector, or by the AI itself via the `load_skill` tool.

## What is a Skill?

A skill is a directory containing a `SKILL.md` file with YAML frontmatter (metadata) and Markdown body (instructions). When a skill is loaded, its instructions are injected into the conversation as a system-level prompt, guiding the AI to follow a specific workflow.

## SKILL.md Format

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

## Storage Locations

- **Built-in skills:** `app/src/main/assets/skills/<skill-name>/SKILL.md`
- **User-created skills:** `filesDir/skills/<skill-name>/SKILL.md`

## Built-in Skills

| Skill | Description |
|-------|-------------|
| `create-skill` | Guides the user through creating a new custom skill |
| `create-tool` | Guides the user through creating a new custom JavaScript tool |

## Invocation Methods

### 1. Slash Command

Type `/` in the chat input to see available skills. Select one and fill in any required parameters. The `SlashCommandPopup` provides autocomplete.

### 2. UI Skill Selector

Use the skill selection bottom sheet (`SkillSelectionBottomSheet`) to browse and select skills. A parameter form is shown for skills that require input.

### 3. AI Self-Invocation

The AI can call the `load_skill` tool to load a skill's instructions into the current conversation. This enables automated workflows where the AI recognizes that a task matches an available skill.

## Creating a Skill

### Via the UI

1. Navigate to Settings > Skills
2. Tap "Create Skill"
3. Fill in the skill metadata (name, display name, description)
4. Write the SKILL.md content in the editor
5. Save

### Via the `create-skill` Skill

1. Type `/create-skill` in the chat
2. Describe what the skill should do
3. The AI will generate a SKILL.md file and save it

### Manually

Create a directory under `filesDir/skills/` with a `SKILL.md` file following the format above.

## Managing Skills

The Skill Management screen (`SkillManagementScreen`) provides:

- List all skills (built-in and user-created)
- Edit user-created skills
- Delete user-created skills
- Export skills (share as files)
- Import skills from files

### Use Cases

| Use Case | Description |
|----------|-------------|
| `CreateSkillUseCase` | Create and persist a new skill |
| `UpdateSkillUseCase` | Update an existing skill's content |
| `DeleteSkillUseCase` | Remove a user-created skill |
| `LoadSkillContentUseCase` | Load and parse SKILL.md with parameter substitution |
| `ExportSkillUseCase` | Export a skill for sharing |
| `ImportSkillUseCase` | Import a skill from a file |
| `GetAllSkillsUseCase` | List all available skills |

## Parameter Substitution

When a skill is loaded, `SkillFileParser` replaces `{{param_name}}` placeholders in the Markdown body with the actual parameter values provided by the user.

## Skill Registry

`SkillRegistry` manages skill discovery and caching:

1. Scans `assets/skills/` for built-in skills
2. Scans `filesDir/skills/` for user-created skills
3. Caches parsed skill definitions
4. Provides lookup by name

The registry is refreshed when skills are created, updated, or deleted.
