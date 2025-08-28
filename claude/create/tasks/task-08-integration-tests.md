# Task 7: Add and Run Integration Tests

## Prerequisites

**🚨 CRITICAL**: Before starting this task, read `CLAUDE.md` to understand the project structure, rules, and requirements.

**Task ID**: `testing-add-integration-tests`  
**Step**: 7  
**Category**: Testing & Validation  

## Overview

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
   - Ensure all integration test decisions align with the original user request
   - Keep the user's specific intentions and scope in mind throughout the task
   - If any conflicts arise between task instructions and user intent, prioritize user intent

3. **Context preservation**:
   - Reference the original prompt when designing test scenarios
   - Ensure the integration tests validate the user's actual needs, not generic functionality

## Task Overview

Create comprehensive integration tests that validate the module's ability to connect and interact with the real API. These tests are always created but only run when test credentials are provided. When credentials are available and tests run, they must pass with full permissions assumed.

## 🚨 Critical Rules

- **ALWAYS CREATE TESTS** - Integration tests are always created, regardless of credential availability
- **🚨 MANDATORY TESTING WHEN CREDENTIALS PROVIDED** - When API keys/credentials are provided, integration tests MUST be run and MUST pass - never skip integration tests when having credentials
- **🚨 NEVER SKIP TESTS** - Do not skip tests regardless of credentials availability or other conditions - tests must always execute
- **🚨 NEVER RETRY OPERATIONS** - Do not use retry mechanisms or retryOperation() function - use direct await operation(params...) calls only
- **🚨 LOCATE AND USE CREDENTIALS** - Find where credentials are stored (check .env files, environment variables) and use them for testing
- **🚨 CREDENTIAL SECURITY** - NEVER add credentials to any code that could be committed - only use .env files (which should be in .gitignore)
- **ASSUME FULL PERMISSIONS** - When tests run, assume a token/credentials with all necessary permissions are provided
- **NO PERMISSION-BASED SKIPPING** - When credentials are available, do not skip tests due to lack of permissions; assume access is available
- **DIRECT OPERATION CALLS** - Always use direct await operation(params...) calls without retry wrappers or skip conditions
- **ALWAYS sanitize sensitive data** - Remove real tokens, emails, and personal information from test outputs
- **MUST use realistic test scenarios** - Test actual API endpoints and common use cases
- **NEVER commit real credentials** - Use `.env.example` for templates only
- **🚨 MANDATORY: ALL INTEGRATION TESTS MUST PASS** - **ZERO FAILURES ALLOWED** when credentials are provided - 100% pass rate is REQUIRED for task completion
- **🚨 NO SKIP/IGNORE/EXCEPTIONS** - NO `.skip()`, `.only()`, or ignored tests - All tests must execute and pass regardless of credentials
- **🚨 NO RETRY WRAPPERS** - Remove all retryOperation() calls and use direct await operation(params...) calls
- **🚨 FIX IMPLEMENTATION IF TESTS FAIL** - If integration tests fail, fix the implementation code or test issues, not the tests themselves
- **🚨 VALIDATE TEST EXECUTION** - Must run `npm run test:integration` and verify 0 failures before task completion when credentials available
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

### Memory Output
- `.claude/.localmemory/{action}-{module-identifier}/task-07-output.json` - Task completion status and results

### Test Coverage Areas
- **Connection tests**: Authentication and connection validation  
- **Producer tests**: Core API operations per producer (list, get, create, update, delete as applicable)
- **Common utilities**: Shared credential loading and module initialization
- **Error handling**: Real API error responses
- **Pagination and filtering**: If supported by the API
- **Rate limiting**: Retry behavior and timeout handling

## Implementation

### Step 1: Create Integration Test Structure

```bash
cd ${module_path}
mkdir -p test/integration test/fixtures/integration test/utils
```

### Step 2: Install Required Dependencies

```bash
npm install --save-dev dotenv @types/dotenv
```

### Step 3: Create Integration Test Suites

Create the test file structure:
- **Common.ts**: Credential loading, `prepareApi()` function, shared utilities
- **ConnectionTest.ts**: Authentication and connection validation tests
- **Producer tests**: One file per producer class for API operations
- Load credentials from environment variables
- Test real API connections and operations for each producer
- Validate response formats and data types
- Handle authentication errors gracefully
- Sanitize and save response examples

### Step 4: Create Environment Template

Generate `.env.example` with placeholders for:
- API keys, tokens, or credentials
- Base URLs and endpoints
- Test-specific configuration
- Timeout and retry settings

### Step 5: Create Integration Utilities

Build helper functions for:
- Environment variable validation
- Response data sanitization
- Test fixture generation
- Connection setup and teardown

### Step 6: Run Integration Tests (If Credentials Available)

```bash
# Only run if credentials are properly configured
if [ -f .env ] && [ "${test_credentials_available}" = "true" ]; then
  npm run test:integration
else
  echo "Integration tests skipped - no credentials available"
fi
```

**Test Behavior**:
- **With credentials**: All tests run and must pass with full permissions
- **Without credentials**: Tests skip gracefully with informative messages
- **Partial credentials**: Tests assume full permissions for provided credentials

### Step 8: Execute and Validate Integration Tests - 🚨 MANDATORY VALIDATION

**🚨 CRITICAL: MANDATORY INTEGRATION TEST EXECUTION AND VALIDATION (When Credentials Available)**

**Run Integration Tests:**
When credentials are available, execute all integration tests and verify **ZERO FAILURES**:
```bash
# Must execute this command and verify 0 failures when credentials are available
npm run test:integration
```

**🚨 FAILURE RESOLUTION PROTOCOL (When Credentials Available):**
If ANY integration tests fail, you MUST:
1. **ANALYZE the failure** - Understand why integration tests are failing
2. **FIX THE IMPLEMENTATION** - Correct bugs in source code (`src/` directory)
3. **FIX TEST SETUP ISSUES** - Resolve authentication, network, or configuration problems
4. **NEVER SKIP TESTS** - Do not use `.skip()`, `.only()`, or comment out failing tests
5. **NEVER MODIFY GENERATED FILES** - Focus on implementation and test fixes only
6. **RE-RUN TESTS** - Execute `npm run test:integration` again until ALL tests pass
7. **REPEAT UNTIL ZERO FAILURES** - Continue fixing until 100% pass rate achieved

**🚨 TASK IS NOT COMPLETE UNTIL (When Credentials Available):**
- `npm run test:integration` shows **0 failures, 0 errors**
- All integration tests execute (no skipped tests due to implementation issues)
- Test output shows **100% pass rate**
- No `.skip()`, `.only()`, or ignored test methods remain in code

**ACCEPTABLE FIXES (in order of preference):**
- **Test code issues**: Debug and fix problems in test files
- **Implementation errors**: Fix bugs in module source code (src/ directory)
- **Authentication issues**: Verify credentials and permissions
- **Network issues**: Add retry logic or increase timeouts
- **Data mapping errors**: Adjust test expectations to match API reality

**PROHIBITED ACTIONS:**
- **NEVER modify /generated files** - These are auto-generated from API specifications
- **NEVER modify api.yml** - API specification is source of truth
- **NEVER change model definitions** - Models are generated from OpenAPI spec
- Using `.skip()` to ignore failing tests when credentials are available
- Using `.only()` to run only passing tests when credentials are available
- Commenting out failing test cases when credentials are available
- Accepting any test failures as "expected" when credentials are available

**If Model Changes Required:**
1. **STOP task execution**
2. **Inform user** about required API model changes
3. **Request user action** to update api.yml and regenerate
4. **Wait for user** to complete changes before continuing

**🚨 Success Requirement:**
- **ALL integration tests must pass** - 100% pass rate required for task completion when credentials are available

### Step 8: Final Validation Build and Test

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

### Step 9: Generate Task Output

Create task completion record in local memory:
- Document test execution results
- Record created test files and fixtures
- Note any credential requirements or limitations
- Save integration test summary and recommendations
- Confirm final validation build success

## Output Format

Store the following JSON in memory file: `.claude/.localmemory/{action}-{module-identifier}/task-07-output.json`

```json
{
  "status": "completed|failed|error",
  "timestamp": "${iso_timestamp}",
  "integrationTests": {
    "credentialsAvailable": "${test_credentials_available}",
    "testsCreated": "${created_test_count}",
    "testsExecuted": "${executed_test_count}",
    "testsPassed": "${passed_test_count}",
    "testsFailed": "${failed_test_count}",
    "testPassRate": "${pass_rate_percentage}",
    "allTestsPassed": "${boolean_all_tests_passed}",
    "npmTestIntegrationOutput": "${npm_test_integration_final_output}"
  },
  "files": {
    "connectionTest": "${module_path}/test/integration/ConnectionTest.ts",
    "commonUtilities": "${module_path}/test/integration/Common.ts",
    "producerTests": ["${list_of_producer_test_files}"],
    "fixtures": ["${list_of_fixture_files}"],
    "envTemplate": "${module_path}/.env.example"
  }
}
```

## Test Scenarios

### Authentication Testing
- Valid credentials → successful connection
- Invalid credentials → proper error handling
- Missing credentials → graceful failure
- Expired tokens → refresh or clear error message

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
- Authentication failures (401, 403)
- Resource not found (404)
- Validation errors (400)

### Data Validation
- Response schema compliance
- Data type accuracy
- Required vs optional fields
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
- [ ] **🚨 MANDATORY: ALL INTEGRATION TESTS PASS - ZERO FAILURES REQUIRED (when credentials available)**
- [ ] **🚨 MANDATORY: `npm run test:integration` command executed and shows 0 failures, 0 errors (when credentials available)**
- [ ] **🚨 MANDATORY: No skipped, ignored, or .only() tests remain when credentials available**
- [ ] **🚨 MANDATORY: Implementation code fixed if any integration tests initially failed**
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
- **Authentication errors**: Verify credentials have sufficient permissions
- **Test expectation mismatches**: Update test assertions to match actual API responses
- **Data type mismatches**: Adjust test expectations (e.g., expect numbers vs strings)

### Troubleshooting Guidelines
- Check environment variable names match code expectations
- Verify API endpoints are accessible from test environment
- Ensure test account has necessary permissions for operations
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

## 🚨 FINAL VALIDATION CHECKLIST (When Credentials Available)

Before marking this task as complete when credentials are available, you MUST verify:

**✅ CREDENTIAL AND EXECUTION VALIDATION:**
- [ ] Credentials were detected and located in authorized locations (.env files)
- [ ] Executed `npm run test:integration` command successfully
- [ ] Command output shows "0 failing" tests
- [ ] Command output shows "X passing" tests (where X > 0)
- [ ] No test errors, timeouts, or crashes occurred
- [ ] Exit code was 0 (success)

**✅ CODE QUALITY VALIDATION:**
- [ ] No `.skip()` methods remain in any integration test files when credentials available
- [ ] No `.only()` methods remain in any integration test files when credentials available
- [ ] No commented-out test cases when credentials available
- [ ] All test files compile without TypeScript errors
- [ ] Implementation code passes lint checks (if applicable)

**✅ IMPLEMENTATION VALIDATION:**
- [ ] If any integration tests initially failed, implementation bugs were fixed
- [ ] No generated files in `/generated/` directory were modified
- [ ] Only `src/` and `test/` files were changed to fix issues
- [ ] Core type assertions work correctly with real API data (Email, URL, UUID, etc.)
- [ ] API responses are properly sanitized in captured fixtures

**✅ SECURITY VALIDATION:**
- [ ] No real credentials committed to git
- [ ] Sensitive data properly sanitized in test fixtures
- [ ] `.env.example` contains only template values, no real credentials

**🚨 TASK COMPLETION REQUIREMENT:**
This task is **NOT COMPLETE** until ALL above checkboxes are verified when credentials are available. If credentials are not available, tests should skip gracefully and task can be marked complete after test structure creation.

**🚨 CONDITIONAL COMPLETION:**
- **With credentials**: ALL integration tests must pass (100% pass rate) + all validation checkboxes complete
- **Without credentials**: Test structure created + tests skip gracefully when credentials missing

**Human Review Required**: After integration tests are created, verify that sensitive test data is properly sanitized and real credentials are not exposed in test fixtures or logs.
