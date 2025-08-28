# Task 02: API Analysis and Operation Mapping

## Prerequisites

**🚨 CRITICAL**: Before starting this task, read `CLAUDE.md` to understand the project structure, rules, and requirements.

## Overview

This task performs comprehensive API documentation analysis, maps user-requested operations to API endpoints, analyzes available client libraries, and generates the complete module request specification.

## Input Requirements

- Task 01 output file: `.claude/.localmemory/{action}-{module-identifier}/task-01-output.json`
- Where `{module-identifier}` is the product identifier derived from the identified product package (e.g., `vendor-suite-service` from `@scope/product-vendor-suite-service`, or `vendor-service` from `@scope/product-vendor-service`)
- User prompt analysis to determine required operations based on intent

## Process Steps

### 0. Context Management and Goal Reminder

**🚨 MANDATORY FIRST STEP - CONTEXT CLEARING**: 
- **IGNORE all previous conversation context** - This task runs in isolation
- **CLEAR mental context** - Treat this as a fresh start with no prior assumptions
- **REQUEST**: User should run `/clear` or `/compact` command before starting this task for optimal performance

**🚨 MANDATORY SECOND STEP**: Read and understand the original user intent:

1. **Read initial user prompt**:
   - Load `.claude/.localmemory/{action}-{module-identifier}/task-01-output.json`
   - Where `{module-identifier}` is derived from the product package (see Task 01 for derivation rules)
   - Extract and review the `initialUserPrompt` field
   - Understand the original goal, scope, and specific user requirements

2. **Goal alignment verification**:
   - Ensure all analysis and decisions align with the original user request
   - Keep the user's specific intentions and scope in mind throughout the task
   - If any conflicts arise between task instructions and user intent, prioritize user intent

3. **Context preservation**:
   - Reference the original prompt when making operation mapping decisions
   - Ensure the analysis serves the user's actual needs, not generic assumptions

### 1. API Documentation Discovery

1. **Access primary documentation**:
   - Use base information from Task 01 output
   - Access primary documentation URL if available
   - **Proactively search for additional API documentation**:
     - Search for official API documentation websites
     - Look for REST API references, developer documentation
     - Check for enterprise/admin API sections if dealing with enterprise products
     - Search for GraphQL API documentation if applicable

2. **Parse available endpoints**:
   - Parse available endpoints and operations from ALL discovered documentation sources
   - **Priority**: Ensure comprehensive coverage rather than limiting to basic documentation
   - Look across different API sections (public API, enterprise API, admin API, etc.)

### 2. Credential Analysis and Authentication Method Selection

1. **Parse provided credentials from user prompt**:
   - **🚨 CRITICAL**: Extract ONLY the credentials explicitly mentioned in the `initialUserPrompt`
   - Parse environment variable names and credential types from user request
   - **Examples**:
     - "BITBUCKET_TOKEN" → Token-based authentication (1 field: token)
     - "API_KEY" → API Key authentication (1 field: key)  
     - "USERNAME and PASSWORD" → Basic Authentication (2 fields: username, password)
     - "BITBUCKET_TOKEN and BITBUCKET_EMAIL" → App Password authentication (2 fields: email, token)
   - **NEVER infer additional credentials** beyond what user explicitly provided
   - **NEVER assume standard patterns** if user didn't mention them

2. **Match authentication method to provided credentials**:
   - Research vendor's authentication options and match to provided credential pattern
   - **Priority order**: Exact credential match > Vendor preference > API documentation default
   - **If mismatch detected**: Document the mismatch but proceed with closest compatible method
   - **Document alternative methods**: If vendor supports multiple authentication types, list them in keyNotes

### 3. User Intent Analysis and Operation Mapping

1. **Analyze user prompt for resource intent**:
   - Parse the `initialUserPrompt` from Task 01 output to identify resource types and operation intent
   - **🚨 CRITICAL: CRUD Operation Synonym Mapping Rules**:
     - **READ Operations** (read/retrieval/get/retrieve/see/grab/fetch/show/display/list/view/obtain/access/query/find) → **ALWAYS** maps to BOTH `list*` AND `get*` operations
     - **CREATE Operations** (create/add/make/insert/new/build/establish/generate) → maps to `create*` operations ONLY
     - **UPDATE Operations** (update/change/upsert/modify/edit/patch/alter/revise) → maps to `update*` operations ONLY  
     - **DELETE Operations** (delete/remove/eliminate/erase/destroy/purge/clear) → maps to `delete*` operations ONLY
   - **🚨 NEVER MIX CRUD CATEGORIES**: Only include operations from the CRUD categories that were explicitly requested. If user asks for "read" operations, NEVER add create/update/delete operations
   - **Special Intent Mapping**:
     - "manage/handle/administer" → implies full CRUD operations (`list*`, `get*`, `create*`, `update*`, `delete*`)
     - "search/query/find" → maps to READ operations (`list*` AND `get*` operations)
   - **Resource identification**:
     - Extract all mentioned resources (e.g., "enterprises", "organizations", "users", "repositories")
     - For hierarchical relationships (e.g., "organizations users"), identify both parent and child operations
   - **Generate comprehensive operation list** based on intent analysis
   - **Examples**:
     - "Create a github module that retrieves enterprises, their organizations and organizations users" →
       - Resources: enterprises, organizations, users
       - Intent: "retrieves" → list + get operations
       - Operations: `listEnterprises`, `getEnterprise`, `listOrganizations`, `getOrganization`, `listEnterpriseOrganizations`, `listOrganizationMembers`, `getUser`
     - "Build a module to manage AWS IAM users and roles" →
       - Resources: users, roles
       - Intent: "manage" → full CRUD operations
       - Operations: `listUsers`, `getUser`, `createUser`, `updateUser`, `deleteUser`, `listRoles`, `getRole`, `createRole`, `updateRole`, `deleteRole`

2. **Map operations comprehensively**:
   - For each identified resource and operation intent from user prompt analysis
   - **CRITICAL**: For every resource mentioned, find BOTH list and get operations:
     - If user requests "listResources" → find both `listResources` AND `getResource` (single item)
     - If user requests "listEntities" → find both `listEntities` AND `getEntity` (single item)
     - If user requests "getCollection" → find both `getCollection` AND `listCollections` (multiple items)
   - **🚨 STRICT CRUD Category Compliance**: Find operations ONLY for the requested CRUD categories:
     - **READ Operations**: `list*`, `getAll*`, `fetch*`, `search*` (with pagination/filtering) AND `get*`, `fetch*`, `find*`, `retrieve*` (single item by ID/identifier)
     - **CREATE Operations**: `create*`, `add*`, `post*`, `insert*`, `new*` (ONLY if create was requested)
     - **UPDATE Operations**: `update*`, `modify*`, `patch*`, `put*`, `edit*` (ONLY if update was requested)  
     - **DELETE Operations**: `delete*`, `remove*`, `destroy*`, `purge*` (ONLY if delete was requested)
   - Find corresponding API endpoints in documentation by searching comprehensively:
     - Exact matches (e.g., "listEnterprises")
     - Variations (e.g., "getEnterprises", "enterprises", "/enterprises GET")
     - Related operations (e.g., "list enterprise organizations", "get enterprise orgs")
     - **Search across ALL API documentation sections** (public API, enterprise API, admin API, etc.)
   - **IMPORTANT**: Include ALL operations regardless of access level requirements:
     - Map operations that require enterprise admin privileges
     - Map operations that require site administrator access
     - Map operations that require special permissions or roles
     - **Never skip operations due to access level restrictions**
   - Normalize internal names to standard patterns: "list*", "get*", "create*", "update*", "delete*", "search*"
   - **Prioritize finding the most comprehensive operations** even if they're in different API sections

3. **Final authentication method validation**:
   - **🚨 CRITICAL**: First analyze what credentials were provided during module development request
   - **🚨 STRICT CREDENTIAL ADHERENCE**: ONLY use authentication methods that match the exact credentials provided:
     - **If ONLY token provided** → Use token-based authentication (API Key, Personal Access Token, or Bearer Token)
     - **If ONLY username/password provided** → Use Basic Authentication  
     - **If token + email provided** → Use App Password or token authentication that requires email
     - **If multiple credential types provided** → Use the most secure method supported by the API
     - **NEVER assume missing credentials** → If email is required but not provided, flag as authentication mismatch
   - **Credential-Based Selection Rules**:
     - If API token/key provided → Use API Key authentication  
     - If personal access token provided → Use Personal Access Token authentication
     - If bearer token provided → Use Bearer Token authentication
     - If username/password provided → Use Basic Authentication
     - If app password + email provided → Use App Password authentication
     - **Note**: OAuth2 client credentials (client_id/client_secret) are deferred until OAuth2 support is added
   - **Authentication Method Validation**:
     - **🚨 CRITICAL**: If the vendor's primary authentication method requires credentials NOT provided by the user, document this as a credential mismatch
     - Research alternative authentication methods that work with provided credentials
     - If no compatible authentication method exists, flag as authentication incompatibility issue
     - Document both the recommended method (based on API docs) and the selected method (based on provided credentials)
   - **Research vendor-specific authentication**:
     - Look for vendor-specific connection profiles that match the provided credential type
     - Check authentication-related sections, security schemes, auth examples in vendor documentation
     - Verify the selected authentication method is supported by the vendor's API
     - Find alternative authentication methods if primary method doesn't match provided credentials
   - **Priority**: Provided credentials type > API documentation preferences
   - **Never override**: If specific credentials are provided, never suggest a different authentication method
   - **Credential Mismatch Handling**: If there's a mismatch between provided credentials and vendor requirements, document this clearly in keyNotes

### 4. Client Library Analysis

1. **Comprehensive client library analysis**:
   - **Official SDKs**: Search for official SDKs and client libraries maintained by the product/service provider
   - **HTTP clients**: When REST API is available, always consider axios as a client option
   - **Community libraries**: Check npm registry for existing JavaScript/TypeScript libraries
   - **Feature coverage analysis**: For each library, evaluate:
     - REST API support and coverage
     - GraphQL API support (if applicable to the service)
     - Authentication method support (tokens, OAuth, etc.)
     - TypeScript support and type definitions
     - Enterprise/admin API support
     - Webhook support
     - Rate limiting handling
     - Error handling capabilities
   - **Maintenance and quality assessment**:
     - Last update/release date
     - GitHub stars, forks, and activity
     - Issue resolution patterns
     - Community support and documentation quality
     - Breaking change frequency
   - **Performance and reliability**:
     - Bundle size considerations
     - Node.js and browser compatibility
     - Async/await vs callback patterns
     - Request retry mechanisms
   - **Ecosystem integration**:
     - Plugin/extension capabilities
     - Integration with popular frameworks
     - Middleware support
   - Document comprehensive findings with specific version numbers, feature matrices, and clear recommendations

### 5. Generate Module Request

Compile all discovered information into the complete module request JSON format.

## Output Format

Store the following JSON in memory file: `.claude/.localmemory/{action}-{module-identifier}/task-02-output.json`

```json
{
  "status": "completed|failed|error",
  "productPackage": "${product_package}",
  "modulePackage": "${module_package}",
  "packageVersion": "0.0.0",
  "description": "${service_name}",
  "repository": "https://github.com/zerobias-org/module",
  "author": "team@zerobias.org",
  "apiDocumentationUrl": "${api_documentation_url}",
  "authenticationMethod": "${authentication_method}",
  "providedCredentials": {
    "type": "${credential_type_provided}",
    "fields": ${actual_credential_fields_array},
    "source": "user_provided",
    "mismatch": "${true_if_vendor_requires_different_credentials}"
  },
  "baseUrl": "${base_url}",
  "serviceName": "${service_name}",
  "httpClientLibrary": "${recommended_http_client}",
  "availableClientLibraries": ${client_libraries_json},
  "operations": ${operations_json},
  "keyNotes": ${key_notes_array}
}
```

Where:
- `productPackage`: From Task 01 output
- `modulePackage`: Generated module package name (always `@scope/module-{module-identifier}` format)
- `apiDocumentationUrl`: Primary API documentation URL discovered
- `authenticationMethod`: Selected authentication method (must match provided credential type)
- `providedCredentials`: Details about credentials actually provided by user (never assume additional credentials)
  - `type`: The type of authentication based on provided credentials only
  - `fields`: Array of actual credential field names provided by user  
  - `source`: Always "user_provided" for this workflow
  - `mismatch`: Boolean indicating if vendor requires different credentials than what user provided
- `baseUrl`: API base URL for making requests
- `serviceName`: Human-readable service name
- `httpClientLibrary`: Recommended HTTP client library based on analysis
- `availableClientLibraries`: Array of discovered client libraries with details
- `operations`: JSON object with enhanced operation mappings (see format below)
- `keyNotes`: Array of important findings and notes for future reference

### Enhanced Operations Format

**IMPORTANT**: Include comprehensive operation mapping with BOTH list and get operations for every resource:

```json
"operations": {
  "listEnterprises": {
    "docUrl": "url to list enterprises api doc",
    "method": "GET|GraphQL",
    "path": "/api/enterprises", // Optional: for REST/OpenAPI endpoints
    "body": "graphql_query", // Optional: for GraphQL or custom APIs
    "notes": "Lists all enterprises - pagination, filtering details"
  },
  "getEnterprise": {
    "docUrl": "url to get single enterprise api doc", 
    "method": "GET|GraphQL",
    "path": "/api/enterprises/{id}",
    "notes": "Gets single enterprise by ID/slug"
  },
  "listOrganizations": {
    "docUrl": "url to list organizations api doc",
    "method": "GET",
    "path": "/api/organizations",
    "notes": "Lists all organizations with pagination"
  },
  "getOrganization": {
    "docUrl": "url to get single organization api doc",
    "method": "GET", 
    "path": "/api/organizations/{id}",
    "notes": "Gets single organization by ID"
  }
}
```

**Operation Naming Convention**:
- `list*` - Get multiple items (with pagination, filtering)
- `get*` - Get single item by identifier
- `create*` - Create new item
- `update*` - Update existing item
- `delete*` - Delete item
- `search*` - Search with complex filters

### Available Client Libraries Format

```json
"availableClientLibraries": [
  {
    "name": "library-name",
    "type": "official|community|wrapper",
    "npmPackage": "@scope/package-name",
    "version": "1.2.3",
    "features": ["rest", "graphql", "webhooks"],
    "maintenance": "active|maintained|deprecated",
    "recommendation": "high|medium|low",
    "notes": "additional details"
  }
]
```

## Error Conditions

- **Task 01 output not found**: Stop execution, Task 01 must be completed first
- **API documentation not accessible**: Stop execution, report documentation issue
- **Requested operations not found**: Stop execution, inform user which operations are missing and wait for input

## Success Criteria

- API documentation successfully accessed and parsed
- All requested operations mapped to API endpoints **regardless of access level requirements**
- Authentication method selected
- Client libraries analyzed and recommendations provided
- Complete module request JSON generated and stored
