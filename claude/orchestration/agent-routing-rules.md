# Agent Routing Rules

How the general purpose agent routes user requests to specialist agents.

## Request Pattern Matching

### Keywords → Task Type Mapping

| Keywords | Task Type | Workflow |
|----------|-----------|----------|
| "add [operation]" | Add single operation | add-operation-workflow |
| "add [op1], [op2], [op3]" | Add multiple operations | update-module-workflow |
| "create [service] module" | Create new module | create-module-workflow |
| "fix [issue]" | Fix problem | fix-issue-workflow |
| "validate", "check", "review" | Validation only | invoke reviewer agents |
| "test" | Testing only | invoke test agents |
| "research [api]" | Research only | invoke analysis agents |

## Routing Rules by Request Type

### 1. Add Single Operation

**User says:**
- "Add listWebhooks operation"
- "Implement getWebhook"
- "Create deleteWebhook method"

**Route to:** `add-operation-workflow.md`

**Agent Sequence:**
```
1. @credential-manager - Check credentials FIRST (MANDATORY)
2. @api-researcher - Research endpoint (if new/undocumented)
3. @operation-analyst - Validate operation coverage
4. @api-architect - Update API specification
5. @schema-specialist - Design/update schemas
6. @api-reviewer - Validate specification (Gate 1)
7. @build-validator - Generate types (Gate 2)
8. @operation-engineer - Implement operation (Gate 3)
9. @mapping-engineer - Create/update mappers (Gate 3)
10. @style-reviewer - Check code quality (Gate 3)
11. @test-orchestrator - Coordinate testing:
    - @mock-specialist - Setup HTTP mocks
    - @producer-ut-engineer - Unit tests
    - @producer-it-engineer - Integration tests
    - @ut-reviewer - Review unit tests (Gate 4)
    - @it-reviewer - Review integration tests (Gate 4)
    - Run tests (Gate 5)
12. @build-reviewer - Build & shrinkwrap (Gate 6)
13. @gate-controller - Validate all gates
```

### 2. Add Multiple Operations

**User says:**
- "Add webhook CRUD operations"
- "Add list and get operations"
- "Implement create, update, delete"

**Route to:** `update-module-workflow.md`

**Strategy:** Break down into individual operations, execute sequentially

**Agent Sequence (per operation):**
```
For each operation:
  Execute add-operation-workflow
  Mark complete before next operation
```

**Example:**
```
Request: "Add webhook CRUD"

Operations identified: list, get, create, update, delete

Execution:
1. Add listWebhooks (complete workflow)
2. Add getWebhook (complete workflow)
3. Add createWebhook (complete workflow)
4. Add updateWebhook (complete workflow)
5. Add deleteWebhook (complete workflow)
```

### 3. Create New Module

**User says:**
- "Create GitHub module"
- "New module for Slack API"
- "Build Stripe integration module"

**Route to:** `create-module-workflow.md`

**Agent Sequence:**
```
Phase 1: Discovery & Setup
1. @credential-manager - Setup credentials infrastructure
2. @product-specialist - Research product/API
3. @api-researcher - Test authentication endpoint
4. @operation-analyst - Identify MVP (connect + one operation)

Phase 2: Scaffold & Design
5. Scaffold with Yeoman
6. @api-architect - Design minimal API spec (connect + one op)
7. @schema-specialist - Design connection and operation schemas
8. @security-auditor - Review authentication approach
9. @api-reviewer - Validate specification (Gate 1)

Phase 3: Type Generation
10. @build-validator - Generate types (Gate 2)

Phase 4: Implementation
11. @client-engineer - Implement Client class
12. @operation-engineer - Implement first Producer
13. @mapping-engineer - Create Mappers
14. @style-reviewer - Review code quality (Gate 3)

Phase 5: Testing
15. @test-orchestrator - Coordinate all testing:
    - @connection-ut-engineer - Connection unit tests
    - @producer-ut-engineer - Producer unit tests
    - @connection-it-engineer - Connection integration test
    - @producer-it-engineer - Producer integration test
    - @ut-reviewer + @it-reviewer - Review tests (Gate 4)
    - Run all tests (Gate 5)

Phase 6: Documentation & Finalization
16. @documentation-writer - Create USERGUIDE.md
17. @build-reviewer - Build & shrinkwrap (Gate 6)
18. @gate-controller - Validate all gates
```

### 4. Fix Issue

**User says:**
- "Fix the failing build"
- "Tests are failing"
- "Type error in WebhookProducer"

**Route to:** `fix-issue-workflow.md`

**Agent Sequence (diagnostic first):**
```
1. Identify issue type:
   - Build error → @build-reviewer diagnose
   - Type error → @typescript-expert diagnose
   - Test failure → @test-orchestrator diagnose
   - API spec error → @api-reviewer diagnose

2. Route to specialist for fix:
   - Type issues → @typescript-expert fix
   - Logic issues → @operation-engineer fix
   - Mapper issues → @mapping-engineer fix
   - Test issues → test engineers fix
   - Spec issues → @api-architect fix

3. Validate fix:
   - Re-run failed gate
   - @gate-controller validate
```

### 5. Validation/Review Only

**User says:**
- "Validate the API spec"
- "Review the tests"
- "Check code quality"

**Direct routing to reviewer:**
```
"validate api" → @api-reviewer
"review tests" → @ut-reviewer + @it-reviewer
"check code" → @style-reviewer
"check gates" → @gate-controller
"validate build" → @build-reviewer
```

### 6. Research/Analysis Only

**User says:**
- "Research GitHub webhooks API"
- "Analyze what operations we need"
- "Check credentials"

**Direct routing to analysts:**
```
"research api" → @api-researcher
"analyze operations" → @operation-analyst
"check credentials" → @credential-manager
"understand product" → @product-specialist
```

### 7. Testing Only

**User says:**
- "Create tests for WebhookProducer"
- "Add integration tests"
- "Setup mocks"

**Route to test agents:**
```
"create unit tests" → @producer-ut-engineer
"create integration tests" → @producer-it-engineer
"setup mocks" → @mock-specialist
"coordinate testing" → @test-orchestrator
```

## Routing Decision Algorithm

```python
def route_request(user_request):
    # Step 1: Check for credentials (ALWAYS FIRST)
    invoke("@credential-manager check credentials")

    # Step 2: Identify task type
    if matches(user_request, "add (one|a|an) .*operation"):
        return "add-operation-workflow"

    elif matches(user_request, "add .*(crud|operations|and|,)"):
        return "update-module-workflow"

    elif matches(user_request, "create.*module"):
        return "create-module-workflow"

    elif matches(user_request, "fix|failing|error|broken"):
        return "fix-issue-workflow"

    elif matches(user_request, "validate|review|check"):
        return route_to_reviewer(user_request)

    elif matches(user_request, "research|analyze|investigate"):
        return route_to_analyst(user_request)

    elif matches(user_request, "test"):
        return route_to_test_engineer(user_request)

    else:
        # Unclear - ask user
        return ask_user_for_clarification()
```

## Context-Based Routing

### If module exists:
```
Operation request → add-operation-workflow
Multiple operations → update-module-workflow
```

### If module doesn't exist:
```
Any operation request → create-module-workflow first
Then → add-operation-workflow for additional ops
```

### If in middle of workflow:
```
Continue current workflow
Don't start new workflow
```

## Special Routing Cases

### Case 1: Credential Missing (User says "continue without")
```
@credential-manager reports missing credentials
↓
User approves "continue without credentials"
↓
Skip integration tests
Proceed with workflow
Flag integration tests as skipped
```

### Case 2: Gate Failure
```
Gate N fails
↓
STOP current workflow
↓
Route to specialist for fix:
  Gate 1 fail → @api-architect + @api-reviewer
  Gate 2 fail → @build-validator + @api-architect
  Gate 3 fail → @typescript-expert + engineers
  Gate 4 fail → test engineers
  Gate 5 fail → @test-orchestrator
  Gate 6 fail → @build-reviewer
↓
Re-validate gate
↓
If PASS → Resume workflow
```

### Case 3: Multi-Phase Request
```
User: "Create GitHub module with webhook CRUD"
↓
Break down:
  Phase 1: Create module (create-module-workflow)
  Phase 2: Add list, get operations (already in MVP)
  Phase 3: Add create, update, delete (update-module-workflow)
```

### Case 4: Ambiguous Request
```
User: "Do something with webhooks"
↓
Ask clarification:
  "What would you like to do with webhooks?"
  Options:
  1. Create webhook module
  2. Add webhook operations to existing module
  3. Research webhook API
  4. Fix webhook-related issues
```

## Agent Collaboration Patterns

### Pattern: Design Team
```
@api-architect + @schema-specialist + @security-auditor
Use when: Designing API specification with security considerations
```

### Pattern: Implementation Team
```
@typescript-expert + @operation-engineer + @mapping-engineer
Use when: Implementing operation with type safety
```

### Pattern: Test Team
```
@test-orchestrator coordinates:
  @mock-specialist
  @producer-ut-engineer
  @producer-it-engineer
  @ut-reviewer
  @it-reviewer
Use when: Creating comprehensive test coverage
```

### Pattern: Quality Team
```
@api-reviewer + @build-reviewer + @style-reviewer + @gate-controller
Use when: Final validation before completion
```

## Routing Validation

Before routing, verify:
- ✅ Request type identified correctly
- ✅ Appropriate workflow selected
- ✅ Required agents available
- ✅ @credential-manager invoked first
- ✅ Context exists for dependent agents

After routing, track:
- ✅ Which agents were invoked
- ✅ What phase workflow is in
- ✅ Which gates passed/failed
- ✅ What context was created

## Examples

### Example 1: Simple Operation Add
```
User: "Add listWebhooks operation"

Routing:
✅ Matches: "add [operation]"
✅ Route to: add-operation-workflow
✅ First: @credential-manager
✅ Then: Full operation workflow
```

### Example 2: Multiple Operations
```
User: "Add webhook CRUD operations"

Routing:
✅ Matches: "add .* operations"
✅ Identify ops: list, get, create, update, delete
✅ Route to: update-module-workflow
✅ Execute: 5x add-operation-workflow (sequential)
```

### Example 3: New Module
```
User: "Create GitHub module"

Routing:
✅ Matches: "create .* module"
✅ Route to: create-module-workflow
✅ Check: Module doesn't exist
✅ Execute: Full module creation (12 phases)
```

### Example 4: Fix Request
```
User: "Build is failing with type errors"

Routing:
✅ Matches: "failing"
✅ Identify: Build error + type errors
✅ Route to: fix-issue-workflow
✅ Diagnose: @build-reviewer
✅ Fix: @typescript-expert
✅ Validate: @build-reviewer + @gate-controller
```

## Success Criteria

Routing is correct when:
- ✅ Right workflow selected for request type
- ✅ Right agents invoked in right order
- ✅ Credentials checked first
- ✅ Gates validated at checkpoints
- ✅ Context properly passed
- ✅ Task completes successfully
