# Module Development Checklists
## Printable Validation Checklists

---

## Master Checklist: Complete Module Creation

### Phase 1: API Research & Discovery (10-15 min)
- [ ] API documentation reviewed
- [ ] Authentication method identified (Token/OAuth/Basic)
- [ ] Test credentials obtained
- [ ] Authentication tested with curl (200 OK)
- [ ] First operation tested with curl (200 OK)
- [ ] Response saved to file
- [ ] Response sanitized (no PII)
- [ ] Response structure documented

### Phase 2: Module Scaffolding (5 min)
- [ ] Module identifier determined (vendor-service)
- [ ] Product package installed (if exists)
- [ ] Yeoman generator executed
- [ ] ⭐ **npm run sync-meta executed** (CRITICAL!)
- [ ] npm install completed
- [ ] package.json metadata correct
- [ ] api.yml title/version synced
- [ ] Stub files exist (api.yml, connectionProfile.yml, connectionState.yml)
- [ ] Source stubs exist (src/)
- [ ] Test stubs exist (test/)
- [ ] Dependencies installed
- [ ] .env file created with credentials

### Phase 3: API Specification Design (15-20 min)
- [ ] api.yml designed for operation
- [ ] ⭐ **12 Critical Rules** (see checklist below)
- [ ] connectionProfile extends core profile
- [ ] connectionState extends baseConnectionState
- [ ] expiresIn field present
- [ ] OpenAPI syntax validates
- [ ] ✅ **Gate 1: API Specification PASSED**
- [ ] npm run clean && npm run generate successful
- [ ] Generated types exist
- [ ] No InlineResponse types
- [ ] ✅ **Gate 2: Type Generation PASSED**

### Phase 4: Core Implementation (20-30 min)
- [ ] ServiceClient.ts created
- [ ] connect/isConnected/disconnect implemented
- [ ] util.ts created with all helpers
- [ ] Mappers.ts created
- [ ] All mappers use to<Model> naming
- [ ] Mappers validate required fields
- [ ] Mappers handle ALL fields (zero missing)
- [ ] Producer.ts created
- [ ] Producer has NO connection context params
- [ ] ESLint passes (0 errors)
- [ ] No Promise<any>
- [ ] Build successful
- [ ] ✅ **Gate 3: Implementation PASSED**

### Phase 5: Testing (20-30 min)
- [ ] test/unit/Common.ts (NO env vars)
- [ ] test/unit/ConnectionTest.ts
- [ ] test/unit/ResourceProducerTest.ts
- [ ] Unit tests use nock ONLY
- [ ] ✅ **Gate 4: Unit Tests PASSED**
- [ ] test/integration/Common.ts (loads .env)
- [ ] test/integration/ConnectionTest.ts
- [ ] test/integration/ResourceProducerTest.ts
- [ ] Integration tests have debug logging
- [ ] Test data in .env (not hardcoded)
- [ ] ✅ **Gate 5: Integration Tests PASSED**

### Phase 6: Documentation (5-10 min)
- [ ] USERGUIDE.md created
- [ ] Credential instructions complete
- [ ] Connection profile mapping table
- [ ] No real credentials in docs

### Phase 7: Build & Finalization (5 min)
- [ ] npm run build successful
- [ ] dist/ directory created
- [ ] npm run shrinkwrap successful
- [ ] npm-shrinkwrap.json exists
- [ ] ✅ **Gate 6: Build PASSED**

### Phase 8: Final Validation (5 min)
- [ ] All 6 gates validated
- [ ] Module ready for deployment

**TOTAL ESTIMATED TIME**: 90-150 minutes

---

## 12 Critical API Specification Rules

**MUST ALL PASS - Single violation = Gate 1 failure**

- [ ] **Rule 1**: No servers/security at root level
- [ ] **Rule 2**: Tags and schemas are singular nouns
- [ ] **Rule 3**: All product operations included
- [ ] **Rule 4**: Duplicate parameters moved to components
- [ ] **Rule 5**: All properties are camelCase (NO snake_case)
- [ ] **Rule 6**: Sorting uses orderBy/orderDir
- [ ] **Rule 7**: Path parameters descriptive (userId not id)
- [ ] **Rule 8**: Use id > name for identifiers
- [ ] **Rule 9**: Tags are singular lowercase
- [ ] **Rule 10**: operationId uses get/list/search/create/update/delete (NOT describe)
- [ ] **Rule 11**: Pagination uses pageTokenParam
- [ ] **Rule 12**: ONLY 200/201 responses (NO 4xx/5xx)

**Validation:**
```bash
grep -E "describe[A-Z]" api.yml  # Should return nothing
grep -E "'40[0-9]'|'50[0-9]'" api.yml  # Should return nothing
grep -E "^ *[a-z_]+_[a-z_]+:" api.yml | grep -v "x-" | grep -v "#"  # Should return nothing
npx swagger-cli validate api.yml  # Should pass
```

---

## The 6 Quality Gates

### Gate 1: API Specification
- [ ] No 'describe' operations
- [ ] No error responses (4xx/5xx)
- [ ] No snake_case properties
- [ ] No 'nullable' keyword
- [ ] No connection context parameters
- [ ] No servers/security at root
- [ ] Sorting parameters correct (orderBy/orderDir)
- [ ] Tags lowercase singular
- [ ] OpenAPI syntax valid

**Validate:**
```bash
grep -E "describe[A-Z]" api.yml
grep -E "'40[0-9]'|'50[0-9]'" api.yml
grep "_" api.yml | grep -v "x-" | grep -v "#"
npx swagger-cli validate api.yml
```

### Gate 2: Type Generation
- [ ] npm run clean && npm run generate exits 0
- [ ] generated/api/ directory exists
- [ ] TypeScript files generated
- [ ] No InlineResponse types
- [ ] npm run build succeeds

**Validate:**
```bash
npm run clean && npm run generate
ls generated/api/
grep "InlineResponse" generated/ || echo "✅"
npm run build
```

### Gate 3: Implementation
- [ ] ESLint passes (0 errors, warnings OK)
- [ ] No Promise<any> in signatures
- [ ] Using generated types
- [ ] ConnectorImpl has boilerplate
- [ ] No connection context in producers
- [ ] Mappers use to<Model> naming
- [ ] Mappers use map() utility
- [ ] Mappers use ensureProperties()
- [ ] Mappers use optional()

**Validate:**
```bash
npm run lint:src 2>&1 | grep "✖.*error"  # Should return nothing
grep "Promise<any>" src/*.ts  # Should return nothing
grep "import.*generated" src/*.ts  # Should show imports
npm run build  # Should exit 0
```

### Gate 4: Unit Test Creation & Execution
- [ ] Unit test files exist (ConnectionTest, ResourceProducerTest)
- [ ] Using nock for HTTP mocking
- [ ] No forbidden mocking (jest/sinon/fetch-mock)
- [ ] 3+ test cases per operation
- [ ] NO environment variables in unit tests
- [ ] All unit tests passing

**Validate:**
```bash
ls test/unit/*Test.ts
grep "from.*nock" test/unit/*.ts
grep -E "jest\.mock|sinon|fetch-mock" test/unit/*.ts  # Should return nothing
grep -i "dotenv\|process.env" test/unit/Common.ts  # Should return nothing
npm run test:unit
```

### Gate 5: Integration Tests
- [ ] Integration test files exist
- [ ] 3+ test cases per operation
- [ ] No mocking in integration tests
- [ ] No hardcoded test values
- [ ] Debug logging present
- [ ] .env file has credentials
- [ ] All integration tests passing

**Validate:**
```bash
ls test/integration/*Test.ts
grep -E "nock|mock" test/integration/*.ts  # Should return nothing
grep -E "(const|let|var) [a-zA-Z]*[Ii]d = ['\"][0-9]+['\"]" test/integration/*.ts | grep -v "non-existent"  # Should return nothing
grep "getLogger" test/integration/*.ts  # Should show debug logging
npm run test:integration
```

### Gate 6: Build
- [ ] npm run build exits 0
- [ ] dist/ directory created
- [ ] JavaScript files in dist/
- [ ] Type declarations (.d.ts) in dist/
- [ ] npm run shrinkwrap exits 0
- [ ] npm-shrinkwrap.json exists

**Validate:**
```bash
npm run build
ls dist/
npm run shrinkwrap
ls npm-shrinkwrap.json
```

---

## Implementation Quality Checklist

### Client Implementation
- [ ] Only 3 methods: connect, isConnected, disconnect
- [ ] connect() returns ConnectionState
- [ ] isConnected() makes real API call
- [ ] disconnect() clears state
- [ ] apiClient getter provides AxiosInstance
- [ ] Request interceptor adds authentication
- [ ] Response interceptor handles errors
- [ ] NO business operations in client

### Producer Implementation
- [ ] Constructor receives client
- [ ] Methods receive ONLY business parameters
- [ ] NO connection context (apiKey, token, baseUrl)
- [ ] Uses this.client.apiClient for requests
- [ ] All methods use .catch(handleAxiosError)
- [ ] PagedResults uses manual assignment
- [ ] NEVER uses results.ingest()
- [ ] Updates results.count when available
- [ ] NO Promise<any> in signatures
- [ ] ALL return types use generated types

### Mapper Implementation
- [ ] All mappers use to<Model> naming
- [ ] Uses const output: Type pattern
- [ ] Validates required fields with ensureProperties()
- [ ] Handles ALL interface fields (zero missing)
- [ ] Uses map() for core types (UUID, Email, DateTime, URL)
- [ ] Uses optional() for optional fields
- [ ] Uses mapWith() for nested objects
- [ ] Uses toEnum() for enums
- [ ] NO logical OR on optional fields
- [ ] NO fallbacks between different fields
- [ ] Helper mappers declared before main mappers
- [ ] Helper mappers NOT exported

### Test Implementation Quality
- [ ] Unit tests use nock ONLY
- [ ] Integration tests use real API
- [ ] Both test success AND error cases
- [ ] 3+ test cases per operation
- [ ] Test names use "should" pattern
- [ ] Integration tests have debug logging
- [ ] Test data from .env (not hardcoded)
- [ ] Unit test Common.ts has NO env vars
- [ ] Integration test Common.ts loads dotenv
- [ ] hasCredentials() check in integration tests

---

## Pre-Commit Final Checklist

**Before git commit:**

- [ ] ✅ All 6 quality gates PASSED
- [ ] ✅ All tests pass (unit and integration)
- [ ] ✅ Build successful (exit 0)
- [ ] ✅ ESLint passes (0 errors)
- [ ] ✅ Dependencies locked (npm-shrinkwrap.json)
- [ ] ✅ No hardcoded credentials
- [ ] ✅ No hardcoded test data
- [ ] ✅ .env file in .gitignore (NOT committed)
- [ ] ✅ USERGUIDE.md complete
- [ ] ✅ No console.log debug statements (use logger)
- [ ] ✅ No TODO comments
- [ ] ✅ Module identifier correct (vendor-service)
- [ ] ✅ Package version correct

---

## Troubleshooting Quick Checklist

**Build failing?**
- [ ] Run npm run clean && npm run generate
- [ ] Check TypeScript errors
- [ ] Verify imports correct
- [ ] Check tsconfig.json has skipLibCheck: true

**Tests failing?**
- [ ] Check .env file exists
- [ ] Verify credentials valid
- [ ] Check nock paths match exactly
- [ ] Run with LOG_LEVEL=debug

**Types missing?**
- [ ] Run npm run clean && npm run generate
- [ ] Check api.yml valid
- [ ] Verify no inline schemas

**Integration tests skipping?**
- [ ] Check .env file exists
- [ ] Verify hasCredentials() returns true
- [ ] Check Common.ts has config()

---

## Code Review Checklist

**When reviewing PRs:**

### API Specification Review
- [ ] All 12 critical rules followed
- [ ] No inline schemas
- [ ] All properties camelCase
- [ ] Only 200/201 responses
- [ ] Tags singular lowercase
- [ ] operationId correct verbs

### Implementation Review
- [ ] Client has only connection methods
- [ ] Producers have NO connection context params
- [ ] All types from generated/
- [ ] No Promise<any>
- [ ] Error handling uses core errors
- [ ] Mappers validate required fields
- [ ] Mappers handle all fields

### Test Review
- [ ] Unit tests use nock ONLY
- [ ] Integration tests have debug logging
- [ ] Test data in .env (not hardcoded)
- [ ] Both success and error cases
- [ ] 3+ cases per operation
- [ ] All tests passing

### Build Review
- [ ] npm run build succeeds
- [ ] dist/ has all files
- [ ] npm-shrinkwrap.json exists
- [ ] No compilation errors

---

## Quick Validation Commands

```bash
# API Spec (Gate 1)
grep -E "describe[A-Z]|'40[0-9]'|'50[0-9]'" api.yml

# Type Gen (Gate 2)
npm run clean && npm run generate

# Implementation (Gate 3)
npm run lint:src
grep "Promise<any>" src/*.ts
npm run build

# Unit Tests (Gate 4)
npm run test:unit

# Integration Tests (Gate 5)
npm run test:integration

# Build (Gate 6)
npm run shrinkwrap
ls npm-shrinkwrap.json
```

---

## Emergency Debug Checklist

**When everything is failing:**

1. [ ] Clean everything
   ```bash
   npm run clean
   rm -rf node_modules dist generated
   ```

2. [ ] Reinstall
   ```bash
   npm install
   ```

3. [ ] Regenerate types
   ```bash
   npm run generate
   ```

4. [ ] Check basics
   ```bash
   npm run build
   npm run lint:src
   ```

5. [ ] Run tests
   ```bash
   npm run test:unit
   LOG_LEVEL=debug npm run test:integration
   ```

6. [ ] Check .env
   ```bash
   cat .env
   # Verify credentials present and valid
   ```

7. [ ] Test API manually
   ```bash
   curl -X POST "https://api.service.com/auth" \
     -d '{"email":"...","password":"..."}'
   ```

---

## Module Types Reference

### connectionProfile.yml Selection

- [ ] API Key/Bearer Token → tokenProfile.yml
- [ ] OAuth Client Credentials → oauthClientProfile.yml
- [ ] OAuth with Refresh → oauthTokenProfile.yml
- [ ] Username/Password → basicConnection.yml
- [ ] Email/Password → Extend basicConnection.yml

### connectionState.yml Selection

- [ ] Simple token → tokenConnectionState.yml
- [ ] OAuth with refresh → oauthTokenState.yml
- [ ] Custom → Extend baseConnectionState.yml

**CRITICAL**: Always extend baseConnectionState for expiresIn!

---

## Mapper Field Type Reference

| Input Type | Pattern | Example |
|------------|---------|---------|
| ID (number) | `.toString()` | `id: raw.id.toString()` |
| String (required) | Direct | `name: raw.name` |
| String (optional) | `optional()` | `lastName: optional(raw.last_name)` |
| Email | `map(Email, ...)` | `email: map(Email, raw.email)` |
| UUID | `map(UUID, ...)` | `userId: map(UUID, raw.user_id)` |
| DateTime | `map(DateTime, ...)` | `createdAt: map(DateTime, raw.created_at)` |
| URL | `map(URL, ...)` | `website: map(URL, raw.website)` |
| Date | `map(Date, ...)` | `birthDate: map(Date, raw.birth_date)` |
| Enum | `toEnum(Type, ...)` | `status: toEnum(Status, raw.status)` |
| Nested | `mapWith(toFn, ...)` | `address: mapWith(toAddress, raw.address)` |
| Array | `.map(toFn)` | `items: raw.items?.map(toItem)` |

---

## Test Data Environment Variables

**Pattern for .env:**
```bash
# Credentials
SERVICE_API_KEY=your-key
SERVICE_EMAIL=your-email
SERVICE_PASSWORD=your-password

# Test Data IDs
SERVICE_TEST_USER_ID=12345
SERVICE_TEST_ORGANIZATION_ID=1067
SERVICE_TEST_RESOURCE_NAME=test-resource

# Optional Config
SERVICE_BASE_URL=https://api.service.com
LOG_LEVEL=info
```

**Pattern for Common.ts:**
```typescript
export const SERVICE_TEST_USER_ID = process.env.SERVICE_TEST_USER_ID || '';
export const SERVICE_TEST_ORGANIZATION_ID = process.env.SERVICE_TEST_ORGANIZATION_ID || '';
```

---

## Common Validation Failures

| Failure | Cause | Fix |
|---------|-------|-----|
| snake_case found | Properties not camelCase | Change to camelCase in api.yml |
| describe found | Wrong verb | Change to get/list/create/update/delete |
| Error responses | 4xx/5xx in spec | Remove all error responses |
| Promise<any> | Not using generated types | Import from ../generated/api |
| InlineResponse | Inline schema | Move to components/schemas |
| Nock not matching | Path mismatch | Check URL path exactly |
| Tests skipping | No .env file | Create .env with credentials |
| Build failing | Missing types | Run npm run generate |
| Hardcoded ID | Test data in code | Move to .env |

---

## Speed Optimization Tips

**Time Savers:**
1. Use code templates from Chapter 17
2. Run validation early and often
3. Test API with curl BEFORE coding
4. Keep .env file updated from start
5. Run gates incrementally
6. Copy patterns from existing modules

**Common Time Sinks:**
- Debugging without proper logging (add debug early!)
- Forgetting to regenerate types (run after every api.yml change)
- Hardcoding test data (use .env from start)
- Skipping curl testing (test API before implementing)

---

## Daily Developer Checklist

**Before starting work:**
- [ ] Git status clean
- [ ] Latest code pulled
- [ ] Node/npm versions correct
- [ ] .env file ready

**During development:**
- [ ] Commit after each phase
- [ ] Run gates incrementally
- [ ] Test with curl frequently
- [ ] Use debug logging

**Before committing:**
- [ ] All 6 gates pass
- [ ] All tests pass
- [ ] Build successful
- [ ] No hardcoded data
- [ ] .env not committed

---

## Print These for Quick Reference!

**Recommended prints:**
1. 12 Critical Rules (1 page)
2. 6 Quality Gates (1 page)
3. Master Checklist (2 pages)
4. Quick Validation Commands (1 page)
5. Mapper Field Type Reference (1 page)

**Total**: 6 pages for quick desk reference

---

**VERSION**: 1.0 | **LAST UPDATED**: 2025-10-22
