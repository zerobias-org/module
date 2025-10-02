#!/bin/bash

# Validate Path and Operation Consistency
# Rule 15: Path plurality must match operation type

echo "🔍 Validating Path/Operation Consistency"
echo "========================================="

ERRORS=0
MODULE_PATH=${1:-.}

cd "$MODULE_PATH"

if [ ! -f "api.yml" ]; then
    echo "⚠️  No api.yml found in $MODULE_PATH"
    exit 0
fi

echo ""
echo "Checking path plurality vs operation type..."
echo ""

# Extract paths and their operations
temp_file=$(mktemp)
awk '/^  \/[^:]+:$/,/operationId:/' api.yml > "$temp_file"

while IFS= read -r line; do
    if [[ "$line" =~ ^[[:space:]]{2}(/[^:]+): ]]; then
        current_path="${BASH_REMATCH[1]}"
        current_method=""
    elif [[ "$line" =~ ^[[:space:]]{4}(get|post|put|patch|delete): ]]; then
        current_method="${BASH_REMATCH[1]}"
    elif [[ "$line" =~ operationId:[[:space:]]*(.*) ]]; then
        operation_id="${BASH_REMATCH[1]}"

        # Check consistency
        path_has_param=false
        if [[ "$current_path" =~ \{.*\} ]]; then
            path_has_param=true
        fi

        # Get base path (last segment without parameter)
        base_path=$(echo "$current_path" | sed 's/{[^}]*}//g' | sed 's/\/$//' | awk -F'/' '{print $NF}')

        # Check if path ends with 's' (plural)
        path_is_plural=false
        if [[ "$base_path" =~ s$ ]]; then
            path_is_plural=true
        fi

        # Check operation type
        is_list_op=false
        is_get_op=false
        if [[ "$operation_id" =~ ^list ]]; then
            is_list_op=true
        elif [[ "$operation_id" =~ ^get ]]; then
            is_get_op=true
        fi

        # Validate combinations
        if [ "$is_list_op" = true ]; then
            # list* operations should have plural path without parameter
            if [ "$path_is_plural" = false ]; then
                echo "❌ INCONSISTENCY: $current_path ($current_method)"
                echo "   Operation: $operation_id (list)"
                echo "   Issue: Path should be PLURAL for list operations"
                echo "   Fix: Change path to plural form"
                echo ""
                ERRORS=$((ERRORS + 1))
            elif [ "$path_has_param" = true ]; then
                echo "⚠️  WARNING: $current_path ($current_method)"
                echo "   Operation: $operation_id (list)"
                echo "   Issue: List operations typically don't have path parameters"
                echo ""
            fi
        elif [ "$is_get_op" = true ] && [ "$path_has_param" = false ]; then
            # get* without path parameter
            if [ "$path_is_plural" = true ]; then
                echo "❌ INCONSISTENCY: $current_path ($current_method)"
                echo "   Operation: $operation_id (get single)"
                echo "   Issue: Plural path with 'get' operation but NO {id} parameter"
                echo "   Fix: Either:"
                echo "     1. Add {id} parameter: $current_path/{${base_path%s}Id}"
                echo "     2. Change path to singular: /${base_path%s}"
                echo "     3. Change operation to list: list${operation_id#get}"
                echo ""
                ERRORS=$((ERRORS + 1))
            fi
        fi
    fi
done < "$temp_file"

rm "$temp_file"

echo "========================================="

if [ $ERRORS -gt 0 ]; then
    echo "❌ VALIDATION FAILED: $ERRORS inconsistenc(ies)"
    echo ""
    echo "Rule 15: Path Plurality Must Match Operation"
    echo ""
    echo "Correct patterns:"
    echo "  • list*: /resources (plural, no {id})"
    echo "  • get*:  /resources/{id} (plural + {id})"
    echo "  • get*:  /resource (singular, no {id})"
    echo ""
    echo "Common violation:"
    echo "  ❌ /accessTokens + getAccessToken (no {id})"
    echo "  ✅ /accessTokens/{tokenId} + getAccessToken"
    echo "  ✅ /accessToken + getAccessToken"
    exit 1
else
    echo "✅ PATH/OPERATION CONSISTENCY PASSED"
fi