# Task: Add Operation

## Overview

Adds a single operation to an existing module. This task handles everything from API research to full implementation with tests.

## Usage Contexts

This task can be used in two ways:

1. **Standalone**: When user requests to add a single operation
   - Example: "Add listWebhooks to the GitHub module"
   - Execute this task directly

2. **As part of update-module**: When adding multiple operations
   - Example: "Add webhook CRUD operations to GitHub"
   - Called by [update-module.md](./update-module.md) for each operation
   - Context passed from parent task

## Responsible Personas
- **Product Specialist**: Research operation
- **API Architect**: Update specification
- **TypeScript Expert + Integration Engineer**: Implementation
- **Testing Specialist**: Test coverage
- **Documentation Writer**: Update docs if needed

## Prerequisites
- Module exists and builds successfully
- Git state is clean
- Tests are passing
- Operation identified from user request

## Input
- Module path and details
- Operation to add
- Previous task outputs

## Process Steps

### 0. Context Management and Goal Reminder
**MANDATORY FIRST STEP - CONTEXT CLEARING**:
- IGNORE all previous conversation context
- CLEAR mental context
- REQUEST: User should run `/clear` or `/compact` command

**MANDATORY SECOND STEP**: Read and understand original user intent:
1. Load `initial-request.json` from memory folder
2. Extract the specific operation requested
3. Ensure focus on SINGLE operation only
4. Maintain existing module compatibility

### 1. Research Operation (Product Specialist)

```bash
# Test with curl first
curl -X GET "https://api.github.com/repos/{owner}/{repo}/hooks" \
  -H "Authorization: token $API_TOKEN" \
  -H "Accept: application/vnd.github.v3+json"

# Save response for reference
# in _work/test-responses/list-webhooks.json
```

Document findings:
- Endpoint path
- HTTP method
- Required parameters
- Response format
- Authentication needs
- Special requirements

### 2. Update API Specification (API Architect)

**🚨 MANDATORY: Load Rules BEFORE Starting**
1. **READ**: `claude/rules/api-specification.md` - ALL 18 critical rules
2. **READ**: `claude/ENFORCEMENT.md` - Validation Gate 1
3. **VERIFY**: Critical rules understood:
   - ❌ NEVER use 'describe' prefix (use 'get')
   - ✅ ONLY 200/201 responses (no 4xx/5xx)
   - ✅ All properties camelCase (not snake_case)
   - ✅ Nested objects MUST use $ref
   - ✅ Response schemas: Direct $ref to main business object ONLY (NO envelope: status/data/meta/response/result/caller/token)
   - ✅ NO connection context parameters (apiKey, token, baseUrl, organizationId) in operations
   - ✅ Add descriptions from vendor docs

Add to `api.yml`:

```yaml
paths:
  /webhooks:
    get:
      operationId: listWebhooks
      x-method-name: list
      tags:
        - webhook
      summary: List webhooks
      parameters:
        - $ref: '#/components/parameters/organizationId'
      responses:
        200:
          description: List of webhooks
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/Webhook'

components:
  schemas:
    Webhook:
      type: object
      properties:
        id:
          type: string
          format: uuid
        url:
          type: string
          format: url
        events:
          type: array
          items:
            type: string
```

Validation:
```bash
npx swagger-cli validate api.yml
```

### 3. Generate Interfaces (API Architect)

```bash
npm run generate

# Verify build still passes
npm run build
```

Check generated code:
- New interfaces created
- Existing interfaces unchanged
- Types properly generated

### 4. Implement Operation (TypeScript Expert + Integration Engineer)

**🚨 MANDATORY: Load Rules BEFORE Starting**
1. **READ**: `claude/rules/implementation.md` - All implementation patterns
2. **READ**: `claude/rules/error-handling.md` - Core error types
3. **READ**: `claude/rules/type-mapping.md` - Type conversions
4. **VERIFY**: Critical patterns understood:
   - ❌ NEVER guess signatures - READ generated/api/index.ts for exact types
   - ✅ NEVER use `any` in signatures (use generated types)
   - ✅ ALWAYS use `const output: Type` pattern in mappers
   - ✅ NEVER use `!` (non-null assertion)
   - ✅ Validate required fields before mapping
   - ❌ NEVER add connection context params (apiKey, token, baseUrl, organizationId) to producer methods
   - ❌ DO NOT modify isSupported() - it always returns `OperationSupportStatus.Maybe` (boilerplate only)

#### 4.1 Add Mapper (if new type)

In `src/Mappers.ts`:

```typescript
export function toWebhook(data: any): Webhook {
  return {
    id: map(UUID, data.id),
    url: map(URL, data.url),
    events: data.events || []
  };
}
```

#### 4.2 Add to Producer

Create or update `src/WebhookProducer.ts`:

```typescript
export class WebhookProducer implements WebhookApi {
  constructor(private client: GitHubClient) {}

  async list(organizationId: string): Promise<Webhook[]> {
    try {
      const response = await this.client.getHttpClient()
        .get(`/orgs/${organizationId}/hooks`);

      return response.data.map(toWebhook);
    } catch (error) {
      this.client.handleApiError(error);
    }
  }
}
```

#### 4.3 Update Connector

In main connector:

```typescript
private webhookProducer?: WebhookProducer;

getWebhookApi(): WebhookApi {
  if (!this.webhookProducer) {
    this.webhookProducer = new WebhookProducer(this.client);
  }
  return this.webhookProducer;
}
```

### 5. Add Tests (Testing Specialist)

#### 5.1 Integration Test

```typescript
describe('WebhookProducer Integration', () => {
  before(async function() {
    if (!process.env.API_TOKEN) {
      this.skip();
    }
  });

  it('should list webhooks', async () => {
    const client = new GitHubClient();
    await client.connect({ token: process.env.API_TOKEN });

    const producer = new WebhookProducer(client);
    const webhooks = await producer.list('my-org');

    expect(webhooks).to.be.an('array');
    if (webhooks.length > 0) {
      expect(webhooks[0].id).to.be.instanceof(UUID);
      expect(webhooks[0].url).to.be.instanceof(URL);
    }
  });
});
```

#### 5.2 Unit Test

```typescript
describe('WebhookProducer', () => {
  describe('list', () => {
    it('should return mapped webhooks', async () => {
      const mockClient = createMockClient();
      mockClient.get.resolves({
        data: [
          { id: '123', url: 'https://example.com/hook', events: ['push'] }
        ]
      });

      const producer = new WebhookProducer(mockClient);
      const webhooks = await producer.list('org-id');

      expect(webhooks).to.have.lengthOf(1);
      expect(webhooks[0].id).to.be.instanceof(UUID);
      expect(mockClient.get).to.have.been.calledWith('/orgs/org-id/hooks');
    });
  });
});
```

### 6. Validate Everything

```bash
# Build must pass
npm run build

# All tests must pass
npm run test
npm run test:integration

# Lint must pass
npm run lint
```

### 7. Update Documentation (if needed)

Only if special requirements:

```markdown
## Special Requirements

### listWebhooks
- **Required Permissions**: admin:org_hook scope
- **Rate Limit**: Stricter limits apply
```

### 8. Commit Changes

```bash
git add -A
git commit -m "feat(webhook): add listWebhooks operation

Task: Add webhook listing functionality
- Updated API specification with webhook endpoint
- Implemented WebhookProducer with list method
- Added comprehensive unit and integration tests
- All tests passing, build successful"
```

## Decision Points

### Schema Reuse vs New
- Check if schema exists → Reuse with $ref
- Similar schema exists → Extend with allOf
- No similar schema → Create new

### Producer Creation vs Extension
- Same resource type → Add to existing producer
- New resource type → Create new producer
- Mixed operations → Ask user preference

## Output Format

```json
{
  "status": "completed",
  "operation": "listWebhooks",
  "changes": {
    "apiSpec": ["Added /webhooks GET endpoint"],
    "schemas": ["Created Webhook schema"],
    "implementation": ["Created WebhookProducer", "Added toWebhook mapper"],
    "tests": ["Added 2 unit tests", "Added 1 integration test"]
  },
  "validation": {
    "build": "passed",
    "tests": "passed",
    "lint": "passed"
  },
  "commit": "abc123def"
}
```

## Success Criteria
- Operation works with real API
- All tests pass
- No regression in existing tests
- Build succeeds
- Code follows patterns

## Error Recovery
- API mismatch → Update mappers
- Build failure → Fix types/imports
- Test failure → Debug with curl
- Lint failure → Fix style issues

## Context Usage
- Medium: ~30-40% typical
- API spec takes space
- Generated code loaded
- Test files included