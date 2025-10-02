# 📝 EXAMPLE REQUESTS - Copy & Modify These

## For Adding a Single Operation

```
🚨 STRICT ENFORCEMENT MODE

BEFORE STARTING:
Read and complete: claude/PRE-EXECUTION-MANDATORY.md

TASK: add-operation
MODULE: avigilon/alta/access
OPERATION: getAccessToken

MANDATORY SEQUENCE (NO FLEXIBILITY):
1. Complete pre-execution checklist
2. Create TodoWrite with ALL 7 steps
3. Execute in exact order with validation gates
4. Do NOT mark complete until all gates pass

LOAD EXACTLY (in order):
1. claude/STRICT-ENFORCEMENT.md
2. claude/EXECUTION-PROTOCOL.md
3. claude/workflow/ADD-OPERATION-COMPLETE.md
4. claude/STOP-GATES.md
5. claude/rules/operation-naming.md
6. claude/rules/api-descriptions.md

CRITICAL REMINDERS:
- NEVER use 'describe' - ALWAYS use 'get'
- Tests are MANDATORY not optional
- Run tests WITHOUT being asked
- Build WITHOUT being asked
- STOP at EACH validation gate

FAILURE TRIGGERS:
- Using 'describe' prefix = FAIL
- Skipping tests = FAIL
- Stopping before build = FAIL
- User reminder needed = FAIL
```

## For Creating a New Module

```
FRESH START - Clear previous context
TASK: create-module
MODULE: stripe/payments
OPERATIONS: createPayment, getPayment

LOAD ONLY:
1. claude/EXECUTION-PROTOCOL.md
2. claude/workflow/tasks/create-module.md
3. claude/rules/prerequisites.md

VALIDATION GATES: Required at each phase
TODO TRACKING: Required
```

## For Fixing an Issue

```
FRESH START - Clear previous context
TASK: fix-module
MODULE: github/github
ISSUE: Authentication failing in tests

LOAD ONLY:
1. claude/EXECUTION-PROTOCOL.md
2. claude/workflow/tasks/fix-module.md
3. claude/rules/error-handling.md
4. claude/rules/testing.md

NO API CHANGES ALLOWED (unless fixing the issue)
```

## For Adding Multiple Operations

```
FRESH START - Clear previous context
TASK: update-module
MODULE: amazon/aws/s3
OPERATIONS: listBuckets, createBucket, deleteBucket

LOAD ONLY:
1. claude/EXECUTION-PROTOCOL.md
2. claude/workflow/tasks/update-module.md
3. Phase-specific rules as needed

PROCESS: One operation at a time
VALIDATION: After each operation
```

## Minimal Test Request

```
TEST REQUEST - Verify rule compliance
TASK: add-operation
MODULE: test/module
OPERATION: testOperation

EXPECTED BEHAVIOR:
- Load only specified files
- No error responses in API
- Run generate before implement
- No 'any' types
- Use TodoWrite
```

## Copy-Paste Template

```
[FRESH START/CONTINUE] - [Clear/Keep] context
TASK: [add-operation/create-module/update-module/fix-module]
MODULE: [vendor/suite/service or vendor/service]
OPERATION(S): [operation names]

LOAD ONLY:
1. claude/EXECUTION-PROTOCOL.md
2. claude/workflow/tasks/[task-name].md
3. [Specific rules for phase]

GATES: Validate at each checkpoint
TODOS: Track with TodoWrite
```