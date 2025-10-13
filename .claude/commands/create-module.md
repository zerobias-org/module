---
description: Create a new module from scratch (8 phases, 6 gates, 3-4 hours)
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

**Workflow Phases (8 phases, 6 gates):**

1. **Phase 1: Discovery & Analysis**
   - Invoke @product-specialist for product research
     - Check product bundle: `npm view @zerobias-org/product-bundle --json`
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
     - Execute Yeoman generator (creates stub files)
     - Run npm run sync-meta (sync package.json to api.yml)
     - Verify complete directory structure
     - Install initial dependencies
     - Create required symlinks (.npmrc, .nvmrc)
     - Validate package.json, tsconfig.json, configs
     - **Validate stubs exist** (connectionProfile.yml, connectionState.yml, api.yml)
     - NO design decisions - stubs will be replaced in Phase 3
     - NO build validation - build will be validated in Phase 8 (after implementation)

3. **Phase 3: API Specification Design & Type Generation**
   - Invoke @credential-manager for authentication analysis
     - Check for credentials in .env
     - Identify authentication method from API docs
     - Validate credential format
     - Provide raw authentication data to @api-architect
   - Invoke @security-auditor for security requirements
     - Analyze authentication patterns
     - Review security considerations
   - Invoke @api-architect to design ALL schemas
     - Receive authentication data from @credential-manager
     - Design api.yml paths and operations (replace stub)
     - Define security schemes in api.yml
     - Select and extend appropriate core connectionProfile
     - Design connectionProfile.yml schema (extend tokenProfile, oauthClientProfile, etc.)
     - Design connectionState.yml schema (MUST extend baseConnectionState with expiresIn)
     - Check for additional optional connection parameters (region, environment, etc.)
   - Invoke @schema-specialist for complex resource schemas
     - Design resource schemas
     - Use $ref for composition
   - Invoke @api-reviewer for specification review
     - Validate api.yml against all rules
   - Invoke @security-auditor to review connection schemas
     - Review connectionProfile/State security
   - Run `npm run sync-meta` to sync package.json metadata to api.yml
   - Run `npm run generate` to validate spec and generate types
   - **Gate 1: API Specification** (all 3 files: api.yml, connectionProfile.yml, connectionState.yml)
   - **Gate 2: Type Generation** (TypeScript interfaces created)

4. **Phase 4: Core Implementation**
   - Invoke @client-engineer for HTTP client
   - Invoke @typescript-expert for interfaces
   - Create ConnectorImpl (with boilerplate metadata/isSupported)
   - Create Producer classes
   - Invoke @mapping-engineer for data mapping
   - **Gate 3: Implementation**

5. **Phase 5: Testing Setup**
   - Invoke @test-orchestrator for test strategy
   - Invoke @mock-specialist for fixtures
   - Invoke @connection-ut-engineer for connection unit tests
   - Invoke @connection-it-engineer for connection integration tests
   - Invoke @producer-ut-engineer for producer unit tests
   - Invoke @producer-it-engineer for producer integration tests
   - **Gates 4 & 5: Tests Created & Passing**

6. **Phase 6: Documentation**
   - Invoke @documentation-writer for README
   - Document connection setup
   - Document available operations

7. **Phase 7: Build & Finalization**
   - Run `npm run build`
   - Run `npm run shrinkwrap`
   - **Gate 6: Build**

8. **Phase 8: Final Validation**
   - Invoke @gate-controller to validate all gates
   - Verify module ready for deployment

**Success Criteria:**
- All 6 gates passed
- Module structure complete
- Core operations implemented
- Full test coverage
- Build successful
- Documentation complete
