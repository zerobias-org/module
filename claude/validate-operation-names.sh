#!/bin/bash

# Operation Name Validation Script

echo "🔍 Validating Operation Names..."
echo "================================"

ERRORS=0

# Check for forbidden prefixes in api.yml
echo -n "Checking for 'describe' prefix... "
if grep -E "operationId:\s*describe[A-Z]" */*/api.yml */*/*/api.yml 2>/dev/null; then
    echo "❌ FOUND!"
    ERRORS=$((ERRORS + 1))
else
    echo "✅"
fi

echo -n "Checking for 'fetch' prefix... "
if grep -E "operationId:\s*fetch[A-Z]" */*/api.yml */*/*/api.yml 2>/dev/null; then
    echo "❌ FOUND!"
    ERRORS=$((ERRORS + 1))
else
    echo "✅"
fi

echo -n "Checking for 'retrieve' prefix... "
if grep -E "operationId:\s*retrieve[A-Z]" */*/api.yml */*/*/api.yml 2>/dev/null; then
    echo "❌ FOUND!"
    ERRORS=$((ERRORS + 1))
else
    echo "✅"
fi

echo -n "Checking for 'find' prefix (should be 'get' or 'search')... "
if grep -E "operationId:\s*find[A-Z]" */*/api.yml */*/*/api.yml 2>/dev/null; then
    echo "❌ FOUND!"
    ERRORS=$((ERRORS + 1))
else
    echo "✅"
fi

echo -n "Checking for 'query' prefix (should be 'search')... "
if grep -E "operationId:\s*query[A-Z]" */*/api.yml */*/*/api.yml 2>/dev/null; then
    echo "❌ FOUND!"
    ERRORS=$((ERRORS + 1))
else
    echo "✅"
fi

echo -n "Checking for 'add' prefix (should be 'create')... "
if grep -E "operationId:\s*add[A-Z]" */*/api.yml */*/*/api.yml 2>/dev/null; then
    echo "❌ FOUND!"
    ERRORS=$((ERRORS + 1))
else
    echo "✅"
fi

echo -n "Checking for 'remove' prefix (should be 'delete')... "
if grep -E "operationId:\s*remove[A-Z]" */*/api.yml */*/*/api.yml 2>/dev/null; then
    echo "❌ FOUND!"
    ERRORS=$((ERRORS + 1))
else
    echo "✅"
fi

echo -n "Checking for 'modify' prefix (should be 'update')... "
if grep -E "operationId:\s*modify[A-Z]" */*/api.yml */*/*/api.yml 2>/dev/null; then
    echo "❌ FOUND!"
    ERRORS=$((ERRORS + 1))
else
    echo "✅"
fi

echo -n "Checking for 'patch' prefix (should be 'update')... "
if grep -E "operationId:\s*patch[A-Z]" */*/api.yml */*/*/api.yml 2>/dev/null; then
    echo "❌ FOUND!"
    ERRORS=$((ERRORS + 1))
else
    echo "✅"
fi

# Check in TypeScript files
echo ""
echo "Checking TypeScript files..."

echo -n "Checking for 'describe' methods... "
if grep -E "async describe[A-Z]" */*/src/*.ts */*/*/src/*.ts 2>/dev/null; then
    echo "❌ FOUND!"
    ERRORS=$((ERRORS + 1))
else
    echo "✅"
fi

echo "================================"

if [ $ERRORS -gt 0 ]; then
    echo "❌ Found $ERRORS naming violations!"
    echo ""
    echo "Preferred prefixes:"
    echo "  • get     - Single resource retrieval"
    echo "  • list    - Multiple resources (GET)"
    echo "  • search  - Complex filtering (POST)"
    echo "  • create  - New resource"
    echo "  • update  - Modify resource"
    echo "  • delete  - Remove resource"
    exit 1
else
    echo "✅ All operation names are valid!"
fi