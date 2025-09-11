# Task 01: Validate Prerequisites

## Prerequisites

**🚨 CRITICAL**: Before starting this task, read `CLAUDE.md` to understand the project structure, rules, and requirements.

## Overview

This task validates that an existing module is in a stable state before attempting any updates. This is a **FAIL FAST** task - if any validation fails, the update process must stop immediately and the user should use the "fix" workflow instead.

## Input Requirements

- User request specifying the target module for updates (e.g., "add operations to module vendor-service")
- **Target module path** - Path to existing module directory
- **Complete initial user prompt** - Store the full user request for context preservation across tasks

## Module Identifier Derivation

**Module Identifier**: Extract from existing module path structure:
- Path: `package/{vendor}/{service}` → Module Identifier: `vendor-service`
- Path: `package/{vendor}/{suite}/{service}` → Module Identifier: `vendor-suite-service`

**Memory Folder Structure**: `.claude/.localmemory/update-{module-identifier}/`

## Process Steps

### 1. Locate Target Module

1. **Parse user request**:
   - Extract module identifier from user request
   - Construct expected module path: `package/{vendor}/{service}` or `package/{vendor}/{suite}/{service}`

2. **Validate module exists**:
   - Check if module directory exists
   - Verify it contains required files: `package.json`, `src/`, `api.yml`
   - If module doesn't exist: **STOP** and inform user module not found

### 2. Git State Validation

1. **Check git status**:
   - Run `git status --porcelain` in repository root
   - If any uncommitted changes exist: **STOP** and require clean git state
   - User must commit or stash changes before proceeding

2. **Working directory validation**:
   - Ensure we're in the correct repository root
   - Store absolute path to module for subsequent tasks

### 3. Build Validation (CRITICAL GATE)

Navigate to module directory and run build validation:

1. **Clean and build**:
   ```bash
   cd package/{vendor}/{service}
   npm run clean
   npm run build
   ```

2. **Build success requirement**:
   - Build must exit with code 0
   - If build fails: **STOP** and inform user to use "fix" workflow
   - Store build output for reference

### 4. Test Validation (CRITICAL GATE)

Run all existing tests to ensure module is stable:

1. **Unit test validation**:
   ```bash
   npm run test
   ```
   - Must show 0 failures
   - If any unit tests fail: **STOP** and require "fix" workflow

2. **Integration test validation** (if integration tests exist):
   ```bash
   npm run test:integration
   ```
   - Must show 0 failures if credentials available
   - If no credentials: tests should skip gracefully
   - If integration tests fail: **STOP** and require "fix" workflow

3. **Test discovery**:
   - Document which test types exist (unit, integration)
   - Note credential availability for integration tests
   - Store baseline test results for comparison

### 5. Code Quality Validation

1. **Lint validation**:
   ```bash
   npm run lint
   ```
   - Must pass without errors
   - If lint fails: **STOP** and require "fix" workflow

2. **Dependency validation**:
   - Check for outdated critical dependencies
   - Note if major dependency updates might be needed (for future "update dependencies" workflow)

### 6. Structure Pattern Validation

1. **Expected structure validation**:
   - Verify module follows expected patterns from creation workflow
   - Check for: `src/index.ts`, `src/*Client.ts`, `src/*Impl.ts`, `src/mappers.ts`
   - Check for: `generated/api/index.ts`, `test/` directory

2. **Structure mismatch handling**:
   - If structure doesn't match expected patterns: **STOP**
   - Ask user for guidance (may need to create new module instead)
   - Document any structural anomalies

### 7. Create Memory Folder and Store Context

1. **Setup memory folder**:
   - Create directory: `.claude/.localmemory/update-{module-identifier}/`
   - Store initial user prompt for context preservation

2. **Baseline documentation**:
   - Document current module state (files, tests, structure)
   - Store validation results for reference
   - Note any special considerations or anomalies

## Output Format

Store the following JSON in memory file: `.claude/.localmemory/update-{module-identifier}/task-01-output.json`

```json
{
  "status": "completed|failed|error",
  "moduleIdentifier": "{module-identifier}",
  "modulePath": "package/{vendor}/{service}",
  "modulePackage": "@zerobias-org/module-{vendor}-{service}",
  "initialUserPrompt": "{complete_user_request}",
  "validations": {
    "gitState": {
      "status": "clean|dirty",
      "uncommittedChanges": []
    },
    "build": {
      "status": "passed|failed",
      "exitCode": 0,
      "command": "npm run build",
      "output": "{build_output_summary}"
    },
    "unitTests": {
      "status": "passed|failed",
      "exitCode": 0,
      "command": "npm run test",
      "passing": "{test_count}",
      "failing": "{failure_count}",
      "output": "{test_output_summary}"
    },
    "integrationTests": {
      "exists": true,
      "status": "passed|failed|skipped",
      "exitCode": 0,
      "credentialsAvailable": true,
      "command": "npm run test:integration",
      "passing": "{test_count}",
      "failing": "{failure_count}",
      "output": "{test_output_summary}"
    },
    "lint": {
      "status": "passed|failed",
      "exitCode": 0,
      "command": "npm run lint",
      "output": "{lint_output_summary}"
    },
    "structure": {
      "status": "valid|invalid|anomalies",
      "expectedFiles": {
        "packageJson": "present|missing",
        "apiSpec": "present|missing",
        "sourceFiles": "present|missing",
        "generatedApi": "present|missing",
        "tests": "present|missing"
      },
      "anomalies": ["{list_of_structural_issues}"]
    }
  },
  "moduleInfo": {
    "serviceName": "{service_name}",
    "currentVersion": "{current_version}",
    "existingProducers": ["{list_of_existing_producer_files}"],
    "existingOperations": ["{list_of_current_api_operations}"],
    "dependencies": {
      "production": ["{list_of_production_deps}"],
      "outdated": ["{list_of_potentially_outdated_deps}"]
    }
  }
}
```

## Error Conditions (FAIL FAST SCENARIOS)

- **Git not clean**: Stop execution, require clean git state
- **Module not found**: Stop execution, verify module path
- **Build fails**: Stop execution, require "fix" workflow
- **Unit tests fail**: Stop execution, require "fix" workflow  
- **Integration tests fail**: Stop execution, require "fix" workflow
- **Lint fails**: Stop execution, require "fix" workflow
- **Structure mismatch**: Stop execution, ask user for guidance

## Success Criteria

- Module exists at expected location
- Git working directory is clean
- All validation gates pass:
  - `npm run build` exits with code 0
  - `npm run test` shows 0 failures
  - `npm run test:integration` shows 0 failures (or skips gracefully)
  - `npm run lint` passes without errors
- Module structure follows expected patterns
- Memory folder created and baseline documented

## Failure Handling

If ANY validation fails:
1. **STOP execution immediately** - do not proceed to subsequent tasks
2. **Record failure details** in output file with status "failed"
3. **Inform user of failure** and recommend appropriate action:
   - Git issues: "Clean git state required"
   - Build/test failures: "Use 'fix' workflow to resolve issues"
   - Structure issues: "Module structure needs review"
4. **Do not attempt fixes** - this task only validates, doesn't fix

## Next Steps

**On Success**: Proceed to Task 02 (Analyze Existing Structure)
**On Failure**: User must resolve issues before update workflow can continue

## 🚨 Critical Rules

1. **FAIL FAST MANDATORY** - Any validation failure stops the entire update process
2. **NO FIXES ATTEMPTED** - This task only validates, never attempts repairs
3. **CLEAN GIT REQUIRED** - Absolutely no uncommitted changes allowed
4. **ALL TESTS MUST PASS** - Zero tolerance for existing test failures
5. **STRUCTURE VALIDATION** - Stop if module doesn't match expected patterns