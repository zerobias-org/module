# Create Module Workflow

Complete workflow for creating a new module from scratch.

## Workflow Overview

**Duration:** 2-4 hours
**Gates:** All 6 gates
**Strategy:** MVP first (connect + one operation), then expand
**Outcome:** Working module ready for production

## Prerequisites

- Access to vendor API documentation
- Credentials available (or plan to skip integration tests)
- Yeoman generator configured

## MVP Strategy

**Minimum Viable Product:**
1. Connection (connect, disconnect, isConnected)
2. ONE operation (typically list or get)

**Rationale:** Validate entire stack works before adding more operations

## Workflow Phases

### Phase 1: Discovery & Planning (MINIMAL SCOPE)

**Agents:** @product-specialist, @api-researcher, @operation-analyst, @credential-manager

**Actions:**
```
@product-specialist Product package discovery and setup
- List product bundles to find matching product
- Extract module identifier from product package name
- Create memory folder: .claude/.localmemory/create-{module-identifier}/_work
- Install product package in _work directory
- Extract product info from index.yml or catalog.yml#Product (NO Operations)
- Document findings

@credential-manager Check credentials FIRST
- Check .env, .connectionProfile.json, root .env
- If missing: ASK user (provide now or continue without)
- Identify authentication method from API docs
- Select core profile (tokenProfile, oauthClientProfile, etc.)
- Guide .env setup if needed

@operation-analyst Select SINGLE MVP operation
- Review available API endpoints from docs
- Apply selection criteria:
  - PREFER: GET operations (read-only)
  - PREFER: No required parameters (or minimal)
  - PREFER: Returns list or simple object
  - AVOID: Complex setup or many dependencies
- Select ONE operation for MVP
- Document selection reasoning
- Save other operations for future

@api-researcher Research MINIMAL SCOPE (connection + ONE operation)
- Research connection/authentication endpoint ONLY
- Research the ONE operation selected by @operation-analyst
- Test BOTH endpoints with curl/node
- Save example responses (2 files only)
- Document auth flow, rate limits, base URL
- DO NOT research other operations yet
```

**Deliverables:**
- Module identifier extracted
- Product package installed in _work directory
- Product information extracted (NO Operations)
- Credentials checked and configured
- SINGLE MVP operation selected with reasoning
- Connection + ONE operation tested and documented

**Context saved:**
```
.claude/.localmemory/create-{module-identifier}/
├── _work/
│   ├── node_modules/{product-package}/  # Temporarily installed
│   ├── product-model.md                 # Product info (NO Operations)
│   ├── credentials-status.md
│   ├── operation-selection.md           # ONE operation + reasoning
│   ├── api-research.md                  # Connection + ONE operation
│   └── test-responses/
│       ├── auth-response.json           # Connection test
│       └── operation-response.json      # ONE operation test
```

---

### Phase 2: Module Scaffolding

**Agent:** @module-scaffolder

**Actions:**
```
@module-scaffolder Execute Yeoman scaffolding
- Extract parameters from Phase 1 outputs
- Determine module path (vendor/suite?/service)
- Run Yeoman generator:
  yo @auditmation/hub-module \
    --productPackage '${product_package}' \
    --modulePackage '${module_package}' \
    --packageVersion '0.0.0' \
    --description '${service_name}' \
    --repository 'https://github.com/zerobias-org/module' \
    --author '${author}'
- Navigate to module directory
- Run npm run sync-meta (syncs title/version to api.yml)
- Install dependencies: npm install
- Create symlinks (.npmrc, .nvmrc) if root configs exist
- Validate complete structure
- Run initial build validation (must pass with stubs)
- Git commit: "chore: scaffold {module} module"
```

**Deliverables:**
- Module directory created: package/{vendor}/{suite?}/{service}/
- Stub files generated:
  - api.yml (template with x-product-infos)
  - connectionProfile.yml (stub)
  - connectionState.yml (stub)
  - src/index.ts
  - src/{Service}Impl.ts
  - test/unit/{Service}Test.ts
- Dependencies installed
- Initial build passing
- Git commit created

**Validation Checklist:**
- ✅ package.json has moduleId and correct metadata
- ✅ api.yml has x-product-infos reference
- ✅ api.yml has title and version synced
- ✅ Source files exist with proper structure
- ✅ Test files exist
- ✅ npm install completed
- ✅ npm run build passes
- ✅ Git commit created

**DEFER to Phase 3:**
- Designing real connectionProfile.yml schema (currently stub)
- Designing real connectionState.yml schema (currently stub)
- Designing real api.yml specification (currently template)

---

### Phase 3: API Specification Design

**Agents:** @api-architect, @schema-specialist, @security-auditor, @api-reviewer

**Actions:**
```
@security-auditor Design authentication approach
- Select security scheme (bearer, oauth2, etc.)
- Review connectionProfile.yml
- Validate connectionState.yml includes expiresIn

@api-architect Design minimal API spec
- Connection operation (if needed)
- ONE MVP operation (list or get)
- Clean path structure
- Proper operation naming

@schema-specialist Design schemas
- Connection profile/state schemas
- Resource schema for MVP operation
- Use $ref for composition
- Apply proper formats

@api-reviewer Validate specification (Gate 1)
- Check all rules
- Verify no violations
- Validate naming

IF Gate 1 FAILS:
  Fix and re-validate

IF Gate 1 PASSES:
  Proceed to generation
```

**Deliverables:**
- api.yml with connect + one operation
- Schemas designed
- connectionProfile.yml configured
- connectionState.yml configured
- Gate 1 PASSED

---

### Phase 4: Type Generation

**Agent:** @build-validator

**Actions:**
```
@build-validator Generate types (Gate 2)

npm run generate

Validate:
- Types generated
- No InlineResponse
- Compiles successfully

IF Gate 2 FAILS:
  Fix spec and regenerate

IF Gate 2 PASSES:
  Proceed to implementation
```

---

### Phase 5: Implementation

**Agents:** @client-engineer, @operation-engineer, @mapping-engineer, @style-reviewer

**Actions:**
```
@client-engineer Implement Client class
- Create {Service}Client.ts
- Implement connect() method
- Implement disconnect() and isConnected()
- Configure HTTP client
- Initialize producers

@operation-engineer Implement Producer
- Create {Resource}Producer.ts
- Implement MVP operation
- Use generated types
- Validate inputs
- Build HTTP requests
- Call mappers

@mapping-engineer Create Mappers.ts
- Create mapper functions
- Use map() utility
- Validate required fields
- Convert snake_case to camelCase
- Apply core types

@style-reviewer Review code quality
- Check naming conventions
- Verify structure
- Validate patterns
```

**Deliverables:**
- Client implemented
- Producer implemented
- Mappers created
- Gate 3 criteria met

---

### Phase 6: Testing

**Agents:** @test-orchestrator, @mock-specialist, test engineers, reviewers

**Actions:**
```
@test-orchestrator Coordinate testing

@mock-specialist Setup mocks
- Connection mocks
- Operation mocks

@connection-ut-engineer Create connection unit tests
- Test connect()
- Test disconnect()
- Test isConnected()

@producer-ut-engineer Create operation unit tests
- Test MVP operation
- Success cases
- Error cases

@connection-it-engineer Create connection integration test
- Test real connection
- Use credentials from .env

@producer-it-engineer Create operation integration test
- Test real operation
- Use test data from .env

@ut-reviewer + @it-reviewer Review tests (Gate 4)

Run tests (Gate 5)
- npm test
- All must pass
```

**Deliverables:**
- Connection tests (unit + integration)
- Operation tests (unit + integration)
- Gates 4 & 5 PASSED

---

### Phase 7: Documentation

**Agent:** @documentation-writer

**Actions:**
```
@documentation-writer Create USERGUIDE.md

Include:
- Module overview
- Installation
- Authentication setup
- Connection example
- Operation usage examples
- Error handling
- Common patterns
```

**Deliverables:**
- USERGUIDE.md created

---

### Phase 8: Build & Finalization

**Agents:** @build-reviewer, @gate-controller

**Actions:**
```
@build-reviewer Build and validate (Gate 6)
- npm run build
- npm run shrinkwrap
- Verify artifacts

@gate-controller Validate all gates
- Check gates 1-6 all PASSED
- Verify module completeness
```

**Deliverables:**
- Build successful
- Dependencies locked
- All 6 gates PASSED
- MVP module complete

---

### Phase 9: Expansion (Optional)

If user wants more operations:
```
Execute update-module-workflow
- Add additional operations one at a time
- Each operation goes through full workflow
```

---

## MVP Success Criteria

Module is complete when:
- ✅ Connects to service
- ✅ One operation works end-to-end
- ✅ All tests pass
- ✅ Documentation exists
- ✅ All 6 gates PASSED
- ✅ Ready for production use

## Module Structure

```
package/{vendor}/{suite?}/{service}/
├── api.yml                    # OpenAPI spec
├── connectionProfile.yml       # Auth config
├── connectionState.yml         # Connection state
├── src/
│   ├── {Service}Client.ts     # Client class
│   ├── {Resource}Producer.ts  # Producer
│   ├── Mappers.ts             # Data mappers
│   └── index.ts               # Exports
├── test/
│   ├── {Service}ClientTest.ts # Connection tests
│   ├── {Resource}ProducerTest.ts # Unit tests
│   └── integration/
│       ├── Common.ts           # Test utilities
│       └── {Resource}IntegrationTest.ts
├── generated/                  # Generated types
├── dist/                       # Build output
├── USERGUIDE.md               # Documentation
├── package.json
├── npm-shrinkwrap.json
└── tsconfig.json
```

## Time Estimates

- Phase 1 (Discovery): 30-45 min
- Phase 2 (Scaffolding): 10 min
- Phase 3 (Design): 30-45 min
- Phase 4 (Generation): 5 min
- Phase 5 (Implementation): 45-60 min
- Phase 6 (Testing): 45-60 min
- Phase 7 (Documentation): 30 min
- Phase 8 (Build): 10 min

**Total MVP: 3-4 hours**

## Common Patterns

### Email/Password Authentication
```
Use: basicConnection.yml (from types-core)
State: tokenConnectionState.yml (with expiresIn)
```

### API Key/Token
```
Use: tokenProfile.yml (from types-core)
State: tokenConnectionState.yml (with expiresIn)
```

### OAuth Client Credentials
```
Use: oauthClientProfile.yml (from types-core)
State: oauthTokenState.yml (with refresh support)
```

## Success Indicators

- ✅ Module scaffolded correctly
- ✅ Authentication working
- ✅ One operation fully functional
- ✅ All tests passing
- ✅ Documentation complete
- ✅ Build successful
- ✅ Ready for expansion

## Next Steps After MVP

1. Add more operations (update-module-workflow)
2. Publish to npm
3. Integrate with production systems
4. Monitor usage and iterate
