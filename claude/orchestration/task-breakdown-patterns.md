# Task Breakdown Patterns

How the general purpose agent decomposes complex requests into executable tasks.

## Decomposition Principles

### 1. Identify Request Type
Map user language to task category

### 2. Extract Components
Break request into discrete, actionable items

### 3. Determine Execution Strategy
Sequential, parallel, or hybrid

### 4. Create Task Sequence
Order tasks by dependencies

### 5. Validate Completeness
Ensure all user requirements covered

## Common Breakdown Patterns

### Pattern 1: Multiple Operations Request

**User Request:**
"Add webhook CRUD operations to github-github"

**Breakdown:**
```
Parse request:
  Keyword: "CRUD" → operations: create, read, update, delete
  Resource: "webhook" → base name for operations

Expand CRUD:
  Create: createWebhook
  Read: listWebhooks, getWebhook
  Update: updateWebhook
  Delete: deleteWebhook

Result: 5 operations identified

Execution strategy:
  Sequential (one at a time)

Order by dependencies:
  1. listWebhooks (establish data structure, no deps)
  2. getWebhook (single item retrieval, no deps)
  3. createWebhook (write operation, no deps)
  4. updateWebhook (needs getWebhook pattern)
  5. deleteWebhook (simple, last)
```

---

### Pattern 2: List of Named Operations

**User Request:**
"Add listDoors, unlockDoor, and lockDoor operations"

**Breakdown:**
```
Parse request:
  Explicit operations: listDoors, unlockDoor, lockDoor

No expansion needed (already explicit)

Execution strategy:
  Sequential

Order by type:
  1. listDoors (read operation, establishes structure)
  2. unlockDoor (action operation)
  3. lockDoor (action operation)
```

---

### Pattern 3: Create Module with Operations

**User Request:**
"Create GitHub module with webhook management"

**Breakdown:**
```
Primary task: Create module
  Subtask 1: MVP (connect + one operation)

Secondary task: Add operations
  Parse "webhook management" → CRUD operations
  Defer to post-MVP expansion

Execution strategy:
  Phase 1: Create module (create-module-workflow)
    - Connect + listWebhooks (MVP)
  Phase 2: Expand (update-module-workflow)
    - getWebhook, createWebhook, updateWebhook, deleteWebhook
```

---

### Pattern 4: Ambiguous Request

**User Request:**
"I need repository operations"

**Breakdown:**
```
Ambiguous: "repository operations" too vague

Clarification needed:
  Ask user: "Which repository operations do you need?"
  Options:
    - CRUD (listRepositories, getRepository, createRepository, etc.)
    - Management (archiveRepository, transferRepository, etc.)
    - Collaboration (listCollaborators, addCollaborator, etc.)

Wait for user response → Then decompose
```

---

### Pattern 5: Fix Request

**User Request:**
"The build is failing in github-github with type errors in WebhookProducer"

**Breakdown:**
```
Task type: fix-issue

Components identified:
  - Module: github-github
  - Issue type: build failure + type errors
  - Location: WebhookProducer

Execution strategy:
  Single fix workflow (no decomposition needed)

Route to:
  @build-reviewer (diagnose build)
  @typescript-expert (fix types)
```

---

## Decomposition Algorithms

### Algorithm 1: CRUD Expansion

```python
def expand_crud(resource_name):
    return [
        f"list{capitalize(pluralize(resource_name))}",  # listWebhooks
        f"get{capitalize(resource_name)}",              # getWebhook
        f"create{capitalize(resource_name)}",           # createWebhook
        f"update{capitalize(resource_name)}",           # updateWebhook
        f"delete{capitalize(resource_name)}"            # deleteWebhook
    ]
```

### Algorithm 2: Comma-Separated Operations

```python
def parse_operations(text):
    # "listWebhooks, getWebhook, createWebhook"
    operations = text.split(',')
    return [op.strip() for op in operations]
```

### Algorithm 3: Natural Language Pattern Matching

```python
def extract_operations(text):
    patterns = {
        'CRUD': expand_crud,
        'list and get': lambda r: [f'list{r}s', f'get{r}'],
        'create, update, delete': lambda r: [f'create{r}', f'update{r}', f'delete{r}']
    }

    for pattern, handler in patterns.items():
        if pattern in text.lower():
            resource = extract_resource_name(text)
            return handler(resource)
```

---

## Execution Strategy Selection

### Sequential Execution
**When:** Tasks have dependencies or modify same files
```
Example: Multiple operations to same module
- Each operation modifies api.yml, src/, test/
- Must complete one before starting next
- Prevents conflicts
```

### Parallel Execution
**When:** Tasks are independent
```
Example: Creating multiple separate modules
- Each module in separate directory
- No shared files
- Can run simultaneously (if supported)

Note: Current system uses sequential for safety
```

### Hybrid Execution
**When:** Some tasks parallel, some sequential
```
Example: Within a phase, multiple agents work in parallel
- Phase 4 (Implementation):
  - @operation-engineer implements operation
  - @mapping-engineer creates mappers
  Both can work in parallel (different files)

- Phase 5 (Testing):
  - @producer-ut-engineer creates unit tests
  - @producer-it-engineer creates integration tests
  Can work in parallel
```

---

## Operation Ordering Strategies

### Strategy 1: Type-Based Ordering

```
Priority order:
1. List operations (establish data structure)
2. Get operations (single item patterns)
3. Search operations (filtering patterns)
4. Create operations (write patterns)
5. Update operations (reuse create + get)
6. Delete operations (simple, no complex logic)
```

### Strategy 2: Dependency-Based Ordering

```
Build dependency graph:
  updateWebhook depends on getWebhook
  ↓
Topological sort:
  1. getWebhook (no dependencies)
  2. listWebhooks (no dependencies)
  3. createWebhook (no dependencies)
  4. updateWebhook (depends on getWebhook)
  5. deleteWebhook (no dependencies)
```

### Strategy 3: Complexity-Based Ordering

```
Simple to complex:
1. Simple read (get, list)
2. Simple write (create, delete)
3. Complex operations (update, batch, search with filters)
```

---

## Task Breakdown Examples

### Example 1: Comprehensive Request

**User:** "Create GitHub module with full repository management: list, get, create, update, delete, archive, transfer, and collaborator management"

**Breakdown:**
```
Phase 1: Create Module (MVP)
  - Create github-github module
  - Connect + listRepositories (MVP operation)

Phase 2: Core CRUD
  - getRepository
  - createRepository
  - updateRepository
  - deleteRepository

Phase 3: Repository Management
  - archiveRepository
  - transferRepository

Phase 4: Collaborator Management
  - listCollaborators
  - addCollaborator
  - removeCollaborator

Total: 3 phases, 10 operations
Execution: Sequential within each phase
```

### Example 2: Incremental Request

**User:** "Add listWebhooks to github-github"

**Breakdown:**
```
Single operation: listWebhooks
Execution: add-operation-workflow
No decomposition needed
```

### Example 3: Mixed Request

**User:** "Add CRUD for webhooks and just list for repositories"

**Breakdown:**
```
Group 1: Webhook CRUD
  - listWebhooks
  - getWebhook
  - createWebhook
  - updateWebhook
  - deleteWebhook

Group 2: Repository List
  - listRepositories

Total: 6 operations
Execution: Sequential, one at a time
```

---

## Validation Checkpoints

### After Decomposition

Check:
- ✅ All user requirements covered?
- ✅ No duplicate operations?
- ✅ Dependencies resolved?
- ✅ Execution order logical?
- ✅ Estimate reasonable?

### Before Execution

Confirm with user (if complex):
```
"I've identified 5 operations to add:
1. listWebhooks
2. getWebhook
3. createWebhook
4. updateWebhook
5. deleteWebhook

Estimated time: ~6 hours (sequential execution)

Proceed with this plan?"
```

---

## Edge Cases

### Case 1: Conflicting Operations

**User:** "Add createWebhook, but we already have createWebhook"

**Breakdown:**
```
Detect conflict:
  Check api.yml for existing createWebhook

Action:
  If exists: "createWebhook already exists. Did you mean updateWebhook?"
  If not exists: Proceed with createWebhook
```

### Case 2: Missing MVP

**User:** "Add updateWebhook to github-github"

**Breakdown:**
```
Check prerequisites:
  - Module exists? ✅
  - getWebhook exists? ❌

Missing dependency: getWebhook required for update pattern

Expand task:
  1. Add getWebhook (prerequisite)
  2. Add updateWebhook (original request)
```

### Case 3: Scope Too Large

**User:** "Add all GitHub API operations"

**Breakdown:**
```
Scope: 100+ operations

Too large for single execution

Response:
  "GitHub API has 100+ operations. Let's start with a subset.
   Which operations are most important?
   - Repository management?
   - Issue tracking?
   - Pull requests?
   - Webhooks?
   - Actions?"

Wait for user to narrow scope
```

---

## Success Indicators

Decomposition is effective when:
- ✅ All requirements mapped to tasks
- ✅ Dependencies identified and ordered
- ✅ Execution strategy clear
- ✅ Estimation accurate
- ✅ No task forgotten
- ✅ User confirms plan (if complex)
