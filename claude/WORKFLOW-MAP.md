# 🗺️ Complete Workflow Map

Comprehensive map of all tasks and subtasks for module development workflows.

## 📋 Quick Navigation

| Workflow | When to Use | Task File |
|----------|-------------|-----------|
| **[Analyze API](#-analyze-api-workflow)** | Research API before creating module (optional) | `workflow/tasks/analyze-api.md` |
| **[Create Module](#-create-module-workflow)** | Create new module from scratch | `workflow/tasks/create-module.md` |
| **[Add Operation](#-add-operation-workflow)** | Add single operation to existing module | `workflow/tasks/add-operation.md` |
| **[Update Module](#-update-module-workflow)** | Add multiple operations to existing module | `workflow/tasks/update-module.md` |
| **[Fix Module](#-fix-module-workflow)** | Fix issues in existing module | `workflow/tasks/fix-module.md` |
| **Deprecate Module** | Deprecate/archive a module | ❌ **Not yet implemented** |

---

## 🔍 Analyze API Workflow

**Purpose**: Research and understand API structure before module creation (optional preparatory work)

**Input**: Vendor/Product name OR API documentation URL

**Output**: Entity relationship diagram, operation priorities, analysis summary

### Task Sequence

```
0. Context Management
   └─ Clear context, understand user intent

1. Identify Product Package (Optional)
   └─ Find product package if available
   └─ Install temporarily for context

2. API Documentation Discovery
   └─ Find official API documentation
   └─ Validate access to docs
   └─ Identify doc type (REST, GraphQL, etc.)

3. Comprehensive API Analysis
   └─ Identify all resource types (entities)
   └─ Map entity relationships
   └─ Document all operations
   └─ Identify data structures

4. Create Entity Relationship Diagram
   └─ Create Mermaid ERD
   └─ Map all entities and relationships
   └─ Include key attributes

5. Identify Essential Operations
   └─ Tier 1: Connection validation (simple GET)
   └─ Tier 2: Core CRUD operations
   └─ Tier 3: Advanced operations
   └─ Document operation dependencies

6. Generate Analysis Summary
   └─ API overview
   └─ Entity summary
   └─ Operation summary
   └─ Recommendations
```

### Outputs

```
.claude/.localmemory/analyze-api-{vendor}-{product}/
├── task-01-output.json          # Product package info
├── task-02-output.json          # Documentation URLs
├── task-03-output.json          # Detailed API analysis
├── api-model.mmd                # Mermaid entity diagram
├── task-05-output.json          # Prioritized operations
└── api-analysis-summary.md      # Human-readable summary
```

### Personas Involved
- Product Specialist (API research)
- API Architect (Entity modeling)

---

## 🚀 Create Module Workflow

**Purpose**: Create new module from scratch with connection + one operation

**Input**: Product/service name, optional credentials

**Output**: Working module with connection and minimal operation

### Task Sequence

```
0. Context Management and Goal Reminder
   ├─ MANDATORY: Clear all previous context
   ├─ Request user run /clear or /compact
   └─ Load task-01-output.json to understand original intent

1. Product Discovery and Setup
   ├─ List product bundle dependencies
   ├─ Identify matching product package
   ├─ Create memory folder structure
   ├─ Install product package temporarily
   └─ Extract basic product information
   └─ OUTPUT: task-01-output.json

2. External API Analysis
   ├─ OPTION A: Use pre-existing analyze-api results
   │   ├─ Load from .claude/.localmemory/analyze-api-{vendor}-{product}/
   │   ├─ Reference api-model.mmd
   │   └─ Use operation prioritization
   ├─ OPTION B: Run analyze-api task now
   │   ├─ Comprehensive API documentation analysis
   │   ├─ Create Mermaid entity relationship diagram
   │   ├─ Map all entities and relationships
   │   └─ Identify essential operations
   └─ OUTPUT: task-02-output.json, api-model.mmd

3. API Analysis and Operation Mapping
   ├─ Map operations to module structure
   ├─ Determine essential operations for minimal module
   ├─ Analyze operation dependencies
   └─ Select HTTP client library
   └─ OUTPUT: task-03-output.json

4. Check Prerequisites
   ├─ Verify Node.js and npm versions
   ├─ Check Yeoman generator availability
   ├─ Validate git configuration
   └─ Confirm lerna setup
   └─ OUTPUT: task-04-output.json

5. Scaffold Module with Yeoman
   ├─ Run Yeoman generator with exact command
   ├─ Navigate to module directory
   ├─ Run npm run sync-meta
   ├─ Install dependencies
   └─ Initial build validation
   └─ OUTPUT: task-05-output.json
   └─ GIT COMMIT: "chore: scaffold {module} module"

6. Define API Specification
   ├─ 🚨 LOAD RULES: api-specification.md, ENFORCEMENT.md
   ├─ Research API documentation
   ├─ Select authentication method
   ├─ Define minimal API endpoints (one operation)
   ├─ Create schemas with $ref for nested objects
   └─ Validate specification (Gate 1 checks)
   └─ OUTPUT: task-06-output.json, api.yml
   └─ GIT COMMIT: "feat: define minimal API specification"

7. Implement Module Logic
   ├─ 🚨 LOAD RULES: implementation.md, error-handling.md, type-mapping.md
   ├─ Configure TypeScript (skipLibCheck: true)
   ├─ Create HTTP Client class
   ├─ Implement connection logic
   ├─ Run npm run generate (from api.yml)
   ├─ READ generated/api/index.ts for {Service}Connector interface
   ├─ Create mappers (const output pattern)
   ├─ Implement minimal producer
   └─ Create {Service}ConnectorImpl
       ├─ Extends ONLY {Service}Connector (from generated)
       ├─ metadata(): Exact boilerplate only
       ├─ isSupported(): Exact boilerplate only
       └─ Implement API operation methods
   └─ OUTPUT: task-07-output.json
   └─ GIT COMMIT: "feat: implement connection and {operation}"

8. Integration Tests
   ├─ 🚨 LOAD RULES: testing.md
   ├─ Set up test environment (Common.ts for env vars)
   ├─ Create integration test for connection
   ├─ Test minimal operation
   └─ Handle credential discovery (skip if no creds)
   └─ OUTPUT: task-08-output.json

9. Unit Tests
   ├─ 🚨 LOAD RULES: testing.md
   ├─ Create unit tests for client
   ├─ Test mappers
   ├─ Test producer using nock for HTTP mocks
   ├─ Use sanitized fixtures for nock responses
   └─ Run tests - verify 100% pass rate
   └─ OUTPUT: task-09-output.json
   └─ GIT COMMIT: "test: add unit and integration tests"

10. Create User Guide
    ├─ Document credential acquisition
    ├─ Map to connection profile
    ├─ List required permissions
    └─ Keep focused on authentication only
    └─ OUTPUT: USERGUIDE.md

11. Create README
    └─ Only if special requirements exist
    └─ OUTPUT: README.md (if needed)
    └─ GIT COMMIT: "docs: add USERGUIDE documentation"

12. Implementation Summary
    ├─ Validate all tests pass
    ├─ Build succeeds
    ├─ Lint passes
    └─ Documentation complete
    └─ OUTPUT: task-12-output.json
```

### Validation Gates (Every Step)

```
Gate 1: API Specification
├─ No 'describe' operations
├─ ONLY 200/201 responses
├─ All properties camelCase
├─ Nested objects use $ref
├─ Response = main business object (no envelope)
├─ No connection context params in operations
└─ No nullable in api.yml

Gate 2: Type Generation
├─ npm run generate succeeds
├─ New types exist in generated/
└─ No InlineResponse types

Gate 3: Implementation
├─ No 'any' types in signatures
├─ Using generated types
├─ ConnectorImpl extends ONLY generated interface
├─ metadata() = exact boilerplate
├─ isSupported() = exact boilerplate
└─ No connection context in producer params

Gate 4: Test Creation
├─ Unit test file created/updated
├─ Integration test created/updated
├─ At least 3 test cases per operation
├─ ONLY nock for HTTP mocking
└─ No hardcoded test values in integration tests

Gate 5: Test Execution
├─ npm test passes (exit code 0)
├─ No test failures
└─ No regression

Gate 6: Build
├─ npm run build passes
├─ Distribution files created
├─ npm run shrinkwrap succeeds
└─ npm-shrinkwrap.json created
```

### Success Criteria
- ✅ Module connects successfully
- ✅ One operation works
- ✅ All tests pass
- ✅ Build succeeds
- ✅ USERGUIDE complete

### Personas Involved
- Product Specialist (Discovery, API analysis)
- API Architect (API specification)
- Security Auditor (Authentication)
- TypeScript Expert (Implementation)
- Integration Engineer (HTTP client)
- Testing Specialist (Tests)
- Documentation Writer (USERGUIDE)

---

## ➕ Add Operation Workflow

**Purpose**: Add single operation to existing module

**Input**: Module path, operation to add

**Output**: Fully implemented and tested operation

**Usage Contexts**:
1. **Standalone**: User requests single operation ("Add listWebhooks")
2. **Called by update-module**: For each operation in multi-operation request

### Task Sequence

```
0. Context Management and Goal Reminder
   ├─ MANDATORY: Clear all previous context
   ├─ Request user run /clear or /compact
   ├─ Load initial-request.json from memory folder
   ├─ Extract specific operation requested
   └─ Ensure focus on SINGLE operation only

1. Research Operation (Product Specialist)
   ├─ Test with curl first
   │   └─ curl -X GET "https://api.example.com/endpoint" -H "Authorization: ..."
   ├─ Save response in _work/test-responses/{operation}.json
   └─ Document findings:
       ├─ Endpoint path
       ├─ HTTP method
       ├─ Required parameters
       ├─ Response format
       ├─ Authentication needs
       └─ Special requirements

2. Update API Specification (API Architect)
   ├─ 🚨 LOAD RULES: api-specification.md, ENFORCEMENT.md Gate 1
   ├─ Add operation to api.yml
   │   ├─ No 'describe' prefix (use get/list/create/update/delete)
   │   ├─ ONLY 200/201 responses (no 4xx/5xx)
   │   ├─ All properties camelCase
   │   ├─ Nested objects use $ref
   │   ├─ Response = direct $ref (no envelope)
   │   ├─ No connection context params
   │   └─ Add descriptions from vendor docs
   └─ Validate: npx swagger-cli validate api.yml

3. Generate Interfaces (API Architect)
   ├─ npm run generate
   ├─ Verify build still passes: npm run build
   └─ Check generated code:
       ├─ New interfaces created
       ├─ Existing interfaces unchanged
       └─ Types properly generated

4. Implement Operation (TypeScript Expert + Integration Engineer)
   ├─ 🚨 LOAD RULES: implementation.md, error-handling.md, type-mapping.md
   ├─ READ generated/api/index.ts for exact types
   ├─ 4.1 Add Mapper (if new type)
   │   └─ In src/Mappers.ts: const output: Type pattern
   ├─ 4.2 Add to Producer
   │   ├─ Create or update src/{Resource}Producer.ts
   │   ├─ Use generated types (NO 'any')
   │   ├─ Implement operation method
   │   └─ Use client error handling
   └─ 4.3 Update Connector
       ├─ Add producer property
       └─ Add getter method (lazy initialization)

5. Add Tests (Testing Specialist)
   ├─ 🚨 LOAD RULES: testing.md
   ├─ 5.1 Integration Test
   │   ├─ Skip suite if no credentials: this.skip()
   │   ├─ Test real API call
   │   └─ Validate response structure
   └─ 5.2 Unit Test
       ├─ Use nock for HTTP mocking (ONLY nock!)
       ├─ Mock at HTTP level (not client/method)
       ├─ Test success case
       ├─ Test error cases
       └─ At least 3 test cases

6. Validate Everything
   ├─ npm run build → Must pass
   ├─ npm run test → Must pass
   ├─ npm run test:integration → Must pass
   └─ npm run lint → Must pass

7. Update Documentation (if needed)
   └─ Only if special requirements (permissions, rate limits, etc.)

8. Commit Changes
   └─ git commit -m "feat({resource}): add {operation} operation"
```

### Decision Points
- **Schema Reuse vs New**: Check if schema exists → Reuse with $ref
- **Producer Creation vs Extension**: Same resource → Add to existing; New resource → Create new

### Success Criteria
- ✅ Operation works with real API
- ✅ All tests pass
- ✅ No regression in existing tests
- ✅ Build succeeds
- ✅ Code follows patterns

### Personas Involved
- Product Specialist (Research)
- API Architect (Specification update)
- TypeScript Expert + Integration Engineer (Implementation)
- Testing Specialist (Test coverage)
- Documentation Writer (Docs if needed)

---

## 🔄 Update Module Workflow

**Purpose**: Add multiple operations to existing module (orchestrator for add-operation)

**Input**: Module path, list of operations to add

**Output**: All operations implemented and tested with zero breaking changes

**Relationship**: This is the **parent task** that calls **add-operation** for each individual operation.

### Task Sequence

```
0. Context Management and Goal Reminder
   ├─ MANDATORY: Clear all previous context
   ├─ Request user run /clear or /compact
   ├─ Load initial-request.json from memory folder
   ├─ Extract user's original update request
   └─ Maintain backward compatibility focus

1. Validate Prerequisites
   ├─ 🚨 FAIL FAST Requirements:
   │   ├─ Check git status (must be clean)
   │   ├─ Run build (must pass)
   │   ├─ Run tests (must pass)
   │   └─ Run lint (must pass)
   └─ OUTPUT: task-01-output.json

2. Analyze Existing Structure
   ├─ Document existing operations
   ├─ Map current producers
   ├─ Inventory existing schemas
   └─ List current test coverage
   └─ OUTPUT: task-02-output.json

3. Analyze Requested Operations
   ├─ Research requested operations in API docs
   ├─ Identify dependencies between operations
   ├─ Check for schema conflicts
   └─ Plan addition order
   └─ OUTPUT: task-03-output.json

4. Execute Add Operation Tasks (LOOP)
   ├─ FOR EACH operation to add:
   │   ├─ Execute add-operation.md task with:
   │   │   ├─ Module context from task-02
   │   │   ├─ Operation details from task-03
   │   │   └─ Previous operation results
   │   ├─ Validate success
   │   └─ Commit changes
   │
   └─ Example for 3 operations:
       ├─ Operation 1: listWebhooks
       │   ├─ Execute add-operation task
       │   ├─ Validate success
       │   └─ Commit: "feat(webhook): add listWebhooks operation"
       │
       ├─ Operation 2: createWebhook
       │   ├─ Execute add-operation task
       │   ├─ Validate success
       │   └─ Commit: "feat(webhook): add createWebhook operation"
       │
       └─ Operation 3: deleteWebhook
           ├─ Execute add-operation task
           ├─ Validate success
           └─ Commit: "feat(webhook): add deleteWebhook operation"

   └─ OUTPUT: task-04-{operation}-output.json for each

5. Final Validation
   ├─ 🚨 CRITICAL VALIDATION:
   │   ├─ All existing tests still pass
   │   ├─ Build succeeds
   │   ├─ Lint passes
   │   └─ No breaking changes detected
   └─ OUTPUT: task-05-output.json
   └─ GIT COMMIT: "chore: validate backward compatibility"
```

### 🚫 Failure Conditions (STOP IMMEDIATELY)
- Any existing test fails
- Build breaks
- Lint fails
- Breaking change detected
- Git state dirty

### Success Criteria
- ✅ All new operations work
- ✅ All existing tests pass
- ✅ All new tests pass
- ✅ Zero breaking changes
- ✅ Build succeeds

### Personas Involved
- Product Specialist (Research new operations)
- API Architect (Update specification)
- TypeScript Expert (Extend implementation)
- Integration Engineer (Extend producers)
- Testing Specialist (Add new tests)
- Security Auditor (Validate no security regression)

---

## 🔧 Fix Module Workflow

**Purpose**: Diagnose and fix issues in existing module (breaking changes allowed if needed)

**Input**: Module path, issue description

**Output**: Fixed module with all tests passing

### Task Sequence

```
0. Context Management and Goal Reminder
   ├─ MANDATORY: Clear all previous context
   ├─ Request user run /clear or /compact
   ├─ Read error messages carefully
   ├─ Identify type of failure (build, test, runtime, logic)
   └─ Document specific problem to fix

1. Diagnose the Issue
   ├─ Check Error Messages
   │   ├─ Build errors: npm run build
   │   ├─ Test failures: npm run test, npm run test:integration
   │   └─ Lint errors: npm run lint
   │
   ├─ Compare with Rules
   │   ├─ Check implementation vs implementation.md
   │   ├─ Check API spec vs api-specification.md
   │   └─ Check tests vs testing.md
   │
   └─ Debug the Operation
       ├─ Test with curl outside module
       ├─ Compare response with mapper expectations
       └─ Check if response matches api.yml schema

2. Identify Root Cause
   ├─ Build Failures
   │   ├─ Missing imports
   │   ├─ Type mismatches
   │   ├─ Wrong return types (connect() returns ConnectionState or void)
   │   └─ Non-null assertions (remove all '!')
   │
   ├─ Test Failures
   │   ├─ Wrong expectations
   │   ├─ Mapper issues (field mapping completeness)
   │   ├─ Mock mismatches
   │   └─ Missing credentials
   │
   └─ Runtime Errors
       ├─ Authentication fails (verify credential format)
       ├─ 404 errors (check endpoint paths)
       ├─ Type conversion errors (check mapper validations)
       └─ Missing required fields (add validation before mapping)

3. Apply the Fix
   ├─ Fix Implementation Issues (src/)
   │   ├─ Remove non-null assertions (!)
   │   ├─ Fix mapper pattern (const output: Type)
   │   ├─ Add required field validation
   │   └─ Use core error types
   │
   ├─ Fix API Specification (api.yml)
   │   ├─ Change snake_case to camelCase
   │   ├─ Remove root-level servers/security
   │   ├─ Fix path parameter names
   │   └─ Add missing schemas
   │
   └─ Fix Tests (test/)
       ├─ Update expectations to match implementation
       ├─ Fix mock responses
       ├─ Add missing test cases
       └─ Update fixtures with real data

4. Validate the Fix
   ├─ npm run clean
   ├─ npm run build → Must pass
   ├─ npm run test → Must pass
   ├─ npm run test:integration → Must pass
   └─ npm run lint → Must pass

5. Document Breaking Changes (if applicable)
   ├─ Update version in package.json (major bump)
   ├─ Document changes in CHANGELOG.md
   ├─ Update tests to reflect new behavior
   └─ Update documentation if API changed
```

### Common Fix Patterns

**Pattern 1: Fix Mapper Field Mismatch**
```typescript
// Diagnosis: Interface has 5 fields, mapper only maps 3
// Fix: Add missing field mappings
export function toResource(data: any): Resource {
  if (!data?.id) {
    throw new InvalidInputError('resource', 'Missing required fields');
  }
  const output: Resource = {
    id: map(UUID, data.id),
    name: data.name,
    createdAt: map(Date, data.created_at),
    status: toEnum(StatusEnum, data.status),
    owner: data.owner  // Was missing
  };
  return output;
}
```

**Pattern 2: Fix PagedResults Implementation**
```typescript
// Diagnosis: Pagination not working correctly
// Fix: Use manual field assignment
async list(results: PagedResults<Resource>): Promise<void> {
  const response = await this.client.get('/resources', {
    params: {
      page: results.pageNumber,
      per_page: results.pageSize
    }
  });

  results.items = response.data.items.map(toResource);

  if (response.data.total_count !== undefined) {
    results.count = response.data.total_count;
  }

  if (response.data.next_token) {
    results.pageToken = response.data.next_token;
  }
}
```

**Pattern 3: Fix Authentication**
```typescript
// Diagnosis: Auth token not being sent
// Fix: Add to axios headers
constructor() {
  this.httpClient = axios.create({
    baseURL: this.baseUrl,
    headers: {
      'Authorization': `Bearer ${this.connectionProfile.apiKey}`
    }
  });
}
```

### Success Criteria
- ✅ Build passes (npm run build exits with 0)
- ✅ All tests pass
- ✅ Lint passes
- ✅ Original issue is resolved
- ✅ No new issues introduced

### When to Escalate
If unable to fix after analysis:
1. Document findings
2. Identify what information is missing
3. Ask user for:
   - Additional context
   - Access to API documentation
   - Example of expected behavior
   - Permission for breaking changes

### Personas Involved
- Integration Engineer (Diagnosis and debugging)
- TypeScript Expert (Fix implementation)
- API Architect (Fix specification)
- Testing Specialist (Fix tests)

---

## ❌ Deprecate Module Workflow

**Status**: Not yet implemented

**When available, would include**:
- Mark module as deprecated in package.json
- Update README with deprecation notice
- Add deprecation warnings to code
- Document migration path
- Archive repository/package

---

## 🎯 Workflow Selection Guide

### User Request → Workflow Mapping

| User Says | Execute Workflow |
|-----------|------------------|
| "Analyze the {vendor} API" | **Analyze API** |
| "Create {vendor} {product} module" | **Create Module** |
| "Add {operation} to {module}" | **Add Operation** |
| "Add {operation1}, {operation2}, and {operation3}" | **Update Module** → calls Add Operation × 3 |
| "Fix {issue} in {module}" | **Fix Module** |
| "Tests are failing in {module}" | **Fix Module** |
| "Build is broken in {module}" | **Fix Module** |

### Context Usage Estimates

| Workflow | Estimated Context | Heavy Phases |
|----------|------------------|--------------|
| Analyze API | 30-40% | API analysis (20%), Diagram (10%) |
| Create Module | 60-80% | API spec (30%), Implementation (40%) |
| Add Operation | 30-40% | API spec, Generated code |
| Update Module | 40-60% per operation | API spec, Implementation |
| Fix Module | 20-40% | Diagnosis, validation |

### Recovery Points

All workflows include git commit checkpoints for easy rollback:

**Create Module**:
- After scaffolding
- After API specification
- After implementation
- After tests

**Add Operation**:
- After operation implementation and tests

**Update Module**:
- After each operation added

**Fix Module**:
- After each fix applied

---

## 📚 Key Principles

1. **Sequential Execution**: Tasks run in order with validation gates
2. **Zero Hallucination**: Always verify against documentation
3. **Test-Driven**: Write tests for every operation
4. **Backward Compatible**: Update module never breaks existing functionality
5. **Rule-Based**: Load and follow rules at each phase
6. **Incremental Commits**: Commit after each major milestone

---

## 🛠️ Quick Reference Commands

```bash
# Validate API Specification
npx swagger-cli validate api.yml

# Generate TypeScript Types
npm run generate

# Build Project
npm run build

# Run Tests
npm test
npm run test:integration

# Lint Code
npm run lint

# Lock Dependencies
npm run shrinkwrap

# Sync Package Metadata
npm run sync-meta
```

---

## 📖 Related Documentation

- [Execution Protocol](./EXECUTION-PROTOCOL.md) - How to execute workflows
- [Enforcement](./ENFORCEMENT.md) - Validation gates and checklists
- [API Specification Rules](./rules/api-specification.md)
- [Implementation Rules](./rules/implementation.md)
- [Testing Rules](./rules/testing.md)
- [Main Workflow](./workflow/WORKFLOW.md) - Unified workflow orchestration
