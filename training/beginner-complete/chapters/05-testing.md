## Chapter 8: Phase 5 - Testing

### 8.1 Phase Overview

**Goal**: Write comprehensive unit and integration tests

**Duration**: 60-90 minutes

**Deliverables**:
- Unit tests (mocked HTTP, no credentials)
- Integration tests (real API, credentials required)
- **Gate 4: Unit Test Creation & Execution** ✅
- **Gate 5: Integration Tests** ✅

**Testing Philosophy:**
- **Unit Tests**: Fast, offline, mocked HTTP with nock
- **Integration Tests**: Real API calls, verify actual behavior

### 8.2 Unit Tests (Mocked HTTP)

#### 8.2.1 Update test/unit/Common.ts

```typescript
import * as nock from 'nock';
import { Email } from '@auditmation/types-core-js';
import { newService } from '../../src';
import type { GitHubConnector } from '../../src';

/**
 * Get a connected instance for unit testing
 * Uses mocked HTTP - no real credentials needed
 * Unit tests should NEVER depend on environment variables
 */
export async function getConnectedInstance(): Promise<GitHubConnector> {
  nock('https://api.github.com')
    .get('/user')
    .reply(200, {
      id: '12345',
      login: 'testuser',
      email: 'test@example.com'
    });

  const connector = newService();

  await connector.connect({
    token: 'test-token-123'
  });

  return connector;
}
```

**Critical Rules for Unit Test Common.ts:**
- ✅ Uses nock to mock HTTP
- ✅ NO environment variables
- ✅ NO dotenv
- ✅ NO real credentials
- ✅ Works without .env file

#### 8.2.2 Create test/unit/ConnectionTest.ts

```typescript
import { expect } from 'chai';
import * as nock from 'nock';
import { InvalidCredentialsError } from '@auditmation/types-core-js';
import { newService } from '../../src';

describe('Connection', () => {
  describe('connect', () => {
    it('should successfully authenticate and store token', async () => {
      nock('https://api.github.com')
        .get('/user')
        .reply(200, {
          id: '12345',
          login: 'testuser'
        });

      const connector = newService();
      const connectionState = await connector.connect({
        token: 'test-token'
      });

      expect(connectionState).to.have.property('accessToken');
      expect(connectionState.accessToken).to.equal('test-token');
    });

    it('should handle authentication failure', async () => {
      nock('https://api.github.com')
        .get('/user')
        .reply(401, { message: 'Bad credentials' });

      const connector = newService();

      try {
        await connector.connect({
          token: 'invalid-token'
        });
        expect.fail('Should have thrown an error');
      } catch (error: any) {
        expect(error).to.be.instanceOf(InvalidCredentialsError);
      }
    });
  });

  describe('isConnected', () => {
    it('should return true when connected', async () => {
      nock('https://api.github.com')
        .get('/user')
        .twice()
        .reply(200, { id: '12345' });

      const connector = newService();
      await connector.connect({ token: 'test-token' });

      const isConnected = await connector.isConnected();
      expect(isConnected).to.be.true;
    });

    it('should return false when not connected', async () => {
      const connector = newService();
      const isConnected = await connector.isConnected();
      expect(isConnected).to.be.false;
    });
  });

  describe('disconnect', () => {
    it('should clear connection state', async () => {
      nock('https://api.github.com')
        .get('/user')
        .reply(200, { id: '12345' });

      const connector = newService();
      await connector.connect({ token: 'test-token' });

      await connector.disconnect();
      const isConnected = await connector.isConnected();
      expect(isConnected).to.be.false;
    });
  });
});
```

#### 8.2.3 Create test/unit/UserProducerTest.ts

```typescript
import { expect } from 'chai';
import * as nock from 'nock';
import { PagedResults, UUID, Email } from '@auditmation/types-core-js';
import { User } from '../../generated/api';
import { getConnectedInstance } from './Common';

describe('UserProducer', () => {
  describe('listUsers', () => {
    it('should retrieve paginated list of users', async () => {
      nock('https://api.github.com')
        .get('/users')
        .query({ offset: 0, limit: 10 })
        .reply(200, {
          data: [
            {
              id: '1',
              email: 'user1@example.com',
              first_name: 'John',
              last_name: 'Doe',
              created_at: '2025-01-01T00:00:00Z'
            },
            {
              id: '2',
              email: 'user2@example.com',
              first_name: 'Jane',
              last_name: 'Smith',
              created_at: '2025-01-02T00:00:00Z'
            }
          ],
          total: 100
        });

      const connector = await getConnectedInstance();
      const userApi = connector.getUserApi();

      const results = new PagedResults<User>();
      results.pageNumber = 1;
      results.pageSize = 10;

      await userApi.listUsers(results);

      expect(results.items).to.be.an('array');
      expect(results.items).to.have.length(2);
      expect(results.count).to.equal(100);

      const firstUser = results.items[0];
      expect(firstUser.id).to.equal('1');
      expect(firstUser.email).to.be.instanceof(Email);
      expect(firstUser.firstName).to.equal('John');
    });

    it('should filter users by name', async () => {
      nock('https://api.github.com')
        .get('/users')
        .query({ offset: 0, limit: 10, name: 'John' })
        .reply(200, {
          data: [
            {
              id: '1',
              email: 'john@example.com',
              first_name: 'John',
              created_at: '2025-01-01T00:00:00Z'
            }
          ],
          total: 1
        });

      const connector = await getConnectedInstance();
      const userApi = connector.getUserApi();

      const results = new PagedResults<User>();
      results.pageNumber = 1;
      results.pageSize = 10;

      await userApi.listUsers(results, 'John');

      expect(results.items).to.have.length(1);
      expect(results.items[0].firstName).to.equal('John');
    });
  });

  describe('getUser', () => {
    it('should retrieve user by ID', async () => {
      nock('https://api.github.com')
        .get('/users/123')
        .reply(200, {
          id: '123',
          email: 'user@example.com',
          first_name: 'John',
          last_name: 'Doe',
          created_at: '2025-01-01T00:00:00Z'
        });

      const connector = await getConnectedInstance();
      const userApi = connector.getUserApi();

      const user = await userApi.getUser('123');

      expect(user).to.not.be.null;
      expect(user.id).to.equal('123');
      expect(user.email).to.be.instanceof(Email);
      expect(user.firstName).to.equal('John');
    });

    it('should handle user not found', async () => {
      nock('https://api.github.com')
        .get('/users/999')
        .reply(404, { message: 'Not found' });

      const connector = await getConnectedInstance();
      const userApi = connector.getUserApi();

      try {
        await userApi.getUser('999');
        expect.fail('Should have thrown an error');
      } catch (error: any) {
        expect(error).to.exist;
      }
    });
  });
});
```

#### 8.2.4 Unit Test Validation

```bash
# Run unit tests
npm run test:unit

# Expected output:
# Connection
#   connect
#     ✓ should successfully authenticate and store token
#     ✓ should handle authentication failure
#   isConnected
#     ✓ should return true when connected
#     ✓ should return false when not connected
#   disconnect
#     ✓ should clear connection state
#
# UserProducer
#   listUsers
#     ✓ should retrieve paginated list of users
#     ✓ should filter users by name
#   getUser
#     ✓ should retrieve user by ID
#     ✓ should handle user not found
#
# 9 passing (50ms)
```

**Gate 4 Pass Criteria:**
- ✅ Unit tests exist for Connection
- ✅ Unit tests exist for all Producers
- ✅ 3+ test cases per operation
- ✅ All tests use nock for HTTP mocking
- ✅ No forbidden mocking libraries (jest, sinon, fetch-mock)
- ✅ All unit tests passing (100% pass rate)

### 8.3 Integration Tests (Real API)

#### 8.3.1 Update test/integration/Common.ts

```typescript
import { config } from 'dotenv';
import { getLogger as getLoggerBase } from '@auditmation/util-logger';
import { newService } from '../../src';
import type { GitHubConnector } from '../../src';

// Load .env file explicitly
config();

// Export credentials
export const GITHUB_TOKEN = process.env.GITHUB_TOKEN || '';

// Export test data
export const GITHUB_TEST_USER_ID = process.env.GITHUB_TEST_USER_ID || '';

const LOG_LEVEL = (process.env.LOG_LEVEL || 'info') as string;

/**
 * Get a logger with configurable level from LOG_LEVEL env var
 * Usage: LOG_LEVEL=debug npm run test:integration
 */
export function getLogger(name: string) {
  return getLoggerBase(name, {}, LOG_LEVEL);
}

export function hasCredentials(): boolean {
  return !!GITHUB_TOKEN;
}

// Cached connector instance - connect once, reuse many times
let cachedConnector: GitHubConnector | null = null;

/**
 * Get a connected instance for integration testing
 * Connects once on first call, returns cached instance on subsequent calls
 * Uses real credentials from .env file
 * Makes real API calls
 */
export async function getConnectedInstance(): Promise<GitHubConnector> {
  if (cachedConnector) {
    return cachedConnector;
  }

  const connector = newService();

  await connector.connect({
    token: GITHUB_TOKEN
  });

  cachedConnector = connector;
  return connector;
}
```

**Critical Rules for Integration Test Common.ts:**
- ✅ Loads dotenv explicitly
- ✅ Exports credential constants
- ✅ Exports getLogger() helper
- ✅ Has hasCredentials() check
- ✅ Connection caching for performance
- ✅ Makes real API calls

#### 8.3.2 Create test/integration/ConnectionTest.ts

```typescript
import { expect } from 'chai';
import { newService } from '../../src';
import { GITHUB_TOKEN, hasCredentials, getLogger } from './Common';

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

      logger.debug('connector.connect(token)');
      const connectionState = await connector.connect({
        token: GITHUB_TOKEN
      });
      logger.debug('→', JSON.stringify(connectionState, null, 2));

      expect(connectionState).to.have.property('accessToken');
    });
  });

  describe('isConnected', () => {
    it('should return true when connected', async () => {
      const connector = newService();

      await connector.connect({
        token: GITHUB_TOKEN
      });

      logger.debug('connector.isConnected()');
      const isConnected = await connector.isConnected();
      logger.debug('→', JSON.stringify({ isConnected }, null, 2));

      expect(isConnected).to.be.true;
    });
  });

  describe('disconnect', () => {
    it('should successfully disconnect from API', async () => {
      const connector = newService();

      await connector.connect({
        token: GITHUB_TOKEN
      });

      logger.debug('connector.disconnect()');
      await connector.disconnect();
      logger.debug('→ disconnect completed');
    });
  });
});
```

#### 8.3.3 Create test/integration/UserProducerTest.ts

```typescript
import { expect } from 'chai';
import { PagedResults, Email } from '@auditmation/types-core-js';
import { User } from '../../generated/api';
import {
  getConnectedInstance,
  hasCredentials,
  getLogger,
  GITHUB_TEST_USER_ID
} from './Common';

const logger = getLogger('UserProducerTest');

describe('UserProducer Integration Tests', function () {
  this.timeout(30000);

  before(function () {
    if (!hasCredentials()) {
      this.skip();
    }
  });

  describe('listUsers', () => {
    it('should retrieve real users from API', async () => {
      logger.debug('getConnectedInstance()');
      const connector = await getConnectedInstance();
      const userApi = connector.getUserApi();

      const results = new PagedResults<User>();
      results.pageNumber = 1;
      results.pageSize = 10;

      logger.debug('userApi.listUsers(results, pageSize=10)');
      await userApi.listUsers(results);
      logger.debug('→', JSON.stringify(results, null, 2));

      expect(results.items).to.be.an('array');
      expect(results.items.length).to.be.greaterThan(0);

      const firstUser = results.items[0];
      expect(firstUser.id).to.be.a('string');
      expect(firstUser.email).to.be.instanceof(Email);
    });
  });

  describe('getUser', () => {
    it('should retrieve user by ID from real API', async () => {
      const connector = await getConnectedInstance();
      const userApi = connector.getUserApi();

      const userId = GITHUB_TEST_USER_ID;

      logger.debug(`userApi.getUser(${userId})`);
      const user = await userApi.getUser(userId);
      logger.debug('→', JSON.stringify(user, null, 2));

      expect(user).to.not.be.null;
      expect(user.id).to.equal(userId);
      expect(user.email).to.be.instanceof(Email);
    });
  });
});
```

#### 8.3.4 Integration Test Validation

```bash
# Run integration tests
npm run test:integration

# Or with debug logging
LOG_LEVEL=debug npm run test:integration

# Expected output:
# Connection Integration Tests
#   connect
#     ✓ should successfully connect to real API (500ms)
#   isConnected
#     ✓ should return true when connected (300ms)
#   disconnect
#     ✓ should successfully disconnect from API (200ms)
#
# UserProducer Integration Tests
#   listUsers
#     ✓ should retrieve real users from API (1200ms)
#   getUser
#     ✓ should retrieve user by ID from real API (800ms)
#
# 5 passing (3s)
```

**Gate 5 Pass Criteria:**
- ✅ Integration tests exist for Connection
- ✅ Integration tests exist for all Producers
- ✅ 3+ test cases per operation
- ✅ No mocking in integration tests
- ✅ No hardcoded test values (all from .env)
- ✅ All integration tests passing (100% pass rate)
- ✅ Debug logging present

### 8.4 Phase 5 Validation Checklist

**Before proceeding to Phase 6:**

- [ ] ✅ test/unit/Common.ts created (NO env vars, uses nock)
- [ ] ✅ test/unit/ConnectionTest.ts created
- [ ] ✅ test/unit/{Resource}ProducerTest.ts created
- [ ] ✅ All unit tests use nock ONLY
- [ ] ✅ No forbidden mocking libraries
- [ ] ✅ **Gate 4: Unit Test Creation & Execution PASSED** ✅
- [ ] ✅ test/integration/Common.ts created (loads dotenv, has getLogger)
- [ ] ✅ test/integration/ConnectionTest.ts created
- [ ] ✅ test/integration/{Resource}ProducerTest.ts created
- [ ] ✅ All integration tests have debug logging
- [ ] ✅ Test data from .env (not hardcoded)
- [ ] ✅ .env file has credentials and test values
- [ ] ✅ **Gate 5: Integration Tests PASSED** ✅

---

*[Training manual continues with Chapters 9-17 covering Documentation, Build, Quality Gates Validation, Adding Operations, Troubleshooting, Best Practices, Quick Reference, Validation Checklists, and Code Templates]*

---

**END OF EXCERPT**

*Note: This is Part 1 of the training manual (Chapters 1-8). The complete manual would continue with the remaining chapters covering documentation, build processes, advanced topics, and reference materials. Each chapter follows the same detailed, step-by-step approach with code examples, validation scripts, and hands-on exercises.*

---

## About This Training Manual

**Total Estimated Pages**: 350-400 pages
**Format**: Markdown for easy viewing and printing
**Updates**: Living document, updated as processes evolve

**For Questions or Updates**: Contact the module development team

