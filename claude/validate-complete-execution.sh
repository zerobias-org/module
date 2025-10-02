#!/bin/bash

# Validate Complete Execution Script

OPERATION=$1

if [ -z "$OPERATION" ]; then
  echo "Usage: ./validate-complete-execution.sh <operationName>"
  exit 1
fi

echo "🔍 Validating Complete Execution for: $OPERATION"
echo "========================================="

FAILURES=0

# Step 1: Check API Spec
echo -n "1. API Specification... "
if grep -q "operationId: $OPERATION" */*/api.yml */*/*/api.yml 2>/dev/null; then
  echo "✅"
else
  echo "❌ NOT FOUND"
  FAILURES=$((FAILURES + 1))
fi

# Step 2: Check Generation
echo -n "2. Type Generation... "
if [ -d "generated" ] && [ "$(ls -A generated/)" ]; then
  echo "✅"
else
  echo "❌ NOT GENERATED"
  FAILURES=$((FAILURES + 1))
fi

# Step 3: Check Implementation
echo -n "3. Implementation... "
if grep -q "async $OPERATION" */*/src/*.ts */*/*/src/*.ts 2>/dev/null; then
  echo "✅"
else
  echo "❌ NOT IMPLEMENTED"
  FAILURES=$((FAILURES + 1))
fi

# Step 4: Check Tests Written
echo -n "4. Tests Written... "
if grep -q "describe.*$OPERATION\|it.*$OPERATION" */*/test/*.ts */*/*/test/*.ts 2>/dev/null; then
  echo "✅"
else
  echo "❌ NO TESTS!"
  FAILURES=$((FAILURES + 1))
fi

# Step 5: Check Tests Pass
echo -n "5. Tests Passing... "
if npm test 2>&1 | grep -q "passing"; then
  echo "✅"
else
  echo "❌ TESTS FAILING"
  FAILURES=$((FAILURES + 1))
fi

# Step 6: Check Build
echo -n "6. Build Success... "
if npm run build > /dev/null 2>&1; then
  echo "✅"
else
  echo "❌ BUILD FAILING"
  FAILURES=$((FAILURES + 1))
fi

echo "========================================="

if [ $FAILURES -eq 0 ]; then
  echo "✅ OPERATION COMPLETE!"
  echo "All steps executed successfully."
else
  echo "❌ OPERATION INCOMPLETE!"
  echo "Failed steps: $FAILURES"
  echo ""
  echo "Claude must complete ALL steps:"
  echo "1. Update API spec"
  echo "2. Generate types"
  echo "3. Implement operation"
  echo "4. Write tests"
  echo "5. Run tests"
  echo "6. Build project"
  exit 1
fi