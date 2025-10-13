# Implementation Rules

## 🚨 CRITICAL RULES (Immediate Failure)

### 1. Business Logic in Producers Only
- **Client**: ONLY connection management (connect, isConnected, disconnect)
- **Producers**: ALL API operations (list, get, create, update, delete)
- Client provides HTTP client instance to producers, nothing more

### 2. No Environment Variables in Module Source
- **FORBIDDEN**: `process.env.*` in `src/` directory
- **ONLY EXCEPTION**: `test/Common.ts` may read env vars
- **MANDATORY**: All params from method arguments or connection profile

```typescript
// ✅ ALLOWED: test/Common.ts
export const TEST_API_KEY = process.env.TEST_API_KEY || '';

// ❌ FORBIDDEN: Any src/ file
const apiKey = process.env.API_KEY; // NEVER
```

### 3. Core Error Usage
- **NEVER** use generic `Error` class
- **ALWAYS** use errors from `@auditmation/types-core-js`
- See [error-handling.md](./error-handling.md)

### 4. Type Generation Workflow
**MANDATORY SEQUENCE:**
```bash
# 1. Update API spec
vim api.yml

# 2. IMMEDIATELY generate types (MANDATORY)
npm run generate

# 3. THEN implement using generated types
vim src/SomeProducer.ts

# 4. Build and validate
npm run build
```

- **NEVER use `any`** in producer method signatures
- **ALWAYS use generated interfaces** from generated/api/

### 5. API Specification is Source of Truth
**🚨 CRITICAL: NEVER modify API spec to match mapper logic**

```typescript
// ❌ WRONG APPROACH: Weakening API spec because mapper has issues
// api.yml - removing 'id' from required because mapper can't handle it
Organization:
  type: object
  properties:    # ❌ NO! Don't remove required to fix mapper
    id:
      type: string

// ✅ CORRECT APPROACH: Mapper validates required fields from API spec
// api.yml - Keep spec accurate to API documentation
Organization:
  type: object
  required:
    - id       # ✅ YES! Spec reflects API reality
  properties:
    id:
      type: string

// Mapper validates required field
export function toOrganization(data: any): Organization | undefined {
  if (!data) return undefined;

  if (!data.id && data.id !== 0) {  // ✅ YES! Validate required fields
    throw new InvalidInputError('organization', 'Missing required field: id');
  }

  return {
    id: String(data.id),
    name: data.name || undefined,
  };
}
```

**RULE**: API spec drives implementation, NOT the other way around.
- API spec reflects API documentation (source of truth)
- Mappers validate and throw errors for missing required fields
- NEVER weaken spec (remove required, add nullable) to make mapper easier

**WHY**:
- API spec is contract with external API
- Type safety depends on accurate spec
- Validation belongs in mappers, not spec modification

### 6. Build Gate Compliance
- `npm run build` MUST pass before ANY implementation
- If build fails at ANY point, STOP immediately
- No implementation while build is broken

### 6. Never Use Python
- Only Bash and Node.js when needed
- Prefer curl for API testing

### 7. Never Create Module Root Files Manually
- **FORBIDDEN**: Manually creating module root files (package.json, tsconfig.json, api.yml, etc.)
- **MANDATORY**: Use Yeoman generator to create module scaffolding
- **EXCEPTION**: Documentation files (.md) may be created manually

```bash
# ✅ CORRECT - Use Yeoman generator
npm run module:create

# ❌ FORBIDDEN - Manual file creation
touch package.json  # NO!
touch api.yml       # NO!
touch tsconfig.json # NO!

# ✅ ALLOWED - Documentation files
touch USERGUIDE.md  # YES!
touch README.md     # YES!
```

**WHY**: Yeoman generator ensures consistent structure, proper dependencies, and correct configuration across all modules.

### 8. Parameter Design - Avoid Connection Context Duplication
- **FORBIDDEN**: Adding parameters that exist in ConnectionProfile or ConnectionState
- **MANDATORY**: Access connection context through client instance
- Operations receive ONLY operation-specific parameters

```typescript
// ❌ WRONG - Duplicating connection context
class UserProducer {
  async getUser(
    userId: string,
    apiKey: string,      // NO! In ConnectionProfile
    baseUrl: string,     // NO! In ConnectionProfile
    orgId: string        // NO! In ConnectionState
  ): Promise<User> {
    // ...
  }
}

// ✅ CORRECT - Only operation-specific parameters
class UserProducer {
  constructor(private client: GitHubClient) {}

  async getUser(userId: string): Promise<User> {
    // Client already has apiKey, baseUrl, orgId from profile/state
    const { data } = await this.client.apiClient
      .request({
        url: `/users/${userId}`,  // baseUrl handled by client
        method: 'get'
        // apiKey/auth handled by client interceptor
        // orgId available via this.client.getOrganizationId()
      })
      .catch(handleAxiosError);

    return toUser(data);
  }
}
```

**RULES**:
- ✅ Parameters in ConnectionProfile → Access via client
- ✅ Parameters in ConnectionState → Access via client
- ✅ Operation methods receive ONLY business parameters (IDs, filters, etc.)
- ❌ NEVER add apiKey, token, baseUrl, organizationId to method signatures
- ❌ NEVER duplicate what connect() already provides

**WHY**: Connection context is established once during connect() and managed by the client. Operations should focus on business logic, not connection details.

## 🟡 STANDARD RULES

### src/index.ts Pattern - MANDATORY Factory Function

**CRITICAL**: The `src/index.ts` file MUST export a factory function following this exact pattern:

```typescript
import { {Service}Connector } from '../generated/api';
import { {Service}ConnectorImpl } from './{Service}ConnectorImpl';

export * from '../generated/api';
export * from '../generated/model';

export function new{Service}(): {Service}Connector {
  return new {Service}ConnectorImpl();
}
```

**Examples**:
```typescript
// ✅ CORRECT - Avigilon Alta Access
export function newAvigilonAltaAccess(): AvigilonAltaAccessConnector {
  return new AvigilonAltaAccessConnectorImpl();
}

// ✅ CORRECT - ReadyPlayerMe
export function newReadyPlayerMe(): ReadyPlayerMeConnector {
  return new ReadyPlayerMeImpl();
}

// ❌ WRONG - Missing factory function
export * from '../generated/api';
export * from '../generated/model';
export { MyConnectorImpl } from './MyConnectorImpl';  // NO! Need factory function
```

**WHY**:
- Consistent instantiation pattern across all modules
- Consumers call `newServiceName()` instead of using `new`
- Hides implementation details from consumers
- Allows for future initialization logic in factory

**NAMING CONVENTION**:
- Function name: `new{Service}` in camelCase
- Return type: `{Service}Connector` interface from generated code
- NO "Impl" suffix in public API

### File Organization
```
src/
├── {Service}Client.ts          # HTTP client only
├── {Service}ConnectorImpl.ts   # Main connector
├── {Resource}Producer.ts       # One per resource
├── Mappers.ts                  # ALL mappers in single file
├── util.ts                     # Error handler
└── index.ts                    # Exports + factory function
```

### Client Class - Connection Management Only
```typescript
class ServiceClient {
  // ✅ Client responsibilities ONLY
  async connect(profile: ConnectionProfile): Promise<void> {
    // Setup authentication, HTTP client config
  }

  async isConnected(): Promise<boolean> {
    // Real API call to verify connection
  }

  async disconnect(): Promise<void> {
    // Cleanup
  }
}
```

**Client MUST NOT implement**: list, get, create, update, delete, or any API operations.

### Connect Method Return Types
- `Promise<ConnectionState>` - If state needs persistence (tokens, expiration)
- `Promise<void>` - If no state persistence needed

### Connector Implementation - CRITICAL RULES

**🚨 ConnectorImpl Class:**

```typescript
// ✅ CORRECT - Extends ONLY the generated interface
export class GitHubConnectorImpl extends GitHubConnector {
  // NO other base classes, NO AbstractConnector, JUST the generated interface
}
```

**NEVER GUESS METHOD SIGNATURES:**
1. **READ** the generated interface in `generated/api/index.ts`
2. **CHECK** what methods the generated connector interface defines
3. **IMPLEMENT** exactly those methods with exact signatures
4. **VERIFY** against the generated file

**Discovery Process for ConnectorImpl:**
1. Run `npm run generate` (must be done first)
2. Open `generated/api/index.ts`
3. Find the `{Service}Connector` interface
4. Read ALL methods defined in that interface
5. Implement EXACTLY those methods in `{Service}ConnectorImpl`
6. Check parameter types, return types from the generated interface

**🚨 CRITICAL: metadata() and isSupported() Methods**

**These methods MUST be implemented with EXACT boilerplate - NEVER customize them!**

```typescript
// ✅ CORRECT - EXACT boilerplate (always the same)
export class GitHubConnectorImpl extends GitHubConnector {

  async metadata(): Promise<ConnectionMetadata> {
    return { status: ConnectionStatus.Down } satisfies ConnectionMetadata;
  }

  async isSupported(_operationId: string) {
    return OperationSupportStatus.Maybe;
  }

  // Then implement actual API operation methods
  async getUser(userId: string): Promise<User> { ... }
  async listUsers(results: PagedResults<User>): Promise<void> { ... }
}

// ❌ WRONG - Customizing these methods
export class GitHubConnectorImpl extends GitHubConnector {
  async metadata(): Promise<ConnectionMetadata> {
    return {
      status: ConnectionStatus.Up,  // NO! Always Down
      version: '1.0.0',  // NO! Don't add fields
      capabilities: []  // NO! Don't add fields
    } satisfies ConnectionMetadata;
  }

  async isSupported(operationId: string) {
    if (operationId === 'getUser') return OperationSupportStatus.Yes;  // NO! Always Maybe
    return OperationSupportStatus.No;
  }
}
```

**WHY**: Hub manages actual connector metadata and operation support. These are placeholder implementations that the hub overrides at runtime.

**RULES**:
- ✅ metadata() is `async`, returns `Promise<ConnectionMetadata>`
- ✅ metadata() ALWAYS returns `{ status: ConnectionStatus.Down } satisfies ConnectionMetadata`
- ✅ isSupported() is `async`, parameter named `_operationId` (underscore - unused)
- ✅ isSupported() ALWAYS returns `OperationSupportStatus.Maybe`
- ❌ NEVER customize these methods
- ❌ NEVER add logic to determine status or support
- ❌ NEVER add additional properties to metadata

**NEVER**:
- ❌ Guess method signatures - READ from generated interface
- ❌ Extend classes other than the generated interface
- ❌ Customize `metadata()` method (always DOWN)
- ❌ Customize `isSupported()` method (always Maybe)
- ❌ Implement methods not in the generated interface

### Mapper Pattern - MANDATORY

**ALWAYS use `const output: Type` pattern:**

```typescript
// ✅ REQUIRED PATTERN
export function toUser(data: any): User {
  // Validate required fields first
  if (!data?.id || !data?.name) {
    throw new InvalidInputError('user', 'Missing required fields');
  }

  const output: User = {
    id: map(UUID, data.id),           // Required - validated above
    name: data.name,                   // Required - validated above
    email: map(Email, data.email),    // Optional - returns Email | undefined
    website: map(URL, data.website),  // Optional - returns URL | undefined
    createdAt: map(DateTime, data.created_at)?.toDate() // DateTime requires .toDate()
  };

  return output;
}
```

**CRITICAL**:
- **NEVER use `!`** (non-null assertion) - Design code to avoid it
- **PREFER `map()` over constructors** - Use `map()` from `@auditmation/util-hub-module-utils` unless map() doesn't meet requirements
- **Validate required fields** - Throw if missing, then TypeScript knows they exist
- **Let map() handle optionals** - It returns `Type | undefined` automatically
- **Always declare const with type** - Enables type checking
- **DateTime special case** - Use `map(DateTime, value)?.toDate()` since interface expects `Date` not `DateTime`
- **Constructor fallback** - Only use constructors directly if map() doesn't provide needed functionality
- **🚨 Null to undefined** - Convert null values to undefined (use `|| undefined` or `map()` which handles this)

```typescript
// ⚠️ AVOID - Using constructors without checking if map() works
export function toUser(data: any): User {
  const output: User = {
    id: new UUID(data.id),                    // ⚠️ Try map(UUID, ...) first
    email: new Email(data.email),             // ⚠️ Try map(Email, ...) first
    createdAt: new DateTime(data.created_at).toDate() // ⚠️ Try map(DateTime, ...)?.toDate() first
  };
  return output;
}
// Only use constructors if map() doesn't meet requirements
```

### Null to Undefined Conversion

**🚨 CRITICAL: API spec never uses `nullable: true`, mappers convert null → undefined**

**RULE**: If external API returns `null` values, convert them to `undefined` in mappers.

```typescript
// ✅ CORRECT - map() automatically converts null → undefined
export function toUser(data: any): User {
  const output: User = {
    id: map(UUID, data.id),              // map() returns undefined if data.id is null
    name: data.name || undefined,         // Explicit: null → undefined
    email: map(Email, data.email),        // map() handles null → undefined
    website: map(URL, data.website)       // map() handles null → undefined
  };
  return output;
}

// ✅ CORRECT - Explicit conversion for non-map fields
export function toOrganization(data: any): Organization {
  const output: Organization = {
    id: data.id !== null ? String(data.id) : undefined,  // null → undefined
    name: data.name || undefined,                        // null → undefined
    isActive: data.isActive ?? undefined                 // null → undefined (nullish coalescing)
  };
  return output;
}

// ❌ WRONG - Leaving null values as-is
export function toUser(data: any): User {
  const output: User = {
    id: map(UUID, data.id),
    name: data.name,        // ❌ Could be null
    email: data.email       // ❌ Could be null
  };
  return output;
}
```

**WHY**:
- TypeScript uses `| undefined` for optional fields, not `| null`
- API spec doesn't have `nullable: true` (keeps it clean)
- Generated types use `Type | undefined`, not `Type | null`
- Consistent with TypeScript conventions

**map() function behavior**:
- `map(Type, null)` → returns `undefined`
- `map(Type, undefined)` → returns `undefined`
- `map(Type, value)` → returns `Type` instance or `undefined`

**⚠️ WARNING: NEVER use `|| undefined` on number fields**

```typescript
// ❌ WRONG - Turns 0 into undefined
count: data.count || undefined         // 0 becomes undefined!
age: data.age || undefined             // 0 becomes undefined!
balance: data.balance || undefined     // 0 becomes undefined!

// ✅ CORRECT - Preserves 0 values
count: data.count ?? undefined         // 0 stays 0, null/undefined → undefined
age: data.age !== null ? data.age : undefined  // Explicit null check
balance: data.balance !== undefined ? data.balance : undefined
```

**WHY**: `0 || undefined` evaluates to `undefined` because 0 is falsy.
- Use `?? undefined` (nullish coalescing) for numbers
- Or explicit `!== null` / `!== undefined` checks
- `|| undefined` is ONLY safe for strings, objects, and arrays

### Extending Mappers
```typescript
export function toUserInfo(data: any): UserInfo {
  const output: UserInfo = {
    ...toUser(data),                          // Reuse base mapper
    lastLogin: map(DateTime, data.last_login)?.toDate(),
    permissions: data.permissions || []
  };
  return output;
}
```

### Field Validation Process

**MANDATORY 3-STEP:**

1. **Analyze interface** (generated/api/index.ts)
2. **Check API schema** (api.yml response)
3. **Map ALL fields** (count must match)

```typescript
// Interface has 5 fields → Mapper must map 5 fields
interface UserInfo {
  id: UUID;           // Required
  name: string;       // Required
  email?: Email;      // Optional
  createdAt: Date;    // Required
  status?: StatusEnum;// Optional
}

export function toUserInfo(raw: any): UserInfo {
  const output: UserInfo = {
    // Required (3)
    id: map(UUID, raw.id),
    name: raw.name,
    createdAt: map(Date, raw.created_at),
    // Optional (2) - MUST be mapped too
    email: map(Email, raw.email),
    status: toEnum(StatusEnum, raw.status)
  };
  return output;
}
```

### Required Field Validation

**🚨 CRITICAL: Mappers MUST throw errors for missing required fields**

```typescript
// ✅ CORRECT - Validate required fields
export function toOrganization(data: any): Organization | undefined {
  if (!data) return undefined;

  // Required field validation - MUST throw error
  if (!data.id && data.id !== 0) {
    throw new InvalidInputError('organization', 'Missing required field: id');
  }

  return {
    id: String(data.id),
    name: data.name || undefined,  // Optional field
  };
}

// ❌ WRONG - Silently returning undefined for missing required field
export function toOrganization(data: any): Organization | undefined {
  if (!data) return undefined;
  if (!data.id) return undefined;  // NO! Should throw error

  return {
    id: String(data.id),
    name: data.name || undefined,
  };
}
```

**Nested Object Handling**:

When objects appear in nested contexts and might have incomplete data, the **parent mapper** handles validation errors:

```typescript
// Parent mapper catches validation errors from child mappers
export function toTokenScope(data: any): TokenScope {
  // Nested org might have null id in some API responses
  let org: Organization | undefined;
  if (data.org) {
    try {
      org = toOrganization(data.org);  // May throw if id is null
    } catch (error) {
      // Incomplete nested organization - treat as undefined
      org = undefined;
    }
  }

  return {
    org,
    user: data.user ? toUser(data.user) : undefined,
    scope: data.scope || undefined,
  };
}
```

**RULE**:
- **Child mappers**: Always validate required fields, throw errors
- **Parent mappers**: Catch validation errors when nesting allows incomplete objects
- **Direct API responses**: Let errors propagate (invalid response = error)
- **Nested contexts**: Catch and convert to undefined (incomplete nested = skip)

### Enum Mapping

**🚨 CRITICAL: Enum values MUST be snake_case**

**STANDARD**: All enum values in this codebase use snake_case format.

- **NEVER** instantiate EnumValue directly
- **ALWAYS** use `toEnum(EnumClass, value)`
- **DEFAULT**: `toEnum` automatically converts input to snake_case (matches our standard)
- **RARELY NEEDED**: Pass third parameter to override transform only if API truly doesn't use snake_case

```typescript
// ✅ CORRECT - Default snake_case transform (PREFERRED)
const status = toEnum(StatusEnum, data.status);
// Input: "ActiveUser" or "active_user" or "ACTIVE_USER" → Converted to "active_user" → Mapped to enum
// This is the standard - use this unless API has special requirements

// ⚠️ RARE - Custom transform only when API doesn't use snake_case
const status = toEnum(StatusEnum, data.status, (val) => val);
// Only use if API truly returns exact enum values in non-snake_case format
// Most APIs return snake_case, so this is rarely needed

// For required enums, validate first
if (!data?.status) {
  throw new InvalidInputError('resource', 'Missing status');
}
const status = toEnum(StatusEnum, data.status); // Safe - uses default snake_case
```

**Why snake_case is our standard:**
- Consistent across all modules
- Matches OpenAPI spec conventions
- Most REST APIs return enum values in snake_case
- Generated enums from OpenAPI use snake_case values
- `toEnum` default behavior aligns with this standard

**When custom transform might be needed (rare):**
- Legacy API uses ONLY PascalCase enum values (e.g., "ActiveUser" not "active_user")
- Legacy API uses ONLY UPPERCASE values (e.g., "ACTIVE" not "active")
- API documentation explicitly shows non-snake_case enum format

**IMPORTANT**: Before using custom transform, verify the API truly doesn't accept snake_case. Most APIs accept multiple formats but prefer snake_case.

### Producer Pattern - ALL Business Logic Here

```typescript
class UserProducer {
  constructor(private client: GitHubClient) {}

  // List operation
  async listUsers(
    results: PagedResults<User>,
    name?: string
  ): Promise<void> {
    const { data } = await this.client.apiClient
      .request({
        url: '/users',
        method: 'get',
        params: {
          limit: results.pageSize,
          page: results.pageNumber,
          name
        }
      })
      .catch(handleAxiosError);

    // Manual assignment (NOT ingest)
    results.items = data.map(toUser) || [];

    // Update count if API provides it
    if (data.total !== undefined) {
      results.count = data.total;
    }
  }

  // Get operation
  async getUser(userId: string): Promise<User> {
    const { data } = await this.client.apiClient
      .request({
        url: `/users/${userId}`,
        method: 'get'
      })
      .catch(handleAxiosError);

    return toUser(data);
  }

  // Create operation
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

  // Delete operation
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

### PagedResults Rules
- **ALWAYS use manual assignment**: `results.items = ...`
- **NEVER use `ingest()`** - Unreliable trimming behavior
- **Update `results.count`** when API provides total
- **Set `results.pageToken`** for token-based pagination
- **NEVER modify** `pageNumber`, `pageSize`, or `pageCount`
- **Sorting**: ALWAYS done by API via params, never post-process

### ConnectionState Pattern

**🚨 CRITICAL: ConnectionState Design Rules**

1. **Include ALL refresh-relevant data** - Store everything needed for token refresh
2. **MANDATORY: expiresIn field** - All states MUST include `expiresIn` (extend baseConnectionState.yml)
   - **WHY**: The server sets cronjobs based on `expiresIn` for automatic token refresh
   - **UNIT**: Must be in **seconds** (integer) until token expires
   - **REQUIRED**: For any token that has an expiration
3. **Store refresh tokens** - If API provides refresh capability, store the refresh token
4. **Refresh method constraint** - `refresh()` can ONLY use ConnectionProfile + ConnectionState data
5. **expiresIn calculation from API responses**:
   - If API returns `expires_in` (seconds) → Use directly as `expiresIn`
   - If API returns `expires_at` (timestamp) → Calculate `expiresIn` as seconds until that time
   - If API returns other expiration format → Convert to `expiresIn` (seconds)
   - **DROP the original field** - Only store `expiresIn`, not `expiresAt` or other formats

#### What to Store in ConnectionState

**MANDATORY when provided by API:**
- `accessToken` - Always store the current access token
- `expiresIn` - Token expiration time (seconds or timestamp)
- `refreshToken` - If API supports token refresh
- `scope` - OAuth scope if relevant for refresh
- `tokenType` - Type of token (bearer, etc.)

**OPTIONAL based on API:**
- `url` - If different endpoints for different tokens
- Vendor-specific metadata needed for refresh

```yaml
# ✅ CORRECT - Using core state (recommended)
# connectionState.yml
$ref: './node_modules/@auditmation/types-core/schema/oauthTokenState.yml'
# Includes: tokenType, accessToken, refreshToken, expiresIn, scope, url
# Already extends baseConnectionState.yml (which provides expiresIn)
```

```yaml
# ✅ CORRECT - Custom state with all refresh data
# connectionState.yml
type: object
allOf:
  - $ref: './node_modules/@auditmation/types-core/schema/baseConnectionState.yml'  # Provides expiresIn
  - type: object
    required:
      - accessToken
    properties:
      accessToken:
        type: string
        format: password
        description: Current access token
      refreshToken:
        type: string
        format: password
        description: Token used to obtain new access token
      scope:
        type: string
        description: OAuth scope for this token
# Note: expiresIn comes from baseConnectionState.yml
```

```yaml
# ❌ WRONG - Missing refresh data and not extending baseConnectionState
type: object
properties:
  accessToken:
    type: string
# Missing: expiresIn (MANDATORY - must extend baseConnectionState.yml)
# Missing: refreshToken (needed for refresh capability)
```

#### Implementing connect() with State

```typescript
// ✅ CORRECT - Store ALL relevant data from API (expiresIn provided directly)
async connect(profile: ConnectionProfile): Promise<ConnectionState> {
  const response = await this.httpClient.post('/auth/login', {
    username: profile.username,
    password: profile.password
  });

  // Store EVERYTHING the API provides that might be needed for refresh
  const state: ConnectionState = {
    accessToken: response.data.access_token,
    refreshToken: response.data.refresh_token,  // Store for refresh()
    expiresIn: response.data.expires_in,         // MANDATORY - seconds until expiration
    tokenType: response.data.token_type,         // Store if needed for headers
    scope: response.data.scope                   // Store if needed for refresh
  };

  this.connectionState = state;
  return state; // Framework persists
}
```

```typescript
// ✅ CORRECT - Calculate expiresIn when API returns expires_at (timestamp)
async connect(profile: ConnectionProfile): Promise<ConnectionState> {
  const response = await this.httpClient.post('/auth/login', {
    username: profile.username,
    password: profile.password
  });

  // Calculate expiresIn from expires_at timestamp
  const expiresAtTimestamp = new Date(response.data.expires_at).getTime();
  const nowTimestamp = Date.now();
  const expiresIn = Math.floor((expiresAtTimestamp - nowTimestamp) / 1000); // Convert to seconds

  // CRITICAL: Store ONLY expiresIn, DROP expires_at
  // The server needs expiresIn for cronjobs
  const state: ConnectionState = {
    accessToken: response.data.access_token,
    refreshToken: response.data.refresh_token,
    expiresIn: expiresIn,  // MANDATORY - calculated from expires_at, in SECONDS
    tokenType: response.data.token_type,
    scope: response.data.scope
    // Note: expires_at NOT stored - only expiresIn is needed
  };

  this.connectionState = state;
  return state; // Framework persists
}
```

```typescript
// ❌ WRONG - Storing expiresAt instead of expiresIn
async connect(profile: ConnectionProfile): Promise<ConnectionState> {
  const response = await this.httpClient.post('/auth/login', {
    username: profile.username,
    password: profile.password
  });

  const state: ConnectionState = {
    accessToken: response.data.access_token,
    expiresAt: response.data.expires_at,  // ❌ WRONG - should be expiresIn (seconds)
  };

  this.connectionState = state;
  return state;
}
// Problem: Server cannot set cronjob without expiresIn (seconds)
```

#### Implementing refresh() Method

**CRITICAL**: `refresh()` can ONLY access:
- `this.connectionProfile` - Original connection credentials
- `this.connectionState` - Current state (with refreshToken, etc.)

```typescript
// ✅ CORRECT - Uses only profile + state
async refresh(): Promise<ConnectionState> {
  // Can use data from connectionState (refreshToken)
  const response = await this.httpClient.post('/auth/refresh', {
    refresh_token: this.connectionState.refreshToken,
    // Can also use profile data if needed
    client_id: this.connectionProfile.client_id
  });

  // Update state with new tokens
  const newState: ConnectionState = {
    accessToken: response.data.access_token,
    refreshToken: response.data.refresh_token || this.connectionState.refreshToken,
    expiresIn: response.data.expires_in,
    tokenType: response.data.token_type,
    scope: response.data.scope
  };

  this.connectionState = newState;
  return newState;
}
```

```typescript
// ❌ WRONG - Requires data not in profile/state
async refresh(): Promise<ConnectionState> {
  const response = await this.httpClient.post('/auth/refresh', {
    refresh_token: this.connectionState.refreshToken,
    device_id: 'hardcoded-value'  // NO! Not in profile/state
  });
  // This will fail - device_id should be in ConnectionProfile or ConnectionState
}
```

**When to use ConnectionState**:
- OAuth2 flows (access + refresh tokens)
- Session-based authentication
- APIs requiring token refresh
- Token expiration tracking needed

**When to return void from connect()**:
- API key authentication (static, never expires)
- Basic auth (credentials used each request, no state)
- No refresh capability needed

### 9. Use Core Connection Profiles and States
- **MANDATORY**: Use existing core schemas from `@auditmation/types-core/schema` when they match
- **FORBIDDEN**: Creating custom connectionProfile.yml or connectionState.yml when core schema exists

**Available Core Connection Profiles:**

```yaml
# ✅ CORRECT - Token/API Key authentication
# connectionProfile.yml
$ref: './node_modules/@auditmation/types-core/schema/tokenProfile.yml'

# Fields: apiToken (required), url (optional)
# Use when: API uses a single token/key for authentication
```

```yaml
# ✅ CORRECT - OAuth Client Credentials
# connectionProfile.yml
$ref: './node_modules/@auditmation/types-core/schema/oauthClientProfile.yml'

# Fields: client_id (required), client_secret (required), url (optional)
# Use when: OAuth client credentials grant (RFC 6749 section 4.4)
```

```yaml
# ✅ CORRECT - OAuth Token-based
# connectionProfile.yml
$ref: './node_modules/@auditmation/types-core/schema/oauthTokenProfile.yml'

# Fields: tokenType (default: bearer), accessToken (required), url (optional)
# Use when: Pre-obtained OAuth token authentication
```

```yaml
# ✅ CORRECT - Username/Password authentication (Basic Auth pattern)
# connectionProfile.yml
$ref: './node_modules/@auditmation/types-core/schema/basicConnection.yml'

# Fields: uri (required, URL), username (required), password (required)
# Use when: API uses username/password or email/password authentication
# Note: For email specifically, you can extend this and change username to email with format: email
```

```yaml
# ✅ CORRECT - Email/Password authentication (extending basicConnection)
# connectionProfile.yml
type: object
allOf:
  - $ref: './node_modules/@auditmation/types-core/schema/basicConnection.yml'
  - type: object
    properties:
      username:
        type: string
        format: email  # Override to require email format
        description: User email for authentication

# Extends basicConnection but enforces email format on username field
# Use when: API requires email specifically (not just any username)
```

**Available Core Connection States:**

```yaml
# ✅ CORRECT - Simple token state
# connectionState.yml
$ref: './node_modules/@auditmation/types-core/schema/tokenConnectionState.yml'

# Fields: accessToken, expiresIn (from baseConnectionState)
# Use when: Only need to persist access token with expiration
# Note: Extends baseConnectionState.yml
```

```yaml
# ✅ CORRECT - Full OAuth state
# connectionState.yml
$ref: './node_modules/@auditmation/types-core/schema/oauthTokenState.yml'

# Fields: tokenType, accessToken, refreshToken, expiresIn (from base), scope, url
# Use when: OAuth authorization code flow with refresh capability
# Note: Extends baseConnectionState.yml
```

**When to Create Custom Profile/State:**

Only create custom schemas when:
- Authentication method doesn't match any core profile
- Additional vendor-specific fields required beyond core profile fields
- Specialized authentication flow not covered by core

```yaml
# ⚠️ CONSIDER FIRST - Can this use basicConnection.yml?
# For username/password or email/password auth, prefer extending basicConnection.yml
# See examples above for basicConnection.yml usage

# ✅ ACCEPTABLE - Fully custom (but consider basicConnection first!)
# connectionProfile.yml (custom when core types don't fit)
type: object
required:
  - email
  - password
properties:
  email:
    type: string
    format: email
  password:
    type: string
    format: password
  baseUrl:
    type: string
    format: url
    default: https://api.vendor.com
# Note: Could potentially extend basicConnection.yml instead
```

**Decision Process:**
1. Check if core profile matches authentication method
   - Token/API Key → `tokenProfile.yml`
   - OAuth client credentials → `oauthClientProfile.yml`
   - OAuth token → `oauthTokenProfile.yml`
   - Username/password or email/password → `basicConnection.yml` (or extend it)
2. If exact match → Use core profile with $ref
3. If partial match → Extend core profile (use allOf)
4. If no match → Create custom profile with full schema (rare)

**WHY**: Core profiles ensure consistency, reduce duplication, and provide standard patterns that the framework expects.

### Error Handler Pattern

```typescript
// src/util.ts
import {
  InvalidCredentialsError,
  NoSuchObjectError,
  UnauthorizedError,
  RateLimitExceededError,
  UnexpectedError
} from '@auditmation/types-core-js';

export function handleAxiosError(error: any): never {
  const status = error.response?.status || 500;
  const message = error.response?.data?.message || error.message;

  switch (status) {
    case 401:
      throw new InvalidCredentialsError();
    case 403:
      throw new UnauthorizedError();
    case 404:
      throw new NoSuchObjectError('resource', 'unknown');
    case 429:
      throw new RateLimitExceededError();
    default:
      throw new UnexpectedError(`API error: ${message}`, status);
  }
}
```

### TypeScript Configuration
Add to tsconfig.json:
```json
{
  "compilerOptions": {
    "skipLibCheck": true
  }
}
```

## 🟢 GUIDELINES

### Import Organization
```typescript
// Core types - from @auditmation packages
import { URL, UUID, Email } from '@auditmation/types-core-js';
import { toEnum, map } from '@auditmation/util-hub-module-utils';

// NEVER from Node.js built-ins
// ❌ import { URL } from 'url';
```

### Type Discovery Priority
1. Check generated/api/index.ts for exact names
2. Check library .d.ts files
3. Check existing Mappers for patterns
4. Check OpenAPI spec (adapt to TypeScript)
5. Never guess

### Complex Mapper Patterns

**Nested objects:**
```typescript
function toAddress(data: any): Address | undefined {
  if (!data) return undefined;

  const output: Address = {
    street: data.street,
    city: data.city,
    country: toCountry(data.country)
  };
  return output;
}
```

**Arrays:**
```typescript
function toUsers(data: any[]): User[] {
  if (!Array.isArray(data)) return [];
  return data.map(toUser);
}
```

**Recursive structures:**
```typescript
function toTreeNode(data: any): TreeNode {
  const output: TreeNode = {
    id: data.id,
    name: data.name,
    children: data.children?.map(toTreeNode)
  };
  return output;
}
```

### Code Style
- No comments unless specifically requested
- Clear, self-documenting code
- Follow existing patterns in codebase
- Consistent naming throughout

## API Quirks Handling

### APIs Returning 200 for Errors
Apply "fake error handler":

```typescript
async someOperation(): Promise<Resource> {
  const response = await this.httpClient.get('/resource');

  if (response.status === 200 && response.data.error) {
    if (response.data.error === 'NOT_FOUND') {
      throw new NoSuchObjectError('resource', 'unknown');
    }
    throw new UnexpectedError(response.data.error_message);
  }

  return toResource(response.data);
}
```

### Rate Limiting
Simple - just throw:
```typescript
case 429:
  throw new RateLimitExceededError();
```

## Dependency Management

### Required Dependencies (package.json)

**MANDATORY packages that MUST be in dependencies:**

```json
{
  "dependencies": {
    "@auditmation/hub-core": "^4.5.19",
    "@auditmation/util-hub-module-utils": "^1.0.0",
    "@auditmation/util-logger": "^4.0.9"
  },
  "peerDependencies": {
    "@auditmation/types-core": "^4.9.1",
    "@auditmation/types-core-js": "^4.10.2"
  }
}
```

**What each package provides:**

- **`@auditmation/hub-core`** - Hub framework core functionality, interfaces
- **`@auditmation/util-hub-module-utils`** - **MANDATORY** - Provides `map()`, `toEnum()`, `mapArray()` functions for type mapping
- **`@auditmation/util-logger`** - Logging utilities (winston wrapper)
- **`@auditmation/types-core`** - Core type schemas
- **`@auditmation/types-core-js`** - Core TypeScript types (UUID, Email, URL, DateTime, Errors, etc.)

**Why util-hub-module-utils is mandatory:**
- Provides `map()` function used in ALL mappers
- Handles optional value mapping automatically
- Ensures consistent type conversion patterns
- Without it, mappers will fail to compile
