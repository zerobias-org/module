# Request Template for Claude

## Template Format

```
🎯 TASK: [create-module|add-operation|update-module|fix-module]
📦 MODULE: [vendor/module or vendor/suite/module]
🔧 OPERATION: [operation name if applicable]
📋 LOAD CONTEXT:
  1. claude/EXECUTION-PROTOCOL.md
  2. claude/workflow/tasks/[task-name].md
  3. ONLY rules for [task-name]
```

## Examples

### Add Single Operation
```
🎯 TASK: add-operation
📦 MODULE: avigilon/alta/access
🔧 OPERATION: describeAccessToken
📋 LOAD CONTEXT:
  1. claude/EXECUTION-PROTOCOL.md
  2. claude/workflow/tasks/add-operation.md
  3. Rules: api-specification-critical, implementation (phase-based)
```

### Create New Module
```
🎯 TASK: create-module
📦 MODULE: stripe/payments
🔧 OPERATIONS: createPayment, getPayment
📋 LOAD CONTEXT:
  1. claude/EXECUTION-PROTOCOL.md
  2. claude/workflow/tasks/create-module.md
  3. Rules: prerequisites, api-specification-critical
```

### Fix Module Issue
```
🎯 TASK: fix-module
📦 MODULE: github/github
🔧 ISSUE: Failing authentication tests
📋 LOAD CONTEXT:
  1. claude/EXECUTION-PROTOCOL.md
  2. claude/workflow/tasks/fix-module.md
  3. Rules: error-handling, testing
```

## Copy-Paste Templates

### For Adding Operations:
```
Load ONLY:
- claude/EXECUTION-PROTOCOL.md
- claude/workflow/tasks/add-operation.md
- claude/rules/api-specification-critical.md (for API phase)
- claude/rules/implementation.md (for implementation phase)
- claude/rules/testing.md (for testing phase)

Task: Add [OPERATION] to [MODULE]
```

### For Creating Modules:
```
Load ONLY:
- claude/EXECUTION-PROTOCOL.md
- claude/workflow/tasks/create-module.md
- claude/rules/prerequisites.md
- claude/rules/api-specification-critical.md

Task: Create [MODULE] with [OPERATIONS]
```