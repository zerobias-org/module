# Task 03: Check Prerequisites

## Overview

This task verifies that all required tools and environment configurations are properly installed and meet the minimum version requirements before proceeding with module generation.

## Input Requirements

- Task 02 output file: `.claude/.localmemory/{action}-{module-identifier}/task-02-output.json`
- System environment with required tools installed

## Process Steps

### 1. Tool Version Verification

Check each required tool and verify it meets the minimum version requirement:

1. **Node.js**:
   - Minimum version: `>=16.0.0`
   - Check command: `node --version`
   - Validation pattern: `v1[6-9]\.|v[2-9]\d\.`

2. **npm**:
   - Minimum version: `>=8.0.0`
   - Check command: `npm --version`

3. **Yeoman (yo)**:
   - Minimum version: `>=4.0.0`
   - Check command: `yo --version`
   - Validation pattern: `^[4-9]\.`

4. **Java**:
   - Minimum version: `>=11.0.0`
   - Check command: `java --version`

5. **Yeoman Generator**:
   - Required package: `@auditmation/generator-hub-module`
   - Check command: `npm ls -g @auditmation/generator-hub-module`
   - **CRITICAL**: If generator is not installed, STOP execution and ask user to install it
   - Installation command: `npm install -g @auditmation/generator-hub-module`

### 2. Environment Validation

1. **PATH Environment**:
   - Verify PATH includes all required executables: node, npm, yo, java
   - Description: "Must include node, npm, yo, and java executables"

### 3. Validation Checks

Run the following validation checks:

1. **Node.js version check**:
   - Expected pattern: `v1[6-9]\.|v[2-9]\d\.`
   - Description: "Node.js version is 16 or higher"

2. **Yeoman installation check**:
   - Expected pattern: `^[4-9]\.`
   - Description: "Yeoman is installed globally"

## Process Implementation

For each tool, execute the following steps:

1. Run the check command using Bash tool
2. Parse the version output
3. Compare against minimum version requirement
4. Record success/failure status
5. If any tool fails, stop execution and report missing/outdated tools

## Output Format

Store the following JSON in memory file: `.claude/.localmemory/{action}-{module-identifier}/task-03-output.json`

```json
{
  "prerequisitesCheck": {
    "status": "passed|failed",
    "timestamp": "${iso_timestamp}",
    "tools": {
      "node": {
        "required": ">=16.0.0",
        "installed": "${actual_version}",
        "status": "passed|failed",
        "command": "node --version"
      },
      "npm": {
        "required": ">=8.0.0",
        "installed": "${actual_version}",
        "status": "passed|failed",
        "command": "npm --version"
      },
      "yo": {
        "required": ">=4.0.0",
        "installed": "${actual_version}",
        "status": "passed|failed",
        "command": "yo --version"
      },
      "java": {
        "required": ">=11.0.0",
        "installed": "${actual_version}",
        "status": "passed|failed",
        "command": "java --version"
      },
      "generator": {
        "required": "@auditmation/generator-hub-module",
        "installed": "${actual_package_version}",
        "status": "passed|failed",
        "command": "npm ls -g @auditmation/generator-hub-module"
      }
    },
    "environment": {
      "path": {
        "description": "Must include node, npm, yo, and java executables",
        "status": "passed|failed",
        "details": "${path_check_results}"
      }
    },
    "validations": [
      {
        "check": "Node.js version is 16 or higher",
        "expected": "v1[6-9]\\.|v[2-9]\\d\\.",
        "actual": "${node_version}",
        "status": "passed|failed"
      },
      {
        "check": "Yeoman is installed globally",
        "expected": "^[4-9]\\.",
        "actual": "${yo_version}",
        "status": "passed|failed"
      },
      {
        "check": "Hub module generator is installed",
        "expected": "@auditmation/generator-hub-module",
        "actual": "${generator_package}",
        "status": "passed|failed"
      }
    ],
    "failedTools": ["${list_of_failed_tools}"],
    "nextSteps": "${next_steps_or_error_message}"
  }
}
```

## Error Conditions

- **Task 02 output not found**: Stop execution, Task 02 must be completed first
- **Tool not found**: Stop execution, report which tools are missing
- **Version requirement not met**: Stop execution, report which tools need updating
- **Environment validation failed**: Stop execution, report environment issues
- **🚨 CRITICAL - Yeoman generator not installed**: STOP execution immediately, ask user to install `@auditmation/generator-hub-module` globally using: `npm install -g @auditmation/generator-hub-module`

## Success Criteria

- All required tools are installed and meet minimum version requirements
- Environment PATH includes all required executables
- **🚨 CRITICAL**: Yeoman generator `@auditmation/generator-hub-module` is installed globally
- All validation checks pass
- Prerequisites check status is "passed"
- Ready to proceed to module generation

## Failure Handling

If any prerequisite check fails:
1. Record all failure details in the output file
2. Set overall status to "failed"
3. Provide clear error messages and remediation steps
4. Stop task execution and wait for user to resolve issues
