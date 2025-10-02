# 🚨 STRICT MODE REQUEST TEMPLATE

Copy and paste this template for EVERY request to ensure compliance.

## Template for Add Operation

```
🚨 STRICT ENFORCEMENT MODE - ZERO TOLERANCE

STEP 0: PRE-EXECUTION (MANDATORY)
Complete claude/PRE-EXECUTION-MANDATORY.md checklist
Declare readiness before proceeding

TASK: add-operation
MODULE: [vendor/suite/service or vendor/service]
OPERATION: [operationName using get/list/search/create/update/delete]

LOAD FILES (EXACT ORDER):
1. claude/STRICT-ENFORCEMENT.md
2. claude/EXECUTION-PROTOCOL.md
3. claude/workflow/ADD-OPERATION-COMPLETE.md
4. claude/STOP-GATES.md
5. claude/rules/operation-naming.md
6. claude/rules/api-descriptions.md

TODOWRITE (ALL 7 STEPS):
1. Update API specification
2. Run npm generate
3. Implement operation
4. Write unit tests
5. Write integration tests
6. Run tests
7. Build project

EXECUTION SEQUENCE (NO CHANGES ALLOWED):
Step 1: Update API spec
  → STOP at Gate 1: Validate naming, descriptions, no errors
Step 2: Run npm generate
  → STOP at Gate 2: Validate types generated
Step 3: Implement operation
  → STOP at Gate 3: Validate using generated types
Step 4: Write tests (unit + integration)
  → STOP at Gate 4: Validate tests written
Step 5: Run tests
  → STOP at Gate 5: Validate all pass
Step 6: Build
  → STOP at Gate 6: Validate build success
Step 7: Mark complete ONLY if all gates passed

VALIDATION: Run ./claude/AUTO-VALIDATION-HOOK.sh after each step

FAILURE TRIGGERS (TASK AUTOMATICALLY FAILS):
❌ Using 'describe' prefix
❌ Adding error responses (4xx/5xx)
❌ Using Promise<any>
❌ Skipping generation
❌ Skipping tests
❌ Not running tests
❌ Not building
❌ Stopping before step 7
❌ Skipping any gate
❌ User needs to remind

COMPLETION CRITERIA:
✅ All 7 steps executed
✅ All 6 gates passed
✅ Tests passing
✅ Build successful
✅ No rule violations

DO NOT STOP UNTIL ALL CRITERIA MET
```

## Example Request (Copy This)

```
🚨 STRICT ENFORCEMENT MODE - ZERO TOLERANCE

STEP 0: PRE-EXECUTION (MANDATORY)
I will complete claude/PRE-EXECUTION-MANDATORY.md checklist before starting

TASK: add-operation
MODULE: avigilon/alta/access
OPERATION: getAccessLevel

LOAD FILES (EXACT ORDER):
1. claude/STRICT-ENFORCEMENT.md
2. claude/EXECUTION-PROTOCOL.md
3. claude/workflow/ADD-OPERATION-COMPLETE.md
4. claude/STOP-GATES.md
5. claude/rules/operation-naming.md
6. claude/rules/api-descriptions.md

CREATE TODOWRITE with ALL 7 steps BEFORE starting

EXECUTE: Follow exact sequence with validation at each gate

VALIDATE: Run AUTO-VALIDATION-HOOK.sh after each step

COMPLETE: Only when all 7 steps done and all 6 gates passed
```

## Request Checklist

Before sending request, verify:

- [ ] Includes "STRICT ENFORCEMENT MODE"
- [ ] Lists pre-execution requirement
- [ ] Specifies exact file load order
- [ ] Mentions TodoWrite with all 7 steps
- [ ] Lists all 6 validation gates
- [ ] Includes failure triggers
- [ ] States completion criteria
- [ ] Emphasizes "DO NOT STOP UNTIL COMPLETE"

## What NOT to Say

❌ "Add operation X" (too vague)
❌ "Implement Y" (missing enforcement)
❌ "Create operation Z" (no checklist)
❌ "Can you add..." (not strict enough)

## What TO Say

✅ Use the full template above
✅ Include all enforcement language
✅ List all gates explicitly
✅ State failure triggers
✅ Emphasize zero tolerance

## Remember

The stricter your request, the better the compliance.

Verbose enforcement > Concise flexibility

Every explicit reminder helps prevent:
- Skipped steps
- Missing tests
- Rule violations
- Incomplete work