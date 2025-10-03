# Agent Invocation Guide

How to invoke and work with Claude Code agents using the @ mention pattern.

## Quick Start

### Basic Invocation
```
@agent-name Your request here
```

### Examples
```
@api-researcher Research GitHub webhook API endpoints

@api-architect Design OpenAPI spec for webhook CRUD operations

@operation-engineer Implement listWebhooks in WebhookProducer
```

## Invocation Patterns

### 1. Single Agent for Specific Task
Use when you need one agent's expertise:

```
@credential-manager Check for GitHub credentials in .env
```

```
@api-reviewer Validate api.yml before type generation
```

```
@build-validator Run type generation and validate output
```

### 2. Multiple Agents for Collaboration
Use when task requires multiple perspectives:

```
@api-architect @schema-specialist Design GitHub webhook schema
- Full Webhook object for endpoints
- WebhookSummary for nested usage
- Proper composition with allOf
```

```
@typescript-expert @operation-engineer Implement getWebhook operation
- Use generated types
- Call mappers
- Handle errors with core types
```

### 3. Phase-Based Invocation
Use for complete workflow phases:

```
@test-orchestrator Coordinate testing for listWebhooks operation
- Create unit tests with mocks
- Create integration tests with real API
- Ensure 100% coverage
```

### 4. Validation and Review
Use agents to validate work:

```
@api-reviewer Validate api.yml changes for webhook operations
Report any rule violations
```

```
@ut-reviewer Review unit tests for WebhookProducer
Check coverage and mock usage
```

## Agent Communication Styles

### Agents Provide Context-Rich Responses

Each agent responds in character with their expertise:

**@api-researcher** (Investigation style):
```
# API Research: List Webhooks

I tested the endpoint with curl and here's what I found:

## Endpoint
- URL: GET /repos/{owner}/{repo}/hooks
- Auth: Bearer token required
- Response: Array of webhook objects

## Example Response
[saves response to _work/test-responses/list-webhooks.json]

## Pagination
Uses 'page' and 'per_page' query parameters.
Max 100 per page.

Confidence: 100% (tested with real API)
```

**@api-architect** (Design style):
```
# API Specification Design: Webhooks

I'll design the OpenAPI spec following our patterns:

## Operations
- listWebhooks: GET /webhooks (not /repos/...)
- getWebhook: GET /webhooks/{webhookId}
- createWebhook: POST /webhooks
- updateWebhook: PUT /webhooks/{webhookId}
- deleteWebhook: DELETE /webhooks/{webhookId}

## Schemas
- Webhook (full object)
- WebhookConfig (nested configuration)

Using $ref for reuse, camelCase for properties.
```

**@gate-controller** (Enforcement style):
```
# Gate Validation: listWebhooks

## Gate 1: API Specification ✅ PASSED
## Gate 2: Type Generation ✅ PASSED
## Gate 3: Implementation ❌ FAILED

**BLOCKED at Gate 3**

Issue: Promise<any> found in WebhookProducer.ts:45

Fix Required:
1. Change to Promise<Webhook[]>
2. Import Webhook from generated/model

Cannot proceed until Gate 3 passes.
```

## Workflow Invocation Examples

### Example 1: Add Single Operation

```
Step 1: Check credentials
@credential-manager Check for GitHub credentials

Step 2: Research operation
@api-researcher Research GitHub listWebhooks endpoint
Test with curl and document findings

Step 3: Design specification
@api-architect Add listWebhooks operation to api.yml
Use get prefix, return array of Webhook

Step 4: Validate specification
@api-reviewer Validate api.yml changes
Check for rule violations

Step 5: Generate types
@build-validator Run npm generate and validate

Step 6: Implement operation
@operation-engineer Implement listWebhooks in WebhookProducer
@mapping-engineer Create toWebhook and toWebhookArray mappers

Step 7: Create tests
@test-orchestrator Coordinate testing for listWebhooks
Ensure unit and integration tests

Step 8: Validate build
@build-reviewer Run build and shrinkwrap

Step 9: Final validation
@gate-controller Validate all gates for listWebhooks
```

### Example 2: Create Module MVP

```
Phase 1: Discovery
@product-specialist Research GitHub API and product
@api-researcher Test authentication and connection endpoint
@credential-manager Setup GitHub token in .env

Phase 2: Design
@api-architect Design minimal API spec (connect + one operation)
@schema-specialist Design connection schemas
@api-reviewer Validate specification

Phase 3: Implementation
@build-validator Generate types
@client-engineer Implement GitHubClient
@operation-engineer Implement first producer
@mapping-engineer Create mappers

Phase 4: Testing
@test-orchestrator Coordinate all testing
@connection-ut-engineer + @producer-ut-engineer (unit tests)
@connection-it-engineer + @producer-it-engineer (integration tests)

Phase 5: Quality
@build-reviewer Validate build
@gate-controller Validate all gates
```

### Example 3: Fix Failing Build

```
@build-reviewer Diagnose build failure

(Build reviewer identifies type errors)

@typescript-expert Fix type errors in WebhookProducer
- Change Promise<any> to Promise<Webhook>
- Import from generated types

@build-reviewer Re-run build validation
```

## Agent Specialization

### When to Use Which Agent

**Need to understand an API?**
→ @product-specialist, @api-researcher

**Designing API specification?**
→ @api-architect, @schema-specialist

**Validating specification?**
→ @api-reviewer, @security-auditor

**Generating types?**
→ @build-validator

**Implementing code?**
→ @typescript-expert, @client-engineer, @operation-engineer, @mapping-engineer

**Creating tests?**
→ @test-orchestrator, @mock-specialist, @producer-ut-engineer, @producer-it-engineer

**Reviewing quality?**
→ @api-reviewer, @ut-reviewer, @it-reviewer, @build-reviewer, @style-reviewer

**Enforcing gates?**
→ @gate-controller

## Multi-Agent Requests

### Collaborative Design
```
@api-architect @schema-specialist @security-auditor
Design secure API specification for GitHub webhooks

Requirements:
- CRUD operations for webhooks
- OAuth authentication
- Proper schema composition
- Security best practices
```

### Implementation Team
```
@typescript-expert @operation-engineer @mapping-engineer
Implement createWebhook operation

Requirements:
- Use generated types
- Validate input
- Call mapper for response
- Handle errors with core types
```

### Testing Team
```
@mock-specialist @producer-ut-engineer @ut-reviewer
Create comprehensive unit tests for WebhookProducer

Requirements:
- Use nock for HTTP mocking
- Test success and error cases
- 100% coverage
```

## Agent Response Format

Agents typically respond with:

1. **Context** - What they understand about the request
2. **Analysis** - Their expert assessment
3. **Recommendations** - What should be done
4. **Actions** - Specific steps or code
5. **Validation** - Quality checks
6. **Next Steps** - What comes after

Example:
```
@api-reviewer Validate api.yml

# API Specification Review (Context)

I've analyzed api.yml for the webhook operations.

# Analysis
Found 3 rule violations and 1 warning.

# Issues Found (Expert assessment)
❌ Rule #5: snake_case found in properties
❌ Rule #9: Error responses (401, 403)
⚠️  Schema context: Webhook used in nested and direct

# Recommendations (What to do)
1. Convert all snake_case to camelCase
2. Remove all 4xx/5xx responses
3. Consider creating WebhookSummary

# Validation (Quality checks)
Gate 1: ❌ FAILED

# Next Steps
Fix violations and re-run validation before generation.
```

## Tips for Effective Agent Use

### 1. Be Specific
```
❌ @api-architect Design the spec
✅ @api-architect Design OpenAPI spec for GitHub webhook CRUD operations
```

### 2. Provide Context
```
❌ @operation-engineer Implement the operation
✅ @operation-engineer Implement listWebhooks in WebhookProducer
    Use WebhookProducer pattern, call toWebhookArray mapper
```

### 3. Combine Related Agents
```
✅ @api-architect @schema-specialist Design specification
   Ensures both path structure and schema design coordinated
```

### 4. Use for Validation
```
✅ @api-reviewer Validate before generation
   @ut-reviewer Review test quality
   @gate-controller Check all gates before completion
```

### 5. Follow Workflow Sequence
```
✅ Credentials → Research → Design → Review → Generate → Implement → Test → Build
   Each phase has appropriate agents
```

## Agent Authority and Escalation

### Agents Can Decide
- Their domain expertise
- Implementation approach
- Design patterns
- Quality standards

### Agents Must Escalate
- User requirements unclear
- Rule conflicts
- Breaking changes
- Complex edge cases

### Agents Cannot Override
- Critical rules
- Security requirements
- Gate failures
- User intent

## Success Patterns

### Pattern 1: Validate Early and Often
```
@api-reviewer after API spec changes
@build-validator after generation
@ut-reviewer after creating tests
@build-reviewer before completion
@gate-controller at end
```

### Pattern 2: Collaborate on Complex Tasks
```
@api-architect + @schema-specialist for complex schemas
@typescript-expert + @operation-engineer for tricky types
@test-orchestrator + @mock-specialist for test setup
```

### Pattern 3: Phase-Based Progress
```
Phase completed → Invoke gate controller
Gate failed → Invoke specialist to fix
Gate passed → Continue to next phase
```

## See Also

- [AGENTS.md](./AGENTS.md) - Complete agent reference
- [ENFORCEMENT.md](../ENFORCEMENT.md) - Validation gates
- [EXECUTION-PROTOCOL.md](../EXECUTION-PROTOCOL.md) - Execution workflow
