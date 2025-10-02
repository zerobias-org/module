#!/bin/bash

# Validate API Paths and Response Schemas

echo "🔍 Validating API Paths and Response Schemas"
echo "============================================="

ERRORS=0
MODULE_PATH=${1:-.}

cd "$MODULE_PATH"

if [ ! -f "api.yml" ]; then
    echo "⚠️  No api.yml found in $MODULE_PATH"
    exit 0
fi

# Rule 13: Response Schema Must Be Direct $ref or Array
echo ""
echo "Rule 13: Response Schema Structure"
echo "-----------------------------------"

# Check for wrapped $ref in response schemas
if grep -A 10 "responses:" api.yml | grep -A 8 "'200':" | grep -E "properties:|required:" | head -5; then
    echo ""
    echo "❌ VIOLATION: Response schema wraps \$ref in object properties"
    echo ""
    echo "Found response with 'properties' or 'required' (wrapping pattern):"
    grep -B 5 -A 10 "properties:" api.yml | grep -B 5 -A 10 "data:" | head -20
    echo ""
    echo "RULE: Response schema MUST be one of:"
    echo "  1. Direct \$ref: schema: \$ref: '#/components/schemas/Model'"
    echo "  2. Array of \$ref: schema: type: array, items: \$ref: ..."
    echo ""
    echo "NEVER wrap like this:"
    echo "  schema:"
    echo "    type: object"
    echo "    properties:"
    echo "      data:"
    echo "        \$ref: '#/components/schemas/Model'"
    ERRORS=$((ERRORS + 1))
else
    echo "✅ Response schemas use direct \$ref or array of \$ref"
fi

# Rule 15: Clean Path Structure
echo ""
echo "Rule 15: Clean Path Structure"
echo "------------------------------"

# Check for forbidden path prefixes
if grep -E "^\s+/auth/|^\s+/api/|^\s+/v[0-9]/" api.yml; then
    echo ""
    echo "❌ VIOLATION: Paths contain forbidden prefixes"
    echo ""
    echo "RULE: Paths must be clean and resource-based"
    echo "  ✅ GOOD: /accessTokens, /users, /users/{userId}"
    echo "  ❌ BAD:  /auth/accessTokens, /api/v1/users"
    ERRORS=$((ERRORS + 1))
else
    echo "✅ No forbidden path prefixes (/auth/, /api/, /v1/)"
fi

# Check for paths with 'current', 'me', 'self'
if grep -E "^\s+/.*/current\b|^\s+/.*/me\b|^\s+/.*/self\b" api.yml; then
    echo ""
    echo "❌ VIOLATION: Paths contain 'current', 'me', or 'self'"
    echo ""
    echo "RULE: Use clean resource paths"
    echo "  ❌ BAD:  /accessTokens/current"
    echo "  ✅ GOOD: /accessTokens/{tokenId}"
    ERRORS=$((ERRORS + 1))
else
    echo "✅ No 'current/me/self' in paths"
fi

# Check for paths starting with parameters (like /{orgId})
if grep -E "^\s+/\{[^}]+\}/" api.yml; then
    echo ""
    echo "❌ VIOLATION: Paths start with parameter"
    echo ""
    grep -E "^\s+/\{[^}]+\}/" api.yml
    echo ""
    echo "RULE: Paths must start with resource name"
    echo "  ❌ BAD:  /{orgId}/resources"
    echo "  ✅ GOOD: /resources"
    ERRORS=$((ERRORS + 1))
else
    echo "✅ Paths don't start with parameters"
fi

# Verify paths follow resource pattern
echo ""
echo "Analyzing path patterns..."
paths=$(grep -E "^\s+/" api.yml | sed 's/://g' | sed 's/^\s*//')

for path in $paths; do
    # Check if path is clean (starts with /{resource})
    if [[ ! "$path" =~ ^/[a-z][a-zA-Z0-9]* ]]; then
        echo "⚠️  Suspicious path: $path"
        echo "   Should start with /{resourceName}"
    fi
done

echo ""
echo "============================================="

if [ $ERRORS -gt 0 ]; then
    echo "❌ VALIDATION FAILED: $ERRORS violation(s)"
    echo ""
    echo "Fix violations before proceeding."
    echo ""
    echo "Quick Reference:"
    echo "  Rule 13: Response must be direct \$ref or array"
    echo "  Rule 15: Paths must be clean and resource-based"
    exit 1
else
    echo "✅ PATH AND SCHEMA VALIDATION PASSED"
fi