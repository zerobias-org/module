# Task 07: Extend Producers

## Prerequisites

**🚨 CRITICAL**: Before starting this task, read `CLAUDE.md` to understand the project structure, rules, and requirements.

## Overview

This task extends existing producer implementations or creates new producers for the new operations. It adds only NEW methods to existing producers or creates entirely new producer files based on the tag assignments from previous tasks.

## Input Requirements

- Task 06 output file: `.claude/.localmemory/update-{module-identifier}/task-06-output.json` (extended mappers)
- Task 03 output file with producer assignment strategy
- Generated interfaces with new API methods
- All previous task outputs for context

## Process Steps

### 0. Context Management and Goal Reminder

**🚨 MANDATORY FIRST STEP**: Read and understand the original user intent:

1. **Review producer assignment strategy**:
   - Load Task 03 output to understand which operations extend existing producers vs need new ones
   - Focus only on adding NEW methods, don't modify existing methods

### 1. Analyze Producer Assignment Strategy

From Task 03 output, determine the implementation approach:

1. **Extending existing producers**:
   - Identify which existing producer files need new methods
   - Document which new operations map to existing producer tags
   - Plan new method additions without modifying existing methods

2. **Creating new producers**:
   - Identify completely new producers needed for new tags
   - Plan new producer file structure and naming
   - Ensure new producers follow existing patterns

### 2. Extend Existing Producers

For each existing producer that needs new methods:

1. **Backup existing producer files**:
   - Create backup: `src/{Producer}Impl.ts.backup-{timestamp}`
   - Preserve all existing methods exactly as they are

2. **Add new methods to existing producers**:
   - **🚨 CRITICAL**: Do not modify any existing methods
   - Add new methods following existing patterns in the file
   - Use same architectural approach as existing methods
   - Implement all new interface methods for the producer's tag

3. **Method implementation requirements**:
   - Use HTTP client from constructor: `this.client.getHttpClient()`
   - Apply new mapper functions for data transformation
   - Follow existing error handling patterns
   - Use existing PagedResults patterns for list operations
   - **NO ENVIRONMENT VARIABLES**: All parameters must come from method arguments

### 3. Create New Producers (if needed)

For new tags that don't have existing producers:

1. **Create new producer files**:
   - File: `src/{NewTag}ProducerImpl.ts`
   - Follow existing producer file structure and patterns
   - Implement the corresponding generated interface

2. **Producer implementation pattern**:
   ```typescript
   export class NewTagProducerImpl implements NewTagApi {
     private httpClient: any;

     constructor(private client: ServiceClient) {
       this.httpClient = client.getHttpClient();
     }

     // Implement all interface methods
     async list(results: PagedResults<EntityType>): Promise<void> {
       // Follow existing pagination patterns
       // Use new mapper functions
     }

     async get(id: string): Promise<EntityType> {
       // Follow existing get patterns
       // Use new mapper functions
     }
   }
   ```

### 4. Implementation Requirements

**🚨 CRITICAL IMPLEMENTATION PATTERNS**:

1. **Follow existing patterns exactly**:
   - Use same HTTP client access pattern
   - Follow same error handling approach
   - Use same pagination patterns (`results.count`, `results.pageToken`)
   - Apply same authentication patterns

2. **Mapper integration**:
   - Use new mapper functions from Task 06
   - Apply mappers with direct method reference: `response.data.map(mapNewEntity)`
   - Handle pagination results consistently

3. **Error handling**:
   - Let HTTP client handle error mapping (don't duplicate)
   - Focus on API-specific logic only
   - Follow existing error handling patterns in the file

4. **PagedResults pattern**:
   - Use `results.count` (not `totalCount`)
   - Handle `results.pageNumber` and `results.pageSize` for requests
   - Set `results.pageToken` when available (undefined when not)

### 5. Build Validation (CRITICAL GATE)

1. **TypeScript compilation**:
   ```bash
   npm run build
   ```

2. **Build success requirement**:
   - **CRITICAL**: Build must exit with code 0
   - Fix any TypeScript compilation errors
   - Ensure all interface methods are implemented

### 6. Test Existing Functionality

1. **Run existing tests**:
   ```bash
   npm run test
   ```

2. **Test requirements**:
   - **CRITICAL**: All existing unit tests must pass
   - Zero tolerance for regressions in existing producer functionality
   - New methods don't have tests yet (that's Task 10)

## Output Format

Store the following JSON in memory file: `.claude/.localmemory/update-{module-identifier}/task-07-output.json`

```json
{
  "status": "completed|failed|error",
  "timestamp": "{iso_timestamp}",
  "producerExtension": {
    "existingProducersExtended": [
      {
        "producer": "src/{Producer}Impl.ts",
        "backupFile": "src/{Producer}Impl.ts.backup-{timestamp}",
        "newMethods": [
          {
            "methodName": "{method_name}",
            "operationId": "{operation_id}",
            "httpMethod": "{http_method}",
            "returnType": "{return_type}"
          }
        ],
        "existingMethodsPreserved": true
      }
    ],
    "newProducersCreated": [
      {
        "producer": "src/{NewTag}ProducerImpl.ts",
        "className": "{NewTag}ProducerImpl",
        "interface": "{NewTag}Api",
        "tag": "{api_tag}",
        "methods": [
          {
            "methodName": "{method_name}",
            "operationId": "{operation_id}",
            "httpMethod": "{http_method}",
            "returnType": "{return_type}"
          }
        ]
      }
    ]
  },
  "validation": {
    "buildValidation": {
      "command": "npm run build",
      "exitCode": 0,
      "status": "passed|failed"
    },
    "testValidation": {
      "command": "npm run test",
      "exitCode": 0,
      "status": "passed|failed",
      "regressionCount": 0
    },
    "interfaceImplementation": {
      "allMethodsImplemented": true,
      "missingMethods": []
    }
  }
}
```

## Error Conditions

- **Task 06 not completed**: Stop execution, mappers must be extended first
- **Build fails**: Stop execution, fix TypeScript errors
- **Existing tests fail**: Stop execution, producer changes broke existing functionality
- **Interface methods missing**: Stop execution, all required methods must be implemented

## Success Criteria

- All new methods added to existing producers (existing methods unchanged)
- New producers created for new tags (if needed)
- All generated interface methods are implemented
- TypeScript compilation passes (`npm run build` exits with code 0)
- All existing unit tests continue to pass (no regressions)
- New producer methods follow existing patterns exactly
- HTTP client integration consistent with existing code
- Error handling patterns consistent with existing code

## 🚨 Critical Rules

1. **NO MODIFICATIONS TO EXISTING METHODS** - Existing producer methods must remain exactly the same
2. **BUILD GATE MANDATORY** - Build must pass or task fails
3. **COMPLETE INTERFACE IMPLEMENTATION** - All required interface methods must be implemented
4. **FOLLOW EXISTING PATTERNS** - New methods must match existing method patterns exactly
5. **NO ENVIRONMENT VARIABLES** - All parameters must come from method arguments
6. **ZERO REGRESSIONS** - All existing tests must continue to pass
7. **HTTP CLIENT CONSISTENCY** - Use same HTTP client access patterns as existing methods
8. **PAGINATION PATTERNS** - Use existing PagedResults patterns exactly

## Implementation Checklist

For each new method:

- [ ] HTTP client accessed via `this.client.getHttpClient()`
- [ ] Mapper functions applied correctly
- [ ] PagedResults pattern used for list operations
- [ ] Error handling follows existing patterns
- [ ] No environment variables used
- [ ] Method signature matches generated interface
- [ ] Return types match interface requirements
- [ ] Pagination parameters handled correctly
- [ ] Authentication follows existing patterns

## Rollback Procedure

If producer extension causes issues:

1. **Stop all work immediately**
2. **Restore from backups**:
   ```bash
   cp src/{Producer}Impl.ts.backup-{timestamp} src/{Producer}Impl.ts
   # Repeat for all modified producers
   ```
3. **Remove new producer files** (if any were created)
4. **Verify restoration**: `npm run build && npm run test`
5. **Report issue** with specific error details

## Next Steps

**On Success**: Proceed to Task 08 (Update Connector)
**On Build/Test Failure**: Fix producer implementation issues
**On Pattern Violation**: Ensure new methods match existing patterns exactly