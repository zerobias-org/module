---
description: Validate all 6 quality gates for a module (5-10 min)
argument-hint: <module-identifier>
---

Execute comprehensive validation of all 6 quality gates for module: $1

**Invoke @gate-controller to validate:**

**Gate 1: API Specification**
```bash
# No 'describe' operations
grep -E "describe[A-Z]" api.yml

# No error responses in spec
grep -E "'40[0-9]'|'50[0-9]'" api.yml

# No snake_case properties (camelCase ONLY - Rule 5)
grep -E "^ *[a-z_]+_[a-z_]+:" api.yml | grep -v "x-" | grep -v "#"

# No nullable in API spec
grep "nullable:" api.yml

# No connection context parameters (apiKey, token, baseUrl)
# Note: organizationId in path is ALLOWED (scope parameter)
grep -E "name: (apiKey|token|baseUrl)" api.yml

# No servers or security at root level (Rule 1)
grep -E "^servers:" api.yml
grep -E "^security:" api.yml

# Sorting parameters must be orderBy/orderDir (Rule 6)
grep -E "name: (sortBy|sortDir|sort)" api.yml | grep -v orderBy | grep -v orderDir

# Tags must be lowercase singular (Rule 10)
yq '.paths.*.*.tags[]' api.yml 2>/dev/null | grep -E "^[A-Z]|s$"
```
✅ **Pass Criteria:** All content checks return nothing

---

**Gate 2: Type Generation**
```bash
# Generation succeeds
npm run clean && npm run generate

# New types exist
ls generated/api/*.ts

# No inline types
grep "InlineResponse" generated/
```
✅ **Pass Criteria:** Exit code 0, types created, no inline types

---

**Gate 3: Implementation**
```bash
# Source code linting (ESLint) - must have 0 errors
npm run lint:src 2>&1 | grep "✖.*error"

# No 'any' types in function signatures
grep "Promise<any>" src/*.ts

# Using generated types
grep "import.*generated" src/*.ts

# ConnectorImpl/AccessImpl has boilerplate
grep -E "(ConnectionStatus\.Down|OperationSupportStatus\.Maybe)" src/*Impl.ts

# No connection context in producers (organizationId as method parameter is OK)
grep -E "(apiKey|token|baseUrl):" src/*Producer*.ts

# Mappers use to<Model> naming convention (NOT map<Model>)
grep -E "export function map[A-Z]" src/Mappers.ts

# Mappers prefer map() function over constructors
grep -E "new (UUID|Email|URL|DateTime)\(" src/Mappers.ts

# Mappers use ensureProperties for validation
grep "ensureProperties" src/Mappers.ts

# Mappers use optional() for optional fields
grep "optional\(" src/Mappers.ts
```
✅ **Pass Criteria:** Zero ESLint errors (warnings OK), no `any`, using generated types, boilerplate present, to<Model> naming, map() preferred, ensureProperties used

---

**Gate 4: Unit Test Creation & Execution**
```bash
# Unit test files exist
ls test/unit/*Test.ts

# Unit test suites exist
grep "describe" test/unit/*.ts | wc -l

# Multiple unit test cases
grep "it.*should" test/unit/*.ts | wc -l

# ONLY nock for mocking in unit tests
grep -E "from ['\"]nock['\"]" test/unit/*.ts

# No forbidden mocking libraries
grep -E "jest\.mock|sinon|fetch-mock" test/unit/*.ts

# Run unit tests
npm test
```
✅ **Pass Criteria:** Unit tests exist, 3+ cases per producer, only nock, all passing

---

**Gate 5: Integration Test Creation & Execution**
```bash
# Integration test files exist
ls test/integration/*Test.ts

# Integration test suites exist
grep "describe" test/integration/*.ts | wc -l

# Multiple integration test cases
grep "it.*should" test/integration/*.ts | wc -l

# No mocking in integration tests
grep -E "nock|mock" test/integration/*.ts

# No hardcoded test values (except non-existent ID tests)
grep -E "(const|let|var) [a-zA-Z]*[Ii]d = ['\"][0-9]+['\"]" test/integration/*.ts

# Run integration tests
npm run test:integration
```
✅ **Pass Criteria:** Integration tests exist, 3+ cases per operation, no mocking, all passing, no hardcoded IDs (except non-existent)

---

**Gate 6: Build**
```bash
# Build succeeds
npm run build

# Distribution files created
ls dist/

# Shrinkwrap file exists (created during build)
ls npm-shrinkwrap.json || npm shrinkwrap
```
✅ **Pass Criteria:** Exit code 0, dist/ populated, npm-shrinkwrap.json exists

---

**Output Format:**
```
VALIDATION RESULTS: $1

Gate 1: API Specification              [✅ PASS | ❌ FAIL]
Gate 2: Type Generation                [✅ PASS | ❌ FAIL]
Gate 3: Implementation                 [✅ PASS | ❌ FAIL]
Gate 4: Unit Test Creation & Execution [✅ PASS | ❌ FAIL]
Gate 5: Integration Tests              [✅ PASS | ❌ FAIL]
Gate 6: Build                          [✅ PASS | ❌ FAIL]

Overall: [✅ ALL GATES PASSED | ❌ FAILURES DETECTED]

[Detailed failure information if any gate failed]
[Remediation steps for failures]
```

**Example:**
```
/validate-gates github-github
/validate-gates amazon-aws-s3
```
