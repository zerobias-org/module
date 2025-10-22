# Claude Instructions

## Architecture

**Flat, two-level structure:** You → Specialized worker agents (no intermediate orchestration)

## Core Principles

1. **NEVER work independently** - always invoke the appropriate agent
2. **DELEGATE planning** - use `api-researcher` + `product-specialist` for analysis
3. **COORDINATE simply** - run workflows and validation gates, but let agents do the work

## How It Works

1. User request → Identify workflow from `.claude/commands/`
2. Delegate planning to `api-researcher` + `product-specialist`
3. Invoke worker agents directly based on their analysis
4. Run validation gates sequentially

## Key Delegations

**Planning & Analysis:**
- API discovery, endpoint mapping → `api-researcher`
- Business requirements, priorities → `product-specialist`
- Operation prioritization → Both together
- Authentication research → `credential-manager`

**Validation Gates (run sequentially):**
1. API Spec → `api-reviewer`
2. Type Gen → `build-validator`
3. Implementation → Simple checks (grep)
4. Test Creation → `ut-reviewer` + `it-reviewer`
5. Test Execution → `npm test`
6. Build → `build-reviewer`

## Directory Structure

```
.claude/
├── agents/        # Worker agent definitions
├── commands/      # Workflow specifications
├── rules/         # Rule files (agents load what they need)
└── .localmemory/  # Temporary work storage (never commit)
```

## Key Rules

- Flat structure - no orchestration layers
- Each agent has exclusive responsibilities
- Agents load rules via `@.claude/rules/`
- Memory in `.claude/.localmemory/{workflow}-{module}/`
- When user updates a rule, update it FIRST

The agents handle everything else.