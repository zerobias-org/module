# Update Module Workflow

Workflow for adding multiple operations to an existing module.

## Workflow Overview

**Duration:** Varies by operation count (~45 min per operation)
**Strategy:** Sequential execution (one operation at a time)
**Gates:** All 6 gates per operation
**Outcome:** All operations implemented, tested, and validated

## Prerequisites

- Module exists and builds successfully
- Git state is clean
- Tests are passing

## High-Level Process

```
User Request: "Add webhook CRUD operations"
  ↓
Parse operations: list, get, create, update, delete
  ↓
For each operation:
  Execute add-operation-workflow
  Mark complete before next
  ↓
Final validation
```

## Workflow Phases

### Phase 0: Request Analysis

**Agents:** @operation-analyst

**Actions:**
```
@operation-analyst Analyze request and identify all operations

Parse request:
- Identify keywords: CRUD, list, get, create, update, delete
- Map to operations
- Determine implementation order
- Check dependencies

Output:
- List of operations to implement
- Priority order
- Dependencies mapped
```

**Example:**
```
Request: "Add webhook CRUD operations"

Operations identified:
1. listWebhooks (P1 - list first for testing)
2. getWebhook (P1 - get next)
3. createWebhook (P2 - create after read ops)
4. updateWebhook (P2 - update requires get)
5. deleteWebhook (P3 - delete last)

Implementation order: list → get → create → update → delete
```

---

### Phase 1: Credential Check (MANDATORY)

**Agent:** @credential-manager

**Actions:**
```
@credential-manager Check credentials once for all operations

Decision applies to all operations:
- Credentials available → All operations include integration tests
- Credentials missing, user approves proceed → Skip all integration tests
- Credentials missing, no decision → STOP entire workflow
```

---

### Phase 2: Sequential Operation Implementation

**Strategy:** Execute add-operation-workflow for each operation

```
FOR EACH operation IN operations:
  1. Execute add-operation-workflow.md
  2. Wait for completion
  3. Validate all 6 gates passed
  4. Git checkpoint commit (optional)
  5. Proceed to next operation

  IF operation fails:
    STOP entire workflow
    Report failure
    Fix issue
    Resume from failed operation
```

**Progress Tracking:**
```
Operation 1 of 5: listWebhooks
  Phase 1: Research ✅
  Phase 2: Design ✅
  Phase 3: Generation ✅
  Phase 4: Implementation ✅
  Phase 5: Testing ✅
  Phase 6: Build ✅
  Status: Complete ✅

Operation 2 of 5: getWebhook
  Phase 1: Research [in progress]
  ...
```

---

### Phase 3: Final Validation

**Agents:** @build-reviewer, @gate-controller

**Actions:**
```
All operations complete
  ↓
@build-reviewer Final build validation
- Ensure all operations build together
- No conflicts between implementations
- All tests still passing

@gate-controller Final gate check
- Verify all operations passed all gates
- Check for regressions
- Validate overall module state
```

---

## Operation-Specific Considerations

### First Operation
- May require schema creation
- Sets pattern for subsequent operations
- Extra validation recommended

### Middle Operations
- Reuse existing schemas
- Follow established patterns
- Faster implementation

### Last Operation
- Final validation critical
- Check cumulative impact
- Ensure no conflicts

## Incremental Approach Benefits

1. **Testable checkpoints** - Each operation validated independently
2. **Early failure detection** - Issues found before implementing all operations
3. **Git-friendly** - Can commit after each operation
4. **Easier debugging** - Know exactly which operation caused issue
5. **Progressive complexity** - Start simple (list/get), add complexity (create/update/delete)

## Checkpoint Commits (Optional)

After each operation:
```bash
git add .
git commit -m "feat: add [operation] operation

- Implemented [operation] in [Producer]
- Added schemas and mappers
- Created unit and integration tests
- All gates passed

🤖 Generated with Claude Code"
```

## Failure Handling

### Operation Fails Mid-Workflow

```
Operation 3 of 5 fails at Gate 4 (tests)
  ↓
STOP workflow
  ↓
Report: "❌ createWebhook failed at Gate 4"
  ↓
Fix issues with test engineers
  ↓
Re-run Gate 4
  ↓
IF PASS:
  Complete operation 3
  Resume with operation 4
```

### All Operations Complete but Final Build Fails

```
All 5 operations individually passed
  ↓
Final build fails (conflicts)
  ↓
@build-reviewer diagnose
  ↓
@typescript-expert fix type conflicts
  ↓
Re-build
  ↓
@gate-controller final validation
```

## Time Estimates

- Phase 0 (Analysis): 5 min
- Phase 1 (Credentials): 5 min
- Phase 2 (Per operation): 45-70 min each
- Phase 3 (Final validation): 5-10 min

**Total for 5 operations: ~4-6 hours**

## Success Criteria

- ✅ All operations implemented
- ✅ Each operation passed all 6 gates
- ✅ Final build successful
- ✅ All tests passing (cumulative)
- ✅ No conflicts between operations
- ✅ Module ready for use

## Context Files

```
.claude/.localmemory/update-{module}/
├── _work/
│   ├── operation-analysis.md       # From @operation-analyst
│   ├── operation-1-api-research.md
│   ├── operation-2-api-research.md
│   └── ...
├── operation-1-status.json
├── operation-2-status.json
└── overall-status.json
```

## Workflow Diagram

```
User Request
  ↓
Analyze operations (@operation-analyst)
  ↓
Check credentials (@credential-manager)
  ↓
┌─────────────────────────────────┐
│ FOR EACH OPERATION:             │
│   ↓                              │
│   Execute add-operation-workflow │
│   ↓                              │
│   Validate all gates             │
│   ↓                              │
│   (Optional) Git commit          │
│   ↓                              │
│   Next operation                 │
└─────────────────────────────────┘
  ↓
Final validation (@build-reviewer + @gate-controller)
  ↓
Complete ✅
```
