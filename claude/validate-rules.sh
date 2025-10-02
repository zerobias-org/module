#!/bin/bash

echo "🔍 Validating Claude Rules Compliance..."

# Check for error responses in API specs
echo -n "Checking for forbidden error responses... "
if grep -r "40[0-9]\|50[0-9]" package/*/api.yml 2>/dev/null | grep -v "#"; then
    echo "❌ FOUND! Only 200/201 allowed!"
    exit 1
else
    echo "✅"
fi

# Check for 'any' types in TypeScript files
echo -n "Checking for 'any' types in signatures... "
if grep -r "Promise<any>" package/*/src/*.ts 2>/dev/null; then
    echo "❌ FOUND! Use generated types!"
    exit 1
else
    echo "✅"
fi

# Check for camelCase in API specs
echo -n "Checking for snake_case in API specs... "
if grep -r "snake_case\|under_score" package/*/api.yml 2>/dev/null | grep -v "#"; then
    echo "❌ FOUND! Use camelCase only!"
    exit 1
else
    echo "✅"
fi

echo "✅ All basic rules validated!"