# Module Update Workflow

## Overview

The module update process adds new operations to existing modules while preserving all existing functionality. This workflow ensures safe, reliable updates with zero tolerance for regressions or breaking changes.

## Task Discovery and Execution

**Task Discovery**: Find all available tasks by examining files in `claude/update/tasks/` directory:
- Look for files matching pattern `task-##-*.md` (e.g., `task-01-validate-prerequisites.md`)
- Tasks are numbered sequentially starting from 01
- The number of tasks is dynamic and may vary based on workflow requirements

**Sequential Execution**: Execute tasks in numerical order:
1. Read task definitions from `claude/update/tasks/task-##-*.md` files
2. Execute tasks sequentially (task-01 → task-02 → task-03 → ... → task-NN)
3. Complete all discovered tasks for a fully updated, production-ready module

## Task Execution Rules

- **Dynamic task count**: Number of tasks varies - discover all tasks in the folder
- **Sequential execution**: Tasks must be executed in numerical order (01, 02, 03, etc.)
- **Task isolation**: Each task runs independently with defined inputs/outputs
- **Memory persistence**: Task results stored in `.claude/.localmemory/update-{module-identifier}/`
- **Failure handling**: If any task fails, resolve issues before proceeding to next task
- **Complete workflow**: Execute ALL discovered tasks for production-ready updated module

## Critical Prerequisites

Before starting ANY update tasks, the existing module MUST be in a stable state:

**MANDATORY VALIDATION (Task 01 - FAIL FAST)**:
- **Clean Git State**: No uncommitted changes allowed
- **Build Success**: `npm run build` must exit with code 0
- **Unit Tests Pass**: `npm run test` must show 0 failures
- **Integration Tests Pass**: `npm run test:integration` must show 0 failures (if credentials available)
- **Linting Clean**: `npm run lint` must pass without errors

**If ANY prerequisite fails**: STOP immediately and use the \"fix\" workflow (to be created separately)

## Update Process Overview

### Phase 1: Prerequisites & Analysis (Tasks 01-03)
1. **Validate Prerequisites** - CRITICAL: Ensure existing module is stable (fail fast)
2. **Analyze Existing Structure** - Document current operations, producers, tests
3. **Analyze Requested Operations** - Parse user request, identify specific operations to add

### Phase 2: API Specification Updates (Tasks 04-05)
4. **Update API Specification** - Add new operations to `api.yml` (no breaking changes)
5. **Regenerate Interfaces** - Generate new TypeScript interfaces for new operations

### Phase 3: Implementation Updates (Tasks 06-09)
6. **Extend Data Mappers** - Add mappers only for NEW data types
7. **Extend Producers** - Add new methods to existing producers OR create new ones
8. **Update Main Connector** - Add NEW producer getters (preserve existing exactly)
9. **Update Module Exports** - Add new exports without modifying existing ones

### Phase 4: Testing & Final Validation (Tasks 10-11)
10. **Add Unit and Integration Tests** - Create tests for NEW operations only
11. **Validate Backward Compatibility** - CRITICAL: Ensure ALL existing functionality preserved

## Key Principles

### Zero Breaking Changes Policy
- **NO MODIFICATIONS** to existing operations, interfaces, or method signatures
- **NO VERSION CHANGES** to API specification (lerna handles versioning)
- **PRESERVE EXISTING TESTS** exactly as they are - add new tests separately
- **MAINTAIN BACKWARD COMPATIBILITY** - all existing code must continue to work

### Fail Fast Approach
- **STOP IMMEDIATELY** on any existing test failure
- **STOP IMMEDIATELY** on build failures
- **STOP IMMEDIATELY** on validation failures
- **NO FIXES ATTEMPTED** in update workflow - use separate \"fix\" workflow

### Incremental Update Strategy
- **EXTEND existing producers** when operations match existing domains
- **CREATE new producers** only when new domains/tags are introduced
- **ADD ONLY** - never modify or remove existing functionality
- **PRESERVE PATTERNS** - follow existing code patterns and conventions

### Test Coverage Requirements
- **ALL new operations** must have unit test coverage
- **ALL new operations** must have integration test coverage
- **EXISTING tests** must continue to pass without modification
- **NO SKIPPED TESTS** for new operations when credentials are available

## Memory Management

### Local Memory Storage
- **Location**: `.claude/.localmemory/update-{module-identifier}/`
- **Purpose**: Store task progress, analysis results, and context between tasks
- **Scope**: Local only - never commit these files to git
- **Structure**: Each update request gets its own folder

### Module Memory Folder Naming
Each update request creates a memory folder with this pattern:
```
.claude/.localmemory/update-{module-identifier}/
```

Where **module-identifier** follows the pattern:
- `{vendor}-{module}` - for simple modules (e.g., `update-github-github/`)
- `{vendor}-{suite}-{module}` - when suite exists (e.g., `update-amazon-aws-iam/`)

## Task Dependencies

Each task has specific input requirements:
- **Task 01**: User request, module path
- **Task 02**: Task 01 output (successful validation)
- **Task 03**: Tasks 01-02 outputs
- **Task 04**: Tasks 01-03 outputs, resolved conflicts
- **Task 05**: Task 04 output (updated API spec)
- **Tasks 06-09**: Previous task outputs + regenerated interfaces
- **Tasks 10-11**: All previous task outputs + completed implementation

## Error Handling and Recovery

### Common Failure Scenarios
- **Prerequisites fail**: Use \"fix\" workflow to resolve existing issues
- **Conflicts detected**: Manual resolution required, may involve API review
- **Structure mismatches**: May require new module creation instead of update
- **Generation fails**: Usually indicates API specification issues
- **Test regressions**: Implementation issues that must be fixed immediately

### Recovery Procedures
- **Backup restoration** available at multiple checkpoints
- **Rollback capabilities** for API specification and generated files
- **Clean rebuild** process to reset to known good state
- **Memory cleanup** tools for restarting failed update attempts

## Quality Gates

### Build Gates (Multiple throughout workflow)
- **Initial**: Task 01 - existing module must build and test cleanly
- **Post-generation**: Task 05 - regenerated interfaces must build cleanly
- **Final**: Task 11 - complete updated module must build and test cleanly

### Test Gates
- **Existing functionality**: Must pass throughout entire workflow
- **New functionality**: Must pass before completion
- **Integration testing**: Required for all new operations (with credentials)

### Code Quality Gates
- **Linting**: Must pass at multiple checkpoints
- **Pattern consistency**: New code must follow existing patterns
- **Documentation**: Must be updated for new operations

## Success Criteria

An update is considered successful when:

- **All requested operations** successfully implemented
- **Zero regressions** in existing functionality
- **All tests pass** (existing + new)
- **Build completes cleanly** with no errors
- **Code quality maintained** with consistent patterns
- **Documentation updated** as appropriate
- **Backward compatibility preserved** completely

## Task-Specific Implementation

Tasks for module updates are defined in: `claude/update/tasks/`

## 🚨 Critical Update Rules

**MANDATORY PATTERNS - Violating ANY rule means update failure:**

1. **No Breaking Changes**: Existing interfaces, methods, and contracts must remain exactly the same
2. **Fail Fast Prerequisites**: Any existing test failure stops the update process immediately
3. **Clean Git Required**: No uncommitted changes allowed before starting updates
4. **Build Gates Mandatory**: Build must pass at multiple checkpoints throughout workflow
5. **Test Preservation**: Existing tests must pass throughout entire update process
6. **Incremental Only**: Add new functionality, never modify or remove existing functionality
7. **Pattern Consistency**: New code must follow existing architectural patterns
8. **Complete Coverage**: All new operations must have both unit and integration test coverage
9. **Validation Required**: API specification and generated code must validate successfully
10. **Backward Compatibility**: All existing client code must continue to work without changes

## Related Workflows

- **Module Creation**: Use `claude/create/` for new modules from scratch
- **Module Fix**: Use `claude/fix/` for fixing broken existing modules (to be created)
- **Dependency Updates**: Use `claude/deps/` for updating module dependencies (to be created)

## Next Steps

- Complete remaining task definitions for comprehensive update coverage
- Create complementary \"fix\" workflow for resolving existing module issues  
- Define \"dependency update\" workflow for major dependency upgrades
- Add automation tools for common update patterns and validation