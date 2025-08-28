# Task 08: Add and Run Unit Tests

## Prerequisites

**🚨 CRITICAL**: Before starting this task, read `CLAUDE.md` to understand the project structure, rules, and requirements.

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
   - Ensure all unit test decisions align with the original user request
   - Keep the user's specific intentions and scope in mind throughout the task
   - If any conflicts arise between task instructions and user intent, prioritize user intent

3. **Context preservation**:
   - Reference the original prompt when designing test coverage
   - Ensure the unit tests validate the user's actual requirements, not generic functionality

## Task Overview

**🚨 UPDATED WORKFLOW**: Complete removal and regeneration of unit tests with new mocks based on integration test recordings.

Create comprehensive unit tests using HTTP-level mocking with nock. These tests validate complete request/response flows while maintaining fast execution. 

**PRIMARY WORKFLOW (MANDATORY IF CREDENTIALS AVAILABLE):**
1. **Run integration tests with nock recording** to capture real API interactions
2. **Anonymize ALL recorded data** - remove real names, phone numbers, addresses, IDs, emails, etc.
3. **Generate unit tests** that match integration tests 1:1 but use mocked network
4. **Ensure unit tests use anonymized fixtures** matching the new mocks from recordings
5. **Validate NO personal data exposure** in unit tests (separate validation step)

**FALLBACK APPROACH**: Create fixtures from original API specifications when credentials are not available.

## 🚨 Critical Rules

- **USE HTTP-level mocking only** - Mock with nock, never mock internal library methods
- **VERIFY request parameters** - Ensure module sends correct data to API endpoints
- **TEST error scenarios** - Include tests for 401, 403, 404, 500, and network failures
- **SANITIZE sensitive data** - Remove credentials and PII from any recorded responses
- **🚨 MANDATORY: ALL TESTS MUST PASS** - **ZERO FAILURES ALLOWED** - 100% pass rate is REQUIRED for task completion
- **🚨 NO SKIP/IGNORE/EXCEPTIONS** - NO `.skip()`, `.only()`, or ignored tests - All tests must execute and pass
- **🚨 FIX IMPLEMENTATION IF TESTS FAIL** - If tests fail, fix the implementation code, not the tests
- **🚨 VALIDATE TEST EXECUTION** - Must run `npm test` and verify 0 failures before task completion
- **NEVER modify generated files** - Only edit source and test files, not `/generated/` directory
- **USE original API specifications** - Reference Task 01/02 outputs for API schemas when credentials unavailable

## Input Requirements

### Essential
- Task 01 output: `.claude/.localmemory/{action}-{module-identifier}/task-01-output.json` (original API specs)
- Task 02 output: `.claude/.localmemory/{action}-{module-identifier}/task-02-output.json` (operation mapping)  
- Task 06 output: `.claude/.localmemory/{action}-{module-identifier}/task-06-output.json` (implementation details)
- Where `{module-identifier}` is the product identifier derived from the identified product package (e.g., `vendor-suite-service` from `@scope/product-vendor-suite-service`, or `vendor-service` from `@scope/product-vendor-service`)
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
- `test/fixtures/*.json` - Test fixtures and mock response data (copied from memory folder)
- `.env.example` - Environment variables template for integration testing

### Reusable Scripts Created (in `/claude/create/scripts/`)
- `/claude/create/scripts/record-integration-tests.sh` - Module-agnostic recording script
- `/claude/create/scripts/generate-unit-tests.sh` - Module-agnostic test generation script

### AI Agent Tasks Used
- **Data Sanitization Agent**: AI-powered comprehensive data anonymization (replaces script-based approach)

### Working Files (in memory folder - never committed)
- `.claude/.localmemory/{action}-{module-identifier}/_work/recordings-raw.json` - Raw API recordings
- `.claude/.localmemory/{action}-{module-identifier}/_work/recordings-sanitized.json` - Sanitized recordings
- `.claude/.localmemory/{action}-{module-identifier}/_work/test-generation-config.json` - Test generation metadata

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

## Implementation

### Step 1: Setup Test Environment

**🚨 FIRST: Remove All Existing Unit Tests:**
- Delete ALL existing unit test files to ensure clean regeneration
- Remove old fixture files that may contain unsanitized data
- Clear any previous mocking setup that doesn't use recorded data

**Install Dependencies:**
- Install nock and TypeScript types for HTTP mocking
- Create test directory structure: `test/unit`, `test/utils`, `test/fixtures`
- Set up subdirectories for different fixture types (recorded, sanitized, templates)

**Create Base Configuration:**
- **DO NOT MODIFY package.json scripts** - use existing test configuration
- Set up TypeScript compilation for test files
- Ensure test framework (Mocha) and assertions (Chai) are configured
- Use reusable recording scripts instead of package.json modifications

### Step 2: Create Test Utilities

**Nock Helper Functions (`test/utils/nock-helpers.ts`):**
Create reusable functions for common testing patterns:
- `mockAuthenticatedRequest()` - Handle API authentication headers
- `mockPaginatedResponse()` - Mock paginated API responses  
- `mockErrorResponse()` - Mock HTTP error responses (401, 403, 404, etc.)
- `loadFixture()` - Load test data from JSON fixture files
- `setupNockFromFixture()` - Automatically configure nock from recorded fixtures

**Recording Utilities (when credentials available):**
- **🚨 REUSABLE RECORDING SCRIPTS**: Create recording utilities in `/claude/create/scripts/` directory
- Scripts must be **module-agnostic** and work across all modules using input parameters only
- **NO MODULE-SPECIFIC HARDCODING** - use parameters for module paths, API endpoints, etc.
- `RecordingManager` class to capture real API traffic during integration tests
- Automatic recording of HTTP requests/responses with nock.recorder
- Immediate sanitization of recorded data to remove sensitive information
- **OUTPUT TO MEMORY FOLDER**: All recording output goes to `.claude/.localmemory/{action}-{module-identifier}/_work/` (never committed)

**🚨 AI-POWERED DATA SANITIZATION AND ANONYMIZATION - MANDATORY:**

Use an AI agent to perform comprehensive data sanitization. This ensures intelligent context-aware anonymization that scripts cannot achieve.

**STEP: AI Agent Data Sanitization**
Launch a specialized AI agent to sanitize recorded data with the following comprehensive requirements:

## Sanitization Requirements for AI Agent

### 🚨 CRITICAL: Complete PII Removal
- **Remove ALL authentication tokens, API keys, and sensitive credentials** from recordings
- **Identify and replace ANY data that could reveal real system information** including but not limited to:
  - Real user names, employee names, customer names
  - Real organization names, company names, client names
  - Real addresses, locations, geographic data
  - Real phone numbers, contact information
  - Real email addresses and domains (except test domains)
  - Real account numbers, identifiers, reference numbers
  - Real timestamps that could indicate actual system activity
  - Real URLs, hostnames, server names (except localhost/test patterns)

### 🎯 Name Handling (CRITICAL - Fix Current Issues)
- **PROPER NAME SPLITTING**: "firstName": "Jane Doe" is WRONG
  - ✅ CORRECT: "firstName": "Jane", "lastName": "Doe"  
  - ✅ CORRECT: "firstName": "John", "lastName": "Smith"
  - ✅ CORRECT: "firstName": "Test", "lastName": "User"
- **Use realistic test name combinations**:
  - Test User, John Smith, Jane Doe, Bob Wilson, Alice Johnson, Carol Brown, David White
  - Mix names appropriately - don't use "Jane Doe" for both first and last name
- **Handle name variations**: fullName, displayName, username, etc.

### 🔢 ID Anonymization (PRESERVE TYPE & FORMAT)
- **Numeric IDs**: Replace large/production IDs with test values (123, 456, 789, 1001, 2002)
- **String IDs**: Replace while preserving patterns ("usr_abc123def" → "usr_test123")
- **UUIDs**: Replace with test UUIDs ("12345678-1234-1234-1234-123456789012")
- **Organization IDs**: Use patterns like "test-org-123", "demo-company-456"
- **Sequential consistency**: Keep logical relationships (user 123 always maps to same test user)
- **🚨 CRITICAL**: Preserve original data type (number→number, string→string, UUID→UUID)

### 📧 Contact Information
- **Email addresses**: Replace with test domains (@example.com, @testcorp.com, @demo.org)
- **Phone numbers**: Use clearly fake patterns (+1234567890, +1-555-0123, (555) 123-4567)
- **Addresses**: Replace with test addresses ("123 Test Street, Demo City, ST 12345")

### 🏢 Business Data
- **Organization names**: "Test Corp", "Demo Company", "Sample Organization", "Example Inc"  
- **Department names**: "Test Department", "Demo Team", "Sample Division"
- **Project names**: "Test Project", "Demo Initiative", "Sample Campaign"

### 📅 Temporal Data Handling
- **Preserve date/time formats** but use obviously test dates
- **Maintain relative timing** if sequence matters
- **Use consistent test date ranges** (2024-01-01 through 2024-12-31)

### 🔍 Contextual Intelligence Required
- **Understand field semantics** beyond just field names
- **Detect sensitive data patterns** in values regardless of field names
- **Maintain data relationships** and referential integrity
- **Preserve business logic** while anonymizing personal details
- **Handle nested objects** and arrays comprehensively
- **Process all data types**: strings, numbers, booleans, arrays, objects

### ✅ Validation Requirements
- **Scan for missed sensitive data** using pattern recognition
- **Verify logical consistency** of anonymized data
- **Ensure test data realism** (no obvious anonymization artifacts)
- **Confirm format preservation** for all data types
- **Validate referential integrity** between related fields

### 📋 Agent Task Specification
Create AI agent task with:
```markdown
Analyze and sanitize recorded API data from integration tests. Replace ALL real personal, business, and system information with realistic test data while preserving exact JSON structure, data types, and business logic relationships. Pay special attention to proper name handling (firstName/lastName split), ID anonymization, and contextual data relationships.
```

**🚨 MANDATORY**: Use Task tool to launch general-purpose agent for this sanitization step
**📂 INPUT**: Raw recordings from `.claude/.localmemory/{action}-{module-identifier}/_work/recordings-raw.json`  
**📂 OUTPUT**: Sanitized data to `.claude/.localmemory/{action}-{module-identifier}/_work/recordings-sanitized.json`

### Step 3: Create Unit Test Files Based on Integration Tests

**🚨 CRITICAL REQUIREMENT: Unit tests MUST match integration tests almost 1:1**
- Unit tests should mirror integration tests with identical test structures and flows
- Use mocked network responses instead of real API calls but run mostly the same tests
- Maintain same test scenarios, assertions, and edge cases as integration tests
- Ensure unit tests validate exact same functionality as integration tests
- **MANDATORY**: Unit tests should be nearly identical to integration tests but with mocked network layer
- **CRITICAL**: Use recorded and anonymized fixtures from integration test recordings as mock data

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
- **🚨 MANDATORY: Use anonymized mock data** - ALL test data MUST be anonymized (fake names, emails, addresses, IDs)
- **🚨 NO REAL PERSONAL DATA** - Ensure ZERO real names, phone numbers, addresses, emails, or any PII in unit tests

**Implementation Unit Tests (`test/unit/${service_name}ImplTest.ts`):**
Create tests for the main implementation class covering:
- **Constructor and client initialization** - Verify proper setup of internal client
- **Connection lifecycle** - Test connect(), isConnected(), disconnect() delegation to client
- **Module metadata** - Test metadata() method returns proper module information
- **Producer getters** - Test that producer properties return correct instances
- **Error propagation** - Verify errors from client are properly handled
- **🚨 MANDATORY: Mirror integration test structure** - Match integration tests 1:1 with mocked responses
- **🚨 MANDATORY: Use anonymized mock data** - ALL test data MUST be anonymized (fake names, emails, addresses, IDs)
- **🚨 NO REAL PERSONAL DATA** - Ensure ZERO real names, phone numbers, addresses, emails, or any PII in unit tests
- **🚨 USE RECORDED FIXTURES** - Use anonymized fixtures from integration test recordings as mock data source

**Mapper Unit Tests (`test/unit/MappersTest.ts`):**
Create tests for data transformation functions covering:
- **Field mapping accuracy** - Verify raw API data is correctly transformed
- **Date parsing** - Test conversion of string dates to Date objects
- **Optional field handling** - Test behavior with missing or null fields  
- **Data type validation** - Ensure mapped objects have correct TypeScript types
- **🚨 CRITICAL: Core Type Assertions** (see [Core Type Mapping Guide](core-type-mapping-guide.md)):
  - Assert `instanceof` for core types: `expect(mapped.email).to.be.instanceof(Email)`
  - Assert UUID fields: `expect(mapped.id).to.be.instanceof(UUID)`
  - Assert URL fields: `expect(mapped.url).to.be.instanceof(URL)`
  - Assert Date fields: `expect(mapped.createdAt).to.be.instanceof(Date)`
  - Assert vendor-specific types when applicable (e.g., `expect(mapped.arn).to.be.instanceof(Arn)`)
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

**🚨 MANDATORY: Run Integration Tests with Nock Recording:**
- Execute integration tests with nock.recorder enabled to capture real API interactions
- Record ALL HTTP requests and responses during integration test execution
- Capture complete interaction flows for all operations from Task 02
- Use existing integration tests as the basis for recording
- **CRITICAL**: Unit tests MUST match integration tests almost 1:1 but use mocked network

**Recording Process:**
1. **Use reusable recording scripts from `/claude/create/scripts/` (NO package.json changes):**
   ```bash
   # Run recording script with module parameters - called directly, not via npm scripts
   /claude/create/scripts/record-integration-tests.sh \
     --module-path "$(pwd)" \
     --output-dir ".claude/.localmemory/{action}-{module-identifier}/_work" \
     --service-name "AvigilonAltaAccess"
   ```
2. **Capture real API traffic:**
   - Run integration tests against real API endpoints using the reusable script
   - Record all HTTP requests/responses with complete headers and payloads
   - Capture both successful responses and error conditions
   - **ALL OUTPUT GOES TO MEMORY FOLDER** - never committed to git
3. **🚨 COMPREHENSIVE DATA ANONYMIZATION - MANDATORY PRIVACY PROTECTION:**
   - **Remove ALL personal identifiable information (PII)** from recorded responses
   - **Replace real names** with anonymized fake names (e.g., "Test User", "John Smith", "Jane Doe")
   - **Replace ALL email addresses** with anonymized patterns:
     - Use test domains: @example.com, @testcorp.com, @demo.org
     - Pattern: firstname.lastname@example.com, user123@testcorp.com
   - **Replace ALL phone numbers** with test patterns:
     - Use patterns like: +1234567890, +1-555-0123, (555) 123-4567
     - Maintain format but use clearly fake numbers
   - **Replace ALL physical addresses** with fake test addresses:
     - Use test street names, cities, postal codes
     - Example: "123 Test Street, Demo City, ST 12345"
   - **Replace ALL personal identifiers** with anonymized test IDs:
     - Preserve format patterns but use clearly fake values
     - Example: real ID "usr_abc123def" becomes "usr_test123"
   - **Replace organization/company names** with test organization names
   - **Replace ANY other sensitive personal data** (SSN, license numbers, etc.)
   - **Ensure ZERO real personal data remains** in any fixture or mock
4. **Generate sanitized fixtures:**
   - **FIRST**: Save raw recordings to `.claude/.localmemory/{action}-{module-identifier}/_work/recordings-raw.json` (never committed)
   - **THEN**: Generate sanitized recordings in `.claude/.localmemory/{action}-{module-identifier}/_work/recordings-sanitized.json`
   - **FINALLY**: Copy sanitized data to `test/fixtures/recorded/` for use in unit tests
   - Maintain exact API response structure but with anonymized data
   - Create both success and error response fixtures
   - **VERIFY no real personal data leaked** into fixtures
   - **USE AI AGENT FOR SANITIZATION**: Launch general-purpose agent with data sanitization task

### Step 7: Create Environment Template

Create `.env.example` with authentication variables based on the connection profile from Task 02:
- Include all authentication methods discovered during API analysis
- Add base URL and API version information from original specifications
- Include test configuration variables (timeouts, log levels)
- Add recording mode configuration for capturing API traffic

### Step 8: Configure Test Execution (No Package.json Changes)

**🚨 DO NOT MODIFY PACKAGE.JSON SCRIPTS** - Use reusable scripts directly to avoid commit conflicts

Use reusable scripts and AI agents:
- Run unit tests: Use existing `npm test` command
- Recording: Use `/claude/create/scripts/record-integration-tests.sh` directly
- Sanitization: Use AI agent with Task tool for intelligent data anonymization

**🚨 RECORDING SCRIPT SPECIFICATIONS:**
Create these reusable scripts in `/claude/create/scripts/`:

**`/claude/create/scripts/record-integration-tests.sh`:**
- Parameters: `--module-path`, `--output-dir`, `--service-name`, `--test-pattern` (optional)
- Function: Run integration tests with nock recording enabled
- Output: Raw recordings to `{output-dir}/recordings-raw.json`
- Must work across all modules without modification

**AI Agent Data Sanitization:**
- **Input**: Raw recordings from `.claude/.localmemory/{action}-{module-identifier}/_work/recordings-raw.json`
- **Function**: Intelligent context-aware sanitization of recorded API data, removing ALL PII
- **Output**: Sanitized recordings to `.claude/.localmemory/{action}-{module-identifier}/_work/recordings-sanitized.json`
- **Agent Type**: general-purpose (has access to all tools for comprehensive analysis)
- **Task**: Use Task tool to launch agent with data sanitization requirements above

**`/claude/create/scripts/generate-unit-tests.sh`:**
- Parameters: `--module-path`, `--recordings-file`, `--service-name`, `--template-dir` (optional)
- Function: Generate unit test files based on sanitized recordings
- Output: Unit test files in module's `test/unit/` directory
- Must work across all modules without modification

### Step 9: Validate Data Anonymization - 🚨 MANDATORY PRIVACY PROTECTION

**🚨 CRITICAL: Ensure No Personal Data Exposure**
Before executing tests, validate that ALL test data is anonymized:

**Data Privacy Validation:**
- **Scan all fixture files** in `test/fixtures/` for any real personal data
- **Verify anonymization** of names, emails, phone numbers, addresses
- **Check test files** for hardcoded real personal information
- **Validate organization names** are anonymized (use test company names)
- **Confirm IDs are anonymized** while preserving format patterns
- **Remove any real API tokens or credentials** from test data

**🚨 MANDATORY Privacy Protection Checklist - ALL MUST BE VERIFIED:**
- [ ] **NO REAL PERSON NAMES** appear in any test files or fixtures (use "Test User", "John Smith", "Jane Doe")
- [ ] **NO REAL EMAIL ADDRESSES** (all use test domain patterns like @example.com, @testcorp.com, @demo.org)
- [ ] **NO REAL PHONE NUMBERS** (all use test patterns like +1234567890, +1-555-xxxx, (555) 123-4567)
- [ ] **NO REAL STREET ADDRESSES, CITIES, OR POSTAL CODES** (use "123 Test Street, Demo City, ST 12345")
- [ ] **NO REAL ORGANIZATION OR COMPANY NAMES** (use "Test Corp", "Demo Company", "Sample Organization")
- [ ] **NO REAL API TOKENS, KEYS, OR SENSITIVE IDENTIFIERS** (use clearly anonymized test values)
- [ ] **NO REAL ACCOUNT NUMBERS, SSN, OR PERSONAL IDS** (anonymize while preserving format)
- [ ] **ALL TEST DATA USES CLEARLY FAKE, ANONYMIZED VALUES** that cannot identify real people
- [ ] **FIXTURES MATCH UNIT TEST MOCKS** - ensure consistency between recorded fixtures and unit test data
- [ ] **INTEGRATION AND UNIT TESTS USE SAME ANONYMIZED DATA** - maintain consistency across test types

**🚨 IMMEDIATE STOP CONDITION**: If ANY real personal data is discovered in test files or fixtures, immediately anonymize it before proceeding. Task cannot continue with real PII present.

### Step 10: Execute and Validate Tests - 🚨 MANDATORY VALIDATION

**🚨 CRITICAL: MANDATORY TEST EXECUTION AND VALIDATION**

**Run Unit Tests:**
Execute all unit tests and verify **ZERO FAILURES**:
```bash
# Must execute this command and verify 0 failures
npm test
```

**🚨 FAILURE RESOLUTION PROTOCOL:**
If ANY tests fail, you MUST:
1. **ANALYZE the failure** - Understand why tests are failing
2. **FIX THE IMPLEMENTATION** - Correct bugs in source code (`src/` directory) 
3. **NEVER SKIP TESTS** - Do not use `.skip()`, `.only()`, or comment out failing tests
4. **NEVER MODIFY GENERATED FILES** - Focus on implementation and test fixes only
5. **RE-RUN TESTS** - Execute `npm test` again until ALL tests pass
6. **REPEAT UNTIL ZERO FAILURES** - Continue fixing until 100% pass rate achieved

**🚨 TASK IS NOT COMPLETE UNTIL:**
- `npm test` shows **0 failures, 0 errors**
- All tests execute (no skipped tests)
- Test output shows **100% pass rate**
- No `.skip()`, `.only()`, or ignored test methods remain in code

**ACCEPTABLE FIXES (in order of preference):**
1. Fix implementation bugs in `src/` directory
2. Fix test setup or configuration issues
3. Update test expectations to match correct behavior (only if implementation is verified correct)

**UNACCEPTABLE WORKAROUNDS:**
- Using `.skip()` to ignore failing tests
- Using `.only()` to run only passing tests  
- Commenting out failing test cases
- Modifying generated interface files
- Accepting any test failures as "expected"

**Record API Traffic (using reusable approach):**
- Use `/claude/create/scripts/record-integration-tests.sh` to capture fresh API interactions
- Output raw recordings to `.claude/.localmemory/{action}-{module-identifier}/_work/recordings-raw.json`
- **Use AI agent for data sanitization** - launch general-purpose agent to clean data and output to `_work/recordings-sanitized.json`
- Copy sanitized data to `test/fixtures/recorded/` for use in unit tests
- **NEVER COMMIT** raw recordings - they stay in memory folder only
- Verify sensitive data has been properly removed from all fixtures using AI agent validation

**Generate Test Report:**
Create comprehensive summary showing:
- Test files created and their coverage areas
- Test execution results and pass/fail status
- Fixture files generated and their sources
- Next steps for ongoing test maintenance

## Output Format

Store the following JSON in memory file: `.claude/.localmemory/{action}-{module-identifier}/task-08-output.json`

```json
{
  "status": "completed|failed|error",
  "timestamp": "${iso_timestamp}",
  "unitTests": {
    "testFilesCreated": "${created_test_count}",
    "fixturesCreated": "${created_fixture_count}",
    "testsExecuted": "${executed_test_count}",
    "testsPassed": "${passed_test_count}",
    "testsFailed": "${failed_test_count}",
    "testPassRate": "${pass_rate_percentage}",
    "allTestsPassed": "${boolean_all_tests_passed}",
    "npmTestCommandOutput": "${npm_test_final_output}",
    "recordingMode": "${recording_mode_used}"
  },
  "files": {
    "clientTest": "${module_path}/test/unit/${service_name}ClientTest.ts",
    "implTest": "${module_path}/test/unit/${service_name}ImplTest.ts",
    "mappersTest": "${module_path}/test/unit/MappersTest.ts",
    "producerTests": ["${list_of_producer_test_files}"],
    "utilities": ["${list_of_utility_files}"],
    "fixtures": ["${list_of_fixture_files}"]
  },
  "reusableScripts": {
    "recordingScript": "/claude/create/scripts/record-integration-tests.sh",
    "testGenerationScript": "/claude/create/scripts/generate-unit-tests.sh"
  },
  "aiAgentTasks": {
    "dataSanitization": "general-purpose agent with comprehensive PII removal and context-aware anonymization"
  },
  "workingFiles": {
    "rawRecordings": ".claude/.localmemory/${action}-${module-identifier}/_work/recordings-raw.json",
    "sanitizedRecordings": ".claude/.localmemory/${action}-${module-identifier}/_work/recordings-sanitized.json",
    "testGenerationConfig": ".claude/.localmemory/${action}-${module-identifier}/_work/test-generation-config.json"
  }
}
```

## Success Criteria

- [ ] Nock dependencies installed successfully
- [ ] Unit test files created for Client, Impl, and Mappers classes
- [ ] Test utility files created with reusable nock helpers
- [ ] Recording and sanitization utilities created 
- [ ] Basic test fixtures created with realistic data
- [ ] **🚨 REUSABLE SCRIPTS CREATED in `/claude/create/scripts/` directory**
- [ ] Integration recording workflow established (if credentials available)
- [ ] Environment template created with comprehensive variable list
- [ ] **🚨 MANDATORY: ALL UNIT TESTS PASS - ZERO FAILURES REQUIRED**
- [ ] **🚨 MANDATORY: `npm test` command executed and shows 0 failures, 0 errors**
- [ ] **🚨 MANDATORY: No skipped, ignored, or .only() tests remain in codebase**
- [ ] **🚨 MANDATORY: Implementation code fixed if any tests initially failed**
- [ ] **🚨 NO PACKAGE.JSON MODIFICATIONS** - all scripts remain reusable and external
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
2. **Run recording script directly** to capture actual HTTP traffic (no package.json changes)
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
  /claude/create/scripts/record-integration-tests.sh \
    --module-path "$(pwd)" \
    --output-dir ".claude/.localmemory/{action}-{module-identifier}/_work" \
    --service-name "${service_name}"
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

## 🚨 FINAL VALIDATION CHECKLIST

Before marking this task as complete, you MUST verify:

**✅ TEST EXECUTION VALIDATION:**
- [ ] Executed `npm test` command successfully
- [ ] Command output shows "0 failing" tests
- [ ] Command output shows "X passing" tests (where X > 0)
- [ ] No test errors, timeouts, or crashes occurred
- [ ] Exit code was 0 (success)

**✅ CODE QUALITY VALIDATION:**
- [ ] No `.skip()` methods remain in any test files
- [ ] No `.only()` methods remain in any test files  
- [ ] No commented-out test cases
- [ ] All test files compile without TypeScript errors
- [ ] Implementation code passes lint checks (if applicable)

**✅ IMPLEMENTATION VALIDATION:**
- [ ] If any tests initially failed, implementation bugs were fixed
- [ ] No generated files in `/generated/` directory were modified
- [ ] Only `src/` and `test/` files were changed to fix issues
- [ ] Core type assertions work correctly (Email, URL, UUID, etc.)

**🚨 TASK COMPLETION REQUIREMENT:**
This task is **NOT COMPLETE** until ALL above checkboxes are verified. If any tests fail, the implementation must be fixed and tests re-run until 100% pass rate is achieved.

**Human Review Required**: After unit tests are created, verify that all tests pass and provide comprehensive coverage of the module's functionality. Check that sensitive data is properly sanitized in any recorded fixtures.