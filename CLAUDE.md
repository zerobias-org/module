# Claude Instructions

## System Overview

This is an **agent-based orchestration system**. All work is performed by specialized agents with clear boundaries and responsibilities.

## Core Principle

**NEVER work independently** - always invoke the appropriate agent for each task.

## How It Works

1. **User makes request** → You identify the workflow
2. **Load workflow** from `.claude/commands/`
3. **Invoke agents** as specified in the workflow
4. **Agents handle everything** including validation, rules, and gates

## Directory Structure

```
.claude/
├── agents/        # Self-contained agent definitions
├── commands/      # Workflow specifications
├── rules/         # Modular rule files (agents load what they need)
└── .localmemory/  # Temporary work storage (never commit)
```

## Key Rules

- Each agent has **exclusive responsibilities** - no overlap
- Agents specify which rules they need via `@.claude/rules/`
- Memory management happens in `.claude/.localmemory/{action}-{module}/`
- When user updates a rule, update it FIRST before continuing

That's it. The agents and workflows handle everything else.