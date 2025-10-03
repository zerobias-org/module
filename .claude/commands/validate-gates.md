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

# No error responses
grep -E "'40[0-9]'|'50[0-9]'" api.yml

# No snake_case properties
grep "_" api.yml | grep -v "x-" | grep -v "#"

# No nullable in API spec
grep "nullable:" api.yml

# No connection context parameters
grep -E "name: (apiKey|token|baseUrl|organizationId)" api.yml

# Schema context separation check
# (detailed validation in ENFORCEMENT.md)
```
✅ **Pass Criteria:** All checks return nothing, proper naming, descriptions present

---

**Gate 2: Type Generation**
```bash
# Generation succeeds
npm run generate

# New types exist
ls generated/api/*.ts

# No inline types
grep "InlineResponse" generated/
```
✅ **Pass Criteria:** Exit code 0, types created, no inline types

---

**Gate 3: Implementation**
```bash
# No 'any' types
grep "Promise<any>" src/*.ts

# Using generated types
grep "import.*generated" src/*.ts

# ConnectorImpl extends only interface
grep "extends.*Connector" src/*ConnectorImpl.ts

# Metadata uses boilerplate
grep "ConnectionStatus.Down" src/*ConnectorImpl.ts

# isSupported uses boilerplate
grep "OperationSupportStatus.Maybe" src/*ConnectorImpl.ts

# No connection context in producers
grep -E "(apiKey|token|baseUrl|organizationId):" src/*Producer.ts

# Mappers prefer map() function
grep -E "new (UUID|Email|URL|DateTime)\(" src/Mappers.ts
```
✅ **Pass Criteria:** No `any`, using generated types, boilerplate unchanged, no connection context

---

**Gate 4: Test Creation**
```bash
# Test files exist
ls test/*Test.ts

# Test suites exist
grep "describe.*" test/*.ts

# Multiple test cases
grep "it.*should" test/*.ts | wc -l

# ONLY nock for mocking
grep -E "from ['\"]nock['\"]" test/unit/*.ts

# No forbidden mocking
grep -E "jest\.mock|sinon|fetch-mock" test/*.ts

# No hardcoded test values in integration tests
grep -E "(const|let|var) [a-zA-Z]*[Ii]d = ['\"][0-9]+['\"]" test/integration/*.ts
```
✅ **Pass Criteria:** Tests exist, 3+ cases per operation, only nock, no hardcoded values

---

**Gate 5: Test Execution**
```bash
# All tests pass
npm test
```
✅ **Pass Criteria:** Exit code 0, zero failures

---

**Gate 6: Build**
```bash
# Build succeeds
npm run build

# Distribution files created
ls dist/

# Lock dependencies
npm run shrinkwrap

# Shrinkwrap file exists
ls npm-shrinkwrap.json
```
✅ **Pass Criteria:** Exit code 0, dist/ populated, npm-shrinkwrap.json created

---

**Output Format:**
```
VALIDATION RESULTS: $1

Gate 1: API Specification    [✅ PASS | ❌ FAIL]
Gate 2: Type Generation      [✅ PASS | ❌ FAIL]
Gate 3: Implementation       [✅ PASS | ❌ FAIL]
Gate 4: Test Creation        [✅ PASS | ❌ FAIL]
Gate 5: Test Execution       [✅ PASS | ❌ FAIL]
Gate 6: Build                [✅ PASS | ❌ FAIL]

Overall: [✅ ALL GATES PASSED | ❌ FAILURES DETECTED]

[Detailed failure information if any gate failed]
[Remediation steps for failures]
```

**Example:**
```
/validate-gates github-github
/validate-gates amazon-aws-s3
```
