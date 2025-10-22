# Operation Engineer Workflow

## Overview

This workflow covers implementing Producer classes that contain all business logic for API operations. Each producer handles one resource type (User, Webhook, Repository, etc.).

## Input

- Generated types (from generated/api/ and generated/model/)
- HTTP client instance (from Client)
- API spec (for understanding operations)
- Mapper functions (from Mappers.ts)

## Output

- Producer class files (src/{Resource}Producer.ts)
- CRUD operations implemented
- Error handling via core types
- Integration with mappers

## Key Principles

- **ALL business logic in producers** - Client only handles connection
- **NO connection context parameters** - No apiKey, token, baseUrl, orgId in method signatures
- **Use generated types** - Never Promise<any>
- **Call mappers** for all conversions
- **Throw core errors** - Never generic Error

## Detailed Steps

### Step 1: Create Producer File

- Navigate to module src directory
- Create {Resource}Producer.ts file
- Follow naming convention: {Resource}Producer (PascalCase)

### Step 2: Import Dependencies

Import required modules:
- AxiosInstance from axios
- Generated types from ../generated/model/
- Mapper functions from ./Mappers
- Core error types from @auditmation/types-core-js
- Utility functions (handleAxiosError) if available

**Import patterns:** See "Producer Pattern" in @.claude/rules/implementation.md

### Step 3: Create Producer Class Structure

- Create producer class with constructor accepting AxiosInstance
- Store HTTP client in private field
- Define method signatures for CRUD operations:
  - list() → Promise<Resource[]>
  - get(id) → Promise<Resource>
  - create(input) → Promise<Resource>
  - update(id, input) → Promise<Resource>
  - delete(id) → Promise<void>
- **Class structure:** See "Producer Pattern" in @.claude/rules/implementation.md

### Step 4: Implement List Operation

- Accept business parameters only (no connection context)
- Validate required inputs (throw InvalidInputError if missing)
- Make HTTP GET request to list endpoint
- Handle errors via catch(handleAxiosError)
- Convert response via array mapper
- Return typed array
- **List pattern:** See "List Operation" in @.claude/rules/http-client-patterns.md

### Step 5: Implement Get Operation

- Accept resource identifier parameter
- Make HTTP GET request to resource endpoint
- Handle errors via catch(handleAxiosError)
- Convert response via single object mapper
- Check if resource exists (throw ResourceNotFoundError if not)
- Return typed object
- **Get pattern:** See "Get Operation" in @.claude/rules/http-client-patterns.md

### Step 6: Implement Create Operation

- Accept input parameters (business data only)
- Validate input (throw InvalidInputError if invalid)
- Make HTTP POST request with request body
- Handle errors via catch(handleAxiosError)
- Convert response via mapper
- Return created resource
- **Create pattern:** See "Create Operation" in @.claude/rules/http-client-patterns.md

### Step 7: Implement Update Operation

- Accept resource identifier and update data
- Validate inputs
- Make HTTP PUT or PATCH request with update body
- Handle errors via catch(handleAxiosError)
- Convert response via mapper
- Return updated resource
- **Update pattern:** See "Update Operation" in @.claude/rules/http-client-patterns.md

### Step 8: Implement Delete Operation

- Accept resource identifier parameter
- Make HTTP DELETE request
- Handle errors via catch(handleAxiosError)
- Return Promise<void>
- **Delete pattern:** See "Delete Operation" in @.claude/rules/http-client-patterns.md

### Step 9: Validate Implementation

Run validation checks:
- Verify no Promise<any> in signatures (forbidden)
- Confirm using generated types
- Check no connection context in parameters (apiKey, token, etc.)
- Verify calling mappers for all conversions
- Ensure using core errors only (not generic Error)

**Validation scripts:** See "Producer Validation" in @.claude/rules/implementation.md

### Step 10: Build and Test

- Run npm run build (must pass)
- Run npm run test:unit (unit tests must pass)
- Fix any compilation or test errors

## Common Patterns

All patterns with code examples in @.claude/rules/implementation.md and @.claude/rules/http-client-patterns.md:

**Input Validation:**
- Always validate required business parameters
- Throw InvalidInputError for missing/invalid inputs

**Response Handling:**
- Always use mappers for conversion
- Never return raw API responses

**Error Propagation:**
- Let errors propagate via catch(handleAxiosError)
- Don't swallow errors
- Convert all errors to core types

## Anti-Patterns to Avoid

**❌ Connection Context Parameters:**
- DO NOT pass apiKey, token, baseUrl, orgId in method signatures
- Client already has these - producer shouldn't need them
- **Why:** See "No Connection Context" rule in @.claude/rules/implementation.md

**❌ Using Promise<any>:**
- DO NOT use Promise<any> in return types
- Always use generated types (Promise<Resource>, Promise<Resource[]>, etc.)
- **Why:** See "No Promise<any>" in @.claude/rules/failure-conditions.md

**❌ Not Using Mappers:**
- DO NOT return raw response.data
- Always call mapper functions for conversion
- **Why:** See "Mapper Usage" in @.claude/rules/implementation.md

## Success Criteria

Producer implementation is complete when:

- ✅ All operations in producers (not client)
- ✅ No Promise<any> signatures
- ✅ Generated types used throughout
- ✅ Mappers called for all conversions
- ✅ Core errors thrown (not generic Error)
- ✅ No connection context in parameters
- ✅ Clean, testable code
- ✅ Build passes
- ✅ Unit tests pass

## Related Documentation

**Primary Rules:**
- @.claude/rules/implementation.md - ALL producer patterns, validation, anti-patterns
- @.claude/rules/http-client-patterns.md - HTTP operation patterns (GET, POST, PUT, DELETE)

**Supporting Rules:**
- @.claude/rules/error-handling.md - Core error usage and error mapping
- @.claude/rules/mapper-patterns.md - How to call mappers correctly
- @.claude/rules/failure-conditions.md - What causes failures (Promise<any>, etc.)
