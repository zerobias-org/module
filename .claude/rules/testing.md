# Testing Rules

## 🚨 CRITICAL RULES (Immediate Failure)

### 1. Zero Test Failure Tolerance
- ANY existing test failure STOPS the update process
- NO excuses for test failures
- Determine cause and fix immediately
- Never disable or skip tests to proceed

### 2. Test Coverage Requirements
- ALL new operations MUST have unit test coverage
- ALL new operations MUST have integration tests
- Integration tests run when credentials available, fail naturally when not
- Never use `.skip()` on individual tests
- Tests should be written and ready to work

### 3. Test Preservation
- EXISTING tests must pass throughout entire workflow
- NEVER modify existing tests during updates
- Add new tests separately

### 4. API Validation Before Implementation
- Test every operation with curl/Node.js FIRST
- Capture real API responses for test fixtures
- Verify credentials work before implementing

### 5. Test Failure Resolution Protocol

**MANDATORY when tests fail:**

1. **ANALYZE** the failure - understand why
2. **FIX THE IMPLEMENTATION** - correct bugs in src/
3. **NEVER SKIP TESTS** - no .skip(), .only(), or comments
4. **NEVER MODIFY GENERATED FILES**
5. **RE-RUN TESTS** - execute until ALL pass
6. **REPEAT UNTIL ZERO FAILURES**

**PROHIBITED**:
- Using `.skip()` on individual tests
- Using `.only()` to run only passing tests
- Commenting out failing tests
- Accepting any failures as "expected"
- Modifying test expectations to match wrong behavior

**Required Pass Rates**:
- **Unit Tests**: 100% pass rate
- **Integration Tests**: 100% when credentials available
- **Lint**: Zero errors, zero warnings
- **Build**: Exit code 0

### 6. HTTP Mocking - ONLY Use Nock

**🚨 CRITICAL: Use ONLY `nock` for HTTP mocking in unit tests**

```typescript
// ✅ CORRECT - Using nock
import * as nock from 'nock';

describe('UserProducer', () => {
  it('should get user', async () => {
    nock('https://api.example.com')
      .get('/users/123')
      .reply(200, { id: '123', name: 'John' });

    const user = await producer.getUser('123');
    expect(user.id).to.be.instanceof(UUID);
  });
});

// ❌ FORBIDDEN - Other mocking libraries
import { jest } from '@jest/globals';  // NO
import sinon from 'sinon';  // NO
import fetchMock from 'fetch-mock';  // NO
```

**WHY**:
- Nock intercepts HTTP requests at the network level
- Consistent with existing test infrastructure
- Works with any HTTP client library (axios, node-fetch, etc.)
- Can record and replay real API interactions

**NEVER**:
- ❌ Use jest.mock()
- ❌ Use sinon stubs/spies for HTTP
- ❌ Use fetch-mock or similar
- ❌ Mock the HTTP client class directly
- ❌ Use any mocking library except nock

### 7. Test Data Configuration - NEVER Hardcode Values

**🚨 CRITICAL: ALL integration test values MUST be in .env, NEVER hardcoded**

When user provides test values (IDs, names, etc.), they MUST go in `.env`:

```bash
# ✅ CORRECT - Test values in .env
SERVICE_API_KEY=your-api-key
SERVICE_TEST_USER_ID=12345
SERVICE_TEST_ORGANIZATION_ID=1067
SERVICE_TEST_RESOURCE_NAME=test-resource

# ❌ WRONG - Hardcoding in test files
const userId = '12345';  // NO!
const orgId = '1067';    // NO!
```

**RULE**:
1. User gives you test values → Add to `.env` immediately
2. Export from `test/integration/Common.ts`
3. Import and use in integration tests
4. NEVER hardcode any test data values

**Example**:

```typescript
// .env
AVIGILON_ALTA_ACCESS_TEST_ORGANIZATION_ID=1067

// test/integration/Common.ts
export const AVIGILON_ALTA_ACCESS_TEST_ORGANIZATION_ID =
  process.env.AVIGILON_ALTA_ACCESS_TEST_ORGANIZATION_ID || '';

// test/integration/OrganizationProducerTest.ts
import { AVIGILON_ALTA_ACCESS_TEST_ORGANIZATION_ID } from './Common';

it('should retrieve organization', async () => {
  const organizationId = AVIGILON_ALTA_ACCESS_TEST_ORGANIZATION_ID;  // ✅ From .env
  // NOT: const organizationId = '1067';  // ❌ Hardcoded

  const org = await api.get(organizationId);
  expect(org.id).to.equal(organizationId);
});
```

**WHY**:
- Test values are configuration, not code
- Different developers may need different test IDs
- Makes tests portable across environments
- Easy to update without touching code
- Consistent with credential management

### 8. Credential Loading for Integration Tests

**🚨 CRITICAL: Automatic credential loading with dotenv**

Integration tests need credentials and test values from `.env` file. Configure automatic loading:

**Step 0: Create .env file after scaffolding**

**IMMEDIATELY after running module scaffolding**, create a `.env` file in the module root with credentials from the user's initial request:

```bash
# Example: User said "credentials are in .env: SERVICE_EMAIL and SERVICE_PASSWORD"
# Create module/.env with:
SERVICE_EMAIL=user@example.com
SERVICE_PASSWORD=their-password
SERVICE_API_KEY=their-api-key
```

**Important**: Extract credentials from wherever user specified in their request:
- Module root `.env`
- Repository root `.env`
- `.connectionProfile.json`
- User message directly
- System environment

**Step 1: Install dotenv**
```bash
npm install --save-dev dotenv
```

**Step 2: Configure .mocharc.json**
```json
{
  "extension": ["ts"],
  "require": ["ts-node/register", "dotenv/config"]
}
```

**Step 3: Load dotenv explicitly in test/Common.ts**
```typescript
// test/Common.ts - ONLY file allowed to access process.env
import { config } from 'dotenv';

// Load .env file explicitly to ensure credentials are available
config();

export const SERVICE_API_KEY = process.env.SERVICE_API_KEY || '';
export const SERVICE_BASE_URL = process.env.SERVICE_BASE_URL || 'https://api.example.com';

export function hasCredentials(): boolean {
  return !!SERVICE_API_KEY;
}
```

**Why explicit config()?**
- .mocharc.json `"require": ["dotenv/config"]` loads dotenv, BUT
- Environment variables in Common.ts are evaluated at module load time
- Explicit `config()` ensures .env is loaded BEFORE env vars are accessed
- This guarantees integration tests can detect credentials properly

**Step 4: Use in integration tests**
```typescript
import { hasCredentials, SERVICE_API_KEY } from './Common';

describe('Service Integration Tests', function () {
  before(function () {
    if (!hasCredentials()) {
      this.skip(); // Skip entire suite if no credentials
    }
  });

  it('should connect with real API', async function () {
    // Test with real credentials
  });
});
```

**Benefits**:
- ✅ Credentials automatically loaded from `.env`
- ✅ No manual process.env access in test files
- ✅ Graceful skipping when credentials unavailable
- ✅ Consistent credential management across all modules

**Document in USERGUIDE.md**:
```markdown
### Setting Up Credentials for Testing

Create a `.env` file in the module root:

\`\`\`bash
SERVICE_API_KEY=your-api-key
SERVICE_SECRET=your-secret
\`\`\`

The test suite automatically loads credentials using `dotenv`.
```

## 🟡 STANDARD RULES

### Test File Naming Convention

**MANDATORY naming pattern for test files:**

```
test/unit/{Resource}ProducerTest.ts       # Producer unit tests
test/unit/ConnectionTest.ts               # Connection unit tests
test/unit/MappersTest.ts                  # Mapper unit tests
test/integration/{Resource}ProducerTest.ts # Producer integration tests
test/integration/ConnectionTest.ts         # Connection integration tests
```

**Examples**:
- ✅ `test/unit/AccessProducerTest.ts` - unit test
- ✅ `test/integration/AccessProducerTest.ts` - integration test (same name, different folder)
- ✅ `test/unit/UserProducerTest.ts` - unit test
- ✅ `test/integration/UserProducerTest.ts` - integration test
- ✅ `test/unit/ConnectionTest.ts` - unit test
- ✅ `test/integration/ConnectionTest.ts` - integration test

**Rationale**:
- Consistent naming makes test files easy to find
- Follows TypeScript naming conventions (PascalCase with Test suffix)
- Integration tests use SAME names as unit tests - folder location distinguishes them
- No need for `IntegrationTest` suffix since `test/integration/` folder makes it clear
- Simpler and cleaner naming pattern

### Connection Testing - Mandatory Unit and Integration Tests

**EVERY module MUST have connection tests in BOTH unit and integration:**

**Unit Tests: `test/unit/ConnectionTest.ts`** - Tests connection lifecycle with mocked HTTP
**Integration Tests: `test/integration/ConnectionTest.ts`** - Tests with real API

#### Unit Test (ConnectionTest.ts)

```typescript
// test/unit/ConnectionTest.ts
import { expect } from 'chai';
import * as nock from 'nock';
import { Email, InvalidCredentialsError } from '@auditmation/types-core-js';
import { newService } from '../../src';

describe('Connection', () => {
  describe('connect', () => {
    it('should successfully authenticate and store token', async () => {
      nock('https://api.example.com')
        .post('/auth/login')
        .reply(200, { accessToken: 'test-token', expiresAt: '2025-10-02T00:00:00Z' });

      const connector = newService();
      const connectionState = await connector.connect({
        email: new Email('test@example.com'),
        password: 'testpass',
      });

      expect(connectionState).to.have.property('accessToken');
      expect(await connector.isConnected()).to.be.true;
    });

    it('should handle authentication failure', async () => {
      nock('https://api.example.com')
        .post('/auth/login')
        .reply(401, { error: 'Invalid credentials' });

      const connector = newService();

      try {
        await connector.connect({
          email: new Email('test@example.com'),
          password: 'wrongpass',
        });
        expect.fail('Should have thrown an error');
      } catch (error: any) {
        expect(error).to.be.instanceOf(InvalidCredentialsError);
      }
    });
  });

  describe('isConnected', () => {
    it('should return true when connected', async () => {
      nock('https://api.example.com')
        .post('/auth/login')
        .reply(200, { accessToken: 'test-token', expiresAt: '2025-10-02T00:00:00Z' });

      const connector = newService();
      await connector.connect({
        email: new Email('test@example.com'),
        password: 'testpass',
      });

      expect(await connector.isConnected()).to.be.true;
    });

    it('should return false when not connected', async () => {
      const connector = newService();
      expect(await connector.isConnected()).to.be.false;
    });
  });

  describe('disconnect', () => {
    it('should clear connection state', async () => {
      nock('https://api.example.com')
        .post('/auth/login')
        .reply(200, { accessToken: 'test-token', expiresAt: '2025-10-02T00:00:00Z' });

      const connector = newService();
      await connector.connect({
        email: new Email('test@example.com'),
        password: 'testpass',
      });

      await connector.disconnect();
      expect(await connector.isConnected()).to.be.false;
    });
  });
});
```

**Benefits**:
- ✅ Ensures connection lifecycle works correctly with mocked HTTP
- ✅ Tests all connection methods systematically
- ✅ Fast execution without network calls

#### Integration Test (ConnectionTest.ts)

**MANDATORY: Also create integration tests for connection with real API**

```typescript
// test/integration/ConnectionTest.ts
import { expect } from 'chai';
import { getLogger } from '@auditmation/util-logger';
import { Email } from '@auditmation/types-core-js';
import { newService } from '../../src';
import { SERVICE_EMAIL, SERVICE_PASSWORD, hasCredentials } from './Common';

const logger = getLogger('ConnectionTest');

describe('Connection Integration Tests', function () {
  this.timeout(30000);

  before(function () {
    if (!hasCredentials()) {
      this.skip();
    }
  });

  describe('connect', () => {
    it('should successfully connect to real API', async () => {
      const connector = newService();

      logger.debug('connector.connect(email, password)');
      const connectionState = await connector.connect({
        email: new Email(SERVICE_EMAIL),
        password: SERVICE_PASSWORD,
      });
      logger.debug('→', JSON.stringify(connectionState, null, 2));

      expect(connectionState).to.have.property('accessToken');
    });

    it('should fail with invalid credentials', async () => {
      const connector = newService();

      logger.debug('connector.connect(invalid credentials)');
      try {
        await connector.connect({
          email: new Email('invalid@example.com'),
          password: 'wrongpassword',
        });
        expect.fail('Should have thrown an error');
      } catch (error: any) {
        logger.debug('→ error', JSON.stringify({ name: error.name }, null, 2));
        expect(error).to.exist;
      }
    });
  });

  describe('isConnected', () => {
    it('should return true when connected', async () => {
      const connector = newService();

      await connector.connect({
        email: new Email(SERVICE_EMAIL),
        password: SERVICE_PASSWORD,
      });

      logger.debug('connector.isConnected()');
      const isConnected = await connector.isConnected();
      logger.debug('→', JSON.stringify({ isConnected }, null, 2));

      expect(isConnected).to.be.true;
    });

    it('should return false when not connected', async () => {
      const connector = newService();

      logger.debug('connector.isConnected() - without connection');
      const isConnected = await connector.isConnected();
      logger.debug('→', JSON.stringify({ isConnected }, null, 2));

      expect(isConnected).to.be.false;
    });
  });

  describe('disconnect', () => {
    it('should successfully disconnect from API', async () => {
      const connector = newService();

      await connector.connect({
        email: new Email(SERVICE_EMAIL),
        password: SERVICE_PASSWORD,
      });

      logger.debug('connector.disconnect()');
      await connector.disconnect();
      logger.debug('→ disconnect completed');
    });
  });
});
```

**Benefits**:
- ✅ Tests real API authentication and connection
- ✅ Verifies credentials work correctly
- ✅ Catches network and API-specific issues
- ✅ Debug logging shows actual API responses

### Connected Instance Helpers

**CRITICAL: Unit and integration tests have DIFFERENT Common.ts patterns**

#### Unit Test Common.ts - Fully Mocked, No Environment Variables

```typescript
// test/unit/Common.ts
import * as nock from 'nock';
import { Email } from '@auditmation/types-core-js';
import { newService } from '../../src';
import type { ServiceConnector } from '../../src';

/**
 * Get a connected instance for unit testing.
 * Uses mocked HTTP - no real credentials needed.
 * Unit tests should NEVER depend on environment variables.
 */
export async function getConnectedInstance(): Promise<ServiceConnector> {
  nock('https://api.example.com')
    .post('/auth/login')
    .reply(200, {
      accessToken: 'test-token-123',
      expiresAt: '2025-10-02T00:00:00Z',
    });

  const connector = newService();

  await connector.connect({
    email: new Email('test@example.com'),
    password: 'testpass',
  });

  return connector;
}
```

**Key Points**:
- ✅ NO environment variables or dotenv
- ✅ NO credential loading
- ✅ Uses nock to mock HTTP requests
- ✅ Works without any .env file
- ✅ Always returns a mocked connected instance

#### Integration Test Common.ts - Real Credentials with Connection Caching

```typescript
// test/integration/Common.ts
import { config } from 'dotenv';
import { Email } from '@auditmation/types-core-js';
import { getLogger as getLoggerBase } from '@auditmation/util-logger';
import { newService } from '../../src';
import type { ServiceConnector } from '../../src';

// Load .env file explicitly to ensure credentials are available
config();

export const SERVICE_EMAIL = process.env.SERVICE_EMAIL || '';
export const SERVICE_PASSWORD = process.env.SERVICE_PASSWORD || '';

// Test data values - export any test IDs, names, or other values from .env
export const SERVICE_TEST_USER_ID = process.env.SERVICE_TEST_USER_ID || '';
export const SERVICE_TEST_ORGANIZATION_ID = process.env.SERVICE_TEST_ORGANIZATION_ID || '';

const LOG_LEVEL = (process.env.LOG_LEVEL || 'info') as string;

/**
 * Get a logger with configurable level from LOG_LEVEL env var.
 * Usage: LOG_LEVEL=debug npm run test:integration
 */
export function getLogger(name: string) {
  return getLoggerBase(name, {}, LOG_LEVEL);
}

export function hasCredentials(): boolean {
  return !!SERVICE_EMAIL && !!SERVICE_PASSWORD;
}

// Cached connector instance - connect once, reuse many times
let cachedConnector: ServiceConnector | null = null;

/**
 * Get a connected instance for integration testing.
 * Connects once on first call, then returns cached instance on subsequent calls.
 * This avoids repeated authentication overhead across multiple tests.
 * Uses real credentials from .env file.
 * Makes real API calls.
 */
export async function getConnectedInstance(): Promise<ServiceConnector> {
  if (cachedConnector) {
    return cachedConnector;
  }

  const connector = newService();

  await connector.connect({
    email: new Email(SERVICE_EMAIL),
    password: SERVICE_PASSWORD,
  });

  cachedConnector = connector;
  return connector;
}
```

**Key Points**:
- ✅ Uses dotenv to load real credentials
- ✅ Exports credential constants
- ✅ Exports getLogger() helper that respects LOG_LEVEL env var
- ✅ Has hasCredentials() check
- ✅ **Connection caching**: Connects once on first call, returns cached instance on subsequent calls
- ✅ **Performance**: Avoids repeated authentication overhead across multiple integration tests
- ✅ Makes real API calls
- ✅ Requires .env file to run
- ✅ Run with `LOG_LEVEL=debug npm run test:integration` to see debug logs

**Usage in integration tests** (with real credentials and debug logging):
```typescript
// test/integration/ServiceProducerTest.ts
import { expect } from 'chai';
import { getConnectedInstance, hasCredentials, getLogger } from './Common';

const logger = getLogger('ServiceProducerTest');

describe('Service Integration', function () {
  before(function () {
    if (!hasCredentials()) {
      this.skip(); // Skip if no credentials
    }
  });

  it('should retrieve access token info from real API', async () => {
    logger.debug('getConnectedInstance()');
    const connector = await getConnectedInstance();

    const api = connector.getAccessApi();

    logger.debug('api.getToken()');
    const tokenInfo = await api.getToken();
    logger.debug('→', JSON.stringify(tokenInfo, null, 2));

    expect(tokenInfo.token).to.exist;
  });
});
```

**Usage in unit tests** (with mocked connection from Common.ts):
```typescript
// test/unit/AccessProducerTest.ts
import { expect } from 'chai';
import * as nock from 'nock';
import { getConnectedInstance } from './Common';

describe('AccessProducer', () => {
  let accessApi;

  beforeEach(async () => {
    // Use mocked connected instance from Common.ts
    // No env vars, no real credentials - fully mocked
    const connector = await getConnectedInstance();
    accessApi = connector.getAccessApi();
  });

  it('should retrieve access token info', async () => {
    nock('https://api.example.com')
      .get('/auth/accessTokens/test-token')
      .reply(200, { token: 'test-token', identityId: 123 });

    const tokenInfo = await accessApi.getToken();
    expect(tokenInfo.token).to.equal('test-token');
  });
});
```

**Benefits**:
- ✅ Integration tests: Reuse connected instance with real credentials from .env
- ✅ Unit tests: Reuse mocked connected instance from Common.ts
- ✅ Unit tests work without any .env file or credentials
- ✅ Consistent authentication patterns across test types
- ✅ Central place to update connection logic for both unit and integration tests

### Environment Variable Usage

**CRITICAL: ONLY integration tests use environment variables**

- **ONLY ALLOWED IN**: `test/integration/Common.ts`
- **NEVER in**: `test/unit/Common.ts` or any unit test files
- **Test files**: Import from Common.ts in same folder, NEVER access process.env directly

```typescript
// ✅ CORRECT: test/integration/Common.ts ONLY
import { config } from 'dotenv';

config();

export const TEST_API_KEY = process.env.TEST_API_KEY || '';
export const TEST_BASE_URL = process.env.TEST_BASE_URL || 'https://api.example.com';

export function hasCredentials(): boolean {
  return !!TEST_API_KEY;
}

// ✅ CORRECT: Integration test files
import { TEST_API_KEY, hasCredentials } from './Common';

// ✅ CORRECT: Unit test Common.ts - NO env vars
import * as nock from 'nock';
// Use only mocked helpers

// ❌ WRONG: Direct access in test files
const apiKey = process.env.TEST_API_KEY;

// ❌ WRONG: Env vars in unit test Common.ts
// Unit tests should NEVER depend on environment variables
```

### File Organization
```
test/
├── unit/
│   ├── Common.ts                    # ONLY mocked helpers (getConnectedInstance with nock)
│   ├── ConnectionTest.ts            # NO env vars, fully mocked
│   ├── {Resource}ProducerTest.ts    # NO env vars, fully mocked
│   └── MappersTest.ts               # Pure logic tests
└── integration/
    ├── Common.ts                    # Real credentials from .env + getConnectedInstance
    ├── ConnectionTest.ts            # Real API calls, requires .env
    └── {Resource}ProducerTest.ts    # Real API calls, requires .env
```

**Key points**:
- ✅ Both unit and integration folders have their own `Common.ts`
- ✅ Import from `./Common` (same folder) not `../Common`
- ✅ Test files in unit and integration look similar, except nocks vs real API calls
- ✅ **Unit `Common.ts`**: ONLY mocked helpers, NO env vars, works without .env
- ✅ **Integration `Common.ts`**: Real credentials from .env, has hasCredentials() check
- ✅ **Unit tests**: Run always, no credentials needed
- ✅ **Integration tests**: Skip if no credentials via hasCredentials() check

### Unit Test Pattern

```typescript
describe('{ClassName}', () => {
  describe('{methodName}', () => {
    it('should {expected behavior}', () => {
      // Arrange
      const mock = createMock();

      // Act
      const result = method(mock);

      // Assert
      expect(result).to.be.instanceof(ExpectedType);
    });
  });
});
```

### Integration Test Pattern

**MANDATORY: Add debug logging for all operations**

```typescript
import { getLogger, hasCredentials } from './Common';

const logger = getLogger('{Resource}ProducerTest');

describe('{Resource} Producer Integration', () => {
  before(function() {
    // Skip entire suite if no credentials
    if (!hasCredentials()) {
      this.skip(); // This is OK - skips whole suite
    }
  });

  it('should perform operation with real API', async () => {
    const connector = newService();

    logger.debug('connector.connect(email, password)');
    await connector.connect({ email, password });
    logger.debug('→', JSON.stringify(connectionState, null, 2));

    const api = connector.getResourceApi();

    logger.debug('api.getResource(id)');
    const result = await api.getResource('123');
    logger.debug('→', JSON.stringify(result, null, 2));

    expect(result).to.have.property('id');
  });
});
```

**Debug Logging Requirements**:
- ✅ Import getLogger from `./Common` (not from @auditmation/util-logger directly)
- ✅ Create logger with test file name: `const logger = getLogger('{Resource}ProducerTest')`
- ✅ Log operation call BEFORE execution: `logger.debug('api.methodName(param1, param2)')`
- ✅ Log result AFTER execution: `logger.debug('→', JSON.stringify(result, null, 2))`
- ✅ Use arrow `→` to indicate result
- ✅ Always use `JSON.stringify(result, null, 2)` for consistent formatting

**Why debug logging?**
- Visible when log level set to debug via `LOG_LEVEL=debug npm run test:integration`
- Shows exact operation calls with parameters
- Shows complete API responses for debugging
- Helps diagnose integration issues without re-running tests

**Note**: Using `this.skip()` in `before()` hook is ALLOWED - it gracefully skips the entire suite when credentials missing.

### Test Data Management
- Use captured real API responses as fixtures
- Store in test/fixtures/ directory
- Update fixtures when API changes
- Never use randomly generated test data

### Mock Strategy
- **ONLY use nock** for HTTP mocking in unit tests
- Mock at HTTP level, not at client/method level
- Use real responses from fixtures
- Test error scenarios with nock error responses

## 🟢 GUIDELINES

### Test Naming
- Descriptive test names explaining the scenario
- Use 'should' for expected behaviors
- Include failure cases: 'should throw error when...'

### Assertion Patterns
```typescript
// Type assertions
expect(user.id).to.be.instanceof(UUID);
expect(user.email).to.be.instanceof(Email);

// Structure assertions
expect(result).to.have.property('id');
expect(result).to.deep.equal(expectedObject);

// Error assertions - ALWAYS use specific core error types
try {
  await api.someMethod();
  expect.fail('Should have thrown an error');
} catch (error: any) {
  expect(error).to.be.instanceOf(InvalidCredentialsError); // ✅ Specific
  // NOT: expect(error).to.be.instanceOf(Error); // ❌ Too generic
}

// Common core error types to use in assertions:
// - InvalidCredentialsError (401 auth failures)
// - UnauthorizedError (403 permission denied)
// - NoSuchObjectError (404 not found)
// - RateLimitExceededError (429 rate limits)
// - InvalidInputError (validation failures)
// - UnexpectedError (other API errors)
```

### Test Execution Order
1. Run unit tests first (fast feedback)
2. Run integration tests with credentials
3. Document skipped tests in output
4. All must pass for task completion

### Credential Discovery
1. Check `.env` file first
2. Check `.connectionProfile.json` second
3. Ask user if not found
4. Skip integration tests gracefully if no credentials

### Common Failure Diagnoses
1. **Wrong API requests**: Log request/response, fix mapping
2. **Mapping issues**: Compare API response vs mapped output
3. **Auth failures**: Verify credentials and method
4. **Type mismatches**: Check generated types vs implementation

## Test Recording and Sanitization

### Unit Test Creation (When Credentials Available)

**Step 1: Record Real API Interactions**
```typescript
import * as nock from 'nock';

nock.recorder.rec({
  output_objects: true,
  enable_reqheaders_recording: true
});

await runIntegrationTest();
const recordings = nock.recorder.play();
```

**Step 2: AI-Powered Sanitization**

**CRITICAL**: Remove ALL PII before saving fixtures

**Sanitization Checklist**:
- [ ] Real names → "Jane Doe", "John Smith"
- [ ] Email addresses → "user@example.com"
- [ ] Phone numbers → "+1-555-0123"
- [ ] Addresses → "123 Main St, Anytown, ST 12345"
- [ ] SSNs/Tax IDs → "XXX-XX-1234"
- [ ] API keys → "sk_test_..."
- [ ] Passwords → "********"
- [ ] Birth dates → "1990-01-01"
- [ ] Company names → "Example Corp"

**Name Splitting**:
```json
// ✅ CORRECT
{
  "firstName": "Jane",
  "lastName": "Doe"
}
```

**Step 3: Generate Unit Tests**
1. Create unit test matching integration test structure
2. Use sanitized fixtures for mocked responses
3. Match test cases 1:1 with integration tests
4. Use HTTP-level mocking (not method mocking)

**Step 4: Fixture Organization**
```
test/fixtures/
├── recorded/          # Raw recordings (.localmemory)
├── sanitized/         # For unit tests
└── templates/         # Reusable templates
```

### Final Validation Cycle
```bash
npm run clean
npm run build
npm run test
npm run test:integration  # If credentials
```

**SUCCESS CRITERIA**:
- Zero test failures
- Zero skipped tests (except integration without creds)
- All PII removed from fixtures
- Build exits with code 0

## Exceptions

### When Tests Can Be Modified
- Only in 'fix' workflow (not 'update')
- When API has breaking changes
- When fixing incorrect test logic
- Document reason in commit message

### Integration Test Exceptions
- Can skip suite if no credentials (using `this.skip()` in before hook)
- Must document in test output
- Should provide instructions for enabling
