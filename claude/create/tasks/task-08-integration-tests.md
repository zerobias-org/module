# Task 8: Add and Run Integration Tests

**Task ID**: `testing-add-integration-tests`  
**Step**: 8  
**Category**: Testing & Validation  

## Overview

Create comprehensive integration tests that validate the module's ability to connect and interact with the real API. These tests are always created but only run when test credentials are provided. When credentials are available and tests run, they must pass with full permissions assumed.

## 🚨 Critical Rules

- **ALWAYS CREATE TESTS** - Integration tests are always created, regardless of credential availability
- **🚨 MANDATORY TESTING WHEN CREDENTIALS PROVIDED** - When API keys/credentials are provided, integration tests MUST be run and MUST pass - never skip integration tests when having credentials
- **🚨 LOCATE AND USE CREDENTIALS** - Find where credentials are stored (check .env files, environment variables) and use them for testing
- **🚨 CREDENTIAL SECURITY** - NEVER add credentials to any code that could be committed - only use .env files (which should be in .gitignore)
- **ASSUME FULL PERMISSIONS** - When tests run, assume a token/credentials with all necessary permissions are provided
- **NO PERMISSION-BASED SKIPPING** - When credentials are available, do not skip tests due to lack of permissions; assume access is available
- **CREDENTIAL-BASED EXECUTION** - Tests only run when credentials are provided; skip gracefully when missing
- **ALWAYS sanitize sensitive data** - Remove real tokens, emails, and personal information from test outputs
- **MUST use realistic test scenarios** - Test actual API endpoints and common use cases
- **NEVER commit real credentials** - Use `.env.example` for templates only
- **ALL TESTS MUST PASS** - Integration tests must achieve 100% pass rate before task completion
- **NEVER modify generated files** - Do not edit any files in `/generated/` directory
- **NEVER modify api.yml** - API specification is immutable during integration testing
- **REQUEST USER ACTION for model changes** - If API model changes are needed, inform user and stop

## Required Inputs

### Essential
- `module_path` - Path to the module directory (e.g., `package/vendor/service`)
- `service_name` - Service name in PascalCase (e.g., `ServiceName`)
- `test_credentials_available` - Whether test API credentials are provided (true/false)

### Optional
- `env_file_location` - Location of environment file with credentials (default: `.env`)
- `integration_timeout` - Test timeout in milliseconds (default: 30000)

## Expected Outputs

### Files Created
- `test/integration/ConnectionTest.ts` - Connection establishment and authentication tests
- `test/integration/Common.ts` - Shared test utilities and `prepareApi()` function
- `test/integration/<ProducerName>Test.ts` - One test file per producer (e.g., `UserProducerTest.ts`, `OrganizationProducerTest.ts`)
- `test/utils/integration-helpers.ts` - Integration test utilities
- `.env.example` - Environment variable template
- `test/fixtures/integration/` - Real API response examples (sanitized)

**🚨 CRITICAL: Core Type Imports Required**:
All test files must import core types for assertions:
```typescript
// Core types for assertions
import { Email, URL, UUID, IpAddress } from '@auditmation/types-core-js';
// Vendor-specific types when applicable
import { Arn, AwsService } from '@auditmation/types-amazon-js';  // For AWS modules
import { AzureVmSize } from '@auditmation/types-microsoft-js';   // For Azure modules
import { GcpAccessPolicy } from '@auditmation/types-google-js';  // For GCP modules
```

### Memory Output
- `.claude/.localmemory/{action}-{module-identifier}/task-08-output.json` - Task completion status and results

### Test Coverage Areas
- **Connection tests**: Authentication and connection validation  
- **Producer tests**: Core API operations per producer (list, get, create, update, delete as applicable)
- **Common utilities**: Shared credential loading and module initialization
- **Error handling**: Real API error responses
- **Pagination and filtering**: If supported by the API
- **Rate limiting**: Retry behavior and timeout handling

## Implementation

### Step 1: Detect and Locate Credentials

**🚨 CRITICAL FIRST STEP**: Before creating tests, check for existing credentials in authorized locations only:

**Credential Search Locations (ONLY these):**
1. **Initial user prompt** - Check if API keys/tokens were provided in the original request
2. **Module directory** - Check for .env files in `${module_path}/.env*`
3. **Repository root** - Check for .env files in repository root directory only

```bash
cd ${module_path}
# Check module directory for .env files
ls -la .env* 2>/dev/null || echo "No .env files found in module"
# Check repository root for .env files  
ls -la ../../.env* 2>/dev/null || echo "No .env files found in repo root"
```

**🚨 NEVER search credentials in:**
- Other modules or directories outside current module
- External files not specified in the prompt
- System-wide configuration files
- Other local files beyond module/repo root scope

**If credentials are found:**
- Note their location and variable names
- Ensure tests use these credentials
- **MANDATORY**: Run full integration test suite

**If no credentials found:**
- Create tests that skip gracefully when credentials missing
- Provide clear instructions on how to add credentials

### Step 2: Create Integration Test Structure

```bash
cd ${module_path}
mkdir -p test/integration test/fixtures/integration test/utils
```

### Step 3: Install Required Dependencies

```bash
npm install --save-dev dotenv @types/dotenv
```

### Step 4: Create Integration Test Suites

Create the test file structure:
- **Common.ts**: Credential loading, `prepareApi()` function, shared utilities
- **ConnectionTest.ts**: Authentication and connection validation tests
- **Producer tests**: One file per producer class for API operations
- Load credentials from environment variables
- Test real API connections and operations for each producer
- Validate response formats and data types
- Handle authentication errors gracefully
- Sanitize and save response examples

### Step 4.1: Add Debug Logging to Integration Tests

**IMPORTANT**: Add debug logging to all integration tests to capture both method calls and results:

1. **Import logger**: Add `import { getLogger } from '@auditmation/util-logger';` to each test file
2. **Initialize logger**: Add `const logger = getLogger('console', {}, process.env.LOG_LEVEL || 'info');` 
3. **Log method call and result**: After each module operation call, add:
   ```typescript
   const result = await userApi.get('testuser');
   logger.debug(`userApi.get('testuser')`, JSON.stringify(result, null, 2));
   ```

**Format**: `logger.debug('methodName(parameters)', JSON.stringify(result, null, 2))`

**Examples of exact logging code**:
```typescript
// Resource operations (adapt to your module's resources)
const resource = await resourceApi.get('resource-id');
logger.debug(`resourceApi.get('resource-id')`, JSON.stringify(resource, null, 2));

const resources = await resourceApi.list(1, 10);
logger.debug(`resourceApi.list(1, 10)`, JSON.stringify(resources, null, 2));

// Entity operations
const entity = await entityApi.get('entity-name');
logger.debug(`entityApi.get('entity-name')`, JSON.stringify(entity, null, 2));

// Collection operations  
const collection = await collectionApi.get('collection-id');
logger.debug(`collectionApi.get('collection-id')`, JSON.stringify(collection, null, 2));

const items = await collectionApi.list(1, 5);
logger.debug(`collectionApi.list(1, 5)`, JSON.stringify(items, null, 2));
```

**Usage**: Enable debug output by running tests with `LOG_LEVEL=debug npm run test:integration`

This format shows exactly what method was called with what parameters and the complete result returned.

### Step 5: Create Environment Template

Generate `.env.example` with placeholders for:
- API keys, tokens, or credentials
- Base URLs and endpoints
- Test-specific configuration
- Timeout and retry settings

### Step 6: Create Integration Utilities

Build helper functions for:
- Environment variable validation
- Response data sanitization
- Test fixture generation
- Connection setup and teardown

### Step 7: Run Integration Tests (If Credentials Available)

Tests are designed to skip gracefully when credentials are missing, but run completely when available:

```bash
# Tests will check for credentials and skip if not available
npm run test:integration
```

**Test Behavior**:
- **With credentials**: All tests run and must pass with full permissions
- **Without credentials**: Tests skip gracefully with informative messages
- **Partial credentials**: Tests assume full permissions for provided credentials

### Step 8: Interpret Results and Fix Issues

Analyze test results and fix any failures following these rules:

**Allowed Fixes:**
- **Test code issues**: Debug and fix problems in test files
- **Implementation errors**: Fix bugs in module source code (src/ directory)
- **Authentication issues**: Verify credentials and permissions
- **Network issues**: Add retry logic or increase timeouts
- **Data mapping errors**: Adjust test expectations to match API reality

**Prohibited Actions:**
- **NEVER modify /generated files** - These are auto-generated from API specifications
- **NEVER modify api.yml** - API specification is source of truth
- **NEVER change model definitions** - Models are generated from OpenAPI spec

**If Model Changes Required:**
1. **STOP task execution**
2. **Inform user** about required API model changes
3. **Request user action** to update api.yml and regenerate
4. **Wait for user** to complete changes before continuing

**Success Requirement:**
- **ALL tests must pass** - 100% pass rate required for task completion

### Step 9: Final Validation Build and Test

After all tests pass and issues are resolved, run the complete build cycle:

```bash
npm run clean && npm run build && npm run test:integration
```

**This command must pass completely** - any failures indicate code issues that need fixing:
- `npm run clean` - Removes all generated and compiled files
- `npm run build` - Regenerates API code and compiles TypeScript
- `npm run test:integration` - Runs integration tests with fresh build

**If this step fails:**
- Review build errors and fix implementation code
- Check for missing dependencies or configuration issues  
- Ensure all generated files compile correctly with current implementation
- Fix any integration test failures that emerge after clean build

**This step validates that:**
- Implementation works with freshly generated API code
- No hidden dependencies on old generated files
- Integration tests pass against clean, rebuilt module
- Module is ready for production use

### Step 10: Generate Task Output

Create task completion record in local memory:
- Document test execution results
- Record created test files and fixtures
- Note any credential requirements or limitations
- Save integration test summary and recommendations
- Confirm final validation build success

## Test Scenarios

### Authentication Testing
- Valid credentials → successful connection
- Connection establishment and API access validation
- Token/credential functionality verification
- Service availability and endpoint accessibility

### Core Operations Testing
- **List operations**: Pagination, filtering, sorting
- **Retrieve operations**: Valid IDs, invalid IDs, not found scenarios
- **Create operations**: Valid data, validation errors, conflicts
- **Update operations**: Partial updates, version conflicts
- **Delete operations**: Successful deletion, already deleted items

### Error Scenarios
- Network connectivity issues
- API rate limiting
- Service unavailable (503)
- Resource not found (404)
- Validation errors (400)
- Malformed request handling

### Data Validation
- Response schema compliance
- Data type accuracy
- Required vs optional fields
- **🚨 CRITICAL: Core Type Assertions** (see [Core Type Mapping Guide](core-type-mapping-guide.md))
  - Assert `instanceof` for core types: `expect(user.email).to.be.instanceof(Email)`
  - Assert UUID fields: `expect(resource.id).to.be.instanceof(UUID)`
  - Assert URL fields: `expect(resource.url).to.be.instanceof(URL)`
  - Assert Date fields: `expect(resource.createdAt).to.be.instanceof(Date)`
  - Assert vendor-specific types when applicable (e.g., `expect(resource.arn).to.be.instanceof(Arn)`)
- Null and undefined handling

## Environment Variables Template

The `.env.example` will include sections for:

```bash
# API Authentication
API_KEY=your_api_key_here
# or
CLIENT_ID=your_client_id
CLIENT_SECRET=your_client_secret
# or
ACCESS_TOKEN=your_access_token

# API Configuration  
BASE_URL=https://api.example.com
API_VERSION=v1

# Test Configuration
NODE_ENV=test
LOG_LEVEL=debug
INTEGRATION_TIMEOUT=30000
```

## Success Criteria

- [ ] `Common.ts` with `prepareApi()` function created
- [ ] `ConnectionTest.ts` for authentication testing created
- [ ] Integration test file created for each producer class
- [ ] Tests run successfully when credentials are provided
- [ ] Tests skip gracefully when credentials are missing  
- [ ] Real API responses are captured and sanitized
- [ ] **ALL TESTS PASS (100% pass rate required)**
- [ ] Test failures resolved without modifying generated files or api.yml
- [ ] Environment template is comprehensive
- [ ] No sensitive data is committed to git
- [ ] **Final validation passes**: `npm run clean && npm run build && npm run test:integration`
- [ ] Task output file generated in local memory

## Next Steps

After integration tests are created:
1. **Configure credentials**: Copy `.env.example` to `.env` and fill in real values
2. **Run tests**: Execute `npm run test:integration` to verify everything works
3. **Review fixtures**: Check generated response examples in `test/fixtures/integration/`
4. **Update documentation**: Add integration test instructions to module README

## Error Handling

### Common Issues and Allowed Solutions
- **Missing credentials**: Provide clear instructions on where to get test API keys
- **API rate limits**: Implement retry logic and appropriate delays between tests
- **Network timeouts**: Increase timeout values for slow APIs
- **Connection issues**: Verify API endpoints and network accessibility
- **Test expectation mismatches**: Update test assertions to match actual API responses
- **Data type mismatches**: Adjust test expectations (e.g., expect numbers vs strings)

### Troubleshooting Guidelines
- Check environment variable names match code expectations
- Verify API endpoints are accessible from test environment
- Validate credentials are properly configured and active
- Review API documentation for any recent changes
- **STOP and inform user** if API model changes are needed
- Focus on test and implementation fixes, not generated code changes

### When to Request User Action
If integration tests reveal any of these issues, **STOP and inform the user**:
- API response structure doesn't match generated models
- Missing required fields in API responses
- Incorrect data types in generated models
- API endpoints that don't exist in the specification
- Authentication methods not supported by current models

The user must update `api.yml` and regenerate before integration testing can continue.

---

**Human Review Required**: After integration tests are created, verify that sensitive test data is properly sanitized and real credentials are not exposed in test fixtures or logs.
