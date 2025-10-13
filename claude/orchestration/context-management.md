# Context Management Protocol

How context and information flows between agents during workflow execution.

## Context Flow Principles

### 1. Memory as State Store
All workflow context stored in `.claude/.localmemory/{action}-{module}/`

### 2. Agent Output → Memory → Next Agent Input
Agents don't directly communicate; they share via memory files.

### 3. Structured Context Files
Predefined structure for different context types.

### 4. General Purpose Agent as Coordinator
Ensures context files exist before invoking dependent agents.

## Memory Structure

```
.claude/.localmemory/{workflow}-{module-identifier}/
├── phase-01-discovery.json      # Phase 1 output
├── phase-02-scaffolding.json    # Phase 2 output (for create-module)
├── phase-03-api-spec.json       # Phase 3 output
├── phase-04-type-generation.json # Phase 4 output
├── phase-05-implementation.json # Phase 5 output
├── phase-06-testing.json        # Phase 6 output
├── phase-07-documentation.json  # Phase 7 output (optional)
├── phase-08-build.json          # Phase 8 output
├── _work/                       # Working directory
│   ├── credentials-status.md    # From @credential-manager
│   ├── api-research.md          # From @api-researcher
│   ├── operation-analysis.md    # From @operation-analyst
│   ├── product-model.md         # From @product-specialist
│   ├── test-responses/          # API response examples
│   │   ├── list-operation.json
│   │   └── get-operation.json
│   └── reasoning/               # Decision documentation
│       ├── api-decisions.md     # From @api-architect
│       ├── type-decisions.md    # From @typescript-expert
│       └── security-decisions.md # From @security-auditor
└── gate-status.json             # From @gate-controller (optional)
```

## Context Files Specification

### phase-{NN}-{phase-name}.json
Standard format for all phase outputs. See `.claude/rules/output-file-naming.md` for complete specification.

Example `phase-01-discovery.json`:
```json
{
  "phase": 1,
  "name": "Discovery & Analysis",
  "status": "completed",
  "timestamp": "2025-10-08T10:00:00Z",
  "productPackage": "@auditlogic/product-github-github",
  "modulePackage": "@zerobias-org/module-github-github",
  "moduleIdentifier": "github-github",
  "serviceName": "GitHub",
  "credentials": {
    "status": "found",
    "authMethod": "Bearer token"
  },
  "selectedOperation": {
    "name": "listRepositories",
    "endpoint": "/user/repos"
  }
}
```

### credentials-status.md
```markdown
# Credentials Status: {module-identifier}

## Status
✅ Found in .env

## Location
.env in module directory

## Credentials Available
- GITHUB_TOKEN: Personal access token
  - Scope: repo, admin:repo_hook

## Decision
Proceed with integration tests

## Profile Type
tokenProfile.yml

## State Type
tokenConnectionState.yml (with expiresIn)
```

### api-research.md
```markdown
# API Research: {operation-name}

## Endpoint
- **URL**: /repos/{owner}/{repo}/hooks
- **Method**: GET
- **Auth**: Bearer token in Authorization header

## Parameters
- owner (path, required): Repository owner
- repo (path, required): Repository name
- per_page (query, optional): Results per page (default: 30, max: 100)
- page (query, optional): Page number (starts at 1)

## Response
- **Status**: 200 OK
- **Format**: Array of webhook objects
- **Example**: See _work/test-responses/list-webhooks.json

## Pagination
- Uses page/per_page query parameters
- Max 100 per page
- Link header provides next/prev URLs

## Rate Limiting
- 5000 requests/hour for authenticated requests
- Rate limit headers in response

## Confidence: 100%
Tested with real API on 2025-10-03
```

### operation-analysis.md
```markdown
# Operation Analysis: {module-identifier}

## User Request
"Add webhook CRUD operations"

## Operations Identified
1. **listWebhooks** - Priority: P1 (MVP)
   - Maps to: GET /repos/{owner}/{repo}/hooks
   - Dependencies: connect()

2. **getWebhook** - Priority: P1
   - Maps to: GET /repos/{owner}/{repo}/hooks/{hook_id}
   - Dependencies: connect()

3. **createWebhook** - Priority: P2
   - Maps to: POST /repos/{owner}/{repo}/hooks
   - Dependencies: connect()

4. **updateWebhook** - Priority: P2
   - Maps to: PATCH /repos/{owner}/{repo}/hooks/{hook_id}
   - Dependencies: connect(), getWebhook()

5. **deleteWebhook** - Priority: P3
   - Maps to: DELETE /repos/{owner}/{repo}/hooks/{hook_id}
   - Dependencies: connect()

## Implementation Order
1. listWebhooks (establish data structure)
2. getWebhook (single resource pattern)
3. createWebhook (write operation)
4. updateWebhook (reuses getWebhook)
5. deleteWebhook (simple, last)

## Coverage Validation
✅ All requested operations identified
✅ Complete CRUD coverage
✅ Dependencies mapped
✅ MVP identified (list + get)
```

### gate-status.json
```json
{
  "module": "github-github",
  "timestamp": "2025-10-03T14:30:00Z",
  "overall_status": "PASSED",
  "gates": {
    "gate_1": {
      "name": "API Specification",
      "status": "PASSED",
      "validator": "@api-reviewer",
      "validated_at": "2025-10-03T13:15:00Z"
    },
    "gate_2": {
      "name": "Type Generation",
      "status": "PASSED",
      "validator": "@build-validator",
      "validated_at": "2025-10-03T13:20:00Z"
    },
    "gate_3": {
      "name": "Implementation",
      "status": "PASSED",
      "validator": "@typescript-expert",
      "validated_at": "2025-10-03T13:45:00Z"
    },
    "gate_4": {
      "name": "Test Creation",
      "status": "PASSED",
      "validator": "@test-orchestrator",
      "validated_at": "2025-10-03T14:10:00Z"
    },
    "gate_5": {
      "name": "Test Execution",
      "status": "PASSED",
      "validator": "@test-orchestrator",
      "validated_at": "2025-10-03T14:20:00Z",
      "test_results": {
        "total": 15,
        "passing": 15,
        "failing": 0
      }
    },
    "gate_6": {
      "name": "Build",
      "status": "PASSED",
      "validator": "@build-reviewer",
      "validated_at": "2025-10-03T14:30:00Z"
    }
  }
}
```

### Working Files (_work/)
Additional context files for detailed information and decision tracking. These are intermediate files used during workflow execution, not standardized phase outputs.

## Context Passing Patterns

### Pattern 1: Sequential Agent Handoff

```
@api-researcher executes
  ↓
  Writes: _work/api-research.md
  Saves: _work/test-responses/list-webhooks.json
  ↓
@api-architect invoked
  ↓
  Reads: _work/api-research.md
  Uses: Endpoint details, parameters, response structure
  ↓
  Writes: api.yml (actual specification)
  ↓
@api-reviewer invoked
  ↓
  Reads: api.yml
  Validates: Against rules
```

### Pattern 2: Parallel Agent Coordination

```
@operation-engineer implements
  (parallel with)
@mapping-engineer creates mappers
  ↓
Both complete
  ↓
@style-reviewer validates both
  ↓
Reads: src/*Producer.ts, src/Mappers.ts
```

### Pattern 3: Cumulative Context

```
Phase 1: @api-researcher creates api-research.md
Phase 2: @api-architect creates api.yml (uses api-research.md)
Phase 3: @build-validator generates types (uses api.yml)
Phase 4: @operation-engineer implements (uses generated types + api-research.md)

All phases build on previous phase outputs
```

## Context Reading Protocol

### Before Invoking Agent

General purpose agent checks:
```javascript
function invokeAgent(agent, dependencies) {
  // Check dependencies exist
  for (const dep of dependencies) {
    if (!fileExists(dep)) {
      throw new Error(`Missing dependency: ${dep}`);
    }
  }

  // Invoke agent with context
  agent.execute({
    workingDirectory: '.claude/.localmemory/{module}/_work/',
    dependencies: dependencies
  });
}
```

### Example: Invoking @api-architect

```javascript
invokeAgent('@api-architect', {
  dependencies: [
    '_work/api-research.md',     // API details
    '_work/operation-analysis.md' // Operation coverage
  ],
  output: 'api.yml',
  instructions: 'Design API specification for listWebhooks operation'
});
```

## Context Cleanup

### During Workflow
Keep all context - needed for later phases

### After Successful Completion
```
Keep:
- phase-{NN}-*.json files (all phase outputs)
- gate-status.json (if used)

Optional cleanup:
- _work/ directory (can be removed, but useful for reference)
```

### After Failed Workflow
Keep everything for debugging and recovery

## Context Size Management

### If context grows large:
1. **Summarize** completed phases
2. **Archive** detailed outputs
3. **Keep** only essential for next phases
4. **Reference** archived files instead of including full content

### Example Summarization:
```
# Phase 1 Summary
- API researched: GET /repos/{owner}/{repo}/hooks
- Response structure documented
- Pagination identified: page/per_page
- Full details: _work/api-research.md (archived)
```

## Error Context

When errors occur, capture:
```json
{
  "error_type": "gate_failure",
  "failed_gate": "gate_3",
  "failed_agent": "@operation-engineer",
  "error_message": "Promise<any> found in src/WebhookProducer.ts:45",
  "context": {
    "file": "src/WebhookProducer.ts",
    "line": 45,
    "issue": "Using Promise<any> instead of Promise<Webhook[]>"
  },
  "recovery_action": "Fix type and re-validate gate 3"
}
```

## Context Validation

Before proceeding to next phase:
```javascript
function validatePhaseContext(phase) {
  const required = getRequiredContext(phase);

  for (const file of required) {
    if (!exists(file)) {
      throw new Error(`Missing required context: ${file}`);
    }
    if (!isValid(file)) {
      throw new Error(`Invalid context file: ${file}`);
    }
  }
}
```

## Success Indicators

Context management is working when:
- ✅ Agents find all needed information
- ✅ No duplicate information collection
- ✅ Context files well-organized
- ✅ Easy to resume after interruption
- ✅ Clear audit trail of decisions
