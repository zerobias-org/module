#!/bin/bash

# Rule Loader - Load only relevant rules for specific tasks

TASK=$1

echo "📋 Loading rules for: $TASK"
echo "================================"

case "$TASK" in
  "create-module")
    echo "Loading CREATE MODULE rules:"
    echo "• claude/workflow/tasks/create-module.md"
    echo "• claude/rules/prerequisites.md"
    echo "• claude/rules/api-specification-critical.md"
    echo "• claude/rules/implementation.md"
    echo "• claude/rules/build-quality.md"
    ;;

  "add-operation")
    echo "Loading ADD OPERATION rules:"
    echo "• claude/workflow/tasks/add-operation.md"
    echo "• claude/rules/operation-naming.md"
    echo "• claude/rules/api-descriptions.md"
    echo "• claude/rules/api-specification-critical.md"
    echo "• claude/rules/api-specification.md"
    echo "• claude/rules/implementation.md"
    echo "• claude/rules/type-mapping.md"
    echo "• claude/rules/error-handling.md"
    ;;

  "update-module")
    echo "Loading UPDATE MODULE rules:"
    echo "• claude/workflow/tasks/update-module.md"
    echo "• claude/rules/api-specification-critical.md"
    echo "• claude/rules/implementation.md"
    echo "• claude/rules/testing.md"
    ;;

  "fix-module")
    echo "Loading FIX MODULE rules:"
    echo "• claude/workflow/tasks/fix-module.md"
    echo "• claude/rules/error-handling.md"
    echo "• claude/rules/testing.md"
    echo "• claude/rules/build-quality.md"
    ;;

  "api-spec")
    echo "Loading API SPECIFICATION rules:"
    echo "• claude/rules/operation-naming.md"
    echo "• claude/rules/api-descriptions.md"
    echo "• claude/rules/api-specification-critical.md"
    echo "• claude/rules/api-specification.md"
    ;;

  "implementation")
    echo "Loading IMPLEMENTATION rules:"
    echo "• claude/rules/implementation.md"
    echo "• claude/rules/type-mapping.md"
    echo "• claude/rules/error-handling.md"
    ;;

  "testing")
    echo "Loading TESTING rules:"
    echo "• claude/rules/testing.md"
    echo "• claude/rules/build-quality.md"
    ;;

  *)
    echo "❌ Unknown task: $TASK"
    echo "Available tasks:"
    echo "  • create-module"
    echo "  • add-operation"
    echo "  • update-module"
    echo "  • fix-module"
    echo "  • api-spec"
    echo "  • implementation"
    echo "  • testing"
    exit 1
    ;;
esac

echo "================================"
echo "✅ Rules loaded for $TASK"