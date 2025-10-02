# Task: Update Module

## Overview
Adds new operations to an existing module while preserving all existing functionality. Zero tolerance for breaking changes.

**IMPORTANT**: This is the parent task that orchestrates adding multiple operations. For each individual operation, it calls the [add-operation.md](./add-operation.md) task as a sub-task.

## Task Relationship
- **update-module**: Parent task for adding multiple operations
- **add-operation**: Child task called for each individual operation
- **Workflow**: update-module → (add-operation × N) → validation

## Responsible Personas
- **Product Specialist**: Research new operations
- **API Architect**: Update specification
- **TypeScript Expert**: Extend implementation
- **Integration Engineer**: Extend producers
- **Testing Specialist**: Add new tests
- **Security Auditor**: Validate no security regression

## Prerequisites
- Module exists and is stable
- Git state is clean
- All tests passing
- Build succeeds

## Input
- Module path
- Operations to add
- User's original request

## Sub-Tasks

### 0. Context Management and Goal Reminder
**MANDATORY FIRST STEP - CONTEXT CLEARING**:
- IGNORE all previous conversation context
- CLEAR mental context
- REQUEST: User should run `/clear` or `/compact` command

**MANDATORY SECOND STEP**: Read and understand original user intent:
1. Load `initial-request.json` from memory folder
2. Extract user's original update request
3. Verify all decisions align with user's intent
4. Maintain backward compatibility focus throughout

### 1. Validate Prerequisites
**Persona**: Integration Engineer
**Rules**: [build-quality.md](../../rules/build-quality.md#clean-git-state-for-updates)

**FAIL FAST Requirements**:
1. Check git status (must be clean)
2. Run build (must pass)
3. Run tests (must pass)
4. Run lint (must pass)

**Output**: `task-01-output.json`

### 2. Analyze Existing Structure
**Personas**: TypeScript Expert + API Architect
**Rules**: [implementation.md](../../rules/implementation.md)

1. Document existing operations
2. Map current producers
3. Inventory existing schemas
4. List current test coverage

**Output**: `task-02-output.json`

### 3. Analyze Requested Operations
**Persona**: Product Specialist
**Rules**: [api-specification.md](../../rules/api-specification.md#operation-discovery-process)

1. Research requested operations in API docs
2. Identify dependencies between operations
3. Check for schema conflicts
4. Plan addition order

**Output**: `task-03-output.json`

### 4. Execute Add Operation Tasks (LOOP)
**For each operation to add**:

Execute [add-operation.md](./add-operation.md) task with:
- Module context from task-02
- Operation details from task-03
- Previous operation results

**Example for 3 operations**:
```
Operation 1: listWebhooks
  → Execute add-operation task
  → Validate success
  → Commit changes

Operation 2: createWebhook
  → Execute add-operation task
  → Validate success
  → Commit changes

Operation 3: deleteWebhook
  → Execute add-operation task
  → Validate success
  → Commit changes
```

**Output**: `task-04-{operation}-output.json` for each

### 5. Final Validation (was 4. Update API Specification)
**Personas**: API Architect + Security Auditor
**Rules**: [api-specification.md](../../rules/api-specification.md)

**NO BREAKING CHANGES**:
1. Add new endpoints only
2. Extend schemas with allOf if needed
3. Never modify existing operations
4. Validate specification

**Output**: `task-04-output.json`, updated `api.yml`

### 5. Regenerate Interfaces
**Persona**: TypeScript Expert
**Rules**: [build-quality.md](../../rules/build-quality.md#code-generation-rules)

1. Run code generation
2. Verify build passes
3. Check no existing interfaces changed
4. Document new interfaces

**Output**: `task-05-output.json`

### 6. Extend Data Mappers
**Persona**: TypeScript Expert
**Rules**: [implementation.md](../../rules/implementation.md#mapper-field-validation-requirements)

**ADD ONLY**:
1. Add mappers for NEW types only
2. Never modify existing mappers
3. Validate against interfaces
4. Count fields match exactly

**Output**: `task-06-output.json`

### 7. Extend Producers
**Personas**: TypeScript Expert + Integration Engineer
**Rules**: [implementation.md](../../rules/implementation.md#producer-implementation)

1. Add methods to existing producers OR
2. Create new producers for new resources
3. Never modify existing methods
4. Use client error handling

**Output**: `task-07-output.json`

### 8. Update Connector
**Persona**: TypeScript Expert
**Rules**: [implementation.md](../../rules/implementation.md)

1. Add new producer getters
2. Preserve all existing getters exactly
3. Maintain lazy initialization pattern

**Output**: `task-08-output.json`

### 9. Update Module Exports
**Persona**: TypeScript Expert

1. Add new exports
2. Never remove/modify existing exports
3. Maintain export order

**Output**: `task-09-output.json`

### 10. Add Tests
**Persona**: Testing Specialist
**Rules**: [testing.md](../../rules/testing.md)

**NEW TESTS ONLY**:
1. Add unit tests for new operations
2. Add integration tests for new operations
3. Never modify existing tests
4. All must pass

**Output**: `task-10-output.json`

### 11. Validate Backward Compatibility
**All Personas**: Final validation
**Rules**: [testing.md](../../rules/testing.md#test-preservation)

**CRITICAL VALIDATION**:
1. All existing tests still pass
2. Build succeeds
3. Lint passes
4. No breaking changes detected

**Output**: `task-11-output.json`

## Context Management
- **Estimated Total**: 40-60% typical
- **Heavy Tasks**: API update, Implementation
- **Split Points**: After API spec, after implementation

## Decision Points
- Schema conflicts → Extend with allOf
- Producer placement → Same resource = extend, new resource = new producer
- Test failures → STOP immediately, use fix workflow

## Failure Conditions (STOP IMMEDIATELY)
- Any existing test fails
- Build breaks
- Lint fails
- Breaking change detected
- Git state dirty

## Success Criteria
- All new operations work
- All existing tests pass
- All new tests pass
- Zero breaking changes
- Build succeeds

## Git Commits
```bash
# After API update
git commit -m "feat(api): add {operation} endpoints"

# After implementation
git commit -m "feat: implement {operation} operations"

# After tests
git commit -m "test: add tests for {operation}"

# Final validation
git commit -m "chore: validate backward compatibility"
```

## Recovery Points
- Before API changes
- After regeneration
- After implementation
- After test addition