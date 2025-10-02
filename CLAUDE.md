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

3. **Identify Active Personas**
   - API Specification → API Architect + Security Auditor
   - Implementation → TypeScript Expert + Integration Engineer
   - Testing → Testing Specialist

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

### Using the New System (Recommended)
1. Express your intent (e.g., "Create GitHub module" or "Add webhook operations")
2. System analyzes request and activates appropriate personas
3. Rules are enforced throughout
4. Workflow adapts to your needs

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
