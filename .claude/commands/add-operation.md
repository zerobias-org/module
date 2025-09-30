---
description: Add a single operation to an existing module (45-70 min)
argument-hint: <module-identifier> <operation-name>
---

Execute the Add Operation workflow for module: $1, operation: $2

**Workflow Steps:**

1. **Phase 0: Credential Check (MANDATORY)**
   - Check for credentials in .env, .connectionProfile.json
   - If missing, ASK user before proceeding
   - Only continue with explicit permission

2. **Phase 1: Research & Analysis**
   - Invoke @api-researcher for **TARGETED SCOPE** API research
     - Research ONLY the specific operation endpoint
     - Test the endpoint with real API call
     - Document parameters and response format
     - Save example response
   - Invoke @operation-analyst to analyze operation priority

3. **Phase 2: API Specification Design**
   - Invoke @api-architect to design OpenAPI spec
   - Invoke @schema-specialist for schema design
   - Invoke @api-reviewer to review specification
   - **Gate 1: API Specification** - Validate before proceeding

4. **Phase 3: Type Generation**
   - Invoke @build-validator to run `npm run clean && npm run generate`
   - **Gate 2: Type Generation** - Validate types created

5. **Phase 4: Implementation**
   - Invoke @operation-engineer to implement the operation
   - Invoke @mapping-engineer for data mapping
   - Invoke @style-reviewer for code quality
   - **Gate 3: Implementation** - Validate no `any` types
   - **Mapper Runtime Validation** - Add [TEMP-RAW-API] logs, run tests with LOG_LEVEL=debug, validate ZERO missing fields

6. **Phase 5: Testing**
   - Invoke @test-orchestrator to plan tests
   - Invoke @mock-specialist for test fixtures
   - Invoke @producer-ut-engineer for unit tests
   - Invoke @producer-it-engineer for integration tests
   - Invoke @ut-reviewer and @it-reviewer
   - **Gate 4: Test Creation** - Validate tests exist
   - **Gate 5: Test Execution** - Run `npm test`, all must pass

7. **Phase 6: Build & Finalization**
   - Invoke @build-reviewer to run `npm run build`
   - Run `npm run shrinkwrap` to lock dependencies
   - **Gate 6: Build** - Validate successful

8. **Final Validation**
   - Invoke @gate-controller to validate all 6 gates passed

**Success Criteria:**
- All 6 gates passed
- Operation fully implemented
- Tests created and passing
- Build successful
- Ready for commit

**Module Identifier Format:** vendor-service or vendor-suite-service
Examples: github-github, amazon-aws-s3, avigilon-alta-access

**Operation Name Pattern:** Must start with: list, get, create, update, delete, or search
Examples: listWebhooks, getUser, createRepository
