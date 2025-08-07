# Task 02: API Analysis and Operation Mapping

## Overview

This task performs comprehensive API documentation analysis, maps user-requested operations to API endpoints, analyzes available client libraries, and generates the complete module request specification.

## Input Requirements

- Task 01 output file: `.claude/.localmemory/{action}-{module-identifier}/task-01-output.json`
- User prompt analysis to determine required operations based on intent

## Process Steps

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

### 2. User Intent Analysis and Operation Mapping

1. **Analyze user prompt for resource intent**:
   - Parse the `initialUserPrompt` from Task 01 output to identify resource types and operation intent
   - **Intent correlation mapping**:
     - "retrieve/get/fetch" → implies BOTH `list*` AND `get*` operations (e.g., "retrieve entities" = `listEntities` + `getEntity`)
     - "list/show/display" → implies BOTH `list*` AND `get*` operations (comprehensive access to resources)
     - "manage/handle" → implies full CRUD operations (`list*`, `get*`, `create*`, `update*`, `delete*`)
     - "query/search/find" → implies `search*` AND `list*` AND `get*` operations
   - **Resource identification**:
     - Extract all mentioned resources (e.g., "resources", "entities", "items", "collections")
     - For hierarchical relationships (e.g., "resource items"), identify both parent and child operations
   - **Generate comprehensive operation list** based on intent analysis
   - **Examples**:
     - "Create a module that retrieves resources and their sub-resources" →
       - Resources: resources, sub-resources
       - Intent: "retrieves" → list + get operations
       - Operations: `listResources`, `getResource`, `listSubResources`, `getSubResource`, `listResourceSubItems`
     - "Build a module to manage entities and collections" →
       - Resources: entities, collections
       - Intent: "manage" → full CRUD operations
       - Operations: `listEntities`, `getEntity`, `createEntity`, `updateEntity`, `deleteEntity`, `listCollections`, `getCollection`, `createCollection`, `updateCollection`, `deleteCollection`

2. **Map operations comprehensively**:
   - For each identified resource and operation intent from user prompt analysis
   - **CRITICAL**: For every resource mentioned, find BOTH list and get operations:
     - If user requests "listResources" → find both `listResources` AND `getResource` (single item)
     - If user requests "listEntities" → find both `listEntities` AND `getEntity` (single item)
     - If user requests "getCollection" → find both `getCollection` AND `listCollections` (multiple items)
   - **Find ALL available versions and variants**:
     - List operations: `list*`, `getAll*`, `fetch*`, `search*` (with pagination/filtering)
     - Get operations: `get*`, `fetch*`, `find*`, `retrieve*` (single item by ID/identifier)
     - Create operations: `create*`, `add*`, `post*`, `insert*`
     - Update operations: `update*`, `modify*`, `patch*`, `put*`, `edit*`
     - Delete operations: `delete*`, `remove*`, `destroy*`
   - Find corresponding API endpoints in documentation by searching comprehensively:
     - Exact matches (e.g., "listResources")
     - Variations (e.g., "getResources", "resources", "/resources GET")
     - Related operations (e.g., "list resource items", "get resource details")
     - **Search across ALL API documentation sections** (public API, enterprise API, admin API, etc.)
   - **IMPORTANT**: Include ALL operations regardless of access level requirements:
     - Map operations that require enterprise admin privileges
     - Map operations that require site administrator access
     - Map operations that require special permissions or roles
     - **Never skip operations due to access level restrictions**
   - Normalize internal names to standard patterns: "list*", "get*", "create*", "update*", "delete*", "search*"
   - **Prioritize finding the most comprehensive operations** even if they're in different API sections

2. **Extract authentication methods**:
   - Identify all available authentication methods from API documentation
   - Look for authentication-related sections, security schemes, auth examples
   - Select most preferred method based on current instructions
   - Preference order: API Key > Basic Auth > Others > OAuth2

### 3. Client Library Analysis

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
     - Repository metrics and activity
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

### 4. Generate Module Request

Compile all discovered information into the complete module request JSON format.

## Output Format

Store the following JSON in memory file: `.claude/.localmemory/{action}-{module-identifier}/task-02-output.json`

```json
{
  "productPackage": "${product_package}",
  "modulePackage": "${module_package}",
  "packageVersion": "0.0.0",
  "description": "${service_name}",
  "repository": "https://github.com/zerobias-org/module",
  "author": "team@zerobias.org",
  "apiDocumentationUrl": "${api_documentation_url}",
  "authenticationMethod": "${authentication_method}",
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
- `authenticationMethod`: Selected authentication method
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
  "listResources": {
    "docUrl": "url to list resources api doc",
    "method": "GET|GraphQL",
    "path": "/api/resources", // Optional: for REST/OpenAPI endpoints
    "body": "graphql_query", // Optional: for GraphQL or custom APIs
    "notes": "Lists all resources - pagination, filtering details"
  },
  "getResource": {
    "docUrl": "url to get single resource api doc", 
    "method": "GET|GraphQL",
    "path": "/api/resources/{id}",
    "notes": "Gets single resource by ID/identifier"
  },
  "listEntities": {
    "docUrl": "url to list entities api doc",
    "method": "GET",
    "path": "/api/entities",
    "notes": "Lists all entities with pagination"
  },
  "getEntity": {
    "docUrl": "url to get single entity api doc",
    "method": "GET", 
    "path": "/api/entities/{id}",
    "notes": "Gets single entity by ID"
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