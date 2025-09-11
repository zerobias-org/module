# Task 04: Update API Specification

## Prerequisites

**🚨 CRITICAL**: Before starting this task, read `CLAUDE.md` to understand the project structure, rules, and requirements.

## Overview

This task updates the module's `api.yml` file to include the new operations identified in Task 03. It preserves all existing operations exactly while adding only the new requested operations with proper tags and documentation.

## Input Requirements

- Task 03 output file: `.claude/.localmemory/update-{module-identifier}/task-03-output.json`
- All previous task outputs for context
- Resolved conflicts from Task 03 (if any were flagged)

## Process Steps

### 0. Context Management and Goal Reminder

**🚨 MANDATORY FIRST STEP**: Read and understand the original user intent:

1. **Read initial user prompt and analysis**:
   - Review task-01-output.json for original request
   - Review task-03-output.json for specific operations to add
   - Ensure only requested operations are added (no scope creep)

### 1. Backup Current API Specification

1. **Create backup**:
   - Copy current `api.yml` to `api.yml.backup-{timestamp}`
   - Store backup path for potential rollback
   - Verify backup was created successfully

### 2. Load Current API Specification

1. **Parse existing specification**:
   - Load and parse current `api.yml`
   - Document existing paths, operations, and components
   - Preserve all existing metadata (version, title, description, etc.)

2. **Preserve existing structure**:
   - **CRITICAL**: Do not modify existing operations
   - **CRITICAL**: Do not change API version
   - Maintain existing tag definitions
   - Keep all existing schemas and components

### 3. Add New Operations to API Specification

Based on Task 03 analysis, add only the approved new operations:

1. **Add new paths and operations**:
   - Add each new operation to appropriate path
   - Use exact method, path, and parameters from product API documentation
   - Preserve existing operations on same paths (if different methods)

2. **Operation details**:
   - **operationId**: Use descriptive, unique IDs following existing patterns
   - **tags**: Assign appropriate tags (determines producer assignment)
   - **summary**: Clear, concise operation description
   - **description**: Detailed operation documentation
   - **parameters**: Include all required and optional parameters
   - **responses**: Define response schemas for success and error cases

3. **Schema additions**:
   - Add any new request/response schemas needed
   - Reference existing schemas where appropriate
   - Follow existing naming conventions

### 4. Add or Update Tags

1. **Tag management**:
   - **Existing tags**: Add operations to existing tags where appropriate
   - **New tags**: Create new tags only when operations don't fit existing categories
   - **Tag descriptions**: Provide clear descriptions for new tags

2. **Producer assignment consistency**:
   - Ensure tag assignments match producer assignment plan from Task 03
   - Verify tag names follow existing naming patterns

### 5. Validate API Specification

1. **Validation checks**:
   ```bash
   npm run validate
   ```
   - Must pass without errors
   - Fix any validation issues immediately
   - Ensure specification is well-formed OpenAPI 3.x

2. **Linting checks**:
   ```bash
   npm run lint:api
   ```
   - Must pass API linting rules
   - Fix any style or consistency issues

### 6. Version and Metadata Management

1. **Preserve version**:
   - **CRITICAL**: Do not increment API version (lerna handles versioning)
   - Keep existing title and description unchanged
   - Preserve all existing metadata

2. **Update info if needed**:
   - Only update description if new operations significantly change module purpose
   - Get user approval before changing any existing metadata

## Output Format

Store the following JSON in memory file: `.claude/.localmemory/update-{module-identifier}/task-04-output.json`

```json
{
  "status": "completed|failed|error",
  "timestamp": "{iso_timestamp}",
  "apiSpecUpdate": {
    "backupFile": "api.yml.backup-{timestamp}",
    "originalOperationCount": "{original_count}",
    "newOperationCount": "{new_count}",
    "totalOperationCount": "{total_count}",
    "addedOperations": [
      {
        "operationId": "{operation_id}",
        "method": "{http_method}",
        "path": "{api_path}",
        "tags": ["{operation_tags}"],
        "summary": "{operation_summary}"
      }
    ],
    "addedSchemas": [
      {
        "schemaName": "{schema_name}",
        "type": "request|response|model",
        "description": "{schema_purpose}"
      }
    ],
    "tagsUpdated": [
      {
        "tagName": "{tag_name}",
        "status": "new|extended",
        "operationsAdded": ["{operation_ids}"]
      }
    ]
  },
  "validation": {
    "syntaxValidation": {
      "status": "passed|failed",
      "command": "npm run validate",
      "exitCode": 0,
      "output": "{validation_output}"
    },
    "lintingValidation": {
      "status": "passed|failed", 
      "command": "npm run lint:api",
      "exitCode": 0,
      "output": "{linting_output}"
    }
  }
}
```

## Error Conditions

- **Task 03 conflicts unresolved**: Stop execution, conflicts must be resolved first
- **API validation fails**: Stop execution, fix validation errors before proceeding
- **Backup creation fails**: Stop execution, ensure file system permissions
- **Specification malformed**: Stop execution, verify current API spec integrity

## Success Criteria

- All new operations from Task 03 successfully added to `api.yml`
- All existing operations preserved exactly (no modifications)
- API specification validates successfully (`npm run validate` passes)
- API linting passes without errors (`npm run lint:api` passes)
- Backup created successfully for rollback capability
- Tags properly assigned for producer routing
- No version changes made to API specification

## 🚨 Critical Rules

1. **NO BREAKING CHANGES** - Existing operations must remain exactly as they are
2. **NO VERSION INCREMENT** - Leave API version unchanged (lerna will handle)
3. **PRESERVE EXISTING STRUCTURE** - Do not modify existing paths, schemas, or components
4. **VALIDATION REQUIRED** - Both syntax and linting validation must pass
5. **BACKUP MANDATORY** - Always create backup before modifications
6. **EXACT OPERATION DETAILS** - Use operation details exactly as documented in product API

## Rollback Procedure

If any issues occur:

1. **Stop all work immediately**
2. **Restore from backup**:
   ```bash
   cp api.yml.backup-{timestamp} api.yml
   ```
3. **Verify restoration**:
   ```bash
   npm run validate
   ```
4. **Document failure reason** in output file
5. **Report issue** to user with specific error details

## Next Steps

**On Success**: Proceed to Task 05 (Regenerate Interfaces)
**On Validation Failure**: Fix validation errors and retry
**On Conflicts**: Return to Task 03 for conflict resolution