# Task 05: Define API Specification, Validate, and Sync Metadata

## Prerequisites

**🚨 CRITICAL**: Before starting this task, read `CLAUDE.md` to understand the project structure, rules, and requirements.

## ⚠️ CRITICAL IMPORTANCE ⚠️

**THIS IS THE MOST IMPORTANT TASK IN THE ENTIRE MODULE DEVELOPMENT PROCESS**

This task requires the HIGHEST level of attention, precision, and quality. NO shortcuts, NO approximations, NO "good enough" solutions. Take ALL the time necessary to ensure PERFECT compliance with every rule and requirement. The quality of this task directly impacts the entire module's functionality and maintainability.

**QUALITY OVER SPEED**: Spend whatever time and processing power is needed to achieve excellence.

## Overview

This task creates a comprehensive OpenAPI specification, defines connection profiles, validates the specification, and synchronizes metadata from package.json. It involves researching the target API documentation, selecting appropriate authentication methods using AI-powered analysis, and defining complete API endpoints and schemas.

## 🚨 CRITICAL RULES - MANDATORY COMPLIANCE 🚨

**⚠️ STOP AND READ EVERY RULE BEFORE STARTING IMPLEMENTATION ⚠️**

**ALL RULES MUST BE FOLLOWED WITHOUT EXCEPTION - VIOLATING ANY RULE MEANS TASK FAILURE:**

## 🛡️ PERMANENT RULE COMPLIANCE SYSTEM

**These instructions are added to prevent systematic rule violations in ANY context:**

### 📋 MANDATORY RULE RE-CHECK PROCESS
**Before EVERY major implementation step, you MUST:**

1. **Before defining endpoints**: Re-read rules 1-4 (immediate failure rules)
2. **Before defining parameters**: Re-read naming rules 5-10  
3. **Before defining schemas**: Re-read technical rules 11-12
4. **Before connection profiles**: Re-read profile selection requirements

### 🎯 RULE COMPLIANCE FIRST PRINCIPLE
**NEVER use "looks good" over following rules:**

- ❌ **WRONG**: "This looks more realistic" - using external API paths that violate consistency
- ❌ **WRONG**: "This looks cleaner" - creating custom profiles instead of reusing core types
- ❌ **WRONG**: "This looks better" - using mixed resource naming because it matches external API
- ✅ **CORRECT**: Follow rules exactly, even if result looks "weird" or "unrealistic"
- **Absolute Rule**: RULE COMPLIANCE > "looks good" > external API accuracy

### ✅ SYSTEMATIC VALIDATION AFTER EACH SECTION
**After completing each section, run these checks:**

- **After paths**: Verify consistent resource naming throughout
- **After parameters**: Verify all reused parameters are in components
- **After profiles**: Verify AI-recommended core type is used correctly

### 🔄 CONTEXT REMINDER SYSTEM
**For ANY similar task in the future:**

1. **Ask**: "What are the critical rules I must not violate?"
2. **List them explicitly** before starting implementation
3. **Check compliance** after each major section
4. **Treat rules as hard constraints** that cause task failure when violated, not guidelines

**CORE PRINCIPLE: Rules are constraints that cause task failure, not suggestions to ignore when convenient.**

### 🔴 IMMEDIATE FAILURE RULES (Check FIRST before any implementation):
1. **Root Level Restrictions**: NEVER add `servers` or `security` sections at root level
2. **Resource Naming Consistency**: Choose the term that makes most sense for users of the operations, prioritizing clarity and user understanding. Use consistently throughout.
   - ❌ BAD: `/resources` and `/res/{resourceId}` (mixed terms)
   - ✅ GOOD: `/resources` and `/resources/{resourceId}` (consistent)
3. **Operation Coverage**: ALL Task 02 operations MUST be implemented as API endpoints
4. **Parameter Reuse**: Path parameters used in multiple operations MUST be declared in `components/parameters`

### 🟡 NAMING & STRUCTURE RULES:
5. **Property Naming**: ALL property names MUST use camelCase - NEVER snake_case
   - ❌ BAD: `avatar_url`, `created_at`, `web_url`, `extern_uid`  
   - ✅ GOOD: `avatarUrl`, `createdAt`, `webUrl`, `externUid`
   - **CRITICAL**: Convert ALL external API snake_case properties to camelCase in schemas
   - **NO EXCEPTIONS**: Even if external API uses snake_case, module API must use camelCase
6. **Parameter Naming**: Use `orderBy` and `orderDir` (NOT `sortBy` and `sortDir`)
7. **Path Parameters**: Must be descriptive - specify identifier type (e.g., `{resourceId}`, `{userId}`)
8. **Resource Identifier Priority**: `id` > `name` > others (handle, key, etc.) - Always prefer `id` when available for operations. Exception: Use the most natural identifier for the service
9. **Tags**: Use singular nouns for tags
10. **Method Naming**: When `operationId` is `listResources` and tag is 'resource', the `x-method-name` becomes 'list' (e.g., `operationId: listItems` -> `x-method-name: list`)

### 🟢 TECHNICAL RULES:
11. **Pagination**: Use `pageTokenParam` from core types, NOT custom parameters like `since`
12. **Response Codes**: Only include 200 response codes

## 🔍 MANDATORY PRE-IMPLEMENTATION CHECKLIST

**BEFORE WRITING ANY API SPECIFICATION CODE, COMPLETE THIS CHECKLIST:**

□ Read all 12 rules above
□ Identify the ONE consistent resource term to use throughout (prioritizing user clarity)
□ List all Task 02 operations that must be implemented
□ Plan which path parameters will be reused and need component definitions
□ Verify no `servers` or `security` will be added at root level

## Input Requirements

- Task 04 output file: `.claude/.localmemory/{action}-{module-identifier}/task-04-output.json`
- Task 02 output file: `.claude/.localmemory/{action}-{module-identifier}/task-02-output.json`
- Where `{module-identifier}` is the product identifier derived from the identified product package (e.g., `vendor-suite-service` from `@scope/product-vendor-suite-service`, or `vendor-service` from `@scope/product-vendor-service`)
- Module scaffolding must be completed with base `api.yml` file

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
   - Ensure all API definition decisions align with the original user request
   - Keep the user's specific intentions and scope in mind throughout the task
   - If any conflicts arise between task instructions and user intent, prioritize user intent

3. **Context preservation**:
   - Reference the original prompt when making API specification decisions
   - Ensure the API definition serves the user's actual needs, not generic assumptions

### 1. Research Target API Documentation

Research the target service's API documentation and authentication methods:

1. **API Documentation Analysis**:
   - Access the API documentation URL from Task 02 output
   - Verify documentation is accessible and current
   - Document service name, base URL, and authentication methods

2. **Credential-Based Authentication Analysis**:
   - **🚨 MANDATORY FIRST STEP**: Analyze what credentials were provided during module development
   - **Credential-Based Selection**: Use the authentication method that matches provided credentials:
     - API Key/Token provided → API Key authentication
     - Personal Access Token provided → PAT authentication  
     - Bearer Token provided → Bearer Token authentication
     - Username/Password provided → Basic Authentication
     - **Note**: OAuth2 credentials (client_id/client_secret) are deferred until OAuth2 support is added
   - **Vendor Research**: Research vendor-specific authentication patterns that match the credential type
   - **Document**: Record the credential type provided and rationale for connection profile selection
   - **Never Override**: Use the authentication method that matches the provided credentials, not documentation preferences

### 2. Update API Specification Metadata

Update the base `api.yml` with service-specific information:

1. **Service Information**:
   - Set API title to `{service_name} Hub Module`
   - Set description to service name
   - Configure server base URL

2. **Service Configuration**:
   - Extract service name, base URL, and authentication method from Task 02 output
   - **CRITICAL**: Set `x-impl-name` to service name only (simple as possible, no prefixes, no suffixes)
   - **CRITICAL**: Do NOT set title, description, or version - these will be set by sync-meta script

### 3. Define Security Schemes

Configure authentication with OAuth2 security schema (ALWAYS use OAuth2 with scopes due to technical limitations):

1. **Security Scheme Definition** (ALWAYS use OAuth2 format with scopes):
   ```yaml
   securitySchemes:
     service-token:
       type: oauth2
       description: Authentication for the service API (actual method may be PAT/API Key/Basic)
       flows:
         authorizationCode:
           authorizationUrl: https://service.com/oauth/authorize
           tokenUrl: https://service.com/oauth/token
           scopes:
             read:resource: Description of read permission
             write:resource: Description of write permission
   ```

2. **Security Application Requirements**:
   - **CRITICAL**: ALWAYS use OAuth2 type regardless of actual authentication method (PAT, API Key, Basic Auth)
   - The OAuth2 schema with scopes defines what permissions the credentials need
   - Apply specific scopes to each operation based on required permissions
   - Document required permissions/scopes for each operation
   - Use meaningful scope names that describe the actual permissions needed

### 4. Define API Endpoints

**🚨 RULE CHECK: Before defining endpoints, re-read Resource Naming Consistency rule and choose ONE term that makes most sense for users**

Create comprehensive API endpoints based on service documentation:

1. **🚨 CRITICAL: Standardized Path Format Rules** (**MANDATORY COMPLIANCE**):
   - **🚨 NEVER use ugly external API path formats** like `{owner}/{resource}/{subResource}` or `/users/{username}/repos/{repo}`
   - **🚨 ALWAYS use clean standardized format**:
     - `/resources` (for list operations)
     - `/resources/{resourceId}` (for single resource operations)  
     - `/resources/{resourceId}/sub-resources` (for nested list operations)
     - `/resources/{resourceId}/sub-resources/{subResourceId}` (for nested single operations)
     - `/resources/{resourceId}/sub-resources/{subResourceId}/sub-sub-resources` (for deeper nesting)
   - **🚨 ALWAYS ignore original API paths**: Store original path mapping in output file for later implementation, but API spec MUST use standardized format
   - **🚨 Parameter names must describe what property/identifier is expected**:
     - ❌ BAD: `{id}`, `{name}`, `{owner}`, `{repo}` (generic, unclear)
     - ✅ GOOD: `{resourceId}`, `{userId}`, `{repositoryId}`, `{organizationId}` (specific, descriptive)
   - **Examples of correct standardized paths**:
     - Original: `/users/{username}/repos/{repo}` → Standardized: `/repositories/{repositoryId}` 
     - Original: `/orgs/{org}/members/{username}` → Standardized: `/organizations/{organizationId}/members/{memberId}`
     - Original: `/projects/{project}/issues/{issue_id}` → Standardized: `/projects/{projectId}/issues/{issueId}`

2. **Endpoint Definition**:
   - Reference operations from Task 02 output
   - Map each operation to proper HTTP method and path
   - Use singular nouns for tags (e.g., `item`, `group`, `resource`)
   - Include `x-method-name` for clean interface method names

3. **PagedResults Configuration**:
   - Add `pageNumberParam` and `pageSizeParam` to paginated endpoints
   - Include `pagedLinkHeader` in response headers
   - **🚨 CRITICAL PAGED RESULTS SCHEMA REQUIREMENT**: 
     - For paged endpoints, the response content schema MUST be of type `array`
     - Items MUST be the generic type `T` from `PagedResults<T>`
     - **NEVER** use `object` or nested properties like `data`, `results`, `items`
     - If original API returns results inside properties (data/results/items), store mapping in output file for later implementation
     ```yaml
     responses:
       '200':
         content:
           application/json:
             schema:
               type: array  # MANDATORY - never object
               items:
                 $ref: '#/components/schemas/ResourceType'  # Direct array of T
     ```
   - Reference core paged schemas:
     ```yaml
     parameters:
       - $ref: '#/components/parameters/pageNumberParam'
       - $ref: '#/components/parameters/pageSizeParam'
     responses:
       '200':
         headers:
           link:
             $ref: '#/components/headers/pagedLinkHeader'
     ```

4. **Parameter Standards**:
   - Use `orderBy` and `orderDir` instead of `sortBy` and `sortDir`
   - **CRITICAL**: When same parameter used in 2+ operations, declare in `components/parameters` and reference
   - **MANDATORY**: Use `pageTokenParam` from core types for pagination, NOT custom `since` parameters
   - **MANDATORY**: Use consistent resource naming - choose ONE term (most user-friendly) and use throughout
   - **MANDATORY**: Path parameters must be descriptive - specify what type of identifier (e.g., `{resourceId}`, `{itemId}`, `{groupId}`)
   - **MANDATORY**: Resource identifier priority order: `id` > `name` > others (handle, key, etc.)
   - **MANDATORY**: All path parameters used in 2+ operations MUST be declared in `components/parameters`
   - **🚨 CRITICAL: NO ENVIRONMENT VARIABLES**: All parameters needed for API calls MUST be available through path parameters, query parameters, or request body - NEVER assume parameters exist in environment variables
   - **MANDATORY**: If operations require contextual parameters (like organizationId, tenantId), these MUST be passed as path or query parameters in the API specification
   - Declare enum inputs in `components/schemas`
   - Use camelCase for parameter names
   - Examples of reusable parameters: `pageNumberParam`, `pageSizeParam`, `pageTokenParam`, `orderByParam`, `orderDirParam`, `organizationIdParam`, `tenantIdParam`

5. **Security Mapping**:
   - Attach security scheme to ALL operations with specific scopes:
     ```yaml
     security:
       - service-token: ["read:resource", "write:resource"]
     ```
   - Use the OAuth2 security scheme defined in step 3
   - **CRITICAL**: Specify exact scopes needed for each operation
   - Map operation requirements to appropriate scopes (read, write, admin, etc.)

### 5. Define Data Schemas and Mappers

Create comprehensive data models in `components.schemas` and define required mappers:

1. **Schema Requirements**:
   - All property names must use camelCase
   - Use core types with proper format specifications
   - Each sub-type should be a separate schema with corresponding prefix
   - No nested objects - flatten to individual schemas
   - **CRITICAL**: When there are different models for list and get operations, use "Resource" for list and "ResourceInfo" for get response model

2. **Core Type Usage**:
   - `format: uuid` for UUID identifiers
   - `format: date-time` for timestamps
   - `format: email` for email addresses
   - `format: url` for absolute URLs
   - Only use format types when API guarantees exact format

3. **Common Schemas**:
   - Entity objects
   - Item objects
   - Resource objects
   - Error responses (reference core error types)

4. **🚨 CRITICAL MAPPER REQUIREMENTS**:
   - **MANDATORY PROPERTY COVERAGE**: ALL properties from input type MUST be mapped - never skip or omit properties
   - **AVOID UNNECESSARY CASTING**: Only use type casting when absolutely necessary for type safety
   - **MAPPER PATTERN**: Use function pattern: `raw: InputType => output: OutputType = { property1: raw.prop_1, property2: raw.prop_2 }`
   - **INFO MODEL EXTENSION**: For extended models: `raw: InputType => output: OutputTypeInfo = { ...mapperBase(raw), additionalProp1: raw.add_1 }`
   - **NESTED MODEL HANDLING**: 
     - Create mappers for nested models but DO NOT export them from Mappers.ts
     - Handle optional nested objects: `property1: raw.property1 ? mapProp1Model(raw.property1) : undefined`
     - Handle arrays: `property2: raw.property2?.map(mapProp2Model)`
   - **COMPLETE MAPPING**: Ensure every property in the input type has a corresponding mapping in the output
   - **PROPERTY NAME CONVERSION**: Convert snake_case input properties to camelCase output properties consistently

### 6. Create Connection Profile

**⚠️ MANDATORY AI-POWERED PROFILE SELECTION ⚠️**

You MUST use AI analysis to find and select the most appropriate existing connection profile. Creating a custom profile is ONLY allowed when AI analysis confirms NO existing profile satisfies the authentication requirements.

1. **AI-Powered Profile Analysis Based on Provided Credentials**:
   Use the Task tool to analyze available connection profiles that match the provided credential type:
   
   **Task prompt**:
   ```
   Analyze the provided credentials for this module development and find matching connection profiles.
   
   CRITICAL: First determine what type of credentials were provided:
   - Check Task 01 output for any credential information
   - Look for client_id/client_secret, api_key/token, username/password, bearer_token, personal_access_token
   - If no credentials were explicitly provided, note this and proceed with vendor research
   
   Then search for and analyze connection profiles in @auditmation/types-* packages that match the credential type:
   
   For vendor {VENDOR_NAME} with credential type {CREDENTIAL_TYPE}:
   1. Search for vendor-specific profiles: @auditmation/types-{vendor}/**/*profile.yml
   2. Search for credential-type-specific profiles matching the provided credential structure
   3. Focus on simple authentication methods: API Key, Personal Access Token, Bearer Token, Basic Auth
   4. Analyze field compatibility between provided credentials and profile requirements
   5. Rate compatibility focusing on exact credential field matching (not generic auth methods)
   
   Based on credential analysis, recommend:
   - The profile that best matches the provided credential structure
   - Whether the profile can accommodate the specific credential fields provided
   - If extension needed, specify fields to accommodate the provided credentials
   - Never recommend OAuth2 profiles or different credential types than what was provided
   
   Focus on: matching the exact credential structure provided, avoiding OAuth2 complexity
   ```

2. **Profile Implementation Based on AI Analysis**:
   
   **Option A - Direct Reference (when AI recommends exact match)**:
   ```yaml
   $ref: "./node_modules/@auditmation/types-{package}/schema/{profile}.yml"
   ```
   
   **Option B - Extension (when AI recommends extending existing profile)**:
   ```yaml
   allOf:
     - $ref: "./node_modules/@auditmation/types-{package}/schema/{profile}.yml"
     - type: object
       properties:
         additionalServiceSpecificField:
           type: string
           description: Service-specific requirement identified by AI
   ```
   
   **Option C - Custom Profile (ONLY when AI confirms no existing profile works)**:
   Create custom profile following core type patterns

3. **Profile Requirements**:
   - **CRITICAL**: Follow AI analysis recommendations based on provided credentials
   - **CRITICAL**: Profile MUST accommodate the exact credential structure provided
   - **CRITICAL**: Do NOT change credential types - use what was provided
   - **CRITICAL**: Do NOT create custom profile unless no existing profile matches the provided credential structure
   - **CRITICAL**: Use AI-recommended approach that matches provided credentials (direct reference vs extension)
   - Document provided credential type and AI analysis rationale in task output
   - Profile selection MUST align with credential type from Task 02 analysis

### 7. API Specification Style Compliance

Ensure the specification follows established guidelines:

1. **Naming Conventions**:
   - Path names: lowercase with hyphens
   - Property names: camelCase
   - Enum values: snake_case
   - Tags: singular nouns

2. **OpenAPI Requirements**:
   - **NEVER** add `servers` or `security` sections at root level
   - Skip servers and security fields (not needed)
   - Use `x-impl-name` with clean class prefix
   - Include proper operation summaries starting with "Retrieves" for GET
   - **No unnecessary comments**: Remove category comments or any comments not needed for future reference

3. **Pagination and Headers**:
   - Include `link` header for paged results
   - Reference `pagedLinkHeader` from core types

### 8. Verify Complete Operation Coverage

**CRITICAL**: Ensure all operations from Task 02 are implemented in the API specification:

1. **Operation Coverage Verification**:
   - Read Task 02 output: `.claude/.localmemory/{action}-{module-identifier}/task-02-output.json`
   - Extract all operation keys from `operations` object in Task 02 output
   - Verify each operation is implemented as an endpoint in `api.yml`
   - **MANDATORY**: If any operations are missing, add them immediately before proceeding

2. **Missing Operation Detection**:
   ```bash
   # Count operations in Task 02 output
   TASK02_OPS=$(cat .claude/.localmemory/{action}-{module-identifier}/task-02-output.json | jq '.operations | keys | length')
   
   # Count paths in API specification  
   API_PATHS=$(yq eval '.paths | keys | length' api.yml)
   
   # Compare counts - they must match
   if [ "$TASK02_OPS" -ne "$API_PATHS" ]; then
     echo "ERROR: Missing operations in API specification"
     echo "Task 02 operations: $TASK02_OPS, API paths: $API_PATHS"
   fi
   ```

3. **Automatic Correction**:
   - If operations are missing, add them following the same patterns as existing endpoints
   - Use appropriate HTTP methods (GET for retrievals, POST for creation, etc.)
   - Apply consistent security scopes based on operation type
   - Ensure all path parameters are properly defined in `components/parameters`

4. **Verification Requirements**:
   - **MANDATORY**: Every operation from Task 02 must have a corresponding path in `api.yml`
   - **MANDATORY**: All endpoints must follow established naming conventions  
   - **MANDATORY**: All endpoints must have proper security scope mappings
   - **MANDATORY**: Path parameter consistency across all operations

### 9. Generate and Validate Interfaces

Generate TypeScript interfaces and validate both specification and generated code:

1. **Clean Previous Generation**:
   ```bash
   cd ${module_path}
   npm run clean
   ```

2. **Generate Interfaces**:
   ```bash
   cd ${module_path}
   npm run generate
   ```

3. **Validate Generated Code**:
   ```bash
   cd ${module_path}
   npm run transpile
   ```

4. **Check for Invalid Generated Names**:
   - Generated interfaces MUST NOT contain names like "InlineResponse" or "InlineRequestBody"
   - If found, these indicate API spec issues that need fixing
   - **MANDATORY**: If invalid names found, go back to `api.yml` and extract inline schemas into proper named schemas
   - Re-run generation after fixing API spec
   ```bash
   # Check for invalid generated names
   grep -r "InlineResponse\|InlineRequestBody" generated/ && echo "INVALID NAMES FOUND - FIX API SPEC" || echo "Generated names OK"
   ```

5. **Verify Generation Success**:
   ```bash
   ls -la generated/api/
   grep -c 'Connector' generated/api/index.ts
   grep -c 'Api' generated/api/index.ts
   ```

### 10. API Specification Validation and Sync

Complete validation and metadata synchronization using hybrid approach:

1. **Basic Validation**:
   ```bash
   npx swagger-cli validate api.yml
   npm run sync-meta
   ```

2. **Create Validation Script**:
   Create `claude/scripts/validate-api-spec.sh`:
   ```bash
   #!/bin/bash
   
   echo "🔍 Running Basic API Specification Validation..."
   
   # Colors for output
   RED='\033[0;31m'
   GREEN='\033[0;32m'
   NC='\033[0m' # No Color
   
   ERRORS=0
   
   # Function to run check and report
   run_check() {
       local description="$1"
       local command="$2"
       local expected="$3"
       
       echo -n "Checking: $description... "
       
       if result=$(eval "$command" 2>/dev/null); then
           if [[ "$result" == "$expected" ]] || [[ -z "$expected" ]]; then
               echo -e "${GREEN}PASS${NC}"
           else
               echo -e "${RED}FAIL${NC} (got: $result, expected: $expected)"
               ((ERRORS++))
           fi
       else
           echo -e "${RED}ERROR${NC} (command failed)"
           ((ERRORS++))
       fi
   }
   
   # Rule compliance checks
   run_check "No root level servers/security" \
     "yq eval 'has(\"servers\") or has(\"security\")' api.yml | grep -q 'true' && echo 'ERROR' || echo 'PASS'" \
     "PASS"
   
   run_check "Descriptive parameter names" \
     "yq eval '.components.parameters | keys' api.yml | grep -E '^(id|name)$' && echo 'ERROR' || echo 'PASS'" \
     "PASS"
   
   run_check "OpenAPI validation" \
     "npx swagger-cli validate api.yml >/dev/null 2>&1 && echo 'PASS' || echo 'FAIL'" \
     "PASS"
   
   run_check "Connection profile exists" \
     "[ -f connectionProfile.yml ] && echo 'PASS' || echo 'FAIL'" \
     "PASS"
   
   # CamelCase property validation  
   run_check "All properties are camelCase (no snake_case)" \
     "yq eval '.. | select(type == \"!!map\") | keys | .[]' api.yml | grep -E '_[a-z]' && echo 'FAIL' || echo 'PASS'" \
     "PASS"
   
   # API linting check
   run_check "API linting validation" \
     "npm run lint:api >/dev/null 2>&1 && echo 'PASS' || echo 'FAIL'" \
     "PASS"
   
   # Operation coverage check
   run_check "Complete operation coverage" \
     "TASK02_OPS=\$(jq '.operations | keys | length' ../../.claude/.localmemory/{action}-{module-identifier}/task-02-output.json) && API_PATHS=\$(yq eval '.paths | keys | length' api.yml) && [ \"\$TASK02_OPS\" -eq \"\$API_PATHS\" ] && echo 'PASS' || echo 'FAIL'" \
     "PASS"
   
   echo
   if [ $ERRORS -eq 0 ]; then
       echo -e "${GREEN}✅ All basic validation checks passed!${NC}"
       exit 0
   else
       echo -e "${RED}❌ $ERRORS basic validation check(s) failed${NC}"
       exit 1
   fi
   ```

3. **Run Basic Validation Script**:
   ```bash
   chmod +x claude/scripts/validate-api-spec.sh
   ./claude/scripts/validate-api-spec.sh
   ```

4. **AI-Powered Semantic Validation**:
   Use the Task tool for comprehensive semantic rule compliance analysis:
   
   **Task prompt**:
   ```
   Perform comprehensive semantic rule compliance analysis of the {SERVICE_NAME} API specification.
   
   Analyze these critical rules requiring AI judgment:
   
   1. **Resource Naming Consistency**: 
      - Examine all paths in api.yml for consistent terminology
      - Look for semantic equivalents that should be unified (e.g., 'orgs' vs 'organizations', 'repos' vs 'repositories')
      - Verify the chosen term makes sense for API users and is used consistently
      - Flag any mixed terminology or inconsistent patterns
   
   2. **Operation Coverage Completeness**: 
      - Compare Task 02 operations with implemented API paths
      - Verify all operations are semantically covered (not just count matching)
      - Check if operation intent matches endpoint functionality
      - Confirm each Task 02 operation has appropriate corresponding endpoint
   
   3. **Parameter Reuse Logic**:
      - Identify path parameters used in multiple operations
      - Verify they're properly defined in components/parameters with appropriate names
      - Check parameter naming makes sense across different endpoint contexts
      - Validate parameter descriptions are accurate and helpful
   
   4. **Schema Naming Appropriateness**:
      - Verify Resource vs ResourceInfo distinction is logical and follows patterns
      - Check if schema names match their usage patterns and are user-friendly
      - Validate camelCase compliance while ensuring semantic meaning is preserved
      - Confirm schema structures match their intended use cases
   
   5. **Security Scope Mapping**:
      - Analyze if security scopes match operation requirements and follow logical patterns
      - Verify scope combinations make sense for each endpoint's functionality
      - Check that scope names are descriptive and match actual permissions needed
   
   Files to analyze:
   - api.yml (complete specification)
   - Task 02 output (.claude/.localmemory/{action}-{module-identifier}/task-02-output.json)
   - Connection profile analysis results
   
   For each rule, provide:
   - ✅ PASS or ❌ FAIL with detailed reasoning
   - Specific examples of any violations found with file locations
   - Clear suggestions for fixes if violations exist
   - Overall compliance assessment
   ```

5. **Final Validation Requirements**:
   - **MANDATORY**: Both basic script validation AND AI semantic validation must pass
   - **MANDATORY**: Interface generation must succeed without "InlineResponse" or "InlineRequestBody" names
   - **MANDATORY**: Generated TypeScript code must transpile without errors in `generated/` directory
   - **MANDATORY**: Fix any issues identified by validation methods and re-run until all checks pass
   - **MANDATORY**: If interface generation fails due to invalid names, fix API spec schemas and regenerate
   - Only proceed to task completion when all validations and generation are successful

## Output Format

Store the following JSON in memory file: `.claude/.localmemory/{action}-{module-identifier}/task-05-output.json`

```json
{
  "apiDefinition": {
    "status": "completed|failed",
    "timestamp": "${iso_timestamp}",
    "serviceName": "${service_name}",
    "baseUrl": "${base_url}",
    "authenticationMethod": "${authentication_method}",
    "apiDocumentationUrl": "${api_documentation_url}",
    "aiProfileAnalysis": {
      "providedCredentialType": "${credential_type_provided}",
      "credentialFields": "${credential_fields_identified}",
      "recommendedProfile": "${core_type_reference}",
      "rationale": "${ai_analysis_summary}",
      "profilesAnalyzed": "${number_of_profiles_analyzed}",
      "selectionMethod": "direct_reference|extension|custom_profile",
      "credentialCompatibility": "${how_profile_matches_provided_credentials}"
    },
    "files": {
      "apiSpec": {
        "path": "${module_path}/api.yml",
        "status": "created|updated",
        "validation": "valid|invalid",
        "endpointCount": "${endpoint_count}",
        "schemaCount": "${schema_count}"
      },
      "connectionProfile": {
        "path": "${module_path}/connectionProfile.yml",
        "status": "created",
        "authMethod": "${authentication_method}",
        "coreTypeUsed": "${core_type_reference}"
      },
      "validationScript": {
        "path": "claude/scripts/validate-api-spec.sh",
        "status": "created",
        "execution": "passed|failed"
      }
    },
    "securitySchemes": [
      {
        "name": "${security_scheme_name}",
        "type": "${security_type}",
        "description": "${security_description}"
      }
    ],
    "operations": {
      "implemented": "${operations_array}",
      "count": "${operation_count}"
    },
    "propertyMappings": {
      "description": "Mapping between module API spec property names and original API property names",
      "mappings": {
        "${spec_property_name}": "${original_api_property_name}"
      }
    },
    "mapperRequirements": {
      "description": "Mapper implementation requirements for type conversions",
      "completeCoverage": "All input properties must be mapped to output",
      "avoidCasting": "Only use type casting when absolutely necessary",
      "nestedHandling": "Nested models require non-exported helper mappers",
      "optionalHandling": "Handle optional nested objects and arrays safely",
      "namingConversion": "Convert snake_case input to camelCase output consistently"
    },
    "enumMappings": {
      "description": "Mapping between module API spec enum values and original API enum values",
      "mappings": {
        "${spec_enum_value}": "${original_api_enum_value}"
      }
    },
    "pathMappings": {
      "description": "Mapping between standardized API spec paths and original API paths for implementation",
      "mappings": {
        "${standardized_path}": "${original_api_path}"
      }
    },
    "operationExclusions": {
      "description": "Operations from Task 02 that were not implemented due to path conflicts",
      "excluded": [
        {
          "operationName": "${excluded_operation}",
          "reason": "Path conflict with ${chosen_operation}",
          "originalPath": "${original_api_path}",
          "standardizedPath": "${standardized_path_used}"
        }
      ]
    },
    "validations": [
      {
        "check": "API specification is valid OpenAPI 3.0",
        "command": "npx swagger-cli validate api.yml",
        "expected_exit_code": 0,
        "status": "passed|failed"
      },
      {
        "check": "Basic validation script execution",
        "command": "./claude/scripts/validate-api-spec.sh",
        "expected_exit_code": 0,
        "status": "passed|failed"
      },
      {
        "check": "AI semantic validation",
        "method": "Task tool analysis",
        "description": "Comprehensive semantic rule compliance check",
        "status": "passed|failed",
        "aiAnalysisSummary": "${ai_validation_results}"
      },
      {
        "check": "Connection profile exists and matches provided credential type",
        "file_exists": "${module_path}/connectionProfile.yml",
        "content_matches": "${provided_credential_structure}",
        "credential_type_used": "${credential_type_from_provided_creds}",
        "profile_compatibility": "${how_profile_accommodates_provided_creds}",
        "status": "passed|failed"
      },
      {
        "check": "Metadata synchronized from package.json",
        "command": "yq eval '.info.title' api.yml",
        "expected": "${service_name}",
        "status": "passed|failed"
      },
      {
        "check": "All Task 02 operations implemented in API specification",
        "description": "Verifies that every operation from Task 02 output has a corresponding endpoint in api.yml",
        "task02_operations_file": ".claude/.localmemory/{action}-{module-identifier}/task-02-output.json",
        "validation_method": "Both count matching and semantic analysis",
        "status": "passed|failed",
        "details": "${operation_coverage_details}"
      },
      {
        "check": "TypeScript interface generation successful",
        "command": "npm run generate && npm run transpile",
        "expected_exit_code": 0,
        "status": "passed|failed"
      },
      {
        "check": "Generated interfaces have proper names (no InlineResponse/InlineRequestBody)",
        "command": "grep -r 'InlineResponse\\|InlineRequestBody' generated/",
        "expected_exit_code": 1,
        "status": "passed|failed",
        "description": "Ensures no invalid inline schema names in generated interfaces"
      },
      {
        "check": "Mapper implementation requirements documented",
        "description": "Verifies mapper requirements are properly documented for implementation phase",
        "requirements_documented": {
          "complete_property_coverage": "All input properties mapped",
          "casting_minimization": "Avoid unnecessary type casting",
          "nested_model_handling": "Non-exported helper mappers for nested types",
          "safe_optional_handling": "Proper handling of optional objects and arrays"
        },
        "status": "passed|failed"
      },
      {
        "check": "Connector and API interfaces generated",
        "validation_method": "File content analysis",
        "files_checked": [
          "generated/api/index.ts",
          "generated/api/manifest.json"
        ],
        "status": "passed|failed"
      }
    ],
    "interfaceGeneration": {
      "status": "completed|failed",
      "generatedFiles": {
        "bundledSpec": "${module_path}/module-${vendor}{-suite?}-${product}.yml",
        "interfaces": "${module_path}/generated/api/index.ts",
        "manifest": "${module_path}/generated/api/manifest.json"
      },
      "validNames": "${no_inline_names_found}",
      "transpilationSuccess": "${transpilation_passed}"
    }
  }
}
```

## Error Conditions

- **Task 04 output not found**: Stop execution, module scaffolding must be completed first
- **Task 02 output not found**: Stop execution, API analysis must be completed first
- **API documentation inaccessible**: Stop execution, verify documentation URL
- **OpenAPI validation failed**: Fix syntax errors and retry
- **AI profile analysis failed**: Retry analysis or create custom profile with justification
- **Basic validation script failed**: Fix mechanical rule violations and retry
- **AI semantic validation failed**: Address semantic rule violations and retry
- **Missing operations from Task 02**: Add missing endpoints to API specification before proceeding
- **Operation count mismatch**: Verify all Task 02 operations are implemented as API paths
- **Interface generation failed**: Fix API specification schemas and retry generation
- **Invalid generated names found**: Extract inline schemas to proper named schemas in API spec
- **TypeScript transpilation failed**: Fix generated code issues by improving API specification

## Success Criteria

- OpenAPI specification is valid OpenAPI 3.0 format
- API specification contains defined paths and security schemes
- Connection profile exists with AI-recommended authentication configuration
- All operations from Task 02 are implemented in the specification
- Metadata is properly synchronized from package.json
- Specification follows established style guidelines
- Both basic script validation and AI semantic validation pass
- All 12 critical rules are verified as compliant
- TypeScript interfaces generate successfully without invalid names
- Generated code transpiles without errors in `generated/` directory
- Connector and API interfaces are properly created

## Mapper Implementation Guidelines

### Mandatory Mapping Patterns

1. **Basic Mapper Pattern**:
   ```typescript
   const mapResourceModel = (raw: InputType): OutputType => {
     return {
       property1: raw.prop_1,
       property2: raw.prop_2,
       // Map ALL properties - never skip any
     };
   };
   ```

2. **Extended Info Model Pattern**:
   ```typescript
   const mapResourceInfoModel = (raw: InputType): OutputTypeInfo => {
     return {
       ...mapResourceModel(raw),  // Base mapping
       additionalProp1: raw.add_1,
       additionalProp2: raw.add_2
     };
   };
   ```

3. **Nested Model Handling**:
   ```typescript
   // Helper mapper (NOT exported from Mappers.ts)
   const mapNestedModel = (raw: NestedInputType): NestedOutputType => ({ /* mapping */ });
   
   const mapMainModel = (raw: MainInputType): MainOutputType => {
     return {
       property1: raw.property1 ? mapNestedModel(raw.property1) : undefined,
       property2: raw.property2?.map(mapNestedModel),  // For arrays
       // ... other properties
     };
   };
   ```

### Critical Requirements
- **Complete Coverage**: Map every single property from input type
- **No Casting**: Avoid `as` casting unless absolutely required for type safety
- **Consistent Naming**: Convert snake_case to camelCase consistently
- **Non-Exported Helpers**: Nested model mappers must not be exported
- **Safe Optionals**: Use optional chaining and proper undefined handling

## Authentication Method Guidelines

### Priority Order (Simplest First)
1. **Personal Access Token** - Preferred for simplicity
2. **API Key** - Simple header-based authentication
3. **Bearer Token** - Generic token authentication
4. **Basic Auth** - Username/password authentication
5. **OAuth2** - **AVOIDED FOR NOW** - Deferred until OAuth2 support context is added

### OAuth2 Technical Requirement
- **MANDATORY**: Always use OAuth2 security schema format due to technical limitations
- Actual authentication method (PAT, API Key, Basic) is handled by connection profile
- OAuth2 scopes define required permissions for operations

## API Specification Guidelines

### Critical Rules
- **NEVER** add `servers` or `security` sections at root level
- **MANDATORY**: ALWAYS use OAuth2 security schema with scopes, regardless of actual authentication method (PAT, API Key, Basic Auth)
- **MANDATORY**: Apply specific scopes to each operation - never use empty scope arrays `[]`
- All property names must use camelCase
- Use `orderBy` and `orderDir` parameters (not `sortBy` and `sortDir`)
- **MANDATORY**: Use `pageTokenParam` from core types for pagination, NOT custom parameters like `since`
- **MANDATORY**: Use consistent resource naming - choose ONE term that makes most sense for users and use throughout
- **MANDATORY**: Path parameters must be descriptive - specify identifier type (e.g., `{resourceId}`, `{itemId}`, `{groupId}`)
- **MANDATORY**: Resource identifier priority order: `id` > `name` > others (handle, key, etc.)
- **MANDATORY**: Path parameters used in multiple operations MUST be declared in `components/parameters`
- Only include 200 response codes
- Use singular nouns for tags
- Reference core types and schemas when possible

### PagedResults Requirements
For endpoints that return paginated data:

1. **Parameter References**:
   ```yaml
   components:
     parameters:
       pageNumberParam:
         $ref: "./node_modules/@auditmation/types-core/schema/pageNumberParam.yml"
       pageSizeParam:
         $ref: "./node_modules/@auditmation/types-core/schema/pageSizeParam.yml"
     headers:
       pagedLinkHeader:
         $ref: "./node_modules/@auditmation/types-core/schema/pagedLinkHeader.yml"
   ```

2. **Response Headers**:
   ```yaml
   responses:
     '200':
       headers:
         link:
           $ref: '#/components/headers/pagedLinkHeader'
   ```

3. **Endpoint Parameters**:
   ```yaml
   parameters:
     - $ref: '#/components/parameters/pageNumberParam'
     - $ref: '#/components/parameters/pageSizeParam'
   ```

### Component Organization
- **Reusable Parameters**: Declare in `components/parameters`
- **Enum Inputs**: Declare in `components/schemas`
- **Security Scopes**: Map to each operation with detailed descriptions
- **Property Mappings**: Document spec-to-original API property name mappings

### Core Type Format Usage
Only use strict format types when API guarantees exact format:
- `format: uuid` - UUID strings (RFC 4122)
- `format: date-time` - ISO 8601 date-time with timezone
- `format: email` - Email addresses (RFC 5322)
- `format: url` - Absolute URLs with protocol

### Schema Organization
- Flatten nested objects to separate schemas
- Use descriptive schema names with service prefix
- Reference error schemas from `@auditmation/types-core`
- Include proper validation and descriptions
