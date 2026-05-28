---
name: test-fixtures
description: Test fixture creation and management patterns. Use when creating test data, mock objects, or test setup utilities.
---

# Test Fixture Patterns

## 🚨 CRITICAL RULES

### Rule #1: Fixtures MUST Match Real API Responses

- **ALWAYS copy actual API responses** from API documentation or real API calls
- **MAINTAIN exact field names** as returned by API
- **KEEP realistic data** - don't use placeholder text like "test" or "foo"
- **TEST mappers with real structure** - fixtures validate mapper logic
- **NEVER invent fields** that don't exist in API

```json
// ✅ CORRECT - Real API response structure
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "name": "John Doe",
  "email": "john.doe@example.com",
  "created_at": "2024-01-01T00:00:00Z",
  "role": "admin"
}

// ❌ WRONG - Invented structure
{
  "userId": "123",           // API uses "id", not "userId"
  "fullName": "Test User",   // API uses "name", not "fullName"
  "emailAddress": "test"     // Wrong field name and unrealistic value
}
```

**WHY**: Fixtures are test data that verify mappers correctly transform API responses. If fixtures don't match real API, tests are meaningless.

### Rule #2: Organize by Operation/Resource

- **One directory per resource** (users/, organizations/, webhooks/)
- **Group related fixtures** (success, error, edge cases)
- **Clear naming conventions** (mockResponse1.json, mockError1.json)
- **NO mixed resources in same directory**

```
test/unit/resources/
├── users/
│   ├── mockResponse1.json    # Single user GET response
│   ├── mockResponse2.json    # User list response
│   └── mockError1.json        # 404 error response
├── organizations/
│   ├── mockResponse1.json    # Single organization
│   ├── mockResponse2.json    # Organization list
│   └── mockError1.json        # Permission denied error
└── webhooks/
    ├── mockResponse1.json    # Webhook details
    └── mockResponse2.json    # Webhook list
```

### Rule #3: NEVER Commit PII in Fixtures

**🚨 CRITICAL: Remove ALL personally identifiable information (PII)**

**Sanitization Checklist** (before saving fixtures):
- [ ] Real names → "Jane Doe", "John Smith", "Alice Johnson"
- [ ] Email addresses → "user@example.com", "admin@example.com"
- [ ] Phone numbers → "+1-555-0123", "+1-555-0456"
- [ ] Addresses → "123 Main St, Anytown, ST 12345"
- [ ] SSNs/Tax IDs → "XXX-XX-1234"
- [ ] API keys/tokens → "sk_test_...", "token_sanitized"
- [ ] Passwords → "********"
- [ ] Birth dates → "1990-01-01"
- [ ] Company names → "Example Corp", "Test Industries"
- [ ] IP addresses → "192.0.2.1" (use TEST-NET-1 range)

```json
// ✅ CORRECT - Sanitized fixture
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "firstName": "Jane",
  "lastName": "Doe",
  "email": "user@example.com",
  "phone": "+1-555-0123",
  "address": {
    "street": "123 Main St",
    "city": "Anytown",
    "state": "ST",
    "zip": "12345"
  }
}

// ❌ WRONG - Contains real PII
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "firstName": "Robert",
  "lastName": "Johnson",
  "email": "rjohnson@acmecorp.com",     // Real email
  "phone": "+1-415-555-1234",           // Real phone
  "address": {
    "street": "456 Oak Avenue",         // Real address
    "city": "San Francisco",
    "state": "CA",
    "zip": "94102"
  }
}
```

**Name Splitting**:
```json
// ✅ CORRECT - Use common placeholder names
{
  "firstName": "Jane",
  "lastName": "Doe"
}

{
  "firstName": "John",
  "lastName": "Smith"
}

{
  "firstName": "Alice",
  "lastName": "Johnson"
}

// ❌ WRONG - Using real people's names from production
{
  "firstName": "Robert",
  "lastName": "Peterson"  // Real person's name
}
```

## File Structure

### Directory Organization

**Location**: `test/unit/resources/`

```
test/unit/resources/
├── {resource}/
│   ├── mockResponse1.json      # Success response - single item
│   ├── mockResponse2.json      # Success response - list/collection
│   ├── mockResponse3.json      # Success response - special case
│   ├── mockError1.json          # Error response - 404
│   ├── mockError2.json          # Error response - 401
│   └── mockError3.json          # Error response - validation
└── {anotherResource}/
    ├── mockResponse1.json
    └── mockError1.json
```

**Examples**:
```
test/unit/resources/
├── users/
│   ├── mockResponse1.json      # GET /users/{id} - single user
│   ├── mockResponse2.json      # GET /users - user list
│   ├── mockError1.json          # 404 - user not found
│   └── mockError2.json          # 401 - unauthorized
├── organizations/
│   ├── mockResponse1.json      # Single organization
│   ├── mockResponse2.json      # Organization list with pagination
│   └── mockError1.json          # 403 - forbidden
└── webhooks/
    ├── mockResponse1.json      # Webhook details
    ├── mockResponse2.json      # Webhook list
    └── mockError1.json          # 422 - invalid webhook config
```

### Naming Conventions

**Pattern**: `mock{Type}{Number}.json`

**Success responses**: `mockResponse{N}.json`
- mockResponse1.json - First success case (single item, simple case)
- mockResponse2.json - Second success case (list, collection, pagination)
- mockResponse3.json - Third success case (edge case, special scenario)

**Error responses**: `mockError{N}.json`
- mockError1.json - First error case (usually 404)
- mockError2.json - Second error case (usually 401/403)
- mockError3.json - Third error case (usually validation 400/422)

**Descriptive names** (for complex cases):
- mockUserWithPermissions.json - User object with expanded permissions
- mockOrganizationWithMembers.json - Organization with nested member list
- mockWebhookCreated.json - Response from webhook creation
- mockEmptyList.json - Empty array/list response
- mockPaginatedResponse.json - Response with pagination metadata

## JSON Format Rules

### Pretty-Printed with 2-Space Indentation

```json
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "name": "Jane Doe",
  "email": "user@example.com",
  "created_at": "2024-01-01T00:00:00Z",
  "metadata": {
    "role": "admin",
    "department": "Engineering"
  },
  "tags": [
    "active",
    "verified"
  ]
}
```

**Format rules**:
- ✅ 2-space indentation (consistent with TypeScript)
- ✅ Valid JSON (no trailing commas)
- ✅ Realistic values (not "test", "foo", "bar")
- ✅ Match API documentation exactly
- ✅ Include all fields API returns (even if null/empty)
- ❌ NO tabs for indentation
- ❌ NO trailing commas
- ❌ NO comments (JSON doesn't support comments)

### Realistic Values

```json
// ✅ CORRECT - Realistic values
{
  "id": "usr_1a2b3c4d5e6f",
  "name": "Jane Doe",
  "email": "jane.doe@example.com",
  "created_at": "2024-01-15T10:30:00Z",
  "age": 28,
  "balance": 1250.75,
  "status": "active"
}

// ❌ WRONG - Unrealistic placeholder values
{
  "id": "123",
  "name": "test",
  "email": "test",
  "created_at": "2020-01-01",
  "age": 1,
  "balance": 0,
  "status": "foo"
}
```

**Why realistic values matter**:
- Tests validate mappers work with real data shapes
- Catches type conversion issues (string vs number)
- Validates date parsing works correctly
- Tests enum mapping with actual enum values
- Helps identify missing validations

### Array/List Responses

```json
[
  {
    "id": "usr_1a2b3c4d",
    "name": "Jane Doe",
    "email": "jane.doe@example.com"
  },
  {
    "id": "usr_2b3c4d5e",
    "name": "John Smith",
    "email": "john.smith@example.com"
  },
  {
    "id": "usr_3c4d5e6f",
    "name": "Alice Johnson",
    "email": "alice.johnson@example.com"
  }
]
```

**For paginated responses**:
```json
{
  "data": [
    {
      "id": "usr_1a2b3c4d",
      "name": "Jane Doe"
    }
  ],
  "pagination": {
    "total": 100,
    "page": 1,
    "per_page": 20,
    "total_pages": 5
  }
}
```

### Error Response Format

```json
// mockError1.json - 404 Not Found
{
  "error": "Not Found",
  "message": "User with id 'usr_999' does not exist",
  "code": "RESOURCE_NOT_FOUND",
  "status": 404
}

// mockError2.json - 401 Unauthorized
{
  "error": "Unauthorized",
  "message": "Invalid or expired authentication token",
  "code": "INVALID_CREDENTIALS",
  "status": 401
}

// mockError3.json - 422 Validation Error
{
  "error": "Validation Failed",
  "message": "Invalid input parameters",
  "errors": [
    {
      "field": "email",
      "message": "Invalid email format"
    },
    {
      "field": "age",
      "message": "Must be at least 18"
    }
  ],
  "status": 422
}
```

## Recording Real API Responses

### Using nock.recorder

**Step 1: Record real API interactions during integration test**

```typescript
// test/e2e/fixtures/recordFixtures.ts (temporary file)
import nock from 'nock';
import * as fs from 'fs';
import { getConnectedInstance } from './Common';

async function recordFixtures() {
  // Start recording
  nock.recorder.rec({
    output_objects: true,
    enable_reqheaders_recording: true
  });

  // Run real API calls
  const connector = await getConnectedInstance();
  const api = connector.getUserApi();

  const user = await api.getUser('usr_123');
  const users = await api.listUsers(/* ... */);

  // Stop recording and save
  const recordings = nock.recorder.play();

  // Save to .localmemory (NOT committed)
  fs.writeFileSync(
    '.localmemory/recorded_fixtures.json',
    JSON.stringify(recordings, null, 2)
  );

  console.log('Recorded', recordings.length, 'API calls');
}

recordFixtures();
```

**Step 2: Extract and sanitize responses**

```bash
# Review recorded fixtures
cat .localmemory/recorded_fixtures.json

# Manually extract response bodies
# Sanitize PII (see Rule #3)
# Save to test/unit/resources/{resource}/mockResponse1.json
```

**Step 3: Create unit tests using sanitized fixtures**

### Using curl to Capture API Responses

```bash
# Capture single resource response
curl -H "Authorization: Bearer $API_TOKEN" \
     https://api.example.com/users/usr_123 \
     | jq '.' > .localmemory/user_response_raw.json

# Capture list response
curl -H "Authorization: Bearer $API_TOKEN" \
     https://api.example.com/users \
     | jq '.' > .localmemory/users_list_raw.json

# Capture error response
curl -H "Authorization: Bearer $API_TOKEN" \
     https://api.example.com/users/invalid_id \
     | jq '.' > .localmemory/user_error_raw.json
```

**Then sanitize and move to test/unit/resources/**

## Using Fixtures in Tests

### Loading Fixture Files

```typescript
import * as fs from 'fs';
import * as path from 'path';

// Load fixture helper
function loadFixture(resourceType: string, fixtureName: string): any {
  const fixturePath = path.join(
    __dirname,
    'resources',
    resourceType,
    `${fixtureName}.json`
  );

  return JSON.parse(fs.readFileSync(fixturePath, 'utf-8'));
}

// Usage in test
const mockUser = loadFixture('users', 'mockResponse1');
```

### Using Fixtures with nock

```typescript
import nock from 'nock';
import * as fs from 'fs';
import * as path from 'path';

describe('UserProducer', () => {
  afterEach(() => {
    nock.cleanAll();
  });

  it('should get user successfully', async () => {
    // Load fixture
    const mockUser = JSON.parse(
      fs.readFileSync(
        path.join(__dirname, 'resources/users/mockResponse1.json'),
        'utf-8'
      )
    );

    // Mock HTTP request with fixture
    nock('https://api.example.com')
      .get('/users/usr_123')
      .reply(200, mockUser);

    // Execute test
    const connector = await getConnectedInstance();
    const api = connector.getUserApi();
    const user = await api.getUser('usr_123');

    // Assertions
    expect(user.id).to.not.be.undefined;
    expect(user.name).to.equal('Jane Doe');
    expect(nock.pendingMocks()).to.be.empty;
  });

  it('should handle user not found error', async () => {
    // Load error fixture
    const mockError = JSON.parse(
      fs.readFileSync(
        path.join(__dirname, 'resources/users/mockError1.json'),
        'utf-8'
      )
    );

    // Mock error response
    nock('https://api.example.com')
      .get('/users/usr_999')
      .reply(404, mockError);

    // Test error handling
    const connector = await getConnectedInstance();
    const api = connector.getUserApi();

    try {
      await api.getUser('usr_999');
      assert.fail('should have thrown');
    } catch (e) {
      expect(e).to.be.instanceOf(NoSuchObjectError);
    }

    expect(nock.pendingMocks()).to.be.empty;
  });
});
```

### Reusable Fixture Patterns

**Create fixture helper file**:

```typescript
// test/unit/FixtureHelpers.ts
import * as fs from 'fs';
import * as path from 'path';
import nock from 'nock';

export function loadFixture(resourceType: string, fixtureName: string): any {
  const fixturePath = path.join(
    __dirname,
    'resources',
    resourceType,
    `${fixtureName}.json`
  );
  return JSON.parse(fs.readFileSync(fixturePath, 'utf-8'));
}

export function mockGetRequest(
  baseUrl: string,
  path: string,
  fixture: any,
  statusCode: number = 200
): nock.Scope {
  return nock(baseUrl)
    .get(path)
    .reply(statusCode, fixture);
}

export function mockPostRequest(
  baseUrl: string,
  path: string,
  requestBody: any,
  responseFixture: any,
  statusCode: number = 201
): nock.Scope {
  return nock(baseUrl)
    .post(path, requestBody)
    .reply(statusCode, responseFixture);
}
```

**Usage**:

```typescript
import { loadFixture, mockGetRequest } from './FixtureHelpers';

describe('UserProducer', () => {
  it('should get user', async () => {
    const mockUser = loadFixture('users', 'mockResponse1');

    mockGetRequest(
      'https://api.example.com',
      '/users/usr_123',
      mockUser
    );

    const user = await api.getUser('usr_123');
    expect(user.name).to.equal('Jane Doe');
  });
});
```

## Fixture Maintenance

### When to Update Fixtures

Update fixtures when:
- API response format changes (new fields, removed fields, renamed fields)
- API returns different data types (string → number, etc.)
- Adding new test cases for edge cases
- API error response format changes

### Version Control

```bash
# Fixtures are committed to git
git add test/unit/resources/

# Raw recordings are NOT committed (.localmemory)
echo ".localmemory/" >> .gitignore
```

### Documentation

**Document fixtures in test file comments**:

```typescript
/**
 * mockResponse1.json - Single user GET response
 * Captured from: GET /users/usr_123
 * Date: 2024-01-15
 * Sanitized: Yes (real user data replaced)
 */
const mockUser = loadFixture('users', 'mockResponse1');
```

## Validation

### Check Fixture Structure

```bash
# Verify fixtures are valid JSON
for file in test/unit/resources/**/*.json; do
  jq empty "$file" 2>/dev/null && echo "✅ $file valid" || echo "❌ $file invalid JSON"
done

# Check fixtures use 2-space indentation
for file in test/unit/resources/**/*.json; do
  if grep -q $'\t' "$file"; then
    echo "❌ $file uses tabs instead of spaces"
  else
    echo "✅ $file uses spaces"
  fi
done
```

### Check Fixture Organization

```bash
# Verify resources directory exists
[ -d test/unit/resources ] && echo "✅ Resources directory exists" || echo "❌ Missing resources directory"

# List fixture files
find test/unit/resources -name "*.json" -type f | sort

# Check naming convention (mockResponse or mockError)
find test/unit/resources -name "*.json" -type f | while read file; do
  basename "$file" | grep -qE "^mock(Response|Error)[0-9]+\.json$" || \
    echo "⚠️  $file doesn't follow naming convention"
done
```

### Check for PII in Fixtures

```bash
# Warning: These are basic checks - manual review still required!

# Check for common email domains (flag real ones)
grep -rE "@(?!example\.com|example\.org|test\.com)" test/unit/resources/*.json && \
  echo "⚠️  Found non-example.com email addresses - verify sanitized" || \
  echo "✅ Email addresses look sanitized"

# Check for realistic phone patterns (might be real)
grep -rE "\+1-[2-9][0-9]{2}-[2-9][0-9]{2}-[0-9]{4}" test/unit/resources/*.json && \
  echo "⚠️  Found realistic phone numbers - verify sanitized" || \
  echo "✅ Phone numbers look sanitized"

# Check for common test/placeholder names
grep -rE "(Jane Doe|John Smith|Alice Johnson|Bob Wilson|Example Corp)" test/unit/resources/*.json > /dev/null && \
  echo "✅ Using placeholder names" || \
  echo "⚠️  Check if using real names"
```

### Manual Review Checklist

Before committing fixtures:

- [ ] All fixtures are valid JSON
- [ ] Fixtures match real API response structure
- [ ] All PII removed (names, emails, phones, addresses)
- [ ] Realistic placeholder values used
- [ ] 2-space indentation (no tabs)
- [ ] No trailing commas
- [ ] Organized by resource type
- [ ] Named following convention (mockResponse1, mockError1, etc.)
- [ ] Fixtures work correctly with nock in tests
- [ ] Documentation/comments explain what each fixture represents

## Common Fixture Patterns

### Empty Response

```json
// mockEmptyList.json
[]
```

```json
// mockEmptyPaginatedResponse.json
{
  "data": [],
  "pagination": {
    "total": 0,
    "page": 1,
    "per_page": 20,
    "total_pages": 0
  }
}
```

### Nested Objects

```json
// mockUserWithOrganization.json
{
  "id": "usr_1a2b3c4d",
  "name": "Jane Doe",
  "email": "jane.doe@example.com",
  "organization": {
    "id": "org_9x8y7z6w",
    "name": "Example Corp",
    "type": "enterprise"
  }
}
```

### Minimal vs Complete Responses

```json
// mockResponseMinimal.json - Minimum fields
{
  "id": "usr_1a2b3c4d",
  "name": "Jane Doe"
}

// mockResponseComplete.json - All possible fields
{
  "id": "usr_1a2b3c4d",
  "name": "Jane Doe",
  "email": "jane.doe@example.com",
  "phone": "+1-555-0123",
  "avatar_url": "https://example.com/avatars/jane.jpg",
  "bio": "Software engineer",
  "created_at": "2024-01-01T00:00:00Z",
  "updated_at": "2024-01-15T10:30:00Z",
  "metadata": {
    "role": "admin",
    "department": "Engineering"
  }
}
```

### Different Response States

```json
// mockUserActive.json
{
  "id": "usr_1a2b3c4d",
  "name": "Jane Doe",
  "status": "active",
  "enabled": true
}

// mockUserInactive.json
{
  "id": "usr_2b3c4d5e",
  "name": "John Smith",
  "status": "inactive",
  "enabled": false
}

// mockUserPending.json
{
  "id": "usr_3c4d5e6f",
  "name": "Alice Johnson",
  "status": "pending",
  "enabled": false
}
```

## Success Criteria

Test fixture implementation MUST meet all criteria:

- ✅ Fixtures organized in `test/unit/resources/{resource}/` directories
- ✅ Naming follows convention (mockResponse1.json, mockError1.json)
- ✅ All fixtures are valid JSON
- ✅ 2-space indentation used consistently
- ✅ Fixtures match real API response structure
- ✅ ALL PII removed and sanitized
- ✅ Realistic placeholder values (not "test", "foo", "bar")
- ✅ Fixtures work correctly with nock in unit tests
- ✅ Error response fixtures cover common error cases (404, 401, 422)
- ✅ Documentation explains what each fixture represents
- ✅ Fixtures committed to git (NOT in .localmemory)
- ✅ Raw recordings excluded from git (in .localmemory, .gitignore)
