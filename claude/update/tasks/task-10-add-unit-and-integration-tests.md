# Task 10: Add Unit and Integration Tests

## Prerequisites

**🚨 CRITICAL**: Before starting this task, read `CLAUDE.md` to understand the project structure, rules, and requirements.

## Overview

This task adds comprehensive test coverage for the new operations added to the module. It creates unit tests for new producer methods and integration tests for end-to-end validation, while preserving all existing tests exactly.

## Input Requirements

- Task 09 output file: `.claude/.localmemory/update-{module-identifier}/task-09-output.json` (updated exports)
- All previous task outputs for context
- Existing test structure and patterns for reference

## Process Steps

### 0. Context Management and Goal Reminder

**🚨 MANDATORY FIRST STEP**: Read and understand the original user intent:

1. **Review added functionality**:
   - Identify all new operations that need test coverage
   - Focus only on NEW operations, don't modify existing tests
   - Ensure comprehensive coverage for all new functionality

### 1. Analyze Existing Test Structure

1. **Examine current test patterns**:
   - Review existing unit tests in `test/unit/` directory
   - Review existing integration tests in `test/integration/` directory
   - Understand existing test utilities and patterns
   - Note credential handling and setup patterns

2. **Identify testing approach**:
   - NEW unit test files for new producers (if created)
   - NEW methods in existing unit test files (if extending producers)
   - NEW integration test files for new operations
   - Use existing test fixtures where possible, create new ones as needed

### 2. Add Unit Tests for New Operations

**For Extended Producers**:

1. **Add new test methods to existing unit test files**:
   - **🚨 CRITICAL**: Do not modify existing test methods
   - Add new `describe` blocks for new operations
   - Follow existing test patterns and utilities
   - Test all new methods added to existing producers

**For New Producers**:

2. **Create new unit test files**:
   - File: `test/unit/{NewProducer}Test.ts`
   - Follow existing unit test file structure and patterns
   - Test all methods in new producer implementation

**Unit Test Requirements**:
- Test successful operation scenarios
- Test error handling scenarios  
- Test parameter validation
- Test data mapping and transformation
- Mock HTTP client responses appropriately
- Use existing test utilities and setup patterns

### 3. Add Integration Tests for New Operations

**Integration Test Strategy**:

1. **Extend existing integration test files** (if operations fit existing domains):
   - Add new test methods to existing integration test files
   - **🚨 CRITICAL**: Do not modify existing integration test methods
   - Use existing `prepareApi()` function and setup

2. **Create new integration test files** (if new domains/producers):
   - File: `test/integration/{NewProducer}Test.ts`
   - Follow existing integration test patterns
   - Use existing `Common.ts` utilities and credential setup

**Integration Test Requirements**:
- **🚨 MANDATORY**: ALL new operations must have integration tests
- Tests must pass when credentials are available
- Tests must skip gracefully when credentials lack permissions
- Use realistic test scenarios and data
- Create new test fixtures for new API responses
- Follow existing error handling and timeout patterns

### 4. Integration Test Implementation Pattern

```typescript
import { expect } from 'chai';
import { prepareApi } from './Common';

describe('New Producer Integration Tests', () => {
  const api = prepareApi();

  describe('new operation', () => {
    it('should perform new operation successfully', async () => {
      // Test implementation using real API
      const result = await api.getNewProducer().newOperation(params);
      expect(result).to.exist;
      // Additional assertions
    });

    it('should handle errors appropriately', async () => {
      // Test error scenarios
    });
  });
});
```

### 5. Create New Test Fixtures

For new operations that return new data types:

1. **Create new fixture directories**:
   - `test/fixtures/integration/{new-operation}/`
   - Store sanitized real API response examples
   - Follow existing fixture naming and structure patterns

2. **Sanitize fixture data**:
   - Remove real credentials, tokens, personal information
   - Replace sensitive data with placeholder values
   - Maintain realistic data structure and types

### 6. Install Test Dependencies (if needed)

Add any additional test dependencies required for new operations:
```bash
npm install --save-dev {additional-test-dependencies}
```

### 7. Test Execution and Validation

1. **Run new unit tests**:
   ```bash
   npm run test
   ```
   - **CRITICAL**: All new unit tests must pass
   - **CRITICAL**: All existing unit tests must continue to pass

2. **Run new integration tests** (if credentials available):
   ```bash
   npm run test:integration
   ```
   - **CRITICAL**: All new integration tests must pass
   - **CRITICAL**: All existing integration tests must continue to pass
   - New tests should skip gracefully if no permissions

### 8. Build Validation (CRITICAL GATE)

1. **Full clean build and test**:
   ```bash
   npm run clean && npm run build && npm run test
   ```

2. **Build and test success requirement**:
   - **CRITICAL**: Build must exit with code 0
   - **CRITICAL**: All tests must pass (existing + new)

## Output Format

Store the following JSON in memory file: `.claude/.localmemory/update-{module-identifier}/task-10-output.json`

```json
{
  "status": "completed|failed|error",
  "timestamp": "{iso_timestamp}",
  "testCoverage": {
    "unitTests": {
      "newTestFiles": [
        {
          "file": "test/unit/{Producer}Test.ts",
          "type": "new|extended",
          "newTestMethods": ["{test_method_names}"],
          "operationsCovered": ["{operation_ids}"]
        }
      ],
      "testExecution": {
        "command": "npm run test",
        "exitCode": 0,
        "status": "passed|failed",
        "newTestsPassing": "{new_test_count}",
        "existingTestsPassing": "{existing_test_count}",
        "totalFailures": 0
      }
    },
    "integrationTests": {
      "newTestFiles": [
        {
          "file": "test/integration/{Producer}Test.ts", 
          "type": "new|extended",
          "newTestMethods": ["{test_method_names}"],
          "operationsCovered": ["{operation_ids}"]
        }
      ],
      "testExecution": {
        "command": "npm run test:integration",
        "exitCode": 0,
        "status": "passed|failed|skipped",
        "credentialsAvailable": true,
        "newTestsPassing": "{new_test_count}",
        "existingTestsPassing": "{existing_test_count}",
        "totalFailures": 0
      },
      "newFixtures": [
        {
          "directory": "test/fixtures/integration/{operation}/",
          "files": ["{fixture_file_names}"],
          "sanitized": true
        }
      ]
    }
  },
  "validation": {
    "buildValidation": {
      "command": "npm run clean && npm run build && npm run test",
      "exitCode": 0,
      "status": "passed|failed"
    },
    "testCoverageValidation": {
      "allNewOperationsTested": true,
      "unitTestCoverage": "100%",
      "integrationTestCoverage": "100%",
      "noRegressions": true
    }
  }
}
```

## Error Conditions

- **Task 09 not completed**: Stop execution, exports must be updated first
- **Unit tests fail**: Stop execution, fix unit test implementation
- **Integration tests fail**: Stop execution, fix integration test implementation  
- **Existing tests fail**: Stop execution, new tests broke existing functionality
- **Build fails**: Stop execution, fix compilation issues

## Success Criteria

**MANDATORY REQUIREMENTS:**
- Unit tests created for ALL new operations
- Integration tests created for ALL new operations
- All new unit tests pass (100% success rate)
- All new integration tests pass (100% success rate when credentials available)
- All existing unit tests continue to pass (no regressions)
- All existing integration tests continue to pass (no regressions)
- Build passes with all tests (`npm run clean && npm run build && npm run test`)
- New test fixtures created and properly sanitized
- Test patterns consistent with existing test code

## 🚨 Critical Rules

1. **100% TEST COVERAGE REQUIRED** - Every new operation must have both unit and integration tests
2. **ALL TESTS MUST PASS** - Zero tolerance for test failures when task is complete
3. **NO MODIFICATIONS TO EXISTING TESTS** - Existing test methods must remain exactly the same
4. **ZERO REGRESSIONS** - All existing tests must continue to pass
5. **BUILD GATE MANDATORY** - Build must pass with all tests
6. **CREDENTIAL HANDLING** - Integration tests must handle missing credentials gracefully
7. **FIXTURE SANITIZATION** - All test fixtures must have sensitive data removed

## Test Implementation Checklist

For each new operation:
- [ ] Unit test method created and passing
- [ ] Integration test method created and passing  
- [ ] Error scenarios tested
- [ ] Parameter validation tested
- [ ] Data mapping/transformation tested
- [ ] Real API integration tested (when credentials available)
- [ ] Test fixtures created and sanitized
- [ ] Existing tests unmodified and still passing

## Rollback Procedure

If test addition causes issues:

1. **Stop all work immediately**
2. **Remove new test files**: Delete any new test files created
3. **Restore modified test files**: Restore backups of extended test files
4. **Verify restoration**: `npm run build && npm run test`
5. **Report issue** with specific error details

## Next Steps

**On Success**: Proceed to Task 11 (Validate Backward Compatibility)  
**On Test Failure**: Fix test implementation and ensure all tests pass
**On Build Failure**: Fix compilation issues with new tests