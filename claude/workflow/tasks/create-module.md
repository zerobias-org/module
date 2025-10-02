# Task: Create Module

## Overview
Creates a new module from scratch with minimal implementation (connection + one operation).

## Responsible Personas
- **Product Specialist**: Product discovery and analysis
- **API Architect**: API specification design
- **Security Auditor**: Authentication analysis
- **TypeScript Expert**: Implementation lead
- **Integration Engineer**: HTTP client implementation
- **Testing Specialist**: Test creation
- **Documentation Writer**: USERGUIDE creation

## Prerequisites
- Module does not exist
- User has specified target product/service
- Credentials available (optional but recommended)

## Input
- User request with product/service name
- Optional: Credentials for testing

## Sub-Tasks

### 0. Context Management and Goal Reminder
**MANDATORY FIRST STEP - CONTEXT CLEARING**:
- IGNORE all previous conversation context
- CLEAR mental context
- REQUEST: User should run `/clear` or `/compact` command

**MANDATORY SECOND STEP**: Read and understand original user intent:
1. Load `task-01-output.json` if it exists
2. Extract `initialUserPrompt` field
3. Verify all decisions align with user's original intent

### 1. Product Discovery and Setup
**Persona**: Product Specialist
**Rules**: [api-specification.md](../../rules/api-specification.md#operation-mapping-requirements)

1. List product bundle dependencies:
   ```bash
   npm view @zerobias-org/product-bundle --json  # Get dependencies
   npm view @auditlogic/product-bundle --json    # Get dependencies
   ```
2. Identify matching product package:
   - Products follow format: `@scope/product-{vendor}{-suite?}-{product}`
   - Match case-insensitively by product component
   - If multiple matches: STOP and ask user to clarify
   - If not found: STOP and wait for user action
3. Create memory folder structure
4. Install product package temporarily
5. Extract basic product information:
   ```bash
   # Priority 1: Read index.yml
   cat node_modules/{product-package}/index.yml
   # Priority 2: Use yq to extract from catalog.yml
   yq '.Product' node_modules/{product-package}/catalog.yml
   ```

**Output**: `task-01-output.json`

### 2. External API Analysis
**Persona**: Product Specialist (with AI agent)
**Rules**: [api-specification.md](../../rules/api-specification.md#entity-discovery-checklist)

**OPTION A - Use Pre-existing Analysis:**
If `analyze-api` task was run beforehand:
1. Load results from `.claude/.localmemory/analyze-api-{vendor}-{product}/`
2. Reference `api-model.mmd` for entity relationships
3. Use operation prioritization from analysis

**OPTION B - Run Analysis Now:**
Execute [analyze-api.md](./analyze-api.md) task:
1. Comprehensive API documentation analysis
2. Create Mermaid entity relationship diagram
3. Map all entities and relationships
4. Identify essential operations

**Note**: The Mermaid diagram can be searched during operation selection (no need to keep it in context).

**Output**: `task-02-output.json`, `api-model.mmd`

### 3. API Analysis and Operation Mapping
**Personas**: Product Specialist + API Architect
**Rules**: [api-specification.md](../../rules/api-specification.md#operation-discovery-process)

1. Map operations to module structure
2. Determine essential operations for minimal module
3. Analyze operation dependencies
4. Select HTTP client library

**Output**: `task-03-output.json`

### 4. Check Prerequisites
**Persona**: Integration Engineer
**Rules**: [build-quality.md](../../rules/build-quality.md)

1. Verify Node.js and npm versions
2. Check Yeoman generator availability
3. Validate git configuration
4. Confirm lerna setup

**Output**: `task-04-output.json`

### 5. Scaffold Module with Yeoman
**Persona**: TypeScript Expert
**Rules**: [implementation.md](../../rules/implementation.md#file-organization)

1. Run Yeoman generator with exact command:
   ```bash
   yo @auditmation/hub-module \
     --productPackage '${product_package}' \
     --modulePackage '${module_package}' \
     --packageVersion '0.0.0' \
     --description '${service_name}' \
     --repository 'https://github.com/zerobias-org/module' \
     --author '${author}'
   ```
   Where:
   - `product_package`: From Task 02 (e.g., `@auditlogic/product-github-github`)
   - `module_package`: From Task 02 (e.g., `@zerobias-org/module-github-github`)
   - `service_name`: Short name from Task 02 (e.g., 'GitHub', not full description)
   - `author`: From user's CLAUDE.md or `team@zerobias.org`

2. Navigate to module directory
3. Run `npm run sync-meta` to sync metadata
4. Install dependencies
5. Initial build validation

**Output**: `task-05-output.json`

### 6. Define API Specification
**Personas**: API Architect + Security Auditor

**🚨 MANDATORY: Load Rules BEFORE Starting**
1. **READ**: `claude/rules/api-specification.md` - ALL 18 critical rules
2. **READ**: `claude/ENFORCEMENT.md` - Validation gates
3. **VERIFY**: Understanding of critical rules:
   - ❌ NEVER use 'describe' prefix (use 'get')
   - ✅ ONLY 200/201 responses (no 4xx/5xx)
   - ✅ All properties camelCase (not snake_case)
   - ✅ Nested objects MUST use $ref
   - ✅ Response schemas: Direct $ref to main business object ONLY (NO envelope: status/data/meta/response/result/caller/token)
   - ✅ NO connection context parameters (apiKey, token, baseUrl, organizationId) in operations

**Rules Reference**: [api-specification.md](../../rules/api-specification.md)

**Execution Steps:**
1. Research API documentation
2. Select authentication method based on credentials
3. Define minimal API endpoints (one operation)
4. Create schemas with proper formats (validate against Rule 18: nested objects use $ref)
5. Validate specification (run checks from ENFORCEMENT.md Gate 1)

**Output**: `task-06-output.json`, `api.yml`

### 7. Implement Module Logic
**Personas**: TypeScript Expert + Integration Engineer

**🚨 MANDATORY: Load Rules BEFORE Starting**
1. **READ**: `claude/rules/implementation.md` - All implementation patterns
2. **READ**: `claude/rules/error-handling.md` - Core error types
3. **READ**: `claude/rules/type-mapping.md` - Type conversions
4. **VERIFY**: Understanding critical rules:
   - ❌ NEVER guess signatures - READ generated/api/index.ts
   - ✅ ConnectorImpl extends ONLY generated interface (no other base classes)
   - ✅ metadata() ALWAYS: `async metadata(): Promise<ConnectionMetadata> { return { status: ConnectionStatus.Down } satisfies ConnectionMetadata; }`
   - ✅ isSupported() ALWAYS: `async isSupported(_operationId: string) { return OperationSupportStatus.Maybe; }`
   - ❌ NEVER customize metadata() or isSupported() - exact boilerplate only
   - ❌ NEVER add connection context params (apiKey, token, baseUrl, organizationId) to producer methods
   - ✅ USE core connectionProfile/connectionState when they match (tokenProfile, oauthClientProfile, etc.)
   - ✅ connectionState MUST include ALL refresh-relevant data (expiresIn, refreshToken if applicable)
   - ✅ connectionState MUST extend baseConnectionState.yml (includes expiresIn)
   - ✅ refresh() method can ONLY use data from ConnectionProfile + ConnectionState
   - ✅ No env vars in src/, use generated types, const output pattern

**Rules Reference**: [implementation.md](../../rules/implementation.md)

**Execution Steps:**
1. Configure TypeScript (`skipLibCheck: true`)
2. Create HTTP Client class (connection management only)
3. Implement connection logic
4. Run `npm run generate` FIRST (from api.yml)
5. **READ** `generated/api/index.ts` - Find {Service}Connector interface
6. Create mappers (validated against interfaces, const output pattern)
7. Implement minimal producer (using generated types)
8. Create {Service}ConnectorImpl:
   - Extends ONLY {Service}Connector (from generated)
   - Implement metadata(): `async metadata(): Promise<ConnectionMetadata> { return { status: ConnectionStatus.Down } satisfies ConnectionMetadata; }`
   - Implement isSupported(): `async isSupported(_operationId: string) { return OperationSupportStatus.Maybe; }`
   - Implement actual API operation methods from generated interface

**Output**: `task-07-output.json`

### 8. Integration Tests
**Persona**: Testing Specialist

**🚨 MANDATORY: Load Rules BEFORE Starting**
1. **READ**: `claude/rules/testing.md` - All test requirements
2. **VERIFY**: Understanding critical rules:
   - ✅ ONLY use nock for HTTP mocking (no jest.mock, sinon, fetch-mock)
   - ✅ Env vars only in Common.ts
   - ✅ 100% pass rate required

**Rules Reference**: [testing.md](../../rules/testing.md#integration-test-patterns)

**Execution Steps:**
1. Set up test environment (Common.ts for env vars)
2. Create integration test for connection
3. Test minimal operation
4. Handle credential discovery (skip suite with this.skip() if no creds)

**Output**: `task-08-output.json`

### 9. Unit Tests
**Persona**: Testing Specialist

**🚨 MANDATORY: Load Rules BEFORE Starting**
1. **READ**: `claude/rules/testing.md` - All test requirements
2. **VERIFY**: Understanding critical rules:
   - ✅ ONLY use nock for HTTP mocking (no jest.mock, sinon, fetch-mock)
   - ✅ Mock at HTTP level with nock, not at client/method level
   - ✅ Use fixtures for mock responses
   - ✅ 100% pass rate required

**Rules Reference**: [testing.md](../../rules/testing.md#unit-test-patterns)

**Execution Steps:**
1. Create unit tests for client
2. Test mappers (validate type conversions)
3. Test producer using nock to mock HTTP requests
4. Use sanitized fixtures for nock responses
5. Run tests - verify 100% pass rate

**Output**: `task-09-output.json`

### 10. Create User Guide
**Persona**: Documentation Writer
**Rules**: [documentation.md](../../rules/documentation.md#userguide-structure)

1. Document credential acquisition
2. Map to connection profile
3. List required permissions
4. Keep focused on authentication only

**Output**: `USERGUIDE.md`

### 11. Create README
**Persona**: Documentation Writer
**Rules**: [documentation.md](../../rules/documentation.md#readme-updates)

Only if special requirements exist:
1. Document special operation requirements
2. Note billing implications
3. List admin-only features

**Output**: `README.md` (if needed)

### 12. Implementation Summary
**All Personas**: Review and validate

1. Validate all tests pass
2. Build succeeds
3. Lint passes
4. Documentation complete

**Output**: `task-12-output.json`

## Context Management
- **Estimated Total**: 60-80% across all tasks
- **Heavy Tasks**: API specification (30%), Implementation (40%)
- **Split Points**: After scaffolding, after API spec, after implementation

## Decision Points
- Multiple product matches → Ask user
- No credentials → Use simplest auth method
- Essential operation selection → Simplest no-param operation

## Success Criteria
- Module connects successfully
- One operation works
- All tests pass
- Build succeeds
- USERGUIDE complete

## Git Commits
```bash
# After scaffolding
git commit -m "chore: scaffold {module} module"

# After API spec
git commit -m "feat: define minimal API specification"

# After implementation
git commit -m "feat: implement connection and {operation}"

# After tests
git commit -m "test: add unit and integration tests"

# Final
git commit -m "docs: add USERGUIDE documentation"
```

## Recovery Points
- After scaffold generation
- After API specification
- After implementation
- After tests pass