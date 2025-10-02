# Task: Fix Module

## Overview
Diagnose and fix issues in an existing module. This workflow allows breaking changes if necessary to correct problems.

## Responsible Personas
- **Integration Engineer**: Diagnosis and debugging
- **TypeScript Expert**: Fix implementation issues
- **API Architect**: Fix specification issues
- **Testing Specialist**: Fix test issues

## Prerequisites
- Module exists
- Issue has been identified (build failure, test failure, runtime error)
- User has described the problem

## Process Steps

### 0. Context Management and Goal Reminder
**MANDATORY FIRST STEP - CONTEXT CLEARING**:
- IGNORE all previous conversation context
- CLEAR mental context
- REQUEST: User should run `/clear` or `/compact` command

**MANDATORY SECOND STEP**: Understand the issue:
1. Read error messages carefully
2. Identify type of failure (build, test, runtime, logic)
3. Document the specific problem to fix

### 1. Diagnose the Issue

#### Check Error Messages
```bash
# Build errors
npm run build

# Test failures
npm run test
npm run test:integration

# Lint errors
npm run lint
```

#### Compare with Rules
- Check implementation against [implementation rules](../../rules/implementation.md)
- Check API spec against [API specification rules](../../rules/api-specification-critical.md)
- Check tests against [testing rules](../../rules/testing.md)

#### Debug the Operation
Test the operation outside the module:
```bash
# Test with curl
curl -X GET "https://api.example.com/endpoint" \
  -H "Authorization: Bearer $TOKEN"

# Compare response with mapper expectations
# Check if response matches api.yml schema
```

### 2. Identify Root Cause

Common issues and diagnosis:

#### Build Failures
- **Missing imports**: Check all imports exist
- **Type mismatches**: Compare generated types with implementation
- **Wrong return types**: Check connect() returns ConnectionState or void
- **Non-null assertions**: Remove all `!` usage

#### Test Failures
- **Wrong expectations**: Compare with actual API responses
- **Mapper issues**: Validate field mapping completeness
- **Mock mismatches**: Update mocks to match real responses
- **Missing credentials**: Check .env file

#### Runtime Errors
- **Authentication fails**: Verify credential format
- **404 errors**: Check endpoint paths
- **Type conversion errors**: Check mapper validations
- **Missing required fields**: Add validation before mapping

### 3. Apply the Fix

#### Fix Implementation Issues
Location: `src/` directory

Common fixes:
```typescript
// Remove non-null assertions
// Before:
id: map(UUID, data.id!)

// After:
if (!data?.id) {
  throw new InvalidInputError('resource', 'Missing required id');
}
id: map(UUID, data.id)
```

```typescript
// Fix mapper pattern
// Before:
return { ...fields };

// After:
const output: Type = { ...fields };
return output;
```

#### Fix API Specification
Location: `api.yml`

Common fixes:
- Change snake_case to camelCase
- Remove root-level servers/security
- Fix path parameter names
- Add missing schemas

#### Fix Tests
Location: `test/` directory

Common fixes:
- Update expectations to match implementation
- Fix mock responses
- Add missing test cases
- Update fixtures with real data

### 4. Validate the Fix

Run validation sequence:
```bash
# Clean and rebuild
npm run clean
npm run build

# Run tests
npm run test
npm run test:integration

# Check lint
npm run lint
```

### 5. Document Breaking Changes

If fix required breaking changes:

1. **Update version** in package.json (major version bump)
2. **Document changes** in CHANGELOG.md
3. **Update tests** to reflect new behavior
4. **Update documentation** if API changed

## Common Fix Patterns

### Pattern: Fix Mapper Field Mismatch
```typescript
// Diagnosis: Interface has 5 fields, mapper only maps 3

// Fix: Add missing field mappings
export function toResource(data: any): Resource {
  if (!data?.id) {
    throw new InvalidInputError('resource', 'Missing required fields');
  }

  const output: Resource = {
    id: map(UUID, data.id),
    name: data.name,
    // Add missing fields:
    createdAt: map(Date, data.created_at),
    status: toEnum(StatusEnum, data.status),
    owner: data.owner  // Was missing
  };

  return output;
}
```

### Pattern: Fix PagedResults Implementation
```typescript
// Diagnosis: Pagination not working correctly

// Fix: Use manual field assignment
async list(results: PagedResults<Resource>): Promise<void> {
  const response = await this.client.get('/resources', {
    params: {
      page: results.pageNumber,
      per_page: results.pageSize
    }
  });

  // Manual assignment (more reliable)
  results.items = response.data.items.map(toResource);

  // Update count when available
  if (response.data.total_count !== undefined) {
    results.count = response.data.total_count;
  }

  // Set page token if exists
  if (response.data.next_token) {
    results.pageToken = response.data.next_token;
  }
}
```

### Pattern: Fix Authentication
```typescript
// Diagnosis: Auth token not being sent

// Fix: Add to axios headers
constructor() {
  this.httpClient = axios.create({
    baseURL: this.baseUrl,
    headers: {
      'Authorization': `Bearer ${this.connectionProfile.apiKey}`
    }
  });
}
```

## Recovery Points

Create checkpoint commits after each successful fix:
```bash
git add .
git commit -m "fix: resolve mapper field mismatch in UserInfo"
git commit -m "fix: correct authentication header format"
git commit -m "fix: update tests to match new implementation"
```

## Output

Document the fix in recovery context:
```json
{
  "issue": "Description of the problem",
  "diagnosis": "Root cause identified",
  "fix": "What was changed",
  "validation": "Tests now passing",
  "breakingChanges": false
}
```

## Success Criteria

✅ Build passes (`npm run build` exits with 0)
✅ All tests pass
✅ Lint passes
✅ Original issue is resolved
✅ No new issues introduced

## When to Escalate

If unable to fix after analysis:
1. Document findings
2. Identify what information is missing
3. Ask user for:
   - Additional context
   - Access to API documentation
   - Example of expected behavior
   - Permission for breaking changes