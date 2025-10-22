---
description: Add multiple operations to an existing module
argument-hint: <module-identifier> <operation1,operation2,operation3>
---

Execute the Add Multiple Operations workflow for module: $1

**Operations to add:** $2 (comma-separated)

Split the operations from $2 by comma, then for EACH operation:

1. **Execute /add-operation workflow** with full validation
2. **Wait for completion** before starting next operation
3. **Track progress** - mark each operation complete before next

**Execution Pattern:**
```
For each operation in [$2]:
  1. Check credentials (once at start)
  2. Research & Analysis
  3. API Specification Design → Gate 1
  4. Type Generation → Gate 2
  5. Implementation → Gate 3
  6. Testing → Gates 4 & 5
  7. Build → Gate 6
  8. Validate all gates passed

  Move to next operation only after current is complete.
```

**Example:**
```
/add-operations github-github listWebhooks,getWebhook,createWebhook
```

This will add 3 operations sequentially with full validation for each.

**Important:**
- Each operation goes through ALL 6 gates
- No skipping steps
- Full test coverage for each
- Build after each operation
- Stop on any gate failure
