#!/bin/bash

# Auto-Validation Hook
# Run this after ANY code change to catch violations immediately

set -e

echo "🔒 STRICT ENFORCEMENT - Auto Validation"
echo "========================================"

VIOLATIONS=0
MODULE_PATH=${1:-.}

cd "$MODULE_PATH"

# Validation 1: Operation Naming
echo -n "Validating operation names... "
if grep -rE "operationId:\s*(describe|fetch|retrieve|find|query|add|remove|modify|patch)[A-Z]" api.yml 2>/dev/null; then
    echo "❌ VIOLATION: Forbidden operation name found"
    echo "  Allowed: get, list, search, create, update, delete"
    VIOLATIONS=$((VIOLATIONS + 1))
else
    echo "✅"
fi

# Validation 2: Error Responses
echo -n "Checking for error responses... "
if grep -rE "'\s*40[0-9]\s*'|'\s*50[0-9]\s*'" api.yml 2>/dev/null; then
    echo "❌ VIOLATION: Error responses found in API spec"
    echo "  Only 200/201 allowed"
    VIOLATIONS=$((VIOLATIONS + 1))
else
    echo "✅"
fi

# Validation 3: Any Types
echo -n "Checking for 'any' types... "
if grep -rE "Promise<any>|: any\s*[=;]" src/*.ts 2>/dev/null; then
    echo "❌ VIOLATION: 'any' type found"
    echo "  Must use generated types"
    VIOLATIONS=$((VIOLATIONS + 1))
else
    echo "✅"
fi

# Validation 4: snake_case Properties
echo -n "Checking for snake_case... "
if grep -E "^\s+[a-z_]+_[a-z_]+:" api.yml 2>/dev/null | grep -v "x-" | grep -v "#"; then
    echo "❌ VIOLATION: snake_case property found"
    echo "  Must use camelCase"
    VIOLATIONS=$((VIOLATIONS + 1))
else
    echo "✅"
fi

# Validation 5: Test Files Exist
echo -n "Checking test coverage... "
IMPL_COUNT=$(find src -name "*.ts" -type f 2>/dev/null | wc -l | tr -d ' ')
TEST_COUNT=$(find test -name "*Test.ts" -type f 2>/dev/null | wc -l | tr -d ' ')

if [ "$IMPL_COUNT" -gt 0 ] && [ "$TEST_COUNT" -eq 0 ]; then
    echo "❌ VIOLATION: No test files found"
    echo "  Tests are MANDATORY"
    VIOLATIONS=$((VIOLATIONS + 1))
else
    echo "✅"
fi

# Validation 6: Path and Schema Structure
echo -n "Checking paths and schemas... "
if [ -f "api.yml" ]; then
    # Check for wrapped schemas
    if grep -A 10 "responses:" api.yml | grep -A 8 "'200':" | grep -E "^\s+properties:\s*$" > /dev/null 2>&1; then
        echo "❌ VIOLATION: Response wraps \$ref in properties"
        echo "  Must use direct \$ref or array of \$ref"
        VIOLATIONS=$((VIOLATIONS + 1))
    # Check for forbidden path prefixes
    elif grep -E "^\s+/auth/|^\s+/api/|^\s+/v[0-9]+/" api.yml > /dev/null 2>&1; then
        echo "❌ VIOLATION: Path has forbidden prefix"
        echo "  No /auth/, /api/, /v1/ allowed"
        VIOLATIONS=$((VIOLATIONS + 1))
    # Check for current/me/self in paths
    elif grep -E "/(current|me|self)\b" api.yml > /dev/null 2>&1; then
        echo "❌ VIOLATION: Path contains current/me/self"
        echo "  Use clean resource paths"
        VIOLATIONS=$((VIOLATIONS + 1))
    else
        echo "✅"
    fi
else
    echo "⚠️  (skipped - no api.yml)"
fi

# Validation 6b: Path/Operation Consistency
echo -n "Checking path/operation consistency... "
if [ -f "api.yml" ]; then
    # Simple check: look for plural paths with get operation (without {id})
    if grep -B 5 "operationId: get[A-Z]" api.yml | grep -E "^\s+/[a-z]+s:\s*$" | head -1 > /dev/null 2>&1; then
        echo "⚠️  WARNING: Possible plural path with get operation"
        echo "  Run: ./claude/validate-path-operation-consistency.sh"
    else
        echo "✅"
    fi
else
    echo "⚠️  (skipped - no api.yml)"
fi

# Validation 7: Generation Check
echo -n "Checking type generation... "
if [ -f "api.yml" ] && [ ! -d "generated" ]; then
    echo "⚠️  WARNING: API exists but types not generated"
    echo "  Run: npm run generate"
fi

if [ -d "generated" ]; then
    if grep -r "InlineResponse\|InlineRequestBody" generated/ 2>/dev/null; then
        echo "❌ VIOLATION: Inline types found"
        echo "  Nested objects must use \$ref"
        VIOLATIONS=$((VIOLATIONS + 1))
    else
        echo "✅"
    fi
else
    echo "⚠️  (skipped - no generated dir)"
fi

echo "========================================"

if [ $VIOLATIONS -gt 0 ]; then
    echo "❌ VALIDATION FAILED: $VIOLATIONS violation(s)"
    echo ""
    echo "Fix all violations before proceeding."
    echo "Every violation = TASK FAILURE"
    exit 1
else
    echo "✅ ALL VALIDATIONS PASSED"
    echo ""
    echo "Continue with next step."
fi