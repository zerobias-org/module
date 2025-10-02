# 🎯 CONTEXT LOADING PROTOCOL

## CRITICAL: Load ONLY What's Needed

### Core Principle
**NEVER load all rules/workflows. Load ONLY the specific files needed for the current task.**

## Loading Sequence for Each Request

### 1. IDENTIFY THE TASK
```
User says: "Add operation X to module Y"
    ↓
Task Type: add-operation
    ↓
Load ONLY:
- claude/EXECUTION-PROTOCOL.md (always)
- claude/workflow/tasks/add-operation.md
- claude/rules/api-specification-critical.md
- claude/rules/implementation.md
```

### 2. TASK-SPECIFIC LOADING

#### For "Create Module":
```bash
Read claude/EXECUTION-PROTOCOL.md
Read claude/workflow/tasks/create-module.md
Read claude/rules/prerequisites.md
Read claude/rules/api-specification-critical.md
# STOP - Don't load other rules until needed
```

#### For "Add Operation":
```bash
Read claude/EXECUTION-PROTOCOL.md
Read claude/workflow/tasks/add-operation.md
Read claude/rules/operation-naming.md   # Always for operations
Read claude/rules/api-descriptions.md   # For adding descriptions
Read claude/rules/api-specification-critical.md
Read claude/rules/api-specification.md  # Only if updating API
Read claude/rules/implementation.md     # Only when implementing
Read claude/rules/testing.md            # Only when writing tests
```

#### For "Update Module" (Multiple Operations):
```bash
Read claude/EXECUTION-PROTOCOL.md
Read claude/workflow/tasks/update-module.md
Read claude/rules/api-specification-critical.md
# Load others as each operation is added
```

#### For "Fix Issue":
```bash
Read claude/EXECUTION-PROTOCOL.md
Read claude/workflow/tasks/fix-module.md
Read claude/rules/error-handling.md
Read claude/rules/testing.md  # If tests involved
```

## 3. PHASE-BASED LOADING

### During API Specification Phase:
```bash
Read claude/rules/api-specification-critical.md
Read claude/rules/api-specification.md
# DON'T load implementation rules yet
```

### During Implementation Phase:
```bash
Read claude/rules/implementation.md
Read claude/rules/type-mapping.md
Read claude/rules/error-handling.md
# DON'T load testing rules yet
```

### During Testing Phase:
```bash
Read claude/rules/testing.md
Read claude/rules/build-quality.md
# DON'T reload API rules
```

## 4. RULE RE-LOADING PROTOCOL

### WHEN to Re-load Rules:
1. **New user request** - ALWAYS reload relevant rules
2. **Context reset/cleanup** - Reload active rules
3. **Phase transition** - Load new phase rules
4. **After long interruption** - Reload current rules

### HOW to Re-load:
```typescript
// At start of EVERY new request:
async function handleNewRequest(request: string) {
  // 1. Clear previous rule context
  clearLoadedRules();

  // 2. Identify task type
  const taskType = identifyTask(request);

  // 3. Load ONLY relevant rules
  loadRulesForTask(taskType);

  // 4. Execute with loaded rules
  executeTask(taskType);
}
```

## 5. MEMORY EFFICIENCY

### DO NOT:
- Load all rules at session start
- Keep rules from previous tasks
- Load rules "just in case"
- Pre-load future phase rules

### DO:
- Load rules per task/phase
- Unload when task completes
- Re-load for new requests
- Track what's loaded

## 6. TRACKING LOADED RULES

Create mental tracker:
```
CURRENTLY LOADED:
✓ EXECUTION-PROTOCOL.md
✓ workflow/tasks/add-operation.md
✓ rules/api-specification-critical.md
✗ rules/implementation.md (not yet needed)
✗ rules/testing.md (not yet needed)
```

## 7. EXAMPLE EXECUTION

### User: "Add getUser operation to the GitHub module"

```bash
# Step 1: Load execution protocol
Read claude/EXECUTION-PROTOCOL.md

# Step 2: Load specific workflow
Read claude/workflow/tasks/add-operation.md

# Step 3: Load API rules (for API phase)
Read claude/rules/api-specification-critical.md
Read claude/rules/api-specification.md

# Step 4: Update API spec
Edit api.yml

# Step 5: Generate types
npm run generate

# Step 6: NOW load implementation rules
Read claude/rules/implementation.md
Read claude/rules/type-mapping.md

# Step 7: Implement
Edit src/UserProducer.ts

# Step 8: NOW load testing rules
Read claude/rules/testing.md

# Step 9: Write tests
Edit test/UserProducerTest.ts
```

## 8. VALIDATION CHECKLIST

Before starting ANY task:
- [ ] Did I clear previous rule context?
- [ ] Did I identify the specific task?
- [ ] Did I load ONLY relevant rules?
- [ ] Am I loading phase-specific rules as needed?
- [ ] Will I reload for the next request?

## REMEMBER

**Precision Loading**: Load exactly what's needed, when it's needed, nothing more.

**Fresh Context**: Every new request gets fresh rule loading.

**Phase Awareness**: Load rules for current phase, not future phases.

**Memory First**: Preserve context by loading minimally.