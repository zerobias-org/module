# Claude Instructions

## General Rules

**IMPORTANT**: When working in this repository or any of its subdirectories, always:
- First run `pwd` to get the current repository root path
- Store this path and use absolute paths in all commands and file operations
- Never use relative paths to avoid "file not found" errors
- **NEVER work independently** - always follow the task definitions in the appropriate workflow directory
- **DO NOT create modules or perform work without following the defined tasks**
- **READ ALL CRITICAL RULES**: Each task file contains mandatory compliance rules marked with 🚨 - violating ANY rule means task failure
- **USE CHECKLISTS**: Complete all pre-implementation and post-implementation checklists in task files
- **🚨 NEVER TOUCH FILES OUTSIDE THE MODULE SCOPE** - Only modify, create, or fix files within the specific module being worked on. Do not improve or fix files in other modules, shared directories, or repository-wide configurations unless explicitly requested for the current module.

This repository contains multiple types of tasks for module development. Each task type has its own dedicated documentation file.

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

## Task-Specific Documentation

- **[Module Creation](claude/create/CLAUDE.md)** - Process to create a module from scratch
- **[Additional task types]** - More documentation files will be added as needed

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
