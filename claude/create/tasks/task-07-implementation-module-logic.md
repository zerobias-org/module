# Task 06: Implement Module Logic

## Prerequisites

**🚨 CRITICAL**: Before starting this task, read `CLAUDE.md` to understand the project structure, rules, and requirements.

## Overview

This task implements the core module logic including Client, Producer implementations, and main Connector class following standard architectural patterns.

## Input Requirements

- Task 05 output file: `.claude/.localmemory/{action}-{module-identifier}/task-05-output.json` (includes interface generation and property mappings)
- Task 02 output file: `.claude/.localmemory/{action}-{module-identifier}/task-02-output.json` (for HTTP client library)
- Where `{module-identifier}` is the product identifier derived from the identified product package (e.g., `vendor-suite-service` from `@scope/product-vendor-suite-service`, or `vendor-service` from `@scope/product-vendor-service`)
- Generated TypeScript interfaces in `generated/api/index.ts`
- Validated `api.yml` file for reference

## Process Steps

### 0. Context Management and Goal Reminder

**🚨 MANDATORY FIRST STEP - CONTEXT CLEARING**: 
- **IGNORE all previous conversation context** - This task runs in isolation
- **CLEAR mental context** - Treat this as a fresh start with no prior assumptions
- **REQUEST**: User should run `/clear` or `/compact` command before starting this task for optimal performance

**🚨 MANDATORY SECOND STEP**: Read and understand the original user intent:

1. **Read initial user prompt**:
   - Load `.claude/.localmemory/{action}-{module-identifier}/task-01-output.json`
   - Extract and review the `initialUserPrompt` field
   - Understand the original goal, scope, and specific user requirements

2. **Goal alignment verification**:
   - Ensure all implementation decisions align with the original user request
   - Keep the user's specific intentions and scope in mind throughout the task
   - If any conflicts arise between task instructions and user intent, prioritize user intent

3. **Context preservation**:
   - Reference the original prompt when making implementation architecture decisions
   - Ensure the module logic serves the user's actual needs, not generic assumptions

### 🚨 CRITICAL: Use Generated Interface Names as Source of Truth

**Before starting implementation:**
1. **Analyze `generated/api/index.ts`** to identify exact interface names
2. **Use interface names for all file and class naming** - ignore any mismatches in existing module structure
3. **Example**: If generated interface is `GitHubConnector`, create `GitHubConnectorImpl.ts` with `class GitHubConnectorImpl`
4. **Producer interfaces**: Use exact names from generated code (e.g., `UserApi`, `OrganizationApi`, etc.)

### 1. Pre-Implementation Validation and Build Gate

**🚨 CRITICAL BUILD GATE - MUST PASS BEFORE ANY IMPLEMENTATION**:

This task has a **MANDATORY BUILD GATE** that must pass before any implementation work can proceed. If `npm run build` fails at any point, all work must stop and issues must be resolved.

**Step 1: Configure TypeScript Build Environment**
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

**Step 2: Initial Build Validation**
- **🚨 MANDATORY**: Run `npm run build` to establish baseline compilation status
- **If build fails**: Fix ALL compilation errors before proceeding to implementation
- **Common fixes**:
  - Fix import issues in generated TypeScript files
  - Ensure all dependencies are properly installed
  - Resolve any interface or type mismatches
- **Build must pass**: Only proceed to implementation steps once `npm run build` exits with code 0

**🚨 CRITICAL RULE**: If `npm run build` fails at ANY point during this task, immediately stop all work and resolve build issues. No implementation artifacts should be created while the build is broken.



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

**🚨 BUILD VALIDATION CHECKPOINT**: After installing dependencies, run `npm run build` to verify all dependencies resolve correctly. Build must pass before proceeding.

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

**🚨 CRITICAL: NO ENVIRONMENT VARIABLES**:
- **FORBIDDEN**: Client MUST NOT depend on environment variables (NO `process.env.*` usage)
- **FORBIDDEN**: Client MUST NOT expect parameters from environment variables
- **MANDATORY**: ALL required parameters MUST come from method arguments or connection profile
- **MANDATORY**: If API operations require contextual parameters (organizationId, tenantId), these MUST be passed as method parameters from the API specification

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

**🚨 BUILD VALIDATION CHECKPOINT**: After creating HTTP Client class, run `npm run build` to verify client compiles correctly. Fix any import or type errors before proceeding.

### 5. Create Data Mappers Using Task 05 Property Mappings

Create the data transformation layer using the property mappings from Task 05:

**File**: `src/Mappers.ts`

**🚨 CRITICAL: Field Mapping Validation Requirements**:

**Before Writing Any Mapper**:
1. **Analyze Generated Interfaces**: Read `generated/api/index.ts` to understand the EXACT interface structure for each type
2. **Validate Against OpenAPI Spec**: Cross-reference `api.yml` to verify which fields are actually available in API responses
3. **Field Existence Verification**: ONLY map fields that exist in BOTH the OpenAPI response schema AND the generated interface
4. **Required vs Optional**: Respect the required/optional nature of fields as defined in the generated interfaces

**Implementation Principles**:
1. **🚨 STRICT INTERFACE ADHERENCE**: Map ONLY fields that exist in the generated interface - never add extra fields
2. **🚨 API SPEC VALIDATION**: Map ONLY fields that are documented in the `api.yml` response schemas
3. **Use Task 05 Property Mappings**: Apply the exact field mappings from `task-05-output.json` BUT validate each mapping
4. **Object Literal Pattern**: Use object literals, not constructors 
5. **Utility Functions**: Use `toEnum()` and `map()` from `@auditmation/util-hub-module-utils`
6. **String Interpolation**: Use `${field}` for required enum/date/URL fields
7. **Nested Objects**: Extract as internal functions, not inline
8. **Critical Type Imports**: Always import `URL`, `UUID` from `@auditmation/types-core-js`, never from Node.js built-ins

**🚨 FIELD MAPPING VALIDATION PROCESS**:

**Step 1: Interface Analysis**
- Read the target interface from `generated/api/index.ts`
- Document ALL required fields and their types
- Document ALL optional fields and their types  
- **🚨 COUNT TOTAL FIELDS**: Record exact number of fields (required + optional) that must be mapped
- Note any enum types or special core types (URL, UUID, Date)

**Step 2: API Response Schema Validation**
- Examine the corresponding response schema in `api.yml`
- Verify each interface field has a corresponding API response field
- Check field types match between interface and API spec
- Identify any fields in interface that don't exist in API (these should NOT be mapped)

**Step 3: Complete Mapping Implementation**
- **🚨 CRITICAL**: Map ALL fields that exist in the interface - this means EVERY SINGLE FIELD including required AND optional fields
- **🚨 ZERO MISSING FIELDS**: Count the fields in the interface, count the mapped fields in mapper - numbers must match exactly
- **Validation**: Ensure every interface field (required and optional) has a corresponding API response field 
- **Error Handling**: If interface field has no API response field, this indicates a mismatch that must be resolved
- **Required Fields**: Must be mapped with proper type conversion
- **Optional Fields**: Must be mapped with conditional logic (using && operator or ternary)
- **Type Conversion**: Use proper type conversion for all fields that exist
- **No Missing Fields**: TypeScript compilation will fail if any interface fields are missing from mapper output

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

**🚨 CRITICAL: Enum to String Conversion**:
- **When enums need strings**: Use `.toString()` method or template literals
- **toString() example**: `const statusString = myEnum.toString()`
- **Template literal example**: `const message = \`Status: \${myEnum}\``
- **Applies to ALL core types**: URL, UUID, Email, etc. - all support `.toString()` and template interpolation

**Validated Mapping Pattern Example**:
```typescript
import { toEnum, map } from '@auditmation/util-hub-module-utils';
import { URL, UUID } from '@auditmation/types-core-js'; // CRITICAL: Use core types, not Node.js

// Example: Mapping ALL fields from interface (both required AND optional)
export function mapEntity(raw: any): EntityType {
  // STEP 1: Count fields in EntityType interface: 7 fields total (4 required + 3 optional)
  // STEP 2: Validate all 7 fields exist in api.yml response schema
  // STEP 3: Map ALL 7 fields - zero missing fields allowed
  
  const output: EntityType = {
    // ✅ REQUIRED FIELD 1: 'id' exists in both EntityType interface AND api.yml response
    id: raw.id,
    
    // ✅ REQUIRED FIELD 2: 'name' exists in both EntityType interface AND api.yml response  
    name: raw.name,
    
    // ✅ REQUIRED FIELD 3: Apply Task 05 property mappings for required fields
    // Example: API returns 'api_field_name', interface expects 'fieldName'
    fieldName: map(URL, raw.api_field_name),           
    
    // ✅ REQUIRED FIELD 4: Required date field exists in both interface and API spec
    createdAt: map(Date, `${raw.created_at}`),         
    
    // ✅ OPTIONAL FIELD 1: Optional field - MUST be mapped with conditional logic
    ...(raw.optional_field && { optionalField: map(Date, raw.optional_field) }),
    
    // ✅ OPTIONAL FIELD 2: Optional enum - MUST be mapped even if optional
    ...(raw.status && { status: toEnum(StatusEnum, raw.status) }),
    
    // ✅ OPTIONAL FIELD 3: Another optional field - MUST be included in mapping
    ...(raw.description && { description: raw.description }),
    
    // ✅ FIELD COUNT VALIDATION: 7 fields mapped = 7 fields in interface ✓
    
    // ❌ INVALID EXAMPLES - DON'T DO THIS:
    // ❌ Skipping optional fields - FORBIDDEN even if they're optional in interface
    // ❌ Adding extra fields not in interface - WILL CAUSE BUILD ERROR
  };
  
  return output;
}
```

**🚨 CRITICAL VALIDATION RULES**:
- **Build Error Prevention**: Mapping non-existent interface fields causes TypeScript compilation errors
- **Runtime Error Prevention**: Mapping non-existent API fields causes undefined value errors  
- **Interface Compliance**: Every mapped field MUST exist in the generated interface
- **API Compliance**: Every mapped field MUST be documented in the api.yml response schema
- **🚨 COMPLETE MAPPING REQUIREMENT**: ALL fields that exist in the interface MUST be mapped - this includes BOTH required AND optional fields (zero missing fields allowed)
- **🚨 NO EXCEPTIONS**: Every single field defined in the interface (required: true, required: false, optional: true) MUST have a corresponding mapping line in the mapper
- **Two-Way Validation**: 
  - Interface → API: Every interface field (required AND optional) must have corresponding API response field
  - API → Interface: Every mapped API field must exist in interface
  - Complete Coverage: Every interface field must be included in mapper output - no fields can be skipped

**🚨 BUILD VALIDATION CHECKPOINT**: After creating data mappers, run `npm run build` to verify mappers compile correctly. Fix any import, type, or enum usage errors before proceeding.

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

**🚨 CRITICAL: NO ENVIRONMENT VARIABLES**:
- **FORBIDDEN**: Producer MUST NOT depend on environment variables (NO `process.env.*` usage)
- **FORBIDDEN**: Producer MUST NOT expect contextual parameters from environment variables
- **MANDATORY**: ALL required parameters (organizationId, tenantId, etc.) MUST come from method arguments as defined in API specification
- **MANDATORY**: If operations require contextual parameters, they MUST be passed as explicit parameters in each method call

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

**🚨 BUILD VALIDATION CHECKPOINT**: After creating each producer implementation, run `npm run build` to verify producer compiles correctly. Fix any interface implementation errors before proceeding to the next producer.

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

**🚨 BUILD VALIDATION CHECKPOINT**: After creating main connector implementation, run `npm run build` to verify connector compiles correctly. Fix any interface implementation or dependency injection errors before proceeding.

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

**🚨 BUILD VALIDATION CHECKPOINT**: After updating module entry point, run `npm run build` to verify all exports resolve correctly. Fix any export/import errors before proceeding to final validation.

### 9. Final Implementation Validation

**🚨 CRITICAL: FINAL BUILD GATE - MUST PASS TO COMPLETE TASK**

This is the final and most critical validation step. The implementation is NOT complete until `npm run build` passes with exit code 0.

**Build Validation (MANDATORY)**:
- **🚨 CRITICAL**: Run `npm run build` to verify complete TypeScript compilation
- **Exit Code 0 Required**: Build must exit with code 0 - any other exit code means task failure
- **No Warnings Allowed**: Resolve any TypeScript warnings or errors
- **Fix all issues**:
  - Type errors or import issues
  - Missing interface implementations  
  - Incorrect dependency imports
  - Export/import mismatches
- **Task Failure Condition**: If `npm run build` fails, the entire task has failed and must be restarted

**🚨 CRITICAL RULE**: This task cannot be marked as completed unless `npm run build` exits with code 0. No exceptions.

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

**🚨 MANDATORY BUILD SUCCESS CRITERIA (TASK FAILURE IF NOT MET)**:

- **`npm run build` exits with code 0** - This is the primary success criteria
- **Zero TypeScript compilation errors** - Any compilation error means task failure
- **Zero TypeScript warnings** - All warnings must be resolved

**Implementation Success Criteria**:
- All producer interface methods are implemented  
- Error handling covers all API response codes using core error types
- Data mappers handle all required fields using mandatory patterns  
- Client provides HTTP client access method
- Standard `isSupported()` and `metadata()` methods implemented with required patterns
- All generated interfaces properly implemented

**🚨 BUILD GATE ENFORCEMENT**: Task cannot proceed to completion or be marked as successful unless `npm run build` passes with exit code 0.

## Output Format

Store in `.claude/.localmemory/{action}-{module-identifier}/task-06-output.json`:

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
        "check": "🚨 CRITICAL: TypeScript compilation successful",
        "command": "npm run build",
        "expected_exit_code": 0,
        "status": "passed|failed",
        "critical": true,
        "notes": "Task fails if this validation does not pass"
      },
      {
        "check": "Linting passes",
        "command": "npm run lint", 
        "expected_exit_code": 0,
        "status": "passed|failed",
        "critical": false
      }
    ],
    "buildGateValidation": {
      "finalBuildCheck": {
        "command": "npm run build", 
        "expected_exit_code": 0,
        "actual_exit_code": "${actual_exit_code}",
        "status": "passed|failed",
        "timestamp": "${iso_timestamp}",
        "notes": "Final build gate - task completion depends on this check"
      }
    }
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
10. **🚨 FIELD MAPPING VALIDATION**: Map ALL fields from interfaces (required AND optional) that exist in api.yml response schemas - complete coverage required, zero missing fields, no extra fields allowed

**🚨 CRITICAL BUILD GATE VALIDATION CHECKLIST:**

**Pre-Implementation (MANDATORY)**:
- [ ] **CRITICAL**: Initial `npm run build` passes with exit code 0
- [ ] Generated code import issues fixed  
- [ ] Required dependencies installed and build-compatible
- [ ] TypeScript config includes `"skipLibCheck": true`

**Implementation Checkpoints (BUILD REQUIRED AFTER EACH)**:
- [ ] **BUILD CHECKPOINT**: After HTTP Client creation - `npm run build` passes
- [ ] **BUILD CHECKPOINT**: After Mappers creation - `npm run build` passes  
- [ ] **BUILD CHECKPOINT**: After each Producer creation - `npm run build` passes
- [ ] **BUILD CHECKPOINT**: After Connector creation - `npm run build` passes
- [ ] **BUILD CHECKPOINT**: After entry point update - `npm run build` passes

**Final Validation (TASK COMPLETION GATE)**:
- [ ] **🚨 FINAL BUILD GATE**: `npm run build` exits with code 0 - TASK FAILS IF NOT MET
- [ ] **MANDATORY**: `npm run lint:src -- --fix` executed after implementation
- [ ] Zero TypeScript warnings remaining

**Implementation Quality Checks**:
- [ ] **CRITICAL**: All error handling uses core errors - NO generic `Error` class used
- [ ] Core errors imported from `@auditmation/types-core-js`
- [ ] Client provides HTTP client access method
- [ ] Producers use mandatory PagedResults pattern with `pageToken` (not `nextToken`)
- [ ] Mappers use object literals with utility functions
- [ ] **🚨 CRITICAL**: ALL interface fields are mapped in every mapper function - this includes BOTH required AND optional fields (zero exceptions)
- [ ] **🚨 CRITICAL**: ONLY fields that exist in both interface AND api.yml are mapped - no extra fields
- [ ] **🚨 FIELD COUNT VALIDATION**: Number of mapped fields equals total number of interface fields (required + optional)
- [ ] Field mapping validation completed against generated interfaces and api.yml schemas
- [ ] Standard `isSupported()` and `metadata()` methods implemented
- [ ] Error handling maps HTTP status codes to appropriate core error types
- [ ] Implementation follows architectural separation

**🚨 ABSOLUTE RULE**: Task cannot be completed or marked successful unless the final `npm run build` command exits with code 0. If build fails, all work must stop and build issues must be resolved before task completion.
