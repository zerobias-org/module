# Claude Instructions

## 🚨 CRITICAL: STRICT ENFORCEMENT MODE

This project uses **STRICT ENFORCEMENT** - there is NO flexibility in execution.
- Every rule violation = TASK FAILURE
- Every skipped step = TASK FAILURE
- Every incomplete execution = TASK FAILURE

## 🚨 RULE UPDATE PROTOCOL

**When user asks to update a rule:**
1. **STOP ALL OTHER WORK IMMEDIATELY**
2. **UPDATE THE RULE FIRST** - before any other action
3. **CONFIRM the update with the user**
4. **THEN proceed** with the original task using the new rule

This ensures all subsequent work follows the updated rule from the start.

## 🚨 MANDATORY FIRST STEP - READ EXECUTION PROTOCOL
**BEFORE DOING ANYTHING ELSE**:
1. Read @claude/EXECUTION-PROTOCOL.md - Learn the exact execution sequence
2. Read @claude/ENFORCEMENT.md - Understand validation gates and zero-tolerance policy

Without reading these, you WILL fail to properly execute any module work.

## General Rules

**IMPORTANT**: When working in this repository or any of its subdirectories, you MUST follow this exact sequence:

### 🚨 MANDATORY PRE-EXECUTION SEQUENCE

**BEFORE any work, complete these steps in order:**

1. **Identify Task Type**
   - Map user request to workflow: analyze-api / create-module / add-operation / update-module / fix-module
   - Open the corresponding task file in `claude/workflow/tasks/`

2. **Load Required Rules** (ONLY the relevant ones)
   - API work → `claude/rules/api-specification.md` + `claude/ENFORCEMENT.md`
   - Implementation → `claude/rules/implementation.md` + `claude/rules/error-handling.md`
   - Testing → `claude/rules/testing.md`
   - Always → `claude/rules/prerequisites.md`

3. **Identify Active Agents** (invoke with @agent-name)
   - API Specification → @api-architect + @schema-specialist + @security-auditor
   - Implementation → @typescript-expert + @operation-engineer + @mapping-engineer
   - Testing → @test-orchestrator + @mock-specialist + test engineers
   - See [claude/agents/AGENTS.md](claude/agents/AGENTS.md) for complete agent reference

4. **Create TodoWrite** with ALL workflow steps (usually 7 steps for add-operation)

5. **Execute with Constraints**
   - Work ONLY within loaded rules
   - Validate at EACH gate (see `claude/ENFORCEMENT.md`)
   - Stop at any gate failure
   - Never work independently outside the workflow

**See `claude/EXECUTION-PROTOCOL.md` for complete details.**

### Other Important Rules
- Run `pwd` to get repository root path, use absolute paths
- **NEVER work independently** - always follow the workflow
- **READ ALL CRITICAL RULES** marked with 🚨 - violations = task failure
- **USE CHECKLISTS** in `claude/ENFORCEMENT.md` before marking complete

## 🚀 SYSTEM STRUCTURE

**Core enforcement and execution:**
- **🚨 [EXECUTION PROTOCOL](claude/EXECUTION-PROTOCOL.md)** - **READ THIS FIRST!** Exact execution sequence
- **🚨 [ENFORCEMENT](claude/ENFORCEMENT.md)** - Validation gates and completion checklist
- **[Rules](claude/rules/)** - Consolidated, organized rules by domain
- **[Workflows](claude/workflow/)** - Task-specific workflows

**Consolidated rule files:**
- **[API Specification](claude/rules/api-specification.md)** - All API spec rules in one place
- **[Implementation](claude/rules/implementation.md)** - Streamlined implementation patterns
- **[Testing](claude/rules/testing.md)** - Streamlined testing requirements
- **[Error Handling](claude/rules/error-handling.md)** - Core error types
- **[Type Mapping](claude/rules/type-mapping.md)** - Type conversion reference

The system provides:
- Zero redundancy - each rule appears once
- Clear enforcement - validation gates at each step
- Positive examples - focus on correct patterns
- Fast loading - reduced file count and size

## 🤖 AGENT SYSTEM

**26 Specialized AI Agents** organized by workflow phase, each with distinct personality and expertise.

**Agent Directory:**
- **[AGENTS.md](claude/agents/AGENTS.md)** - Complete agent reference
- **[agent-invocation-guide.md](claude/agents/agent-invocation-guide.md)** - How to invoke agents with @

**Agent Phases:**
1. **ANALYSIS & DISCOVERY** (4 agents) - @product-specialist, @api-researcher, @operation-analyst, @credential-manager
2. **DESIGN & SPECIFICATION** (5 agents) - @api-architect, @schema-specialist, @api-reviewer, @security-auditor, @documentation-writer
3. **GENERATION** (1 agent) - @build-validator
4. **IMPLEMENTATION** (6 agents) - @typescript-expert, @client-engineer, @operation-engineer, @mapping-engineer, @build-reviewer, @style-reviewer
5. **TESTING** (10 agents) - @test-orchestrator, @mock-specialist, test engineers, reviewers
6. **ORCHESTRATION** (1 agent) - @gate-controller

**Invoke agents with @agent-name:**
```
@api-researcher Research GitHub webhook endpoints
@api-architect Design OpenAPI spec for webhooks
@operation-engineer Implement listWebhooks operation
@test-orchestrator Coordinate testing for operation
@gate-controller Validate all gates
```

**Agent Features:**
- Distinct personality per agent (e.g., @api-researcher is meticulous investigator)
- Domain-specific expertise
- Enforces specific rules from claude/rules/
- Collaborative workflow integration
- Clear invocation patterns

## ⚡ SLASH COMMANDS (Quick Workflow Execution)

**6 Slash Commands** for executing complete workflows with full agent orchestration.

**Quick Start:**
```bash
/add-operation github-github listWebhooks
/add-operations github-github list,get,create,update,delete
/create-module github github
/analyze-api github webhooks
/fix-issue github-github "type error"
/validate-gates github-github
```

**Command Directory:**
- **[Slash Commands README](claude/.slashcommands/README.md)** - Complete command reference
- **[Command Definitions](claude/.slashcommands/)** - JSON specs for each command

**Available Commands:**

1. **`/create-module <vendor> <service> [suite]`** - Create new module (3-4 hours)
2. **`/add-operation <module-id> <operation>`** - Add single operation (45-70 min)
3. **`/add-operations <module-id> <op1,op2,op3>`** - Add multiple operations
4. **`/analyze-api <vendor> <product>`** - Create API entity diagram (30-60 min)
5. **`/fix-issue <module-id> [description]`** - Diagnose and fix (15 min - 2 hours)
6. **`/validate-gates <module-id>`** - Check all 6 gates (5-10 min)

**Module Identifier Format:** `vendor-service` or `vendor-suite-service`
- Examples: `github-github`, `amazon-aws-s3`, `avigilon-alta-access`

**Benefits:**
- Single command triggers complete workflow
- Automatic agent orchestration
- All gates validated
- Progress tracking
- Error handling

## 🎯 ORCHESTRATION SYSTEM

**How the general purpose agent orchestrates workflows and agents.**

**Orchestration Directory:**
- **[ORCHESTRATION-GUIDE.md](claude/orchestration/ORCHESTRATION-GUIDE.md)** - Main orchestration rules
- **[agent-routing-rules.md](claude/orchestration/agent-routing-rules.md)** - Agent selection logic
- **[Workflow Templates](claude/orchestration/workflow-templates/)** - Detailed execution workflows

**Core Concepts:**

**1. Request → Workflow Mapping**
```
User request analyzed → Task type identified → Workflow template loaded → Agents orchestrated
```

**2. Agent Routing**
```
"Add operation" → add-operation-workflow → 18 agents in sequence
"Create module" → create-module-workflow → 24 agents across 9 phases
"Fix issue" → fix-issue-workflow → Diagnostic → Specialist → Validate
```

**3. Context Management**
```
Agent output → Memory files → Next agent input
Stored in: .claude/.localmemory/{action}-{module}/
```

**4. Validation Gates**
```
Gate 1: API Specification
Gate 2: Type Generation
Gate 3: Implementation
Gate 4: Test Creation
Gate 5: Test Execution
Gate 6: Build

Each phase validated before proceeding
```

**Orchestration Features:**
- Sequential phase execution
- Context passing via memory
- Failure handling and recovery
- Task decomposition for complex requests
- Agent collaboration coordination

**See Also:**
- [context-management.md](claude/orchestration/context-management.md) - How context flows
- [task-breakdown-patterns.md](claude/orchestration/task-breakdown-patterns.md) - How tasks decompose
- [agent-collaboration-patterns.md](claude/orchestration/agent-collaboration-patterns.md) - How agents collaborate

## Memory Management

### Local Memory Storage
- **Location**: `.claude/.localmemory/`
- **Purpose**: Store task progress, intermediate results, and context between tasks
- **Scope**: Local only - never commit these files to git
- **Structure**: Each module request gets its own folder

### Module Memory Folder Naming
Each module request creates a memory folder with this pattern:
```
.claude/.localmemory/{action}-{module-identifier}/
```

Where **module-identifier** follows the pattern:
- `{vendor}-{module}` - for simple modules
- `{vendor}-{suite}-{module}` - when suite exists

**Examples**:
- `create-github-github/`
- `update-amazon-aws-iam/`
- `deprecate-gitlab-gitlab/`

**Important**: Memory folders are created by the first task that runs, not beforehand. If a memory folder doesn't exist for a module, it means work on that module has not been started yet.

## Task Structure

### Task Definitions
- **Format**: Each task is defined in a separate markdown file
- **Execution**: Tasks run individually and in isolation
- **Memory**: Task status and progress stored in `.localmemory/{action}-{module-identifier}/`

### Task Memory
Each task stores its status and intermediate results in:
```
.claude/.localmemory/{action}-{module-identifier}/
├── task-status.json     # Overall task progress
├── task-01-status.json  # Individual task status
├── task-02-status.json
└── ...
```

## Workflow Execution

1. **Task Execution**: Run each task individually, with the first task creating the memory folder
2. **Context Passing**: Tasks read previous results from memory files
3. **Progress Tracking**: Each task stores its status and intermediate results
4. **Completion**: Final artifacts are created, memory can be cleaned up

### Module Creation Workflow
**🚨 CRITICAL**: When executing individual module creation tasks:
- **SINGLE TASK EXECUTION**: Execute only the requested task, then stop and return control
- **NO AUTO-CONTINUE**: Do not automatically proceed to subsequent tasks - wait for explicit instruction
- **TASK COMPLETION**: After completing a task, provide a summary of the work done and stop
- **FAILURE HANDLING**: If a task fails, stop execution and report the issue clearly
- **RETURN CONTROL**: Always return control to the calling script after task completion or failure

## Task Execution

### Using the Agent System (Recommended)
1. Express your intent (e.g., "Create GitHub module" or "Add webhook operations")
2. Invoke appropriate agents with @agent-name for each phase
3. Agents enforce their domain-specific rules throughout
4. Workflow progresses through validation gates
5. See [claude/agents/AGENTS.md](claude/agents/AGENTS.md) for complete agent guide

### Workflow Entry Points
- **[Unified Workflow](claude/workflow/WORKFLOW.md)** - Single adaptive workflow
- **[Create Module Task](claude/workflow/tasks/create-module.md)** - New module creation
- **[Update Module Task](claude/workflow/tasks/update-module.md)** - Add operations
- **[Add Operation Task](claude/workflow/tasks/add-operation.md)** - Single operation

### Legacy Task Documentation (Reference)
- **[Module Creation Tasks](claude/create/CLAUDE.md)** - Detailed step-by-step tasks
- **[Module Update Tasks](claude/update/CLAUDE.md)** - Update process tasks

Note: The legacy tasks contain detailed examples and edge cases but should be interpreted through the new persona/rule system.

## Breaking Changes Policy

### Update Workflow (claude/update/)
- **NO BREAKING CHANGES ALLOWED** - All existing functionality must be preserved exactly
- **FAIL FAST ON REGRESSIONS** - Any existing test failure stops the update process immediately
- **BACKWARD COMPATIBILITY MANDATORY** - All existing client code must continue to work

### Fix Workflow (claude/fix/ - to be created)
- **BREAKING CHANGES PERMITTED** - May modify existing functionality to fix issues
- **REGRESSION FIXES ALLOWED** - May change behavior to correct bugs
- **CLIENT CODE UPDATES MAY BE REQUIRED** - Breaking changes require version bumps

## Repository Structure

This is a monorepo for API client modules.

### Types Repository Reference
- **Location**: `/types/` (optional submodule)
- **Contents**: 
  - `types-core` - Core type definitions
  - `types-<vendor>` - Vendor-specific type definitions  
  - `types-core-<lang>` - Core types for specific languages
  - `types-<vendor>-<lang>` - Vendor-specific types for specific languages
- **Usage**: Reference types when available for enhanced type safety and consistency
- **Availability**: Not all users may have access to clone this submodule - gracefully handle absence

## Next Steps

- Define memory file formats and schemas
- Specify task dependencies and execution order
- Create task-specific documentation for each workflow type
