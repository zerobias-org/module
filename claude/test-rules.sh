#!/bin/bash

# Quick test to verify Claude is following rules

echo "🧪 TESTING RULE COMPLIANCE"
echo "=========================="

TEST_DIR="/tmp/claude-test-$$"
mkdir -p $TEST_DIR

# Test 1: Check if Claude would add error responses
echo "Test 1: Error Response Prevention"
cat > $TEST_DIR/test-api.yml << 'EOF'
paths:
  /test:
    get:
      responses:
        '200':
          description: Success
        '401':
          description: Unauthorized
        '500':
          description: Server Error
EOF

if grep -E "'40[0-9]'|'50[0-9]'" $TEST_DIR/test-api.yml > /dev/null; then
  echo "❌ FAIL: Error responses detected (should only have 200/201)"
else
  echo "✅ PASS: No error responses"
fi

# Test 2: Check for any types
echo "Test 2: Type Safety Check"
cat > $TEST_DIR/test-impl.ts << 'EOF'
async getUser(): Promise<any> {
  return this.client.get('/user');
}
EOF

if grep "Promise<any>" $TEST_DIR/test-impl.ts > /dev/null; then
  echo "❌ FAIL: 'any' type detected (should use generated types)"
else
  echo "✅ PASS: No 'any' types"
fi

# Test 3: Check for snake_case
echo "Test 3: Naming Convention"
cat > $TEST_DIR/test-naming.yml << 'EOF'
properties:
  user_name:
    type: string
  first_name:
    type: string
EOF

if grep -E "user_name|first_name" $TEST_DIR/test-naming.yml > /dev/null; then
  echo "❌ FAIL: snake_case detected (should use camelCase)"
else
  echo "✅ PASS: No snake_case"
fi

# Cleanup
rm -rf $TEST_DIR

echo "=========================="
echo "If any test failed, rules are not being followed!"