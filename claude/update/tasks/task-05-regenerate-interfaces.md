# Task 05: Regenerate Interfaces

## Prerequisites

**🚨 CRITICAL**: Before starting this task, read `CLAUDE.md` to understand the project structure, rules, and requirements.

## Overview

This task regenerates TypeScript interfaces and API code after updating the API specification. It ensures that the new operations are properly reflected in the generated interfaces while preserving all existing interfaces.

## Input Requirements

- Task 04 output file: `.claude/.localmemory/update-{module-identifier}/task-04-output.json`
- Updated `api.yml` file with new operations
- API validation must have passed in Task 04

## Process Steps

### 0. Context Management and Goal Reminder

**🚨 MANDATORY FIRST STEP**: Read and understand the original user intent:

1. **Review previous task outputs**:
   - Confirm API specification was updated successfully
   - Understand which operations were added
   - Ensure generation will include only intended changes

### 1. Pre-Generation Validation

1. **Verify API specification**:
   - Confirm `api.yml` contains new operations
   - Verify API validation still passes
   - Check that existing operations are unchanged

2. **Backup existing generated files**:
   - Create backup of `generated/` directory: `generated.backup-{timestamp}/`
   - Store backup path for potential rollback

### 2. Clean and Regenerate

1. **Clean existing generated files**:
   ```bash
   cd package/{vendor}/{service}
   npm run clean
   ```

2. **Regenerate all interfaces**:
   ```bash
   npm run generate
   ```

3. **Verify generation success**:
   - Check exit code is 0
   - Verify `generated/` directory is recreated
   - Confirm all expected files are present

### 3. Validate Generated Interfaces

1. **Interface completeness check**:
   - Verify all new operations have corresponding interfaces
   - Check that existing interfaces are preserved
   - Confirm API interface naming follows patterns

2. **Generated file structure**:
   - `generated/api/index.ts`: Should contain all API interfaces
   - `generated/model/index.ts`: Should contain all data models
   - New interfaces should be present for new operations

3. **Interface naming validation**:
   - New API interfaces should follow existing naming conventions
   - Producer interfaces should match tag names from API spec
   - Connector interface should remain unchanged

### 4. Build Validation (CRITICAL GATE)

1. **TypeScript compilation**:
   ```bash
   npm run build
   ```
   
2. **Build success requirement**:
   - **CRITICAL**: Build must exit with code 0
   - If build fails, generation has issues that must be resolved
   - Fix any TypeScript compilation errors immediately

3. **Interface compatibility**:
   - Existing implementation should still compile
   - New interfaces should be properly exported
   - No breaking changes to existing interfaces

### 5. Test Existing Functionality

1. **Run existing tests**:
   ```bash
   npm run test
   ```

2. **Test requirements**:
   - **CRITICAL**: All existing unit tests must pass
   - If tests fail, generation may have broken existing interfaces
   - Zero tolerance for test regressions

3. **Integration test validation** (if credentials available):
   ```bash
   npm run test:integration
   ```
   - Existing integration tests must continue to pass
   - New operations don't have tests yet (that's Task 10-11)

### 6. Analyze New Interfaces

1. **Document new interfaces**:
   - Identify new API interfaces for new operations
   - Map new interfaces to expected producer assignments
   - Note any new data models or enums

2. **Interface analysis**:
   - Verify new interfaces have all expected methods
   - Check parameter types and return types
   - Ensure interfaces match API specification

## Output Format

Store the following JSON in memory file: `.claude/.localmemory/update-{module-identifier}/task-05-output.json`

```json
{
  "status": "completed|failed|error",
  "timestamp": "{iso_timestamp}",
  "generation": {
    "backupDirectory": "generated.backup-{timestamp}",
    "commands": [
      {
        "command": "npm run clean",
        "exitCode": 0,
        "status": "passed|failed"
      },
      {
        "command": "npm run generate", 
        "exitCode": 0,
        "status": "passed|failed"
      }
    ],
    "generatedFiles": {
      "apiInterfaces": "generated/api/index.ts",
      "dataModels": "generated/model/index.ts",
      "manifest": "generated/api/manifest.json"
    }
  },
  "validation": {
    "buildValidation": {
      "command": "npm run build",
      "exitCode": 0,
      "status": "passed|failed",
      "output": "{build_output_summary}"
    },
    "unitTestValidation": {
      "command": "npm run test",
      "exitCode": 0,
      "status": "passed|failed",
      "passing": "{test_count}",
      "failing": "{failure_count}"
    },
    "integrationTestValidation": {
      "command": "npm run test:integration",
      "exitCode": 0,
      "status": "passed|failed|skipped",
      "passing": "{test_count}",
      "failing": "{failure_count}"
    }
  },
  "interfaceAnalysis": {
    "newInterfaces": [
      {
        "interfaceName": "{interface_name}",
        "type": "api|model|enum",
        "file": "{file_path}",
        "methods": ["{method_names}"],
        "relatedOperations": ["{operation_ids}"]
      }
    ],
    "existingInterfacesPreserved": true,
    "interfaceMapping": [
      {
        "tag": "{api_tag}",
        "interface": "{interface_name}",
        "expectedProducer": "{producer_name}",
        "newOperations": ["{operation_ids}"]
      }
    ]
  }
}
```

## Error Conditions

- **Task 04 not completed**: Stop execution, API spec must be updated first
- **Generation fails**: Stop execution, fix generation issues
- **Build fails after generation**: Stop execution, resolve TypeScript errors
- **Existing tests fail**: Stop execution, generation broke existing functionality
- **Backup creation fails**: Stop execution, ensure file system permissions

## Success Criteria

- Interface generation completes successfully (`npm run generate` exits with code 0)
- TypeScript compilation passes (`npm run build` exits with code 0)
- All existing unit tests continue to pass (no regressions)
- All existing integration tests continue to pass (no regressions)
- New interfaces are properly generated for new operations
- Generated interfaces follow existing naming conventions
- No breaking changes to existing interfaces

## 🚨 Critical Rules

1. **BUILD GATE MANDATORY** - Build must pass or task fails
2. **TEST REGRESSION FORBIDDEN** - All existing tests must continue passing
3. **NO BREAKING CHANGES** - Existing interfaces must remain compatible
4. **BACKUP REQUIRED** - Always backup before generation
5. **GENERATION SUCCESS REQUIRED** - Generation must complete without errors

## Rollback Procedure

If generation causes issues:

1. **Stop all work immediately**
2. **Restore from backup**:
   ```bash
   rm -rf generated/
   cp -r generated.backup-{timestamp}/ generated/
   ```
3. **Verify restoration**:
   ```bash
   npm run build && npm run test
   ```
4. **Document failure reason** in output file
5. **Report issue** to user with specific error details

## Troubleshooting Common Issues

### Generation Failures
- Check API specification syntax
- Verify generator dependencies are installed
- Ensure API spec follows OpenAPI 3.x standards

### Build Failures
- Review TypeScript compilation errors
- Check for missing imports or type definitions
- Verify generated interfaces are well-formed

### Test Failures
- Check if existing implementation is compatible with new interfaces
- Verify no breaking changes were introduced
- Ensure test dependencies are still satisfied

## Next Steps

**On Success**: Proceed to Task 06 (Extend Data Mappers)
**On Generation Failure**: Fix API specification or generation issues
**On Build/Test Failure**: Resolve compatibility issues or rollback