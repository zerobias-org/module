# Fix Issue Workflow

Workflow for diagnosing and fixing issues in existing modules.

## Workflow Overview

**Duration:** Varies by issue complexity (15 min - 2 hours)
**Strategy:** Diagnose → Fix → Validate
**Gates:** Re-validate affected gates only
**Outcome:** Issue resolved, all gates passing

## Issue Types

1. **Build errors** - TypeScript compilation failures
2. **Test failures** - Unit or integration tests failing
3. **Type errors** - Type safety violations
4. **API spec errors** - Specification issues
5. **Logic errors** - Incorrect behavior
6. **Performance issues** - Slow operations
7. **Integration issues** - External API problems

## Workflow Phases

### Phase 1: Issue Identification

**Actions:**
```
Analyze user report or error message:
- What's failing? (build, test, runtime)
- Which component? (client, producer, mapper, test)
- Error messages?
- When did it start failing?

Classify issue:
- Build failure → @build-reviewer diagnose
- Type error → @typescript-expert diagnose
- Test failure → @test-orchestrator diagnose
- API spec error → @api-reviewer diagnose
- Logic error → @operation-engineer diagnose
```

**Decision Tree:**
```
Error contains "TypeScript"
  → Type error
  → Route to @typescript-expert

Error contains "Test" or "expect"
  → Test failure
  → Route to @test-orchestrator

Error contains "npm run build"
  → Build failure
  → Route to @build-reviewer

Error contains "api.yml" or "spec"
  → API spec error
  → Route to @api-reviewer

Error describes incorrect behavior
  → Logic error
  → Route to @operation-engineer
```

---

### Phase 2: Diagnosis

**Route to diagnostic agent based on issue type:**

#### Build Error Diagnosis
```
@build-reviewer Diagnose build failure

Actions:
- Review build output
- Identify error locations
- Check recent changes
- Determine root cause

Common causes:
- Missing imports
- Type mismatches
- Circular dependencies
- Configuration issues
```

#### Type Error Diagnosis
```
@typescript-expert Diagnose type error

Actions:
- Review type error messages
- Check type definitions
- Verify generated types
- Identify type conflicts

Common causes:
- Using Promise<any>
- Missing type imports
- Incorrect type assertions
- Mapper type mismatches
```

#### Test Failure Diagnosis
```
@test-orchestrator Diagnose test failure

Actions:
- Run tests to reproduce
- Review failing test output
- Check test expectations
- Identify failure pattern

Common causes:
- Mock setup incorrect
- Test data outdated
- API response changed
- Integration test credentials missing
```

#### API Spec Error Diagnosis
```
@api-reviewer Diagnose spec error

Actions:
- Validate api.yml
- Run validation scripts
- Check for rule violations
- Identify specific errors

Common causes:
- snake_case in properties
- Error responses defined
- Missing $ref
- Inline schemas
```

#### Logic Error Diagnosis
```
@operation-engineer Diagnose logic error

Actions:
- Review operation implementation
- Test operation manually
- Check mapper logic
- Verify HTTP requests

Common causes:
- Incorrect mapper logic
- Missing field transformations
- Wrong HTTP method/path
- Invalid parameter handling
```

---

### Phase 3: Fix Implementation

**Route to fix agent based on diagnosis:**

#### Fix Type Errors
```
@typescript-expert Fix type issues

Common fixes:
- Add missing type imports
- Change Promise<any> to Promise<Type>
- Fix type assertions
- Update mapper return types
```

#### Fix Build Errors
```
@build-reviewer coordinate fix

May involve:
- @typescript-expert for type issues
- @operation-engineer for logic issues
- @mapping-engineer for mapper issues
```

#### Fix Test Failures
```
@test-orchestrator coordinate fix

May involve:
- @mock-specialist - Fix mocks
- @producer-ut-engineer - Fix unit tests
- @producer-it-engineer - Fix integration tests
- @operation-engineer - Fix implementation causing test failure
```

#### Fix API Spec Errors
```
@api-architect + @schema-specialist Fix spec

Common fixes:
- Convert snake_case to camelCase
- Remove error responses
- Move inline schemas to components
- Add missing $ref
- Fix schema composition
```

#### Fix Logic Errors
```
@operation-engineer Fix implementation

Common fixes:
- Correct mapper field mappings
- Fix HTTP request construction
- Update parameter validation
- Correct response handling
```

---

### Phase 4: Validation

**Re-run affected gates:**

```
Determine which gates were affected:

Build error → Re-run Gates 2, 3, 6
Type error → Re-run Gates 2, 3, 6
Test error → Re-run Gates 4, 5
API spec error → Re-run Gates 1, 2
Logic error → Re-run Gates 3, 4, 5, 6

For each affected gate:
  Re-validate
  IF PASS → Continue
  IF FAIL → Return to Phase 3 (fix again)
```

**Final validation:**
```
@gate-controller Validate all gates

Ensure:
- Previously passing gates still pass
- Fixed gates now pass
- No regressions introduced
```

---

### Phase 5: Verification

**Actions:**
```
Verify fix thoroughly:

1. Run full test suite
   npm test
   - All tests must pass

2. Run full build
   npm run build
   npm run shrinkwrap
   - Build must succeed

3. Manual testing (if applicable)
   - Test the specific scenario that was failing
   - Verify expected behavior

4. Check for side effects
   - Review changes
   - Ensure no unintended impacts
```

---

## Common Issue Patterns

### Pattern 1: "Promise<any> in signature"

**Diagnosis:**
```
@typescript-expert: Using generic Promise<any> instead of typed
```

**Fix:**
```
@typescript-expert:
1. Identify correct type from generated/
2. Import type
3. Change Promise<any> to Promise<Type>
```

**Validate:**
- Gate 3 (Implementation) ✅

---

### Pattern 2: "Tests failing after adding operation"

**Diagnosis:**
```
@test-orchestrator: New operation tests failing
```

**Fix:**
```
@mock-specialist: Update mocks
@producer-ut-engineer: Fix test expectations
```

**Validate:**
- Gate 4 (Test Creation) ✅
- Gate 5 (Test Execution) ✅

---

### Pattern 3: "Build failing with type errors"

**Diagnosis:**
```
@build-reviewer: Type mismatch in mapper
```

**Fix:**
```
@mapping-engineer: Fix mapper return type
@typescript-expert: Verify type consistency
```

**Validate:**
- Gate 3 (Implementation) ✅
- Gate 6 (Build) ✅

---

### Pattern 4: "API spec validation failing"

**Diagnosis:**
```
@api-reviewer: snake_case found in properties
```

**Fix:**
```
@api-architect: Convert to camelCase
@build-validator: Regenerate types
@mapping-engineer: Update mapper field names
```

**Validate:**
- Gate 1 (API Specification) ✅
- Gate 2 (Type Generation) ✅
- Gate 3 (Implementation) ✅

---

## Prevention Strategies

After fix, document:
- What caused the issue
- How it was fixed
- How to prevent in future
- Add validation check if applicable

## Time Estimates

- Simple fix (type import): 5-10 min
- Moderate fix (mapper logic): 15-30 min
- Complex fix (spec changes): 45-60 min
- Critical fix (architecture): 1-2 hours

## Success Criteria

Fix is complete when:
- ✅ Issue resolved
- ✅ All affected gates pass
- ✅ No regressions
- ✅ Tests passing
- ✅ Build successful
- ✅ Verified manually (if applicable)

## Workflow Diagram

```
Issue Reported
  ↓
Identify issue type
  ↓
Route to diagnostic agent
  ↓
Diagnose root cause
  ↓
Route to fix agent
  ↓
Implement fix
  ↓
Re-validate affected gates
  ↓
IF PASS:
  Verify fix
  Document
  Complete ✅

IF FAIL:
  Iterate fix
  Re-validate
```
