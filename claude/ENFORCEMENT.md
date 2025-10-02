# 🚨 Enforcement Protocol

Complete enforcement guide for ensuring quality and consistency in module development.

## Core Philosophy

**There is ONE correct way to execute each task.**

- Every rule violation = TASK FAILURE
- Every skipped step = TASK FAILURE
- Every incomplete execution = TASK FAILURE

## Mandatory Execution Sequence

### For Add-Operation Task

```
1. Update API specification
   ↓ STOP: Validate
2. Run npm generate
   ↓ STOP: Validate
3. Implement operation
   ↓ STOP: Validate
4. Write tests (unit + integration)
   ↓ STOP: Validate
5. Run tests
   ↓ STOP: Validate
6. Build project
   ↓ STOP: Validate
7. COMPLETE
```

**NEVER skip steps. NEVER skip validation.**

## 🛑 Validation Gates

### Gate 1: API Specification

**STOP AND CHECK:**
```bash
# No 'describe' operations
grep -E "describe[A-Z]" api.yml
# Must return nothing

# No error responses
grep -E "'40[0-9]'|'50[0-9]'" api.yml
# Must return nothing

# No snake_case in properties
grep "_" api.yml | grep -v "x-" | grep -v "#"
# Must return nothing

# No envelope/wrapper objects in responses
# Response should be direct $ref or array, not wrapped in {status, data, meta, response, result, caller, token, etc.}
yq eval '.paths.*.*.responses.*.content.*.schema | select(has("properties"))' api.yml
# Should only return schemas with business object properties, NOT envelope properties

# No connection context parameters in operations
grep -E "name: (apiKey|token|baseUrl|organizationId)" api.yml
# Must return nothing - these come from ConnectionProfile/State

# No nullable in API specification
grep "nullable:" api.yml
# Must return nothing - NEVER use nullable in api.yml

# Schema context separation check
# Find schemas referenced by other schemas (nested usage)
nested_schemas=$(yq eval '.components.schemas[] | .. | select(type == "string" and test("#/components/schemas/")) | capture("#/components/schemas/(?<schema>.+)").schema' api.yml | sort -u)

# Find schemas with their own endpoints (direct response usage)
endpoint_schemas=$(yq eval '.paths.*.*.responses.*.content.*.schema["$ref"]' api.yml | grep -o '[^/]*$' | sort -u)

# Schemas in BOTH lists need review
for schema in $nested_schemas; do
  if echo "$endpoint_schemas" | grep -q "^${schema}$"; then
    # Count properties in schema
    prop_count=$(yq eval ".components.schemas.${schema}.properties | length" api.yml)
    if [ "$prop_count" -gt 10 ]; then
      echo "⚠️  WARNING: Schema '${schema}' used in BOTH nested and direct contexts with ${prop_count} properties"
      echo "   Consider creating '${schema}Summary' for nested usage"
    fi
  fi
done
```

**PROCEED ONLY IF:**
- ✅ Operation name uses get/list/search/create/update/delete
- ✅ Summary uses "Retrieve" for get/list operations
- ✅ Descriptions from vendor documentation included
- ✅ ONLY 200/201 responses (NO 4xx/5xx)
- ✅ All properties camelCase
- ✅ Nested objects use $ref
- ✅ Response schemas map directly to main business object (NO envelope: status/data/meta/response/result/caller/token)
- ✅ NO connection context parameters (apiKey, token, baseUrl, organizationId) in operation parameters
- ✅ NO `nullable: true` in any schema properties
- ✅ Schemas used in BOTH nested and direct contexts have been reviewed for separation (if >10 properties, consider Summary version)

**IF FAILED:** Fix API spec before proceeding to generation.

### Gate 2: Type Generation

**STOP AND CHECK:**
```bash
# Generation must succeed
npm run generate
echo "Exit code: $?"
# Must be 0

# New types must exist
ls generated/api/*.ts | wc -l
# Must show new files

# No inline types
grep "InlineResponse" generated/
# Must return nothing
```

**PROCEED ONLY IF:**
- ✅ Generation command succeeded (exit code 0)
- ✅ New types exist in generated/ directory
- ✅ No InlineResponse or InlineRequestBody types
- ✅ No TypeScript compilation errors

**IF FAILED:** Fix API spec issues before implementing.

### Gate 3: Implementation

**STOP AND CHECK:**
```bash
# No 'any' types in signatures
grep "Promise<any>" src/*.ts
# Must return nothing

# Using generated types
grep "import.*generated" src/*.ts
# Must show imports

# ConnectorImpl extends only generated interface
grep "extends.*Connector" src/*ConnectorImpl.ts
# Should show: extends {Service}Connector (not AbstractConnector or other classes)

# Metadata uses boilerplate (ConnectionStatus.Down)
grep "ConnectionStatus.Down" src/*ConnectorImpl.ts
# MUST return match - this is the required boilerplate

# Metadata is async returning Promise
grep "async metadata(): Promise<ConnectionMetadata>" src/*ConnectorImpl.ts
# MUST return match - correct signature

# isSupported uses boilerplate (OperationSupportStatus.Maybe)
grep "OperationSupportStatus.Maybe" src/*ConnectorImpl.ts
# MUST return match - this is the required boilerplate

# isSupported parameter named correctly (_operationId with underscore)
grep "isSupported(_operationId:" src/*ConnectorImpl.ts
# MUST return match - underscore indicates unused parameter

# No customized metadata logic
grep -E "ConnectionStatus\.(Up|Connected|Ready)" src/*ConnectorImpl.ts
# Should return nothing - only Down allowed

# No customized isSupported logic
grep -E "OperationSupportStatus\.(Yes|No)" src/*ConnectorImpl.ts
# Should return nothing - only Maybe allowed

# No connection context in producer method parameters
grep -E "(apiKey|token|baseUrl|organizationId):" src/*Producer.ts
# Should return nothing - these come from client

# Mappers should prefer map() function over constructors
if [ -f "src/Mappers.ts" ]; then
  if grep -E "new (UUID|Email|URL|DateTime)\(" src/Mappers.ts > /dev/null; then
    echo "⚠️  WARNING: Mappers.ts using constructors directly"
    echo "   PREFER map() from @auditmation/util-hub-module-utils"
    echo "   Example: map(UUID, value) instead of new UUID(value)"
    echo "   Only use constructors if map() doesn't meet requirements"
  fi

  # Check for map() import
  if ! grep -q "import.*map.*from.*@auditmation/util-hub-module-utils" src/Mappers.ts; then
    echo "⚠️  WARNING: Mappers.ts missing map() import"
    echo "   Add: import { map } from '@auditmation/util-hub-module-utils';"
    echo "   map() handles optional values automatically and is preferred"
  fi
fi

# Check if using core profiles when applicable
# If connectionProfile.yml exists and is custom
if [ -f "connectionProfile.yml" ] && ! grep -q "\$ref.*types-core/schema" connectionProfile.yml; then
  echo "⚠️  WARNING: Custom connectionProfile.yml - verify core profile not applicable"
  echo "   Available core profiles:"
  echo "   - tokenProfile.yml (API token/key auth)"
  echo "   - oauthClientProfile.yml (OAuth client credentials)"
  echo "   - oauthTokenProfile.yml (OAuth token)"
  echo "   - basicConnection.yml (username/password or email/password)"
  echo "   For email/password auth, consider extending basicConnection.yml"
fi

# If connectionState.yml exists, validate it
if [ -f "connectionState.yml" ]; then
  # Check if using core state (recommended)
  if ! grep -q "\$ref.*types-core/schema" connectionState.yml; then
    echo "⚠️  WARNING: Custom connectionState.yml - verify core state not applicable"
    echo "   Available core states:"
    echo "   - tokenConnectionState.yml (access token + expiresIn)"
    echo "   - oauthTokenState.yml (OAuth with refresh capability)"
    echo "   All states MUST extend baseConnectionState.yml for expiresIn"
  fi

  # CRITICAL: Validate expiresIn field (required for server cronjobs)
  if ! grep -q "\$ref.*baseConnectionState\|expiresIn:" connectionState.yml; then
    echo "❌  CRITICAL ERROR: connectionState.yml missing expiresIn field"
    echo "   WHY: Server sets cronjobs based on expiresIn for automatic token refresh"
    echo "   FIX: Must extend baseConnectionState.yml OR include expiresIn property"
    echo "   UNIT: expiresIn must be in SECONDS (integer) until token expires"
  fi

  # Check for expiresAt (should be converted to expiresIn)
  if grep -q "expiresAt:" connectionState.yml; then
    echo "⚠️  WARNING: connectionState.yml has expiresAt field"
    echo "   REQUIRED: Calculate expiresIn (seconds) from expiresAt and DROP expiresAt"
    echo "   WHY: Server needs expiresIn (seconds) for cronjobs, not timestamps"
    echo "   FIX: In connect(), calculate: expiresIn = Math.floor((expiresAt - now) / 1000)"
  fi

  # If has refreshToken, MUST have expiresIn
  if grep -q "refreshToken:" connectionState.yml && ! grep -q "\$ref.*baseConnectionState\|expiresIn:" connectionState.yml; then
    echo "❌  CRITICAL ERROR: Has refreshToken but missing expiresIn"
    echo "   WHY: Cannot schedule token refresh without knowing when it expires"
    echo "   FIX: Must extend baseConnectionState.yml to include expiresIn"
  fi
fi
```

**PROCEED ONLY IF:**
- ✅ NO `any` types in method signatures
- ✅ Using generated types from generated/api/
- ✅ ConnectorImpl extends ONLY generated interface (read from generated/api/index.ts)
- ✅ metadata() exact boilerplate: `async metadata(): Promise<ConnectionMetadata> { return { status: ConnectionStatus.Down } satisfies ConnectionMetadata; }`
- ✅ isSupported() exact boilerplate: `async isSupported(_operationId: string) { return OperationSupportStatus.Maybe; }`
- ❌ NO customization of metadata() or isSupported() methods
- ✅ Mappers implemented for field conversions (const output pattern)
- ✅ Mappers use `map()` from `@auditmation/util-hub-module-utils` (NOT constructors directly)
- ✅ Error handling uses core error types
- ✅ NO connection context parameters (apiKey, token, baseUrl, organizationId) in producer methods
- ✅ Using core connectionProfile/connectionState when applicable (or justified custom)
- ✅ If custom connectionState: includes expiresIn (extends baseConnectionState.yml)
- ✅ If refresh capability: connectionState has refreshToken + all refresh-relevant data
- ✅ No TypeScript compilation errors

**IF FAILED:** Fix implementation before writing tests.

### Gate 4: Test Creation

**STOP AND CHECK:**
```bash
# Test files must exist
ls test/*Test.ts | grep -i "Operation"
# Must show test files

# Test suites must exist
grep "describe.*Operation" test/*.ts
# Must show test suite

# Multiple test cases
grep "it.*should" test/*.ts | wc -l
# Must show 3+ test cases

# ONLY nock used for HTTP mocking
grep -E "from ['\"]nock['\"]" test/unit/*.ts
# Must show nock imports in unit tests

# No forbidden mocking libraries
grep -E "jest\.mock|sinon|fetch-mock" test/*.ts
# Should return nothing - ONLY nock allowed

# No hardcoded test values in integration tests
# Check for common hardcoded ID patterns
if grep -E "(const|let|var) [a-zA-Z]*[Ii]d = ['\"][0-9]+['\"]" test/integration/*.ts > /dev/null 2>&1; then
  echo "❌ Gate 4 FAILED: Hardcoded test values found in integration tests"
  echo "   ALL test values must be in .env and imported from Common.ts"
  echo "   Example: const userId = TEST_USER_ID; (NOT: const userId = '12345';)"
fi

# Verify test data is exported from Common.ts if integration tests exist
if [ -d "test/integration" ] && [ -f "test/integration/Common.ts" ]; then
  # Check if Common.ts exports test values (if integration tests use IDs)
  if grep -E "api\.(get|list|update|delete)\(" test/integration/*.ts | grep -v "Common.ts" > /dev/null 2>&1; then
    if ! grep -E "export const.*TEST.*=" test/integration/Common.ts > /dev/null 2>&1; then
      echo "⚠️  WARNING: Integration tests may need test data constants exported from Common.ts"
      echo "   Check if any test IDs/values should be moved to .env and exported"
    fi
  fi
fi
```

**PROCEED ONLY IF:**
- ✅ Unit test file created/updated
- ✅ Integration test created/updated
- ✅ At least 3 test cases per operation
- ✅ Error cases covered
- ✅ ONLY nock used for HTTP mocking (no jest.mock, sinon, fetch-mock)
- ✅ Mocking at HTTP level (not at client/method level)
- ✅ NO hardcoded test values in integration tests (all values from .env via Common.ts)

**IF FAILED:** Tests are MANDATORY - write them now.

### Gate 5: Test Execution

**STOP AND CHECK:**
```bash
# All tests must pass
npm test
echo "Exit code: $?"
# Must be 0

# No failures
npm test 2>&1 | grep -i "fail"
# Must return nothing
```

**PROCEED ONLY IF:**
- ✅ All new tests pass
- ✅ No regression in existing tests
- ✅ Zero test failures
- ✅ Coverage maintained or improved

**IF FAILED:** Fix failing tests before building.

### Gate 6: Build

**STOP AND CHECK:**
```bash
# Build must succeed
npm run build
echo "Exit code: $?"
# Must be 0

# Distribution files created
ls dist/
# Must show compiled files

# Lock dependencies for npm package
npm run shrinkwrap
echo "Exit code: $?"
# Must be 0

# Verify shrinkwrap file created
ls npm-shrinkwrap.json
# Must exist
```

**PROCEED ONLY IF:**
- ✅ Build succeeds (exit code 0)
- ✅ No TypeScript errors
- ✅ No linting errors
- ✅ Distribution files created
- ✅ npm shrinkwrap completed successfully
- ✅ npm-shrinkwrap.json file created

**IF FAILED:** Fix build errors before completing.

## ✅ Completion Checklist

Task is ONLY complete when ALL items checked:

### API Specification Phase
- [ ] Operation added to api.yml
- [ ] Operation name follows pattern (get/list/search/create/update/delete)
- [ ] Summary uses "Retrieve" for get/list operations
- [ ] Description added from vendor documentation
- [ ] NO error responses (only 200/201)
- [ ] Properties are camelCase
- [ ] Nested objects use $ref

### Code Generation Phase
- [ ] Ran `npm run generate`
- [ ] Generation succeeded without errors
- [ ] New types created in generated/
- [ ] No InlineResponse types generated

### Implementation Phase
- [ ] Method added to producer class
- [ ] Using generated types (NO Promise<any>)
- [ ] Mappers implemented for field conversions
- [ ] Error handling using core types
- [ ] No TypeScript compilation errors

### Testing Phase
- [ ] Unit test file created/updated
- [ ] Integration test file created/updated
- [ ] At least 3 test cases for the operation
- [ ] Success case tested
- [ ] Error cases tested
- [ ] Mocks properly configured

### Validation Phase
- [ ] All tests passing (`npm test`)
- [ ] No regression in existing tests
- [ ] Build successful (`npm run build`)
- [ ] Dependencies locked (`npm run shrinkwrap`)
- [ ] npm-shrinkwrap.json created
- [ ] No linting errors

### Documentation Phase
- [ ] Operation documented in code
- [ ] Test names descriptive
- [ ] Any special behavior noted

## 🚫 Immediate Failure Conditions

Task FAILS if:

1. **Operation name contains 'describe'**
   - STOP immediately, use 'get' instead

2. **API spec contains 4xx or 5xx responses**
   - DELETE all error responses
   - Framework handles errors automatically

3. **Skipped generation step**
   - TASK FAILED - must run `npm run generate`

4. **No tests written**
   - TASK FAILED - tests are mandatory

5. **Tests not run**
   - TASK FAILED - must execute `npm test`

6. **Build not verified**
   - TASK FAILED - must execute `npm run build`

7. **Dependencies not locked**
   - TASK FAILED - must execute `npm run shrinkwrap`

8. **Used `Promise<any>` in signatures**
   - TASK FAILED - use generated types

9. **Stopped before Gate 6**
   - TASK INCOMPLETE - continue working

10. **Schema used in both nested and direct contexts without separation**
   - If schema has 10+ properties AND is referenced by other schemas AND has its own endpoint
   - CREATE separate `{Schema}Summary` for nested usage
   - See api-specification.md Rule #19

11. **Hardcoded test values in integration tests**
   - ALL test values (IDs, names, etc.) MUST be in .env
   - Export from test/integration/Common.ts
   - Import and use in integration tests
   - NEVER hardcode: `const userId = '12345'` ❌
   - ALWAYS use .env: `const userId = TEST_USER_ID` ✅
   - See testing.md Rule #7

## Red Flags (Incomplete Work)

Watch for these signs:

1. **No mention of tests** - MAJOR red flag
2. **"I've added the operation"** without test confirmation
3. **No `npm test` output shown**
4. **No build validation**
5. **Jumping to "done" after implementation**

## User Should NEVER Have To Say

- "Now write tests"
- "Did you test it?"
- "Run the tests"
- "Does it build?"
- "You forgot the tests"

If user has to remind about tests: **TASK HAS FAILED**

## Complete Execution Example

```
✅ OPERATION COMPLETE: getAccessToken

Gate 1 - API Specification: ✅
- Operation: getAccessToken (uses 'get', not 'describe')
- Summary: "Retrieve an access token"
- Description from vendor docs included
- Response: 200 only (no error codes)
- All properties camelCase

Gate 2 - Type Generation: ✅
- Ran: npm run generate
- Generated: AccessToken interface
- No InlineResponse types
- Exit code: 0

Gate 3 - Implementation: ✅
- Added to AccessTokenProducer
- Signature: Promise<AccessToken> (not any)
- Mappers handle snake_case → camelCase
- Error handling uses core errors

Gate 4 - Test Creation: ✅
- Unit tests: test/AccessTokenProducerTest.ts (4 cases)
- Integration: test/integration/AccessTokenIntegrationTest.ts (2 cases)
- Mock responses configured

Gate 5 - Test Execution: ✅
- Ran: npm test
- Result: 6 passing
- No regressions
- Exit code: 0

Gate 6 - Build: ✅
- Ran: npm run build
- Result: Success
- No errors
- Exit code: 0
- Ran: npm run shrinkwrap
- npm-shrinkwrap.json created
- Dependencies locked

Operation is fully implemented, tested, and verified.
```

## Enforcement Rules Summary

| Rule | Violation = Task Failure |
|------|--------------------------|
| NEVER use 'describe' prefix | ✅ Yes |
| ONLY 200/201 responses | ✅ Yes |
| ALWAYS run generate after API changes | ✅ Yes |
| ALWAYS write tests | ✅ Yes |
| ALWAYS run tests | ✅ Yes |
| ALWAYS build and verify | ✅ Yes |
| ALWAYS run shrinkwrap | ✅ Yes |
| NO `Promise<any>` types | ✅ Yes |

## Quick Validation Script

```bash
#!/bin/bash
# validate-operation.sh

echo "🚦 Validating operation completion..."

FAILED=0

# Gate 1: API Spec
if grep -E "describe[A-Z]" api.yml > /dev/null 2>&1; then
  echo "❌ Gate 1 FAILED: 'describe' found in api.yml"
  FAILED=1
fi

if grep "nullable:" api.yml > /dev/null 2>&1; then
  echo "❌ Gate 1 FAILED: 'nullable' found in api.yml"
  FAILED=1
fi

# Check for schema context separation issues
nested_schemas=$(yq eval '.components.schemas[] | .. | select(type == "string" and test("#/components/schemas/")) | capture("#/components/schemas/(?<schema>.+)").schema' api.yml 2>/dev/null | sort -u)
endpoint_schemas=$(yq eval '.paths.*.*.responses.*.content.*.schema["$ref"]' api.yml 2>/dev/null | grep -o '[^/]*$' | sort -u)
for schema in $nested_schemas; do
  if echo "$endpoint_schemas" | grep -q "^${schema}$"; then
    prop_count=$(yq eval ".components.schemas.${schema}.properties | length" api.yml 2>/dev/null)
    if [ "$prop_count" -gt 10 ] 2>/dev/null; then
      echo "⚠️  WARNING: Schema '${schema}' used in BOTH nested and direct contexts with ${prop_count} properties"
      echo "   Consider creating '${schema}Summary' for nested usage (see api-specification.md Rule #19)"
    fi
  fi
done

# Gate 2: Generation
if [ ! -d "generated" ]; then
  echo "❌ Gate 2 FAILED: No generated directory"
  FAILED=1
fi

# Gate 3: Implementation
if grep "Promise<any>" src/*.ts > /dev/null 2>&1; then
  echo "❌ Gate 3 FAILED: Promise<any> found"
  FAILED=1
fi

# Gate 4: Tests exist
if ! find test -name "*Test.ts" | grep -q .; then
  echo "❌ Gate 4 FAILED: No test files"
  FAILED=1
fi

# Gate 4b: No hardcoded test values in integration tests
if [ -d "test/integration" ]; then
  if grep -E "(const|let|var) [a-zA-Z]*[Ii]d = ['\"][0-9]+['\"]" test/integration/*.ts > /dev/null 2>&1; then
    echo "❌ Gate 4 FAILED: Hardcoded test values in integration tests"
    echo "   All test values must be in .env and imported from Common.ts"
    FAILED=1
  fi
fi

# Gate 5: Tests pass
if ! npm test > /dev/null 2>&1; then
  echo "❌ Gate 5 FAILED: Tests failing"
  FAILED=1
fi

# Gate 6: Build passes
if ! npm run build > /dev/null 2>&1; then
  echo "❌ Gate 6 FAILED: Build failing"
  FAILED=1
fi

# Gate 6b: Shrinkwrap dependencies
if ! npm run shrinkwrap > /dev/null 2>&1; then
  echo "❌ Gate 6 FAILED: Shrinkwrap failing"
  FAILED=1
fi

if [ ! -f "npm-shrinkwrap.json" ]; then
  echo "❌ Gate 6 FAILED: npm-shrinkwrap.json not created"
  FAILED=1
fi

if [ $FAILED -eq 0 ]; then
  echo "✅ ALL GATES PASSED - Operation complete!"
else
  echo "🚨 FAILED - Fix issues and re-validate"
  exit 1
fi
```

## Remember

**GATES ARE NOT OPTIONAL**

Every operation MUST pass through ALL gates sequentially.
No shortcuts. No exceptions. Complete or fail.
