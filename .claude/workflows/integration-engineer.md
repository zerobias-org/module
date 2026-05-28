# Integration Engineer Workflow

## Overview

This workflow covers implementing HTTP client classes and API integration for a module. The integration engineer implements the communication layer between the module and the external API.

## Input

- API specification (api.yml)
- Connection profile schema (connectionProfile.yml)
- Generated TypeScript types (generated/model/)
- Operation definitions from api.yml

## Output

- Client class implementation (src/{Service}Client.ts)
- Producer class implementations (src/producers/*.ts)
- Error handling configured
- Integration tests passing

## Detailed Steps

### Step 1: Review API Specification

- Read api.yml from module directory
- Identify base URL/endpoint
- Determine authentication method
- List available operations (paths)
- Note request/response formats
- Document error responses
- **Record findings** in .claude/.localmemory/{workflow}-{module}/integration-notes.md

### Step 2: Review Connection Profile

- Read connectionProfile.yml from module directory
- Identify where auth token is stored
- Note connection parameters
- Identify required vs optional fields
- **Connection patterns:** See "Core Profile Usage" in @.claude/skills/connection-profile/SKILL.md

### Step 3: Implement Client Class

**Create or edit client file:** src/{Service}Client.ts

**Implement three core methods:**

1. **connect()** - Set up HTTP client
   - Create axios instance
   - Configure base URL from profile
   - Store credentials from profile
   - Set up interceptors
   - Validate connection with real API call
   - Return Promise<void> (NOT ConnectionState)

2. **isConnected()** - Verify connection
   - Make real API call (lightweight endpoint)
   - Return boolean
   - NOT just checking stored state

3. **disconnect()** - Clean up
   - Clear HTTP client reference
   - Clear credentials
   - Clear any cached data

**Add helper methods:**
- HTTP methods: get(path), post(path, data), put(path, data), delete(path)
- Error handler: handleApiError(error) - Map HTTP errors to core types

**Patterns:** See "Client Implementation" in @.claude/skills/http-client/SKILL.md

### Step 4: Implement Error Handling

**Map HTTP status codes to core error types:**
- 401 → InvalidCredentialsError
- 403 → UnauthorizedError
- 404 → NoSuchObjectError
- 429 → RateLimitExceededError
- 5xx → ServiceUnavailableError
- Other → UnexpectedError

**Configure response interceptor:**
- Intercept all errors
- Call handleApiError()
- NEVER let generic Error escape

**Error mapping:** See "Error Mapping" in @.claude/skills/error-handling/SKILL.md
**Interceptor setup:** See "Response Interceptors" in @.claude/skills/http-client/SKILL.md

### Step 5: Implement Producer Classes

**For each resource type (User, Webhook, etc.):**

1. **Create producer file:** src/producers/{Resource}Producer.ts

2. **Implement CRUD operations:**
   - list() → Promise<Resource[]>
   - get(id) → Promise<Resource>
   - create(data) → Promise<Resource>
   - update(id, data) → Promise<Resource>
   - delete(id) → Promise<void>

3. **Validate responses:**
   - Check array format before mapping
   - Check object format before mapping
   - Throw appropriate errors for invalid formats

4. **Map responses:**
   - Call mapper functions for single objects
   - Use Array.map() for collections
   - Handle nested objects

**Producer patterns:** See "Producer Implementation" in @.claude/skills/http-client/SKILL.md
**Validation:** See "Response Validation" in @.claude/skills/implementation-core/SKILL.md

### Step 6: Configure Timeouts and Retries

**Set appropriate timeouts:**
- Default: 30 seconds
- Adjust per operation if needed based on API characteristics

**Add retry logic:**
- Only for transient failures (5xx, network errors)
- NEVER retry client errors (4xx)
- Use exponential backoff
- Limit to 3 attempts

**Retry patterns:** See "Retry Logic" in @.claude/skills/http-client/SKILL.md

### Step 7: Validate Implementation

**Run validation checks:**
- Verify error handling uses only core error types (no generic Error)
- Check no Promise<any> usage (forbidden)
- Confirm disconnect() method exists and returns Promise<void>
- Verify response validation before mapping (Array.isArray checks)

**Validation scripts:** See "Integration Validation" in @.claude/skills/http-client/SKILL.md

### Step 8: Run Unit Tests

**Execute unit tests:** zbb test --slot local

**Verify:**
- All HTTP calls mocked with nock
- Error scenarios covered
- Response validation tested
- Connection lifecycle tested

**If failures:**
- Review test output for specific failures
- Fix implementation issues
- Re-run tests until passing

**Testing patterns:** See @.claude/skills/nock-mocking/SKILL.md

### Step 9: Run Integration Tests

**Execute e2e tests:** zbb testDirect --slot local

**Verify:**
- Real API calls succeed
- Authentication works correctly
- Error handling works with real API errors
- Connection management works end-to-end

**If failures:**
- Check credentials in .credentials.json
- Review API endpoint configuration
- Check network connectivity
- Fix issues and re-run

**Integration testing:** See @.claude/skills/gate-unit-tests/SKILL.md

### Step 10: Document Decisions

**Record technical decisions in localmemory:**

Create .claude/.localmemory/{workflow}-{module}/integration-decisions.md with:
- HTTP client library choice and reasoning
- Timeout configuration and justification
- Retry strategy details
- Any deviations from standard patterns
- Confidence levels for decisions

## Common Issues

**Issue 1: Generic errors being thrown**
- Symptom: throw new Error() used instead of core types
- Fix: Map all errors to appropriate core error types
- Reference: @.claude/skills/error-handling/SKILL.md

**Issue 2: No response validation**
- Symptom: Mapping responses without checking format
- Fix: Validate Array.isArray() or object type before mapping
- Reference: @.claude/skills/implementation-core/SKILL.md

**Issue 3: State-based connection check**
- Symptom: isConnected() just checks if token exists
- Fix: Make real API call to verify connection
- Reference: @.claude/skills/http-client/SKILL.md

## Success Criteria

Integration implementation is complete when:

- ✅ Client class implements connect/isConnected/disconnect
- ✅ All errors map to core types (ZERO generic errors)
- ✅ Producer classes implement all operations
- ✅ Response validation before mapping
- ✅ Appropriate timeouts configured
- ✅ Retry logic for transient failures
- ✅ Unit tests pass (with nock mocks)
- ✅ Integration tests pass (real API calls)
- ✅ No connection leaks
- ✅ No credentials in error messages

## Tools Used

- HTTP client library (axios)
- nock (for mocking in unit tests)
- Core error types from @zerobias-org/types-core-js
- Mapper functions from Mappers.ts

## Related Documentation

**Primary Rules:**
- @.claude/skills/http-client/SKILL.md - All client and producer code patterns
- @.claude/skills/error-handling/SKILL.md - Error mapping and core error usage
- @.claude/skills/implementation-core/SKILL.md - General implementation rules

**Supporting Rules:**
- @.claude/skills/nock-mocking/SKILL.md - Unit testing with mocks
- @.claude/skills/gate-unit-tests/SKILL.md - Integration testing
- @.claude/skills/connection-profile/SKILL.md - Profile patterns
