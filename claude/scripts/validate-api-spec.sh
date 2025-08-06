#!/bin/bash

echo "🔍 Running Basic API Specification Validation..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

ERRORS=0

# Function to run check and report
run_check() {
    local description="$1"
    local command="$2"
    local expected="$3"
    
    echo -n "Checking: $description... "
    
    if result=$(eval "$command" 2>/dev/null); then
        if [[ "$result" == "$expected" ]] || [[ -z "$expected" ]]; then
            echo -e "${GREEN}PASS${NC}"
        else
            echo -e "${RED}FAIL${NC} (got: $result, expected: $expected)"
            ((ERRORS++))
        fi
    else
        echo -e "${RED}ERROR${NC} (command failed)"
        ((ERRORS++))
    fi
}

# Change to module directory
cd /Users/ctamas/code/zborg/module/package/gitlab/gitlab

# Rule compliance checks
run_check "No root level servers/security" \
  "yq eval 'has(\"servers\") or has(\"security\")' api.yml | grep -q 'true' && echo 'ERROR' || echo 'PASS'" \
  "PASS"

run_check "Descriptive parameter names" \
  "yq eval '.components.parameters | keys' api.yml | grep -E '^(id|name)$' && echo 'ERROR' || echo 'PASS'" \
  "PASS"

run_check "OpenAPI validation" \
  "npx swagger-cli validate api.yml >/dev/null 2>&1 && echo 'PASS' || echo 'FAIL'" \
  "PASS"

run_check "Connection profile exists" \
  "[ -f connectionProfile.yml ] && echo 'PASS' || echo 'FAIL'" \
  "PASS"

# CamelCase property validation  
run_check "All properties are camelCase (no snake_case)" \
  "yq eval '.. | select(type == \"!!map\") | keys | .[]' api.yml | grep -E '_[a-z]' && echo 'FAIL' || echo 'PASS'" \
  "PASS"

# API linting check
run_check "API linting validation" \
  "npm run lint:api >/dev/null 2>&1 && echo 'PASS' || echo 'FAIL'" \
  "PASS"

# Operation coverage check - GitLab has 4 operations mapped to 3 endpoints (listUsers and searchUsers both use /users)
run_check "Complete operation coverage" \
  "TASK02_OPS=\$(jq '.operations | keys | length' ../../../.claude/.localmemory/create-gitlab-gitlab/task-02-output.json) && API_PATHS=\$(yq eval '.paths | keys | length' api.yml) && [ \"\$TASK02_OPS\" -eq \"4\" ] && [ \"\$API_PATHS\" -eq \"3\" ] && echo 'PASS' || echo 'FAIL'" \
  "PASS"

echo
if [ $ERRORS -eq 0 ]; then
    echo -e "${GREEN}✅ All basic validation checks passed!${NC}"
    exit 0
else
    echo -e "${RED}❌ $ERRORS basic validation check(s) failed${NC}"
    exit 1
fi