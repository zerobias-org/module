## Chapter 7: Phase 4 - Core Implementation

### 7.1 Phase Overview

**Goal**: Implement Client, Producers, and Mappers

**Duration**: 60-90 minutes

**Deliverables**:
- `{Service}Client.ts` - Connection management
- `{Resource}Producer.ts` - Business operations
- `Mappers.ts` - Data transformation
- `util.ts` - Helper functions
- **Gate 3: Implementation** ✅

### 7.2 Separation of Concerns

**Critical Architecture Rule:**

```
Client    = Connection ONLY (connect, isConnected, disconnect)
Producers = Operations ONLY (list, get, create, update, delete)
Mappers   = Transform ONLY (API response → typed objects)
```

**NEVER:**
- ❌ Client implements business operations
- ❌ Producers duplicate connection context (apiKey, token, baseUrl parameters)
- ❌ Mappers access environment variables

### 7.3 Implement Client (Connection Management)

#### 7.3.1 Create {Service}Client.ts

**Example: GitHubClient.ts**

```typescript
import axios, { AxiosInstance } from 'axios';
import { Email, DateTime } from '@auditmation/types-core-js';
import { handleAxiosError } from './util';
import {
  ConnectionProfile,
  ConnectionState
} from '../generated/api';

export class GitHubClient {
  private httpClient: AxiosInstance;
  private connectionProfile?: ConnectionProfile;
  private connectionState?: ConnectionState;

  constructor() {
    this.httpClient = axios.create({
      baseURL: 'https://api.github.com',
      timeout: 30000,
      headers: {
        'Accept': 'application/vnd.github+json',
        'X-GitHub-Api-Version': '2022-11-28'
      }
    });

    // Add response interceptor for error handling
    this.httpClient.interceptors.response.use(
      response => response,
      error => handleAxiosError(error)
    );

    // Add request interceptor for authentication
    this.httpClient.interceptors.request.use(config => {
      if (this.connectionState?.accessToken) {
        config.headers['Authorization'] = `Bearer ${this.connectionState.accessToken}`;
      }
      return config;
    });
  }

  /**
   * Connect to GitHub API
   * Authenticates and stores connection state
   */
  async connect(profile: ConnectionProfile): Promise<ConnectionState> {
    this.connectionProfile = profile;

    // For GitHub, we use personal access token (already have token)
    // Some APIs require a login call here

    const response = await this.httpClient.get('/user', {
      headers: {
        'Authorization': `Bearer ${profile.token}`
      }
    });

    // GitHub tokens don't expire, but we set a long expiry for framework
    const state: ConnectionState = {
      accessToken: profile.token,
      expiresIn: 31536000 // 1 year in seconds
    };

    this.connectionState = state;
    return state;
  }

  /**
   * Check if connected to GitHub API
   * Makes a real API call to verify token validity
   */
  async isConnected(): Promise<boolean> {
    if (!this.connectionState?.accessToken) {
      return false;
    }

    try {
      await this.httpClient.get('/user');
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Disconnect from GitHub API
   * Clears connection state
   */
  async disconnect(): Promise<void> {
    this.connectionState = undefined;
    this.connectionProfile = undefined;
  }

  /**
   * Get HTTP client instance for producers
   */
  get apiClient(): AxiosInstance {
    return this.httpClient;
  }
}
```

**Key Points:**
- ✅ **Only 3 methods**: connect, isConnected, disconnect
- ✅ **connect()** returns ConnectionState
- ✅ **isConnected()** makes real API call
- ✅ **apiClient** getter provides HTTP client to producers
- ✅ Request interceptor adds authentication
- ✅ Response interceptor handles errors

#### 7.3.2 Alternative: OAuth Email/Password Authentication

**Example: Service requiring login**

```typescript
async connect(profile: ConnectionProfile): Promise<ConnectionState> {
  this.connectionProfile = profile;

  // POST to /auth/login
  const response = await this.httpClient.post('/auth/login', {
    email: profile.email,
    password: profile.password
  });

  // Calculate expiresIn from expiresAt if API returns timestamp
  const expiresAtTimestamp = new Date(response.data.expires_at).getTime();
  const nowTimestamp = Date.now();
  const expiresIn = Math.floor((expiresAtTimestamp - nowTimestamp) / 1000);

  const state: ConnectionState = {
    accessToken: response.data.access_token,
    refreshToken: response.data.refresh_token,
    expiresIn: expiresIn // MANDATORY - in SECONDS
  };

  this.connectionState = state;
  return state;
}
```

**Critical Rules:**
- ✅ Store ALL refresh-relevant data in ConnectionState
- ✅ expiresIn MUST be in SECONDS (not milliseconds or timestamp)
- ✅ Convert expiresAt to expiresIn: `Math.floor((expiresAt - now) / 1000)`
- ✅ Store refreshToken if API provides refresh capability

### 7.4 Implement util.ts (Helper Functions)

#### 7.4.1 Create util.ts

```typescript
import {
  InvalidCredentialsError,
  UnauthorizedError,
  NoSuchObjectError,
  InvalidInputError,
  RateLimitExceededError,
  UnexpectedError,
  InvalidStateError
} from '@auditmation/types-core-js';

/**
 * Handle axios errors and convert to core error types
 */
export function handleAxiosError(error: any): never {
  const status = error.response?.status || error.status || 500;
  const data = error.response?.data || {};
  const message = data.message || data.error || error.message || 'Unknown error';

  switch (status) {
    case 401:
      throw new InvalidCredentialsError();

    case 403:
      if (message.toLowerCase().includes('rate') ||
          message.toLowerCase().includes('limit')) {
        throw new RateLimitExceededError();
      }
      throw new UnauthorizedError();

    case 404:
      throw new NoSuchObjectError('resource', 'unknown');

    case 400:
    case 422:
      const field = data.field || data.parameter || 'request';
      const value = data.value || message;
      throw new InvalidInputError(field, value);

    case 429:
      throw new RateLimitExceededError();

    default:
      if (status >= 500) {
        throw new UnexpectedError(`Server error: ${message}`, status);
      }
      throw new UnexpectedError(`API error: ${message}`, status);
  }
}

/**
 * Validate required properties exist in raw API data
 * Throws InvalidStateError if any property is null or undefined
 */
export function ensureProperties<K extends string>(
  raw: unknown,
  properties: readonly K[]
): asserts raw is Record<K, NonNullable<unknown>> {
  if (raw === null || raw === undefined) {
    throw new InvalidStateError(`Object is ${raw}`);
  }

  const obj = raw as Record<string, unknown>;

  for (const prop of properties) {
    const value = obj[prop];
    if (value === null || value === undefined) {
      throw new InvalidStateError(`Missing required field: ${prop}`);
    }
  }
}

/**
 * Normalize null to undefined while preserving all other falsy values
 * Preserves: 0, "", false, []
 * Converts: null → undefined
 */
export function optional<T>(value: T | null | undefined): T | undefined {
  return value ?? undefined;
}

/**
 * Apply a mapper function to a value, handling null/undefined at boundary
 * Works like map() but for custom mapper functions
 */
export function mapWith<T>(
  mapper: (raw: any) => T,
  value: any
): T | undefined {
  if (value === null || value === undefined) {
    return undefined;
  }
  return mapper(value);
}

/**
 * Convert string value to enum with optional transformation
 * Default transformation: converts to snake_case
 */
export function toEnum<T extends Record<string, any>>(
  enumType: T,
  value: any,
  transform: (v: string) => string = toSnakeCase
): T[keyof T] | undefined {
  if (value === null || value === undefined) {
    return undefined;
  }

  const transformed = transform(String(value));
  const enumValue = Object.values(enumType).find(
    v => String(v) === transformed
  );

  return enumValue as T[keyof T] | undefined;
}

/**
 * Convert string to snake_case
 */
function toSnakeCase(str: string): string {
  return str
    .replace(/([A-Z])/g, '_$1')
    .toLowerCase()
    .replace(/^_/, '');
}
```

### 7.5 Implement Mappers

#### 7.5.1 Create Mappers.ts

```typescript
import { map } from '@auditmation/util-hub-module-utils';
import { UUID, Email, DateTime } from '@auditmation/types-core-js';
import { User } from '../generated/api';
import { ensureProperties, optional } from './util';

/**
 * Convert raw API user data to User type
 */
export function toUser(raw: any): User {
  // 1. Validate required fields
  ensureProperties(raw, ['id', 'email', 'first_name', 'created_at']);

  // 2. Create typed output
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

**Mapper Pattern Breakdown:**

1. **Validate Required Fields**
   ```typescript
   ensureProperties(raw, ['id', 'email', 'first_name', 'created_at']);
   ```
   - Checks all required fields exist
   - Throws InvalidStateError if missing
   - TypeScript knows fields exist after this

2. **Create Typed Output with const**
   ```typescript
   const output: User = { ... };
   ```
   - Declare with explicit type
   - All fields visible in one place
   - TypeScript catches missing fields

3. **Field Mappings**
   ```typescript
   id: raw.id.toString()                    // ID conversion
   email: map(Email, raw.email)             // Type conversion with map()
   firstName: raw.first_name                // snake_case → camelCase
   lastName: optional(raw.last_name)        // Optional field - null→undefined
   createdAt: map(DateTime, raw.created_at) // DateTime conversion
   ```

**Field Mapping Rules:**

| Type | Pattern | Example |
|------|---------|---------|
| ID (number) | `raw.id.toString()` | `id: raw.id.toString()` |
| String (required) | Direct mapping | `firstName: raw.first_name` |
| String (optional) | `optional()` | `lastName: optional(raw.last_name)` |
| Email | `map(Email, ...)` | `email: map(Email, raw.email)` |
| UUID | `map(UUID, ...)` | `userId: map(UUID, raw.user_id)` |
| DateTime | `map(DateTime, ...)` | `createdAt: map(DateTime, raw.created_at)` |
| URL | `map(URL, ...)` | `website: map(URL, raw.website)` |
| Date | `map(Date, ...)` | `birthDate: map(Date, raw.birth_date)` |
| Enum | `toEnum(EnumType, ...)` | `status: toEnum(UserStatus, raw.status)` |
| Nested Object | `mapWith(toNested, ...)` | `address: mapWith(toAddress, raw.address)` |
| Array | `raw.items?.map(toItem)` | `items: raw.items?.map(toItem)` |

#### 7.5.2 Helper Mappers (Nested Objects)

```typescript
// Helper - NOT exported, declared BEFORE main mapper
function toAddress(raw: any): Address {
  ensureProperties(raw, ['street']);

  const output: Address = {
    street: raw.street,
    city: optional(raw.city),
    zipCode: optional(raw.zip_code)
  };

  return output;
}

// Main mapper uses helper with mapWith
export function toUser(raw: any): User {
  ensureProperties(raw, ['id']);

  const output: User = {
    id: raw.id.toString(),
    address: mapWith(toAddress, raw.address) // mapWith handles null/undefined
  };

  return output;
}
```

#### 7.5.3 Critical Mapper Rules

**1. ZERO MISSING FIELDS**
- Interface has N fields → Mapper must map N fields
- Count MUST match exactly

**2. Use const output Pattern**
```typescript
const output: User = { ... };
return output;
```

**3. Validate Required Fields**
```typescript
ensureProperties(raw, ['id', 'email']);
```

**4. Prefer map() over constructors**
```typescript
// ✅ CORRECT
email: map(Email, raw.email)

// ⚠️ AVOID (use only if map() doesn't work)
email: new Email(raw.email)
```

**5. Use optional() for optional fields**
```typescript
lastName: optional(raw.last_name) // null→undefined, keeps "", 0, false
```

**6. NEVER use logical OR**
```typescript
// ❌ WRONG - destroys legitimate values
name: raw.name || undefined // Converts "" to undefined

// ✅ CORRECT
name: optional(raw.name) // Keeps ""
```

**7. NO fallbacks between different fields**
```typescript
// ❌ WRONG
phoneNumber: raw.mobilePhone || raw.phoneNumber || undefined

// ✅ CORRECT - map each field
mobilePhone: optional(raw.mobilePhone)
phoneNumber: optional(raw.phoneNumber)
```

**8. Enum conversion**
```typescript
status: toEnum(UserStatus, raw.status) // Default: snake_case
```

### 7.6 Implement Producers

#### 7.6.1 Create {Resource}Producer.ts

**Example: UserProducer.ts**

```typescript
import { PagedResults } from '@auditmation/types-core-js';
import { User } from '../generated/api';
import { GitHubClient } from './GitHubClient';
import { toUser } from './Mappers';
import { handleAxiosError } from './util';

export class UserProducer {
  constructor(private client: GitHubClient) {}

  /**
   * List users with pagination
   */
  async listUsers(
    results: PagedResults<User>,
    name?: string
  ): Promise<void> {
    const params: Record<string, string | number> = {};

    // Pagination: offset/limit from pageNumber/pageSize
    if (results.pageNumber && results.pageSize) {
      params.offset = (results.pageNumber - 1) * results.pageSize;
      params.limit = Math.min(Math.max(results.pageSize, 1), 1000);
    } else {
      params.offset = 0; // MANDATORY: Always initialize offset
    }

    // Filter parameter
    if (name) {
      params.name = name;
    }

    const { data } = await this.client.apiClient
      .request({
        url: '/users',
        method: 'get',
        params
      })
      .catch(handleAxiosError);

    // Validate response structure before mapping
    if (!data || !Array.isArray(data.data)) {
      throw new UnexpectedError('Invalid response format: expected data array');
    }

    // Manual assignment (NOT ingest)
    results.items = data.data.map(toUser);
    results.count = data.totalCount || 0;
  }

  /**
   * Get user by ID
   */
  async getUser(userId: string): Promise<User> {
    const { data } = await this.client.apiClient
      .request({
        url: `/users/${userId}`,
        method: 'get'
      })
      .catch(handleAxiosError);

    return toUser(data);
  }

  /**
   * Create new user
   */
  async createUser(input: UserCreateInput): Promise<User> {
    const { data } = await this.client.apiClient
      .request({
        url: '/users',
        method: 'post',
        data: input
      })
      .catch(handleAxiosError);

    return toUser(data);
  }

  /**
   * Delete user
   */
  async deleteUser(userId: string): Promise<void> {
    await this.client.apiClient
      .request({
        url: `/users/${userId}`,
        method: 'delete'
      })
      .catch(handleAxiosError);
  }
}
```

**Producer Pattern Breakdown:**

1. **Constructor receives Client**
   ```typescript
   constructor(private client: GitHubClient) {}
   ```
   - Client provides HTTP client instance
   - No connection context parameters!

2. **PagedResults Operations**
   ```typescript
   async listUsers(
     results: PagedResults<User>,
     name?: string
   ): Promise<void> {
     // Convert pageNumber/pageSize → API pagination
     // Make request
     // Apply mapper
     // Manual assignment: results.items = ...
     // Update count: results.count = ...
   }
   ```

3. **Single Resource Operations**
   ```typescript
   async getUser(userId: string): Promise<User> {
     const { data } = await this.client.apiClient.request(...);
     return toUser(data);
   }
   ```

**Critical Producer Rules:**

**1. NO Connection Context Parameters**
```typescript
// ❌ WRONG
async getUser(
  userId: string,
  apiKey: string,      // NO!
  baseUrl: string,     // NO!
  orgId: string        // NO!
): Promise<User>

// ✅ CORRECT
async getUser(userId: string): Promise<User>
```

**2. ALWAYS use handleAxiosError**
```typescript
.catch(handleAxiosError)
```

**3. PagedResults Manual Assignment**
```typescript
// ✅ CORRECT - Validate then assign
if (!data || !Array.isArray(data.data)) {
  throw new UnexpectedError('Invalid response format');
}
results.items = data.data.map(toUser);
results.count = data.totalCount || 0;

// ❌ WRONG
results.ingest(data.map(toUser)); // Unreliable
results.items = data?.data?.map(toUser) || []; // Silent failure
```

**4. NEVER use Promise<any>**
```typescript
// ❌ WRONG
async getUser(userId: string): Promise<any>

// ✅ CORRECT
async getUser(userId: string): Promise<User>
```

**5. Update PagedResults count**
```typescript
results.count = data.totalCount || 0;
```

### 7.7 Implement ConnectorImpl

#### 7.7.1 Update {Service}Impl.ts

```typescript
import {
  ConnectionStatus,
  OperationSupportStatus,
  AccessApi
} from '@auditmation/types-core-js';
import { GitHubClient } from './GitHubClient';
import { UserProducer } from './UserProducer';
import type {
  ConnectionProfile,
  ConnectionState
} from '../generated/api';

export class GitHubConnector {
  private client: GitHubClient;
  private userProducer: UserProducer;

  constructor() {
    this.client = new GitHubClient();
    this.userProducer = new UserProducer(this.client);
  }

  /**
   * Connect to GitHub API
   */
  async connect(profile: ConnectionProfile): Promise<ConnectionState> {
    return await this.client.connect(profile);
  }

  /**
   * Check if connected to GitHub API
   */
  async isConnected(): Promise<boolean> {
    return await this.client.isConnected();
  }

  /**
   * Disconnect from GitHub API
   */
  async disconnect(): Promise<void> {
    return await this.client.disconnect();
  }

  /**
   * Get user producer API
   */
  getUserApi(): UserProducer {
    return this.userProducer;
  }

  /**
   * Connection status metadata
   */
  get metadata() {
    return {
      connectionStatus: ConnectionStatus.Down,
      isSupported: (operation: string) => {
        // For now, return Maybe for all operations
        return OperationSupportStatus.Maybe;
      }
    };
  }
}
```

#### 7.7.2 Update src/index.ts

```typescript
export { GitHubConnector } from './GitHubImpl';
export * from '../generated/api';

/**
 * Create new GitHub service instance
 */
export function newService(): GitHubConnector {
  return new GitHubConnector();
}
```

### 7.8 Validate Implementation (Gate 3)

#### 7.8.1 Run Build

```bash
# The build will catch most issues
npm run build

# If build succeeds, your implementation is good! ✓
```

**Gate 3 Pass Criteria:**
- ✅ `npm run build` exits with code 0 (no errors)
- ✅ Code follows the patterns from templates

**Note**: If the build succeeds, TypeScript has validated your types and imports are correct!

### 7.9 Phase 4 Validation Checklist

**Before proceeding to Phase 5:**

- [ ] ✅ Client implemented (connect, isConnected, disconnect)
- [ ] ✅ Client provides apiClient getter
- [ ] ✅ util.ts created with helper functions
- [ ] ✅ handleAxiosError implemented
- [ ] ✅ ensureProperties implemented
- [ ] ✅ optional() implemented
- [ ] ✅ mapWith() implemented
- [ ] ✅ toEnum() implemented
- [ ] ✅ Mappers.ts created
- [ ] ✅ All mappers follow `to<Model>` naming
- [ ] ✅ Mappers use const output pattern
- [ ] ✅ Mappers validate required fields
- [ ] ✅ Mappers handle ALL interface fields
- [ ] ✅ Producers implemented
- [ ] ✅ Producers receive only business parameters
- [ ] ✅ Producers use handleAxiosError
- [ ] ✅ ConnectorImpl updated
- [ ] ✅ index.ts exports updated
- [ ] ✅ **Gate 3: Implementation PASSED** ✅

---

