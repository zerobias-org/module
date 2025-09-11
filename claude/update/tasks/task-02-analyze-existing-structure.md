# Task 02: Analyze Existing Structure

## Prerequisites

**🚨 CRITICAL**: Before starting this task, read `CLAUDE.md` to understand the project structure, rules, and requirements.

## Overview

This task analyzes the existing module structure, documents current operations, producers, and test coverage to provide a baseline for adding new operations. It validates that the module follows expected patterns and identifies any structural anomalies that might affect the update process.

## Input Requirements

- Task 01 output file: `.claude/.localmemory/update-{module-identifier}/task-01-output.json`
- Where `{module-identifier}` is derived from the existing module path
- All Task 01 validations must have passed successfully

## Process Steps

### 0. Context Management and Goal Reminder

**🚨 MANDATORY FIRST STEP - CONTEXT CLEARING**: 
- **IGNORE all previous conversation context** - This task runs in isolation
- **CLEAR mental context** - Treat this as a fresh start with no prior assumptions
- **REQUEST**: User should run `/clear` or `/compact` command before starting this task for optimal performance

**🚨 MANDATORY SECOND STEP**: Read and understand the original user intent:

1. **Read initial user prompt**:
   - Load `.claude/.localmemory/update-{module-identifier}/task-01-output.json`
   - Extract and review the `initialUserPrompt` field
   - Understand the original goal, scope, and specific user requirements

2. **Goal alignment verification**:
   - Ensure all analysis decisions align with the original user request
   - Keep the user's specific intentions and scope in mind throughout the task
   - If any conflicts arise between task instructions and user intent, prioritize user intent

### 1. Analyze API Specification

Navigate to module directory and examine the current API specification:

1. **API specification analysis**:
   - Read `api.yml` file
   - Extract current operations by path and method
   - Document existing tags (determines producer assignments)
   - Note authentication method and security schemes
   - Store current API version and service information

2. **Operation categorization**:
   - Group operations by tags/domains
   - Identify CRUD patterns (list, get, create, update, delete)
   - Document any custom or specialized operations
   - Note operation IDs and parameter patterns

### 2. Analyze Generated Interfaces

Examine the generated TypeScript interfaces:

1. **Generated API analysis**:
   - Read `generated/api/index.ts`
   - Document all generated interface names (Api interfaces, Connector interface)
   - Map interface names to API tags/operations
   - Note data model interfaces and enums

2. **Interface structure validation**:
   - Verify interfaces match expected patterns
   - Check for any generation anomalies
   - Document interface naming conventions used

### 3. Analyze Implementation Structure

Examine the current implementation files:

1. **Source file analysis**:
   - Document existing producer implementations (`src/*ProducerImpl.ts` or `src/*Impl.ts`)
   - Identify client implementation (`src/*Client.ts`)
   - Analyze mapper functions (`src/mappers.ts`)
   - Review main connector implementation
   - Examine entry point exports (`src/index.ts`)

2. **Producer-to-tag mapping**:
   - Map existing producer files to API tags
   - Identify which operations each producer handles
   - Note producer method signatures and patterns
   - Document any custom producer logic or specializations

3. **Data mapping analysis**:
   - Document existing mapper functions
   - Note property transformation patterns
   - Identify enum mappings and type conversions
   - Check for any custom mapping logic

### 4. Analyze Test Coverage

Examine existing test structure and coverage:

1. **Unit test analysis**:
   - Document existing unit test files in `test/unit/`
   - Map unit tests to producer implementations
   - Note test patterns and utilities used
   - Identify any custom test setup or helpers

2. **Integration test analysis**:
   - Document existing integration test files in `test/integration/`
   - Check for `Common.ts` and `prepareApi()` patterns
   - Note credential handling and environment setup
   - Identify test fixtures and mock data patterns
   - Document which operations have integration test coverage

3. **Test execution capabilities**:
   - Verify test scripts in `package.json`
   - Check for credential availability (from Task 01)
   - Note any special test configuration or requirements

### 5. Dependency Analysis

Analyze current dependencies and patterns:

1. **Dependency mapping**:
   - Document production dependencies used
   - Note HTTP client library (axios, fetch, etc.)
   - Identify core type dependencies
   - Check for any specialized utility libraries

2. **Pattern analysis**:
   - Document error handling patterns
   - Note authentication mechanisms used
   - Identify retry logic or rate limiting
   - Check for any custom utilities or helpers

### 6. Structural Validation

Validate the module structure against expected patterns:

1. **File structure validation**:
   - Verify all expected files are present
   - Check for any unexpected files or patterns
   - Note any deviations from standard module structure

2. **Naming convention validation**:
   - Verify producer naming matches interface names
   - Check for consistent naming patterns
   - Note any naming anomalies that might affect updates

3. **Architecture validation**:
   - Verify client/producer/connector separation
   - Check for proper interface implementations
   - Validate export patterns and module organization

## Output Format

Store the following JSON in memory file: `.claude/.localmemory/update-{module-identifier}/task-02-output.json`

```json
{
  "status": "completed|failed|error",
  "timestamp": "{iso_timestamp}",
  "modulePath": "package/{vendor}/{service}",
  "apiAnalysis": {
    "specification": {
      "file": "api.yml",
      "version": "{api_version}",
      "title": "{api_title}",
      "baseUrl": "{base_url}",
      "authMethod": "{auth_method}"
    },
    "operations": [
      {
        "operationId": "{operation_id}",
        "method": "{http_method}",
        "path": "{api_path}",
        "tags": ["{tag_list}"],
        "summary": "{operation_summary}",
        "producer": "{expected_producer_name}"
      }
    ],
    "tags": [
      {
        "name": "{tag_name}",
        "description": "{tag_description}",
        "operationCount": "{operation_count}",
        "expectedProducer": "{producer_file_name}"
      }
    ]
  },
  "implementationAnalysis": {
    "producers": [
      {
        "file": "src/{Producer}Impl.ts",
        "className": "{ProducerClassName}",
        "interface": "{ImplementedInterface}",
        "tags": ["{handled_tags}"],
        "operations": ["{handled_operations}"],
        "methods": ["{implemented_methods}"]
      }
    ],
    "client": {
      "file": "src/{Service}Client.ts",
      "className": "{ClientClassName}",
      "httpLibrary": "{http_library}",
      "authPattern": "{auth_pattern}"
    },
    "connector": {
      "file": "src/{Service}Impl.ts",
      "className": "{ConnectorClassName}",
      "interface": "{ConnectorInterface}",
      "producerGetters": ["{producer_getter_methods}"]
    },
    "mappers": {
      "file": "src/mappers.ts",
      "functions": [
        {
          "name": "{mapper_function_name}",
          "inputType": "{input_type}",
          "outputType": "{output_type}",
          "mappedFields": ["{field_mappings}"]
        }
      ]
    },
    "entryPoint": {
      "file": "src/index.ts",
      "factoryFunction": "{factory_function_name}",
      "exports": ["{exported_items}"]
    }
  },
  "testAnalysis": {
    "unitTests": {
      "directory": "test/unit/",
      "files": ["{unit_test_files}"],
      "coverage": [
        {
          "producer": "{producer_name}",
          "testFile": "{test_file}",
          "coveredOperations": ["{tested_operations}"]
        }
      ]
    },
    "integrationTests": {
      "directory": "test/integration/",
      "exists": true,
      "credentialsAvailable": true,
      "files": ["{integration_test_files}"],
      "commonFile": "test/integration/Common.ts",
      "fixtures": "test/fixtures/integration/",
      "coverage": [
        {
          "producer": "{producer_name}",
          "testFile": "{test_file}",
          "coveredOperations": ["{tested_operations}"]
        }
      ]
    }
  },
  "dependencyAnalysis": {
    "httpClient": "{http_client_library}",
    "coreTypes": "{core_types_version}",
    "hubCore": "{hub_core_version}",
    "productPackage": "{product_package}",
    "customDependencies": ["{additional_dependencies}"]
  },
  "structuralValidation": {
    "status": "valid|anomalies|invalid",
    "expectedPatterns": {
      "producerNaming": "matches|deviates",
      "interfaceImplementation": "correct|issues",
      "fileOrganization": "standard|custom"
    },
    "anomalies": ["{list_of_structural_anomalies}"],
    "recommendations": ["{recommendations_for_updates}"]
  }
}
```

## Error Conditions

- **Task 01 not completed**: Stop execution, Task 01 must pass first
- **Module structure invalid**: Stop execution, ask user for guidance
- **API specification malformed**: Stop execution, require API spec fix
- **Critical files missing**: Stop execution, module may need recreation
- **Generated interfaces corrupted**: Stop execution, require regeneration

## Success Criteria

- Complete analysis of existing API operations and structure
- Mapping of operations to producers and implementation files
- Documentation of current test coverage and patterns
- Identification of dependencies and architectural patterns
- Validation that module structure supports update operations
- Clear baseline established for adding new operations

## Structural Anomaly Handling

If structural anomalies are discovered:

1. **Document anomalies clearly** in output file
2. **Assess impact on update process**:
   - Minor anomalies: Note and proceed with caution
   - Major anomalies: Stop and ask user for guidance
3. **Provide recommendations** for how to handle anomalies during updates
4. **Flag potential risks** that might affect new operation additions

## Next Steps

**On Success**: Proceed to Task 03 (Analyze Requested Operations)
**On Anomalies**: Proceed with documented cautions and recommendations
**On Invalid Structure**: Stop and ask user whether to recreate module or adjust approach