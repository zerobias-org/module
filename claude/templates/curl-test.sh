#!/bin/bash

# Template for testing API endpoints with curl
# Usage: ./curl-test.sh <endpoint> <method> [data]

# Load credentials
if [ -f .env ]; then
  export $(cat .env | xargs)
fi

# Configuration
BASE_URL="${BASE_URL:-https://api.example.com}"
TOKEN="${API_TOKEN:-$TOKEN}"
METHOD="${2:-GET}"
ENDPOINT="$1"
DATA="$3"

# Validate inputs
if [ -z "$ENDPOINT" ]; then
  echo "Usage: ./curl-test.sh <endpoint> [method] [data]"
  exit 1
fi

if [ -z "$TOKEN" ]; then
  echo "Error: No API token found"
  echo "Set API_TOKEN in .env file"
  exit 1
fi

# Build curl command
CURL_CMD="curl -X $METHOD"
CURL_CMD="$CURL_CMD -H 'Authorization: Bearer $TOKEN'"
CURL_CMD="$CURL_CMD -H 'Accept: application/json'"
CURL_CMD="$CURL_CMD -H 'Content-Type: application/json'"

if [ -n "$DATA" ]; then
  CURL_CMD="$CURL_CMD -d '$DATA'"
fi

CURL_CMD="$CURL_CMD '${BASE_URL}${ENDPOINT}'"

# Execute and format output
echo "Testing: $METHOD $ENDPOINT"
echo "Command: $CURL_CMD"
echo "---"

# Check if jq is available for pretty printing
if command -v jq &> /dev/null; then
  eval $CURL_CMD | jq '.'
else
  # Fallback to raw output
  eval $CURL_CMD
fi

# Save response
SAFE_NAME=$(echo "$ENDPOINT" | sed 's/\//_/g')
eval $CURL_CMD > "test-response-${SAFE_NAME}.json" 2>/dev/null
echo ""
echo "Response saved to: test-response-${SAFE_NAME}.json"