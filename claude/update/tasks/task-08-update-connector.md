# Task 08: Update Main Connector

## Prerequisites

**🚨 CRITICAL**: Before starting this task, read `CLAUDE.md` to understand the project structure, rules, and requirements.

## Overview

This task updates the main connector implementation to provide access to new producer functionality. It adds only NEW producer getter methods while preserving all existing connector functionality exactly.

## Input Requirements

- Task 07 output file: `.claude/.localmemory/update-{module-identifier}/task-07-output.json` (extended/new producers)
- All previous task outputs for context

## Process Steps

### 0. Context Management and Goal Reminder

**🚨 MANDATORY FIRST STEP**: Read and understand the original user intent:

1. **Review producer changes**:
   - Understand which producers were extended or created
   - Identify new producer getter methods needed
   - Focus only on adding NEW getters, don't modify existing methods

### 1. Analyze Required Connector Updates

1. **Examine generated connector interface**:
   - Read `generated/api/index.ts` for the main Connector interface
   - Identify new getter methods that need to be implemented
   - Map new methods to producers created/extended in Task 07

2. **Plan connector updates**:
   - NEW producer getters for extended producers (if interface changed)
   - NEW producer getters for newly created producers
   - Preserve all existing producer getters exactly

### 2. Update Main Connector Implementation

1. **Backup existing connector**:
   - Create backup: `src/{Service}Impl.ts.backup-{timestamp}`
   - Preserve all existing methods and properties

2. **Add new producer instance variables**:
   - Add private properties for new producers (if any)
   - Follow existing naming patterns for producer properties
   - Initialize as undefined for lazy loading

3. **Add new producer getter methods**:
   - **🚨 CRITICAL**: Do not modify existing getter methods
   - Add new getter methods following existing patterns
   - Use lazy initialization pattern matching existing code
   - Return correct producer types as defined in generated interface

### 3. Connector Implementation Pattern

**For new producer getters:**

```typescript
export class ServiceConnectorImpl implements ServiceConnector {
  // Existing properties preserved exactly
  private newProducer?: NewProducerImpl;

  // Existing methods preserved exactly
  
  // NEW producer getter (if needed)
  getNewProducer(): NewProducerApi {
    if (!this.newProducer) {
      this.newProducer = new NewProducerImpl(this.client);
    }
    return this.newProducer;
  }
}
```

### 4. Preserve Standard Methods

Ensure all standard connector methods remain exactly the same:

1. **Connection methods**: `connect()`, `isConnected()`, `disconnect()`
2. **Standard methods**: `isSupported()`, `metadata()`
3. **Existing producer getters**: All existing getters unchanged

### 5. Build Validation (CRITICAL GATE)

1. **TypeScript compilation**:
   ```bash
   npm run build
   ```

2. **Build success requirement**:
   - **CRITICAL**: Build must exit with code 0
   - Fix any TypeScript interface implementation errors
   - Ensure all required interface methods are implemented

### 6. Test Existing Functionality

1. **Run existing tests**:
   ```bash
   npm run test
   ```

2. **Test requirements**:
   - **CRITICAL**: All existing unit tests must pass
   - Zero tolerance for regressions in existing connector functionality

## Output Format

Store the following JSON in memory file: `.claude/.localmemory/update-{module-identifier}/task-08-output.json`

```json
{
  "status": "completed|failed|error",
  "timestamp": "{iso_timestamp}",
  "connectorUpdate": {
    "connectorFile": "src/{Service}Impl.ts",
    "backupFile": "src/{Service}Impl.ts.backup-{timestamp}",
    "existingMethodsPreserved": true,
    "newProducerGetters": [
      {
        "getterMethod": "get{Producer}",
        "returnType": "{Producer}Api",
        "producerClass": "{Producer}Impl",
        "lazyInitialization": true
      }
    ],
    "newProducerProperties": [
      {
        "propertyName": "{producer}Producer",
        "type": "{Producer}Impl",
        "optional": true
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

- **Task 07 not completed**: Stop execution, producers must be extended first
- **Build fails**: Stop execution, fix TypeScript interface implementation errors
- **Existing tests fail**: Stop execution, connector changes broke existing functionality
- **Interface methods missing**: Stop execution, all required interface methods must be implemented

## Success Criteria

- New producer getter methods added to connector
- All existing connector methods preserved exactly (no changes)
- All generated connector interface methods implemented
- TypeScript compilation passes (`npm run build` exits with code 0)
- All existing unit tests continue to pass (no regressions)
- Lazy initialization pattern followed for new producers
- Producer getter naming follows existing conventions

## 🚨 Critical Rules

1. **NO MODIFICATIONS TO EXISTING METHODS** - All existing connector methods must remain exactly the same
2. **BUILD GATE MANDATORY** - Build must pass or task fails
3. **COMPLETE INTERFACE IMPLEMENTATION** - All required connector interface methods must be implemented
4. **FOLLOW EXISTING PATTERNS** - New getters must match existing getter patterns exactly
5. **LAZY INITIALIZATION** - Use same lazy loading pattern as existing producer getters
6. **ZERO REGRESSIONS** - All existing tests must continue to pass

## Implementation Checklist

For each new producer getter:

- [ ] Private producer property added (optional type)
- [ ] Getter method follows existing naming convention
- [ ] Lazy initialization pattern used (check if undefined)
- [ ] Correct producer constructor called with `this.client`
- [ ] Return type matches generated interface
- [ ] Method signature matches generated interface exactly

## Next Steps

**On Success**: Proceed to Task 09 (Update Module Exports)
**On Build/Test Failure**: Fix connector implementation issues