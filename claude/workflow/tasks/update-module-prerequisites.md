# Update Module Prerequisites

## 🚨 FAIL FAST Prerequisites

**CRITICAL**: Before ANY update work begins, ALL validations must pass. ANY failure requires immediate stop.

## Overview

This task ensures the module is in a stable, clean state before attempting any updates. Zero tolerance for instability.

## Responsible Persona
- **Integration Engineer**

## The FAIL FAST Protocol

### Step 0: Context Management
**MANDATORY FIRST STEP - CONTEXT CLEARING**:
- IGNORE all previous conversation context
- CLEAR mental context
- REQUEST: User should run `/clear` or `/compact` command

**MANDATORY SECOND STEP**: Read and understand update intent:
1. Load user's update request
2. Identify target module
3. List operations to add
4. Confirm NO breaking changes will be made

### Step 1: Git Working Directory Check
```bash
git status --porcelain
```

**REQUIREMENT**: Output must be empty (no uncommitted changes)

**IF FAILS**:
```
❌ STOP IMMEDIATELY
Message: "Git working directory is dirty. Please commit or stash changes before updating."
Action: Exit update workflow
```

### Step 2: Build Validation
```bash
npm run build
```

**REQUIREMENT**: Exit code must be 0

**IF FAILS**:
```
❌ STOP IMMEDIATELY
Message: "Build is failing. Please fix build errors before updating."
Action: Exit update workflow
Suggestion: Consider 'fix' workflow instead
```

### Step 3: Unit Test Validation
```bash
npm run test
```

**REQUIREMENT**:
- Exit code must be 0
- Zero test failures
- No skipped tests (.skip())
- No focused tests (.only())

**IF FAILS**:
```
❌ STOP IMMEDIATELY
Message: "Unit tests are failing. All tests must pass before updating."
Details: List failing tests
Action: Exit update workflow
Suggestion: Fix tests first or use 'fix' workflow
```

### Step 4: Integration Test Validation
```bash
npm run test:integration
```

**REQUIREMENT**:
- Exit code must be 0 (if credentials available)
- OR gracefully skipped if no credentials

**IF FAILS**:
```
❌ STOP IMMEDIATELY
Message: "Integration tests are failing. All tests must pass before updating."
Details: List failing tests
Action: Exit update workflow
```

### Step 5: Lint Validation
```bash
npm run lint
```

**REQUIREMENT**:
- Zero errors
- Zero warnings (ideally)

**IF FAILS**:
```
❌ STOP IMMEDIATELY
Message: "Lint errors found. Please fix lint issues before updating."
Details: List lint errors
Action: Exit update workflow
```

### Step 6: Module Structure Validation
Verify expected structure exists:

```bash
# Check required directories
test -d src || exit 1
test -d test/unit || exit 1
test -d test/integration || exit 1
test -d generated || exit 1

# Check required files
test -f api.yml || exit 1
test -f connectionProfile.yml || exit 1
test -f package.json || exit 1
test -f tsconfig.json || exit 1

# Check generated interfaces exist
test -f generated/api/index.ts || exit 1
test -f generated/connector/index.ts || exit 1
```

**IF FAILS**:
```
❌ STOP IMMEDIATELY
Message: "Module structure is incomplete or corrupted."
Details: List missing components
Action: Exit update workflow
Suggestion: Module may need repair
```

### Step 7: Dependency Check
```bash
# Check for missing dependencies
npm ls --depth=0

# Check for security vulnerabilities
npm audit --production
```

**REQUIREMENT**:
- No missing dependencies
- No high/critical vulnerabilities

**IF FAILS**:
```
⚠️ WARNING (not blocking)
Message: "Dependency issues detected"
Details: List issues
Action: Continue but document
```

### Step 8: API Specification Validation
```bash
npx swagger-cli validate api.yml
```

**REQUIREMENT**: Valid OpenAPI specification

**IF FAILS**:
```
❌ STOP IMMEDIATELY
Message: "API specification is invalid"
Details: Validation errors
Action: Exit update workflow
```

### Step 9: TypeScript Configuration Check
```typescript
// Verify tsconfig.json has required settings
{
  "compilerOptions": {
    "skipLibCheck": true,  // Must be true
    "strict": true         // Should be true
  }
}
```

**IF FAILS**:
```
⚠️ WARNING
Message: "TypeScript configuration may need updates"
Action: Update tsconfig.json during implementation
```

### Step 10: Previous Task Status Check
If updating from a previous task sequence:

```bash
# Check for recovery context
if [ -f ".localmemory/update-{module}/recovery-context.json" ]; then
  echo "Previous update attempt detected"
  # Analyze recovery context
fi
```

## Output Schema

```json
{
  "status": "ready|blocked",
  "timestamp": "ISO-8601",
  "validationResults": {
    "gitStatus": {
      "clean": true|false,
      "status": "passed|failed",
      "details": "Working directory clean"
    },
    "build": {
      "exitCode": 0,
      "status": "passed|failed",
      "errors": []
    },
    "unitTests": {
      "total": 50,
      "passed": 50,
      "failed": 0,
      "skipped": 0,
      "status": "passed|failed"
    },
    "integrationTests": {
      "total": 10,
      "passed": 10,
      "failed": 0,
      "skipped": 0,
      "status": "passed|failed|skipped"
    },
    "lint": {
      "errors": 0,
      "warnings": 0,
      "status": "passed|failed"
    },
    "structure": {
      "complete": true|false,
      "status": "passed|failed",
      "missing": []
    },
    "dependencies": {
      "missing": 0,
      "vulnerabilities": {
        "critical": 0,
        "high": 0
      },
      "status": "passed|warning"
    },
    "apiSpec": {
      "valid": true|false,
      "status": "passed|failed"
    },
    "typescript": {
      "skipLibCheck": true|false,
      "status": "passed|warning"
    }
  },
  "overallStatus": "ready|blocked",
  "blockers": [
    "Git working directory is dirty",
    "Build failing with 3 errors"
  ],
  "warnings": [
    "2 npm vulnerabilities (low severity)"
  ]
}
```

## Decision Matrix

| Check | Result | Action |
|-------|--------|--------|
| Git dirty | FAIL | **STOP** - Cannot proceed |
| Build fails | FAIL | **STOP** - Cannot proceed |
| Unit tests fail | FAIL | **STOP** - Cannot proceed |
| Integration tests fail | FAIL | **STOP** - Cannot proceed |
| Lint errors | FAIL | **STOP** - Cannot proceed |
| Structure incomplete | FAIL | **STOP** - Cannot proceed |
| API spec invalid | FAIL | **STOP** - Cannot proceed |
| Dependencies missing | WARN | Continue with caution |
| Security vulnerabilities | WARN | Continue but document |
| TypeScript config | WARN | Fix during update |

## Critical Rules

1. **NEVER proceed with dirty git state**
2. **NEVER proceed with failing build**
3. **NEVER proceed with failing tests**
4. **NEVER modify existing tests to make them pass**
5. **NEVER skip validation steps**

## Success Criteria

✅ ALL blocking checks pass (status: "passed")
✅ Overall status is "ready"
✅ Zero blockers in output
✅ Module ready for safe update

## Next Steps

**IF ALL CHECKS PASS**:
- Proceed to Task 02: Analyze Existing Structure
- Begin update workflow

**IF ANY CHECK FAILS**:
- Document failure in output
- Exit update workflow
- Recommend appropriate action:
  - Clean git state
  - Fix build/tests
  - Switch to 'fix' workflow
  - Repair module structure

## Common Issues and Solutions

### Issue: Uncommitted Changes
**Solution**:
```bash
git stash  # Temporarily store changes
# OR
git commit -am "WIP: Save before update"
```

### Issue: Failing Tests
**Solution**:
- Run tests locally to identify issues
- Fix implementation (never modify tests)
- Consider 'fix' workflow if tests are incorrect

### Issue: Build Errors
**Solution**:
- Check recent changes
- Verify dependencies installed
- Check for TypeScript errors
- Ensure generated files are up-to-date

### Issue: Missing Structure
**Solution**:
- Regenerate from API spec
- Verify scaffolding completed
- Check for corrupted files

## Remember

This is a **FAIL FAST** validation. The goal is to catch problems early before attempting updates that could make things worse. It's better to stop and fix issues than to proceed with an unstable module.