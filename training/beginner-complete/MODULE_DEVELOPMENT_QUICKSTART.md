# Module Development Quick Start Guide

**For**: Developers who completed the full training
**Purpose**: Quick reference for creating modules efficiently

---

## Prerequisites Check (2 minutes)

```bash
# Run this first
node --version  # >= 16
npm --version   # >= 8
yo --version    # >= 4
java -version   # >= 11
```

---

## Phase 1: Research (10-15 min)

### Step 1: Find API Docs
- Locate base URL
- Identify auth method
- Find first operation endpoint

### Step 2: Test with curl
```bash
# Test auth
curl -X POST "https://api.service.com/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"testpass"}'

# Save token
ACCESS_TOKEN="your-token"

# Test first operation
curl -X GET "https://api.service.com/users?limit=10" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  > response.json

# Review and sanitize
cat response.json | jq .
```

### Step 3: Document
- Auth method: [OAuth/Token/Basic]
- Endpoint: [URL]
- Request/Response: [Structure]

---

## Phase 2: Scaffold (5 min)

```bash
# From repo root
yo @auditmation/hub-module \
  --productPackage '@zerobias-org/product-vendor-service' \
  --modulePackage '@zerobias-org/module-vendor-service' \
  --packageVersion '0.0.0' \
  --description 'Service Name' \
  --repository 'https://github.com/zerobias-org/module' \
  --author 'your.email@company.com'

cd package/vendor/service

# CRITICAL!
npm run sync-meta

npm install

# Create .env
cat > .env << EOF
SERVICE_API_KEY=your-key
SERVICE_TEST_USER_ID=test-id
EOF
```

---

## Phase 3: API Spec (15-20 min)

### Design api.yml

```yaml
paths:
  /users:
    get:
      operationId: listUsers
      x-method-name: listUsers
      tags:
        - user
      parameters:
        - $ref: '#/components/parameters/limitParam'
      responses:
        '200':
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UserList'

components:
  schemas:
    User:
      required:
        - id
        - email
      properties:
        id:
          type: string
        email:
          type: string
          format: email
        firstName:
          type: string
        createdAt:
          type: string
          format: date-time
```

### Design connectionProfile.yml

```yaml
# For token auth
$ref: './node_modules/@auditmation/types-core/schema/tokenProfile.yml'

# For OAuth email/password
allOf:
  - $ref: './node_modules/@auditmation/types-core/schema/basicConnection.yml'
  - type: object
    properties:
      username:
        format: email
```

### Design connectionState.yml

```yaml
# Simple token
$ref: './node_modules/@auditmation/types-core/schema/tokenConnectionState.yml'

# OAuth with refresh
$ref: './node_modules/@auditmation/types-core/schema/oauthTokenState.yml'
```

### Validate & Generate

```bash
# Gate 1
grep -E "describe[A-Z]|'40[0-9]'|snake_case" api.yml  # Should return nothing
npx swagger-cli validate api.yml

# Gate 2
npm run clean && npm run generate
ls generated/api/
```

---

## Phase 4: Implementation (20-30 min)

### Create Client

```typescript
// src/ServiceClient.ts
export class ServiceClient {
  private httpClient: AxiosInstance;
  private connectionState?: ConnectionState;

  constructor() {
    this.httpClient = axios.create({
      baseURL: 'https://api.service.com',
      timeout: 30000
    });

    this.httpClient.interceptors.response.use(
      response => response,
      error => handleAxiosError(error)
    );

    this.httpClient.interceptors.request.use(config => {
      if (this.connectionState?.accessToken) {
        config.headers['Authorization'] = `Bearer ${this.connectionState.accessToken}`;
      }
      return config;
    });
  }

  async connect(profile: ConnectionProfile): Promise<ConnectionState> {
    const response = await this.httpClient.post('/auth/login', {
      email: profile.email,
      password: profile.password
    });

    const state: ConnectionState = {
      accessToken: response.data.access_token,
      expiresIn: response.data.expires_in
    };

    this.connectionState = state;
    return state;
  }

  async isConnected(): Promise<boolean> {
    if (!this.connectionState?.accessToken) return false;
    try {
      await this.httpClient.get('/status');
      return true;
    } catch {
      return false;
    }
  }

  async disconnect(): Promise<void> {
    this.connectionState = undefined;
  }

  get apiClient(): AxiosInstance {
    return this.httpClient;
  }
}
```

### Create util.ts

```typescript
// Copy template from Chapter 17.4
export function handleAxiosError(error: any): never { /* ... */ }
export function ensureProperties<K extends string>(...) { /* ... */ }
export function optional<T>(value: T | null | undefined): T | undefined { /* ... */ }
export function mapWith<T>(...) { /* ... */ }
export function toEnum<T>(...) { /* ... */ }
```

### Create Mappers.ts

```typescript
import { map } from '@auditmation/util-hub-module-utils';
import { UUID, Email, DateTime } from '@auditmation/types-core-js';
import { User } from '../generated/api';
import { ensureProperties, optional } from './util';

export function toUser(raw: any): User {
  ensureProperties(raw, ['id', 'email', 'firstName', 'createdAt']);

  const output: User = {
    id: raw.id.toString(),
    email: map(Email, raw.email),
    firstName: raw.first_name,
    lastName: optional(raw.last_name),
    createdAt: map(DateTime, raw.created_at)
  };

  return output;
}
```

### Create Producer

```typescript
// src/UserProducer.ts
export class UserProducer {
  constructor(private client: ServiceClient) {}

  async listUsers(results: PagedResults<User>): Promise<void> {
    const params: any = {};

    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = results.pageSize;
    }

    const { data } = await this.client.apiClient
      .request({ url: '/users', method: 'get', params })
      .catch(handleAxiosError);

    results.items = data.map(toUser) || [];
    if (data.total) results.count = data.total;
  }

  async getUser(userId: string): Promise<User> {
    const { data } = await this.client.apiClient
      .request({ url: `/users/${userId}`, method: 'get' })
      .catch(handleAxiosError);

    return toUser(data);
  }
}
```

### Validate Gate 3

```bash
npm run lint:src
grep "Promise<any>" src/*.ts  # Should return nothing
npm run build
```

---

## Phase 5: Testing (20-30 min)

### Unit Test Common.ts

```typescript
// test/unit/Common.ts
import * as nock from 'nock';
import { newService } from '../../src';

export async function getConnectedInstance() {
  nock('https://api.service.com')
    .post('/auth/login')
    .reply(200, { access_token: 'test-token', expires_in: 3600 });

  const connector = newService();
  await connector.connect({
    email: 'test@example.com',
    password: 'testpass'
  });

  return connector;
}
```

### Unit Tests

```typescript
// test/unit/UserProducerTest.ts
import { expect } from 'chai';
import * as nock from 'nock';
import { getConnectedInstance } from './Common';

describe('UserProducer', () => {
  describe('getUser', () => {
    it('should get user by ID', async () => {
      nock('https://api.service.com')
        .get('/users/123')
        .reply(200, {
          id: '123',
          email: 'user@example.com',
          first_name: 'John',
          created_at: '2025-01-01T00:00:00Z'
        });

      const connector = await getConnectedInstance();
      const userApi = connector.getUserApi();

      const user = await userApi.getUser('123');
      expect(user.id).to.equal('123');
    });
  });
});
```

### Integration Test Common.ts

```typescript
// test/integration/Common.ts
import { config } from 'dotenv';
import { getLogger as getLoggerBase } from '@auditmation/util-logger';
import { newService } from '../../src';

config();

export const SERVICE_EMAIL = process.env.SERVICE_EMAIL || '';
export const SERVICE_PASSWORD = process.env.SERVICE_PASSWORD || '';
export const SERVICE_TEST_USER_ID = process.env.SERVICE_TEST_USER_ID || '';

const LOG_LEVEL = (process.env.LOG_LEVEL || 'info') as string;

export function getLogger(name: string) {
  return getLoggerBase(name, {}, LOG_LEVEL);
}

export function hasCredentials(): boolean {
  return !!SERVICE_EMAIL && !!SERVICE_PASSWORD;
}

let cachedConnector = null;

export async function getConnectedInstance() {
  if (cachedConnector) return cachedConnector;

  const connector = newService();
  await connector.connect({
    email: SERVICE_EMAIL,
    password: SERVICE_PASSWORD
  });

  cachedConnector = connector;
  return connector;
}
```

### Integration Tests

```typescript
// test/integration/UserProducerTest.ts
import { getConnectedInstance, hasCredentials, getLogger, SERVICE_TEST_USER_ID } from './Common';

const logger = getLogger('UserProducerTest');

describe('UserProducer Integration', function () {
  this.timeout(30000);

  before(function () {
    if (!hasCredentials()) this.skip();
  });

  it('should get user from real API', async () => {
    const connector = await getConnectedInstance();
    const userApi = connector.getUserApi();

    logger.debug(`getUser(${SERVICE_TEST_USER_ID})`);
    const user = await userApi.getUser(SERVICE_TEST_USER_ID);
    logger.debug('→', JSON.stringify(user, null, 2));

    expect(user.id).to.equal(SERVICE_TEST_USER_ID);
  });
});
```

### Validate Gates 4 & 5

```bash
npm run test:unit
npm run test:integration
# or
LOG_LEVEL=debug npm run test:integration
```

---

## Phase 6: Documentation (5 min)

```markdown
# Service Name Module User Guide

## Obtaining Credentials

1. Log into https://service.com
2. Go to Settings > API
3. Generate API key
4. Copy key

## Connection Profile

| Credential | Field | Required |
|------------|-------|----------|
| API Key | token | Yes |

## Usage

```typescript
import { newService } from '@auditmation/connector-service';

const connector = newService();
await connector.connect({
  token: process.env.SERVICE_API_KEY
});
```
```

---

## Phase 7: Build (2 min)

```bash
npm run build
npm run shrinkwrap

ls dist/
ls npm-shrinkwrap.json
```

---

## Phase 8: Final Validation (5 min)

```bash
# Run all gates
./validate-all-gates.sh

# Or manual
grep -E "describe[A-Z]|'40[0-9]'" api.yml  # Nothing
npm run clean && npm run generate           # Exit 0
npm run lint:src                             # 0 errors
npm run test:unit                            # All pass
npm run test:integration                     # All pass
npm run build                                # Exit 0
npm run shrinkwrap                           # Exit 0
```

---

## Quick Validation

```bash
# One-liner to check all gates
npm run clean && \
npm run generate && \
npm run lint:src && \
npm run test:unit && \
npm run test:integration && \
npm run build && \
npm run shrinkwrap && \
echo "✅ ALL GATES PASSED!"
```

---

## Common Issues Quick Fix

| Issue | Quick Fix |
|-------|-----------|
| Build fails | `npm run clean && npm run generate` |
| Tests fail | Check .env file exists |
| InlineResponse types | Move schemas to components in api.yml |
| snake_case error | Change to camelCase in api.yml |
| Promise<any> error | Import and use generated types |
| Nock not matching | Check URL path matches exactly |

---

## 6 Gates Quick Check

```bash
# Gate 1
grep -E "describe[A-Z]" api.yml || echo "✅"

# Gate 2
npm run generate > /dev/null && echo "✅"

# Gate 3
npm run build > /dev/null && echo "✅"

# Gate 4
npm run test:unit > /dev/null && echo "✅"

# Gate 5
npm run test:integration > /dev/null && echo "✅"

# Gate 6
npm run shrinkwrap > /dev/null && echo "✅"
```

---

## Key Reminders

**Remember the 3 separations:**
- Client = Connection ONLY
- Producer = Operations ONLY
- Mapper = Transform ONLY

**Remember the 4 nevers:**
- NEVER use Promise<any>
- NEVER skip npm run sync-meta
- NEVER hardcode test data
- NEVER include error responses in api.yml

**Remember the 6 gates:**
All must pass - no exceptions!

---

## Speed Tips

1. Copy templates from Chapter 17
2. Use validation scripts early and often
3. Test with curl BEFORE implementing
4. Run gates incrementally, don't wait until end
5. Keep .env file ready from start
6. Use debug logging from day 1

---

**Ready to build modules fast!** 🚀
