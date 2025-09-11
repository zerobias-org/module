# Task 06: Extend Data Mappers

## Prerequisites

**🚨 CRITICAL**: Before starting this task, read `CLAUDE.md` to understand the project structure, rules, and requirements.

## Overview

This task extends the existing data mappers to handle new data types and operations added in previous tasks. It adds only NEW mapper functions while preserving all existing mapper functionality exactly.

## Input Requirements

- Task 05 output file: `.claude/.localmemory/update-{module-identifier}/task-05-output.json` (regenerated interfaces)
- Generated interfaces in `generated/api/index.ts` with new data models
- All previous task outputs for context

## Process Steps

### 0. Context Management and Goal Reminder

**🚨 MANDATORY FIRST STEP**: Read and understand the original user intent:

1. **Review previous task outputs**:
   - Understand which new operations were added
   - Identify new data types that need mapping
   - Focus only on NEW data types, don't modify existing mappers

### 1. Analyze New Data Types

1. **Examine regenerated interfaces**:
   - Read `generated/api/index.ts` and `generated/model/index.ts`
   - Identify NEW data model interfaces (compare against Task 02 baseline)
   - Document new interfaces that need mapper functions

2. **Identify required mappers**:
   - Map new interfaces to API response data
   - Determine which new operations return new data types
   - Only create mappers for data types that don't already have them

### 2. Preserve Existing Mappers

1. **Backup existing mappers**:
   - Create backup of current `src/mappers.ts`: `src/mappers.ts.backup-{timestamp}`
   - Ensure existing mapper functions remain exactly unchanged

2. **Validate existing mappers**:
   - Verify all existing mapper functions are preserved
   - Ensure no modifications to existing function signatures
   - Check that existing field mappings remain identical

### 3. Add New Mapper Functions

Following the same patterns as existing mappers:

1. **Create new mapper functions**:
   - Add mapper functions only for NEW data types
   - Follow existing naming conventions: `mapNewEntityType(raw: any): NewEntityType`
   - Use same patterns and utilities as existing mappers

2. **Implementation requirements**:
   - **🚨 CRITICAL**: Use core error patterns from `core-error-usage-guide.md` (symlinked)
   - **🚨 CRITICAL**: Use core type mappings from `core-type-mapping-guide.md` (symlinked) 
   - Follow exact same patterns as existing mappers in the file
   - Use `toEnum()` and `map()` utilities from `@auditmation/util-hub-module-utils`
   - Import core types from `@auditmation/types-core-js` (never Node.js built-ins)

3. **Mapper function pattern**:
   ```typescript
   export function mapNewEntity(raw: any): NewEntityInterface {
     return {
       // Map ALL fields from interface (required AND optional)
       id: raw.id,
       name: raw.name,
       // Apply property mappings for transformed fields
       createdAt: map(Date, `${raw.created_at}`),
       // Handle optional fields with conditional logic
       ...(raw.optional_field && { optionalField: raw.optional_field }),
       // Use toEnum for enum mappings
       ...(raw.status && { status: toEnum(StatusEnum, raw.status) }),
     };
   }
   ```

### 4. Validate Field Mappings

**🚨 CRITICAL: Complete Field Mapping Validation**:

1. **Analyze generated interfaces**:
   - For each new interface, count total fields (required + optional)
   - Verify each field exists in corresponding API response schema
   - Document all field mappings required

2. **Complete mapping requirement**:
   - **MANDATORY**: Map ALL interface fields that exist in API responses
   - **ZERO MISSING FIELDS**: Every interface field must have mapping
   - **NO EXTRA FIELDS**: Don't map fields not in interface
   - Handle both required and optional fields appropriately

### 5. Build Validation (CRITICAL GATE)

1. **TypeScript compilation**:
   ```bash
   npm run build
   ```

2. **Build success requirement**:
   - **CRITICAL**: Build must exit with code 0
   - Fix any TypeScript compilation errors immediately
   - Resolve import issues or type mismatches

### 6. Test Existing Functionality

1. **Run existing tests**:
   ```bash
   npm run test
   ```

2. **Test requirements**:
   - **CRITICAL**: All existing unit tests must pass
   - Zero tolerance for regressions in existing mapper functionality
   - New mappers don't have tests yet (that's Task 10)

## Output Format

Store the following JSON in memory file: `.claude/.localmemory/update-{module-identifier}/task-06-output.json`

```json
{
  "status": "completed|failed|error",
  "timestamp": "{iso_timestamp}",
  "mapperExtension": {
    "backupFile": "src/mappers.ts.backup-{timestamp}",
    "existingMappersPreserved": true,
    "newMapperFunctions": [
      {
        "functionName": "mapNewEntity",
        "inputType": "any",
        "outputType": "NewEntityInterface",
        "fieldsCount": "{total_fields_mapped}",
        "requiredFields": "{required_field_count}",
        "optionalFields": "{optional_field_count}",
        "mappedFields": ["{list_of_mapped_fields}"]
      }
    ],
    "propertyMappings": [
      {
        "sourceField": "{api_field_name}",
        "targetField": "{interface_field_name}",
        "transformation": "{mapping_type}"
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
    "fieldMappingValidation": [
      {
        "interface": "{interface_name}",
        "totalFields": "{interface_field_count}",
        "mappedFields": "{mapped_field_count}",
        "completeCoverage": true,
        "missingFields": []
      }
    ]
  }
}
```

## Error Conditions

- **Task 05 not completed**: Stop execution, interfaces must be regenerated first
- **Build fails**: Stop execution, fix TypeScript errors
- **Existing tests fail**: Stop execution, mapper changes broke existing functionality
- **Incomplete field mapping**: Stop execution, all interface fields must be mapped

## Success Criteria

- New mapper functions created for all new data types
- All existing mapper functions preserved exactly (no changes)
- TypeScript compilation passes (`npm run build` exits with code 0)
- All existing unit tests continue to pass (no regressions)
- Complete field mapping for all new interfaces (no missing fields)
- New mappers follow existing patterns and conventions
- Core error and type patterns followed correctly

## 🚨 Critical Rules

1. **NO MODIFICATIONS TO EXISTING MAPPERS** - Existing functions must remain exactly the same
2. **BUILD GATE MANDATORY** - Build must pass or task fails
3. **COMPLETE FIELD MAPPING REQUIRED** - ALL interface fields must be mapped (required AND optional)
4. **FOLLOW EXISTING PATTERNS** - New mappers must match existing mapper patterns exactly
5. **CORE TYPE USAGE** - Always import URL, UUID, etc. from `@auditmation/types-core-js`
6. **ZERO REGRESSIONS** - All existing tests must continue to pass

## Pattern Consistency Requirements

New mappers must follow these patterns from existing mappers:

1. **Object literal pattern** (not constructor pattern)
2. **Conditional optional fields** using `&&` operator
3. **String interpolation** for required enum/date/URL fields: `${field}`
4. **toEnum() utility** for enum mappings
5. **map() utility** for core type conversions
6. **Nested object extraction** as internal functions

## Rollback Procedure

If mapper extension causes issues:

1. **Stop all work immediately**
2. **Restore from backup**: `cp src/mappers.ts.backup-{timestamp} src/mappers.ts`
3. **Verify restoration**: `npm run build && npm run test`
4. **Report issue** with specific error details

## Next Steps

**On Success**: Proceed to Task 07 (Extend Producers)
**On Build/Test Failure**: Fix mapper implementation issues
**On Pattern Violation**: Ensure new mappers match existing patterns exactly