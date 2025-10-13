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

