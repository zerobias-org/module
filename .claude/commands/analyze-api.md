---
description: Create API entity diagram and analysis (30-60 min)
argument-hint: <vendor> <product>
---

Execute the API Analysis workflow for: $1 $2

**Goal:** Create comprehensive API entity diagram and operation catalog

**Workflow:**

1. **Phase 1: Documentation Research**
   - Invoke @api-researcher for **COMPLETE SCOPE** API research
   - Collect ALL available endpoints (complete catalog)
   - Document authentication methods
   - Note API versioning strategy
   - Test multiple endpoints with real API calls
   - Save example responses for all operations

2. **Phase 2: Entity Discovery**
   - Invoke @product-specialist to identify core entities
   - Map entity relationships
   - Determine primary vs secondary resources
   - Identify CRUD vs specialized operations

3. **Phase 3: Entity Diagram Creation**
   - Create visual entity relationship diagram
   - Show cardinality (one-to-many, many-to-many)
   - Document key properties per entity
   - Note special relationships or constraints

4. **Phase 4: Operation Cataloging**
   - List all available operations by entity
   - Group by: CRUD, search, specialized actions
   - Note required vs optional parameters
   - Document response formats

5. **Phase 5: Priority Analysis**
   - Invoke @operation-analyst to prioritize operations
   - Identify core vs nice-to-have operations
   - Suggest implementation order
   - Flag complex operations requiring special attention

**Outputs:**
- `_work/api-analysis/entity-diagram.md` - Visual entity diagram
- `_work/api-analysis/operation-catalog.md` - Complete operation list
- `_work/api-analysis/implementation-plan.md` - Prioritized implementation order
- `_work/api-analysis/special-notes.md` - Edge cases and requirements

**Example:**
```
/analyze-api github webhooks
```

This creates a comprehensive analysis of GitHub's webhook API including entities, relationships, and operation priorities.
