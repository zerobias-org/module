# Task 03: Analyze Requested Operations

## Prerequisites

**🚨 CRITICAL**: Before starting this task, read `CLAUDE.md` to understand the project structure, rules, and requirements.

## Overview

This task analyzes the user's request to determine which specific new operations need to be added to the existing module. It identifies potential conflicts with existing operations and determines the implementation approach.

## Input Requirements

- Task 01 output file: `.claude/.localmemory/update-{module-identifier}/task-01-output.json`
- Task 02 output file: `.claude/.localmemory/update-{module-identifier}/task-02-output.json`
- Where `{module-identifier}` is derived from the existing module path
- User's initial request for new operations to add

## Process Steps

### 0. Context Management and Goal Reminder

**🚨 MANDATORY FIRST STEP**: Read and understand the original user intent:

1. **Read initial user prompt**:
   - Load task-01-output.json and extract `initialUserPrompt`
   - Parse the specific operations requested by the user
   - Understand scope and requirements for new operations

2. **Goal alignment verification**:
   - Focus on ONLY the operations specifically requested
   - Don't add operations not explicitly requested
   - Prioritize user intent over comprehensive API coverage

### 1. Parse User Request for Specific Operations

1. **Operation identification**:
   - Extract specific operation names, paths, or descriptions from user request
   - Identify if user requested specific HTTP methods (GET, POST, PUT, DELETE)
   - Note any specific functionality or endpoints mentioned
   - Document any constraints or preferences mentioned

2. **Scope determination**:
   - Determine if request is for specific operations or entire API domains
   - Check if user specified which existing producers to extend
   - Note any exclusions or limitations mentioned

### 2. Analyze Target API Documentation

Examine the product package API documentation to identify available operations:

1. **Product package analysis**:
   - Read from `node_modules/@{scope}/product-{vendor}-{service}/`
   - Examine API documentation or specification files
   - Identify all available operations that match user request

2. **Operation matching**:
   - Match user-requested operations to available API operations
   - Document full operation details (method, path, parameters, responses)
   - Note any operations that couldn't be matched

### 3. Conflict Analysis with Existing Operations

1. **Operation conflict detection**:
   - Compare requested operations against existing operations (from Task 02)
   - Check for exact matches (same method + path)
   - Identify potential conflicts or overlaps

2. **Conflict resolution strategy**:
   - **Exact matches**: Flag for user review - determine if existing is correct or needs update
   - **Path conflicts**: Check if different methods on same path are acceptable
   - **Parameter conflicts**: Verify parameter compatibility

3. **Conflict documentation**:
   - Document each conflict with specific details
   - Recommend resolution approach for each conflict
   - Flag cases requiring user decision

### 4. Producer Assignment Analysis

1. **Tag-based assignment**:
   - Identify API tags for new operations
   - Map tags to existing producers (from Task 02 analysis)
   - Determine which operations extend existing producers vs need new producers

2. **Producer extension strategy**:
   - **Existing producer**: Operations with matching tags extend existing producer
   - **New producer**: Operations with new tags require new producer creation
   - **Mixed assignment**: Some operations extend existing, others need new producers

### 5. Implementation Impact Assessment

1. **Code change assessment**:
   - Estimate required changes to existing files
   - Identify new files that need to be created
   - Assess potential breaking changes (should be none)

2. **Testing requirements**:
   - Determine unit tests needed for new operations
   - Plan integration tests for new functionality
   - Identify any special testing considerations

3. **Dependency assessment**:
   - Check if new operations require additional dependencies
   - Verify existing dependencies support new functionality

## Output Format

Store the following JSON in memory file: `.claude/.localmemory/update-{module-identifier}/task-03-output.json`

```json
{
  "status": "completed|failed|error",
  "timestamp": "{iso_timestamp}",
  "requestAnalysis": {
    "originalRequest": "{initial_user_prompt}",
    "parsedOperations": [
      {
        "userDescription": "{user_described_operation}",
        "identifiedOperation": "{matched_api_operation}",
        "method": "{http_method}",
        "path": "{api_path}",
        "operationId": "{operation_id}",
        "tags": ["{operation_tags}"],
        "matchConfidence": "high|medium|low"
      }
    ],
    "unmatchedRequests": ["{operations_not_found}"]
  },
  "conflictAnalysis": {
    "hasConflicts": true,
    "conflicts": [
      {
        "requestedOperation": "{operation_id}",
        "existingOperation": "{existing_operation_id}",
        "conflictType": "exact_match|path_overlap|parameter_mismatch",
        "severity": "blocking|warning|info",
        "recommendedResolution": "{resolution_strategy}",
        "requiresUserDecision": true
      }
    ],
    "safeToAdd": ["{operations_with_no_conflicts}"]
  },
  "producerAssignment": {
    "extendExisting": [
      {
        "producer": "{existing_producer_name}",
        "operations": ["{operations_to_add}"],
        "tags": ["{matching_tags}"]
      }
    ],
    "createNew": [
      {
        "producerName": "{new_producer_name}",
        "operations": ["{operations_for_new_producer}"],
        "tags": ["{new_tags}"]
      }
    ]
  },
  "implementationPlan": {
    "filesToModify": [
      {
        "file": "{file_path}",
        "changeType": "extend|create|modify",
        "description": "{change_description}"
      }
    ],
    "newFilesToCreate": [
      {
        "file": "{new_file_path}",
        "type": "producer|test|mapper",
        "description": "{file_purpose}"
      }
    ],
    "testingRequirements": {
      "unitTests": ["{operations_needing_unit_tests}"],
      "integrationTests": ["{operations_needing_integration_tests}"],
      "specialConsiderations": ["{testing_considerations}"]
    },
    "dependencyChanges": {
      "required": false,
      "newDependencies": ["{additional_dependencies_needed}"]
    }
  }
}
```

## Error Conditions

- **Task 02 not completed**: Stop execution, previous tasks must complete first
- **User request unclear**: Stop execution, ask user to clarify specific operations wanted
- **No matching operations found**: Stop execution, verify API documentation or user request
- **Blocking conflicts detected**: Stop execution, require user resolution of conflicts

## Success Criteria

- User request parsed and specific operations identified
- All requested operations matched to available API operations
- Conflicts with existing operations identified and resolution strategy documented
- Producer assignment strategy determined
- Implementation plan created with clear next steps
- Any blocking issues flagged for user resolution

## Conflict Resolution Guidelines

### Exact Matches (Blocking)
- **Same method + path**: Requires user decision
- **Options**: Keep existing, replace existing, or modify parameters
- **Recommendation**: Review existing implementation first

### Path Overlaps (Warning)
- **Different methods, same path**: Usually acceptable
- **Verify**: No parameter conflicts or response type issues

### Parameter Mismatches (Info)
- **Same operation, different parameters**: Check compatibility
- **Document**: Any parameter additions or changes needed

## Next Steps

**On Success**: Proceed to Task 04 (Update API Specification)
**On Conflicts**: Resolve conflicts before proceeding
**On Unclear Request**: Ask user for clarification on specific operations wanted