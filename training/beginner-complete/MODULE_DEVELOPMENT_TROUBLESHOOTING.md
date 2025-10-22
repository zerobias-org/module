# Module Development Troubleshooting Guide
## Complete Problem-Solution Reference

**Purpose**: Systematic guide to diagnose and fix all common issues
**Format**: Symptom → Diagnosis → Solution
**Coverage**: All 6 gates + common development issues

---

## Table of Contents

1. [Gate 1 Failures: API Specification](#gate-1-failures-api-specification)
2. [Gate 2 Failures: Type Generation](#gate-2-failures-type-generation)
3. [Gate 3 Failures: Implementation](#gate-3-failures-implementation)
4. [Gate 4 Failures: Unit Tests](#gate-4-failures-unit-tests)
5. [Gate 5 Failures: Integration Tests](#gate-5-failures-integration-tests)
6. [Gate 6 Failures: Build](#gate-6-failures-build)
7. [Environment & Setup Issues](#environment--setup-issues)
8. [Common Development Mistakes](#common-development-mistakes)

---

## Gate 1 Failures: API Specification

### Issue 1.1: 'describe' Operation Found

**Symptom:**
```bash
grep -E "describe[A-Z]" api.yml
# Output: operationId: describeUser
```

**Diagnosis:**
Using 'describe' prefix instead of correct verb.

**Solution:**
```yaml
# ❌ WRONG
operationId: describeUser

# ✅ CORRECT
operationId: getUser
```

**Why**: Framework expects get/list/create/update/delete verbs, not 'describe'.

---

### Issue 1.2: snake_case Properties

**Symptom:**
```bash
grep "_" api.yml | grep -v "x-" | grep -v "#"
# Output: user_id:
```

**Diagnosis:**
Properties using snake_case instead of camelCase.

**Solution:**
```yaml
# ❌ WRONG
User:
  properties:
    user_id:
      type: string
    first_name:
      type: string

# ✅ CORRECT
User:
  properties:
    userId:
      type: string
    firstName:
      type: string
```

**Why**: Generated TypeScript uses camelCase convention (Rule 5).

---

### Issue 1.3: Error Responses in Spec

**Symptom:**
```bash
grep -E "'40[0-9]'|'50[0-9]'" api.yml
# Output: '404': { description: Not found }
```

**Diagnosis:**
Error responses included in specification.

**Solution:**
```yaml
# ❌ WRONG
responses:
  '200':
    description: Success
    content: ...
  '404':
    description: Not found
  '500':
    description: Server error

# ✅ CORRECT
responses:
  '200':
    description: Success
    content: ...
  # Remove all 4xx/5xx responses
```

**Why**: Framework handles errors automatically via handleAxiosError (Rule 12).

---

### Issue 1.4: servers/security at Root Level

**Symptom:**
```bash
grep -E "^servers:|^security:" api.yml
# Output: servers:
```

**Diagnosis:**
Server or security configuration at root level.

**Solution:**
```yaml
# ❌ WRONG
openapi: 3.0.0
servers:
  - url: https://api.example.com
security:
  - bearerAuth: []

# ✅ CORRECT
openapi: 3.0.0
# No servers or security at root
components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
```

**Why**: Connection management handled by framework, not OpenAPI spec (Rule 1).

---

### Issue 1.5: Plural Tags

**Symptom:**
```yaml
tags:
  - users
  - organizations
```

**Diagnosis:**
Using plural instead of singular.

**Solution:**
```yaml
# ❌ WRONG
tags:
  - users
  - organizations

# ✅ CORRECT
tags:
  - user
  - organization
```

**Why**: Singular nouns generate cleaner Producer class names (Rule 9).

---

### Issue 1.6: connectionState Missing baseConnectionState

**Symptom:**
```yaml
# connectionState.yml
type: object
properties:
  accessToken:
    type: string
# Missing expiresIn!
```

**Diagnosis:**
Not extending baseConnectionState.yml, missing expiresIn field.

**Solution:**
```yaml
# ✅ CORRECT
allOf:
  - $ref: './node_modules/@auditmation/types-core/schema/baseConnectionState.yml'
  - type: object
    required:
      - accessToken
    properties:
      accessToken:
        type: string
        format: password
# expiresIn comes from baseConnectionState
```

**Why**: Server uses expiresIn for token refresh cronjobs (critical requirement).

---

## Gate 2 Failures: Type Generation

### Issue 2.1: Generation Fails - Invalid YAML

**Symptom:**
```bash
npm run generate
# Error: Invalid YAML syntax at line 45
```

**Diagnosis:**
YAML syntax error in api.yml.

**Solution:**
```bash
# Validate YAML syntax
npx swagger-cli validate api.yml
# Will show exact error location

# Common issues:
# - Missing colon
# - Wrong indentation (use 2 spaces)
# - Unmatched quotes
# - Tab characters (use spaces only)
```

**Fix and regenerate:**
```bash
# Fix the error in api.yml
# Then regenerate
npm run clean && npm run generate
```

---

### Issue 2.2: InlineResponse Types Generated

**Symptom:**
```bash
grep "InlineResponse" generated/
# Output: export interface InlineResponse200
```

**Diagnosis:**
Inline schema in api.yml instead of named reference.

**Solution:**
```yaml
# ❌ WRONG - Inline schema
responses:
  '200':
    content:
      application/json:
        schema:
          type: object
          properties:
            id:
              type: string

# ✅ CORRECT - Named schema reference
responses:
  '200':
    content:
      application/json:
        schema:
          $ref: '#/components/schemas/User'

# And add to components:
components:
  schemas:
    User:
      type: object
      properties:
        id:
          type: string
```

**Regenerate:**
```bash
npm run clean && npm run generate
grep "InlineResponse" generated/ || echo "✅ Fixed"
```

---

### Issue 2.3: Cannot Resolve Reference

**Symptom:**
```bash
npm run generate
# Error: Could not resolve reference: #/components/schemas/User
```

**Diagnosis:**
Schema referenced but not defined.

**Solution:**
```yaml
# Check api.yml - is User defined?
components:
  schemas:
    User:  # ← Must exist
      type: object
      properties: ...
```

**If missing, add the schema definition.**

---

### Issue 2.4: Java Not Found

**Symptom:**
```bash
npm run generate
# Error: java: command not found
```

**Diagnosis:**
Java not installed or not in PATH.

**Solution:**
```bash
# Check Java
java -version

# If not installed:
# macOS: brew install openjdk@17
# Linux: apt-get install openjdk-17-jdk
# Windows: Download from https://adoptium.net/

# Verify
java -version
# Should show: openjdk version "17.x.x"

# Regenerate
npm run generate
```

---

## Gate 3 Failures: Implementation

### Issue 3.1: Promise<any> in Function Signatures

**Symptom:**
```bash
grep "Promise<any>" src/*.ts
# Output: async getUser(id: string): Promise<any>
```

**Diagnosis:**
Not using generated types.

**Solution:**
```typescript
// ❌ WRONG
import { User } from '../generated/api';

async getUser(userId: string): Promise<any> {
  // ...
}

// ✅ CORRECT
import { User } from '../generated/api';

async getUser(userId: string): Promise<User> {
  // ...
}
```

---

### Issue 3.2: ESLint Errors

**Symptom:**
```bash
npm run lint:src
# Output: ✖ 5 problems (5 errors, 0 warnings)
```

**Diagnosis:**
Code style violations.

**Solution:**
```bash
# Auto-fix what's possible
npm run lint:src -- --fix

# Review remaining errors
npm run lint:src

# Fix manually:
# - Add missing semicolons
# - Fix indentation
# - Remove unused imports
# - Fix any type usage
```

---

### Issue 3.3: Build Fails - Cannot Find Module

**Symptom:**
```bash
npm run build
# error TS2307: Cannot find module '../generated/api'
```

**Diagnosis:**
Types not generated or wrong import path.

**Solution:**
```bash
# Check if generated directory exists
ls generated/api/

# If not, generate
npm run clean && npm run generate

# Check import path
# Correct: import { User } from '../generated/api';
# Wrong: import { User } from '../generated';
# Wrong: import { User } from './generated/api';

# Rebuild
npm run build
```

---

### Issue 3.4: Connection Context in Producer

**Symptom:**
```bash
grep -E "(apiKey|token|baseUrl):" src/*Producer.ts
# Output: apiKey: string,
```

**Diagnosis:**
Producer duplicating connection context.

**Solution:**
```typescript
// ❌ WRONG
class UserProducer {
  async getUser(
    userId: string,
    apiKey: string,    // NO!
    baseUrl: string    // NO!
  ): Promise<User> {
    // ...
  }
}

// ✅ CORRECT
class UserProducer {
  constructor(private client: ServiceClient) {}

  async getUser(userId: string): Promise<User> {
    // apiKey and baseUrl handled by client
    const { data } = await this.client.apiClient.request({
      url: `/users/${userId}`,
      method: 'get'
    });
    // ...
  }
}
```

---

### Issue 3.5: Mapper Not Validating Required Fields

**Symptom:**
Runtime error when API doesn't return expected field:
```
TypeError: Cannot read property 'toString' of undefined
```

**Diagnosis:**
Mapper not validating required fields.

**Solution:**
```typescript
// ❌ WRONG - No validation
export function toUser(raw: any): User {
  const output: User = {
    id: raw.id.toString(), // Crashes if raw.id is undefined!
    email: map(Email, raw.email)
  };
  return output;
}

// ✅ CORRECT - Validate first
export function toUser(raw: any): User {
  // Validate required fields
  ensureProperties(raw, ['id', 'email']);

  const output: User = {
    id: raw.id.toString(), // Safe now
    email: map(Email, raw.email)
  };
  return output;
}
```

---

## Gate 4 Failures: Unit Tests

### Issue 4.1: Nock Not Matching Request

**Symptom:**
```
Error: Nock: No match for request {
  method: 'GET',
  url: 'https://api.example.com/users/123'
}
```

**Diagnosis:**
Nock mock doesn't match actual request.

**Solution:**
```typescript
// Check producer code
const { data } = await this.client.apiClient.request({
  url: `/users/${userId}`,  // What's the actual path?
  method: 'get'
});

// Update nock to match EXACTLY
nock('https://api.example.com')
  .get('/users/123')  // Must match exactly
  .reply(200, fixture);

// For dynamic IDs, use regex
nock('https://api.example.com')
  .get(/\/users\/\d+/)
  .reply(200, fixture);

// For query parameters
nock('https://api.example.com')
  .get('/users')
  .query({ limit: 10, offset: 0 })
  .reply(200, fixture);
```

---

### Issue 4.2: Unit Tests Using Environment Variables

**Symptom:**
```bash
grep -i "process.env" test/unit/Common.ts
# Output: const API_KEY = process.env.API_KEY
```

**Diagnosis:**
Unit tests using environment variables (forbidden).

**Solution:**
```typescript
// ❌ WRONG - Unit test Common.ts
import { config } from 'dotenv';
config();
export const API_KEY = process.env.API_KEY;

// ✅ CORRECT - Unit test Common.ts
import * as nock from 'nock';
// NO dotenv, NO process.env

export async function getConnectedInstance() {
  nock('https://api.example.com')
    .post('/auth/login')
    .reply(200, { access_token: 'test-token', expires_in: 3600 });

  const connector = newService();
  await connector.connect({
    email: 'test@example.com',  // Hardcoded test data OK in unit tests
    password: 'testpass'
  });

  return connector;
}
```

**Why**: Unit tests must work offline without any .env file.

---

### Issue 4.3: Forbidden Mocking Library

**Symptom:**
```bash
grep -rE "jest\.mock|sinon" test/unit/*.ts
# Output: import sinon from 'sinon';
```

**Diagnosis:**
Using forbidden mocking library.

**Solution:**
```typescript
// ❌ WRONG
import sinon from 'sinon';
const stub = sinon.stub(client, 'getUser');

// ❌ WRONG
import { jest } from '@jest/globals';
jest.mock('./ServiceClient');

// ✅ CORRECT - ONLY nock
import * as nock from 'nock';

nock('https://api.example.com')
  .get('/users/123')
  .reply(200, fixture);
```

**Remove forbidden libraries:**
```bash
npm uninstall sinon jest
# Use only nock for HTTP mocking
```

---

### Issue 4.4: Unit Tests Failing - Type Mismatches

**Symptom:**
```
AssertionError: expected 'user@example.com' to be an instance of Email
```

**Diagnosis:**
Mapper not converting to Email type.

**Solution:**
```typescript
// Check mapper
export function toUser(raw: any): User {
  const output: User = {
    email: raw.email  // ❌ Just string, not Email type
  };
  return output;
}

// Fix to use map()
export function toUser(raw: any): User {
  const output: User = {
    email: map(Email, raw.email)  // ✅ Converts to Email type
  };
  return output;
}
```

---

## Gate 5 Failures: Integration Tests

### Issue 5.1: Integration Tests Skipping

**Symptom:**
```
Integration Tests
  - should connect to API (skipped)
  - should list users (skipped)
```

**Diagnosis:**
hasCredentials() returning false.

**Solution:**

**Step 1: Check .env file exists**
```bash
ls -la .env
# If not found, create it
touch .env
```

**Step 2: Add credentials**
```bash
cat > .env << EOF
SERVICE_EMAIL=your-email@example.com
SERVICE_PASSWORD=your-password
SERVICE_TEST_USER_ID=test-user-id
EOF
```

**Step 3: Verify Common.ts loads dotenv**
```typescript
// test/integration/Common.ts
import { config } from 'dotenv';

// MUST have this at the top
config();

export const SERVICE_EMAIL = process.env.SERVICE_EMAIL || '';
export const SERVICE_PASSWORD = process.env.SERVICE_PASSWORD || '';

export function hasCredentials(): boolean {
  return !!SERVICE_EMAIL && !!SERVICE_PASSWORD;
}
```

**Step 4: Run tests**
```bash
npm run test:integration
# Should no longer skip
```

---

### Issue 5.2: Invalid Credentials Error

**Symptom:**
```
Error: InvalidCredentialsError
  at connect (/Users/.../ServiceClient.ts:45:13)
```

**Diagnosis:**
Credentials in .env are invalid or expired.

**Solution:**

**Step 1: Verify credentials manually**
```bash
# Get credentials from .env
cat .env | grep SERVICE_EMAIL
cat .env | grep SERVICE_PASSWORD

# Test with curl
curl -X POST "https://api.service.com/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"your-email\",\"password\":\"your-password\"}"

# Should return 200 with access_token
```

**Step 2: If credentials invalid**
- Log into service dashboard
- Regenerate API key
- Update .env file
- Retry tests

**Step 3: Check for special characters**
```bash
# If password has special characters, quote it
SERVICE_PASSWORD='p@ssw0rd!'  # Use single quotes
```

---

### Issue 5.3: Hardcoded Test Values

**Symptom:**
```bash
grep -E "(const|let|var) [a-zA-Z]*[Ii]d = ['\"][0-9]+['\"]" test/integration/*.ts
# Output: const userId = '12345';
```

**Diagnosis:**
Test values hardcoded instead of in .env.

**Solution:**

**Step 1: Move to .env**
```bash
# Add to .env
echo "SERVICE_TEST_USER_ID=12345" >> .env
```

**Step 2: Export from Common.ts**
```typescript
// test/integration/Common.ts
export const SERVICE_TEST_USER_ID = process.env.SERVICE_TEST_USER_ID || '';
```

**Step 3: Use in tests**
```typescript
// ❌ WRONG
it('should get user', async () => {
  const user = await api.getUser('12345'); // Hardcoded
});

// ✅ CORRECT
import { SERVICE_TEST_USER_ID } from './Common';

it('should get user', async () => {
  const user = await api.getUser(SERVICE_TEST_USER_ID); // From .env
});
```

---

### Issue 5.4: Integration Tests Failing - Wrong Data

**Symptom:**
```
AssertionError: expected 'John' to equal 'Jane'
```

**Diagnosis:**
Test assertions based on wrong assumptions about API data.

**Solution:**

**Step 1: Check actual API response**
```bash
LOG_LEVEL=debug npm run test:integration
# Look at debug output
```

**Step 2: Update test expectations**
```typescript
// ❌ WRONG - Assuming specific name
expect(user.firstName).to.equal('John');

// ✅ CORRECT - Verify structure, not exact values
expect(user.firstName).to.be.a('string');
expect(user.firstName.length).to.be.greaterThan(0);

// Or if you control test data
const userId = SERVICE_TEST_USER_ID; // Known test user
const user = await api.getUser(userId);
expect(user.firstName).to.equal('Expected Name'); // Known value
```

---

### Issue 5.5: No Debug Logging in Integration Tests

**Symptom:**
Integration tests pass but you can't see what's happening.

**Diagnosis:**
Missing debug logging.

**Solution:**
```typescript
// Add to test file
import { getLogger } from './Common';

const logger = getLogger('UserProducerTest');

describe('UserProducer Integration', function () {
  it('should get user', async () => {
    const userId = SERVICE_TEST_USER_ID;

    logger.debug(`api.getUser(${userId})`);
    const user = await api.getUser(userId);
    logger.debug('→', JSON.stringify(user, null, 2));

    expect(user.id).to.equal(userId);
  });
});
```

**Run with debug:**
```bash
LOG_LEVEL=debug npm run test:integration
# Now you see full API responses
```

---

## Gate 6 Failures: Build

### Issue 6.1: Build Fails - TypeScript Errors

**Symptom:**
```bash
npm run build
# error TS2322: Type 'string' is not assignable to type 'Email'
```

**Diagnosis:**
Type mismatch in code.

**Solution:**
```typescript
// ❌ WRONG
const output: User = {
  email: raw.email  // raw.email is string, but email field expects Email type
};

// ✅ CORRECT
const output: User = {
  email: map(Email, raw.email)  // Converts string to Email
};
```

---

### Issue 6.2: Shrinkwrap Fails

**Symptom:**
```bash
npm run shrinkwrap
# npm ERR! Could not resolve dependency
```

**Diagnosis:**
Dependency conflict or corrupted node_modules.

**Solution:**
```bash
# Clean and reinstall
rm -rf node_modules package-lock.json npm-shrinkwrap.json
npm install

# Retry shrinkwrap
npm run shrinkwrap

# Verify
ls npm-shrinkwrap.json
```

---

### Issue 6.3: Missing dist Files

**Symptom:**
```bash
npm run build
# No errors, but dist/ is empty
```

**Diagnosis:**
tsconfig.json misconfigured or no source files.

**Solution:**

**Check tsconfig.json:**
```json
{
  "compilerOptions": {
    "outDir": "./dist",  // Must be set
    "rootDir": "./src"   // Should be set
  },
  "include": ["src/**/*"],  // Must include source files
  "exclude": ["node_modules", "dist", "test"]
}
```

**Check source files exist:**
```bash
ls -la src/
# Should have .ts files
```

**Rebuild:**
```bash
npm run build
ls dist/
```

---

## Environment & Setup Issues

### Issue 7.1: Yeoman Generator Not Found

**Symptom:**
```bash
yo @auditmation/hub-module
# Error: yo: command not found
```

**Diagnosis:**
Yeoman not installed.

**Solution:**
```bash
# Install Yeoman
npm install -g yo

# Install generator
npm install -g @auditmation/generator-hub-module

# Verify
yo --version
yo --generators  # Should show @auditmation/hub-module
```

---

### Issue 7.2: Permission Denied Installing Global Packages

**Symptom:**
```bash
npm install -g yo
# Error: EACCES: permission denied
```

**Diagnosis:**
npm global directory requires sudo (bad practice).

**Solution:**
```bash
# Configure npm to use local directory
mkdir ~/.npm-global
npm config set prefix '~/.npm-global'

# Add to PATH
echo 'export PATH=~/.npm-global/bin:$PATH' >> ~/.bashrc
source ~/.bashrc

# Retry installation
npm install -g yo @auditmation/generator-hub-module
```

---

### Issue 7.3: Wrong Node Version

**Symptom:**
```bash
npm install
# Error: Requires Node.js >= 16.0.0
```

**Diagnosis:**
Using old Node version.

**Solution:**
```bash
# Check version
node --version

# Install nvm if needed
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.0/install.sh | bash

# Install Node 18
nvm install 18
nvm use 18

# Verify
node --version  # Should show v18.x.x

# Retry
npm install
```

---

## Common Development Mistakes

### Mistake 1: Forgetting npm run sync-meta

**Symptom:**
```bash
grep "^title:" api.yml
# Output: (empty or wrong title)
```

**Diagnosis:**
Forgot to run sync-meta after Yeoman.

**Solution:**
```bash
# Run sync-meta
npm run sync-meta

# Verify
grep -E "^(title|version):" api.yml
# Should show title and version matching package.json
```

**Prevention**: ALWAYS run sync-meta after Yeoman, before npm install.

---

### Mistake 2: Editing Generated Files

**Symptom:**
Types customized in generated/ directory, then lost after regeneration.

**Diagnosis:**
Editing generated files directly (they get overwritten).

**Solution:**
```
❌ NEVER edit files in generated/ directory
✅ Change api.yml instead
✅ Regenerate with npm run generate
```

**If you need custom types:**
- Create them in src/ directory
- Import and use alongside generated types
- Don't modify generated files

---

### Mistake 3: Using Logical OR for Optional Fields

**Symptom:**
```typescript
// Empty strings become undefined
lastName: raw.last_name || undefined
```

**Diagnosis:**
Logical OR treats "" as falsy.

**Solution:**
```typescript
// ❌ WRONG
lastName: raw.last_name || undefined  // "" → undefined
age: raw.age || undefined  // 0 → undefined
active: raw.active || undefined  // false → undefined

// ✅ CORRECT
lastName: optional(raw.last_name)  // null→undefined, keeps ""
age: optional(raw.age)  // null→undefined, keeps 0
active: optional(raw.active)  // null→undefined, keeps false
```

---

### Mistake 4: Skipping Validation Gates

**Symptom:**
Module deployed but fails in production.

**Diagnosis:**
Skipped gate validation, issues not caught.

**Solution:**
```bash
# ALWAYS run all gates before deployment
npm run clean && npm run generate  # Gate 2
npm run lint:src                    # Gate 3
npm run build                       # Gate 3
npm run test:unit                   # Gate 4
npm run test:integration            # Gate 5
npm run shrinkwrap                  # Gate 6

# Or use validation script
./validate-all-gates.sh
```

**Prevention**: Make gate validation part of commit process.

---

### Mistake 5: Not Testing with curl First

**Symptom:**
Implementing operation, then discovering API doesn't work as documented.

**Diagnosis:**
Coded based on docs without testing.

**Solution:**
```bash
# ALWAYS test with curl BEFORE implementing
curl -X GET "https://api.example.com/users/123" \
  -H "Authorization: Bearer $API_KEY"

# Save response
curl ... > response.json

# Review structure
cat response.json | jq .

# THEN implement based on actual response
```

**Prevention**: "curl first, code second" rule.

---

## Diagnostic Decision Tree

```
Problem?
├─ Build failing?
│  ├─ TypeScript errors? → Check imports, regenerate types
│  ├─ Module not found? → Install dependencies
│  └─ Syntax errors? → Fix TypeScript syntax
│
├─ Tests failing?
│  ├─ Unit tests?
│  │  ├─ Nock not matching? → Check request path/method
│  │  └─ Type errors? → Check mapper conversions
│  │
│  └─ Integration tests?
│     ├─ Skipping? → Check .env file, hasCredentials()
│     ├─ 401 error? → Verify credentials with curl
│     └─ Assertion failures? → Check debug logs, update expectations
│
├─ Types not generated?
│  ├─ YAML invalid? → Validate with swagger-cli
│  ├─ InlineResponse? → Move schemas to components
│  └─ Java not found? → Install Java >= 11
│
├─ Gate failures?
│  ├─ Gate 1? → Check 12 critical rules
│  ├─ Gate 2? → Check type generation
│  ├─ Gate 3? → Check implementation quality
│  ├─ Gate 4? → Check unit tests
│  ├─ Gate 5? → Check integration tests
│  └─ Gate 6? → Check build and shrinkwrap
│
└─ Not sure?
   └─ Run complete validation and check each gate systematically
```

---

## Emergency Recovery Procedures

### Procedure 1: Complete Reset

**When**: Everything is broken, nothing works

**Steps:**
```bash
# 1. Save your work
git stash

# 2. Clean everything
rm -rf node_modules dist generated package-lock.json npm-shrinkwrap.json

# 3. Reinstall
npm install

# 4. Regenerate types
npm run clean && npm run generate

# 5. Rebuild
npm run build

# 6. Test
npm run test:unit
npm run test:integration

# 7. If still broken, restore clean state
git stash pop
# Fix issues one by one
```

---

### Procedure 2: Isolate the Problem

**When**: Tests started failing after changes

**Steps:**
```bash
# 1. What changed?
git status
git diff

# 2. Revert changes temporarily
git stash

# 3. Do tests pass now?
npm test

# If YES:
# - Changes broke tests
# - Review what you changed
# - Fix implementation

# If NO:
# - Something else is broken
# - Check .env file
# - Check node_modules
# - Check generated types
```

---

### Procedure 3: Debug Failing Mapper

**When**: Mapper throwing errors or producing wrong output

**Steps:**

**1. Create isolation test:**
```typescript
// test-mapper.ts
import { toUser } from './src/Mappers';

const testData = {
  id: '123',
  email: 'user@example.com',
  first_name: 'John',
  created_at: '2025-01-01T00:00:00Z'
};

console.log('Input:', JSON.stringify(testData, null, 2));

try {
  const user = toUser(testData);
  console.log('Output:', JSON.stringify(user, null, 2));
} catch (error) {
  console.error('Error:', error);
}
```

**2. Run isolation test:**
```bash
npx ts-node test-mapper.ts
```

**3. Debug step by step:**
```typescript
export function toUser(raw: any): User {
  console.log('1. Raw input:', raw);

  ensureProperties(raw, ['id', 'email']);
  console.log('2. Validation passed');

  const email = map(Email, raw.email);
  console.log('3. Email mapped:', email);

  const output: User = {
    id: raw.id.toString(),
    email: email,
    firstName: raw.first_name
  };

  console.log('4. Final output:', output);
  return output;
}
```

**4. Fix issues found**

**5. Remove debug logging**

---

## Quick Fixes Reference

| Symptom | Quick Fix |
|---------|-----------|
| snake_case found | Change to camelCase in api.yml |
| describe found | Change to get/list/create/update/delete |
| Error responses | Remove all 4xx/5xx from api.yml |
| InlineResponse | Move schema to components/schemas |
| Promise<any> | Import and use generated types |
| Build fails | `npm run clean && npm run generate` |
| Tests skip | Create .env with credentials |
| Nock not matching | Check URL path exactly matches |
| Module not found | `npm install <package>` |
| Permission denied | Configure npm prefix for global packages |

---

## Getting Help

### Self-Help Steps

**Before asking for help:**
1. Run ./validate-all-gates.sh
2. Read error messages carefully
3. Check this troubleshooting guide
4. Review relevant training manual chapter
5. Check .claude/rules/ for specific pattern
6. Test with curl to isolate API issues

### When to Ask for Help

**Ask when:**
- Tried all troubleshooting steps
- Error message unclear
- API behaving unexpectedly
- Stuck for > 30 minutes on same issue

**Provide when asking:**
- Error message (full output)
- What you've tried
- Relevant code snippets
- Module identifier
- Gate that's failing

---

## Prevention Checklist

**Prevent issues by:**
- [ ] Running validation early and often
- [ ] Testing API with curl before implementing
- [ ] Following templates exactly
- [ ] Using code from Chapter 17
- [ ] Running gates incrementally
- [ ] Keeping .env file updated
- [ ] Committing after each phase
- [ ] Reading error messages carefully
- [ ] Using debug logging from start
- [ ] Following the 12 critical rules

---

## Final Tips

**Most common issues:**
1. Forgetting to regenerate types (30% of issues)
2. Missing .env file (20% of issues)
3. snake_case in api.yml (15% of issues)
4. Hardcoded test data (10% of issues)
5. Not following 12 critical rules (10% of issues)

**Quick wins:**
- Run validation scripts frequently
- Use templates from Chapter 17
- Test with curl early
- Keep debug logging
- Follow checklists religiously

**Remember:**
- Read error messages carefully
- Fix root cause, not symptoms
- Validate after every fix
- All 6 gates must pass

---

**VERSION**: 1.0 | **LAST UPDATED**: 2025-10-22
