#!/bin/bash

# Module-agnostic script to record integration tests with nock
# Usage: record-integration-tests.sh --module-path <path> --output-dir <dir> --service-name <name> [--test-pattern <pattern>]

set -e

# Default values
TEST_PATTERN="test/integration/**/*.ts"
RECORD_MODE="true"

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --module-path)
            MODULE_PATH="$2"
            shift 2
            ;;
        --output-dir)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        --service-name)
            SERVICE_NAME="$2"
            shift 2
            ;;
        --test-pattern)
            TEST_PATTERN="$2"
            shift 2
            ;;
        *)
            echo "Unknown argument: $1"
            exit 1
            ;;
    esac
done

# Validate required parameters
if [ -z "$MODULE_PATH" ] || [ -z "$OUTPUT_DIR" ] || [ -z "$SERVICE_NAME" ]; then
    echo "Error: Missing required parameters"
    echo "Usage: $0 --module-path <path> --output-dir <dir> --service-name <name> [--test-pattern <pattern>]"
    exit 1
fi

echo "🔄 Recording integration tests..."
echo "  Module: $MODULE_PATH"
echo "  Output: $OUTPUT_DIR"
echo "  Service: $SERVICE_NAME"
echo "  Pattern: $TEST_PATTERN"

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Change to module directory
cd "$MODULE_PATH"

# Set recording environment variables
export RECORD_MODE="$RECORD_MODE"
export NOCK_RECORD_OUTPUT_FILE="$OUTPUT_DIR/recordings-raw.json"

# Run integration tests with recording enabled
echo "📡 Running integration tests with recording enabled..."
npm run test:integration 2>&1 | tee "$OUTPUT_DIR/test-execution.log"

# Check if recording file was created
if [ -f "$NOCK_RECORD_OUTPUT_FILE" ]; then
    echo "✅ Recording completed successfully"
    echo "📁 Raw recordings saved to: $NOCK_RECORD_OUTPUT_FILE"
else
    echo "⚠️ No recordings captured - this may be normal if tests are already mocked"
    echo "📁 Creating empty recording file for consistency"
    echo "[]" > "$NOCK_RECORD_OUTPUT_FILE"
fi

echo "🎯 Integration test recording complete"