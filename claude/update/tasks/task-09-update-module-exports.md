# Task 09: Update Module Exports

## Prerequisites

**🚨 CRITICAL**: Before starting this task, read `CLAUDE.md` to understand the project structure, rules, and requirements.

## Overview

This task updates the module's entry point (`src/index.ts`) to export new functionality while preserving all existing exports exactly. This ensures new producers and mappers are accessible to module consumers.

## Input Requirements

- Task 08 output file: `.claude/.localmemory/update-{module-identifier}/task-08-output.json` (updated connector)
- All previous task outputs for context

## Process Steps

### 0. Context Management and Goal Reminder

**🚨 MANDATORY FIRST STEP**: Read and understand the original user intent:

1. **Review added functionality**:
   - Identify new producers created/extended
   - Identify new mappers added
   - Ensure all new functionality is properly exportable

### 1. Analyze Current Module Exports

1. **Examine current exports**:
   - Read `src/index.ts` to understand existing export structure
   - Document current factory function and exports
   - Ensure all existing exports are preserved exactly

2. **Identify required new exports**:
   - New producer implementation classes (if any were created)
   - New mapper functions from Task 06
   - Any new utility functions or types

### 2. Update Module Entry Point

1. **Backup existing entry point**:
   - Create backup: `src/index.ts.backup-{timestamp}`
   - Preserve all existing exports

2. **Add new exports while preserving existing**:
   - **🚨 CRITICAL**: Do not modify existing export statements
   - Add new export statements for new functionality
   - Maintain consistent export patterns with existing code

### 3. Export Requirements

**Factory Function**: Preserve existing factory function exactly:
```typescript
// Existing factory function remains unchanged
export function newServiceName(): ServiceConnectorImpl {
  return new ServiceConnectorImpl();
}
```

**New Exports to Add**:
- New producer implementation classes (if created)
- New mapper functions from extended mappers
- Any new types or utilities

**Export Pattern**:
```typescript
// Preserve all existing exports exactly

// Add new producer exports (if any new producers were created)
export { NewProducerImpl } from './NewProducerImpl';

// Existing mapper exports remain, new mappers added to existing export
export * from './mappers'; // This already includes new mappers from Task 06

// All other existing exports preserved
```

### 4. Validate Export Completeness

1. **Verify all new functionality is exported**:
   - New producers can be imported by consumers
   - New mappers are accessible for testing/debugging
   - Factory function provides access to all new functionality

2. **Test export accessibility**:
   - Verify imports work correctly
   - Check that TypeScript can resolve all exported types
   - Ensure backward compatibility maintained

### 5. Build Validation (CRITICAL GATE)

1. **TypeScript compilation**:
   ```bash
   npm run build
   ```

2. **Build success requirement**:
   - **CRITICAL**: Build must exit with code 0
   - Fix any export/import resolution errors
   - Ensure all exports are properly typed

### 6. Test Module Import

1. **Verify factory function works**:
   - Test that factory function returns working instance
   - Verify new functionality is accessible through returned instance
   - Check that existing functionality still works

2. **Run existing tests**:
   ```bash
   npm run test
   ```

3. **Test requirements**:
   - **CRITICAL**: All existing unit tests must pass
   - Tests can import both old and new functionality
   - No regressions in module interface

## Output Format

Store the following JSON in memory file: `.claude/.localmemory/update-{module-identifier}/task-09-output.json`

```json
{
  "status": "completed|failed|error",
  "timestamp": "{iso_timestamp}",
  "moduleExports": {
    "entryPointFile": "src/index.ts",
    "backupFile": "src/index.ts.backup-{timestamp}",
    "existingExportsPreserved": true,
    "factoryFunctionUnchanged": true,
    "newExports": [
      {
        "exportType": "class|function|type",
        "name": "{export_name}",
        "source": "{source_file}",
        "description": "{export_purpose}"
      }
    ],
    "exportValidation": {
      "allNewFunctionalityExported": true,
      "noExportConflicts": true,
      "backwardCompatible": true
    }
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
    "moduleInterface": {
      "factoryFunctionWorks": true,
      "newFunctionalityAccessible": true,
      "existingFunctionalityPreserved": true
    }
  }
}
```

## Error Conditions

- **Task 08 not completed**: Stop execution, connector must be updated first
- **Build fails**: Stop execution, fix export/import errors
- **Existing tests fail**: Stop execution, export changes broke existing functionality
- **Factory function broken**: Stop execution, factory must continue to work

## Success Criteria

- All new functionality properly exported from module
- All existing exports preserved exactly (no changes)
- Factory function continues to work and provides access to new functionality
- TypeScript compilation passes (`npm run build` exits with code 0)
- All existing unit tests continue to pass (no regressions)
- Module consumers can access both old and new functionality
- Export patterns consistent with existing code

## 🚨 Critical Rules

1. **NO MODIFICATIONS TO EXISTING EXPORTS** - All existing export statements must remain exactly the same
2. **FACTORY FUNCTION PRESERVED** - Factory function must remain unchanged and functional
3. **BUILD GATE MANDATORY** - Build must pass or task fails
4. **COMPLETE EXPORT COVERAGE** - All new functionality must be accessible to consumers
5. **BACKWARD COMPATIBILITY** - Existing import statements must continue to work
6. **ZERO REGRESSIONS** - All existing tests must continue to pass

## Export Checklist

- [ ] All existing export statements preserved
- [ ] Factory function unchanged and working
- [ ] New producer classes exported (if any created)
- [ ] New mapper functions accessible via existing mapper export
- [ ] No export name conflicts
- [ ] TypeScript can resolve all exported types
- [ ] Build passes with all exports
- [ ] Existing tests pass with updated exports

## Rollback Procedure

If export updates cause issues:

1. **Stop all work immediately**
2. **Restore from backup**: `cp src/index.ts.backup-{timestamp} src/index.ts`
3. **Verify restoration**: `npm run build && npm run test`
4. **Report issue** with specific error details

## Next Steps

**On Success**: Proceed to Task 10 (Add Unit and Integration Tests)
**On Build/Test Failure**: Fix export/import issues