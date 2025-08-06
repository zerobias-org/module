# Task 07: Implement Module Logic

## Overview

This task implements the core module logic including Client, Producer implementations, and main Connector class following standard architectural patterns.

## Input Requirements

- Task 06 output file: `.claude/.localmemory/{action}-{module-identifier}/task-06-output.json`  
- Task 05 output file: `.claude/.localmemory/{action}-{module-identifier}/task-05-output.json` (for property mappings and service config)
- Task 02 output file: `.claude/.localmemory/{action}-{module-identifier}/task-02-output.json` (for HTTP client library)
- Generated TypeScript interfaces in `generated/api/index.ts`
- Validated `api.yml` file for reference

## Process Steps

### 🚨 CRITICAL: Use Generated Interface Names as Source of Truth

**Before starting implementation:**
1. **Analyze `generated/api/index.ts`** to identify exact interface names
2. **Use interface names for all file and class naming** - ignore any mismatches in existing module structure
3. **Example**: If generated interface is `GitHubConnector`, create `GitHubConnectorImpl.ts` with `class GitHubConnectorImpl`
4. **Producer interfaces**: Use exact names from generated code (e.g., `UserApi`, `OrganizationApi`, etc.)

### 1. Pre-Implementation Validation

Check for compilation errors and fix any generated code issues:

**🚨 CRITICAL: Configure TypeScript to Skip External Library Checks**
- **Add `"skipLibCheck": true`** to `tsconfig.json` → `compilerOptions` to avoid external library compilation errors
- **Example tsconfig.json fix:**
```json
{
  "compilerOptions": {
    "skipLibCheck": true,
    // ... other options
  }
}
```

- Run `npm run build` to identify compilation errors
- Fix any import issues in generated TypeScript files
- Ensure all dependencies are properly installed



### 2. Extract Service Configuration from Task 05 Output

Read the service configuration from previous task outputs:

- **Service Name**: Extract from `task-05-output.json` → `apiDefinition.serviceName`
- **Base URL**: Extract from `task-05-output.json` → `apiDefinition.baseUrl` 
- **Authentication Method**: Extract from `task-05-output.json` → `apiDefinition.authenticationMethod`
- **HTTP Client Library**: Extract from `task-02-output.json` → `httpClientLibrary`
- **Property Mappings**: Extract from `task-05-output.json` → `apiDefinition.propertyMappings.mappings`

Analyze the generated interfaces:
- Review `generated/api/index.ts` for Connector and Api interfaces
- **Use interface names as source of truth** - File names and class names should match generated interface names exactly
- Identify API tags from the OpenAPI specification for producer creation
- Note: Module file names might not match interface names - always use interface names for implementation

### 3. Install Required Dependencies

Install the mandatory dependencies for module implementation:

- **Core utilities**: `@auditmation/util-hub-module-utils` (for toEnum, map functions)
- **Error types**: `@auditmation/types-core-js` (for standard error classes)
- **Hub core**: `@auditmation/hub-core` (for ConnectionMetadata, OperationSupportStatus)
- **HTTP Client Library**: Install the library specified in `task-02-output.json → httpClientLibrary`

### 4. Create HTTP Client Class

Create the HTTP client class following these principles:

**File**: `src/{ServiceName}Client.ts`

**Responsibilities**:
- Connection management (connect, isConnected, disconnect)
- Authentication setup and credential handling
- HTTP client configuration (timeouts, headers, interceptors)
- Error handling and mapping to core error types
- **Does NOT contain API-specific methods** (those belong in Producers)

**Key Methods**:
- `connect(profile: ConnectionProfile): Promise<void>` - Authenticate using profile (**Note**: Client returns void, Connector interface expects void)
- `isConnected(): Promise<boolean>` - Validate current connection
- `disconnect(): Promise<void>` - Clean up connection state
- `getHttpClient()` - Return configured HTTP client instance

**🚨 CRITICAL: Connect Method Return Types**:
- **HTTP Client**: `connect()` should return `Promise<void>` (not ConnectionState)
- **Main Connector**: `connect()` must return `Promise<void>` to match generated interface
- **Connection State**: Store internally, don't return from connect method

**🚨 CRITICAL: Core Error Usage**:
- **MUST use only core errors from `@auditmation/types-core-js`**
- **NEVER use generic `Error` class**
- **See [Core Error Usage Guide](./core-error-usage-guide.md) for complete documentation**

**Core Error Constructor Patterns (Correct Usage)**:
```typescript
import {
  InvalidCredentialsError,
  UnauthorizedError,
  NoSuchObjectError,
  InvalidInputError,
  RateLimitExceededError,
  UnexpectedError,
} from '@auditmation/types-core-js';

// Most errors only require optional timestamp parameter
new InvalidCredentialsError()  // timestamp auto-generated
new UnauthorizedError()        // timestamp auto-generated
new RateLimitExceededError()   // timestamp auto-generated

// Some errors require specific context parameters
new NoSuchObjectError('user', 'john_doe')  // type, id required
new InvalidInputError('email', 'invalid@value')  // type, value required  
new UnexpectedError('Database connection failed')  // message required
```

**HTTP Status Code Mapping**:
- 401 → `new InvalidCredentialsError()`
- 403 → `new UnauthorizedError()` or `new RateLimitExceededError()` (check message)
- 404 → `new NoSuchObjectError('resource', 'identifier')`
- 400/422 → `new InvalidInputError('field', 'value')`
- 429 → `new RateLimitExceededError()`
- 5xx → `new UnexpectedError('Error message', statusCode)`

**Error Handling Example**:
```typescript
private handleError(error: any): never {
  const status = error.status || 500;
  const message = error.message || 'Unknown error';
  
  switch (status) {
    case 401: throw new InvalidCredentialsError();
    case 403: 
      if (message.includes('rate limit')) {
        throw new RateLimitExceededError();
      }
      throw new UnauthorizedError();
    case 404: throw new NoSuchObjectError('resource', 'unknown');
    case 400:
    case 422: throw new InvalidInputError('request', message);
    case 429: throw new RateLimitExceededError();
    default: throw new UnexpectedError(message, status);
  }
}
```

**Configuration**:
- Use base URL from Task 05 output
- Configure appropriate timeout values
- Set up authentication headers based on auth method from Task 05

### 5. Create Data Mappers Using Task 05 Property Mappings

Create the data transformation layer using the property mappings from Task 05:

**File**: `src/Mappers.ts`

**Implementation Principles**:
1. **Use Task 05 Property Mappings**: Apply the exact field mappings from `task-05-output.json`
2. **Object Literal Pattern**: Use object literals, not constructors 
3. **Utility Functions**: Use `toEnum()` and `map()` from `@auditmation/util-hub-module-utils`
4. **String Interpolation**: Use `${field}` for required enum/date/URL fields
5. **Nested Objects**: Extract as internal functions, not inline
6. **Critical Type Imports**: Always import `URL`, `UUID` from `@auditmation/types-core-js`, never from Node.js built-ins

**Property Mappings from Task 05**:
Extract the specific property mappings defined for this service from `task-05-output.json → apiDefinition.propertyMappings.mappings`. These will vary by service but typically include:
- Field name transformations (camelCase ↔ snake_case)
- Date field mappings
- URL field mappings
- Service-specific property names

**Mapper Functions**:
- `mapUser(raw: any): User` - Transform user data
- `mapOrganization(raw: any): Organization` - Transform organization data  
- `mapEnterprise(raw: any): Enterprise` - Transform enterprise data
- Internal functions for nested objects (not exported)

**🚨 CRITICAL: Enum Mapping Rules**:
- **NEVER instantiate EnumValue directly** (constructor is private)
- **PREFERRED: Use `toEnum(EnumClass, value)`** - where EnumClass does NOT have "Def" suffix
- **Alternative: Use `EnumClass.from(value)`** - direct enum class method (less preferred in mappers)
- **Mappers preference**: Always use `toEnum()` utility function for consistency
- **Example**: `toEnum(StatusEnum, raw.status)` (preferred) vs `StatusEnum.from(raw.status)` (alternative)

**Pattern Example**:
```typescript
import { toEnum, map } from '@auditmation/util-hub-module-utils';
import { URL, UUID } from '@auditmation/types-core-js'; // CRITICAL: Use core types, not Node.js

export function mapEntity(raw: any): EntityType {
  const output: EntityType = {
    id: raw.id,
    name: raw.name,
    // Apply Task 05 property mappings - these vary by service
    fieldName: map(URL, raw.api_field_name),           // URL mapping using core URL type
    createdAt: map(Date, `${raw.created_at}`),         // Required field interpolation  
    optionalField: map(Date, raw.optional_field),       // Optional field, no interpolation
    // Enum mapping examples (prefer toEnum in mappers):
    status: toEnum(StatusEnum, raw.status),             // PREFERRED: Using toEnum utility
    type: toEnum(TypeEnum, raw.type)                    // PREFERRED: Consistent toEnum usage
  };
  return output;
}
```

### 6. Create Producer Implementation Classes

Create producer implementations for each API domain (based on generated interfaces):

**Files**: `src/{InterfaceName}Impl.ts` (one for each generated Api interface)

**Architecture**:
- **Use generated interface names as source of truth**: Analyze `generated/api/index.ts` to identify exact interface names
- **Implement generated interfaces**: Each producer implements its corresponding Api interface exactly
- **File naming**: Use interface name + "Impl" (e.g., `GitHubConnector` → `GitHubConnectorImpl.ts`)
- **Class naming**: Use interface name + "Impl" (e.g., `class GitHubConnectorImpl implements GitHubConnector`)
- **Use HTTP client**: Get configured client from service client
- **Apply mappers**: Transform API responses using mapper functions
- **Handle pagination**: Follow PagedResults patterns for list operations

**Key Patterns**:

**PagedResults Handling**:
- Use `results.count` (not `totalCount`)
- Only set count if pagination header exists
- Use `results.pageNumber` and `results.pageSize` for requests
- **PageToken Support**: Set `results.pageToken` when available (only for operations with pageToken in OpenAPI params), `undefined` when not
- Apply mappers with direct method reference: `results.items = response.data.map(mapEntity)`

**Error Handling**:
- Let HTTP client handle error mapping
- Producers focus on API-specific logic
- Don't duplicate error handling from client

**API Methods**:
- `list(results: PagedResults<Type>): Promise<void>` - List resources with pagination
- `get(id: string|number): Promise<Type>` - Get single resource by ID
- Additional methods as defined in generated interfaces

**Example Structure**:
```typescript
export class EntityProducerImpl implements EntityApi {
  private httpClient: any;

  constructor(private client: ServiceClient) {
    this.httpClient = client.getHttpClient();
  }

  async list(results: PagedResults<EntityType>): Promise<void> {
    // Pagination parameters
    // API request  
    // Header parsing (only if exists)
    // PageToken handling (only for operations with pageToken in OpenAPI params)
    // Mapper application
  }

  async get(id: string): Promise<EntityType> {
    // API request
    // Mapper application
  }
}
```

### 7. Create Main Connector Implementation

Create the main connector class that orchestrates all components:

**File**: `src/{ConnectorInterfaceName}Impl.ts` (based on exact generated interface name)

**Responsibilities**:
- **Implement Connector Interface**: Implement the generated Connector interface (use exact name from `generated/api/index.ts`)
- **Manage Connection Lifecycle**: Delegate to HTTP client for connection management
- **Provide Producer Access**: Create and expose producer instances
- **Standard Methods**: Implement required `isSupported()` and `metadata()` methods

**Key Components**:

**Connection Methods**:
- `connect(profile: ConnectionProfile): Promise<ConnectionState>` - Delegate to client
- `isConnected(): Promise<boolean>` - Check connection status via client  
- `disconnect(): Promise<void>` - Clean up via client

**Standard Methods (Mandatory)**:
- `isSupported(operationId: string): Promise<OperationSupportStatusDef>` - Return `OperationSupportStatus.Maybe`
- `metadata(): Promise<ConnectionMetadata>` - Return `new ConnectionMetadata(ConnectionStatus.Down)`

**Producer Getters**:
- Create getter methods for each producer (based on exact generated interface names)
- Method names must match generated interface exactly
- Instantiate producers with HTTP client
- Example: `getGeneratedApiName(): GeneratedApiInterface { return new GeneratedApiInterfaceImpl(this.client); }`

**Architecture Pattern**:
```typescript
export class GeneratedConnectorImpl implements GeneratedConnector {
  private client: ServiceClient;
  private generatedApiProducer?: GeneratedApiImpl;
  
  constructor() {
    this.client = new ServiceClient();
  }
  
  // Connection methods (delegate to client)
  // Standard methods (mandatory implementations)  
  // Producer getters (lazy initialization with exact interface names)
}
```

### 8. Update Module Entry Point

Update the module entry point to provide clean public API:

**File**: `src/index.ts`

**Exports**:
- **Factory Function**: `newServiceName(): ServiceImpl` - Primary way to create module instances
- **Implementation Class**: Export the main implementation class
- **Client Class**: Export HTTP client (for advanced usage)
- **Mappers**: Export all mapper functions (for testing/debugging)

**Pattern**:
```typescript
import { GeneratedConnectorImpl } from './GeneratedConnectorImpl';

export function newGeneratedServiceName(): GeneratedConnectorImpl {
  return new GeneratedConnectorImpl();
}

export { GeneratedConnectorImpl } from './GeneratedConnectorImpl';
export { ServiceClient } from './ServiceClient';
export * from './Mappers';
```

**Usage**:
- Standard usage: `const service = newGeneratedServiceName()`
- Direct instantiation: `const service = new GeneratedConnectorImpl()`
- Access to internals: Import client or mappers directly

### 9. Validate Implementation

Ensure the implementation is correct and follows standards:

**Compilation Check**:
- Run `npm run build` to verify TypeScript compilation
- Fix any type errors or import issues
- Ensure all generated interfaces are properly implemented

**Code Quality**:
- **🚨 CRITICAL: Run `npm run lint:src -- --fix`** after implementation to auto-fix linting issues
- Run `npm run lint` to check remaining code style issues
- Manually fix any remaining linting warnings or errors
- Ensure consistent naming and formatting

**Architecture Validation**:
- Verify client provides HTTP client access method
- Confirm producers implement all generated interface methods
- Check that mappers use utility functions correctly
- Validate error handling follows core error patterns

## Success Criteria

- TypeScript compilation succeeds without errors
- All producer interface methods are implemented  
- Error handling covers all API response codes
- Data mappers handle all required fields using mandatory patterns
- Client provides HTTP client access method
- Standard `isSupported()` and `metadata()` methods implemented

## Output Format

Store in `.claude/.localmemory/{action}-{module-identifier}/task-07-output.json`:

```json
{
  "moduleImplementation": {
    "status": "completed",
    "timestamp": "${iso_timestamp}",
    "modulePath": "${module_path}",
    "files": {
      "client": {
        "path": "${module_path}/src/{ServiceName}Client.ts",
        "status": "created"
      },
      "implementation": {
        "path": "${module_path}/src/{ServiceName}Impl.ts", 
        "status": "created"
      },
      "mappers": {
        "path": "${module_path}/src/Mappers.ts",
        "status": "created"
      },
      "producers": [
        {
          "path": "${module_path}/src/{Tag}ProducerImpl.ts",
          "status": "created"
        }
      ],
      "entryPoint": {
        "path": "${module_path}/src/index.ts",
        "status": "updated"
      }
    },
    "validations": [
      {
        "check": "TypeScript compilation successful",
        "command": "npm run build",
        "expected_exit_code": 0,
        "status": "passed|failed"
      },
      {
        "check": "Linting passes",
        "command": "npm run lint", 
        "expected_exit_code": 0,
        "status": "passed|failed"
      }
    ]
  }
}
```

## 🚨 Critical Implementation Rules

**MANDATORY PATTERNS - Violating ANY rule means task failure:**

1. **Core Error Usage**: MUST use core errors from `@auditmation/types-core-js` - NEVER use generic `Error` class
2. **Mapper Pattern**: Use object literals with `toEnum()` and `map()` utilities
3. **String Interpolation**: Use `${field}` for required enum/date/URL fields
4. **PagedResults**: Use `results.count` (not `totalCount`) and `results.pageToken` (not `nextToken`)
5. **Client Architecture**: Client provides configured HTTP client, Producers handle API logic
6. **Standard Methods**: Implement `isSupported()` and `metadata()` with exact patterns
7. **Core Type Imports**: Always import `URL`, `UUID` from `@auditmation/types-core-js`
8. **Task 05 Mappings**: Use property mappings from Task 05 output for data transformation
9. **TypeScript Configuration**: Add `"skipLibCheck": true` to avoid external library errors

**Validation Checklist:**
- [ ] Pre-implementation compilation check completed
- [ ] Generated code import issues fixed  
- [ ] Required dependencies installed
- [ ] TypeScript config includes `"skipLibCheck": true`
- [ ] **CRITICAL**: All error handling uses core errors - NO generic `Error` class used
- [ ] Core errors imported from `@auditmation/types-core-js`
- [ ] Client provides HTTP client access method
- [ ] Producers use mandatory PagedResults pattern with `pageToken` (not `nextToken`)
- [ ] Mappers use object literals with utility functions
- [ ] Standard `isSupported()` and `metadata()` methods implemented
- [ ] Error handling maps HTTP status codes to appropriate core error types
- [ ] All files compile without errors
- [ ] Implementation follows architectural separation
- [ ] **MANDATORY**: `npm run lint:src -- --fix` executed after implementation
