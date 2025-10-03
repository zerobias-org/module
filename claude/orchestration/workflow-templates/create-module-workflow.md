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

### Phase 1: Discovery & Planning

**Agents:** @product-specialist, @api-researcher, @operation-analyst, @credential-manager

**Actions:**
```
@product-specialist Research product and API
- Understand vendor's product
- Identify main resources
- Review API documentation
- Suggest resource naming

@credential-manager Setup credential infrastructure
- Identify authentication method
- Select core profile (tokenProfile, oauthClientProfile, etc.)
- Guide .env setup
- Verify credentials work

@api-researcher Test authentication endpoint
- Test connection with curl/node
- Verify credentials work
- Document auth flow
- Save test response

@operation-analyst Identify MVP operation
- Suggest first operation (list or get)
- Confirm with user if needed
- Plan future operations
```

**Deliverables:**
- Product understanding documented
- Authentication verified
- Credentials configured
- MVP operation identified

**Context saved:**
```
.claude/.localmemory/create-{module}/
├── _work/
│   ├── product-model.md
│   ├── api-research.md
│   ├── credentials-status.md
│   └── mvp-plan.md
```

---

### Phase 2: Scaffolding

**Actions:**
```
Run Yeoman generator:

yo @auditmation/hub-module

Inputs:
- Vendor name
- Suite name (optional)
- Service name
- Module name
- Description

Generated structure:
package/{vendor}/{suite?}/{service}/
├── api.yml
├── connectionProfile.yml
├── connectionState.yml
├── src/
├── test/
├── package.json
└── tsconfig.json
```

**Validate:**
- Files created
- Structure correct
- npm install succeeds

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
