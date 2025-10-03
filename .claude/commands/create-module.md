---
description: Create a new module from scratch (3-4 hours)
argument-hint: <vendor> <service> [suite]
---

Execute the Create Module workflow.

**Arguments:**
- Vendor: $1
- Service: $2
- Suite: $3 (optional)

**Module Identifier:**
- If suite: $1-$3-$2
- If no suite: $1-$2

**Workflow Phases:**

1. **Phase 1: Discovery & Analysis**
   - Invoke @product-specialist for product research
     - Check product bundle: `npm view @zerobias-org/bundle-product --json`
     - Install product package: `npm install @zerobias-org/product-{vendor}-{product}`
     - Extract product metadata from index.yml/index.ts
     - Document findings in .claude/.localmemory/create-{module-id}/
   - Invoke @api-researcher for **MINIMAL SCOPE** API research
     - Research connection/authentication endpoints ONLY
     - Research ONE main operation (first to implement)
     - Test connection and main operation with real API calls
     - Document authentication flow, rate limits, base URL
     - Save example responses for these 2 endpoints only
   - Invoke @operation-analyst to prioritize operations
   - Invoke @credential-manager for auth requirements

2. **Phase 2: Module Scaffolding**
   - Invoke @module-scaffolder to create module structure
     - Use product information from @product-specialist research
     - Execute Yeoman generator or scaffolding script
     - Verify complete directory structure
     - Install initial dependencies
     - Create required symlinks (.npmrc, .nvmrc)
     - Validate package.json, tsconfig.json, configs
     - Create connectionProfile.yml based on @credential-manager requirements

3. **Phase 3: API Specification**
   - Invoke @api-architect to design OpenAPI spec
   - Invoke @schema-specialist for schema design
   - Invoke @security-auditor for auth patterns
   - Invoke @api-reviewer for specification review
   - **Gate 1: API Specification**

4. **Phase 4: Type Generation**
   - Run `npm run generate`
   - **Gate 2: Type Generation**

5. **Phase 5: Core Implementation**
   - Invoke @client-engineer for HTTP client
   - Invoke @typescript-expert for interfaces
   - Create ConnectorImpl (with boilerplate metadata/isSupported)
   - Create Producer classes
   - Invoke @mapping-engineer for data mapping
   - **Gate 3: Implementation**

6. **Phase 6: Testing Setup**
   - Invoke @test-orchestrator for test strategy
   - Invoke @mock-specialist for fixtures
   - Invoke @connection-ut-engineer for connection unit tests
   - Invoke @connection-it-engineer for connection integration tests
   - Invoke @producer-ut-engineer for producer unit tests
   - Invoke @producer-it-engineer for producer integration tests
   - **Gates 4 & 5: Tests Created & Passing**

7. **Phase 7: Documentation**
   - Invoke @documentation-writer for README
   - Document connection setup
   - Document available operations

8. **Phase 8: Build & Finalization**
   - Run `npm run build`
   - Run `npm run shrinkwrap`
   - **Gate 6: Build**

9. **Phase 9: Final Validation**
   - Invoke @gate-controller to validate all gates
   - Verify module ready for deployment

**Success Criteria:**
- All 6 gates passed
- Module structure complete
- Core operations implemented
- Full test coverage
- Build successful
- Documentation complete
