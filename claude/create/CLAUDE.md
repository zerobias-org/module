# Module Creation Workflow

## Overview

The module creation process is organized as a workflow with multiple isolated tasks. Each task runs independently and any memory that needs to persist between tasks is stored in local files.

## Task Discovery and Execution

**Task Discovery**: Find all available tasks by examining files in `claude/create/tasks/` directory:
- Look for files matching pattern `task-##-*.md` (e.g., `task-01-product-discovery.md`, `task-02-api-analysis.md`)
- Tasks are numbered sequentially starting from 01
- The number of tasks is dynamic and may vary based on workflow requirements

**Sequential Execution**: Execute tasks in numerical order:
1. Read task definitions from `claude/create/tasks/task-##-*.md` files
2. Execute tasks sequentially (task-01 → task-02 → task-03 → ... → task-NN)
3. Complete all discovered tasks for a fully functional, production-ready module

## Task Execution Rules

- **Dynamic task count**: Number of tasks varies - discover all tasks in the folder
- **Sequential execution**: Tasks must be executed in numerical order (01, 02, 03, etc.)
- **Task isolation**: Each task runs independently with defined inputs/outputs
- **Memory persistence**: Task results stored in `.claude/.localmemory/{action}-{module-identifier}/`
- **Failure handling**: If any task fails, resolve issues before proceeding to next task
- **Complete workflow**: Execute ALL discovered tasks for production-ready module

## Task Definitions

Tasks for module creation are defined in: `claude/create/tasks/`

## Create-Specific Implementation

- Define individual task files in `claude/create/tasks/`
- Follow the general task structure and memory management defined in root CLAUDE.md
- **CRITICAL**: ALL discovered tasks must be completed for a fully functional, production-ready module
