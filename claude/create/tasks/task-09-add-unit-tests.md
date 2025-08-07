# Task 09: Add and Run Unit Tests

## Overview

Create comprehensive unit tests using HTTP-level mocking with nock. These tests validate complete request/response flows while maintaining fast execution. The task supports two approaches: recording real API traffic when credentials are available, or creating fixtures from original API specifications when credentials are not available.

## 🚨 Critical Rules

- **USE HTTP-level mocking only** - Mock with nock, never mock internal library methods
- **VERIFY request parameters** - Ensure module sends correct data to API endpoints
- **TEST error scenarios** - Include tests for 401, 403, 404, 500, and network failures
- **SANITIZE sensitive data** - Remove credentials and PII from any recorded responses
- **ALL TESTS MUST PASS** - 100% pass rate required for task completion
- **NEVER modify generated files** - Only edit source and test files, not `/generated/` directory
- **USE original API specifications** - Reference Task 01/02 outputs for API schemas when credentials unavailable

## Input Requirements

### Essential
- Task 01 output: `.claude/.localmemory/{action}-{module-identifier}/task-01-output.json` (original API specs)
- Task 02 output: `.claude/.localmemory/{action}-{module-identifier}/task-02-output.json` (operation mapping)  
- Task 07 output: `.claude/.localmemory/{action}-{module-identifier}/task-07-output.json` (implementation details)
- `module_path` - Path to the module directory 
- `service_name` - Service name in PascalCase (from generated interfaces)
- `test_api_credentials` - Whether test API credentials are available (true/false)

### Optional  
- `test_coverage_threshold` - Minimum test coverage percentage (default: 80)
- `create_test_env` - Whether to create environment template (default: true)

## Expected Outputs

### Files Created
- `test/unit/${service_name}ClientTest.ts` - Client class unit tests with nock mocking
- `test/unit/${service_name}ImplTest.ts` - Main implementation class unit tests
- `test/unit/MappersTest.ts` - Data mapping function tests
- `test/unit/*ProducerTest.ts` - Producer class unit tests (one per producer)
- `test/utils/nock-helpers.ts` - Nock utility functions for reusable mocks
- `test/utils/recording-helpers.ts` - Recording utilities for API traffic capture
- `test/utils/data-sanitizer.ts` - Data sanitization for recorded responses
- `test/fixtures/*.json` - Test fixtures and mock response data
- `.env.example` - Environment variables template for integration testing

### Memory Output
- `.claude/.localmemory/{action}-{module-identifier}/task-09-output.json` - Task completion status and results

## Implementation

### Step 1: Setup Test Environment

**Install Dependencies:**
- Install nock and TypeScript types for HTTP mocking
- Create test directory structure: `test/unit`, `test/utils`, `test/fixtures`
- Set up subdirectories for different fixture types (recorded, sanitized, templates)

**Create Base Configuration:**
- Configure package.json test scripts for unit tests and recording mode
- Set up TypeScript compilation for test files
- Ensure test framework (Mocha) and assertions (Chai) are configured

### Step 2: Create Test Utilities

**Nock Helper Functions (`test/utils/nock-helpers.ts`):**
Create reusable functions for common testing patterns:
- `mockAuthenticatedRequest()` - Handle API authentication headers
- `mockPaginatedResponse()` - Mock paginated API responses  
- `mockErrorResponse()` - Mock HTTP error responses (401, 403, 404, etc.)
- `loadFixture()` - Load test data from JSON fixture files
- `setupNockFromFixture()` - Automatically configure nock from recorded fixtures

**Recording Utilities (when credentials available):**
- `RecordingManager` class to capture real API traffic during integration tests
- Automatic recording of HTTP requests/responses with nock.recorder
- Immediate sanitization of recorded data to remove sensitive information

**Data Sanitization:**
- Remove authentication tokens, API keys, and personal data from recordings
- Replace email addresses with test@example.com  
- Replace sensitive identifiers with test placeholders
- Preserve response structure while cleaning sensitive content

### Step 3: Create Unit Test Files

**Client Unit Tests (`test/unit/${service_name}ClientTest.ts`):**
Create comprehensive tests for the client class covering:
- **Constructor and initialization** - Verify client instance creation
- **Connection management** - Test connect(), isConnected(), disconnect() methods  
- **Authentication handling** - Verify correct headers are sent with requests
- **Error mapping** - Test conversion of HTTP status codes to proper error types:
  - 401 → InvalidCredentialsError
  - 403 → UnauthorizedError  
  - 404 → NoSuchObjectError
  - 500 → Internal server errors
- **Request verification** - Use nock to ensure requests have correct parameters and headers

**Implementation Unit Tests (`test/unit/${service_name}ImplTest.ts`):**
Create tests for the main implementation class covering:
- **Constructor and client initialization** - Verify proper setup of internal client
- **Connection lifecycle** - Test connect(), isConnected(), disconnect() delegation to client
- **Module metadata** - Test metadata() method returns proper module information
- **Producer getters** - Test that producer properties return correct instances
- **Error propagation** - Verify errors from client are properly handled

**Mapper Unit Tests (`test/unit/MappersTest.ts`):**
Create tests for data transformation functions covering:
- **Field mapping accuracy** - Verify raw API data is correctly transformed
- **Date parsing** - Test conversion of string dates to Date objects
- **Optional field handling** - Test behavior with missing or null fields  
- **Data type validation** - Ensure mapped objects have correct TypeScript types
- **Edge cases** - Test with malformed data, empty objects, invalid dates

### Step 4: Create Test Fixtures Based on API Specifications

**Determine Fixture Source:**
- **If credentials available:** Record real API responses and sanitize them
- **If no credentials:** Create fixtures from Task 01/02 API specifications

**Fixture Types to Create:**
- **Success responses** - Valid API responses for each operation from Task 02 mapping
- **Error responses** - Standard HTTP errors (401, 403, 404, 500) with proper structure
- **Edge cases** - Empty lists, null values, large datasets
- **Authentication samples** - Valid and invalid credential response examples

**Reference Original API Specifications:**
- Load Task 01 output to get original API documentation URLs and schemas
- Load Task 02 output to get mapped operations and expected response formats  
- Use these as the source of truth for response structure when creating manual fixtures

### Step 5: Create Manual Test Fixtures (No Credentials Fallback)

**When credentials are not available:**

**Load Original API Specifications:**
- Read Task 01 output to get original API documentation sources and base URLs
- Read Task 02 output to get the complete operation mapping and expected response formats
- Use original API documentation (not just api.yml) to understand response structures

**Create Template Fixtures:**
- Generate fixtures in `test/fixtures/templates/` based on original API specs
- Create one fixture per operation identified in Task 02 output
- Include both success and error response examples
- Match the exact response structure documented in the original API specifications

**Producer Unit Tests with Manual Fixtures:**
Create producer test files based on the operations mapped in Task 02:
- Load appropriate fixtures for each test case
- Mock the exact endpoints and parameters identified during API analysis
- Test both successful responses and error conditions
- Verify request parameters match what was documented in Task 02

### Step 6: Set Up Recording Workflow (If Credentials Available)

**When credentials are available:**

**Create Integration Recording Tests:**
- Create `test/integration/${service_name}RecordingTest.ts` for capturing real API responses
- Set up tests that call actual API endpoints using the operations from Task 02
- Use RecordingManager to automatically capture and sanitize HTTP traffic
- Create one recording test per major operation (connection, list, get, etc.)

**Recording Process:**
- Run tests with `RECORD_MODE=true` environment variable
- Capture all HTTP requests/responses during test execution
- Automatically sanitize sensitive data (tokens, emails, personal info)
- Save both raw recordings and sanitized fixtures
- Generate unit test fixtures from recorded data

### Step 7: Create Environment Template

Create `.env.example` with authentication variables based on the connection profile from Task 02:
- Include all authentication methods discovered during API analysis
- Add base URL and API version information from original specifications
- Include test configuration variables (timeouts, log levels)
- Add recording mode configuration for capturing API traffic

### Step 8: Configure Test Scripts

Add test execution scripts to package.json:
- `test:unit` - Run unit tests with nock mocking
- `test:integration:record` - Run integration tests in recording mode  
- `test:fixtures:clean` - Clean old recorded fixtures

### Step 9: Execute and Validate Tests

**Run Unit Tests:**
Execute all unit tests and verify 100% pass rate. For any failures:
- Fix implementation bugs in source code (`src/` directory)
- Update test expectations to match actual behavior
- Never modify generated files - focus on implementation and test fixes

**Record API Traffic (if credentials available):**
- Clean old recordings and capture fresh API interactions
- Review sanitized fixtures for accuracy and completeness
- Verify sensitive data has been properly removed

**Generate Test Report:**
Create comprehensive summary showing:
- Test files created and their coverage areas
- Test execution results and pass/fail status
- Fixture files generated and their sources
- Next steps for ongoing test maintenance

## Success Criteria

- [ ] Nock dependencies installed successfully
- [ ] Unit test files created for Client, Impl, and Mappers classes
- [ ] Test utility files created with reusable nock helpers
- [ ] Recording and sanitization utilities created 
- [ ] Basic test fixtures created with realistic data
- [ ] Integration recording tests created (if credentials available)
- [ ] Environment template created with comprehensive variable list
- [ ] **ALL UNIT TESTS PASS (100% pass rate required)**
- [ ] Package.json scripts added for test execution and recording
- [ ] Test report generated with execution summary

## Testing Philosophy: Unit-Style Integration Tests

The tests created are **unit-style integration tests** that:
- Mock at the **HTTP level using nock** (not library internals)
- Test the **complete request/response flow** from module to API  
- Verify **correct parameters are passed** from module to API
- Test **error handling at HTTP level** (401, 403, 404, 500, etc.)
- Validate **data transformation and mapping**
- Maintain **fast, reliable execution** without real network calls

## Testing Workflow Options

### **Option A: Recording-First (When Credentials Available) - PREFERRED**

1. **Write integration tests** that call real API endpoints
2. **Run with RECORD_MODE=true** to capture actual HTTP traffic
3. **Sanitize recorded data** to remove sensitive information  
4. **Generate unit tests** using the accurate recorded responses
5. **Result**: Unit tests with 100% accurate API response structure

### **Option B: Documentation-First (No Credentials Available) - FALLBACK**

1. **Analyze OpenAPI specification** (`api.yml`) for response schemas
2. **Extract example responses** from API documentation
3. **Create manual test fixtures** based on schema definitions
4. **Generate unit tests** using documentation-based fixtures  
5. **Result**: Unit tests with API-spec-compliant response structure

### **Credential Detection Logic**

```bash
# Task automatically detects credential availability
if [ -f .env ] && [ "${test_api_credentials}" = "true" ]; then
  # OPTION A: Recording-first workflow  
  echo "✓ Using recording workflow with real API calls"
  npm run test:integration:record
else
  # OPTION B: Documentation-first workflow
  echo "⚠ No credentials - creating fixtures from API documentation"
  # Creates manual fixtures from api.yml examples
fi
```

### **Manual Fixture Creation Process**

When credentials are not available:

1. **Parse OpenAPI Spec**: Extract response examples from `api.yml`
2. **Generate Realistic Data**: Create test data that matches schema requirements
3. **Include Edge Cases**: Add fixtures for error responses, empty lists, etc.
4. **Maintain Type Safety**: Ensure fixtures match generated TypeScript interfaces

**Example OpenAPI → Manual Fixture:**
```yaml
# In api.yml
components:
  schemas:
    User:
      type: object
      properties:
        id: { type: string }
        name: { type: string }
        email: { type: string, format: email }
      example:
        id: "user-123"
        name: "John Doe"  
        email: "john@example.com"
```

**Becomes:**
```json
// test/fixtures/templates/user-example.json
{
  "id": "user-123",
  "name": "John Doe",
  "email": "john@example.com",
  "created_at": "2023-01-01T00:00:00Z",
  "active": true
}
```

## Error Handling

### Common Issues and Solutions
- **Test compilation errors**: Check imports and generated interface names
- **Nock setup issues**: Verify URL matching and header expectations
- **Test execution failures**: Debug async/await and promise handling
- **Missing dependencies**: Install required test packages
- **Authentication failures**: Check credential configuration and headers

### Troubleshooting Guidelines
- Focus on **test and implementation fixes**, not generated code changes
- Use nock's debugging features: `nock.recorder.rec()` for debugging
- Check that mock URLs exactly match client configuration
- Verify request headers and parameters in failing tests
- **STOP and inform user** if API model changes are needed

---

**Human Review Required**: After unit tests are created, verify that all tests pass and provide comprehensive coverage of the module's functionality. Check that sensitive data is properly sanitized in any recorded fixtures.