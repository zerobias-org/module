# Add Operation Workflow

Complete workflow for adding a single operation to an existing module.

## Workflow Overview

**Duration:** ~30-45 minutes
**Gates:** All 6 gates (1→6)
**Agents:** 13 specialist agents
**Outcome:** Operation fully implemented, tested, and validated

## Prerequisites

- Module exists and builds successfully
- Git state is clean
- Tests are passing

## Workflow Phases

### 🔐 Phase 0: Credential Check (MANDATORY FIRST)

**Agent:** @credential-manager

**Actions:**
```
@credential-manager Check for credentials before starting work

IF credentials found:
  ✅ Note location (.env, .connectionProfile.json)
  ✅ Proceed to Phase 1

IF credentials NOT found:
  ⚠️  Report to user
  ❓ Ask: "Provide credentials now OR continue without?"

  IF user provides:
    ✅ Save to .env
    ✅ Proceed to Phase 1

  IF user says "continue without":
    ⚠️  Flag: Integration tests will be skipped
    ✅ Proceed to Phase 1

  IF no response:
    🛑 STOP - Cannot proceed without decision
```

**Deliverables:**
- Credential status confirmed
- User decision recorded (if credentials missing)

---

### 📋 Phase 1: Research & Analysis

**Agents:** @api-researcher, @operation-analyst

**Actions:**
```
@api-researcher Research [operation] endpoint
- Test with curl/node
- Document endpoint path, method, parameters
- Save response examples to _work/test-responses/
- Note pagination, authentication requirements

@operation-analyst Validate operation coverage
- Confirm operation in requirements
- Check dependencies on other operations
- Validate operation name follows conventions
```

**Deliverables:**
- API research findings (saved to `_work/api-research.md`)
- Operation coverage validated
- Test response examples saved

**Context for next phase:**
- Endpoint details (path, method, params)
- Response structure
- Authentication requirements

---

### 🎨 Phase 2: API Specification Design

**Agents:** @api-architect, @schema-specialist, @api-reviewer

**Actions:**
```
@api-architect Update api.yml with operation
- Use findings from @api-researcher
- Follow naming conventions (get/list/create/update/delete)
- Use "Retrieve" for get/list summaries
- Add operation to appropriate path
- Define parameters
- Define 200/201 responses ONLY (no 4xx/5xx)
- NO connection context parameters

@schema-specialist Design/update schemas
- Create schemas in components/schemas
- Use $ref for nested objects
- Apply proper formats (uuid, date-time, uri, email)
- Use allOf for composition
- NO nullable in spec
- NO inline schemas
- Separate by context if needed (Summary vs Full)

@api-reviewer Validate api.yml (Gate 1)
- Run all validation checks
- Check for rule violations
- Verify naming conventions
- Ensure no forbidden patterns

IF Gate 1 FAILS:
  🛑 STOP
  ❌ Report violations
  🔧 Fix: @api-architect + @schema-specialist
  🔄 Re-validate

IF Gate 1 PASSES:
  ✅ Proceed to Phase 3
```

**Deliverables:**
- Updated api.yml
- Schemas designed
- Gate 1 PASSED

**Validation:**
```bash
# No 'describe' operations
grep -E "operationId:.*describe[A-Z]" api.yml
# Must return nothing

# No error responses
grep -E "'40[0-9]'|'50[0-9]'" api.yml
# Must return nothing

# No nullable
grep "nullable:" api.yml
# Must return nothing
```

---

### ⚙️ Phase 3: Type Generation

**Agent:** @build-validator

**Actions:**
```
@build-validator Generate types and validate (Gate 2)

Commands:
npm run generate

Validate:
- Exit code must be 0
- Generated files exist in generated/
- No InlineResponse types
- No InlineRequestBody types
- TypeScript compiles without errors

IF Gate 2 FAILS:
  🛑 STOP
  ❌ Report generation errors
  🔧 Fix: @api-architect (spec issues)
  🔄 Regenerate

IF Gate 2 PASSES:
  ✅ Proceed to Phase 4
  ✅ Types available for implementation
```

**Deliverables:**
- Types generated in `generated/`
- Gate 2 PASSED

**Context for next phase:**
- Generated type interfaces
- Generated API methods

---

### 💻 Phase 4: Implementation

**Agents:** @operation-engineer, @mapping-engineer, @style-reviewer

**Actions:**
```
@operation-engineer Implement operation in Producer
- Import generated types
- Add method to Producer class
- Use Promise<GeneratedType> (NOT Promise<any>)
- Validate input parameters
- Build HTTP request
- Call mapper for response
- Throw core errors (not generic Error)
- NO connection context parameters

@mapping-engineer Create/update mappers in Mappers.ts
- Import generated types
- Use const output pattern
- PREFER map() from util-hub-module-utils
- Validate required fields from API spec
- Handle optional fields properly
- Convert snake_case to camelCase
- Apply core types (UUID, Email, URL, DateTime)
- NEVER weaken API spec to make mapper easier

@style-reviewer Review code quality
- Check naming conventions
- Verify import organization
- Validate code structure
- Check Mappers.ts has capital M
```

**Deliverables:**
- Operation implemented in Producer
- Mappers created/updated
- Code follows style guidelines
- Gate 3 criteria met

**Validation:**
```bash
# No Promise<any>
grep "Promise<any>" src/*Producer.ts
# Must return nothing

# Using generated types
grep "import.*from.*generated" src/*Producer.ts
# Must show imports

# No connection context parameters
grep -E "(apiKey|token|baseUrl):" src/*Producer.ts
# Must return nothing

# Mappers use map()
grep "import.*map.*from.*@auditmation/util-hub-module-utils" src/Mappers.ts
# Should show import
```

---

### 🧪 Phase 5: Testing

**Agents:** @test-orchestrator, @mock-specialist, @producer-ut-engineer, @producer-it-engineer, @ut-reviewer, @it-reviewer

**Actions:**
```
@test-orchestrator Coordinate testing strategy

@mock-specialist Setup HTTP mocks with nock
- Create mock responses for operation
- Match request pattern (method, path, headers)
- Return realistic mock data
- Setup cleanup (afterEach)

@producer-ut-engineer Create unit tests
- Test success case with mocked HTTP
- Test empty result case
- Test error cases (invalid params)
- Test HTTP client interaction
- At least 3 test cases
- ONLY nock for mocking
- Verify mock called (nock.isDone())

@producer-it-engineer Create integration tests
- Test with real API (if credentials available)
- Use test data from .env via Common.ts
- NO hardcoded test values
- Test actual response parsing
- Skip if credentials not available

@ut-reviewer Review unit tests (Gate 4)
- Check coverage (100% of new code)
- Verify only nock used
- Check test structure and assertions
- Validate test quality

@it-reviewer Review integration tests (Gate 4)
- Check no hardcoded values
- Verify all data from .env
- Validate real API usage
- Check test quality

IF Gate 4 FAILS:
  🛑 STOP
  ❌ Report test issues
  🔧 Fix: test engineers
  🔄 Re-review

IF Gate 4 PASSES:
  ✅ Proceed to test execution
```

**Test Execution (Gate 5):**
```
@test-orchestrator Run all tests

Commands:
npm test

Validate:
- Exit code must be 0
- All tests passing
- No regressions in existing tests
- New tests passing

IF Gate 5 FAILS:
  🛑 STOP
  ❌ Report failing tests
  🔧 Fix: @operation-engineer or test engineers
  🔄 Re-run tests

IF Gate 5 PASSES:
  ✅ Proceed to Phase 6
```

**Deliverables:**
- Unit tests created (3+ test cases)
- Integration tests created
- All tests passing
- Gates 4 & 5 PASSED

**Validation:**
```bash
# Test files exist
ls test/*Test.ts | grep -i "Operation\|Producer"

# Only nock for mocking
grep -E "jest\.mock|sinon|fetch-mock" test/*.ts
# Must return nothing

# No hardcoded values in integration tests
grep -E "(const|let|var) [a-zA-Z]*[Ii]d = ['\"][0-9]+['\"]" test/integration/*.ts
# Must return nothing
```

---

### 🏗️ Phase 6: Build & Finalization

**Agents:** @build-reviewer, @gate-controller

**Actions:**
```
@build-reviewer Build and validate (Gate 6)

Commands:
npm run build
npm run shrinkwrap

Validate:
- npm run build exit code = 0
- No TypeScript errors
- dist/ directory created
- JavaScript files generated
- npm run shrinkwrap exit code = 0
- npm-shrinkwrap.json created

IF Gate 6 FAILS:
  🛑 STOP
  ❌ Report build errors
  🔧 Fix: @typescript-expert + engineers
  🔄 Re-build

IF Gate 6 PASSES:
  ✅ Proceed to final validation

@gate-controller Validate all 6 gates

Check:
✅ Gate 1: API Specification
✅ Gate 2: Type Generation
✅ Gate 3: Implementation
✅ Gate 4: Test Creation
✅ Gate 5: Test Execution
✅ Gate 6: Build

IF ALL GATES PASSED:
  ✅ Operation complete
  📋 Ready for commit

IF ANY GATE FAILED:
  🛑 BLOCKED
  ❌ Report which gate(s) failed
  🔧 Route to appropriate agent
```

**Deliverables:**
- Build successful
- Dependencies locked
- All 6 gates passed
- Operation complete

---

## Completion Criteria

Operation is ONLY complete when:
- ✅ All 6 gates PASSED
- ✅ Operation implemented
- ✅ Tests created and passing
- ✅ Build successful
- ✅ Dependencies locked (npm-shrinkwrap.json)
- ✅ No violations or errors

## Context Files Created

```
.claude/.localmemory/{module}/
├── _work/
│   ├── api-research.md           # From @api-researcher
│   ├── operation-coverage.md     # From @operation-analyst
│   └── test-responses/
│       └── [operation].json      # Mock response example
├── phase-03-api-spec.json        # Phase 3 output (add-operation typically starts here)
├── phase-04-type-generation.json # Phase 4 output
├── phase-05-implementation.json  # Phase 5 output
├── phase-06-testing.json         # Phase 6 output
├── phase-08-build.json           # Phase 8 output
└── gate-status.json              # From @gate-controller (optional)
```

**Note**: add-operation workflow typically doesn't include phase-01 (discovery) or phase-02 (scaffolding) since the module already exists.

## Time Estimates

- Phase 0 (Credentials): 2-5 min
- Phase 1 (Research): 5-10 min
- Phase 2 (Design): 10-15 min
- Phase 3 (Generation): 2-3 min
- Phase 4 (Implementation): 10-15 min
- Phase 5 (Testing): 15-20 min
- Phase 6 (Build): 3-5 min

**Total: ~45-70 minutes**

## Success Indicators

- ✅ No workflow interruptions
- ✅ All gates pass on first attempt
- ✅ No re-work required
- ✅ Clean progression through phases
- ✅ All agents completed their work
- ✅ Ready for git commit
