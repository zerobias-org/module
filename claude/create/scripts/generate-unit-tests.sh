#!/bin/bash

# Module-agnostic script to generate unit tests based on sanitized recordings
# Usage: generate-unit-tests.sh --module-path <path> --recordings-file <file> --service-name <name> [--template-dir <dir>]

set -e

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --module-path)
            MODULE_PATH="$2"
            shift 2
            ;;
        --recordings-file)
            RECORDINGS_FILE="$2"
            shift 2
            ;;
        --service-name)
            SERVICE_NAME="$2"
            shift 2
            ;;
        --template-dir)
            TEMPLATE_DIR="$2"
            shift 2
            ;;
        *)
            echo "Unknown argument: $1"
            exit 1
            ;;
    esac
done

# Validate required parameters
if [ -z "$MODULE_PATH" ] || [ -z "$RECORDINGS_FILE" ] || [ -z "$SERVICE_NAME" ]; then
    echo "Error: Missing required parameters"
    echo "Usage: $0 --module-path <path> --recordings-file <file> --service-name <name> [--template-dir <dir>]"
    exit 1
fi

echo "🏗️ Generating unit tests..."
echo "  Module: $MODULE_PATH"
echo "  Recordings: $RECORDINGS_FILE"
echo "  Service: $SERVICE_NAME"
if [ -n "$TEMPLATE_DIR" ]; then
    echo "  Templates: $TEMPLATE_DIR"
fi

# Change to module directory
cd "$MODULE_PATH"

# Create test directories
mkdir -p test/unit
mkdir -p test/utils
mkdir -p test/fixtures/recorded

echo "📁 Created test directory structure"

# Check if recordings file exists
if [ -f "$RECORDINGS_FILE" ]; then
    echo "📄 Using recordings from: $RECORDINGS_FILE"
    # Copy sanitized recordings to fixtures
    cp "$RECORDINGS_FILE" test/fixtures/recorded/integration-tests-sanitized.json
    echo "✅ Copied sanitized recordings to fixtures"
else
    echo "⚠️ Recordings file not found: $RECORDINGS_FILE"
    echo "📄 Creating empty recordings file"
    echo "[]" > test/fixtures/recorded/integration-tests-sanitized.json
fi

echo "🎯 Unit test generation setup complete"
echo "📝 Note: Actual test file generation should be done by the task implementation"